/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams.internal

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.streams.Platform
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingSpscQueue

import java.util.concurrent.atomic.AtomicLong

/**
 * A concurrent buffered reader. The producer runs on a separate virtual thread
 * (or daemon thread) and feeds elements into a [[BlockingSpscQueue]]; the
 * consumer pulls from the queue.
 *
 * Null elements from upstream are transparently encoded as [[NullSentinel]] to
 * distinguish them from the queue's null-means-closed signal and the
 * [[EndOfStream]] completion sentinel.
 *
 * Thread ownership: the producer thread exclusively owns `upstream`. The
 * consumer (this reader) never calls `upstream.read()` or `upstream.close()`.
 */
private[streams] final class ConcurrentBufferedReader[A](upstream: Reader[A], bufferSize: Int) extends Reader[A] {
  import ConcurrentBufferedReader._

  // `queue` and `producerThread` are reassigned on `reset()` so the buffer can
  // replay a resettable upstream (e.g. under `repeated`). All such reassignment
  // happens on the consumer thread only after the previous producer has fully
  // terminated (see `reset()`), so single-threaded mutation is safe there.
  private var queue: BlockingSpscQueue[AnyRef]   = new BlockingSpscQueue[AnyRef](bufferSize)
  @volatile private var producerError: Throwable = null
  @volatile private var errorDelivered: Boolean  = false
  @volatile private var consumerClosed: Boolean  = false
  @volatile private var producerDone: Boolean    = false
  private var upstreamClosedByProducer: Boolean  = false

  private val producerTask: Runnable = new Runnable {
    def run(): Unit =
      try {
        var running = true
        while (running && !consumerClosed && !queue.isClosed && !Thread.currentThread().isInterrupted) {
          val v = upstream.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) {
            queue.offer(EndOfStream)
            running = false
          } else {
            val wrapped = if (v == null) NullSentinel else v.asInstanceOf[AnyRef]
            if (!queue.offer(wrapped)) {
              running = false
            }
          }
        }
      } catch {
        case _: InterruptedException if consumerClosed =>
          // An interruptible upstream (e.g. a real blocking I/O read) reacts
          // to our own stop-signal interrupt by throwing from read() itself,
          // one call site earlier than the join hazard above. `consumerClosed`
          // being set here can only mean we induced this: nobody outside this
          // class holds the producer thread handle. Recording it as
          // `producerError` would surface deliberate shutdown as a failure.
          queue.offer(EndOfStream)
        case t: Throwable =>
          producerError = t
          queue.offer(EndOfStream)
      } finally {
        queue.close()
        if (!upstreamClosedByProducer) {
          upstreamClosedByProducer = true
          // Consume our own stop-signal interrupt before cascading into
          // `upstream.close()`. At this point the flag can only mean "we told
          // this thread to stop" — that has been honored, so there is nothing
          // left to restore. Left set, it would leak into whatever blocking
          // work `upstream.close()` does — `joinProducer` below already
          // guards the nested-`ConcurrentBufferedReader` case specifically,
          // but `upstream` may be any `Reader` with its own interrupt-sensitive
          // shutdown path.
          Thread.interrupted()
          // A close failure must surface (Principle 4): record it (without
          // displacing an earlier read error) so the consumer rethrows it from
          // read() or close().
          try upstream.close()
          catch { case t: Throwable => if (producerError eq null) producerError = t }
        }
        producerDone = true
      }
  }

  private var producerThread: Thread = spawnProducer()

  private def spawnProducer(): Thread =
    Platform.startVirtualThread(
      s"zio-blocks-buffer-${ConcurrentBufferedReader.counter.getAndIncrement()}",
      producerTask
    )

  def isClosed: Boolean = producerDone && queue.isEmpty

  def read[A1 >: A](sentinel: A1): A1 = {
    val result = queue.take()
    result match {
      case null =>
        val err = producerError
        if (err ne null) rethrow(err)
        sentinel
      case r if r eq EndOfStream =>
        val err = producerError
        if (err ne null) rethrow(err)
        sentinel
      case r if r eq NullSentinel =>
        null.asInstanceOf[A1]
      case r =>
        r.asInstanceOf[A1]
    }
  }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val first = queue.take()
    first match {
      case null =>
        val err = producerError
        if (err ne null) rethrow(err)
        Chunk.empty
      case r if r eq EndOfStream =>
        val err = producerError
        if (err ne null) rethrow(err)
        Chunk.empty
      case r if r eq NullSentinel =>
        if (n == 1) Chunk.single(null.asInstanceOf[A1])
        else {
          val b = ChunkBuilder.make[A1](math.min(n, 16))
          b += null.asInstanceOf[A1]
          drainQueue(b, n - 1)
          b.result()
        }
      case r =>
        if (n == 1) Chunk.single(r.asInstanceOf[A1])
        else {
          val b = ChunkBuilder.make[A1](math.min(n, 16))
          b += r.asInstanceOf[A1]
          drainQueue(b, n - 1)
          b.result()
        }
    }
  }

  private def drainQueue[A1 >: A](b: ChunkBuilder[A1], limit: Int): Unit = {
    var i = 0
    while (i < limit) {
      val v = queue.poll()
      v match {
        case null                   => return
        case r if r eq EndOfStream  => return
        case r if r eq NullSentinel => b += null.asInstanceOf[A1]; i += 1
        case r                      => b += r.asInstanceOf[A1]; i += 1
      }
    }
  }

  def close(): Unit = {
    consumerClosed = true
    queue.close()
    producerThread.interrupt()
    joinProducer()
    // A recorded error the consumer never observed via a read (e.g. an
    // upstream close failure after the last element) must still surface
    // (Principle 4): rethrow it exactly once at teardown.
    val err = producerError
    if ((err ne null) && !errorDelivered) { errorDelivered = true; rethrow(err) }
  }

  // `Thread.join` throws InterruptedException based on the *calling* thread's
  // own interrupt status, checked unconditionally before it even looks at
  // whether the joined thread is alive. The caller here is not always an
  // innocent bystander: closing a nested buffer runs on the outer producer's
  // own thread (from its `finally`, above), and that thread was very likely
  // just interrupted itself to unwind its own read loop. A single
  // clear-before/join/restore-after is not enough on its own, either: the
  // three statements in `close()`/`reset()` above (`consumerClosed = true`,
  // `queue.close()`, `producerThread.interrupt()`) are not atomic with the
  // producer's own progress, so the interrupt can just as easily land *while*
  // this join is already parked as before it starts. Either way, treating
  // that as "the producer failed" is wrong — it's this reader's own
  // stop-signal. Retry across the full timeout budget, consuming (not
  // propagating) every InterruptedException the wait itself produces, then
  // restore the calling thread's flag once at the end so a genuine,
  // unrelated interrupt on the caller is never silently dropped.
  private def joinProducer(): Unit = {
    val deadline          = System.nanoTime() + JoinTimeoutNanos
    var remainingNanos    = JoinTimeoutNanos
    var callerInterrupted = Thread.interrupted()
    while (!producerDone && remainingNanos > 0) {
      try producerThread.join(math.max(1L, remainingNanos / 1000000L))
      catch { case _: InterruptedException => callerInterrupted = true }
      remainingNanos = deadline - System.nanoTime()
    }
    if (callerInterrupted) Thread.currentThread().interrupt()
  }

  override def reset(): Unit = {
    // `buffer` is a pure decoupling transform: it must not weaken replayability.
    // 1) Fully terminate the current producer so no thread touches `upstream` or
    //    the fields below. `Thread.join` establishes happens-before with the
    //    producer's termination, making the subsequent single-threaded mutation
    //    (including the non-volatile `upstreamClosedByProducer`) safe.
    consumerClosed = true
    queue.close()
    producerThread.interrupt()
    joinProducer()
    // 2) Replay the upstream. A genuine one-shot source throws
    //    UnsupportedOperationException here, which correctly propagates: a buffer
    //    over a one-shot source is itself one-shot. (The producer already closed
    //    `upstream` in its finally block; resettable readers re-enable reads.)
    upstream.reset()
    // 3) Reinstate fresh producer state and restart the producer.
    queue = new BlockingSpscQueue[AnyRef](bufferSize)
    producerError = null
    errorDelivered = false
    consumerClosed = false
    producerDone = false
    upstreamClosedByProducer = false
    producerThread = spawnProducer()
  }

  private def rethrow(t: Throwable): Nothing = {
    errorDelivered = true
    t match {
      case se: StreamError => throw se
      case _               => throw t
    }
  }
}

private object ConcurrentBufferedReader {
  val counter: AtomicLong  = new AtomicLong(0L)
  val NullSentinel: AnyRef = new AnyRef

  /** Upper bound on how long `close()`/`reset()` wait for the producer. */
  val JoinTimeoutNanos: Long = 5000L * 1000000L
}
