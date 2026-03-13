package zio.blocks.scope

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xnoclassgc",
    "-Xms3g",
    "-Xmx3g",
    "-Xss4m",
    "-XX:NewSize=2g",
    "-XX:MaxNewSize=2g",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:TLABSize=16m",
    "-XX:-ResizeTLAB",
    "-XX:+UseParallelGC",
    "-XX:-UseAdaptiveSizePolicy",
    "-XX:MaxInlineLevel=20",
    "-XX:InlineSmallCode=2500",
    "-XX:+AlwaysPreTouch",
    "-XX:+PerfDisableSharedMem",
    "-XX:-UsePerfData",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields"
  )
)
class ScopeBenchmark {

  private val noopFinalizer: () => Unit     = () => ()
  private val throwingFinalizer: () => Unit = () => throw new RuntimeException("boom")

  // ---------------------------------------------------------------------------
  // 1. Baseline: open + close with zero finalizers
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_empty(bh: Blackhole): Unit = {
    val os = Scope.global.open()
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 2. open + 1 successful finalizer + close
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_1finalizer(bh: Blackhole): Unit = {
    val os = Scope.global.open()
    os.scope.defer(noopFinalizer())
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 3. open + 10 successful finalizers + close
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_10finalizers(bh: Blackhole): Unit = {
    val os    = Scope.global.open()
    val scope = os.scope
    var i     = 0
    while (i < 10) { scope.defer(noopFinalizer()); i += 1 }
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 4. open + 100 successful finalizers + close
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_100finalizers(bh: Blackhole): Unit = {
    val os    = Scope.global.open()
    val scope = os.scope
    var i     = 0
    while (i < 100) { scope.defer(noopFinalizer()); i += 1 }
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 5. open + 1 throwing finalizer + close
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_1finalizer_throws(bh: Blackhole): Unit = {
    val os = Scope.global.open()
    os.scope.defer(throwingFinalizer())
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 6. open + 10 finalizers, all throw
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_10finalizers_allThrow(bh: Blackhole): Unit = {
    val os    = Scope.global.open()
    val scope = os.scope
    var i     = 0
    while (i < 10) { scope.defer(throwingFinalizer()); i += 1 }
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 7. open + 10 finalizers, half throw
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_10finalizers_halfThrow(bh: Blackhole): Unit = {
    val os    = Scope.global.open()
    val scope = os.scope
    var i     = 0
    while (i < 10) {
      if (i % 2 == 0) scope.defer(noopFinalizer())
      else scope.defer(throwingFinalizer())
      i += 1
    }
    bh.consume(os.close())
  }

  // ---------------------------------------------------------------------------
  // 8. scoped { } with empty body
  // ---------------------------------------------------------------------------
  @Benchmark
  def scoped_empty(bh: Blackhole): Unit = {
    val result = Scope.global.scoped(_ => 42)
    bh.consume(result)
  }

  // ---------------------------------------------------------------------------
  // 9. scoped { } with one acquireRelease allocation
  // ---------------------------------------------------------------------------
  @Benchmark
  def scoped_1allocate(bh: Blackhole): Unit = {
    val result = Scope.global.scoped { scope =>
      import scope._
      val v = allocate(Resource.acquireRelease(42)(_ => ()))
      v.asInstanceOf[Int]
    }
    bh.consume(result)
  }

  // ---------------------------------------------------------------------------
  // 10. defer + cancel before close
  // ---------------------------------------------------------------------------
  @Benchmark
  def openClose_deferAndCancel(bh: Blackhole): Unit = {
    val os     = Scope.global.open()
    val handle = os.scope.defer(noopFinalizer())
    handle.cancel()
    bh.consume(os.close())
  }
}
