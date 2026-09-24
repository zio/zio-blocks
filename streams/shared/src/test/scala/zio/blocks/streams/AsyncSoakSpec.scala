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

package zio.blocks.streams

import scala.concurrent.ExecutionContext
import java.util.concurrent.atomic.AtomicInteger
import zio._
import zio.blocks.async._
import zio.test._

/**
 * Release-only duration-and-operation floor. With no soak environment it is a
 * cheap sentinel; the release workflow supplies both immutable lower bounds.
 */
object AsyncSoakSpec extends StreamsBaseSpec {
  private implicit val executionContext: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private val hours      = sys.env.get("ASYNC_SOAK_MIN_HOURS").fold(0d)(_.toDouble)
  private val operations = sys.env.get("ASYNC_SOAK_MIN_OPERATIONS").fold(0L)(_.toLong)
  private val watchdog   = sys.env.get("ASYNC_SOAK_WATCHDOG_SECONDS").fold(60L)(_.toLong)
  private val batchSize  = 1000

  private def run[A](effect: Async[A]): Task[A] =
    ZIO
      .fromFuture(_ => effect.toFuture)
      .timeoutFail(new RuntimeException("async soak operation exceeded watchdog"))(watchdog.seconds)

  def spec = suite("async release soak")(
    test("satisfies both the configured wall-clock and completed-operation floors without leaking resources") {
      if (hours == 0d && operations == 0L) ZIO.succeed(assertTrue(true))
      else {
        val live      = new AtomicInteger(0)
        val finalized = new AtomicInteger(0)
        for {
          start <- Clock.nanoTime
          count <- {
            def loop(completed: Long): Task[Long] =
              Clock.nanoTime.flatMap { now =>
                val elapsedHours = (now - start).toDouble / 3600000000000d
                if (completed >= operations && elapsedHours >= hours) ZIO.succeed(completed)
                else {
                  val source = Stream.fromAcquireReleaseAsync(
                    Async.succeed { live.incrementAndGet(); () },
                    (_: Unit) => Async.succeed { live.decrementAndGet(); finalized.incrementAndGet(); () }
                  )(_ => Stream.range(0, batchSize))
                  run(
                    source
                      .mapParAsync(8)(value => Async.reschedule(() => Async.succeed(value + 1)))
                      .buffer(64)
                      .runFoldAsync(0L)((sum, value) => Async.succeed(sum + value.toLong))
                  ).flatMap {
                    case Right(sum) if sum == (1L to batchSize.toLong).sum => loop(completed + batchSize)
                    case result                                            => ZIO.fail(new AssertionError("invalid soak checksum: " + result))
                  }
                }
              }
            loop(0L)
          }
        } yield assertTrue(count >= operations, live.get() == 0, finalized.get() > 0)
      }
    }
  )
}
