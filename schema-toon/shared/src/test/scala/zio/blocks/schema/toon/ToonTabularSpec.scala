package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.toon._
import java.nio.charset.StandardCharsets

object ToonTabularSpec extends ZIOSpecDefault {

  case class Point(x: Int, y: Int)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  // Helper
  private def roundTrip[A](codec: ToonBinaryCodec[A], value: A): Either[SchemaError, A] = {
    val writer = new ToonWriter(new Array[Byte](16384), ToonWriterConfig)
    codec.encodeValue(value, writer)
    val encoded = new String(writer.buf, 0, writer.count, StandardCharsets.UTF_8)

    codec.decodeFromString(encoded)
  }

  def spec = suite("ToonTabularSpec")(
    test("round-trips tabular array of records") {
      val codec  = Schema.list(Point.schema).derive(ToonFormat.deriver)
      val value  = List(Point(1, 2), Point(3, 4))
      val result = roundTrip(codec, value)
      assertTrue(result == Right(value))
    }
  )
}
