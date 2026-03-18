package zio.blocks.streams.bench

import org.openjdk.jmh.annotations._
import zio.blocks.streams.Stream

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

// ---- fs2 ----
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Stream => Fs2Stream}

// ---- Pekko Streams ----
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink => PekkaSink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import scala.concurrent.Await
import scala.concurrent.duration.Duration as ScalaDuration

// ---- Kyo ----
import kyo.{Stream => KyoStream, *}

// ---- Ox ----
import ox.flow.Flow

/**
 * Benchmark: Stream evaluation throughput — setup cost separated from eval
 * cost.
 *
 * ==Motivation==
 * Pre-builds stream pipelines once in a `@Setup` method for ALL five libraries,
 * then `@Benchmark` methods measure PURE evaluation throughput.
 *
 * ==Naming convention==
 * `{lib}_{operation}` where lib is `zb`, `fs2`, `kyo`, `pekko`, `ox`.
 *
 * ==Single-op benchmarks (7 per library)==
 *   - `singleton` — `Stream.succeed(42)` → fold
 *   - `drain` — `Stream.range(0, N)` → fold (zero ops)
 *   - `map_1` — `.map(_ + 1)` → fold
 *   - `filter_1` — `.filter(_ % 2 != 0)` → fold
 *   - `flatMap_1` — `range(0,100).flatMap(_ => range(0,100))` → fold (10K)
 *   - `takeDrop` — `.drop(N/2).take(N/2)` → fold
 *   - `mapFilterFlatMap` — `.map.filter.flatMap` mixed chain (×100 inner) →
 *     fold
 *
 * ==Scaling benchmarks (mixed chain × depth 1, 2, 3)==
 * Each level: `filter(_ % 2 != 0).flatMap(_ => range(0,innerN)).map(_ + 1)` Net
 * ~(innerN/2)× per level. 100-element sources for depths 2+.
 *   - `mixed_1` — 200 source × inner=100 × depth 1 → ~10K output
 *   - `mixed_2` — 100 source × inner=20 × depth 2 → ~10K output
 *   - `mixed_3` — 100 source × inner=10 × depth 3 → ~12.5K output
 *
 * ==Total: 10 benchmarks per library × 5 libraries = 50 methods==
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgsPrepend = Array(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
  )
)
@State(Scope.Thread)
class StreamEvalBench {

  @Param(Array("10000"))
  var N: Int = uninitialized

  // ---- Common data ------------------------------------------------------------
  private var seq: Vector[Int] = uninitialized

  // ---- ZB: pre-built streams --------------------------------------------------
  private var zbSingleton: Stream[Nothing, Int]        = uninitialized
  private var zbDrain: Stream[Nothing, Int]            = uninitialized
  private var zbMap1: Stream[Nothing, Int]             = uninitialized
  private var zbFilter1: Stream[Nothing, Int]          = uninitialized
  private var zbFlatMap1: Stream[Nothing, Int]         = uninitialized
  private var zbTakeDrop: Stream[Nothing, Int]         = uninitialized
  private var zbMapFilterFlatMap: Stream[Nothing, Int] = uninitialized
  private var zbMixed1: Stream[Nothing, Int]           = uninitialized
  private var zbMixed2: Stream[Nothing, Int]           = uninitialized
  private var zbMixed3: Stream[Nothing, Int]           = uninitialized

  // ---- fs2: pre-built streams -------------------------------------------------
  private var fs2Singleton: Fs2Stream[IO, Int]        = uninitialized
  private var fs2Drain: Fs2Stream[IO, Int]            = uninitialized
  private var fs2Map1: Fs2Stream[IO, Int]             = uninitialized
  private var fs2Filter1: Fs2Stream[IO, Int]          = uninitialized
  private var fs2FlatMap1: Fs2Stream[IO, Int]         = uninitialized
  private var fs2TakeDrop: Fs2Stream[IO, Int]         = uninitialized
  private var fs2MapFilterFlatMap: Fs2Stream[IO, Int] = uninitialized
  private var fs2Mixed1: Fs2Stream[IO, Int]           = uninitialized
  private var fs2Mixed2: Fs2Stream[IO, Int]           = uninitialized
  private var fs2Mixed3: Fs2Stream[IO, Int]           = uninitialized

  // ---- Kyo: pre-built streams -------------------------------------------------
  private var kyoSingleton: KyoStream[Int, Any]        = uninitialized
  private var kyoDrain: KyoStream[Int, Any]            = uninitialized
  private var kyoMap1: KyoStream[Int, Any]             = uninitialized
  private var kyoFilter1: KyoStream[Int, Any]          = uninitialized
  private var kyoFlatMap1: KyoStream[Int, Any]         = uninitialized
  private var kyoTakeDrop: KyoStream[Int, Any]         = uninitialized
  private var kyoMapFilterFlatMap: KyoStream[Int, Any] = uninitialized
  private var kyoMixed1: KyoStream[Int, Any]           = uninitialized
  private var kyoMixed2: KyoStream[Int, Any]           = uninitialized
  private var kyoMixed3: KyoStream[Int, Any]           = uninitialized

  // ---- Ox: pre-built flows ----------------------------------------------------
  private var oxSingleton: Flow[Int]        = uninitialized
  private var oxDrain: Flow[Int]            = uninitialized
  private var oxMap1: Flow[Int]             = uninitialized
  private var oxFilter1: Flow[Int]          = uninitialized
  private var oxFlatMap1: Flow[Int]         = uninitialized
  private var oxTakeDrop: Flow[Int]         = uninitialized
  private var oxMapFilterFlatMap: Flow[Int] = uninitialized
  private var oxMixed1: Flow[Int]           = uninitialized
  private var oxMixed2: Flow[Int]           = uninitialized
  private var oxMixed3: Flow[Int]           = uninitialized

  // ---- Pekko: pre-built sources + materializer --------------------------------
  implicit var pekkoSystem: ActorSystem = uninitialized
  implicit var pekkoMat: Materializer   = uninitialized

  private var pekkoSingleton: Source[Int, ?]        = uninitialized
  private var pekkoDrain: Source[Int, ?]            = uninitialized
  private var pekkoMap1: Source[Int, ?]             = uninitialized
  private var pekkoFilter1: Source[Int, ?]          = uninitialized
  private var pekkoFlatMap1: Source[Int, ?]         = uninitialized
  private var pekkoTakeDrop: Source[Int, ?]         = uninitialized
  private var pekkoMapFilterFlatMap: Source[Int, ?] = uninitialized

  // ===========================================================================
  // Setup
  // ===========================================================================

  @Setup(Level.Trial)
  def setup(): Unit = {
    seq = (0 until N).toVector

    // ---- ZB streams ----
    val zbBase = Stream.range(0, N)
    zbSingleton = Stream.succeed(42)
    zbDrain = Stream.range(0, N)
    zbMap1 = zbBase.map(_ + 1)
    zbFilter1 = zbBase.filter(_ % 2 != 0)
    zbFlatMap1 = Stream.range(0, 100).flatMap(_ => Stream.range(0, 100))
    zbTakeDrop = zbBase.drop(N / 2).take((N / 2).toLong)
    zbMapFilterFlatMap = zbBase.map(_ + 1).filter(_ % 2 != 0).flatMap(_ => Stream.range(0, 100))
    zbMixed1 = chainZbMixed(Stream.range(0, 200), 1, 100)
    zbMixed2 = chainZbMixed(Stream.range(0, 100), 2, 20)
    zbMixed3 = chainZbMixed(Stream.range(0, 100), 3, 10)

    // ---- fs2 streams ----
    val fs2Base = Fs2Stream.emits(seq).covary[IO]
    fs2Singleton = Fs2Stream.emit(42)
    fs2Drain = Fs2Stream.emits(seq).covary[IO]
    fs2Map1 = fs2Base.map(_ + 1)
    fs2Filter1 = fs2Base.filter(_ % 2 != 0)
    fs2FlatMap1 = Fs2Stream.range(0, 100).covary[IO].flatMap(_ => Fs2Stream.range(0, 100))
    fs2TakeDrop = fs2Base.drop(N / 2).take((N / 2).toLong)
    fs2MapFilterFlatMap = fs2Base.map(_ + 1).filter(_ % 2 != 0).flatMap(_ => Fs2Stream.range(0, 100))
    fs2Mixed1 = chainFs2Mixed(Fs2Stream.range(0, 200).covary[IO], 1, 100)
    fs2Mixed2 = chainFs2Mixed(Fs2Stream.range(0, 100).covary[IO], 2, 20)
    fs2Mixed3 = chainFs2Mixed(Fs2Stream.range(0, 100).covary[IO], 3, 10)

    // ---- Kyo streams ----
    val kyoBase: KyoStream[Int, Any] = KyoStream.init(seq)
    kyoSingleton = KyoStream.init(Seq(42))
    kyoDrain = KyoStream.init(seq)
    kyoMap1 = kyoBase.map(_ + 1)
    kyoFilter1 = kyoBase.filter(_ % 2 != 0)
    kyoFlatMap1 = KyoStream.init(0 until 100).flatMap(_ => KyoStream.init(0 until 100))
    kyoTakeDrop = kyoBase.drop(N / 2).take(N / 2)
    kyoMapFilterFlatMap = kyoBase.map(_ + 1).filter(_ % 2 != 0).flatMap(_ => KyoStream.init(0 until 100))
    kyoMixed1 = chainKyoMixed(KyoStream.init(0 until 200), 1, 100)
    kyoMixed2 = chainKyoMixed(KyoStream.init(0 until 100), 2, 20)
    kyoMixed3 = chainKyoMixed(KyoStream.init(0 until 100), 3, 10)

    // ---- Pekko ----
    pekkoSystem = ActorSystem("eval-bench")
    pekkoMat = SystemMaterializer(pekkoSystem).materializer

    pekkoSingleton = Source.single(42)
    pekkoDrain = Source(seq)
    pekkoMap1 = Source(seq).map(_ + 1)
    pekkoFilter1 = Source(seq).filter(_ % 2 != 0)
    pekkoFlatMap1 = Source(0 until 100).flatMapConcat(_ => Source(0 until 100))
    pekkoTakeDrop = Source(seq).drop(N / 2).take(N / 2)
    pekkoMapFilterFlatMap = Source(seq).map(_ + 1).filter(_ % 2 != 0).flatMapConcat(_ => Source(0 until 100))

    // ---- Ox flows ----
    val oxBase = Flow.fromIterable(seq)
    oxSingleton = Flow.fromValues(42)
    oxDrain = Flow.fromIterable(seq)
    oxMap1 = oxBase.map(_ + 1)
    oxFilter1 = oxBase.filter(_ % 2 != 0)
    oxFlatMap1 = Flow.fromIterable(0 until 100).flatMap(_ => Flow.fromIterable(0 until 100))
    oxTakeDrop = oxBase.drop(N / 2).take(N / 2)
    oxMapFilterFlatMap = oxBase.map(_ + 1).filter(_ % 2 != 0).flatMap(_ => Flow.fromIterable(0 until 100))
    oxMixed1 = chainOxMixed(Flow.fromIterable(0 until 200), 1, 100)
    oxMixed2 = chainOxMixed(Flow.fromIterable(0 until 100), 2, 20)
    oxMixed3 = chainOxMixed(Flow.fromIterable(0 until 100), 3, 10)
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  // ===========================================================================
  // Chain helpers — each level: filter(~50%).flatMap(×innerN).map(1:1) ≈ (innerN/2)× net
  // ===========================================================================

  private def chainZbMixed(s: Stream[Nothing, Int], n: Int, innerN: Int): Stream[Nothing, Int] = {
    var result = s; var i = 0
    while (i < n) {
      result = result.filter(_ % 2 != 0).flatMap(_ => Stream.range(0, innerN)).map(_ + 1)
      i += 1
    }
    result
  }

  private def chainFs2Mixed(s: Fs2Stream[IO, Int], n: Int, innerN: Int): Fs2Stream[IO, Int] = {
    var result = s; var i = 0
    while (i < n) {
      result = result.filter(_ % 2 != 0).flatMap(_ => Fs2Stream.range(0, innerN)).map(_ + 1)
      i += 1
    }
    result
  }

  private def chainKyoMixed(s: KyoStream[Int, Any], n: Int, innerN: Int): KyoStream[Int, Any] = {
    var result = s; var i = 0
    while (i < n) {
      result = result.filter(_ % 2 != 0).flatMap(_ => KyoStream.init(0 until innerN)).map(_ + 1)
      i += 1
    }
    result
  }

  private def chainPekkoMixed(n: Int, innerN: Int)(source: Source[Int, ?]): Source[Int, ?] = {
    var s = source; var i = 0
    while (i < n) {
      s = s.filter(_ % 2 != 0).flatMapConcat(_ => Source(0 until innerN)).map(_ + 1)
      i += 1
    }
    s
  }

  private def chainOxMixed(s: Flow[Int], n: Int, innerN: Int): Flow[Int] = {
    var result = s; var i = 0
    while (i < n) {
      result = result.filter(_ % 2 != 0).flatMap(_ => Flow.fromIterable(0 until innerN)).map(_ + 1)
      i += 1
    }
    result
  }

  // ===========================================================================
  // Eval helpers
  // ===========================================================================

  private def zbFold(s: Stream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(_)  => 0L
    }

  private def fs2Fold(s: Fs2Stream[IO, Int]): Long =
    s.compile.fold(0L)(_ + _).unsafeRunSync()

  private def kyoFold(s: KyoStream[Int, Any]): Long =
    s.fold(0L)(_ + _).eval

  private def pekkoFold(src: Source[Int, ?]): Long =
    Await.result(src.runWith(PekkaSink.fold(0L)(_ + _)), ScalaDuration.Inf)

  private def oxFold(f: Flow[Int]): Long = f.runFold(0L)((acc, i) => acc + i)

  // ===========================================================================
  // ZB benchmarks (10 methods)
  // ===========================================================================

  @Benchmark def zb_singleton(): Long        = zbFold(zbSingleton)
  @Benchmark def zb_drain(): Long            = zbFold(zbDrain)
  @Benchmark def zb_map_1(): Long            = zbFold(zbMap1)
  @Benchmark def zb_filter_1(): Long         = zbFold(zbFilter1)
  @Benchmark def zb_flatMap_1(): Long        = zbFold(zbFlatMap1)
  @Benchmark def zb_takeDrop(): Long         = zbFold(zbTakeDrop)
  @Benchmark def zb_mapFilterFlatMap(): Long = zbFold(zbMapFilterFlatMap)
  @Benchmark def zb_mixed_1(): Long          = zbFold(zbMixed1)
  @Benchmark def zb_mixed_2(): Long          = zbFold(zbMixed2)
  @Benchmark def zb_mixed_3(): Long          = zbFold(zbMixed3)

  // ===========================================================================
  // fs2 benchmarks (10 methods)
  // ===========================================================================

  @Benchmark def fs2_singleton(): Long        = fs2Fold(fs2Singleton)
  @Benchmark def fs2_drain(): Long            = fs2Fold(fs2Drain)
  @Benchmark def fs2_map_1(): Long            = fs2Fold(fs2Map1)
  @Benchmark def fs2_filter_1(): Long         = fs2Fold(fs2Filter1)
  @Benchmark def fs2_flatMap_1(): Long        = fs2Fold(fs2FlatMap1)
  @Benchmark def fs2_takeDrop(): Long         = fs2Fold(fs2TakeDrop)
  @Benchmark def fs2_mapFilterFlatMap(): Long = fs2Fold(fs2MapFilterFlatMap)
  @Benchmark def fs2_mixed_1(): Long          = fs2Fold(fs2Mixed1)
  @Benchmark def fs2_mixed_2(): Long          = fs2Fold(fs2Mixed2)
  @Benchmark def fs2_mixed_3(): Long          = fs2Fold(fs2Mixed3)

  // ===========================================================================
  // Kyo benchmarks (10 methods)
  // ===========================================================================

  @Benchmark def kyo_singleton(): Long        = kyoFold(kyoSingleton)
  @Benchmark def kyo_drain(): Long            = kyoFold(kyoDrain)
  @Benchmark def kyo_map_1(): Long            = kyoFold(kyoMap1)
  @Benchmark def kyo_filter_1(): Long         = kyoFold(kyoFilter1)
  @Benchmark def kyo_flatMap_1(): Long        = kyoFold(kyoFlatMap1)
  @Benchmark def kyo_takeDrop(): Long         = kyoFold(kyoTakeDrop)
  @Benchmark def kyo_mapFilterFlatMap(): Long = kyoFold(kyoMapFilterFlatMap)
  @Benchmark def kyo_mixed_1(): Long          = kyoFold(kyoMixed1)
  @Benchmark def kyo_mixed_2(): Long          = kyoFold(kyoMixed2)
  @Benchmark def kyo_mixed_3(): Long          = kyoFold(kyoMixed3)

  // ===========================================================================
  // Pekko benchmarks (10 methods)
  // ===========================================================================

  @Benchmark def pekko_singleton(): Long        = pekkoFold(pekkoSingleton)
  @Benchmark def pekko_drain(): Long            = pekkoFold(pekkoDrain)
  @Benchmark def pekko_map_1(): Long            = pekkoFold(pekkoMap1)
  @Benchmark def pekko_filter_1(): Long         = pekkoFold(pekkoFilter1)
  @Benchmark def pekko_flatMap_1(): Long        = pekkoFold(pekkoFlatMap1)
  @Benchmark def pekko_takeDrop(): Long         = pekkoFold(pekkoTakeDrop)
  @Benchmark def pekko_mapFilterFlatMap(): Long = pekkoFold(pekkoMapFilterFlatMap)
  @Benchmark def pekko_mixed_1(): Long          = pekkoFold(chainPekkoMixed(1, 100)(Source(0 until 200)))
  @Benchmark def pekko_mixed_2(): Long          = pekkoFold(chainPekkoMixed(2, 20)(Source(0 until 100)))
  @Benchmark def pekko_mixed_3(): Long          = pekkoFold(chainPekkoMixed(3, 10)(Source(0 until 100)))

  // ===========================================================================
  // Ox benchmarks (10 methods)
  // ===========================================================================

  @Benchmark def ox_singleton(): Long        = oxFold(oxSingleton)
  @Benchmark def ox_drain(): Long            = oxFold(oxDrain)
  @Benchmark def ox_map_1(): Long            = oxFold(oxMap1)
  @Benchmark def ox_filter_1(): Long         = oxFold(oxFilter1)
  @Benchmark def ox_flatMap_1(): Long        = oxFold(oxFlatMap1)
  @Benchmark def ox_takeDrop(): Long         = oxFold(oxTakeDrop)
  @Benchmark def ox_mapFilterFlatMap(): Long = oxFold(oxMapFilterFlatMap)
  @Benchmark def ox_mixed_1(): Long          = oxFold(oxMixed1)
  @Benchmark def ox_mixed_2(): Long          = oxFold(oxMixed2)
  @Benchmark def ox_mixed_3(): Long          = oxFold(oxMixed3)

  // ===========================================================================
  // Stack safety: deeply nested flatMap (Ox vs ZB)
  // ===========================================================================

  @Benchmark def ox_nested_flatMap(): Long = {
    var flow: Flow[Int] = Flow.fromValues(1)
    var i               = 0
    while (i < 10000) {
      flow = flow.flatMap(_ => Flow.fromValues(1))
      i += 1
    }
    oxFold(flow)
  }

  @Benchmark def zb_nested_flatMap(): Long = {
    var stream: Stream[Nothing, Int] = Stream.succeed(1)
    var i                            = 0
    while (i < 10000) {
      stream = stream.flatMap(_ => Stream.succeed(1))
      i += 1
    }
    zbFold(stream)
  }

  @Benchmark def fs2_nested_flatMap(): Long = {
    var stream: Fs2Stream[IO, Int] = Fs2Stream.emit(1).covary[IO]
    var i                          = 0
    while (i < 10000) {
      stream = stream.flatMap(_ => Fs2Stream.emit(1).covary[IO])
      i += 1
    }
    fs2Fold(stream)
  }

  @Benchmark def kyo_nested_flatMap(): Long = {
    var stream: KyoStream[Int, Any] = KyoStream.init(Seq(1))
    var i                           = 0
    while (i < 10000) {
      stream = stream.flatMap(_ => KyoStream.init(Seq(1)))
      i += 1
    }
    kyoFold(stream)
  }

  // ===========================================================================
  // Stack safety: deeply nested concat (10k single-element streams)
  // ===========================================================================

  @Benchmark def zb_nested_concat(): Long = {
    var stream: Stream[Nothing, Int] = Stream.succeed(1)
    var i                            = 0
    while (i < 10000) {
      stream = stream ++ Stream.succeed(1)
      i += 1
    }
    zbFold(stream)
  }

  @Benchmark def fs2_nested_concat(): Long = {
    var stream: Fs2Stream[IO, Int] = Fs2Stream.emit(1).covary[IO]
    var i                          = 0
    while (i < 10000) {
      stream = stream ++ Fs2Stream.emit(1).covary[IO]
      i += 1
    }
    fs2Fold(stream)
  }

  @Benchmark def kyo_nested_concat(): Long = {
    var stream: KyoStream[Int, Any] = KyoStream.init(Seq(1))
    var i                           = 0
    while (i < 10000) {
      stream = stream.concat(KyoStream.init(Seq(1)))
      i += 1
    }
    kyoFold(stream)
  }

  @Benchmark def ox_nested_concat(): Long = {
    var flow: Flow[Int] = Flow.fromValues(1)
    var i               = 0
    while (i < 10000) {
      flow = flow.concat(Flow.fromValues(1))
      i += 1
    }
    oxFold(flow)
  }

  @Benchmark def pekko_nested_concat(): Long = {
    var src: Source[Int, ?] = Source.single(1)
    var i                   = 0
    while (i < 10000) {
      src = src.concat(Source.single(1))
      i += 1
    }
    pekkoFold(src)
  }

}
