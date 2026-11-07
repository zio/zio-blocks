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

import scala.compiletime.uninitialized

import org.openjdk.jmh.annotations._

import zio.blocks.streams.Stream

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
class StreamRangeFusionBench {
  @Param(Array("10000")) var N: Int = uninitialized

  private def transformed: Stream[Nothing, Int] =
    Stream.range(0, N).map(_ + 1).filter(_ % 2 == 0)

  @Benchmark def rawLong(): Long =
    Stream.range(0, N).runFoldLongBlocking(0L, _ + _).toOption.get

  @Benchmark def transformedLong(): Long =
    transformed.runFoldLongBlocking(0L, _ + _).toOption.get

  @Benchmark def transformedInt(): Int =
    transformed.runFoldIntBlocking(0, _ + _).toOption.get

  @Benchmark def transformedDouble(): Double =
    transformed.runFoldDoubleBlocking(0d, _ + _).toOption.get

  @Benchmark def takeDropLong(): Long =
    Stream.range(0, N).drop(N / 4).take(N / 2).runFoldLongBlocking(0L, _ + _).toOption.get

  @Benchmark def takeWhileLong(): Long =
    Stream.range(0, N).takeWhile(_ < N / 2).runFoldLongBlocking(0L, _ + _).toOption.get
}
