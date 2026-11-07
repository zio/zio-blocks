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
 * Reports a value that you read at collection time, such as the size of a
 * cache: its callback runs on each collection and reports the current value of
 * each attribute set through an [[ObservableCallback]]. Create and register one
 * with `GaugeBuilder#buildWithCallback`. `name`, `description` and `unit`
 * return what it was created with.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/meter/ Meter]]
 */
final class ObservableGauge private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

  /**
   * Runs the callback once, on the calling thread, and returns a
   * [[MetricData.GaugeData]] with one point per value it reported, with the
   * current time. An exception from the callback propagates.
   */
  def collect(): MetricData = {
    val measurements = new java.util.ArrayList[GaugeDataPoint]()
    val now          = EpochClock.epochNanos()
    val cb           = new ObservableCallback {
      def record(value: Double, attributes: Attributes): Unit = {
        measurements.add(GaugeDataPoint(attributes, now, value))
        ()
      }
    }
    callback(cb)
    MetricData.GaugeData(SyncInstrumentsHelper.listFromJava(measurements))
  }
}

/** Creates ObservableGauges that no meter registers. */
object ObservableGauge {

  /**
   * Creates one that no meter registers, so `MetricReader#collectAllMetrics`
   * never runs its callback; use `GaugeBuilder#buildWithCallback` for a
   * registered one.
   */
  def apply(name: String, description: String, unit: String)(
    callback: ObservableCallback => Unit
  ): ObservableGauge =
    new ObservableGauge(name, description, unit, callback)
}
