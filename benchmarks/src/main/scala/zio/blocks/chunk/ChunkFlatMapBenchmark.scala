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

package zio.blocks.chunk

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.chunk.Chunk
import scala.collection.immutable.ArraySeq
import scala.compiletime.uninitialized

class ChunkFlatMapBenchmark extends BaseBenchmark {
  @Param(Array("100"))
  var size: Int           = 100
  var chunk: Chunk[Int]   = uninitialized
  var vector: Vector[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    vector = array.toVector
  }

  @Benchmark
  def flatMapArraySeq: Chunk[Int] = chunk.flatMap(i => ArraySeq(i, i + 1))

  @Benchmark
  def flatMapChunk: Chunk[Int] = chunk.flatMap(i => Chunk(i, i + 1))

  @Benchmark
  def flatMapVector: Vector[Int] = vector.flatMap(i => Vector(i, i + 1))
}
