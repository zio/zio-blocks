package zio.blocks.ringbuffer.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{Blackhole, Control}
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized
import org.jctools.queues.{MpscArrayQueue, SpmcArrayQueue, MpmcArrayQueue}
import zio.blocks.ringbuffer.{MpscRingBuffer, SpmcRingBuffer, MpmcRingBuffer}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class MpscBenchmark {
  @Param(Array("ZIO_MPSC", "JCTOOLS_MPSC"))
  var impl: String = uninitialized

  private var zioQ: MpscRingBuffer[java.lang.Integer] = uninitialized
  private var jcQ: MpscArrayQueue[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null
    impl match {
      case "ZIO_MPSC" => zioQ = new MpscRingBuffer[java.lang.Integer](1024)
      case "JCTOOLS_MPSC" => jcQ = new MpscArrayQueue[java.lang.Integer](1024)
    }
  }

  @Benchmark @Group("mpsc") @GroupThreads(2)
  def produce(control: Control): Unit = impl match {
    case "ZIO_MPSC" => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_MPSC" => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("mpsc") @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPSC" =>
      var v = zioQ.take(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_MPSC" =>
      var v = jcQ.poll(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class SpmcBenchmark {
  @Param(Array("ZIO_SPMC", "JCTOOLS_SPMC"))
  var impl: String = uninitialized

  private var zioQ: SpmcRingBuffer[java.lang.Integer] = uninitialized
  private var jcQ: SpmcArrayQueue[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null
    impl match {
      case "ZIO_SPMC" => zioQ = new SpmcRingBuffer[java.lang.Integer](1024)
      case "JCTOOLS_SPMC" => jcQ = new SpmcArrayQueue[java.lang.Integer](1024)
    }
  }

  @Benchmark @Group("spmc") @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_SPMC" => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_SPMC" => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("spmc") @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_SPMC" =>
      var v = zioQ.take(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_SPMC" =>
      var v = jcQ.poll(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class MpmcBenchmark {
  @Param(Array("ZIO_MPMC", "JCTOOLS_MPMC"))
  var impl: String = uninitialized

  private var zioQ: MpmcRingBuffer[java.lang.Integer] = uninitialized
  private var jcQ: MpmcArrayQueue[java.lang.Integer] = uninitialized
  private val ITEM: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; jcQ = null
    impl match {
      case "ZIO_MPMC" => zioQ = new MpmcRingBuffer[java.lang.Integer](1024)
      case "JCTOOLS_MPMC" => jcQ = new MpmcArrayQueue[java.lang.Integer](1024)
    }
  }

  @Benchmark @Group("mpmc") @GroupThreads(2)
  def produce(control: Control): Unit = impl match {
    case "ZIO_MPMC" => while (!control.stopMeasurement && !zioQ.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_MPMC" => while (!control.stopMeasurement && !jcQ.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark @Group("mpmc") @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPMC" =>
      var v = zioQ.take(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.take() }
      if (v ne null) bh.consume(v)
    case "JCTOOLS_MPMC" =>
      var v = jcQ.poll(); while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = jcQ.poll() }
      if (v ne null) bh.consume(v)
  }
}
