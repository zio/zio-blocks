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
import scala.compiletime.uninitialized

class MixedChunkBenchmark extends BaseBenchmark {
  @Param(Array("1000"))
  var size: Int = uninitialized

  var chunk: Chunk[Int]             = uninitialized
  var chunkMaterialized: Chunk[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    val whole = Chunk.fromArray(array)

    val firstQuarter  = whole.take(250)
    val secondQuarter = whole.drop(250).take(250)
    val thirdQuarter  = whole.drop(500).take(250)
    val fourthQuarter = whole.drop(750)

    val firstTwoHundred  = firstQuarter.take(200)
    val secondTwoHundred = firstQuarter.drop(200) ++ secondQuarter.take(150)
    val thirdTwoHundred  = secondQuarter.drop(150) ++ thirdQuarter.take(100)
    val fourthTwoHundred = thirdQuarter.drop(100) ++ fourthQuarter.take(50)
    val fifthTwoHundred  = fourthQuarter.drop(50)

    val firstHundredFifty         = firstTwoHundred.take(150)
    val secondThreeHundred        = firstTwoHundred.drop(150) ++ secondTwoHundred ++ thirdTwoHundred.take(50)
    val thirdFifty                = thirdTwoHundred.drop(50).take(50)
    val fourthTwoHundredFifty     = thirdTwoHundred.drop(100) ++ fourthTwoHundred.take(150)
    val fifthHundred              = fourthTwoHundred.drop(150) ++ fifthTwoHundred.take(50)
    val sixthOne                  = fifthTwoHundred.drop(1).take(1)
    val seventhHundredNinetyEight = fifthHundred.drop(2).take(198)
    val lastOne                   = fifthTwoHundred.drop(199)

    chunk = firstHundredFifty ++ secondThreeHundred ++ thirdFifty ++
      fourthTwoHundredFifty ++ fifthHundred ++ sixthOne ++
      seventhHundredNinetyEight ++ lastOne

    chunkMaterialized = chunk.materialize
  }

  @Benchmark
  def fold(): Int = chunk.foldLeft(0)(_ + _)

  @Benchmark
  def foldMaterialized(): Int = chunkMaterialized.foldLeft(0)(_ + _)

  @Benchmark
  def map(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def mapMaterialized(): Chunk[Int] = chunkMaterialized.map(_ * 2)

  @Benchmark
  def flatMap(): Chunk[Int] = chunk.flatMap(n => Chunk(n + 2))

  @Benchmark
  def flatMapMaterialized(): Chunk[Int] = chunkMaterialized.flatMap(n => Chunk(n + 2))

  @Benchmark
  def find(): Option[Int] = chunk.find(_ > 2)

  @Benchmark
  def findMaterialized(): Option[Int] = chunkMaterialized.find(_ > 2)

  @Benchmark
  def filter(): Chunk[Int] = chunk.filter(_ % 2 == 0)

  @Benchmark
  def filterMaterialized(): Chunk[Int] = chunkMaterialized.filter(_ % 2 == 0)
}
