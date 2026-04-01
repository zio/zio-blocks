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

package zio.blocks.otel

/**
 * A counter with pre-declared label names. Label values are validated at
 * runtime to match the expected arity.
 *
 * @param counter
 *   the underlying counter instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledCounter private[otel] (
  private val counter: Counter,
  val labelNames: Array[String]
) {

  def add(value: Long, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    counter.add(value, attributes)
  }

  def bind(labelValues: Any*): BoundCounter = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    counter.bind(attributes)
  }

  def collect(): MetricData = counter.collect()
}

/**
 * A histogram with pre-declared label names. Label values are validated at
 * runtime to match the expected arity.
 *
 * @param histogram
 *   the underlying histogram instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledHistogram private[otel] (
  private val histogram: Histogram,
  val labelNames: Array[String]
) {

  def record(value: Double, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    histogram.record(value, attributes)
  }

  def bind(labelValues: Any*): BoundHistogram = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    histogram.bind(attributes)
  }

  def collect(): MetricData = histogram.collect()
}

/**
 * A gauge with pre-declared label names. Label values are validated at runtime
 * to match the expected arity.
 *
 * @param gauge
 *   the underlying gauge instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledGauge private[otel] (
  private val gauge: Gauge,
  val labelNames: Array[String]
) {

  def record(value: Double, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    gauge.record(value, attributes)
  }

  def bind(labelValues: Any*): BoundGauge = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    gauge.bind(attributes)
  }

  def collect(): MetricData = gauge.collect()
}

private[otel] object LabeledInstrumentsHelper {

  def buildAttributes(labelNames: Array[String], labelValues: Seq[Any]): Attributes = {
    val builder = Attributes.builder
    var i       = 0
    while (i < labelNames.length) {
      labelValues(i) match {
        case s: String  => builder.put(labelNames(i), s)
        case l: Long    => builder.put(labelNames(i), l)
        case i2: Int    => builder.put(labelNames(i), i2.toLong)
        case d: Double  => builder.put(labelNames(i), d)
        case b: Boolean => builder.put(labelNames(i), b)
        case other      => builder.put(labelNames(i), other.toString)
      }
      i += 1
    }
    builder.build
  }
}
