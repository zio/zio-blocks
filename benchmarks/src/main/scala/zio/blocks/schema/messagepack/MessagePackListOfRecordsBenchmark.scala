package zio.blocks.schema.messagepack

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.schema.messagepack.{MessagePackBinaryCodec, MessagePackFormat}
import scala.compiletime.uninitialized

class MessagePackListOfRecordsBenchmark extends BaseBenchmark {
  import MessagePackListOfRecordsDomain._

  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int                         = 100
  var listOfRecords: List[Person]       = uninitialized
  var encodedListOfRecords: Array[Byte] = uninitialized

  @Setup
  def setup(): Unit = {
    listOfRecords = (1 to size).map(_ => Person(12345678901L, "John", 30, "123 Main St", List(5, 7, 9))).toList
    encodedListOfRecords = zioBlocksCodec.encode(listOfRecords)
  }

  @Benchmark
  def readingZioBlocks: List[Person] = zioBlocksCodec.decode(encodedListOfRecords) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(listOfRecords)
}

object MessagePackListOfRecordsDomain {
  case class Person(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])

  implicit val schema: Schema[Person] = Schema.derived

  val zioBlocksCodec: MessagePackBinaryCodec[List[Person]] = Schema.derived.deriving(MessagePackFormat.deriver).derive
}
