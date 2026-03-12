package zio.blocks.schema.yaml

import zio.test._

object YamlBinaryCodecSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlBinaryCodec")(
    suite("Unit codec")(
      test("encode unit") {
        val yaml = YamlBinaryCodec.unitCodec.encodeValue(())
        assertTrue(yaml == Yaml.NullValue)
      },
      test("decode null value") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.NullValue)
        assertTrue(result == Right(()))
      },
      test("decode 'null' scalar") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.Scalar("null"))
        assertTrue(result == Right(()))
      },
      test("decode '~' scalar") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.Scalar("~"))
        assertTrue(result == Right(()))
      },
      test("decode empty scalar") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.Scalar(""))
        assertTrue(result == Right(()))
      },
      test("decode non-null fails") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.Scalar("something"))
        assertTrue(result.isLeft)
      },
      test("decode mapping fails") {
        val result = YamlBinaryCodec.unitCodec.decodeValue(Yaml.Mapping.empty)
        assertTrue(result.isLeft)
      }
    ),
    suite("Boolean codec")(
      test("encode true") {
        val yaml = YamlBinaryCodec.booleanCodec.encodeValue(true)
        assertTrue(yaml == Yaml.Scalar("true", tag = Some(YamlTag.Bool)))
      },
      test("encode false") {
        val yaml = YamlBinaryCodec.booleanCodec.encodeValue(false)
        assertTrue(yaml == Yaml.Scalar("false", tag = Some(YamlTag.Bool)))
      },
      test("decode true") {
        assertTrue(YamlBinaryCodec.booleanCodec.decodeValue(Yaml.Scalar("true")) == Right(true))
      },
      test("decode false") {
        assertTrue(YamlBinaryCodec.booleanCodec.decodeValue(Yaml.Scalar("false")) == Right(false))
      },
      test("decode invalid boolean fails") {
        assertTrue(YamlBinaryCodec.booleanCodec.decodeValue(Yaml.Scalar("yes")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.booleanCodec.decodeValue(Yaml.NullValue).isLeft)
      },
      test("round-trip through bytes") {
        val codec   = YamlBinaryCodec.booleanCodec
        val encoded = codec.encode(true)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(true))
      }
    ),
    suite("Byte codec")(
      test("encode byte") {
        val yaml = YamlBinaryCodec.byteCodec.encodeValue(42.toByte)
        assertTrue(yaml == Yaml.Scalar("42", tag = Some(YamlTag.Int)))
      },
      test("decode byte") {
        assertTrue(YamlBinaryCodec.byteCodec.decodeValue(Yaml.Scalar("42")) == Right(42.toByte))
      },
      test("decode invalid byte fails") {
        assertTrue(YamlBinaryCodec.byteCodec.decodeValue(Yaml.Scalar("abc")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.byteCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Short codec")(
      test("encode short") {
        val yaml = YamlBinaryCodec.shortCodec.encodeValue(1000.toShort)
        assertTrue(yaml == Yaml.Scalar("1000", tag = Some(YamlTag.Int)))
      },
      test("decode short") {
        assertTrue(YamlBinaryCodec.shortCodec.decodeValue(Yaml.Scalar("1000")) == Right(1000.toShort))
      },
      test("decode invalid short fails") {
        assertTrue(YamlBinaryCodec.shortCodec.decodeValue(Yaml.Scalar("abc")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.shortCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Int codec")(
      test("encode int") {
        val yaml = YamlBinaryCodec.intCodec.encodeValue(42)
        assertTrue(yaml == Yaml.Scalar("42", tag = Some(YamlTag.Int)))
      },
      test("decode int") {
        assertTrue(YamlBinaryCodec.intCodec.decodeValue(Yaml.Scalar("42")) == Right(42))
      },
      test("decode invalid int fails") {
        assertTrue(YamlBinaryCodec.intCodec.decodeValue(Yaml.Scalar("abc")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.intCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Long codec")(
      test("encode long") {
        val yaml = YamlBinaryCodec.longCodec.encodeValue(999999999999L)
        assertTrue(yaml == Yaml.Scalar("999999999999", tag = Some(YamlTag.Int)))
      },
      test("decode long") {
        assertTrue(YamlBinaryCodec.longCodec.decodeValue(Yaml.Scalar("999999999999")) == Right(999999999999L))
      },
      test("decode invalid long fails") {
        assertTrue(YamlBinaryCodec.longCodec.decodeValue(Yaml.Scalar("not_a_number")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.longCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Float codec")(
      test("encode float") {
        val yaml = YamlBinaryCodec.floatCodec.encodeValue(3.14f)
        assertTrue(yaml == Yaml.Scalar("3.14", tag = Some(YamlTag.Float)))
      } @@ TestAspect.jvmOnly,
      test("decode float") {
        assertTrue(YamlBinaryCodec.floatCodec.decodeValue(Yaml.Scalar("3.14")) == Right(3.14f))
      },
      test("decode invalid float fails") {
        assertTrue(YamlBinaryCodec.floatCodec.decodeValue(Yaml.Scalar("abc")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.floatCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Double codec")(
      test("encode double") {
        val yaml = YamlBinaryCodec.doubleCodec.encodeValue(3.14159)
        assertTrue(yaml == Yaml.Scalar("3.14159", tag = Some(YamlTag.Float)))
      },
      test("decode double") {
        assertTrue(YamlBinaryCodec.doubleCodec.decodeValue(Yaml.Scalar("3.14159")) == Right(3.14159))
      },
      test("decode invalid double fails") {
        assertTrue(YamlBinaryCodec.doubleCodec.decodeValue(Yaml.Scalar("xyz")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.doubleCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("Char codec")(
      test("encode char") {
        val yaml = YamlBinaryCodec.charCodec.encodeValue('A')
        assertTrue(yaml == Yaml.Scalar("A"))
      },
      test("decode char") {
        assertTrue(YamlBinaryCodec.charCodec.decodeValue(Yaml.Scalar("A")) == Right('A'))
      },
      test("decode multi-char string fails") {
        assertTrue(YamlBinaryCodec.charCodec.decodeValue(Yaml.Scalar("AB")).isLeft)
      },
      test("decode empty string fails") {
        assertTrue(YamlBinaryCodec.charCodec.decodeValue(Yaml.Scalar("")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.charCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("String codec")(
      test("encode string") {
        val yaml = YamlBinaryCodec.stringCodec.encodeValue("hello")
        assertTrue(yaml == Yaml.Scalar("hello"))
      },
      test("decode string") {
        assertTrue(YamlBinaryCodec.stringCodec.decodeValue(Yaml.Scalar("hello")) == Right("hello"))
      },
      test("decode NullValue returns empty string") {
        assertTrue(YamlBinaryCodec.stringCodec.decodeValue(Yaml.NullValue) == Right(""))
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.stringCodec.decodeValue(Yaml.Mapping.empty).isLeft)
      }
    ),
    suite("BigInt codec")(
      test("encode BigInt") {
        val yaml = YamlBinaryCodec.bigIntCodec.encodeValue(BigInt("12345678901234567890"))
        assertTrue(yaml == Yaml.Scalar("12345678901234567890"))
      },
      test("decode BigInt") {
        assertTrue(
          YamlBinaryCodec.bigIntCodec.decodeValue(Yaml.Scalar("12345678901234567890")) == Right(
            BigInt("12345678901234567890")
          )
        )
      },
      test("decode invalid BigInt fails") {
        assertTrue(YamlBinaryCodec.bigIntCodec.decodeValue(Yaml.Scalar("not_number")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.bigIntCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("BigDecimal codec")(
      test("encode BigDecimal") {
        val yaml = YamlBinaryCodec.bigDecimalCodec.encodeValue(BigDecimal("3.14159265358979"))
        assertTrue(yaml == Yaml.Scalar("3.14159265358979"))
      },
      test("decode BigDecimal") {
        assertTrue(
          YamlBinaryCodec.bigDecimalCodec.decodeValue(Yaml.Scalar("3.14159265358979")) == Right(
            BigDecimal("3.14159265358979")
          )
        )
      },
      test("decode invalid BigDecimal fails") {
        assertTrue(YamlBinaryCodec.bigDecimalCodec.decodeValue(Yaml.Scalar("abc")).isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(YamlBinaryCodec.bigDecimalCodec.decodeValue(Yaml.NullValue).isLeft)
      }
    ),
    suite("string-level encode/decode")(
      test("encodeToString and decode(String)") {
        val codec   = YamlBinaryCodec.intCodec
        val str     = codec.encodeToString(42)
        val decoded = codec.decode(str)
        assertTrue(decoded == Right(42))
      }
    ),
    suite("byte-level encode/decode")(
      test("encode and decode through Array[Byte]") {
        val codec   = YamlBinaryCodec.stringCodec
        val bytes   = codec.encode("hello")
        val decoded = codec.decode(bytes)
        assertTrue(decoded == Right("hello"))
      }
    ),
    suite("ByteBuffer encode/decode")(
      test("encode and decode through ByteBuffer") {
        val codec = YamlBinaryCodec.intCodec
        val buf   = java.nio.ByteBuffer.allocate(64)
        codec.encode(42, buf)
        buf.flip()
        val decoded = codec.decode(buf)
        assertTrue(decoded == Right(42))
      }
    ),
    suite("error handling")(
      test("decode invalid YAML returns Left") {
        val codec  = YamlBinaryCodec.intCodec
        val result = codec.decode("{invalid: [yaml".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      },
      test("decode throws non-fatal exception") {
        val badCodec = new YamlBinaryCodec[String]() {
          def decodeValue(yaml: Yaml): Either[YamlError, String] = throw new RuntimeException("boom")
          def encodeValue(x: String): Yaml                       = Yaml.Scalar(x)
        }
        val result = badCodec.decode("test".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      },
      test("decode(String) throws non-fatal exception") {
        val badCodec = new YamlBinaryCodec[String]() {
          def decodeValue(yaml: Yaml): Either[YamlError, String] = throw new RuntimeException("boom")
          def encodeValue(x: String): Yaml                       = Yaml.Scalar(x)
        }
        val result = badCodec.decode("test")
        assertTrue(result.isLeft)
      },
      test("nullValue returns null by default") {
        val codec = YamlBinaryCodec.stringCodec
        assertTrue(codec.nullValue == null)
      }
    ),
    suite("valueOffset for different types")(
      test("objectType offset") {
        assertTrue(YamlBinaryCodec.stringCodec.valueType == YamlBinaryCodec.objectType)
      },
      test("intType offset") {
        assertTrue(YamlBinaryCodec.intCodec.valueType == YamlBinaryCodec.intType)
      },
      test("longType offset") {
        assertTrue(YamlBinaryCodec.longCodec.valueType == YamlBinaryCodec.longType)
      },
      test("floatType offset") {
        assertTrue(YamlBinaryCodec.floatCodec.valueType == YamlBinaryCodec.floatType)
      },
      test("doubleType offset") {
        assertTrue(YamlBinaryCodec.doubleCodec.valueType == YamlBinaryCodec.doubleType)
      },
      test("booleanType offset") {
        assertTrue(YamlBinaryCodec.booleanCodec.valueType == YamlBinaryCodec.booleanType)
      },
      test("byteType offset") {
        assertTrue(YamlBinaryCodec.byteCodec.valueType == YamlBinaryCodec.byteType)
      },
      test("charType offset") {
        assertTrue(YamlBinaryCodec.charCodec.valueType == YamlBinaryCodec.charType)
      },
      test("shortType offset") {
        assertTrue(YamlBinaryCodec.shortCodec.valueType == YamlBinaryCodec.shortType)
      },
      test("unitType offset") {
        assertTrue(YamlBinaryCodec.unitCodec.valueType == YamlBinaryCodec.unitType)
      }
    ),
    suite("toSchemaError coverage")(
      test("YamlError converts to SchemaError with message") {
        val codec  = YamlBinaryCodec.intCodec
        val result = codec.decode("not-a-number".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      }
    )
  )
}
