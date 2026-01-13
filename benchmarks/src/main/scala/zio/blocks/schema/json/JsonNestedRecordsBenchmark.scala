package zio.blocks.schema.json

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark
import zio.blocks.schema.{Schema, SchemaError}
import scala.compiletime.uninitialized

class JsonNestedRecordsBenchmark extends BaseBenchmark {
  import JsonNestedRecordsBenchmark._

  @Param(Array("1", "10", "100"))
  var size: Int                         = 100
  var nestedRecords: Nested             = uninitialized
  var encodedNestedRecords: Array[Byte] = uninitialized
  var brokenNestedRecords: Array[Byte]  = uninitialized

  @Setup
  def setup(): Unit = {
    nestedRecords = (1 to size).foldLeft(Nested(0, None))((n, i) => Nested(i, Some(n)))
    encodedNestedRecords = zioBlocksCodec.encode(nestedRecords)
    brokenNestedRecords = zioBlocksCodec.encode(nestedRecords)
    brokenNestedRecords(brokenNestedRecords.length - size - 2) = 'x'.toByte
  }

  @Benchmark
  def readingZioBlocks: Nested = zioBlocksCodec.decode(encodedNestedRecords) match {
    case Right(value) => value
    case Left(error)  => sys.error(error.getMessage)
  }

  @Benchmark
  def readingErrorZioBlocks: Either[SchemaError, Nested] = zioBlocksCodec.decode(brokenNestedRecords)

  @Benchmark
  def writingZioBlocks: Array[Byte] = zioBlocksCodec.encode(nestedRecords)
}

object JsonNestedRecordsBenchmark {
  case class Nested(value: Int, next: Option[Nested])

  val zioBlocksCodec: JsonBinaryCodec[Nested] = Schema.derived.deriving(JsonFormat.deriver).derive
}
