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
 * A single measurement observation with a value and associated attributes.
 *
 * @param value
 *   the measured value
 * @param attributes
 *   attributes describing the measurement context
 */
final case class Measurement(value: Double, attributes: Attributes)

/**
 * A data point for sum-based metrics (Counter, UpDownCounter).
 *
 * @param attributes
 *   attributes identifying this time series
 * @param startTimeNanos
 *   epoch nanoseconds when the aggregation period started
 * @param timeNanos
 *   epoch nanoseconds when this snapshot was taken
 * @param value
 *   the aggregated sum value
 */
final case class SumDataPoint(
  attributes: Attributes,
  startTimeNanos: Long,
  timeNanos: Long,
  value: Long
)

/**
 * A data point for histogram metrics.
 *
 * @param attributes
 *   attributes identifying this time series
 * @param startTimeNanos
 *   epoch nanoseconds when the aggregation period started
 * @param timeNanos
 *   epoch nanoseconds when this snapshot was taken
 * @param count
 *   total number of recorded values
 * @param sum
 *   sum of all recorded values
 * @param min
 *   minimum recorded value
 * @param max
 *   maximum recorded value
 * @param bucketCounts
 *   counts for each bucket (length = boundaries.length + 1)
 * @param boundaries
 *   upper exclusive boundaries for each bucket
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
 * A data point for gauge metrics.
 *
 * @param attributes
 *   attributes identifying this time series
 * @param timeNanos
 *   epoch nanoseconds when this value was observed
 * @param value
 *   the current gauge value
 */
final case class GaugeDataPoint(
  attributes: Attributes,
  timeNanos: Long,
  value: Double
)

/**
 * Aggregated metric data collected from an instrument. Each variant holds a
 * list of data points, one per unique attribute set observed.
 */
sealed trait MetricData

object MetricData {

  /**
   * Aggregated sum data from a Counter or UpDownCounter.
   */
  final case class SumData(points: List[SumDataPoint]) extends MetricData

  /**
   * Aggregated histogram data from a Histogram instrument.
   */
  final case class HistogramData(points: List[HistogramDataPoint]) extends MetricData

  /**
   * Aggregated gauge data from a Gauge instrument.
   */
  final case class GaugeData(points: List[GaugeDataPoint]) extends MetricData
}
