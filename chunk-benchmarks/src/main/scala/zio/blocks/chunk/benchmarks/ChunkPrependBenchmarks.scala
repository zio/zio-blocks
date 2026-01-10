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

import org.openjdk.jmh.annotations._
import zio.blocks.chunk.Chunk

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkPrependBenchmarks {

  val chunk: Chunk[Int]   = Chunk(1)
  val vector: Vector[Int] = Vector(1)
  val array: Array[Int]   = Array(1)
  val list: List[Int]     = List(1)

  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def chunkPrepend(): Chunk[Int] = {
    var i       = 0
    var current = chunk

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }

  @Benchmark
  def vectorPrepend(): Vector[Int] = {
    var i       = 0
    var current = vector

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }

  @Benchmark
  def arrayPrepend(): Array[Int] = {
    var i       = 0
    var current = array

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }

  @Benchmark
  def listPrepend(): List[Int] = {
    var i       = 0
    var current = list

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }
}
