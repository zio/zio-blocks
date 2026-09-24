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

package zio.blocks.streams.bench

import org.openjdk.jmh.annotations._
import zio.blocks.streams.Stream

import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

// ---- fs2 ----
import fs2.{Pure, Stream => Fs2Stream}

// ---- Pekko Streams ----
import org.apache.pekko.stream.scaladsl.Source

// ---- Kyo ----
import kyo.{Scope as _, Stream => KyoStream, *}

// ---- Ox ----
import ox.flow.Flow

/**
 * Benchmark: Stream pipeline throughput.
 *
 * Compares throughput across five libraries on 10,000 pre-built integers.
 *
 * ==Source convention==
 * Every library consumes the same pre-built `Vector`. Native range-source
 * performance is intentionally outside this suite.
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

  @Param(Array("10000"))
  var N: Int = uninitialized

  // Pre-built vector for libraries that don't have a zero-alloc range source.
  var seq: Vector[Int]  = uninitialized
  var seqA: Vector[Int] = uninitialized
  var seqB: Vector[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    seq = (0 until N).toVector
    seqA = (0 until N / 2).toVector
    seqB = (N / 2 until N).toVector
  }

  // ---- helpers ---------------------------------------------------------------

  private def zbFold(s: Stream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(e)  => e
    }

  private def fs2Fold(s: Fs2Stream[Pure, Int]): Long =
    s.compile.fold(0L)(_ + _)

  private def kyoFold(s: KyoStream[Int, Any]): Long =
    s.foldPure(0L)((acc, i) => acc + i).eval

  private def oxFold(f: Flow[Int]): Long =
    f.runFold(0L)((acc, i) => acc + i)

  // ===========================================================================
  // filterMap — filter(% 2 == 0) → map(+ 1) → fold  (canonical comparison)
  // ===========================================================================

  @Benchmark def zb_filterMap(): Long =
    zbFold(Stream.fromIterable(seq).filter(_ % 2 == 0).map(_ + 1))

  @Benchmark def fs2_filterMap(): Long =
    fs2Fold(Fs2Stream.emits(seq).filter(_ % 2 == 0).map(_ + 1))

  @Benchmark def pekka_filterMap(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).filter(_ % 2 == 0).map(_ + 1))

  @Benchmark def kyo_filterMap(): Long =
    kyoFold(KyoStream.init(seq).filterPure(_ % 2 == 0).mapPure(_ + 1))

  @Benchmark def ox_filterMap(): Long =
    oxFold(Flow.fromIterable(seq).filter(_ % 2 == 0).map(_ + 1))

  // ===========================================================================
  // map — single map stage
  // ===========================================================================

  @Benchmark def zb_map(): Long =
    zbFold(Stream.fromIterable(seq).map(_ * 2))

  @Benchmark def fs2_map(): Long =
    fs2Fold(Fs2Stream.emits(seq).map(_ * 2))

  @Benchmark def pekka_map(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).map(_ * 2))

  @Benchmark def kyo_map(): Long =
    kyoFold(KyoStream.init(seq).mapPure(_ * 2))

  @Benchmark def ox_map(): Long =
    oxFold(Flow.fromIterable(seq).map(_ * 2))

  // ===========================================================================
  // filter — single filter stage
  // ===========================================================================

  @Benchmark def zb_filter(): Long =
    zbFold(Stream.fromIterable(seq).filter(_ % 2 == 0))

  @Benchmark def fs2_filter(): Long =
    fs2Fold(Fs2Stream.emits(seq).filter(_ % 2 == 0))

  @Benchmark def pekka_filter(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).filter(_ % 2 == 0))

  @Benchmark def kyo_filter(): Long =
    kyoFold(KyoStream.init(seq).filterPure(_ % 2 == 0))

  @Benchmark def ox_filter(): Long =
    oxFold(Flow.fromIterable(seq).filter(_ % 2 == 0))

  // ===========================================================================
  // chainedMaps — five consecutive maps (interpreter-overhead / fusion test)
  // ===========================================================================

  @Benchmark def zb_chainedMaps(): Long =
    zbFold(Stream.fromIterable(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  @Benchmark def fs2_chainedMaps(): Long =
    fs2Fold(Fs2Stream.emits(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  @Benchmark def pekka_chainedMaps(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  @Benchmark def kyo_chainedMaps(): Long =
    kyoFold(KyoStream.init(seq).mapPure(_ + 1).mapPure(_ + 1).mapPure(_ + 1).mapPure(_ + 1).mapPure(_ + 1))

  @Benchmark def ox_chainedMaps(): Long =
    oxFold(Flow.fromIterable(seq).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1))

  // ===========================================================================
  // flatMap — one-to-one flatMap
  // ===========================================================================

  @Benchmark def zb_flatMap(): Long =
    zbFold(Stream.fromIterable(seq).flatMap(i => Stream.succeed(i * 2)))

  @Benchmark def fs2_flatMap(): Long =
    fs2Fold(Fs2Stream.emits(seq).flatMap(i => Fs2Stream.emit(i * 2)))

  @Benchmark def pekka_flatMap(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).flatMapConcat(i => Source.single(i * 2)))

  @Benchmark def kyo_flatMap(): Long =
    kyoFold(KyoStream.init(seq).flatMap(i => KyoStream.init(Vector(i * 2))))

  @Benchmark def ox_flatMap(): Long =
    oxFold(Flow.fromIterable(seq).flatMap(i => Flow.fromValues(i * 2)))

  // ===========================================================================
  // take — take first half, fold
  // ===========================================================================

  @Benchmark def zb_take(): Long =
    zbFold(Stream.fromIterable(seq).take((N / 2).toLong))

  @Benchmark def fs2_take(): Long =
    fs2Fold(Fs2Stream.emits(seq).take((N / 2).toLong))

  @Benchmark def pekka_take(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).take(N / 2))

  @Benchmark def kyo_take(): Long =
    kyoFold(KyoStream.init(seq).take(N / 2))

  @Benchmark def ox_take(): Long =
    oxFold(Flow.fromIterable(seq).take(N / 2))

  // ===========================================================================
  // takeWhile — take while element < N/2
  // ===========================================================================

  @Benchmark def zb_takeWhile(): Long =
    zbFold(Stream.fromIterable(seq).takeWhile(_ < N / 2))

  @Benchmark def fs2_takeWhile(): Long =
    fs2Fold(Fs2Stream.emits(seq).takeWhile(_ < N / 2))

  @Benchmark def pekka_takeWhile(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).takeWhile(_ < N / 2))

  @Benchmark def kyo_takeWhile(): Long =
    kyoFold(KyoStream.init(seq).takeWhilePure(_ < N / 2))

  @Benchmark def ox_takeWhile(): Long =
    oxFold(Flow.fromIterable(seq).takeWhile(_ < N / 2))

  // ===========================================================================
  // concat — two halves concatenated
  // ===========================================================================

  @Benchmark def zb_concat(): Long =
    zbFold(Stream.fromIterable(seqA) ++ Stream.fromIterable(seqB))

  @Benchmark def fs2_concat(): Long =
    fs2Fold(Fs2Stream.emits(seqA) ++ Fs2Stream.emits(seqB))

  @Benchmark def pekka_concat(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seqA).concatLazy(Source(seqB)))

  @Benchmark def kyo_concat(): Long =
    kyoFold(KyoStream.init(seqA).concat(KyoStream.init(seqB)))

  @Benchmark def ox_concat(): Long =
    oxFold(Flow.fromIterable(seqA).concat(Flow.fromIterable(seqB)))

  // ===========================================================================
  // drain — discard all elements
  // ===========================================================================

  @Benchmark def zb_drain(): Unit =
    Stream.fromIterable(seq).runDrain match { case Right(()) => (); case Left(e) => e }

  @Benchmark def fs2_drain(): Unit =
    Fs2Stream.emits(seq).compile.drain

  @Benchmark def pekka_drain(runtime: PekkoBenchmarkRuntime): Unit =
    runtime.drain(Source(seq))

  @Benchmark def kyo_drain(): Unit =
    KyoStream.init(seq).discard.eval

  @Benchmark def ox_drain(): Unit =
    Flow.fromIterable(seq).runDrain()

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
    zbFold(chainMaps(Stream.fromIterable(seq), 10))

  @Benchmark def fs2_chainedMaps10(): Long = {
    var s: Fs2Stream[Pure, Int] = Fs2Stream.emits(seq)
    var i                       = 0; while (i < 10) { s = s.map(_ + 1); i += 1 }
    fs2Fold(s)
  }

  @Benchmark def kyo_chainedMaps10(): Long = {
    var s: KyoStream[Int, Any] = KyoStream.init(seq)
    var i                      = 0; while (i < 10) { s = s.mapPure(_ + 1); i += 1 }
    s.foldPure(0L)(_ + _).eval
  }

  @Benchmark def pekka_chainedMaps10(runtime: PekkoBenchmarkRuntime): Long = {
    var source = Source(seq)
    var i      = 0; while (i < 10) { source = source.map(_ + 1); i += 1 }
    runtime.fold(source)
  }

  @Benchmark def ox_chainedMaps10(): Long = {
    var f: Flow[Int] = Flow.fromIterable(seq)
    var i            = 0; while (i < 10) { f = f.map(_ + 1); i += 1 }
    oxFold(f)
  }

  // ===========================================================================
  // chainedMaps100 — 100 chained maps (deep pipeline)
  // ===========================================================================

  @Benchmark def zb_chainedMaps100(): Long =
    zbFold(chainMaps(Stream.fromIterable(seq), 100))

  @Benchmark def fs2_chainedMaps100(): Long = {
    var s: Fs2Stream[Pure, Int] = Fs2Stream.emits(seq)
    var i                       = 0; while (i < 100) { s = s.map(_ + 1); i += 1 }
    fs2Fold(s)
  }

  @Benchmark def kyo_chainedMaps100(): Long = {
    var s: KyoStream[Int, Any] = KyoStream.init(seq)
    var i                      = 0; while (i < 100) { s = s.mapPure(_ + 1); i += 1 }
    s.foldPure(0L)(_ + _).eval
  }

  @Benchmark def pekka_chainedMaps100(runtime: PekkoBenchmarkRuntime): Long = {
    var source = Source(seq)
    var i      = 0; while (i < 100) { source = source.map(_ + 1); i += 1 }
    runtime.fold(source)
  }

  @Benchmark def ox_chainedMaps100(): Long = {
    var f: Flow[Int] = Flow.fromIterable(seq)
    var i            = 0; while (i < 100) { f = f.map(_ + 1); i += 1 }
    oxFold(f)
  }

  // ===========================================================================
  // filterMapChain — alternating filter/map chain (10 ops: 5 filter + 5 map)
  // ===========================================================================

  @Benchmark def zb_filterMapChain(): Long =
    zbFold(
      Stream
        .fromIterable(seq)
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
    fs2Fold(
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
    )

  @Benchmark def kyo_filterMapChain(): Long =
    KyoStream
      .init(seq)
      .filterPure(_ % 2 == 0)
      .mapPure(_ + 1)
      .filterPure(_ % 3 == 0)
      .mapPure(_ * 2)
      .filterPure(_ % 5 == 0)
      .mapPure(_ + 3)
      .filterPure(_ > 0)
      .mapPure(_ - 1)
      .filterPure(_ < 100000)
      .mapPure(_ + 7)
      .foldPure(0L)(_ + _)
      .eval

  @Benchmark def pekka_filterMapChain(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(
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

  @Benchmark def ox_filterMapChain(): Long =
    oxFold(
      Flow
        .fromIterable(seq)
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
    zbFold(Stream.fromIterable(seq).drop(N / 2).take((N / 2).toLong))

  @Benchmark def fs2_takeDrop(): Long =
    fs2Fold(Fs2Stream.emits(seq).drop(N / 2).take((N / 2).toLong))

  @Benchmark def kyo_takeDrop(): Long =
    kyoFold(KyoStream.init(seq).drop(N / 2).take(N / 2))

  @Benchmark def pekka_takeDrop(runtime: PekkoBenchmarkRuntime): Long =
    runtime.fold(Source(seq).drop(N / 2).take(N / 2))

  @Benchmark def ox_takeDrop(): Long =
    oxFold(Flow.fromIterable(seq).drop(N / 2).take(N / 2))
}
