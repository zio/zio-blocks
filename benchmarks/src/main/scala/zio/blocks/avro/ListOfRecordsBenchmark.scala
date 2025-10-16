package zio.blocks.avro

import org.openjdk.jmh.annotations._
import zio.Chunk
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.avro.AvroFormat
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema => ZIOSchema}
import java.nio.ByteBuffer
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import com.sksamuel.avro4s.{AvroSchema, AvroInputStream, AvroOutputStream}

class ListOfRecordsBenchmark extends BaseBenchmark {
  import ListOfRecordsDomain._

  @Param(Array("1", "10", "100", "1000", "10000", "100000"))
  var size: Int                         = 1000
  var listOfRecords: List[Person]       = _
  var encodedListOfRecords: Array[Byte] = _

  @Setup
  def setup(): Unit = {
    listOfRecords = (1 to size).map(_ => Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))).toList
    encodedListOfRecords = zioSchemaCodec.encode(listOfRecords).toArray
  }

  @Benchmark
  def readingAvro4s: List[Person] =
    AvroInputStream
      .binary[List[Person]]
      .from(new ByteArrayInputStream(encodedListOfRecords))
      .build(AvroSchema[List[Person]])
      .iterator
      .next()

  @Benchmark
  def readingZioBlocks: List[Person] =
    zioBlocksSchema.decode(AvroFormat)(ByteBuffer.wrap(encodedListOfRecords)) match {
      case Right(value) => value
      case Left(error)  => sys.error(error.getMessage)
    }

  @Benchmark
  def readingZioSchema: List[Person] =
    zioSchemaCodec.decode(Chunk.fromArray(encodedListOfRecords)) match {
      case Right(value) => value
      case Left(error)  => sys.error(error.getMessage)
    }

  @Benchmark
  def writingAvro4s: Array[Byte] = {
    val baos   = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[List[Person]].to(baos).build()
    output.write(listOfRecords)
    output.close()
    baos.toByteArray
  }

  @Benchmark
  def writingZioBlocks: Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(300 * size)
    zioBlocksSchema.encode(AvroFormat)(byteBuffer)(listOfRecords)
    java.util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
  }

  @Benchmark
  def writingZioSchema: Array[Byte] = zioSchemaCodec.encode(listOfRecords).toArray
}

object ListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  implicit val zioSchema: ZIOSchema[Person] = DeriveSchema.gen[Person]

  implicit val zioBlocksSchema: Schema[List[Person]] = Schema.derived

  val zioSchemaCodec: AvroCodec.ExtendedBinaryCodec[List[Person]] = AvroCodec.schemaBasedBinaryCodec[List[Person]]
}
