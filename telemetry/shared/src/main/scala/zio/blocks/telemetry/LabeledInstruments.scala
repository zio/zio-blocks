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
 * A [[Counter]] whose label names are fixed, so each call passes only the
 * values, in the same order: `add(1L, "GET", "200")`. Get one from
 * `Meter#labeledCounter`, which registers the counter.
 *
 * Only the number of values is checked, when recording: values in the wrong
 * order record a wrong series without an error. It is not faster than the pair
 * form of `Counter#add`; for a hot path, use `bind` once and keep the
 * [[BoundCounter]].
 *
 * {{{
 * val requests = MeterProvider.builder.build().get("com.example.api").labeledCounter("http.requests", "method", "status")
 *
 * requests.add(1L, "GET", "200")
 * requests.add(1L, "GET", "200")
 *
 * val totals = requests.collect() match {
 *   case MetricData.SumData(points) => points.map(_.value)
 *   case _                          => Nil
 * }
 * totals.headOption // Some(2)
 * }}}
 *
 * @param counter
 *   the underlying counter instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledCounter private[telemetry] (
  private val counter: Counter,
  val labelNames: Array[String]
) {

  /**
   * Adds `value` to the series whose attributes pair each label name with the
   * value in the same position; a negative `value` is ignored. Values are
   * converted as `Counter#add` converts the values of its pairs.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def add(value: Long, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    counter.add(value, attributes)
  }

  /**
   * Returns a [[BoundCounter]] for the series of `labelValues`, matched to the
   * label names by position.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def bind(labelValues: Any*): BoundCounter = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    counter.bind(attributes)
  }

  /** Returns the data of the wrapped counter, as `Counter#collect` does. */
  def collect(): MetricData = counter.collect()
}

/**
 * A [[Histogram]] whose label names are fixed, so each call passes only the
 * values, in the same order. Get one from `Meter#labeledHistogram`, which
 * registers the histogram with the default boundaries. Only the number of
 * values is checked: values in the wrong order record a wrong series without an
 * error.
 *
 * @param histogram
 *   the underlying histogram instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledHistogram private[telemetry] (
  private val histogram: Histogram,
  val labelNames: Array[String]
) {

  /**
   * Adds `value` to the series whose attributes pair each label name with the
   * value in the same position. Values are converted as `Counter#add` converts
   * the values of its pairs.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def record(value: Double, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    histogram.record(value, attributes)
  }

  /**
   * Returns a [[BoundHistogram]] for the series of `labelValues`, matched to
   * the label names by position.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def bind(labelValues: Any*): BoundHistogram = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    histogram.bind(attributes)
  }

  /** Returns the data of the wrapped histogram, as `Histogram#collect` does. */
  def collect(): MetricData = histogram.collect()
}

/**
 * A [[Gauge]] whose label names are fixed, so each call passes only the values,
 * in the same order. Get one from `Meter#labeledGauge`, which registers the
 * gauge. Only the number of values is checked: values in the wrong order record
 * a wrong series without an error.
 *
 * @param gauge
 *   the underlying gauge instrument
 * @param labelNames
 *   the declared label names, in order
 */
final class LabeledGauge private[telemetry] (
  private val gauge: Gauge,
  val labelNames: Array[String]
) {

  /**
   * Replaces the value of the series whose attributes pair each label name with
   * the value in the same position. Values are converted as `Counter#add`
   * converts the values of its pairs.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def record(value: Double, labelValues: Any*): Unit = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    gauge.record(value, attributes)
  }

  /**
   * Returns a [[BoundGauge]] for the series of `labelValues`, matched to the
   * label names by position.
   *
   * @throws java.lang.IllegalArgumentException
   *   when the number of values differs from the number of label names, with
   *   the message `requirement failed: Expected 2 label values, got 1` for two
   *   names and one value
   */
  def bind(labelValues: Any*): BoundGauge = {
    require(
      labelValues.length == labelNames.length,
      s"Expected ${labelNames.length} label values, got ${labelValues.length}"
    )
    val attributes = LabeledInstrumentsHelper.buildAttributes(labelNames, labelValues)
    gauge.bind(attributes)
  }

  /** Returns the data of the wrapped gauge, as `Gauge#collect` does. */
  def collect(): MetricData = gauge.collect()
}

private[telemetry] object LabeledInstrumentsHelper {

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
