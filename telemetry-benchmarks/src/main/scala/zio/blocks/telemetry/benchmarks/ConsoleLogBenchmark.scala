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
import scala.compiletime.uninitialized

@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
class ConsoleLogBenchmark {

  private var sb: StringBuilder                     = uninitialized
  private var builder: Attributes.AttributesBuilder = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()))
    val provider = LoggerProvider.builder
      .addLogRecordProcessor(new ConsoleLogRecordProcessor)
      .build()
    GlobalLogState.install(provider.get("bench"), Severity.Info)
    GlobalLogState.setLevel("zio.blocks.telemetry.benchmarks", Severity.Info)
    sb = new StringBuilder(256)
    builder = Attributes.builder
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    GlobalLogState.uninstall()
    System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)))
  }

  @Benchmark
  def enabledSimpleConsole(): Unit =
    log.info("request processed")

  @Benchmark
  def enabledWithAttributesConsole(): Unit =
    log.info("request", "userId" -> 42L, "method" -> "GET")

  @Benchmark
  def textFormatterOnly(bh: Blackhole): Unit = {
    sb.setLength(0)
    builder.put("code.namespace", "com.example.MyClass")
    builder.put("code.function", "handle")
    builder.put("code.lineno", 42L)
    builder.put("code.column", 1L)
    TextLogFormatter.format(
      sb,
      System.nanoTime(),
      Severity.Info,
      "INFO",
      "request processed",
      builder,
      0L,
      0L,
      0L,
      0,
      None
    )
    bh.consume(sb.length)
    builder.clear()
  }

  @Benchmark
  def jsonFormatterOnly(bh: Blackhole): Unit = {
    sb.setLength(0)
    builder.put("code.namespace", "com.example.MyClass")
    builder.put("code.function", "handle")
    builder.put("code.lineno", 42L)
    builder.put("code.column", 1L)
    JsonLogFormatter.format(
      sb,
      System.nanoTime(),
      Severity.Info,
      "INFO",
      "request processed",
      builder,
      0L,
      0L,
      0L,
      0,
      None
    )
    bh.consume(sb.length)
    builder.clear()
  }
}
