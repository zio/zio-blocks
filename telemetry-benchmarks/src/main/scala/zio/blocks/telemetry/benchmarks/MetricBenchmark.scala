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
import scala.compiletime.uninitialized

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
class MetricBenchmark {

  private var counter: Counter               = uninitialized
  private var histogram: Histogram           = uninitialized
  private var gauge: Gauge                   = uninitialized
  private var boundCounter: BoundCounter     = uninitialized
  private var boundHistogram: BoundHistogram = uninitialized
  private var boundGauge: BoundGauge         = uninitialized
  private var prebuiltAttrs: Attributes      = uninitialized
  private var prebuiltAttrs2: Attributes     = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val meterProvider = MeterProvider.builder.build()
    val meter         = meterProvider.get("benchmark")
    counter = meter.counterBuilder("bench.counter").build()
    histogram = meter.histogramBuilder("bench.histogram").build()
    gauge = meter.gaugeBuilder("bench.gauge").build()

    prebuiltAttrs = Attributes.builder
      .put("method", "GET")
      .put("path", "/api/users")
      .put("status", 200L)
      .build

    prebuiltAttrs2 = Attributes.builder
      .put("method", "GET")
      .put("path", "/api/users")
      .put("status", 200L)
      .build

    boundCounter = counter.bind(prebuiltAttrs)
    boundHistogram = histogram.bind(prebuiltAttrs)
    boundGauge = gauge.bind(prebuiltAttrs)
  }

  // --- Counter benchmarks ---

  /** Counter with pre-built Attributes — measures ConcurrentHashMap lookup */
  @Benchmark
  def counterWithPrebuiltAttrs(): Unit =
    counter.add(1L, prebuiltAttrs)

  /**
   * Counter with varargs key-value pairs — measures Attributes construction +
   * lookup
   */
  @Benchmark
  def counterWithVarargs(): Unit =
    counter.add(1L, "method" -> "GET", "path" -> "/api/users", "status" -> 200L)

  /** Bound counter — measures direct LongAdder.add(), no lookup */
  @Benchmark
  def counterBound(): Unit =
    boundCounter.add(1L)

  // --- Histogram benchmarks ---

  /** Histogram with pre-built Attributes */
  @Benchmark
  def histogramWithPrebuiltAttrs(): Unit =
    histogram.record(42.5, prebuiltAttrs)

  /** Bound histogram — direct recording, no lookup */
  @Benchmark
  def histogramBound(): Unit =
    boundHistogram.record(42.5)

  // --- Gauge benchmarks ---

  /** Gauge with pre-built Attributes */
  @Benchmark
  def gaugeWithPrebuiltAttrs(): Unit =
    gauge.record(99.9, prebuiltAttrs)

  /** Bound gauge — direct AtomicReference.set(), no lookup */
  @Benchmark
  def gaugeBound(): Unit =
    boundGauge.record(99.9)

  // --- Attributes construction ---

  /** Measures raw Attributes builder cost */
  @Benchmark
  def attributesBuild3(bh: Blackhole): Unit =
    bh.consume(
      Attributes.builder
        .put("method", "GET")
        .put("path", "/api/users")
        .put("status", 200L)
        .build
    )

  /** Attributes builder with 1 attribute */
  @Benchmark
  def attributesBuild1(bh: Blackhole): Unit =
    bh.consume(Attributes.builder.put("method", "GET").build)

  /** Attributes builder with 10 attributes */
  @Benchmark
  def attributesBuild10(bh: Blackhole): Unit =
    bh.consume(
      Attributes.builder
        .put("a1", "v1")
        .put("a2", 2L)
        .put("a3", 3.0)
        .put("a4", true)
        .put("a5", "v5")
        .put("a6", 6L)
        .put("a7", 7.0)
        .put("a8", false)
        .put("a9", "v9")
        .put("a10", 10L)
        .build
    )

  /** Isolated Attributes hashCode computation */
  @Benchmark
  def attributesHashCode(bh: Blackhole): Unit =
    bh.consume(prebuiltAttrs.hashCode())

  /** Isolated Attributes equals */
  @Benchmark
  def attributesEquals(bh: Blackhole): Unit =
    bh.consume(prebuiltAttrs == prebuiltAttrs2)
}
