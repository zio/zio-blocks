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
import zio.blocks.telemetry.{Measurement => _, _}
import scribe.file.string2PathBuilder

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

/**
 * Head-to-head benchmark: zio-blocks-telemetry vs Scribe. Both write to files
 * using their native file writers. 1000 messages per invocation (matches
 * Scribe's own benchmark).
 *
 * Run: sbt 'otelBenchmarks/Jmh/run -i 5 -wi 5 -f 3 -t 1 -prof gc
 * .*HeadToHead.*'
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime, Mode.Throughput))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class HeadToHeadBenchmark {
  private val Iterations: Int         = 1000
  private var messages: Array[String] = uninitialized

  // --- zio-blocks-telemetry setup ---
  private var otelTmpDir: Path          = uninitialized
  private var otelLogFile: Path         = uninitialized
  private var otelWriter: FileLogWriter = uninitialized

  // --- Scribe setup ---
  private var scribeTmpDir: Path          = uninitialized
  private var scribeLogFile: Path         = uninitialized
  private var scribeLogger: scribe.Logger = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    // Pre-build messages like Scribe's benchmark
    messages = (0 until Iterations).map(i => s"Test $i").toArray

    // Create temp directories for log files
    otelTmpDir = Files.createTempDirectory("bench-otel")
    scribeTmpDir = Files.createTempDirectory("bench-scribe")

    otelLogFile = otelTmpDir.resolve("otel.log")
    scribeLogFile = scribeTmpDir.resolve("scribe.log")

    // --- zio-blocks-telemetry: install with TextLogFormatter + FileLogWriter ---
    otelWriter = FileLogWriter(otelLogFile)
    val otelProvider = LoggerProvider.builder
      .addLogRecordProcessor(
        new FormattedLogRecordProcessor(TextLogFormatter, otelWriter)
      )
      .build()
    GlobalLogState.install(otelProvider.get("bench"), Severity.Info)
    GlobalLogState.setLevel("zio.blocks.telemetry.benchmarks", Severity.Info)

    // --- Scribe: setup with FileWriter ---
    val builder = scribe.Logger()
    scribeLogger = builder
      .clearHandlers()
      .withHandler(
        formatter = scribe.format.Formatter.simple,
        writer = scribe.file.FileWriter(scribeLogFile.toString)
      )
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = {
    GlobalLogState.uninstall()
    if (otelWriter != null) otelWriter.close()

    // Clean up temp files
    if (otelTmpDir != null) {
      try {
        Files.deleteIfExists(otelLogFile)
        Files.deleteIfExists(otelTmpDir)
      } catch { case _: Throwable => () }
    }
    if (scribeTmpDir != null) {
      try {
        Files.deleteIfExists(scribeLogFile)
        Files.deleteIfExists(scribeTmpDir)
      } catch { case _: Throwable => () }
    }
  }

  // ===== zio-blocks-telemetry benchmark =====

  @Benchmark
  @OperationsPerInvocation(1000)
  def withZioBlocksOtel(): Unit = {
    var i = 0
    while (i < Iterations) {
      log.info(messages(i))
      i += 1
    }
  }

  // ===== Scribe benchmark =====

  @Benchmark
  @OperationsPerInvocation(1000)
  def withScribe(): Unit = {
    var i = 0
    while (i < Iterations) {
      scribeLogger.info(messages(i))
      i += 1
    }
  }
}
