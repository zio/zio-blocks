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

package zio.blocks.otel

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed trait ExportResult

object ExportResult {
  case object Success                                           extends ExportResult
  final case class Failure(retryable: Boolean, message: String) extends ExportResult
}

final class BatchProcessor[A](
  exportFn: Seq[A] => ExportResult,
  executor: ScheduledExecutorService,
  maxQueueSize: Int = 2048,
  maxBatchSize: Int = 512,
  flushIntervalMillis: Long = 5000,
  maxRetries: Int = 5,
  retryBaseMillis: Long = 1000L
) {
  private val queue: ConcurrentLinkedQueue[A] = new ConcurrentLinkedQueue[A]()
  private val queueSize: AtomicInteger        = new AtomicInteger(0)
  private val isShutdown: AtomicBoolean       = new AtomicBoolean(false)

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
            "[zio-blocks-otel] BatchProcessor queue full (" + maxQueueSize + "). Dropping oldest item."
          )
        }
      }
    }

  def forceFlush(): Unit = doFlush()

  def shutdown(): Unit =
    if (isShutdown.compareAndSet(false, true)) {
      scheduledFuture.cancel(false)
      doFlush()
    }

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
    exportFn(batch) match {
      case ExportResult.Success                     => ()
      case ExportResult.Failure(retryable, message) =>
        if (!retryable) {
          System.err.println(
            "[zio-blocks-otel] BatchProcessor export failed (non-retryable): " + message + ". Dropping " + batch.size + " items."
          )
        } else if (attempt >= maxRetries) {
          System.err.println(
            "[zio-blocks-otel] BatchProcessor export failed after " + (attempt + 1) + " attempts: " + message + ". Dropping " + batch.size + " items."
          )
        } else {
          val delayMs = math.min(retryBaseMillis * (1L << attempt), 30000L)
          try Thread.sleep(delayMs)
          catch { case _: InterruptedException => Thread.currentThread().interrupt() }
          exportWithRetry(batch, attempt + 1)
        }
    }
}
