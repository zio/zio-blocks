/*
 * Copyright 2023 ZIO Blocks Maintainers
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
import scala.compiletime.uninitialized

class ChunkFoldBenchmark extends BaseBenchmark {
  @Param(Array("1000"))
  var size: Int = uninitialized

  var chunk: Chunk[Int]   = uninitialized
  var vector: Vector[Int] = uninitialized
  var list: List[Int]     = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    vector = array.toVector
    list = array.toList
  }

  @Benchmark
  def foldChunk(): Int = chunk.fold(0)(_ + _)

  @Benchmark
  def foldVector(): Int = vector.fold(0)(_ + _)

  @Benchmark
  def foldList(): Int = list.fold(0)(_ + _)
}
