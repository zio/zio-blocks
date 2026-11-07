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
import java.util.concurrent.locks.ReentrantLock

/**
 * The distribution of a value, such as request latency or payload size.
 * `record` adds a value to the series of its attributes; `collect` returns, per
 * attribute set, the count, sum, minimum, maximum and the number of values in
 * each bucket.
 *
 * Bucket `i` holds the values above `boundaries(i - 1)` and at most
 * `boundaries(i)`, and one last bucket holds the values above the last
 * boundary. The default boundaries are
 * `0, 5, 10, 25, 50, 75, 100, 250, 500, 750, 1000, 2500, 5000, 7500, 10000`, so
 * the first bucket holds the values at most `0`.
 *
 * `Meter#histogramBuilder` and `metric.histogram` create a registered histogram
 * with the default boundaries. `Histogram(name, description, unit, boundaries)`
 * takes other boundaries, but nothing registers that histogram: only its own
 * `collect` reads it. `name`, `description`, `unit` and `boundaries` return
 * what the histogram was created with.
 *
 * {{{
 * val latency = Histogram("http.latency", "Request latency", "ms", Array(10.0, 50.0, 100.0))
 *
 * latency.record(10.0, "route" -> "/orders")
 * latency.record(42.0, "route" -> "/orders")
 * latency.record(250.0, "route" -> "/orders")
 *
 * val buckets = latency.collect() match {
 *   case MetricData.HistogramData(points) => points.map(_.bucketCounts.toList)
 *   case _                                => Nil
 * }
 * buckets.headOption // Some(List(1, 1, 0, 1))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/metrics/instruments/ Instruments]]
 */
final class Histogram private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  val boundaries: Array[Double]
) {
  private val states = new ConcurrentHashMap[Attributes, Histogram.State]()

  /** Adds `value` to the series of `attributes`. */
  def record(value: Double, attributes: Attributes): Unit = {
    var state = states.get(attributes)
    if (state == null) {
      state = new Histogram.State(boundaries.length + 1)
      val existing = states.putIfAbsent(attributes, state)
      if (existing != null) state = existing
    }
    recordInternal(value, state)
  }

  /**
   * Adds `value` to the series of the pairs `attrs`, or of the empty attribute
   * set when there are none. A value that is a `String`, `Long`, `Int` (stored
   * as a `Long`), `Double` or `Boolean` keeps its type; any other value is
   * stored as its `toString`, so `1.5f` becomes the string `1.5`. The order of
   * the pairs does not matter.
   */
  def record(value: Double, attrs: (String, Any)*): Unit = {
    val attributes =
      if (attrs.isEmpty) Attributes.empty
      else SyncInstrumentsHelper.buildPooledAttributes(attrs)

    record(value, attributes)
  }

  /**
   * Returns a [[BoundHistogram]] that adds to the series of `attributes`
   * without looking it up on each call. The series exists from this call on:
   * until a value arrives, `collect` reports it with a count of `0`, a `min` of
   * `Double.MaxValue` and a `max` of `Double.MinValue`.
   */
  def bind(attributes: Attributes): BoundHistogram = {
    var state = states.get(attributes)
    if (state == null) {
      state = new Histogram.State(boundaries.length + 1)
      val existing = states.putIfAbsent(attributes, state)
      if (existing != null) state = existing
    }
    new BoundHistogram(state, this)
  }

  private[telemetry] def recordInternal(value: Double, state: Histogram.State): Unit = {
    state.lock.lock()
    try {
      state.count += 1
      state.sum += value
      if (value < state.min) state.min = value
      if (value > state.max) state.max = value
      val idx = findBucketIndex(value)
      state.bucketCounts(idx) += 1
    } finally {
      state.lock.unlock()
    }
  }

  /**
   * Returns a [[MetricData.HistogramData]] with one point per attribute set,
   * covering every value since the histogram was created, with the current time
   * and a start time of `0`. Each point holds copies of the bucket counts and
   * boundaries.
   */
  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[HistogramDataPoint]()
    states.forEach { (attrs, state) =>
      state.lock.lock()
      try {
        points.add(
          HistogramDataPoint(
            attrs,
            0L,
            now,
            state.count,
            state.sum,
            state.min,
            state.max,
            state.bucketCounts.clone(),
            boundaries.clone()
          )
        )
      } finally {
        state.lock.unlock()
      }
    }
    MetricData.HistogramData(SyncInstrumentsHelper.listFromJava(points))
  }

  private def findBucketIndex(value: Double): Int = {
    var i = 0
    while (i < boundaries.length) {
      if (value <= boundaries(i)) return i
      i += 1
    }
    boundaries.length
  }
}

/** Creates histograms that no meter registers. */
object Histogram {
  private val DefaultBoundaries: Array[Double] =
    Array(0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 100.0, 250.0, 500.0, 750.0, 1000.0, 2500.0, 5000.0, 7500.0, 10000.0)

  private[telemetry] class State(bucketCount: Int) {
    var count: Long               = 0L
    var sum: Double               = 0.0
    var min: Double               = Double.MaxValue
    var max: Double               = Double.MinValue
    val bucketCounts: Array[Long] = new Array[Long](bucketCount)
    val lock: ReentrantLock       = new ReentrantLock()
  }

  /**
   * Creates a histogram with the default boundaries that no meter registers:
   * `MetricReader#collectAllMetrics` never sees it, so read it with
   * `collect()`. Use `Meter#histogramBuilder` or `metric.histogram` for a
   * registered one.
   */
  def apply(name: String, description: String, unit: String): Histogram =
    new Histogram(name, description, unit, DefaultBoundaries)

  /**
   * Creates a histogram with the bucket boundaries `boundaries` that no meter
   * registers, so read it with `collect()`. List the boundaries in increasing
   * order: they are neither sorted nor checked, and the array is kept, not
   * copied.
   */
  def apply(name: String, description: String, unit: String, boundaries: Array[Double]): Histogram =
    new Histogram(name, description, unit, boundaries)
}

/**
 * One series of a [[Histogram]], with its attributes fixed by `Histogram#bind`
 * or `LabeledHistogram#bind`.
 */
final class BoundHistogram private[telemetry] (
  private val state: Histogram.State,
  private val histogram: Histogram
) {

  /** Adds `value` to the series. */
  def record(value: Double): Unit = histogram.recordInternal(value, state)
}
