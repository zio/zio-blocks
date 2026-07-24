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
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized
import org.jctools.queues.{SpscArrayQueue, MpscArrayQueue, SpmcArrayQueue, MpmcArrayQueue}
import zio.blocks.ringbuffer.{SpscRingBuffer, MpscRingBuffer, SpmcRingBuffer, MpmcRingBuffer}
import zio.blocks.ringbuffer.benchmarks.disruptor.{
  DisruptorSpscRingBuffer,
  DisruptorMpscRingBuffer,
  DisruptorSpmcRingBuffer,
  DisruptorMpmcRingBuffer
}

/**
 * Latency benchmarks measuring operation time distribution (p50, p95, p99).
 * Uses Mode.SampleTime to capture per-invocation latency.
 */

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class SpscLatencyBenchmark {
  @Param(Array("ZIO_OBJECT", "ABQ", "LBQ", "JCTOOLS_SPSC", "DISRUPTOR_SPSC"))
  var impl: String = uninitialized

  @Param(Array("256", "512", "1024", "2048", "8192"))
  var capacity: Int = uninitialized

  private var rb: SpscRingBuffer[java.lang.Integer]                            = uninitialized
  private var abq: java.util.concurrent.ArrayBlockingQueue[java.lang.Integer]  = uninitialized
  private var lbq: java.util.concurrent.LinkedBlockingQueue[java.lang.Integer] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Integer]                           = uninitialized
  private var disruptorRb: DisruptorSpscRingBuffer[java.lang.Integer]          = uninitialized

  private val ITEM: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    rb = null; abq = null; lbq = null; jcq = null; disruptorRb = null
    impl match {
      case "ZIO_OBJECT"     => rb = new SpscRingBuffer[java.lang.Integer](capacity)
      case "ABQ"            => abq = new java.util.concurrent.ArrayBlockingQueue[java.lang.Integer](capacity)
      case "LBQ"            => lbq = new java.util.concurrent.LinkedBlockingQueue[java.lang.Integer](capacity)
      case "JCTOOLS_SPSC"   => jcq = new SpscArrayQueue[java.lang.Integer](capacity)
      case "DISRUPTOR_SPSC" => disruptorRb = new DisruptorSpscRingBuffer[java.lang.Integer](capacity)
    }
  }

  @Benchmark @Group("spsc_latency") @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_OBJECT"     => while (!control.stopMeasurement && !rb.offer(ITEM)) Thread.onSpinWait()
    case "ABQ"            => while (!control.stopMeasurement && !abq.offer(ITEM)) Thread.onSpinWait()
    case "LBQ"            => while (!control.stopMeasurement && !lbq.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_SPSC"   => while (!control.stopMeasurement && !jcq.offer(ITEM)) Thread.onSpinWait()
    case "DISRUPTOR_SPSC" => while (!control.stopMeasurement && !disruptorRb.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("spsc_latency") @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_OBJECT" =>
      var v = rb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = rb.take() }
      if (v ne null) bh.consume(v)
    case "ABQ" =>
      var v = abq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
      if (v ne null) bh.consume(v)
    case "LBQ" =>
      var v = lbq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = lbq.poll() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPSC" =>
      var v = jcq.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcq.poll() }
      if (v ne null) bh.consume(v)
    case "DISRUPTOR_SPSC" =>
      var v = disruptorRb.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = disruptorRb.take() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class MpscLatencyBenchmark {
  @Param(Array("ZIO_MPSC", "JCTOOLS_MPSC", "DISRUPTOR_MPSC"))
  var impl: String = uninitialized

  @Param(Array("256", "512", "1024", "2048", "8192"))
  var capacity: Int = uninitialized

  private var zioQ: MpscRingBuffer[java.lang.Integer]                = uninitialized
  private var jcQ: MpscArrayQueue[java.lang.Integer]                 = uninitialized
  private var disruptorQ: DisruptorMpscRingBuffer[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer                                = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null; disruptorQ = null
    impl match {
      case "ZIO_MPSC"       => zioQ = new MpscRingBuffer[java.lang.Integer](capacity)
      case "JCTOOLS_MPSC"   => jcQ = new MpscArrayQueue[java.lang.Integer](capacity)
      case "DISRUPTOR_MPSC" => disruptorQ = new DisruptorMpscRingBuffer[java.lang.Integer](capacity)
    }
  }

  @Benchmark @Group("mpsc_latency") @GroupThreads(2)
  def produce(control: Control): Unit = impl match {
    case "ZIO_MPSC"       => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_MPSC"   => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
    case "DISRUPTOR_MPSC" => while (!control.stopMeasurement && !disruptorQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("mpsc_latency") @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPSC" =>
      var v = zioQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_MPSC" =>
      var v = jcQ.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
    case "DISRUPTOR_MPSC" =>
      var v = disruptorQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = disruptorQ.take() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class SpmcLatencyBenchmark {
  @Param(Array("ZIO_SPMC", "JCTOOLS_SPMC", "DISRUPTOR_SPMC"))
  var impl: String = uninitialized

  @Param(Array("256", "512", "1024", "2048", "8192"))
  var capacity: Int = uninitialized

  private var zioQ: SpmcRingBuffer[java.lang.Integer]                = uninitialized
  private var jcQ: SpmcArrayQueue[java.lang.Integer]                 = uninitialized
  private var disruptorQ: DisruptorSpmcRingBuffer[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer                                = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null; disruptorQ = null
    impl match {
      case "ZIO_SPMC"       => zioQ = new SpmcRingBuffer[java.lang.Integer](capacity)
      case "JCTOOLS_SPMC"   => jcQ = new SpmcArrayQueue[java.lang.Integer](capacity)
      case "DISRUPTOR_SPMC" => disruptorQ = new DisruptorSpmcRingBuffer[java.lang.Integer](capacity)
    }
  }

  @Benchmark @Group("spmc_latency") @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_SPMC"       => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_SPMC"   => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
    case "DISRUPTOR_SPMC" => while (!control.stopMeasurement && !disruptorQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("spmc_latency") @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_SPMC" =>
      var v = zioQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPMC" =>
      var v = jcQ.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
    case "DISRUPTOR_SPMC" =>
      var v = disruptorQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = disruptorQ.take() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class MpmcLatencyBenchmark {
  @Param(Array("ZIO_MPMC", "JCTOOLS_MPMC", "DISRUPTOR_MPMC"))
  var impl: String = uninitialized

  @Param(Array("256", "512", "1024", "2048", "8192"))
  var capacity: Int = uninitialized

  private var zioQ: MpmcRingBuffer[java.lang.Integer]                = uninitialized
  private var jcQ: MpmcArrayQueue[java.lang.Integer]                 = uninitialized
  private var disruptorQ: DisruptorMpmcRingBuffer[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer                                = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null; disruptorQ = null
    impl match {
      case "ZIO_MPMC"       => zioQ = new MpmcRingBuffer[java.lang.Integer](capacity)
      case "JCTOOLS_MPMC"   => jcQ = new MpmcArrayQueue[java.lang.Integer](capacity)
      case "DISRUPTOR_MPMC" => disruptorQ = new DisruptorMpmcRingBuffer[java.lang.Integer](capacity)
    }
  }

  @Benchmark @Group("mpmc_latency") @GroupThreads(2)
  def produce(control: Control): Unit = impl match {
    case "ZIO_MPMC"       => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_MPMC"   => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
    case "DISRUPTOR_MPMC" => while (!control.stopMeasurement && !disruptorQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("mpmc_latency") @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPMC" =>
      var v = zioQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_MPMC" =>
      var v = jcQ.poll()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
    case "DISRUPTOR_MPMC" =>
      var v = disruptorQ.take()
      while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = disruptorQ.take() }
      if (v ne null) bh.consume(v)
  }
}
