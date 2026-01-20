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

class ChunkConcatBenchmark extends BaseBenchmark {

  @Param(Array("10000"))
  var size: Int = uninitialized

  var chunk0: Chunk[Int]        = uninitialized
  var chunk1: Chunk[Int]        = uninitialized
  var chunk10: Chunk[Int]       = uninitialized
  var chunkHalfSize: Chunk[Int] = uninitialized

  @Setup
  def setUp(): Unit = {
    chunk0 = Chunk.empty
    chunk1 = Chunk.single(1)
    chunk10 = Chunk.fill(10)(1)
    chunkHalfSize = Chunk.fill(size / 2)(1)
  }

  @Benchmark
  def leftConcat0(): Chunk[Int] = {
    var i       = 1
    var current = chunk0

    while (i < size) {
      current = chunk0 ++ current
      i += 1
    }

    current
  }

  @Benchmark
  def leftConcat1(): Chunk[Int] = {
    var i       = 1
    var current = chunk1

    while (i < size) {
      current = chunk1 ++ current
      i += 1
    }

    current
  }

  @Benchmark
  def leftConcat10(): Chunk[Int] = {
    var i       = 1
    var current = chunk10

    while (i < size) {
      current = chunk10 ++ current
      i += 10
    }

    current
  }

  @Benchmark
  def rightConcat0(): Chunk[Int] = {
    var i       = 1
    var current = chunk0

    while (i < size) {
      current = current ++ chunk0
      i += 1
    }

    current
  }

  @Benchmark
  def rightConcat1(): Chunk[Int] = {
    var i       = 1
    var current = chunk1

    while (i < size) {
      current = current ++ chunk1
      i += 1
    }

    current
  }

  @Benchmark
  def rightConcat10(): Chunk[Int] = {
    var i       = 1
    var current = chunk10

    while (i < size) {
      current = current ++ chunk10
      i += 10
    }

    current
  }

  @Benchmark
  def balancedConcatOnce(): Chunk[Int] =
    chunkHalfSize ++ chunkHalfSize

  @Benchmark
  def balancedConcatRecursive(): Chunk[Int] =
    concatBalancedRec(size)

  def concatBalancedRec(n: Int): Chunk[Int] =
    if (n == 0) Chunk.empty
    else if (n == 1) chunk1
    else concatBalancedRec(n / 2) ++ concatBalancedRec(n / 2)
}
