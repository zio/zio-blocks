package zio.blocks.schema.messagepack

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema.Schema
import zio.blocks.schema.messagepack.{MessagePackBinaryCodec, MessagePackFormat}
import scala.compiletime.uninitialized

class MessagePackNestedRecordsBenchmark extends BaseBenchmark {
  import MessagePackNestedRecordsDomain._

  var nestedRecord: Level1             = uninitialized
  var encodedNestedRecord: Array[Byte] = uninitialized

  @Setup
  def setup(): Unit = {
    nestedRecord = Level1(
      Level2(
        Level3(
          Level4(
            Level5(12345678901L, "deeply nested value", 42, "test address", List(1, 2, 3))
          )
        )
      )
    )
    encodedNestedRecord = zioBlocksCodec.encode(nestedRecord)
  }

  @Benchmark
  def readingZioBlocks: Level1 = zioBlocksCodec.decode(encodedNestedRecord) match {
    case Right(value) => value
    case Left(error)  => throw error
  }

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(nestedRecord)
}

object MessagePackNestedRecordsDomain {
  case class Level5(id: Long, name: String, age: Int, address: String, childrenAges: List[Int])
  case class Level4(level5: Level5)
  case class Level3(level4: Level4)
  case class Level2(level3: Level3)
  case class Level1(level2: Level2)

  implicit val level5Schema: Schema[Level5] = Schema.derived
  implicit val level4Schema: Schema[Level4] = Schema.derived
  implicit val level3Schema: Schema[Level3] = Schema.derived
  implicit val level2Schema: Schema[Level2] = Schema.derived
  implicit val level1Schema: Schema[Level1] = Schema.derived

  val zioBlocksCodec: MessagePackBinaryCodec[Level1] = level1Schema.derive(MessagePackFormat.deriver)
}
