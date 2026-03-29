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

package zio.blocks.otel.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import zio.blocks.otel.{Measurement => _, _}

import java.util.concurrent.TimeUnit

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

  @Setup(Level.Trial)
  def setup(): Unit = {
    val provider = LoggerProvider.builder
      .addLogRecordProcessor(LogRecordProcessor.noop)
      .build()
    val logger = provider.get("benchmark")
    GlobalLogState.install(logger, Severity.Info)
    GlobalLogState.setLevel("zio.blocks.otel.benchmarks", Severity.Info)
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

  @Benchmark
  def enabledSimple(): Unit =
    log.info("msg")

  @Benchmark
  def enabledOneAttribute(): Unit =
    log.info("msg", "key" -> 42L)

  @Benchmark
  def enabledThreeAttributes(): Unit =
    log.info("msg", "a" -> 1L, "b" -> "x", "c" -> true)

  @Benchmark
  def enabledWithThrowable(): Unit =
    log.info("err", cachedThrowable)

  @Benchmark
  def enabledWithAnnotations(): Unit =
    log.annotated("rid" -> "123") {
      log.info("msg")
    }

  /** Multiple nested annotation scopes */
  @Benchmark
  def enabledNestedAnnotations(): Unit =
    log.annotated("a" -> "1") {
      log.annotated("b" -> "2") {
        log.info("msg")
      }
    }

  @Benchmark
  def enabledWithHierarchicalLevel(): Unit =
    log.info("msg")
}
