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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink => PekkaSink, Source}
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration as ScalaDuration

import ox.channels.BufferCapacity
import ox.flow.Flow

/**
 * Benchmark: Concurrent stream operator throughput — mapPar, mergeAll,
 * flatMapPar.
 *
 * ==Purpose==
 * Measures sustained throughput for parallel stream operations across four
 * streaming libraries: ZIO Blocks (zb), fs2, Apache Pekko Streams (pekko), and
 * Ox. Each operator uses the library's idiomatic API for unordered parallelism.
 *
 * ==Why Unordered?==
 * All benchmarks use unordered parallel variants (`mapParUnordered`,
 * `parEvalMapUnordered`, `mapAsyncUnordered`, `flatMapMerge`/`flattenPar`) for
 * a fair comparison. Ordered variants require buffering to reconstruct input
 * order, adding overhead not intrinsic to parallelism.
 *
 * ==Kyo==
 * Kyo 1.0-RC1 is included for mapPar using `mapParUnordered` which forks a
 * fiber per element via Channel + Fiber + Semaphore. Kyo is excluded from
 * mergeAll and flatMapPar because `Stream.collectAll` with pure `.map(fn)`
 * inner streams runs all work on a single scheduler thread (verified
 * empirically: fibers never yield during synchronous computation).
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
 *   - `heavy` — 100-iteration arithmetic loop (measures parallelism scaling)
 *
 * ==Parallelism (`parallelism` param)==
 * `1`, `8`, `16` — sequential baseline, typical CPU count, oversubscribed.
 *
 * ==Effect Wrappers==
 * fs2 `parEvalMapUnordered` requires `A => IO[B]`; Pekko `mapAsyncUnordered`
 * requires `A => Future[B]`. ZIO Blocks, Ox, and the underlying work function
 * are pure.
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

  @Param(Array("1", "8", "16"))
  var parallelism: Int = uninitialized

  @Param(Array("light", "heavy"))
  var workload: String = uninitialized

  private val N         = 1_000_000
  private val outerN    = 100
  private val innerN    = 10_000
  private val fmpOuterN = 1_000
  private val fmpInnerN = 1_000

  private var workFn: Int => Int = uninitialized

  implicit private var pekkoSystem: ActorSystem = uninitialized
  implicit private var pekkoMat: Materializer   = uninitialized
  implicit private var ec: ExecutionContext     = uninitialized

  private given BufferCapacity = BufferCapacity.default
  @Setup(Level.Trial)
  def setup(): Unit = {
    workFn = workload match {
      case "heavy" => { x =>
        var s = x.toLong
        var i = 0
        while (i < 100) { s = (s * 31 + 17) ^ (s >>> 3); i += 1 }
        s.toInt
      }
      case _ => x => x + 1
    }

    pekkoSystem = ActorSystem("concurrent-bench")
    pekkoMat = SystemMaterializer(pekkoSystem).materializer
    ec = pekkoSystem.dispatcher
  }

  @TearDown(Level.Trial)
  def teardown(): Unit =
    if (pekkoSystem != null)
      Await.result(pekkoSystem.terminate(), ScalaDuration(30, "s"))

  private def zbFold(s: ZbStream[Nothing, Int]): Long =
    s.runFold(0L)((acc, i) => acc + i) match {
      case Right(v) => v
      case Left(_)  => 0L
    }

  private def fs2Fold(s: Fs2Stream[IO, Int]): Long =
    s.compile.fold(0L)(_ + _).unsafeRunSync()

  private def pekkoFold(src: Source[Int, ?]): Long =
    Await.result(src.runWith(PekkaSink.fold(0L)(_ + _)), ScalaDuration.Inf)

  private def oxFold(f: Flow[Int]): Long =
    f.runFold(0L)((acc, i) => acc + i)

  // kyo_mapPar removed: mapParUnordered forks 1M fibers (1 per element),
  // consuming 1-10GB of memory per iteration and causing OOM on the host.
  // Measured at 0.004 ops/s before removal.

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
  def pekko_mapPar(): Long =
    pekkoFold(
      Source(0 until N).mapAsyncUnordered(parallelism)(a => Future(workFn(a)))
    )

  @Benchmark
  def ox_mapPar(): Long =
    oxFold(
      Flow.fromIterable(0 until N).mapParUnordered(parallelism)(workFn)
    )

  @Benchmark
  def zb_mergeAll(): Long = {
    val fn      = workFn
    val streams = ZbStream.range(0, outerN).map(i => ZbStream.range(i * innerN, (i + 1) * innerN).map(fn))
    zbFold(ZbStream.mergeAll(parallelism)(streams))
  }

  @Benchmark
  def fs2_mergeAll(): Long = {
    val fn                                         = workFn
    val streams: Fs2Stream[IO, Fs2Stream[IO, Int]] =
      Fs2Stream
        .range(0, outerN)
        .covary[IO]
        .map(i => Fs2Stream.range(i * innerN, (i + 1) * innerN).covary[IO].map(fn))
    fs2Fold(streams.parJoin(parallelism))
  }

  @Benchmark
  def pekko_mergeAll(): Long = {
    val fn = workFn
    pekkoFold(
      Source(0 until outerN)
        .flatMapMerge(parallelism, i => Source(i * innerN until (i + 1) * innerN).map(fn))
    )
  }

  @Benchmark
  def ox_mergeAll(): Long = {
    val fn                     = workFn
    val flows: Flow[Flow[Int]] =
      Flow
        .fromIterable(0 until outerN)
        .map(i => Flow.fromIterable(i * innerN until (i + 1) * innerN).map(fn))
    oxFold(flows.flattenPar(parallelism))
  }

  @Benchmark
  def zb_flatMapPar(): Long = {
    val fn = workFn
    zbFold(
      ZbStream
        .range(0, fmpOuterN)
        .flatMapPar(parallelism)(i => ZbStream.range(0, fmpInnerN).map(j => fn(i * fmpInnerN + j)))
    )
  }

  @Benchmark
  def fs2_flatMapPar(): Long = {
    val fn = workFn
    fs2Fold(
      Fs2Stream
        .range(0, fmpOuterN)
        .covary[IO]
        .map(i => Fs2Stream.range(0, fmpInnerN).covary[IO].map(j => fn(i * fmpInnerN + j)))
        .parJoin(parallelism)
    )
  }

  @Benchmark
  def pekko_flatMapPar(): Long = {
    val fn = workFn
    pekkoFold(
      Source(0 until fmpOuterN)
        .flatMapMerge(parallelism, i => Source(0 until fmpInnerN).map(j => fn(i * fmpInnerN + j)))
    )
  }

  @Benchmark
  def ox_flatMapPar(): Long = {
    val fn = workFn
    oxFold(
      Flow
        .fromIterable(0 until fmpOuterN)
        .map(i => Flow.fromIterable(0 until fmpInnerN).map(j => fn(i * fmpInnerN + j)))
        .flattenPar(parallelism)
    )
  }

}
