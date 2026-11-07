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
import java.util.concurrent.atomic.LongAdder

/**
 * A total that only grows, such as requests served or bytes sent. `add` adds an
 * amount to the series of its attributes and ignores a negative amount;
 * `collect` returns one total per attribute set.
 *
 * Create one with `Meter#counterBuilder` or `metric.counter`, which register
 * it, so that the provider's reader collects it.
 * `Counter(name, description, unit)` creates one that nothing registers: only
 * its own `collect` reads it. `name`, `description` and `unit` return what the
 * counter was created with.
 *
 * {{{
 * val requests = MeterProvider.builder.build().get("com.example.api").counterBuilder("http.requests").build()
 *
 * requests.add(1L, "method" -> "GET", "status" -> "200")
 * requests.add(2L, "status" -> "200", "method" -> "GET")
 * requests.add(-1L, "method" -> "GET", "status" -> "200")
 *
 * val totals = requests.collect() match {
 *   case MetricData.SumData(points) => points.map(_.value)
 *   case _                          => Nil
 * }
 * totals.headOption // Some(3)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/instruments/ Instruments]]
 */
final class Counter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
) {
  private val adders = new ConcurrentHashMap[Attributes, LongAdder]()

  /**
   * Adds `value` to the series of `attributes`; a negative `value` is ignored.
   */
  def add(value: Long, attributes: Attributes): Unit =
    if (value >= 0) {
      var adder = adders.get(attributes)
      if (adder == null) {
        adder = new LongAdder()
        val existing = adders.putIfAbsent(attributes, adder)
        if (existing != null) adder = existing
      }
      adder.add(value)
    }

  /**
   * Adds `value` to the series of the pairs `attrs`, or of the empty attribute
   * set when there are none; a negative `value` is ignored. A value that is a
   * `String`, `Long`, `Int` (stored as a `Long`), `Double` or `Boolean` keeps
   * its type; any other value is stored as its `toString`, so `1.5f` becomes
   * the string `1.5`. The order of the pairs does not matter.
   */
  def add(value: Long, attrs: (String, Any)*): Unit =
    if (value >= 0) {
      val attributes =
        if (attrs.isEmpty) Attributes.empty
        else SyncInstrumentsHelper.buildPooledAttributes(attrs)

      add(value, attributes)
    }

  /**
   * Returns a [[BoundCounter]] that adds to the series of `attributes` without
   * looking it up on each call. The series exists from this call on, so
   * `collect` reports it with `0` until something is added.
   */
  def bind(attributes: Attributes): BoundCounter = {
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    new BoundCounter(adder)
  }

  /**
   * Returns a [[MetricData.SumData]] with one point per attribute set: the
   * total since the counter was created, the current time, and a start time of
   * `0`.
   */
  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[SumDataPoint]()
    adders.forEach { (attrs, adder) =>
      points.add(SumDataPoint(attrs, 0L, now, adder.sum()))
    }
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(points))
  }
}

/** Creates counters that no meter registers. */
object Counter {

  /**
   * Creates a counter that no meter registers: `MetricReader#collectAllMetrics`
   * never sees it, so read it with `collect()`. Use `Meter#counterBuilder` or
   * `metric.counter` for a registered one.
   */
  def apply(name: String, description: String, unit: String): Counter =
    new Counter(name, description, unit)
}

/**
 * One series of a [[Counter]], with its attributes fixed by `Counter#bind` or
 * `LabeledCounter#bind`.
 */
final class BoundCounter private[telemetry] (private val adder: LongAdder) {

  /** Adds `value` to the series; a negative `value` is ignored. */
  def add(value: Long): Unit = if (value >= 0) adder.add(value)
}
