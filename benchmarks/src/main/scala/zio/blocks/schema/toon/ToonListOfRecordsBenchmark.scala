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

package zio.blocks.schema.toon

import org.openjdk.jmh.annotations._
import io.toonformat.toon4s._
import io.toonformat.toon4s.codec.{Decoder, Encoder}
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonListOfRecordsDomain.{Person, zioBlocksCodec}
import java.nio.charset.StandardCharsets.UTF_8
import scala.compiletime.uninitialized

class ToonListOfRecordsBenchmark extends BaseBenchmark {
  @Param(Array("1", "10", "100", "1000", "10000", "100000"))
  var size: Int                         = 100
  var listOfRecords: List[Person]       = uninitialized
  var encodedListOfRecords: Array[Byte] = uninitialized

  @Setup
  def setup(): Unit = {
    listOfRecords = (1 to size).map(_ => Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))).toList
    encodedListOfRecords = zioBlocksCodec.encode(listOfRecords)
  }

  @Benchmark
  def readingToon4s: List[Person] =
    ToonTyped.decodeAs[List[Person]](new String(encodedListOfRecords, UTF_8)) match {
      case Right(value) => value
      case Left(error)  => throw error
    }

  @Benchmark
  def readingZioBlocks: List[Person] = zioBlocksCodec.decode(encodedListOfRecords) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def writingToon4s: Array[Byte] = Toon.encode(listOfRecords) match {
    case Right(str)  => str.getBytes(UTF_8)
    case Left(error) => throw error
  }

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(listOfRecords)
}

object ToonListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int]) derives Encoder, Decoder

  val zioBlocksCodec = Schema.derived[List[Person]].derive(ToonFormat)

  implicit val zioJsonCodec: zio.json.JsonCodec[Person] = zio.json.DeriveJsonCodec.gen
}
