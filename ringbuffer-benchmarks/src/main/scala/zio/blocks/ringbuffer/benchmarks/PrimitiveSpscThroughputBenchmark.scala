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

package zio.blocks.ringbuffer.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{Blackhole, Control}

import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import scala.compiletime.uninitialized
import org.jctools.queues.SpscArrayQueue
import zio.blocks.ringbuffer.{
  DoubleSpscRingBuffer,
  FloatSpscRingBuffer,
  IntSpscRingBuffer,
  LongSpscRingBuffer,
  SpscRingBuffer
}

/**
 * SPSC throughput benchmarks for the primitive-specialized ring buffers.
 *
 * Each `*Benchmark` class compares the primitive variant against three boxing
 * baselines using the same element type:
 *   - ZIO generic `SpscRingBuffer[BoxedType]` (boxing reference)
 *   - JDK `ArrayBlockingQueue[BoxedType]` (lock-based)
 *   - JCTools `SpscArrayQueue[BoxedType]` (speed-of-light reference)
 *
 * The point is to make the zero-boxing win visible: primitive variants skip the
 * auto-box per element on the producer and the auto-unbox per element on the
 * consumer, and store data in a primitive `int[]` / `long[]` / `float[]` /
 * `double[]` array, so each element costs 4-8 bytes instead of the 16-byte
 * boxed object + 4-byte pointer.
 *
 * Uses 1 producer thread and 1 consumer thread via JMH @Group.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class IntPrimitiveSpscBenchmark {

  @Param(Array("ZIO_INT_PRIM", "ZIO_OBJECT", "ABQ", "JCTOOLS_SPSC"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var prim: IntSpscRingBuffer                    = uninitialized
  private var rb: SpscRingBuffer[java.lang.Integer]      = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Integer]     = uninitialized

  private val ITEM_INT: Int               = 42
  private val ITEM_BOX: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    prim = null; rb = null; abq = null; jcq = null
    impl match {
      case "ZIO_INT_PRIM" => prim = new IntSpscRingBuffer(capacity)
      case "ZIO_OBJECT"   => rb = new SpscRingBuffer[java.lang.Integer](capacity)
      case "ABQ"          => abq = new ArrayBlockingQueue[java.lang.Integer](capacity)
      case "JCTOOLS_SPSC" => jcq = new SpscArrayQueue[java.lang.Integer](capacity)
    }
  }

  @Benchmark
  @Group("spsc_int")
  @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_INT_PRIM" =>
      while (!control.stopMeasurement && !prim.offer(ITEM_INT)) Thread.onSpinWait()
    case "ZIO_OBJECT" =>
      while (!control.stopMeasurement && !rb.offer(ITEM_BOX)) Thread.onSpinWait()
    case "ABQ" =>
      while (!control.stopMeasurement && !abq.offer(ITEM_BOX)) Thread.onSpinWait()
    case "JCTOOLS_SPSC" =>
      while (!control.stopMeasurement && !jcq.offer(ITEM_BOX)) Thread.onSpinWait()
  }

  @Benchmark
  @Group("spsc_int")
  @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_INT_PRIM" =>
      while (!control.stopMeasurement && !prim.peek()) Thread.onSpinWait()
      if (prim.peek()) bh.consume(prim.take())
    case "ZIO_OBJECT" =>
      var v = rb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = rb.take() }
      if (v ne null) bh.consume(v)
    case "ABQ" =>
      var v = abq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPSC" =>
      var v = jcq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcq.poll() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class LongPrimitiveSpscBenchmark {

  @Param(Array("ZIO_LONG_PRIM", "ZIO_OBJECT", "ABQ", "JCTOOLS_SPSC"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var prim: LongSpscRingBuffer                = uninitialized
  private var rb: SpscRingBuffer[java.lang.Long]      = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Long] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Long]     = uninitialized

  private val ITEM_LONG: Long          = 42L
  private val ITEM_BOX: java.lang.Long = java.lang.Long.valueOf(42L)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    prim = null; rb = null; abq = null; jcq = null
    impl match {
      case "ZIO_LONG_PRIM" => prim = new LongSpscRingBuffer(capacity)
      case "ZIO_OBJECT"    => rb = new SpscRingBuffer[java.lang.Long](capacity)
      case "ABQ"           => abq = new ArrayBlockingQueue[java.lang.Long](capacity)
      case "JCTOOLS_SPSC"  => jcq = new SpscArrayQueue[java.lang.Long](capacity)
    }
  }

  @Benchmark
  @Group("spsc_long")
  @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_LONG_PRIM" =>
      while (!control.stopMeasurement && !prim.offer(ITEM_LONG)) Thread.onSpinWait()
    case "ZIO_OBJECT" =>
      while (!control.stopMeasurement && !rb.offer(ITEM_BOX)) Thread.onSpinWait()
    case "ABQ" =>
      while (!control.stopMeasurement && !abq.offer(ITEM_BOX)) Thread.onSpinWait()
    case "JCTOOLS_SPSC" =>
      while (!control.stopMeasurement && !jcq.offer(ITEM_BOX)) Thread.onSpinWait()
  }

  @Benchmark
  @Group("spsc_long")
  @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_LONG_PRIM" =>
      while (!control.stopMeasurement && !prim.peek()) Thread.onSpinWait()
      if (prim.peek()) bh.consume(prim.take())
    case "ZIO_OBJECT" =>
      var v = rb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = rb.take() }
      if (v ne null) bh.consume(v)
    case "ABQ" =>
      var v = abq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPSC" =>
      var v = jcq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcq.poll() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class FloatPrimitiveSpscBenchmark {

  @Param(Array("ZIO_FLOAT_PRIM", "ZIO_OBJECT", "ABQ", "JCTOOLS_SPSC"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var prim: FloatSpscRingBuffer                = uninitialized
  private var rb: SpscRingBuffer[java.lang.Float]      = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Float] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Float]     = uninitialized

  private val ITEM_FLOAT: Float         = 42.0f
  private val ITEM_BOX: java.lang.Float = java.lang.Float.valueOf(42.0f)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    prim = null; rb = null; abq = null; jcq = null
    impl match {
      case "ZIO_FLOAT_PRIM" => prim = new FloatSpscRingBuffer(capacity)
      case "ZIO_OBJECT"     => rb = new SpscRingBuffer[java.lang.Float](capacity)
      case "ABQ"            => abq = new ArrayBlockingQueue[java.lang.Float](capacity)
      case "JCTOOLS_SPSC"   => jcq = new SpscArrayQueue[java.lang.Float](capacity)
    }
  }

  @Benchmark
  @Group("spsc_float")
  @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_FLOAT_PRIM" =>
      while (!control.stopMeasurement && !prim.offer(ITEM_FLOAT)) Thread.onSpinWait()
    case "ZIO_OBJECT" =>
      while (!control.stopMeasurement && !rb.offer(ITEM_BOX)) Thread.onSpinWait()
    case "ABQ" =>
      while (!control.stopMeasurement && !abq.offer(ITEM_BOX)) Thread.onSpinWait()
    case "JCTOOLS_SPSC" =>
      while (!control.stopMeasurement && !jcq.offer(ITEM_BOX)) Thread.onSpinWait()
  }

  @Benchmark
  @Group("spsc_float")
  @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_FLOAT_PRIM" =>
      while (!control.stopMeasurement && !prim.peek()) Thread.onSpinWait()
      if (prim.peek()) bh.consume(prim.take())
    case "ZIO_OBJECT" =>
      var v = rb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = rb.take() }
      if (v ne null) bh.consume(v)
    case "ABQ" =>
      var v = abq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPSC" =>
      var v = jcq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcq.poll() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class DoublePrimitiveSpscBenchmark {

  @Param(Array("ZIO_DOUBLE_PRIM", "ZIO_OBJECT", "ABQ", "JCTOOLS_SPSC"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var prim: DoubleSpscRingBuffer                = uninitialized
  private var rb: SpscRingBuffer[java.lang.Double]      = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Double] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Double]     = uninitialized

  private val ITEM_DOUBLE: Double        = 42.0
  private val ITEM_BOX: java.lang.Double = java.lang.Double.valueOf(42.0)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    prim = null; rb = null; abq = null; jcq = null
    impl match {
      case "ZIO_DOUBLE_PRIM" => prim = new DoubleSpscRingBuffer(capacity)
      case "ZIO_OBJECT"      => rb = new SpscRingBuffer[java.lang.Double](capacity)
      case "ABQ"             => abq = new ArrayBlockingQueue[java.lang.Double](capacity)
      case "JCTOOLS_SPSC"    => jcq = new SpscArrayQueue[java.lang.Double](capacity)
    }
  }

  @Benchmark
  @Group("spsc_double")
  @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_DOUBLE_PRIM" =>
      while (!control.stopMeasurement && !prim.offer(ITEM_DOUBLE)) Thread.onSpinWait()
    case "ZIO_OBJECT" =>
      while (!control.stopMeasurement && !rb.offer(ITEM_BOX)) Thread.onSpinWait()
    case "ABQ" =>
      while (!control.stopMeasurement && !abq.offer(ITEM_BOX)) Thread.onSpinWait()
    case "JCTOOLS_SPSC" =>
      while (!control.stopMeasurement && !jcq.offer(ITEM_BOX)) Thread.onSpinWait()
  }

  @Benchmark
  @Group("spsc_double")
  @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_DOUBLE_PRIM" =>
      while (!control.stopMeasurement && !prim.peek()) Thread.onSpinWait()
      if (prim.peek()) bh.consume(prim.take())
    case "ZIO_OBJECT" =>
      var v = rb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = rb.take() }
      if (v ne null) bh.consume(v)
    case "ABQ" =>
      var v = abq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPSC" =>
      var v = jcq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcq.poll() }
      if (v ne null) bh.consume(v)
  }
}
