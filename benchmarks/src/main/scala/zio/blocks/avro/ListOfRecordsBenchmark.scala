package zio.blocks.avro

import org.openjdk.jmh.annotations._
import zio.Chunk
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.avro.AvroFormat
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema => ZIOSchema}
import java.nio.ByteBuffer

class ListOfRecordsBenchmark extends BaseBenchmark {
  import ListOfRecordsDomain._

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
  def writingZioBlocks: Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(1024)
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

  val listOfRecords: List[Person] =
    (1 to 10).map(_ => Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))).toList

  val encodedListOfRecords: Array[Byte] = zioSchemaCodec.encode(listOfRecords).toArray
}
