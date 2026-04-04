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
 * Callback interface for observable instruments to report measurements during
 * collection.
 */
trait ObservableCallback {

  /**
   * Records a measurement value with associated attributes.
   */
  def record(value: Double, attributes: Attributes): Unit
}

/**
 * An observable counter that invokes a callback on each collection to report
 * cumulative monotonic sum values.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 * @param callback
 *   function invoked on each collect to report current measurements
 */
final class ObservableCounter(
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

  /**
   * Collects current measurements by invoking the registered callback.
   */
  def collect(): MetricData = {
    val measurements = new java.util.ArrayList[SumDataPoint]()
    val now          = System.nanoTime()
    val cb           = new ObservableCallback {
      def record(value: Double, attributes: Attributes): Unit = {
        measurements.add(SumDataPoint(attributes, 0L, now, value.toLong))
        ()
      }
    }
    callback(cb)
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(measurements))
  }
}

object ObservableCounter {
  def apply(name: String, description: String, unit: String)(
    callback: ObservableCallback => Unit
  ): ObservableCounter =
    new ObservableCounter(name, description, unit, callback)
}

/**
 * An observable up-down counter that invokes a callback on each collection to
 * report cumulative non-monotonic sum values.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 * @param callback
 *   function invoked on each collect to report current measurements
 */
final class ObservableUpDownCounter(
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

  /**
   * Collects current measurements by invoking the registered callback.
   */
  def collect(): MetricData = {
    val measurements = new java.util.ArrayList[SumDataPoint]()
    val now          = System.nanoTime()
    val cb           = new ObservableCallback {
      def record(value: Double, attributes: Attributes): Unit = {
        measurements.add(SumDataPoint(attributes, 0L, now, value.toLong))
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

/**
 * An observable gauge that invokes a callback on each collection to report
 * current point-in-time values.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 * @param callback
 *   function invoked on each collect to report current measurements
 */
final class ObservableGauge(
  val name: String,
  val description: String,
  val unit: String,
  private val callback: ObservableCallback => Unit
) {

  /**
   * Collects current measurements by invoking the registered callback.
   */
  def collect(): MetricData = {
    val measurements = new java.util.ArrayList[GaugeDataPoint]()
    val now          = System.nanoTime()
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

object ObservableGauge {
  def apply(name: String, description: String, unit: String)(
    callback: ObservableCallback => Unit
  ): ObservableGauge =
    new ObservableGauge(name, description, unit, callback)
}
