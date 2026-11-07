package nio

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.AsyncNioReaders

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousByteChannel, AsynchronousCloseException, CompletionHandler}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CompletableFuture, CountDownLatch, Future}

/**
 * Managed and unmanaged readers over an `AsynchronousByteChannel` (JVM only).
 *
 * `AsyncNioReaders.fromChannel` takes ownership of the channel and closes it
 * when the reader closes. `AsyncNioReaders.fromChannelUnmanaged` releases the
 * reader and leaves the channel alive for its owner. Both memoize `close()`,
 * and both settle a read that is still in flight when the reader closes.
 *
 * The channel below is scripted rather than networked: it counts its own
 * `close()` calls and parks a read that has nothing left to deliver, so
 * ownership, idempotent close, and the cancelled pending read are all
 * observable without a socket.
 */
object AsyncChannelReaderExample {
  def main(args: Array[String]): Unit = {
    managedClosesTheChannel()
    unmanagedLeavesTheChannelOpen()
    closeCancelsAPendingRead()
  }

  /** Managed ownership: the reader closes the channel, and only once. */
  private def managedClosesTheChannel(): Unit = {
    val channel = new ScriptedChannel("async".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannel(channel, bufferSize = 16)

    val bytes: Chunk[Byte] = reader.readN[Byte](5).block
    reader.close().block
    reader.close().block // memoized: the channel is not closed a second time

    println(
      s"managed   -> read=${text(bytes)}, channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  /** Unmanaged ownership: the channel outlives the reader untouched. */
  private def unmanagedLeavesTheChannelOpen(): Unit = {
    val channel = new ScriptedChannel("async".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 16)

    val bytes: Chunk[Byte] = reader.readN[Byte](5).block
    reader.close().block
    reader.close().block

    println(
      s"unmanaged -> read=${text(bytes)}, channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  /**
   * Closing an unmanaged reader cancels the read the channel is still holding
   * and hands the consumer the end-of-stream answer instead.
   */
  private def closeCancelsAPendingRead(): Unit = {
    val channel = new ScriptedChannel("hi".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 16)

    val delivered: Chunk[Byte] = reader.readN[Byte](2).block
    val pending                = reader.readByte().start

    // Wait until the channel is genuinely holding a read, so the close races an
    // in-flight operation rather than an unstarted one.
    channel.readParked.await()
    reader.close().block

    println(
      s"pending   -> read=${text(delivered)}, cancelledRead=${pending.block}, " +
        s"channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  private def text(bytes: Chunk[Byte]): String = new String(bytes.toArray, UTF_8)

  /**
   * A channel that delivers `payload` once and then parks every further read
   * until it is closed, the way a live socket waits between packets.
   */
  private final class ScriptedChannel(payload: Array[Byte]) extends AsynchronousByteChannel {
    val closeCount: AtomicInteger  = new AtomicInteger
    val readParked: CountDownLatch = new CountDownLatch(1)

    private val lock                      = new AnyRef
    private var position                  = 0
    private var open                      = true
    private var parked: Throwable => Unit = null

    def read[A](dst: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit = {
      val served = lock.synchronized {
        val outcome = serve(dst)
        if (outcome.isEmpty) parked = cause => handler.failed(cause, attachment)
        outcome
      }
      served match {
        case Some(count) => handler.completed(count, attachment)
        case None        => readParked.countDown()
      }
    }

    def read(dst: ByteBuffer): Future[Integer] = {
      val future = new CompletableFuture[Integer]
      val served = lock.synchronized {
        val outcome = serve(dst)
        if (outcome.isEmpty) parked = cause => { future.completeExceptionally(cause); () }
        outcome
      }
      served match {
        case Some(count) => future.complete(count)
        case None        => readParked.countDown()
      }
      future
    }

    def write[A](src: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit =
      handler.failed(new UnsupportedOperationException("scripted channel is read-only"), attachment)

    def write(src: ByteBuffer): Future[Integer] = {
      val future = new CompletableFuture[Integer]
      future.completeExceptionally(new UnsupportedOperationException("scripted channel is read-only"))
      future
    }

    def isOpen: Boolean = lock.synchronized(open)

    def close(): Unit = {
      val release = lock.synchronized {
        closeCount.incrementAndGet()
        open = false
        val current = parked
        parked = null
        current
      }
      if (release ne null) release(new AsynchronousCloseException)
    }

    private def serve(dst: ByteBuffer): Option[Int] = {
      val remaining = payload.length - position
      if (remaining <= 0) None
      else {
        val count = math.min(remaining, dst.remaining)
        dst.put(payload, position, count)
        position += count
        Some(count)
      }
    }
  }
}
