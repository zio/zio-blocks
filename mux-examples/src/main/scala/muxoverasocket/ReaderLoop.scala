package muxoverasocket

import java.io.{DataInputStream, IOException}
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec

import zio.blocks.mux._

// Reads frames off the socket and hands each one to its stream. It is the
// only code that calls `offerInbound` and `signalRemoteClose`.
final class ReaderLoop(
  in: DataInputStream,
  mux: Mux[Int, Message, Message],
  table: StreamTable,
  incoming: BlockingQueue[MessageStream],
  acceptStreams: Boolean,
  running: AtomicBoolean
) extends Runnable {
  // How long this loop stopped reading because a stream's inbound queue was full.
  val pausedMillis = new AtomicLong(0L)

  def run(): Unit =
    try loop()
    catch {
      case _: IOException => () // the connection broke, or we closed the socket ourselves
    } finally {
      running.set(false)
      mux.closeAll(MuxError.Cancelled("connection", "connection closed"))
      table.wakeWriter()
    }

  @tailrec private def loop(): Unit =
    Frame.read(in) match {
      case Some(frame) =>
        handle(frame)
        loop()
      case None => () // the peer closed the connection
    }

  private def handle(frame: Frame): Unit = frame match {
    case Frame.Data(id, text) =>
      streamFor(id).foreach(deliver(_, Message.Text(text)))
    case Frame.End(id) =>
      streamFor(id).foreach { stream =>
        deliver(stream, Message.End) // tell the application, in order, after the data
        stream.signalRemoteClose()   // and tell the Mux: the peer sends nothing more
      }
    case Frame.Reset(id, reason) =>
      mux.cancel(id, MuxError.Cancelled(id, reason))
      table.draining.remove(id)
  }

  // The open stream for `id`, or a new one the first time the peer uses `id`.
  private def streamFor(id: Int): Option[MessageStream] =
    mux.get(id) match {
      case found @ Some(_)                                      => found
      case None if !acceptStreams || table.usedIds.contains(id) => None // a late frame for a finished stream: drop it
      case None                                                 =>
        mux.open(id) match {
          case stream: MessageStream =>
            table.track(stream)
            incoming.put(stream)
            Some(stream)
          case error: MuxError => // CapacityExceeded: refuse the stream
            table.usedIds.add(id)
            table.sendControl(Frame.Reset(id, s"refused: $error"))
            None
        }
    }

  // Back-pressure: while the application is behind, stop reading the socket.
  // Nothing is dropped and memory stays bounded, but every stream on this
  // connection waits for the slow one.
  @tailrec private def deliver(stream: MessageStream, message: Message): Unit =
    stream.offerInbound(message) match {
      case ()                    => ()
      case MuxError.QueueFull(_) =>
        pausedMillis.incrementAndGet()
        LockSupport.parkNanos(1_000_000L)
        deliver(stream, message)
      case _: MuxError => () // closed or cancelled on our side: drop the frame
    }
}
