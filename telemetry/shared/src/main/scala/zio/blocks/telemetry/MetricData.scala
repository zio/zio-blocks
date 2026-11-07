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

/**
 * A value with its attributes. No instrument in this module produces or reads
 * it: observable instruments report values through `ObservableCallback#record`.
 *
 * @param value
 *   the measured value
 * @param attributes
 *   attributes describing the measurement context
 */
final case class Measurement(value: Double, attributes: Attributes)

/**
 * One series of a [[Counter]], [[UpDownCounter]], [[ObservableCounter]] or
 * [[ObservableUpDownCounter]] at collection time.
 *
 * @param attributes
 *   the attribute set of the series
 * @param startTimeNanos
 *   always `0`: no instrument records when its sum started
 * @param timeNanos
 *   the collection time, in epoch nanoseconds
 * @param value
 *   the total since the instrument was created, or, for an observable counter,
 *   the value the callback reported, rounded to a whole number
 */
final case class SumDataPoint(
  attributes: Attributes,
  startTimeNanos: Long,
  timeNanos: Long,
  value: Long
)

/**
 * One series of a [[Histogram]] at collection time, covering every value
 * recorded since the histogram was created. Each collection copies the bucket
 * counts and boundaries into new arrays.
 *
 * @param attributes
 *   the attribute set of the series
 * @param startTimeNanos
 *   always `0`: no histogram records when its series started
 * @param timeNanos
 *   the collection time, in epoch nanoseconds
 * @param count
 *   the number of recorded values
 * @param sum
 *   the sum of the recorded values
 * @param min
 *   the smallest recorded value, or `Double.MaxValue` when `count` is `0`
 * @param max
 *   the largest recorded value, or `Double.MinValue` when `count` is `0`
 * @param bucketCounts
 *   the number of values in each bucket; one more entry than `boundaries`, the
 *   last for values above the last boundary
 * @param boundaries
 *   the inclusive upper bound of each bucket but the last
 */
final case class HistogramDataPoint(
  attributes: Attributes,
  startTimeNanos: Long,
  timeNanos: Long,
  count: Long,
  sum: Double,
  min: Double,
  max: Double,
  bucketCounts: Array[Long],
  boundaries: Array[Double]
)

/**
 * One series of a [[Gauge]] or [[ObservableGauge]] at collection time.
 *
 * @param attributes
 *   the attribute set of the series
 * @param timeNanos
 *   the collection time, in epoch nanoseconds, not the time of the last
 *   `record`
 * @param value
 *   the last value recorded, or reported by the callback
 */
final case class GaugeDataPoint(
  attributes: Attributes,
  timeNanos: Long,
  value: Double
)

/**
 * The data of one instrument at one collection, as `collect()` and
 * `MetricReader#collectAllMetrics` return it: `SumData`, `HistogramData` or
 * `GaugeData`, after the kind of instrument.
 *
 * A synchronous instrument gives one point per attribute set it has seen,
 * including a set passed to `bind` that never recorded anything. An observable
 * instrument gives one point per value its callback reported. The data carry no
 * instrument name, description or unit.
 *
 * {{{
 * val queueDepth = MeterProvider.builder.build().get("worker").gaugeBuilder("queue.depth").build()
 * queueDepth.record(7.0, "queue" -> "orders")
 *
 * val latest = queueDepth.collect() match {
 *   case MetricData.GaugeData(points) => points.map(_.value)
 *   case MetricData.SumData(_)        => Nil
 *   case MetricData.HistogramData(_)  => Nil
 * }
 * latest.headOption // Some(7.0)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/metric-data/ MetricData]]
 */
sealed trait MetricData

/** The three kinds of data. */
object MetricData {

  /**
   * The data of a [[Counter]], [[UpDownCounter]], [[ObservableCounter]] or
   * [[ObservableUpDownCounter]].
   */
  final case class SumData(points: List[SumDataPoint]) extends MetricData

  /** The data of a [[Histogram]]. */
  final case class HistogramData(points: List[HistogramDataPoint]) extends MetricData

  /** The data of a [[Gauge]] or [[ObservableGauge]]. */
  final case class GaugeData(points: List[GaugeDataPoint]) extends MetricData
}
