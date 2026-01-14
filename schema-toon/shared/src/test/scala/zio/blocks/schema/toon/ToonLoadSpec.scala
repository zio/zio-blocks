package zio.blocks.schema.toon

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.{DynamicValue, Schema}
import java.nio.charset.StandardCharsets

object ToonLoadSpec extends ZIOSpecDefault {

  def roundTrip[A](schema: Schema[A], value: A): Either[String, A] = {
    val codec   = schema.derive(ToonFormat.deriver)
    val encoded = codec.encodeToString(value)
    val bytes   = encoded.getBytes(StandardCharsets.UTF_8)
    val reader  = new ToonReader(bytes, new Array[Char](1024), bytes.length, ToonReaderConfig)
    try {
      Right(codec.decodeValue(reader, null.asInstanceOf[A]))
    } catch {
      case e: Exception => Left(s"Failed to decode: ${e.getMessage}")
    }
  }

  def spec = suite("ToonLoadSpec")(
    test("handle large arrays (10k items)") {
      val largeList = (1 to 10000).toList
      val result    = roundTrip(Schema.list[Int], largeList)
      assert(result)(isRight(equalTo(largeList)))
    },
    test("handle deep nesting (100 levels)") {
      // Use DynamicValue to avoid recursive schema definition overhead in tests
      def createNested(levels: Int): DynamicValue =
        if (levels <= 0) DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Long(1L))
        else DynamicValue.Record(Vector("inner" -> createNested(levels - 1)))
      val nested = createNested(100)
      val result = roundTrip(Schema.dynamic, nested)
      assert(result)(isRight(equalTo(nested)))
    },
    test("precision with large numbers") {
      val largeNum = BigDecimal("12345678901234567890.123456789")
      val result   = roundTrip(Schema.bigDecimal, largeNum)
      assert(result)(isRight(equalTo(largeNum)))
    }
  )
}
