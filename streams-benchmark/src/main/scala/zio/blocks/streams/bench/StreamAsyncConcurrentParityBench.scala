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

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized
import zio.blocks.async.*
import zio.blocks.streams.Stream

/**
 * ZIO Blocks-only ready-callback diagnostics. Excluded from cross-provider
 * rankings.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Benchmark)
class StreamAsyncConcurrentParityBench {
  @Param(Array("1", "8", "16")) var parallelism: Int   = uninitialized
  @Param(Array("light", "heavy")) var workload: String = uninitialized
  @Param(Array("1000000")) var N: Int                  = uninitialized
  private val outerN                                   = 100; private def innerN      = math.max(1, N / outerN)
  private val fmpOuterN                                = 1_000; private def fmpInnerN = math.max(1, N / fmpOuterN)
  private var work: Int => Int                         = uninitialized
  @Setup(Level.Trial) def setup(): Unit                = work = if (workload == "heavy") { x =>
    var s = x.toLong; var i = 0; while (i < 100) { s = (s * 31 + 17) ^ (s >>> 3); i += 1 }; s.toInt
  } else _ + 1
  private def fold(s: Stream[Nothing, Int]) = AsyncParity.fold(s)
  @Benchmark def async_mapPar(): Long       = fold(
    AsyncParity.source(0, N).mapParAsync(parallelism)(x => Async.succeed(work(x)))
  )
  @Benchmark def async_mergeAll(): Long = {
    val streams = AsyncParity.source(0, outerN).map(i => AsyncParity.source(i * innerN, (i + 1) * innerN).map(work))
    fold(Stream.mergeAll(parallelism)(streams))
  }
  @Benchmark def async_flatMapPar(): Long = fold(
    AsyncParity
      .source(0, fmpOuterN)
      .flatMapPar(parallelism)(i => AsyncParity.source(0, fmpInnerN).map(j => work(i * fmpInnerN + j)))
  )
  def runOnce(operator: String): Long = operator match {
    case "mapPar" => async_mapPar(); case "mergeAll" => async_mergeAll(); case "flatMapPar" => async_flatMapPar()
  }
}

/**
 * ZIO Blocks-only genuine suspended bounded-callback diagnostics. A fixed
 * worker pool replaces thread-per-element fixtures. Excluded from
 * cross-provider rankings.
 */
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@State(Scope.Benchmark)
class StreamAsyncSuspendedConcurrentBench extends WorkerHandshake {
  @Param(Array("1", "8", "16")) var parallelism: Int = uninitialized
  @Param(Array("1000")) var N: Int                   = uninitialized
  @Benchmark def suspended_mapPar(): Long            =
    AsyncParity.fold(AsyncParity.source(0, N).mapParAsync(parallelism)(x => pending(x + 1)))
  def runOnce(): Long = suspended_mapPar()
}
