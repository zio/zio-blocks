package writer

import zio.blocks.async.*
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Writer

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

/**
 * The `*Async` mirrors on `Writer`, and the two properties that define them:
 * each mirror defers exactly one synchronous writer operation until the
 * returned effect is driven, and cancellation closes the writer and suppresses
 * the stale result.
 *
 * Neither property makes a write nonblocking, and neither moves it to another
 * thread. The last section only observes cancellation at all because the
 * writer's own `close()` is what releases the parked write.
 *
 * JVM only, because it ends in `.block` to turn an `Async` into a value for
 * `main`.
 */
object WriterAsyncExample {

  /** Records what actually reached the writer, so deferral is observable. */
  final class RecordingWriter extends Writer[Int] {
    private val recorded       = scala.collection.mutable.ArrayBuffer.empty[Int]
    private var closed         = false
    def isClosed: Boolean      = closed
    def write(a: Int): Boolean = if (closed) false else { recorded += a; true }
    def close(): Unit          = closed = true
    def snapshot: List[Int]    = recorded.toList
  }

  /**
   * A writer whose `write` parks until someone closes it. `close()` is the
   * cancellation hook every `*Async` mirror installs, so cancelling a driven
   * `writeAsync` is what wakes this writer up again.
   */
  final class GatedWriter extends Writer[Int] {
    private val gate             = new CountDownLatch(1)
    private val recorded         = new ConcurrentLinkedQueue[Int]
    @volatile private var closed = false

    /** Counts down once the deferred thunk has entered `write`. */
    val entered = new CountDownLatch(1)

    /** Counts down once `close()` has run. */
    val wasClosed = new CountDownLatch(1)

    /** Counts down once the parked `write` has returned. */
    val finished = new CountDownLatch(1)

    def isClosed: Boolean = closed

    def write(a: Int): Boolean = {
      entered.countDown()
      gate.await()
      val accepted =
        if (closed) false
        else { recorded.add(a); true }
      finished.countDown()
      accepted
    }

    def close(): Unit = {
      closed = true
      gate.countDown()
      wasClosed.countDown()
    }

    def recordedCount: Int = recorded.size
  }

  def main(args: Array[String]): Unit = {
    deferral()
    sequence()
    cancellation()
  }

  /** Constructing a mirror writes nothing; driving it writes exactly once. */
  private def deferral(): Unit = {
    val writer  = new RecordingWriter
    val pending = writer.writeAsync(1)

    println(s"deferral: after construction  recorded=${writer.snapshot}")
    println(s"deferral: after driving once  accepted=${pending.block}, recorded=${writer.snapshot}")
  }

  /** The mirrors compose like any other `Async`, one operation per step. */
  private def sequence(): Unit = {
    val writer = new RecordingWriter

    val program: Async[Chunk[Int]] =
      writer
        .writeAsync(10)
        .flatMap(_ => writer.writeAllAsync(Chunk(20, 30, 40)))
        .flatMap(undelivered => writer.closeAsync().map(_ => undelivered))

    val undelivered = program.block
    println(s"sequence: recorded=${writer.snapshot}, undelivered=$undelivered, closed=${writer.isClosed}")
  }

  /**
   * Cancellation closes the writer and discards the result it was about to
   * produce.
   */
  private def cancellation(): Unit = {
    val writer  = new GatedWriter
    val running = writer.writeAsync(99).start

    // Wait until the deferred thunk is parked inside `write`, so cancellation
    // races a genuinely in-flight operation rather than an unstarted one.
    writer.entered.await()
    running.cancel()

    // Cancellation ran `close()`, which released the parked `write`.
    writer.wasClosed.await()
    writer.finished.await()

    // The `false` that `write` then returned lost the race to publish, so this
    // run never delivers a value. Never call `.block` on a cancelled handle.
    println(s"cancellation: closed=${writer.isClosed}, recorded=${writer.recordedCount}")
  }
}
