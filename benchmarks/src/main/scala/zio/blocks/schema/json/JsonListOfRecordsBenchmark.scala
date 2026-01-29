package zio.blocks.schema.json

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.schema.{DeriveSchema, Schema as ZIOSchema}
import java.nio.charset.StandardCharsets.UTF_8
import scala.compiletime.uninitialized

class JsonListOfRecordsBenchmark extends BaseBenchmark {
  import JsonListOfRecordsDomain._

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
  def readingJsoniterScala: List[Person] = readFromArray(encodedListOfRecords)

  @Benchmark
  def readingZioBlocks: List[Person] = zioBlocksCodec.decode(encodedListOfRecords) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def readingZioJson: List[Person] = {
    import zio.json.DecoderOps

    new String(encodedListOfRecords, UTF_8).fromJson[List[Person]] match {
      case Right(value) => value
      case Left(error)  => sys.error(error)
    }
  }

  @Benchmark
  def readingZioSchema: List[Person] = zioSchemaCodec.decodeJson(new String(encodedListOfRecords, UTF_8)) match {
    case Right(value) => value
    case Left(error)  => sys.error(error)
  }

  @Benchmark
  def writingJsoniterScala: Array[Byte] = writeToArray(listOfRecords)

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(listOfRecords)

  @Benchmark
  def writingZioJson: Array[Byte] = {
    import zio.json.EncoderOps

    listOfRecords.toJson.getBytes(UTF_8)
  }

  @Benchmark
  def writingZioSchema: Array[Byte] = zioSchemaCodec.encodeJson(listOfRecords, None).toString.getBytes(UTF_8)
}

object JsonListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  implicit val jsoniterScalaCodec: JsonValueCodec[List[Person]] = JsonCodecMaker.make

  val zioBlocksCodec = Schema.derived[List[Person]].derive(JsonFormat)

  implicit val zioJsonCodec: zio.json.JsonCodec[Person] = zio.json.DeriveJsonCodec.gen

  val zioSchemaCodec: zio.json.JsonCodec[List[Person]] =
    zio.schema.codec.JsonCodec.jsonCodec(DeriveSchema.gen[List[Person]])
}
