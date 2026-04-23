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

final class Histogram private[telemetry] (
  val name: String,
  val description: String,
  val unit: String,
  val boundaries: Array[Double]
) {
  private val states = new ConcurrentHashMap[Attributes, Histogram.State]()

  def record(value: Double, attributes: Attributes): Unit = {
    var state = states.get(attributes)
    if (state == null) {
      state = new Histogram.State(boundaries.length + 1)
      val existing = states.putIfAbsent(attributes, state)
      if (existing != null) state = existing
    }
    recordInternal(value, state)
  }

  def record(value: Double, attrs: (String, Any)*): Unit =
    if (attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var state      = states.get(attributes)
      if (state == null) {
        state = new Histogram.State(boundaries.length + 1)
        val existing = states.putIfAbsent(attributes, state)
        if (existing != null) state = existing
      }
      recordInternal(value, state)
    }

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

  def apply(name: String, description: String, unit: String): Histogram =
    new Histogram(name, description, unit, DefaultBoundaries)

  def apply(name: String, description: String, unit: String, boundaries: Array[Double]): Histogram =
    new Histogram(name, description, unit, boundaries)
}

final class BoundHistogram private[telemetry] (
  private val state: Histogram.State,
  private val histogram: Histogram
) {
  def record(value: Double): Unit = histogram.recordInternal(value, state)
}
