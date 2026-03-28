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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicReference, LongAdder}

/**
 * A monotonic sum instrument that only allows non-negative additions.
 *
 * Thread-safe: uses a LongAdder per attribute set for lock-free concurrent
 * increments.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 */
final class Counter(
  val name: String,
  val description: String,
  val unit: String
) {
  private val adders = new ConcurrentHashMap[Attributes, LongAdder]()

  /**
   * Adds a non-negative value to the counter for the given attributes.
   */
  def add(value: Long, attributes: Attributes): Unit =
    if (value >= 0) {

      val adder = adders.computeIfAbsent(attributes, _ => new LongAdder())
      adder.add(value)
    }

  /**
   * Collects a snapshot of current counter data as SumData.
   */
  def collect(): MetricData = {
    val now    = System.nanoTime()
    val points = new java.util.ArrayList[SumDataPoint]()
    adders.forEach { (attrs, adder) =>
      points.add(SumDataPoint(attrs, 0L, now, adder.sum()))
    }
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object Counter {
  def apply(name: String, description: String, unit: String): Counter =
    new Counter(name, description, unit)
}

/**
 * A sum instrument that allows both positive and negative additions.
 *
 * Thread-safe: uses a LongAdder per attribute set for lock-free concurrent
 * updates.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 */
final class UpDownCounter(
  val name: String,
  val description: String,
  val unit: String
) {
  private val adders = new ConcurrentHashMap[Attributes, LongAdder]()

  /**
   * Adds a value (positive or negative) to the counter for the given
   * attributes.
   */
  def add(value: Long, attributes: Attributes): Unit = {

    val adder = adders.computeIfAbsent(attributes, _ => new LongAdder())
    adder.add(value)
  }

  /**
   * Collects a snapshot of current counter data as SumData.
   */
  def collect(): MetricData = {
    val now    = System.nanoTime()
    val points = new java.util.ArrayList[SumDataPoint]()
    adders.forEach { (attrs, adder) =>
      points.add(SumDataPoint(attrs, 0L, now, adder.sum()))
    }
    MetricData.SumData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object UpDownCounter {
  def apply(name: String, description: String, unit: String): UpDownCounter =
    new UpDownCounter(name, description, unit)
}

/**
 * A histogram instrument that records value distributions.
 *
 * Thread-safe: uses synchronized blocks per attribute set for consistent
 * bucket, count, and statistics updates.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 * @param boundaries
 *   upper exclusive boundaries for histogram buckets
 */
final class Histogram(
  val name: String,
  val description: String,
  val unit: String,
  val boundaries: Array[Double]
) {
  private val states = new ConcurrentHashMap[Attributes, Histogram.State]()

  /**
   * Records a value into the histogram for the given attributes.
   */
  def record(value: Double, attributes: Attributes): Unit = {

    val state = states.computeIfAbsent(attributes, _ => new Histogram.State(boundaries.length + 1))
    state.synchronized {
      state.count += 1
      state.sum += value
      if (value < state.min) state.min = value
      if (value > state.max) state.max = value
      val idx = findBucketIndex(value)
      state.bucketCounts(idx) += 1
    }
  }

  /**
   * Collects a snapshot of current histogram data.
   */
  def collect(): MetricData = {
    val now    = System.nanoTime()
    val points = new java.util.ArrayList[HistogramDataPoint]()
    states.forEach { (attrs, state) =>
      state.synchronized {
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

  private[otel] class State(bucketCount: Int) {
    var count: Long               = 0L
    var sum: Double               = 0.0
    var min: Double               = Double.MaxValue
    var max: Double               = Double.MinValue
    val bucketCounts: Array[Long] = new Array[Long](bucketCount)
  }

  def apply(name: String, description: String, unit: String): Histogram =
    new Histogram(name, description, unit, DefaultBoundaries)

  def apply(name: String, description: String, unit: String, boundaries: Array[Double]): Histogram =
    new Histogram(name, description, unit, boundaries)
}

/**
 * A gauge instrument that records the latest observed value.
 *
 * Thread-safe: uses AtomicReference per attribute set for lock-free updates.
 *
 * @param name
 *   instrument name
 * @param description
 *   human-readable description
 * @param unit
 *   unit of measurement
 */
final class Gauge(
  val name: String,
  val description: String,
  val unit: String
) {
  private val values =
    new ConcurrentHashMap[Attributes, AtomicReference[java.lang.Double]]()

  /**
   * Records the current value for the given attributes, replacing any previous
   * value.
   */
  def record(value: Double, attributes: Attributes): Unit = {

    val ref = values.computeIfAbsent(attributes, _ => new AtomicReference[java.lang.Double](0.0))
    ref.set(value)
  }

  /**
   * Collects a snapshot of current gauge data.
   */
  def collect(): MetricData = {
    val now    = System.nanoTime()
    val points = new java.util.ArrayList[GaugeDataPoint]()
    values.forEach { (attrs, ref) =>
      points.add(GaugeDataPoint(attrs, now, ref.get()))
    }
    MetricData.GaugeData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object Gauge {
  def apply(name: String, description: String, unit: String): Gauge =
    new Gauge(name, description, unit)
}

private[otel] object SyncInstrumentsHelper {
  def mapToAttributes(map: Map[String, AttributeValue]): Attributes = {
    val builder = Attributes.builder
    map.foreach { case (k, v) =>
      v match {
        case AttributeValue.StringValue(s)       => builder.put(k, s)
        case AttributeValue.BooleanValue(b)      => builder.put(k, b)
        case AttributeValue.LongValue(l)         => builder.put(k, l)
        case AttributeValue.DoubleValue(d)       => builder.put(k, d)
        case AttributeValue.StringSeqValue(seq)  => builder.put(AttributeKey.stringSeq(k), seq)
        case AttributeValue.LongSeqValue(seq)    => builder.put(AttributeKey.longSeq(k), seq)
        case AttributeValue.DoubleSeqValue(seq)  => builder.put(AttributeKey.doubleSeq(k), seq)
        case AttributeValue.BooleanSeqValue(seq) => builder.put(AttributeKey.booleanSeq(k), seq)
      }
    }
    builder.build
  }

  def listFromJava[A](javaList: java.util.ArrayList[A]): List[A] = {
    var result: List[A] = Nil
    var i               = javaList.size() - 1
    while (i >= 0) {
      result = javaList.get(i) :: result
      i -= 1
    }
    result
  }
}
