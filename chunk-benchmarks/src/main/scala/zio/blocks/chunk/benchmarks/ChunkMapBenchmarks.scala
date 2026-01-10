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
class ChunkMapBenchmarks {
  @Param(Array("1000"))
  var size: Int = _

  var intArray: Array[Int]   = _
  var intChunk: Chunk[Int]   = _
  var intVector: Vector[Int] = _
  var intList: List[Int]     = _

  var stringArray: Array[String]   = _
  var stringChunk: Chunk[String]   = _
  var stringVector: Vector[String] = _
  var stringList: List[String]     = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    intArray = (1 to size).toArray
    stringArray = intArray.map(_.toString)
    intChunk = Chunk.fromArray(intArray)
    stringChunk = intChunk.map(_.toString)
    intVector = intArray.toVector
    stringVector = intVector.map(_.toString)
    intList = intArray.toList
    stringList = intList.map(_.toString)
  }

  @Benchmark
  def mapIntArray(): Array[Int] = intArray.map(_ * 2)

  @Benchmark
  def mapStringArray(): Array[String] = stringArray.map(_ + "123")

  @Benchmark
  def mapIntChunk(): Chunk[Int] = intChunk.map(_ * 2)

  @Benchmark
  def mapStringChunk(): Chunk[String] = stringChunk.map(_ + "123")

  @Benchmark
  def mapIntVector(): Vector[Int] = intVector.map(_ * 2)

  @Benchmark
  def mapStringVector(): Vector[String] = stringVector.map(_ + "123")

  @Benchmark
  def mapIntList(): List[Int] = intList.map(_ * 2)

  @Benchmark
  def mapStringList(): List[String] = stringList.map(_ + "123")
}
