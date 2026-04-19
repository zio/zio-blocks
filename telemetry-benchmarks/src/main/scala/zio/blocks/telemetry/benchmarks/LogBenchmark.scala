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

package zio.blocks.telemetry.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import zio.blocks.telemetry.{Measurement => _, _}

import java.util.concurrent.TimeUnit

private class CountingProcessor extends LogRecordProcessor {
  @volatile var count: Long = 0L
  override def onEmit(logRecord: LogRecord): Unit = count += 1
  override def shutdown(): Unit                   = ()
  override def forceFlush(): Unit                 = ()
}

@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
class LogBenchmark {

  private val cachedThrowable: Throwable = {
    val t = new RuntimeException("benchmark error")
    t.setStackTrace(Array(new StackTraceElement("Bench", "run", "Bench.scala", 1)))
    t
  }

  private var sideEffectCounter: Int                    = 0
  private var countingProcessor: CountingProcessor = scala.compiletime.uninitialized

  private def sideEffect(): String = {
    sideEffectCounter += 1
    "x"
  }

  @Setup(Level.Trial)
  def setup(): Unit = {
    countingProcessor = new CountingProcessor
    val provider = LoggerProvider.builder
      .addLogRecordProcessor(countingProcessor)
      .build()
    val logger = provider.get("benchmark")
    GlobalLogState.install(logger, Severity.Info)
    GlobalLogState.setLevel("zio.blocks.telemetry.benchmarks", Severity.Info)
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    GlobalLogState.uninstall()

  @Benchmark
  def baseline(bh: Blackhole): Unit =
    bh.consume(GlobalLogState.get())

  @Benchmark
  def disabledLevel(): Unit =
    log.trace("skip")

  /** Disabled level with enrichments — verify args aren't evaluated */
  @Benchmark
  def disabledWithEnrichments(): Unit =
    log.trace("skip", "key" -> 42L, "other" -> "val")

  /**
   * Lazy message: string interpolation should not happen when level is
   * disabled.
   */
  @Benchmark
  def disabledWithInterpolation(bh: Blackhole): Unit = {
    sideEffectCounter = 0
    log.trace(s"count is ${sideEffect()}")
    bh.consume(sideEffectCounter)
  }

  @Benchmark
  def enabledSimple(bh: Blackhole): Unit = {
    log.info("msg")
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def enabledOneAttribute(bh: Blackhole): Unit = {
    log.info("msg", "key" -> 42L)
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def enabledThreeAttributes(bh: Blackhole): Unit = {
    log.info("msg", "a" -> 1L, "b" -> "x", "c" -> true)
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def enabledWithThrowable(bh: Blackhole): Unit = {
    log.info("err", cachedThrowable)
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def enabledWithAnnotations(bh: Blackhole): Unit = {
    log.annotated("rid" -> "123") {
      log.info("msg")
    }
    bh.consume(countingProcessor.count)
  }

  /** Multiple nested annotation scopes */
  @Benchmark
  def enabledNestedAnnotations(bh: Blackhole): Unit = {
    log.annotated("a" -> "1") {
      log.annotated("b" -> "2") {
        log.info("msg")
      }
    }
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def enabledWithHierarchicalLevel(bh: Blackhole): Unit = {
    log.info("msg")
    bh.consume(countingProcessor.count)
  }

  /** Rate-limited: infoEvery — only logs every Nth call */
  @Benchmark
  def rateLimitedEvery(bh: Blackhole): Unit = {
    log.infoEvery(100, "rate limited msg")
    bh.consume(countingProcessor.count)
  }

  /** Rate-limited: infoAtMost — at most once per interval */
  @Benchmark
  def rateLimitedAtMost(bh: Blackhole): Unit = {
    log.infoAtMost(1000L, "throttled msg")
    bh.consume(countingProcessor.count)
  }

  @Benchmark
  def systemNanoTime(bh: Blackhole): Unit =
    bh.consume(System.nanoTime())

  @Benchmark
  def systemCurrentTimeMillis(bh: Blackhole): Unit =
    bh.consume(System.currentTimeMillis())
}
