package zio.blocks.avro

import org.openjdk.jmh.annotations._
import zio.Chunk
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.avro.AvroFormat
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema => ZIOSchema}

class ListOfRecordsBenchmark extends BaseBenchmark {
  import ListOfRecordsDomain._

  @Benchmark
  def readingZioBlocks: List[Person] = {
    val byteBuffer = java.nio.ByteBuffer.wrap(encodedListOfRecords)
    zioBlocksSchema.decode(AvroFormat)(byteBuffer) match {
      case Right(value) => value
      case Left(error)  => sys.error(error.getMessage)
    }
  }

  @Benchmark
  def readingZioSchema: List[Person] =
    zioSchemaCodec.decode(Chunk.fromArray(encodedListOfRecords)) match {
      case Right(value) => value
      case Left(error)  => sys.error(error.getMessage)
    }

  @Benchmark
  def writingZioBlocks: Array[Byte] = {
    val byteBuffer = java.nio.ByteBuffer.allocate(1024)
    zioBlocksSchema.encode(AvroFormat)(byteBuffer)(listOfRecords)
    java.util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
  }

  @Benchmark
  def writingZioSchema: Array[Byte] = zioSchemaCodec.encode(listOfRecords).toArray
}

object ListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  object Person {
    implicit val zioSchema: ZIOSchema[Person] = DeriveSchema.gen[Person]
  }

  private val person = Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))

  implicit val zioBlocksSchema: Schema[List[Person]] = Schema.derived

  val zioSchemaCodec: AvroCodec.ExtendedBinaryCodec[List[Person]] = AvroCodec.schemaBasedBinaryCodec[List[Person]]

  val listOfRecords: List[Person] = List(
    person,
    person,
    person,
    person,
    person,
    person,
    person,
    person,
    person,
    person
  )

  val encodedListOfRecords: Array[Byte] = zioSchemaCodec.encode(listOfRecords).toArray
}
