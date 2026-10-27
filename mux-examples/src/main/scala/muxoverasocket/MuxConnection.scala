package muxoverasocket

import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutputStream}
import java.net.Socket
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec

import zio.blocks.mux._

// One TCP connection carrying many streams: a Mux, a reader thread and a
// writer thread. `acceptStreams` is true on the side that lets the peer
// open streams (the server).
final class MuxConnection(socket: Socket, name: String, maxStreams: Int, acceptStreams: Boolean) {
  val mux: Mux[Int, Message, Message] = Mux[Int, Message, Message](maxStreams)

  private val table    = new StreamTable
  private val running  = new AtomicBoolean(true)
  private val incoming = new LinkedBlockingQueue[MessageStream]()
  private val in       = new DataInputStream(new BufferedInputStream(socket.getInputStream))
  private val out      = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))

  val reader: ReaderLoop = new ReaderLoop(in, mux, table, incoming, acceptStreams, running)
  // How many times `send` found the outbound queue full and waited.
  val sendWaits = new AtomicLong(0L)

  private val readerThread = new Thread(reader, s"$name-reader")
  private val writerThread = new Thread(new WriterLoop(out, table, running), s"$name-writer")
  readerThread.start()
  writerThread.start()

  // Opens a stream from this side.
  def open(id: Int): MessageStream | MuxError =
    mux.open(id) match {
      case stream: MessageStream =>
        table.track(stream)
        stream
      case error: MuxError => error
    }

  // The next stream the peer opened, or None once the connection is closed.
  @tailrec def accept(): Option[MessageStream] = {
    val stream = incoming.poll(10, TimeUnit.MILLISECONDS)
    if (stream != null) Some(stream)
    else if (!running.get && incoming.isEmpty) None
    else accept()
  }

  // Waits while the outbound queue is full instead of dropping the message.
  @tailrec def send(stream: MessageStream, message: Message): Unit | MuxError =
    stream.send(message) match {
      case MuxError.QueueFull(_) =>
        sendWaits.incrementAndGet()
        LockSupport.parkNanos(1_000_000L)
        send(stream, message)
      case result =>
        table.wakeWriter()
        result
    }

  // Half-close: queue End behind the data already sent, then stop sending.
  // Call it from the thread that sends on this stream.
  def finish(stream: MessageStream): Unit | MuxError =
    send(stream, Message.End) match {
      case ()              => stream.halfClose()
      case error: MuxError => error
    }

  // Cancels a stream on both ends: drop it here, then send RESET to the peer.
  def reset(id: Int, reason: String): Unit = {
    mux.cancel(id, MuxError.Cancelled(id, reason))
    table.sendControl(Frame.Reset(id, reason))
  }

  // Clean shutdown: fail every stream, let the writer flush what was already
  // queued, then close the socket, which ends the reader loop.
  def close(): Unit = {
    mux.closeAll(MuxError.Cancelled("connection", s"$name closed the connection"))
    running.set(false)
    table.wakeWriter()
    writerThread.join(5000)
    socket.close()
    readerThread.join(5000)
  }

  // Blocks until the peer closes the connection.
  def awaitPeerClose(): Unit = readerThread.join()
}

object MuxConnection {
  // `receive` never blocks: poll it, parking for 1 ms while the queue is empty.
  @tailrec def next(stream: MessageStream): Message | MuxError =
    stream.receive() match {
      case Some(message) => message
      case None          =>
        LockSupport.parkNanos(1_000_000L)
        next(stream)
      case error: MuxError => error
    }
}
