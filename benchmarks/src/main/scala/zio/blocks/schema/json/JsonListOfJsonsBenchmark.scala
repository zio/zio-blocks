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

package zio.blocks.schema.json

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import java.nio.charset.StandardCharsets.UTF_8
import scala.compiletime.uninitialized

class JsonListOfJsonsBenchmark extends BaseBenchmark {
  @Param(Array("1", "10", "100", "1000", "10000", "100000"))
  var size: Int                                  = 100
  val record: String                             = """{"id":12345678901,"name":"John","age":30,"address":"123 Main St","childrenAges":[5,7,9]}"""
  var encodedListOfRecords: Array[Byte]          = uninitialized
  var zioBlocksJson: zio.blocks.schema.json.Json = uninitialized
  var zioJsonJson: zio.json.ast.Json             = uninitialized

  @Setup
  def setup(): Unit = {
    encodedListOfRecords = (1 to size).map(_ => record).mkString("[", ",", "]").getBytes(UTF_8)
    zioBlocksJson = readingZioBlocks
    zioJsonJson = readingZioJson
  }

  @Benchmark
  def readingZioBlocks: zio.blocks.schema.json.Json =
    zio.blocks.schema.json.Json.jsonCodec.decode(encodedListOfRecords) match {
      case Right(value) => value
      case Left(error)  => throw error
    }

  @Benchmark
  def readingZioJson: zio.json.ast.Json = {
    import zio.json._

    new String(encodedListOfRecords, UTF_8).fromJson[zio.json.ast.Json] match {
      case Right(value) => value
      case Left(error)  => sys.error(error)
    }
  }
  @Benchmark
  def writingZioBlocks: Array[Byte] = zio.blocks.schema.json.Json.jsonCodec.encode(zioBlocksJson)

  @Benchmark
  def writingZioJson: Array[Byte] = {
    import zio.json._

    zioJsonJson.toJson.getBytes(UTF_8)
  }
}
