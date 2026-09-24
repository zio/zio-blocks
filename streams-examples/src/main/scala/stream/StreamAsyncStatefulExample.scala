package stream

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/**
 * The stateful asynchronous operators in one pipeline: `takeWhileAsync`,
 * `mapAccumAsync`, `scanAsync`, and `ensuringAsync`.
 *
 * A day's ledger feed is cut at its end-of-day marker, numbered, folded into
 * running balances, and closed by an asynchronous finalizer. Every step
 * suspends on another thread, and every step is still sequential: at most one
 * invocation of each callback is active at a time, so the accumulator is never
 * shared across concurrent work.
 *
 * JVM only: the closing `.block` parks the calling thread until the `Async`
 * completes. On Scala.js, keep the `Async` and let the host drive it.
 */
object StreamAsyncStatefulExample {
  final case class Posting(seq: Long, amount: Int)

  /** Completes on another thread, so each callback below really suspends. */
  def deferred[A](value: => A): Async[A] = {
    val completer = new Completer[A]
    val thread    = new Thread(() => completer.succeed(value))
    thread.start()
    completer
  }

  def main(args: Array[String]): Unit = {
    val sessionsClosed = new AtomicInteger

    // `0` is the end-of-day marker, not an amount. Everything after it belongs
    // to the next day and must not be posted.
    val feed: Stream[Nothing, Int] = Stream(120, -40, 75, 0, 999)

    val balances: Stream[Nothing, Long] = feed
      // Stops at the marker and closes upstream, so `999` is never pulled.
      .takeWhileAsync(amount => deferred(amount != 0))
      // Threads a sequence number through while numbering each entry.
      .mapAccumAsync(0L)((seq, amount) => deferred((seq + 1, Posting(seq + 1, amount))))
      // Emits the accumulator at each step, starting with `0L`, so the output
      // carries one more element than the input.
      .scanAsync(0L)((balance, posting) => deferred(balance + posting.amount))
      // Awaited exactly once when the materialized stream closes.
      .ensuringAsync(deferred { sessionsClosed.incrementAndGet(); () })

    val result: Either[Nothing, Chunk[Long]] = balances.runCollectAsync.block

    require(result == Right(Chunk(0L, 120L, 80L, 155L)), s"unexpected balances: $result")
    require(sessionsClosed.get() == 1, s"finalizer ran ${sessionsClosed.get()} times")

    println(s"running balances -> $result")
    println(s"sessions closed  -> ${sessionsClosed.get()}")
  }
}
