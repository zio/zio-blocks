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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The latest value of something, such as a temperature or the size of a cache.
 * `record` replaces the value of the series of its attributes; `collect`
 * returns the last value per attribute set.
 *
 * Create one with `Meter#gaugeBuilder` or `metric.gauge`, which register it.
 * `Gauge(name, description, unit)` creates one that nothing registers: only its
 * own `collect` reads it. For a value read at collection time, use
 * `GaugeBuilder#buildWithCallback`. `name`, `description` and `unit` return
 * what the gauge was created with.
 *
 * {{{
 * val temperature = MeterProvider.builder.build().get("com.example.sensors").gaugeBuilder("cpu.temperature").build()
 *
 * temperature.record(72.5, "core" -> 0)
 * temperature.record(74.1, "core" -> 0)
 *
 * val latest = temperature.collect() match {
 *   case MetricData.GaugeData(points) => points.map(_.value)
 *   case _                            => Nil
 * }
 * latest.headOption // Some(74.1)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/instruments/ Instruments]]
 */
final class Gauge private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
) {
  private val values = new ConcurrentHashMap[Attributes, AtomicLong]()

  /** Replaces the value of the series of `attributes` with `value`. */
  def record(value: Double, attributes: Attributes): Unit = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    ref.set(java.lang.Double.doubleToRawLongBits(value))
  }

  /**
   * Replaces the value of the series of the pairs `attrs`, or of the empty
   * attribute set when there are none, with `value`. A value that is a
   * `String`, `Long`, `Int` (stored as a `Long`), `Double` or `Boolean` keeps
   * its type; any other value is stored as its `toString`, so `1.5f` becomes
   * the string `1.5`. The order of the pairs does not matter.
   */
  def record(value: Double, attrs: (String, Any)*): Unit = {
    val attributes =
      if (attrs.isEmpty) Attributes.empty
      else SyncInstrumentsHelper.buildPooledAttributes(attrs)

    record(value, attributes)
  }

  /**
   * Returns a [[BoundGauge]] that records into the series of `attributes`
   * without looking it up on each call. The series exists from this call on, so
   * `collect` reports it with `0.0` until something is recorded.
   */
  def bind(attributes: Attributes): BoundGauge = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    new BoundGauge(ref)
  }

  /**
   * Returns a [[MetricData.GaugeData]] with one point per attribute set: the
   * last recorded value and the current time.
   */
  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[GaugeDataPoint]()
    values.forEach { (attrs, ref) =>
      points.add(GaugeDataPoint(attrs, now, java.lang.Double.longBitsToDouble(ref.get())))
    }
    MetricData.GaugeData(SyncInstrumentsHelper.listFromJava(points))
  }
}

/** Creates gauges that no meter registers. */
object Gauge {

  /**
   * Creates a gauge that no meter registers: `MetricReader#collectAllMetrics`
   * never sees it, so read it with `collect()`. Use `Meter#gaugeBuilder` or
   * `metric.gauge` for a registered one.
   */
  def apply(name: String, description: String, unit: String): Gauge =
    new Gauge(name, description, unit)
}

/**
 * One series of a [[Gauge]], with its attributes fixed by `Gauge#bind` or
 * `LabeledGauge#bind`.
 */
final class BoundGauge private[telemetry] (private val ref: AtomicLong) {

  /** Replaces the value of the series with `value`. */
  def record(value: Double): Unit = ref.set(java.lang.Double.doubleToRawLongBits(value))
}
