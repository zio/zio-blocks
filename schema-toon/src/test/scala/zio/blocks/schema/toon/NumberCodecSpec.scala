package zio.blocks.schema.toon

import zio.test._

object NumberCodecSpec extends ZIOSpecDefault {
  def spec = suite("NumberCodec")(
    test("int codec") {
      val codec = ToonBinaryCodec.intCodec
      assertTrue(codec.encodeToString(42) == "42")
    },
    test("long codec") {
      val codec = ToonBinaryCodec.longCodec
      assertTrue(codec.encodeToString(123456789012345L) == "123456789012345")
    },
    test("scientific notation converted to decimal") {
      val big   = BigDecimal("1.5e10")
      val codec = ToonBinaryCodec.bigDecimalCodec
      assertTrue(codec.encodeToString(big) == "15000000000")
    },
    test("NaN becomes null") {
      val codec = ToonBinaryCodec.doubleCodec
      assertTrue(codec.encodeToString(Double.NaN) == "null")
    },
    test("Infinity becomes null") {
      val codec = ToonBinaryCodec.doubleCodec
      assertTrue(codec.encodeToString(Double.PositiveInfinity) == "null")
    },
    test("negative infinity becomes null") {
      val codec = ToonBinaryCodec.doubleCodec
      assertTrue(codec.encodeToString(Double.NegativeInfinity) == "null")
    },
    test("negative zero normalized to zero") {
      val codec = ToonBinaryCodec.doubleCodec
      assertTrue(codec.encodeToString(-0.0) == "0.0")
    },
    test("float NaN becomes null") {
      val codec = ToonBinaryCodec.floatCodec
      assertTrue(codec.encodeToString(Float.NaN) == "null")
    },
    test("regular double value") {
      val codec = ToonBinaryCodec.doubleCodec
      assertTrue(codec.encodeToString(3.14) == "3.14")
    },
    test("bigInt codec") {
      val codec = ToonBinaryCodec.bigIntCodec
      val big   = BigInt("123456789012345678901234567890")
      assertTrue(codec.encodeToString(big) == "123456789012345678901234567890")
    },
    test("bigDecimal with high precision") {
      val codec = ToonBinaryCodec.bigDecimalCodec
      val big   = BigDecimal("123.456789012345678901234567890")
      assertTrue(codec.encodeToString(big) == "123.456789012345678901234567890")
    }
  )
}
