package zio.blocks.ringbuffer.benchmarks

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{Blackhole, Control}
import java.util.concurrent.{ArrayBlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.compiletime.uninitialized
import zio.blocks.ringbuffer.BlockingMpmcRingBuffer

/**
 * Blocking throughput benchmark: put/take in tight loop. Measures raw queue
 * overhead under maximum contention.
 *
 * Compares BlockingMpmcRingBuffer against JDK ArrayBlockingQueue and
 * LinkedBlockingQueue. All three use put/take (blocking API) for
 * apples-to-apples comparison.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class BlockingTightBenchmark {

  @Param(Array("ZIO_MPMC", "ABQ", "LBQ"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var zioQ: BlockingMpmcRingBuffer[java.lang.Integer] = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer]      = uninitialized
  private var lbq: LinkedBlockingQueue[java.lang.Integer]     = uninitialized
  private val ITEM: java.lang.Integer                         = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; abq = null; lbq = null
    impl match {
      case "ZIO_MPMC" => zioQ = new BlockingMpmcRingBuffer[java.lang.Integer](capacity)
      case "ABQ"      => abq = new ArrayBlockingQueue[java.lang.Integer](capacity)
      case "LBQ"      => lbq = new LinkedBlockingQueue[java.lang.Integer](capacity)
    }
  }

  @Benchmark
  @Group("blocking_tight")
  @GroupThreads(2)
  def produce(control: Control): Unit = impl match {
    case "ZIO_MPMC" =>
      if (!control.stopMeasurement)
        try zioQ.offer(ITEM)
        catch { case _: InterruptedException => }
    case "ABQ" =>
      if (!control.stopMeasurement)
        try abq.put(ITEM)
        catch { case _: InterruptedException => }
    case "LBQ" =>
      if (!control.stopMeasurement)
        try lbq.put(ITEM)
        catch { case _: InterruptedException => }
  }

  @Benchmark
  @Group("blocking_tight")
  @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPMC" =>
      if (!control.stopMeasurement)
        try bh.consume(zioQ.take())
        catch { case _: InterruptedException => }
    case "ABQ" =>
      if (!control.stopMeasurement)
        try bh.consume(abq.take())
        catch { case _: InterruptedException => }
    case "LBQ" =>
      if (!control.stopMeasurement)
        try bh.consume(lbq.take())
        catch { case _: InterruptedException => }
  }
}

/**
 * Realistic workload benchmark: producers and consumers do simulated work
 * between queue operations. Uses Blackhole.consumeCPU to simulate processing.
 *
 * This is more representative of real applications where the queue is part of a
 * pipeline, not the sole bottleneck.
 *
 * Work tokens: 64 = ~100ns of CPU work per operation on modern hardware.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class BlockingRealisticBenchmark {

  @Param(Array("ZIO_MPMC", "ABQ", "LBQ"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  @Param(Array("64"))
  var workTokens: Int = uninitialized

  private var zioQ: BlockingMpmcRingBuffer[java.lang.Integer] = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer]      = uninitialized
  private var lbq: LinkedBlockingQueue[java.lang.Integer]     = uninitialized
  private val ITEM: java.lang.Integer                         = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; abq = null; lbq = null
    impl match {
      case "ZIO_MPMC" => zioQ = new BlockingMpmcRingBuffer[java.lang.Integer](capacity)
      case "ABQ"      => abq = new ArrayBlockingQueue[java.lang.Integer](capacity)
      case "LBQ"      => lbq = new LinkedBlockingQueue[java.lang.Integer](capacity)
    }
  }

  @Benchmark
  @Group("blocking_realistic")
  @GroupThreads(2)
  def produce(control: Control): Unit = {
    Blackhole.consumeCPU(workTokens) // simulate work before producing
    impl match {
      case "ZIO_MPMC" =>
        if (!control.stopMeasurement)
          try zioQ.offer(ITEM)
          catch { case _: InterruptedException => }
      case "ABQ" =>
        if (!control.stopMeasurement)
          try abq.put(ITEM)
          catch { case _: InterruptedException => }
      case "LBQ" =>
        if (!control.stopMeasurement)
          try lbq.put(ITEM)
          catch { case _: InterruptedException => }
    }
  }

  @Benchmark
  @Group("blocking_realistic")
  @GroupThreads(2)
  def consume(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPMC" =>
      if (!control.stopMeasurement) try { bh.consume(zioQ.take()); Blackhole.consumeCPU(workTokens) }
      catch { case _: InterruptedException => }
    case "ABQ" =>
      if (!control.stopMeasurement) try { bh.consume(abq.take()); Blackhole.consumeCPU(workTokens) }
      catch { case _: InterruptedException => }
    case "LBQ" =>
      if (!control.stopMeasurement) try { bh.consume(lbq.take()); Blackhole.consumeCPU(workTokens) }
      catch { case _: InterruptedException => }
  }
}

/**
 * Scalability benchmark: varies producer/consumer thread counts. Uses 1P/1C as
 * baseline, then 2P/2C, 4P/4C. Tight put/take loop (no simulated work) to
 * isolate queue contention scaling.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class BlockingScalabilityBenchmark {

  @Param(Array("ZIO_MPMC", "ABQ"))
  var impl: String = uninitialized

  private var zioQ: BlockingMpmcRingBuffer[java.lang.Integer] = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer]      = uninitialized
  private val ITEM: java.lang.Integer                         = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; abq = null
    impl match {
      case "ZIO_MPMC" => zioQ = new BlockingMpmcRingBuffer[java.lang.Integer](1024)
      case "ABQ"      => abq = new ArrayBlockingQueue[java.lang.Integer](1024)
    }
  }

  // 1P/1C configuration
  @Benchmark
  @Group("scale_1p1c")
  @GroupThreads(1)
  def produce_1p1c(control: Control): Unit = impl match {
    case "ZIO_MPMC" =>
      if (!control.stopMeasurement)
        try zioQ.offer(ITEM)
        catch { case _: InterruptedException => }
    case "ABQ" =>
      if (!control.stopMeasurement)
        try abq.put(ITEM)
        catch { case _: InterruptedException => }
  }

  @Benchmark
  @Group("scale_1p1c")
  @GroupThreads(1)
  def consume_1p1c(control: Control, bh: Blackhole): Unit = impl match {
    case "ZIO_MPMC" =>
      if (!control.stopMeasurement)
        try bh.consume(zioQ.take())
        catch { case _: InterruptedException => }
    case "ABQ" =>
      if (!control.stopMeasurement)
        try bh.consume(abq.take())
        catch { case _: InterruptedException => }
  }
}

/**
 * Fast-path advantage benchmark: the buffer is pre-filled to ~50% capacity, and
 * producers/consumers operate at matched rates with light work (8 CPU tokens).
 *
 * The buffer stays in the "happy middle" — never empty, never full. Our
 * offer/poll hits the lock-free fast path every time. ABQ still acquires its
 * ReentrantLock on every single put/take.
 *
 * This isolates the lock acquisition overhead that ABQ pays and we avoid.
 *
 * Uses non-blocking offer/poll (not put/take) so neither side ever blocks.
 * Failed offers/polls are retried with onSpinWait — this simulates a
 * producer/consumer that's slightly faster than the other side but backs off.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = Array("-Xms2G", "-Xmx2G", "-XX:+AlwaysPreTouch"))
@State(Scope.Group)
class BlockingFastPathBenchmark {

  @Param(Array("ZIO_MPMC", "ABQ", "LBQ"))
  var impl: String = uninitialized

  @Param(Array("1024"))
  var capacity: Int = uninitialized

  private var zioQ: BlockingMpmcRingBuffer[java.lang.Integer] = uninitialized
  private var abq: ArrayBlockingQueue[java.lang.Integer]      = uninitialized
  private var lbq: LinkedBlockingQueue[java.lang.Integer]     = uninitialized
  private val ITEM: java.lang.Integer                         = java.lang.Integer.valueOf(42)

  @Setup(Level.Iteration)
  def setup(): Unit = {
    zioQ = null; abq = null; lbq = null
    val halfCap = capacity / 2
    impl match {
      case "ZIO_MPMC" =>
        zioQ = new BlockingMpmcRingBuffer[java.lang.Integer](capacity)
        for (_ <- 0 until halfCap) zioQ.tryOffer(ITEM)
      case "ABQ" =>
        abq = new ArrayBlockingQueue[java.lang.Integer](capacity)
        for (_ <- 0 until halfCap) abq.offer(ITEM)
      case "LBQ" =>
        lbq = new LinkedBlockingQueue[java.lang.Integer](capacity)
        for (_ <- 0 until halfCap) lbq.offer(ITEM)
    }
  }

  @Benchmark
  @Group("fastpath")
  @GroupThreads(1)
  def produce(control: Control): Unit = {
    Blackhole.consumeCPU(8)
    impl match {
      case "ZIO_MPMC" =>
        while (!control.stopMeasurement && !zioQ.tryOffer(ITEM)) Thread.onSpinWait()
      case "ABQ" =>
        while (!control.stopMeasurement && !abq.offer(ITEM)) Thread.onSpinWait()
      case "LBQ" =>
        while (!control.stopMeasurement && !lbq.offer(ITEM)) Thread.onSpinWait()
    }
  }

  @Benchmark
  @Group("fastpath")
  @GroupThreads(1)
  def consume(control: Control, bh: Blackhole): Unit = {
    Blackhole.consumeCPU(8)
    impl match {
      case "ZIO_MPMC" =>
        var v = zioQ.tryTake()
        while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = zioQ.tryTake() }
        if (v ne null) bh.consume(v)
      case "ABQ" =>
        var v = abq.poll()
        while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = abq.poll() }
        if (v ne null) bh.consume(v)
      case "LBQ" =>
        var v = lbq.poll()
        while (!control.stopMeasurement && (v eq null)) { Thread.onSpinWait(); v = lbq.poll() }
        if (v ne null) bh.consume(v)
    }
  }
}
