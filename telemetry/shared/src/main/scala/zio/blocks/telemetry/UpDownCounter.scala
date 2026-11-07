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
 * A total that rises and falls, such as open connections or the depth of a
 * queue. `add` adds an amount, negative or not, to the series of its
 * attributes; `collect` returns one total per attribute set.
 *
 * Create one with `Meter#upDownCounterBuilder` or `metric.upDownCounter`, which
 * register it. `UpDownCounter(name, description, unit)` creates one that
 * nothing registers: only its own `collect` reads it. `name`, `description` and
 * `unit` return what it was created with.
 *
 * {{{
 * val connections = MeterProvider.builder.build().get("com.example.server").upDownCounterBuilder("connections.open").build()
 *
 * connections.add(3L, "pool" -> "primary")
 * connections.add(-1L, "pool" -> "primary")
 *
 * val open = connections.collect() match {
 *   case MetricData.SumData(points) => points.map(_.value)
 *   case _                          => Nil
 * }
 * open.headOption // Some(2)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/instruments/ Instruments]]
 */
final class UpDownCounter private[telemetry] (
  val name: String,
  val description: String,
  val unit: String
) {
  private val adders = new ConcurrentHashMap[Attributes, LongAdder]()

  /** Adds `value`, which may be negative, to the series of `attributes`. */
  def add(value: Long, attributes: Attributes): Unit = {
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    adder.add(value)
  }

  /**
   * Adds `value`, which may be negative, to the series of the pairs `attrs`, or
   * of the empty attribute set when there are none. A value that is a `String`,
   * `Long`, `Int` (stored as a `Long`), `Double` or `Boolean` keeps its type;
   * any other value is stored as its `toString`, so `1.5f` becomes the string
   * `1.5`. The order of the pairs does not matter.
   */
  def add(value: Long, attrs: (String, Any)*): Unit = {
    val attributes =
      if (attrs.isEmpty) Attributes.empty
      else SyncInstrumentsHelper.buildPooledAttributes(attrs)

    add(value, attributes)
  }

  /**
   * Returns a [[BoundUpDownCounter]] that adds to the series of `attributes`
   * without looking it up on each call. The series exists from this call on, so
   * `collect` reports it with `0` until something is added.
   */
  def bind(attributes: Attributes): BoundUpDownCounter = {
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    new BoundUpDownCounter(adder)
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

/** Creates up-down counters that no meter registers. */
object UpDownCounter {

  /**
   * Creates an up-down counter that no meter registers:
   * `MetricReader#collectAllMetrics` never sees it, so read it with
   * `collect()`. Use `Meter#upDownCounterBuilder` or `metric.upDownCounter` for
   * a registered one.
   */
  def apply(name: String, description: String, unit: String): UpDownCounter =
    new UpDownCounter(name, description, unit)
}

/**
 * One series of an [[UpDownCounter]], with its attributes fixed by
 * `UpDownCounter#bind`.
 */
final class BoundUpDownCounter private[telemetry] (private val adder: LongAdder) {

  /** Adds `value`, which may be negative, to the series. */
  def add(value: Long): Unit = adder.add(value)
}
