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

package zio.blocks.telemetry.otel

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private[otel] final class BatchProcessor[A](
  exportFn: Seq[A] => ExportResult,
  executor: ScheduledExecutorService,
  maxQueueSize: Int = 2048,
  maxBatchSize: Int = 512,
  flushIntervalMillis: Long = 5000,
  maxRetries: Int = 5,
  retryBaseMillis: Long = 1000L
) extends AutoCloseable {
  private val queue: ConcurrentLinkedQueue[A] = new ConcurrentLinkedQueue[A]()
  private val queueSize: AtomicInteger        = new AtomicInteger(0)
  private val isShutdown: AtomicBoolean       = new AtomicBoolean(false)

  // Monitor for the retry backoff wait. shutdown() notifies it so a flush
  // parked in backoff wakes immediately instead of sleeping out the delay —
  // the scheduler thread is never blocked longer than necessary and shutdown
  // still flushes promptly.
  private val retryMonitor = new Object

  private val flushTask: Runnable = new Runnable {
    def run(): Unit = doFlush()
  }

  private val scheduledFuture: ScheduledFuture[_] =
    executor.scheduleAtFixedRate(flushTask, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS)

  def enqueue(item: A): Unit =
    if (!isShutdown.get()) {
      queue.add(item)
      val size = queueSize.incrementAndGet()
      if (size > maxQueueSize) {
        val removed = queue.poll()
        if (removed != null) {
          queueSize.decrementAndGet()
          System.err.println(
            "[zio-blocks-telemetry] BatchProcessor queue full (" + maxQueueSize + "). Dropping oldest item."
          )
        }
      }
    }

  def forceFlush(): Unit = doFlush()

  def shutdown(): Unit = {
    // The flag flip and the wake-up are atomic under retryMonitor, so a
    // flush thread checking awaitBackoff cannot slip between them and miss
    // the notify (which would park it for the full backoff delay).
    val shouldFlush = retryMonitor.synchronized {
      if (isShutdown.compareAndSet(false, true)) {
        retryMonitor.notifyAll()
        true
      } else false
    }
    if (shouldFlush) {
      scheduledFuture.cancel(false)
      // Wake any flush parked in retry backoff so it stops retrying now;
      // the doFlush below then drains whatever is left in the queue.
      doFlush()
    }
  }

  override def close(): Unit = shutdown()

  private def doFlush(): Unit = {
    var hasMore = true
    while (hasMore) {
      val batch = drain(maxBatchSize)
      hasMore = batch.nonEmpty
      if (hasMore) exportWithRetry(batch, 0)
    }
  }

  private def drain(max: Int): Seq[A] = {
    val builder = Seq.newBuilder[A]
    var count   = 0
    while (count < max) {
      val item = queue.poll()
      if (item == null) {
        count = max // exit loop
      } else {
        builder += item
        queueSize.decrementAndGet()
        count += 1
      }
    }
    builder.result()
  }

  private def exportWithRetry(batch: Seq[A], attempt: Int): Unit =
    (try exportFn(batch)
    catch {
      case e if scala.util.control.NonFatal(e) =>
        ExportResult.Failure(retryable = true, message = e.getMessage)
    }) match {
      case ExportResult.Success                     => ()
      case ExportResult.Failure(retryable, message) =>
        if (!retryable) {
          System.err.println(
            "[zio-blocks-telemetry] BatchProcessor export failed (non-retryable): " + message + ". Dropping " + batch.size + " items."
          )
        } else if (attempt >= maxRetries) {
          System.err.println(
            "[zio-blocks-telemetry] BatchProcessor export failed after " + (attempt + 1) + " attempts: " + message + ". Dropping " + batch.size + " items."
          )
        } else if (isShutdown.get()) {
          System.err.println(
            "[zio-blocks-telemetry] BatchProcessor shutting down, not retrying. Dropping " + batch.size + " items."
          )
        } else {
          val shift   = math.min(attempt, 30)
          val delayMs = math.min(retryBaseMillis * (1L << shift), 30000L)
          if (awaitBackoff(delayMs)) exportWithRetry(batch, attempt + 1)
          else {
            System.err.println(
              "[zio-blocks-telemetry] BatchProcessor shutting down, not retrying. Dropping " + batch.size + " items."
            )
          }
        }
    }

  /**
   * Waits out the retry backoff, returning `false` early (without sleeping the
   * full delay) once shutdown has started. The check/wait runs under the same
   * `retryMonitor` as `shutdown`'s flag flip plus notify, so the wake-up cannot
   * be missed; the deadline loop additionally guards against spurious wakeups
   * by re-waiting for only the time that is left.
   */
  private def awaitBackoff(delayMs: Long): Boolean =
    retryMonitor.synchronized {
      if (isShutdown.get()) false
      else {
        val deadlineMs  = System.currentTimeMillis() + delayMs
        var remainingMs = delayMs
        while (!isShutdown.get() && remainingMs > 0) {
          try retryMonitor.wait(remainingMs)
          catch {
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
              remainingMs = 0
          }
          if (remainingMs > 0) remainingMs = deadlineMs - System.currentTimeMillis()
        }
        !isShutdown.get()
      }
    }
}
