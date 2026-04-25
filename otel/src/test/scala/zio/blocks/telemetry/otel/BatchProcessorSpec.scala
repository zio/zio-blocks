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

import zio.blocks.telemetry._
import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object BatchProcessorSpec extends ZIOSpecDefault {

  private def collectingExporter[A](): (Seq[A] => ExportResult, AtomicReference[List[Seq[A]]]) = {
    val captured                   = new AtomicReference[List[Seq[A]]](Nil)
    val fn: Seq[A] => ExportResult = { batch =>
      var done = false
      while (!done) {
        val current = captured.get()
        done = captured.compareAndSet(current, current :+ batch)
      }
      ExportResult.Success
    }
    (fn, captured)
  }

  def spec: Spec[Any, Nothing] = suite("BatchProcessor")(
    test("enqueue items and forceFlush delivers them to exportFn") {
      val pe                   = PlatformExecutor.create()
      val (exportFn, captured) = collectingExporter[String]()
      val processor            =
        new BatchProcessor[String](exportFn, executor = pe.executor, flushIntervalMillis = 600000L)
      try {
        processor.enqueue("a")
        processor.enqueue("b")
        processor.enqueue("c")
        processor.forceFlush()
        val batches = captured.get()
        assertTrue(batches.flatten.toSet == Set("a", "b", "c"))
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("exports in batches of maxBatchSize when more items than maxBatchSize") {
      val pe                   = PlatformExecutor.create()
      val (exportFn, captured) = collectingExporter[Int]()
      val processor            =
        new BatchProcessor[Int](exportFn, executor = pe.executor, maxBatchSize = 3, flushIntervalMillis = 600000L)
      try {
        (1 to 7).foreach(processor.enqueue)
        processor.forceFlush()
        val batches = captured.get()
        assertTrue(
          batches.length >= 3 &&
            batches.forall(_.size <= 3) &&
            batches.flatten.toSet == (1 to 7).toSet
        )
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("drops oldest items when queue exceeds maxQueueSize") {
      val pe                   = PlatformExecutor.create()
      val (exportFn, captured) = collectingExporter[Int]()
      val processor            =
        new BatchProcessor[Int](
          exportFn,
          executor = pe.executor,
          maxQueueSize = 5,
          maxBatchSize = 100,
          flushIntervalMillis = 600000L
        )
      try {
        (1 to 10).foreach(processor.enqueue)
        processor.forceFlush()
        val items = captured.get().flatten
        assertTrue(items.size <= 5)
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("retries on retryable failure up to maxRetries") {
      val pe                                    = PlatformExecutor.create()
      val attempts                              = new AtomicInteger(0)
      val exportFn: Seq[String] => ExportResult = { _ =>
        val n = attempts.incrementAndGet()
        if (n < 3) ExportResult.Failure(retryable = true, message = s"fail $n")
        else ExportResult.Success
      }
      val processor =
        new BatchProcessor[String](
          exportFn,
          executor = pe.executor,
          maxRetries = 5,
          flushIntervalMillis = 600000L,
          retryBaseMillis = 1L
        )
      try {
        processor.enqueue("x")
        processor.forceFlush()
        assertTrue(attempts.get() == 3)
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("drops batch on non-retryable failure without retry") {
      val pe                                    = PlatformExecutor.create()
      val attempts                              = new AtomicInteger(0)
      val exportFn: Seq[String] => ExportResult = { _ =>
        attempts.incrementAndGet()
        ExportResult.Failure(retryable = false, message = "fatal")
      }
      val processor =
        new BatchProcessor[String](exportFn, executor = pe.executor, flushIntervalMillis = 600000L)
      try {
        processor.enqueue("x")
        processor.forceFlush()
        assertTrue(attempts.get() == 1)
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("drops batch after max retries exceeded") {
      val pe                                    = PlatformExecutor.create()
      val attempts                              = new AtomicInteger(0)
      val exportFn: Seq[String] => ExportResult = { _ =>
        attempts.incrementAndGet()
        ExportResult.Failure(retryable = true, message = "always fails")
      }
      val processor =
        new BatchProcessor[String](
          exportFn,
          executor = pe.executor,
          maxRetries = 3,
          flushIntervalMillis = 600000L,
          retryBaseMillis = 1L
        )
      try {
        processor.enqueue("x")
        processor.forceFlush()
        assertTrue(attempts.get() == 4) // 1 initial + 3 retries
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    },
    test("shutdown flushes remaining items") {
      val pe                   = PlatformExecutor.create()
      val (exportFn, captured) = collectingExporter[String]()
      val processor            =
        new BatchProcessor[String](exportFn, executor = pe.executor, flushIntervalMillis = 600000L)
      processor.enqueue("a")
      processor.enqueue("b")
      processor.shutdown()
      pe.shutdown()
      val items = captured.get().flatten
      assertTrue(items.toSet == Set("a", "b"))
    },
    test("background flush triggers periodically") {
      val pe                                           = PlatformExecutor.create()
      val latch                                        = new CountDownLatch(1)
      val (exportFn, captured)                         = collectingExporter[String]()
      val wrappedExportFn: Seq[String] => ExportResult = { batch =>
        val result = exportFn(batch)
        latch.countDown()
        result
      }
      val processor =
        new BatchProcessor[String](wrappedExportFn, executor = pe.executor, flushIntervalMillis = 50L)
      try {
        processor.enqueue("auto")
        val flushed = latch.await(2, TimeUnit.SECONDS)
        val items   = captured.get().flatten
        assertTrue(flushed && items.contains("auto"))
      } finally {
        processor.shutdown()
        pe.shutdown()
      }
    }
  ) @@ TestAspect.sequential
}
