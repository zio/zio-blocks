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
 * Reports a total that rises and falls and that you read rather than add to,
 * such as the connections a pool holds: its callback runs on each collection
 * and reports the current value of each attribute set through an
 * [[ObservableCallback]]. Create and register one with
 * `UpDownCounterBuilder#buildWithCallback`. The value is rounded to a whole
 * number with `Math.round`, so `1.5` becomes `2`. `name`, `description` and
 * `unit` return what it was created with.
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/meter/ Meter]]
 */
final class ObservableUpDownCounter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

  /**
   * Runs the callback once, on the calling thread, and returns a
   * [[MetricData.SumData]] with one point per value it reported, with the
   * current time and a start time of `0`. An exception from the callback
   * propagates.
   */
  def collect(): MetricData = {
    val measurements = new java.util.ArrayList[SumDataPoint]()
    val now          = EpochClock.epochNanos()
    val cb           = new ObservableCallback {
      def record(value: Double, attributes: Attributes): Unit = {
        measurements.add(SumDataPoint(attributes, 0L, now, Math.round(value)))
        ()
      }
    }
    callback(cb)
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(measurements))
  }
}

/** Creates ObservableUpDownCounters that no meter registers. */
object ObservableUpDownCounter {

  /**
   * Creates one that no meter registers, so `MetricReader#collectAllMetrics`
   * never runs its callback; use `UpDownCounterBuilder#buildWithCallback` for a
   * registered one.
   */
  def apply(name: String, description: String, unit: String)(
    callback: ObservableCallback => Unit
  ): ObservableUpDownCounter =
    new ObservableUpDownCounter(name, description, unit, callback)
}
