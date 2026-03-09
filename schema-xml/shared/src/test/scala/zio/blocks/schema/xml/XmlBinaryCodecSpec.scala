package zio.blocks.schema.xml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object XmlBinaryCodecSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlBinaryCodecSpec")(
    suite("unitCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.unitCodec
        val result = codec.decode(codec.encode(()))
        assertTrue(result.isRight)
      },
      test("encodeToString produces valid XML") {
        val codec = XmlBinaryCodec.unitCodec
        val xml   = codec.encodeToString(())
        assertTrue(xml.contains("<unit"))
      },
      test("decode from string") {
        val codec  = XmlBinaryCodec.unitCodec
        val xml    = "<unit/>"
        val result = codec.decode(xml)
        assertTrue(result.isRight)
      }
    ),
    suite("booleanCodec")(
      test("round-trip encode/decode true") {
        val codec  = XmlBinaryCodec.booleanCodec
        val result = codec.decode(codec.encode(true))
        assertTrue(result == Right(true))
      },
      test("round-trip encode/decode false") {
        val codec  = XmlBinaryCodec.booleanCodec
        val result = codec.decode(codec.encode(false))
        assertTrue(result == Right(false))
      },
      test("encodeToString produces valid XML") {
        val codec = XmlBinaryCodec.booleanCodec
        val xml   = codec.encodeToString(true)
        assertTrue(xml.contains("true"))
      }
    ),
    suite("byteCodec")(
      test("round-trip encode/decode") {
        val codec       = XmlBinaryCodec.byteCodec
        val value: Byte = 42
        val result      = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.byteCodec
        val value  = Byte.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.byteCodec
        val value  = Byte.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("shortCodec")(
      test("round-trip encode/decode") {
        val codec        = XmlBinaryCodec.shortCodec
        val value: Short = 1234
        val result       = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.shortCodec
        val value  = Short.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.shortCodec
        val value  = Short.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("intCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.intCodec
        val value  = 123456
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.intCodec
        val value  = Int.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.intCodec
        val value  = Int.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("longCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.longCodec
        val value  = 123456789L
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.longCodec
        val value  = Long.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.longCodec
        val value  = Long.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("floatCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.floatCodec
        val value  = 3.14f
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.floatCodec
        val value  = Float.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.floatCodec
        val value  = Float.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode NaN") {
        val codec  = XmlBinaryCodec.floatCodec
        val value  = Float.NaN
        val result = codec.decode(codec.encode(value))
        assertTrue(result.exists(_.isNaN))
      }
    ),
    suite("doubleCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.doubleCodec
        val value  = 3.14159265358979
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlBinaryCodec.doubleCodec
        val value  = Double.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlBinaryCodec.doubleCodec
        val value  = Double.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode NaN") {
        val codec  = XmlBinaryCodec.doubleCodec
        val value  = Double.NaN
        val result = codec.decode(codec.encode(value))
        assertTrue(result.exists(_.isNaN))
      }
    ),
    suite("charCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.charCodec
        val value  = 'A'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode special char") {
        val codec  = XmlBinaryCodec.charCodec
        val value  = '\n'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode unicode") {
        val codec  = XmlBinaryCodec.charCodec
        val value  = '€'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("stringCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.stringCodec
        val value  = "Hello, World!"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode empty string") {
        val codec  = XmlBinaryCodec.stringCodec
        val value  = ""
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode string with special chars") {
        val codec  = XmlBinaryCodec.stringCodec
        val value  = "Hello <>&\" World"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode unicode string") {
        val codec  = XmlBinaryCodec.stringCodec
        val value  = "Hello 世界 🌍"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("bigIntCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.bigIntCodec
        val value  = BigInt("123456789012345678901234567890")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode small value") {
        val codec  = XmlBinaryCodec.bigIntCodec
        val value  = BigInt(42)
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode negative value") {
        val codec  = XmlBinaryCodec.bigIntCodec
        val value  = BigInt("-999999999999999999999")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("bigDecimalCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlBinaryCodec.bigDecimalCodec
        val value  = BigDecimal("123456789.123456789")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode small value") {
        val codec  = XmlBinaryCodec.bigDecimalCodec
        val value  = BigDecimal("3.14")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode negative value") {
        val codec  = XmlBinaryCodec.bigDecimalCodec
        val value  = BigDecimal("-999.999")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode scientific notation") {
        val codec  = XmlBinaryCodec.bigDecimalCodec
        val value  = BigDecimal("1.23E+10")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    )
  )
}
