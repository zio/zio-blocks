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
import zio.blocks.streams.{Stream => ZbStream}
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Stream => Fs2Stream}

import kyo.{KyoApp, Stream as KyoStream, Sync as KyoSync, *}
import kyo.AllowUnsafe.embrace.danger
import org.apache.pekko.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext, Future}

import ox.channels.BufferCapacity
import ox.flow.Flow

/**
 * Benchmark: Concurrent stream operator throughput — mapPar, mergeAll,
 * flatMapPar.
 *
 * ==Purpose==
 * Measures sustained throughput for parallel stream operations across ZIO
 * Blocks, fs2, Kyo, Apache Pekko Streams, and Ox. Each operator uses the
 * library's idiomatic API for unordered parallelism.
 *
 * ==Why Unordered?==
 * All benchmarks use unordered parallel variants (`mapParUnordered`,
 * `parEvalMapUnordered`, `mapAsyncUnordered`, `flatMapMerge`/`flattenPar`) for
 * a fair comparison. Ordered variants require buffering to reconstruct input
 * order, adding overhead not intrinsic to parallelism.
 *
 * ==Kyo==
 * Kyo 1.0.0-RC6 participates in `mapPar` through `mapParUnordered`. Its public
 * `collectAll` and binary `merge` APIs do not expose this benchmark's bounded
 * dynamic fan-in contract, so Kyo is N/A for `mergeAll` and `flatMapPar`. Kyo's
 * `mapParUnordered` buffer size is fixed at 16 and is distinct from
 * `parallelism`.
 *
 * ==Operators==
 *   - `mapPar` — transform each element independently in parallel (1M elements)
 *   - `mergeAll` — fan-in 100 inner streams × 10K elements each (1M total)
 *     concurrently
 *   - `flatMapPar` — map each element to an inner stream and merge (100K × 10 =
 *     1M total)
 *
 * ==Workloads (`workload` param)==
 *   - `light` — `x + 1` (trivial; isolates infrastructure overhead)
 *   - `heavy` — 100-iteration arithmetic loop. In `mapPar` it exercises
 *     callback scheduling; in fan-in cells it measures fan-in under inner work,
 *     not equivalent CPU scheduler scaling.
 *
 * ==Parallelism (`parallelism` param)==
 * `1`, `8`, `16` — sequential baseline, typical CPU count, oversubscribed.
 *
 * ==Effect Wrappers==
 * fs2 `parEvalMapUnordered` requires `A => IO[B]`; Kyo `mapParUnordered`
 * requires an effectful callback; Pekko `mapAsyncUnordered` requires
 * `A => Future[B]`. ZIO Blocks and Ox accept direct callbacks. `mapPar`
 * measures scheduled callback work; `mergeAll` and `flatMapPar` measure bounded
 * active-stream fan-in, not a promise that pure callbacks run on equivalent
 * scheduler threads. Native documented buffering defaults are used without
 * provider-specific extra buffers. Ox uses `BufferCapacity.default`; no
 * explicit source buffer is inserted.
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
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
class StreamConcurrentBench {

  @Param(Array("1000000"))
  var N: Int = uninitialized

  @Param(Array("1", "8", "16"))
  var parallelism: Int = uninitialized

  @Param(Array("light", "heavy"))
  var workload: String = uninitialized

  private val outerN    = 100
  private val fmpOuterN = 1_000

  private var workFn: Int => Int = uninitialized
  private val kyoBufferSize      = 16

  private given BufferCapacity = BufferCapacity.default
  @Setup(Level.Trial)
  def setup(): Unit =
    workFn = workload match {
      case "heavy" => { x =>
        var s = x.toLong
        var i = 0
        while (i < 100) { s = (s * 31 + 17) ^ (s >>> 3); i += 1 }
        s.toInt
      }
      case _ => x => x + 1
    }

  private def zbFold(s: ZbStream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(_)  => 0L
    }

  private def fs2Fold(s: Fs2Stream[IO, Int]): Long =
    s.compile.fold(0L)(_ + _).unsafeRunSync()

  private def oxFold(f: Flow[Int]): Long =
    f.runFold(0L)((acc, i) => acc + i)

  @Benchmark
  def zb_mapPar(): Long =
    zbFold(ZbStream.range(0, N).mapPar(parallelism)(workFn))

  @Benchmark
  def fs2_mapPar(): Long =
    fs2Fold(
      Fs2Stream
        .range(0, N)
        .covary[IO]
        .parEvalMapUnordered(parallelism)(a => IO(workFn(a)))
    )

  @Benchmark
  def kyo_mapPar(): Long = {
    val source: KyoStream[Int, Any] = KyoStream.range(0, N)
    val stream                      = source.mapParUnordered(parallelism, kyoBufferSize)(value => KyoSync.defer(workFn(value)))
    KyoApp.Unsafe.runAndBlock(kyo.Duration.Infinity)(stream.foldPure(0L)(_ + _)).getOrThrow
  }

  @Benchmark
  def pekko_mapPar(runtime: PekkoBenchmarkRuntime): Long = {
    given ExecutionContext = runtime.executionContext
    runtime.fold(
      Source(0 until N).mapAsyncUnordered(parallelism)(a => Future(workFn(a)))
    )
  }

  @Benchmark
  def ox_mapPar(): Long =
    oxFold(
      Flow.fromIterable(0 until N).mapParUnordered(parallelism)(workFn)
    )

  @Benchmark
  def zb_mergeAll(): Long = {
    val fn      = workFn
    val innerN  = N / outerN
    val streams = ZbStream.range(0, outerN).map(i => ZbStream.range(i * innerN, (i + 1) * innerN).map(fn))
    zbFold(ZbStream.mergeAll(parallelism)(streams))
  }

  @Benchmark
  def fs2_mergeAll(): Long = {
    val fn                                         = workFn
    val innerN                                     = N / outerN
    val streams: Fs2Stream[IO, Fs2Stream[IO, Int]] =
      Fs2Stream
        .range(0, outerN)
        .covary[IO]
        .map(i => Fs2Stream.range(i * innerN, (i + 1) * innerN).covary[IO].map(fn))
    fs2Fold(streams.parJoin(parallelism))
  }

  @Benchmark
  def pekko_mergeAll(runtime: PekkoBenchmarkRuntime): Long = {
    val fn     = workFn
    val innerN = N / outerN
    runtime.fold(
      Source(0 until outerN)
        .flatMapMerge(parallelism, i => Source(i * innerN until (i + 1) * innerN).map(fn))
    )
  }

  @Benchmark
  def ox_mergeAll(): Long = {
    val fn                     = workFn
    val innerN                 = N / outerN
    val flows: Flow[Flow[Int]] =
      Flow
        .fromIterable(0 until outerN)
        .map(i => Flow.fromIterable(i * innerN until (i + 1) * innerN).map(fn))
    oxFold(flows.flattenPar(parallelism))
  }

  @Benchmark
  def zb_flatMapPar(): Long = {
    val fn        = workFn
    val fmpInnerN = N / fmpOuterN
    zbFold(
      ZbStream
        .range(0, fmpOuterN)
        .flatMapPar(parallelism)(i => ZbStream.range(0, fmpInnerN).map(j => fn(i * fmpInnerN + j)))
    )
  }

  @Benchmark
  def fs2_flatMapPar(): Long = {
    val fn        = workFn
    val fmpInnerN = N / fmpOuterN
    fs2Fold(
      Fs2Stream
        .range(0, fmpOuterN)
        .covary[IO]
        .map(i => Fs2Stream.range(0, fmpInnerN).covary[IO].map(j => fn(i * fmpInnerN + j)))
        .parJoin(parallelism)
    )
  }

  @Benchmark
  def pekko_flatMapPar(runtime: PekkoBenchmarkRuntime): Long = {
    val fn        = workFn
    val fmpInnerN = N / fmpOuterN
    runtime.fold(
      Source(0 until fmpOuterN)
        .flatMapMerge(parallelism, i => Source(0 until fmpInnerN).map(j => fn(i * fmpInnerN + j)))
    )
  }

  @Benchmark
  def ox_flatMapPar(): Long = {
    val fn        = workFn
    val fmpInnerN = N / fmpOuterN
    oxFold(
      Flow
        .fromIterable(0 until fmpOuterN)
        .map(i => Flow.fromIterable(0 until fmpInnerN).map(j => fn(i * fmpInnerN + j)))
        .flattenPar(parallelism)
    )
  }

}
