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

package zio.blocks.schema.avro

import org.openjdk.jmh.annotations._
import zio.Chunk
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.schema.avro.{AvroBinaryCodec, AvroFormat}
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema => ZIOSchema}
import java.io.ByteArrayOutputStream
import com.sksamuel.avro4s.{AvroSchema, AvroInputStream, AvroOutputStream}
import scala.compiletime.uninitialized

class AvroListOfRecordsBenchmark extends BaseBenchmark {
  import AvroListOfRecordsDomain._

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
  def readingAvro4s: List[Person] =
    AvroInputStream.binary[List[Person]].from(encodedListOfRecords).build(AvroSchema[List[Person]]).iterator.next()

  @Benchmark
  def readingZioBlocks: List[Person] = zioBlocksCodec.decode(encodedListOfRecords) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def readingZioSchema: List[Person] = zioSchemaCodec.decode(Chunk.fromArray(encodedListOfRecords)) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def writingAvro4s: Array[Byte] = {
    val baos   = new ByteArrayOutputStream(30 * size)
    val output = AvroOutputStream.binary[List[Person]].to(baos).build()
    output.write(listOfRecords)
    output.close()
    baos.toByteArray
  }

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(listOfRecords)

  @Benchmark
  def writingZioSchema: Array[Byte] = zioSchemaCodec.encode(listOfRecords).toArray
}

object AvroListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  implicit val zioSchema: ZIOSchema[Person] = DeriveSchema.gen[Person]

  val zioSchemaCodec: AvroCodec.ExtendedBinaryCodec[List[Person]] = AvroCodec.schemaBasedBinaryCodec[List[Person]]

  val zioBlocksCodec: AvroBinaryCodec[List[Person]] = Schema.derived.deriving(AvroFormat.deriver).derive
}
