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
import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import java.util.concurrent.locks.ReentrantLock

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
      var adder = adders.get(attributes)
      if (adder == null) {
        adder = new LongAdder()
        val existing = adders.putIfAbsent(attributes, adder)
        if (existing != null) adder = existing
      }
      adder.add(value)
    }

  /**
   * Adds a non-negative value with inline key-value attributes.
   *
   * Usage: `counter.add(1L, "method" -> "GET", "status" -> 200L)`
   */
  def add(value: Long, attrs: (String, Any)*): Unit =
    if (value >= 0 && attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var adder      = adders.get(attributes)
      if (adder == null) {
        adder = new LongAdder()
        val existing = adders.putIfAbsent(attributes, adder)
        if (existing != null) adder = existing
      }
      adder.add(value)
    }

  /**
   * Returns a pre-bound handle for zero-lookup repeated use with the given
   * attributes.
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
   * Collects a snapshot of current counter data as SumData.
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

object Counter {
  def apply(name: String, description: String, unit: String): Counter =
    new Counter(name, description, unit)
}

/**
 * A pre-bound counter handle for zero-lookup repeated use.
 */
final class BoundCounter private[telemetry] (private val adder: LongAdder) {

  /**
   * Adds a non-negative value without attribute lookup.
   */
  def add(value: Long): Unit = if (value >= 0) adder.add(value)
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
    var adder = adders.get(attributes)
    if (adder == null) {
      adder = new LongAdder()
      val existing = adders.putIfAbsent(attributes, adder)
      if (existing != null) adder = existing
    }
    adder.add(value)
  }

  /**
   * Adds a value with inline key-value attributes.
   *
   * Usage: `upDownCounter.add(-3L, "queue" -> "main")`
   */
  def add(value: Long, attrs: (String, Any)*): Unit =
    if (attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var adder      = adders.get(attributes)
      if (adder == null) {
        adder = new LongAdder()
        val existing = adders.putIfAbsent(attributes, adder)
        if (existing != null) adder = existing
      }
      adder.add(value)
    }

  /**
   * Returns a pre-bound handle for zero-lookup repeated use with the given
   * attributes.
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
   * Collects a snapshot of current counter data as SumData.
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

object UpDownCounter {
  def apply(name: String, description: String, unit: String): UpDownCounter =
    new UpDownCounter(name, description, unit)
}

/**
 * A pre-bound up-down counter handle for zero-lookup repeated use.
 */
final class BoundUpDownCounter private[telemetry] (private val adder: LongAdder) {

  /**
   * Adds a value (positive or negative) without attribute lookup.
   */
  def add(value: Long): Unit = adder.add(value)
}

/**
 * A histogram instrument that records value distributions.
 *
 * Thread-safe: uses ReentrantLock per attribute set for consistent bucket,
 * count, and statistics updates. ReentrantLock avoids virtual thread pinning
 * unlike synchronized blocks.
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
    var state = states.get(attributes)
    if (state == null) {
      state = new Histogram.State(boundaries.length + 1)
      val existing = states.putIfAbsent(attributes, state)
      if (existing != null) state = existing
    }
    recordInternal(value, state)
  }

  /**
   * Records a value with inline key-value attributes.
   *
   * Usage: `histogram.record(3.14, "method" -> "GET")`
   */
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

  /**
   * Returns a pre-bound handle for zero-lookup repeated use with the given
   * attributes.
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
   * Collects a snapshot of current histogram data.
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

/**
 * A pre-bound histogram handle for zero-lookup repeated use.
 */
final class BoundHistogram private[telemetry] (
  private val state: Histogram.State,
  private val histogram: Histogram
) {

  /**
   * Records a value without attribute lookup.
   */
  def record(value: Double): Unit = histogram.recordInternal(value, state)
}

/**
 * A gauge instrument that records the latest observed value.
 *
 * Thread-safe: uses AtomicLong per attribute set for lock-free updates. Values
 * are stored as raw long bits via `doubleToRawLongBits` to avoid Double boxing.
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
  private val values = new ConcurrentHashMap[Attributes, AtomicLong]()

  /**
   * Records the current value for the given attributes, replacing any previous
   * value.
   */
  def record(value: Double, attributes: Attributes): Unit = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    ref.set(java.lang.Double.doubleToRawLongBits(value))
  }

  /**
   * Records a value with inline key-value attributes.
   *
   * Usage: `gauge.record(42.0, "method" -> "GET")`
   */
  def record(value: Double, attrs: (String, Any)*): Unit =
    if (attrs.nonEmpty) {
      val attributes = SyncInstrumentsHelper.buildPooledAttributes(attrs)
      var ref        = values.get(attributes)
      if (ref == null) {
        ref = new AtomicLong(0L)
        val existing = values.putIfAbsent(attributes, ref)
        if (existing != null) ref = existing
      }
      ref.set(java.lang.Double.doubleToRawLongBits(value))
    }

  /**
   * Returns a pre-bound handle for zero-lookup repeated use with the given
   * attributes.
   */
  def bind(attributes: Attributes): BoundGauge = {
    var ref = values.get(attributes)
    if (ref == null) {
      ref = new AtomicLong(0L)
      val existing = values.putIfAbsent(attributes, ref)
      if (existing != null) ref = existing
    }
    new BoundGauge(ref)
  }

  /**
   * Collects a snapshot of current gauge data.
   */
  def collect(): MetricData = {
    val now    = EpochClock.epochNanos()
    val points = new java.util.ArrayList[GaugeDataPoint]()
    values.forEach { (attrs, ref) =>
      points.add(GaugeDataPoint(attrs, now, java.lang.Double.longBitsToDouble(ref.get())))
    }
    MetricData.GaugeData(SyncInstrumentsHelper.listFromJava(points))
  }
}

object Gauge {
  def apply(name: String, description: String, unit: String): Gauge =
    new Gauge(name, description, unit)
}

/**
 * A pre-bound gauge handle for zero-lookup repeated use.
 */
final class BoundGauge private[telemetry] (private val ref: AtomicLong) {

  /**
   * Records a value without attribute lookup.
   */
  def record(value: Double): Unit = ref.set(java.lang.Double.doubleToRawLongBits(value))
}

private[telemetry] object SyncInstrumentsHelper {

  def buildPooledAttributes(attrs: Seq[(String, Any)]): Attributes = {
    val builder = AttributeBuilderPool.get()
    var i       = 0
    while (i < attrs.length) {
      val (k, v) = attrs(i)
      v match {
        case s: String  => builder.put(k, s)
        case l: Long    => builder.put(k, l)
        case i: Int     => builder.put(k, i.toLong)
        case d: Double  => builder.put(k, d)
        case b: Boolean => builder.put(k, b)
        case other      => builder.put(k, other.toString)
      }
      i += 1
    }
    builder.build
  }

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
