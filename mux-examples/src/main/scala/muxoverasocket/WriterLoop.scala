package muxoverasocket

import java.io.{DataOutputStream, IOException}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

import zio.blocks.mux._

// The only code that calls `takeOutbound`, on one thread, so every stream's
// outbound queue has exactly one consumer.
final class WriterLoop(out: DataOutputStream, table: StreamTable, running: AtomicBoolean) extends Runnable {
  // At most this many messages per stream per pass, so one busy stream
  // can't starve the others.
  private val Batch = 16

  def run(): Unit = {
    table.writer.set(Thread.currentThread())
    try loop()
    catch {
      // The connection is gone. Closing the stream closes the socket, which
      // ends the reader loop, which closes every stream in the Mux.
      case _: IOException =>
        running.set(false)
        try out.close()
        catch { case _: IOException => () }
    }
  }

  // After `running` turns false, keep going until a pass writes nothing, so
  // messages queued before the shutdown still reach the peer.
  @tailrec private def loop(): Unit = {
    val controlFrames = writeControlFrames(0)
    val messages      = table.draining.values.asScala.foldLeft(0)((sum, stream) => sum + drain(stream, 0))
    val wrote         = controlFrames + messages > 0
    if (wrote) out.flush()
    else if (running.get) LockSupport.parkNanos(10_000_000L) // idle: wait up to 10 ms or until woken
    if (running.get || wrote) loop()
  }

  @tailrec private def writeControlFrames(written: Int): Int =
    table.control.poll() match {
      case null  => written
      case frame =>
        Frame.write(out, frame)
        frame match {
          case Frame.Reset(id, _) => table.draining.remove(id) // drop whatever the reset stream still had queued
          case _                  => ()
        }
        writeControlFrames(written + 1)
    }

  @tailrec private def drain(stream: MessageStream, written: Int): Int =
    if (written == Batch) written
    else
      stream.takeOutbound() match {
        case Some(Message.Text(text)) =>
          Frame.write(out, Frame.Data(stream.id, text))
          drain(stream, written + 1)
        case Some(Message.End) =>
          Frame.write(out, Frame.End(stream.id))
          drain(stream, written + 1)
        case None =>
          written
        case _: MuxError => // closed, and its queue is empty: nothing more will come
          table.draining.remove(stream.id)
          written
      }
}
