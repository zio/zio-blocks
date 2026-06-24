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

package zio.blocks.telemetry

import zio.test._

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._

object TelemetrySafetySpec extends ZIOSpecDefault {
  private def sampleSpanData(name: String): SpanData =
    SpanData(
      name = name,
      kind = SpanKind.Internal,
      spanContext = SpanContext.invalid,
      parentSpanContext = SpanContext.invalid,
      startTimeNanos = 1L,
      endTimeNanos = 2L,
      attributes = Attributes.empty,
      events = Nil,
      links = Nil,
      status = SpanStatus.Ok,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope("test")
    )

  def spec = suite("TelemetrySafety")(
    test("FileLogWriter writes complete lines under concurrent logging") {
      val path            = Files.createTempFile("file-log-writer", ".log")
      val writer          = FileLogWriter(path, append = false, bufferSize = 64)
      val threadCount     = 4
      val writesPerThread = 200
      val startLatch      = new CountDownLatch(1)
      val payloads        = (0 until threadCount).map(i => s"thread-$i-${"x" * 80}").toVector
      val threads         = payloads.map { payload =>
        new Thread(() => {
          startLatch.await()
          var i = 0
          while (i < writesPerThread) {
            writer.write(payload)
            i += 1
          }
        })
      }

      try {
        threads.foreach(_.start())
        startLatch.countDown()
        threads.foreach(_.join())
        writer.close()

        val lines  = Files.readAllLines(path).asScala.toVector
        val counts = lines.groupBy(identity).view.mapValues(_.size).toMap

        assertTrue(
          lines.size == threadCount * writesPerThread,
          lines.forall(payloads.contains),
          payloads.forall(payload => counts.getOrElse(payload, 0) == writesPerThread)
        )
      } finally Files.deleteIfExists(path)
    },
    test("LogRateLimit allows only one winner under contention") {
      val siteId      = 777
      val threadCount = 32
      val startLatch  = new CountDownLatch(1)
      val doneLatch   = new CountDownLatch(threadCount)
      val successes   = new AtomicInteger(0)

      val threads = (0 until threadCount).map { _ =>
        new Thread(() => {
          startLatch.await()
          if (LogRateLimit.shouldLogAtMost(siteId, 10000L)) successes.incrementAndGet()
          doneLatch.countDown()
        })
      }

      threads.foreach(_.start())
      startLatch.countDown()
      doneLatch.await()
      threads.foreach(_.join())

      assertTrue(successes.get() == 1)
    },
    test("InMemorySpanProcessor handles Int.MinValue write index") {
      val processor = new InMemorySpanProcessor(capacity = 8)
      val field     = processor.getClass.getDeclaredField("writeIndex")
      field.setAccessible(true)
      field.get(processor).asInstanceOf[AtomicInteger].set(Int.MinValue)

      val span = sampleSpanData("wrapped-span")
      processor.onEnd(span)

      assertTrue(processor.collectedSpans.contains(span))
    }
  ) @@ TestAspect.sequential
}
