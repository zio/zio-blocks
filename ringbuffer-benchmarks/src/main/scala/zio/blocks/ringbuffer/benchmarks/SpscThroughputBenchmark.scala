package zio.blocks.ringbuffer.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{Blackhole, Control}

import java.util.concurrent.{ArrayBlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.compiletime.uninitialized
import org.jctools.queues.SpscArrayQueue
import zio.blocks.ringbuffer.SpscRingBuffer

/**
 * SPSC throughput benchmark comparing non-blocking offer/poll across:
 *   - ZIO SpscRingBuffer[Integer] (generic reference)
 *   - JDK ArrayBlockingQueue (lock-based)
 *   - JDK LinkedBlockingQueue (lock-based, node-allocating)
 *   - JCTools SpscArrayQueue (speed-of-light reference)
 *
 * Uses 1 producer thread and 1 consumer thread via JMH @Group.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class SpscThroughputBenchmark {

  @Param(Array("ZIO_OBJECT", "ABQ", "LBQ", "JCTOOLS_SPSC"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var rb: SpscRingBuffer[java.lang.Integer]       = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer]  = uninitialized
  private var lbq: LinkedBlockingQueue[java.lang.Integer] = uninitialized
  private var jcq: SpscArrayQueue[java.lang.Integer]      = uninitialized

  private val ITEM: java.lang.Integer = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    rb = null; abq = null; lbq = null; jcq = null
    impl match {
      case "ZIO_OBJECT"   => rb = new SpscRingBuffer[java.lang.Integer](capacity)
      case "ABQ"          => abq = new ArrayBlockingQueue[java.lang.Integer](capacity)
      case "LBQ"          => lbq = new LinkedBlockingQueue[java.lang.Integer](capacity)
      case "JCTOOLS_SPSC" => jcq = new SpscArrayQueue[java.lang.Integer](capacity)
    }
  }

  @Benchmark
  @Group("spsc")
  @GroupThreads(1)
  def produce(control: Control): Unit = impl match {
    case "ZIO_OBJECT" =>
      while (!control.stopMeasurement && !rb.offer(ITEM)) Thread.onSpinWait()
    case "ABQ" =>
      while (!control.stopMeasurement && !abq.offer(ITEM)) Thread.onSpinWait()
    case "LBQ" =>
      while (!control.stopMeasurement && !lbq.offer(ITEM)) Thread.onSpinWait()
    case "JCTOOLS_SPSC" =>
      while (!control.stopMeasurement && !jcq.offer(ITEM)) Thread.onSpinWait()
  }

  @Benchmark
  @Group("spsc")
  @GroupThreads(1)
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
  }
}
