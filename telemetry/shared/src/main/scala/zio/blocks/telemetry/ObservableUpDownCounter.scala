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

final class ObservableUpDownCounter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

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

object ObservableUpDownCounter {
  def apply(name: String, description: String, unit: String)(
    callback: ObservableCallback => Unit
  ): ObservableUpDownCounter =
    new ObservableUpDownCounter(name, description, unit, callback)
}
