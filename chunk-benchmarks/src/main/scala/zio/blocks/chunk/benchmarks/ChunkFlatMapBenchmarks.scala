/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk.benchmarks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.blocks.chunk.Chunk

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
class ChunkFlatMapBenchmarks {
  @Param(Array("100"))
  var size: Int = _

  var chunk: Chunk[Int]   = _
  var vector: Vector[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    vector = array.toVector
  }

  @Benchmark
  def flatMapChunk(): Chunk[Int] = chunk.flatMap(i => Chunk(i, i + 1))

  @Benchmark
  def flatMapVector(): Vector[Int] = vector.flatMap(i => Vector(i, i + 1))
}
