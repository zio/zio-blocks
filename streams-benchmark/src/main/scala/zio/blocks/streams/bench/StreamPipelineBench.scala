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
import org.apache.pekko.stream.scaladsl.{Flow => PekkoFlow, Sink => PekkaSink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import scala.concurrent.Await
import scala.concurrent.duration.Duration as ScalaDuration

// ---- Kyo ----
import kyo.{Stream => KyoStream, *}

/**
 * Benchmark: Stream pipeline throughput.
 *
 * Compares throughput across four libraries on 10,000 integers.
 *
 * ==Source convention==
 *   - ZIO Blocks uses `Stream.range(0, N)` — its purpose-built zero-allocation
 *     range source backed by `Reader.fromRange` with an `AtomicInteger` index.
 *   - Other libraries use their own idiomatic sources:
 *     - Kyo: `Stream.init(seq)` from a pre-built `Vector` (chunk-based)
 *     - fs2: `Stream.emits(seq)` from a pre-built `Vector`
 *     - Pekko: `Source(seq)` from a pre-built `Vector`
 *
 * Each library uses its best available source. ZIO Blocks having a fast `range`
 * constructor is a design advantage, not an unfair comparison.
 *
 * ==Operations benchmarked==
 * filterMap, map, filter, chainedMaps(×5), flatMap, take, takeWhile, concat,
 * drain — see BENCHMARK-RESEARCH.md for rationale.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgsPrepend = Array(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
  )
)
class StreamPipelineBench {

  val N: Int = 10000

  // Pre-built vector for libraries that don't have a zero-alloc range source.
  val seq: Vector[Int]  = (0 until N).toVector
  val seqA: Vector[Int] = (0 until N / 2).toVector
  val seqB: Vector[Int] = (N / 2 until N).toVector

  // ---- Pekko state -----------------------------------------------------------

  implicit var pekkaSystem: ActorSystem = uninitialized
  implicit var pekkaMat: Materializer   = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    pekkaSystem = ActorSystem("stream-bench")
    pekkaMat = SystemMaterializer(pekkaSystem).materializer
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    Await.result(pekkaSystem.terminate(), ScalaDuration(30, "s"))

  // ---- helpers ---------------------------------------------------------------

  private def zbFold(s: Stream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(e)  => e
    }

  private def pekkaFold(src: Source[Int, ?]): Long =
    Await.result(src.runWith(PekkaSink.fold(0L)(_ + _)), ScalaDuration.Inf)

  private def kyoFold(s: KyoStream[Int, Any]): Long =
    s.fold(0L)((acc, i) => acc + i).eval

  // ===========================================================================
  // filterMap — filter(% 2 == 0) → map(+ 1) → fold  (canonical comparison)
  // ===========================================================================

  @Benchmark def zb_filterMap(): Long =
    zbFold(Stream.range(0, N).filter(_ % 2 == 0).map(_ + 1))

  @Benchmark def fs2_filterMap(): Long =
    Fs2Stream
      .emits(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_filterMap(): Long =
    pekkaFold(Source(seq).filter(_ % 2 == 0).map(_ + 1))

  @Benchmark def kyo_filterMap(): Long =
    kyoFold(KyoStream.init(seq).filter(_ % 2 == 0).map(_ + 1))

  // ===========================================================================
  // map — single map stage
  // ===========================================================================

  @Benchmark def zb_map(): Long =
    zbFold(Stream.range(0, N).map(_ * 2))

  @Benchmark def fs2_map(): Long =
    Fs2Stream
      .emits(seq)
      .map(_ * 2)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_map(): Long =
    pekkaFold(Source(seq).map(_ * 2))

  @Benchmark def kyo_map(): Long =
    kyoFold(KyoStream.init(seq).map(_ * 2))

  // ===========================================================================
  // filter — single filter stage
  // ===========================================================================

  @Benchmark def zb_filter(): Long =
    zbFold(Stream.range(0, N).filter(_ % 2 == 0))

  @Benchmark def fs2_filter(): Long =
    Fs2Stream
      .emits(seq)
      .filter(_ % 2 == 0)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_filter(): Long =
    pekkaFold(Source(seq).filter(_ % 2 == 0))

  @Benchmark def kyo_filter(): Long =
    kyoFold(KyoStream.init(seq).filter(_ % 2 == 0))

  // ===========================================================================
  // chainedMaps — five consecutive maps (interpreter-overhead / fusion test)
  // ===========================================================================

  @Benchmark def zb_chainedMaps(): Long =
    zbFold(Stream.range(0, N).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  @Benchmark def fs2_chainedMaps(): Long =
    Fs2Stream
      .emits(seq)
      .map(_ + 1)
      .map(_ + 1)
      .map(_ + 1)
      .map(_ + 1)
      .map(_ + 1)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_chainedMaps(): Long =
    pekkaFold(Source(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  @Benchmark def kyo_chainedMaps(): Long =
    kyoFold(KyoStream.init(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  // ===========================================================================
  // flatMap — one-to-one flatMap
  // ===========================================================================

  @Benchmark def zb_flatMap(): Long =
    zbFold(Stream.range(0, N).flatMap(i => Stream.succeed(i * 2)))

  @Benchmark def fs2_flatMap(): Long =
    Fs2Stream
      .emits(seq)
      .flatMap(i => Fs2Stream.emit(i * 2))
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_flatMap(): Long =
    pekkaFold(Source(seq).flatMapConcat(i => Source.single(i * 2)))

  @Benchmark def kyo_flatMap(): Long =
    kyoFold(KyoStream.init(seq).flatMap(i => KyoStream.init(Vector(i * 2))))

  // ===========================================================================
  // take — take first half, fold
  // ===========================================================================

  @Benchmark def zb_take(): Long =
    zbFold(Stream.range(0, N).take((N / 2).toLong))

  @Benchmark def fs2_take(): Long =
    Fs2Stream
      .emits(seq)
      .take((N / 2).toLong)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_take(): Long =
    pekkaFold(Source(seq).take(N / 2))

  @Benchmark def kyo_take(): Long =
    kyoFold(KyoStream.init(seq).take(N / 2))

  // ===========================================================================
  // takeWhile — take while element < N/2
  // ===========================================================================

  @Benchmark def zb_takeWhile(): Long =
    zbFold(Stream.range(0, N).takeWhile(_ < N / 2))

  @Benchmark def fs2_takeWhile(): Long =
    Fs2Stream
      .emits(seq)
      .takeWhile(_ < N / 2)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_takeWhile(): Long =
    pekkaFold(Source(seq).takeWhile(_ < N / 2))

  @Benchmark def kyo_takeWhile(): Long =
    kyoFold(KyoStream.init(seq).takeWhile(_ < N / 2))

  // ===========================================================================
  // concat — two halves concatenated
  // ===========================================================================

  @Benchmark def zb_concat(): Long =
    zbFold(Stream.range(0, N / 2) ++ Stream.range(N / 2, N))

  @Benchmark def fs2_concat(): Long =
    (Fs2Stream.emits(seqA) ++ Fs2Stream.emits(seqB))
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def pekka_concat(): Long =
    pekkaFold(Source(seqA).concat(Source(seqB)))

  @Benchmark def kyo_concat(): Long =
    kyoFold(KyoStream.init(seqA).concat(KyoStream.init(seqB)))

  // ===========================================================================
  // drain — discard all elements
  // ===========================================================================

  @Benchmark def zb_drain(): Unit =
    Stream.range(0, N).runDrain match { case Right(()) => (); case Left(e) => e }

  @Benchmark def fs2_drain(): Unit =
    Fs2Stream.emits(seq).covary[IO].compile.drain.unsafeRunSync()

  @Benchmark def pekka_drain(): Unit =
    Await.result(Source(seq).runWith(PekkaSink.ignore), ScalaDuration.Inf)

  @Benchmark def kyo_drain(): Unit =
    KyoStream.init(seq).discard.eval

  // ===========================================================================
  // chainedMaps10 — 10 chained maps (moderate pipeline depth)
  // ===========================================================================

  private def chainMaps(s: Stream[Nothing, Int], n: Int): Stream[Nothing, Int] = {
    var result = s
    var i      = 0
    while (i < n) { result = result.map(_ + 1); i += 1 }
    result
  }

  @Benchmark def zb_chainedMaps10(): Long =
    zbFold(chainMaps(Stream.range(0, N), 10))

  @Benchmark def fs2_chainedMaps10(): Long = {
    var s: Fs2Stream[IO, Int] = Fs2Stream.emits(seq).covary[IO]
    var i                     = 0; while (i < 10) { s = s.map(_ + 1); i += 1 }
    s.compile.fold(0L)(_ + _).unsafeRunSync()
  }

  @Benchmark def kyo_chainedMaps10(): Long = {
    var s: KyoStream[Int, Any] = KyoStream.init(seq)
    var i                      = 0; while (i < 10) { s = s.map(_ + 1); i += 1 }
    s.fold(0L)(_ + _).eval
  }

  @Benchmark def pekka_chainedMaps10(): Long = {
    var flow = PekkoFlow[Int].map(_ + 1)
    var i    = 1; while (i < 10) { flow = flow.map(_ + 1); i += 1 }
    Await.result(Source(seq).via(flow).runWith(PekkaSink.fold(0L)(_ + _)), ScalaDuration.Inf)
  }

  // ===========================================================================
  // chainedMaps100 — 100 chained maps (deep pipeline)
  // ===========================================================================

  @Benchmark def zb_chainedMaps100(): Long =
    zbFold(chainMaps(Stream.range(0, N), 100))

  @Benchmark def fs2_chainedMaps100(): Long = {
    var s: Fs2Stream[IO, Int] = Fs2Stream.emits(seq).covary[IO]
    var i                     = 0; while (i < 100) { s = s.map(_ + 1); i += 1 }
    s.compile.fold(0L)(_ + _).unsafeRunSync()
  }

  @Benchmark def kyo_chainedMaps100(): Long = {
    var s: KyoStream[Int, Any] = KyoStream.init(seq)
    var i                      = 0; while (i < 100) { s = s.map(_ + 1); i += 1 }
    s.fold(0L)(_ + _).eval
  }

  @Benchmark def pekka_chainedMaps100(): Long = {
    var flow = PekkoFlow[Int].map(_ + 1)
    var i    = 1; while (i < 100) { flow = flow.map(_ + 1); i += 1 }
    Await.result(Source(seq).via(flow).runWith(PekkaSink.fold(0L)(_ + _)), ScalaDuration.Inf)
  }

  // ===========================================================================
  // filterMapChain — alternating filter/map chain (10 ops: 5 filter + 5 map)
  // ===========================================================================

  @Benchmark def zb_filterMapChain(): Long =
    zbFold(
      Stream
        .range(0, N)
        .filter(_ % 2 == 0)
        .map(_ + 1)
        .filter(_ % 3 == 0)
        .map(_ * 2)
        .filter(_ % 5 == 0)
        .map(_ + 3)
        .filter(_ > 0)
        .map(_ - 1)
        .filter(_ < 100000)
        .map(_ + 7)
    )

  @Benchmark def fs2_filterMapChain(): Long =
    Fs2Stream
      .emits(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def kyo_filterMapChain(): Long =
    KyoStream
      .init(seq)
      .filter(_ % 2 == 0)
      .map(_ + 1)
      .filter(_ % 3 == 0)
      .map(_ * 2)
      .filter(_ % 5 == 0)
      .map(_ + 3)
      .filter(_ > 0)
      .map(_ - 1)
      .filter(_ < 100000)
      .map(_ + 7)
      .fold(0L)(_ + _)
      .eval

  @Benchmark def pekka_filterMapChain(): Long =
    pekkaFold(
      Source(seq)
        .filter(_ % 2 == 0)
        .map(_ + 1)
        .filter(_ % 3 == 0)
        .map(_ * 2)
        .filter(_ % 5 == 0)
        .map(_ + 3)
        .filter(_ > 0)
        .map(_ - 1)
        .filter(_ < 100000)
        .map(_ + 7)
    )

  // ===========================================================================
  // takeDrop — drop(N/2) then take(N/2) on N-element range (stateful ops)
  // ===========================================================================

  @Benchmark def zb_takeDrop(): Long =
    zbFold(Stream.range(0, N).drop(N / 2).take((N / 2).toLong))

  @Benchmark def fs2_takeDrop(): Long =
    Fs2Stream
      .emits(seq)
      .drop(N / 2)
      .take((N / 2).toLong)
      .covary[IO]
      .compile
      .fold(0L)(_ + _)
      .unsafeRunSync()

  @Benchmark def kyo_takeDrop(): Long =
    kyoFold(KyoStream.init(seq).drop(N / 2).take(N / 2))

  @Benchmark def pekka_takeDrop(): Long =
    pekkaFold(Source(seq).drop(N / 2).take(N / 2))
}
