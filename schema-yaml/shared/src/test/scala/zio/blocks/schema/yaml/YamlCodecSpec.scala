/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.yaml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._
import scala.util.Try

object YamlCodecSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlCodecSpec")(
    suite("Unit codec")(
      test("encode unit") {
        val yaml = YamlCodec.unitCodec.encodeValue(())
        assertTrue(yaml == Yaml.NullValue)
      },
      test("decode null value") {
        val result = YamlCodec.unitCodec.decodeValue(Yaml.NullValue)
        assertTrue(result == ())
      },
      test("decode 'null' scalar") {
        val result = YamlCodec.unitCodec.decodeValue(Yaml.Scalar("null"))
        assertTrue(result == ())
      },
      test("decode '~' scalar") {
        val result = YamlCodec.unitCodec.decodeValue(Yaml.Scalar("~"))
        assertTrue(result == ())
      },
      test("decode empty scalar") {
        val result = YamlCodec.unitCodec.decodeValue(Yaml.Scalar(""))
        assertTrue(result == ())
      },
      test("decode non-null fails") {
        val result = Try(YamlCodec.unitCodec.decodeValue(Yaml.Scalar("something"))).toEither
        assertTrue(result.isLeft)
      },
      test("decode mapping fails") {
        val result = Try(YamlCodec.unitCodec.decodeValue(Yaml.Mapping.empty)).toEither
        assertTrue(result.isLeft)
      }
    ),
    suite("Boolean codec")(
      test("encode true") {
        val yaml = YamlCodec.booleanCodec.encodeValue(true)
        assertTrue(yaml == Yaml.Scalar("true", tag = Some(YamlTag.Bool)))
      },
      test("encode false") {
        val yaml = YamlCodec.booleanCodec.encodeValue(false)
        assertTrue(yaml == Yaml.Scalar("false", tag = Some(YamlTag.Bool)))
      },
      test("decode true") {
        assertTrue(YamlCodec.booleanCodec.decodeValue(Yaml.Scalar("true")) == true)
      },
      test("decode false") {
        assertTrue(YamlCodec.booleanCodec.decodeValue(Yaml.Scalar("false")) == false)
      },
      test("decode invalid boolean fails") {
        assertTrue(Try(YamlCodec.booleanCodec.decodeValue(Yaml.Scalar("yes"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.booleanCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      },
      test("round-trip through bytes") {
        val codec   = YamlCodec.booleanCodec
        val encoded = codec.encode(true)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(true))
      }
    ),
    suite("Byte codec")(
      test("encode byte") {
        val yaml = YamlCodec.byteCodec.encodeValue(42.toByte)
        assertTrue(yaml == Yaml.Scalar("42", tag = Some(YamlTag.Int)))
      },
      test("decode byte") {
        assertTrue(YamlCodec.byteCodec.decodeValue(Yaml.Scalar("42")) == 42.toByte)
      },
      test("decode invalid byte fails") {
        assertTrue(Try(YamlCodec.byteCodec.decodeValue(Yaml.Scalar("abc"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.byteCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Short codec")(
      test("encode short") {
        val yaml = YamlCodec.shortCodec.encodeValue(1000.toShort)
        assertTrue(yaml == Yaml.Scalar("1000", tag = Some(YamlTag.Int)))
      },
      test("decode short") {
        assertTrue(YamlCodec.shortCodec.decodeValue(Yaml.Scalar("1000")) == 1000.toShort)
      },
      test("decode invalid short fails") {
        assertTrue(Try(YamlCodec.shortCodec.decodeValue(Yaml.Scalar("abc"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.shortCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Int codec")(
      test("encode int") {
        val yaml = YamlCodec.intCodec.encodeValue(42)
        assertTrue(yaml == Yaml.Scalar("42", tag = Some(YamlTag.Int)))
      },
      test("decode int") {
        assertTrue(YamlCodec.intCodec.decodeValue(Yaml.Scalar("42")) == 42)
      },
      test("decode invalid int fails") {
        assertTrue(Try(YamlCodec.intCodec.decodeValue(Yaml.Scalar("abc"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.intCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Long codec")(
      test("encode long") {
        val yaml = YamlCodec.longCodec.encodeValue(999999999999L)
        assertTrue(yaml == Yaml.Scalar("999999999999", tag = Some(YamlTag.Int)))
      },
      test("decode long") {
        assertTrue(YamlCodec.longCodec.decodeValue(Yaml.Scalar("999999999999")) == 999999999999L)
      },
      test("decode invalid long fails") {
        assertTrue(Try(YamlCodec.longCodec.decodeValue(Yaml.Scalar("not_a_number"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.longCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Float codec")(
      test("encode float") {
        val yaml = YamlCodec.floatCodec.encodeValue(3.14f)
        assertTrue(yaml == Yaml.Scalar("3.14", tag = Some(YamlTag.Float)))
      },
      test("decode float") {
        assertTrue(YamlCodec.floatCodec.decodeValue(Yaml.Scalar("3.14")) == 3.14f)
      },
      test("decode invalid float fails") {
        assertTrue(Try(YamlCodec.floatCodec.decodeValue(Yaml.Scalar("abc"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.floatCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Double codec")(
      test("encode double") {
        val yaml = YamlCodec.doubleCodec.encodeValue(3.14159)
        assertTrue(yaml == Yaml.Scalar("3.14159", tag = Some(YamlTag.Float)))
      },
      test("decode double") {
        assertTrue(YamlCodec.doubleCodec.decodeValue(Yaml.Scalar("3.14159")) == 3.14159)
      },
      test("decode invalid double fails") {
        assertTrue(Try(YamlCodec.doubleCodec.decodeValue(Yaml.Scalar("xyz"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.doubleCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("Char codec")(
      test("encode char") {
        val yaml = YamlCodec.charCodec.encodeValue('A')
        assertTrue(yaml == Yaml.Scalar("A"))
      },
      test("decode char") {
        assertTrue(YamlCodec.charCodec.decodeValue(Yaml.Scalar("A")) == 'A')
      },
      test("decode multi-char string fails") {
        assertTrue(Try(YamlCodec.charCodec.decodeValue(Yaml.Scalar("AB"))).toEither.isLeft)
      },
      test("decode empty string fails") {
        assertTrue(Try(YamlCodec.charCodec.decodeValue(Yaml.Scalar(""))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.charCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("String codec")(
      test("encode string") {
        val yaml = YamlCodec.stringCodec.encodeValue("hello")
        assertTrue(yaml == Yaml.Scalar("hello"))
      },
      test("decode string") {
        assertTrue(YamlCodec.stringCodec.decodeValue(Yaml.Scalar("hello")) == "hello")
      },
      test("decode NullValue returns empty string") {
        assertTrue(YamlCodec.stringCodec.decodeValue(Yaml.NullValue) == "")
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.stringCodec.decodeValue(Yaml.Mapping.empty)).toEither.isLeft)
      }
    ),
    suite("BigInt codec")(
      test("encode BigInt") {
        val yaml = YamlCodec.bigIntCodec.encodeValue(BigInt("12345678901234567890"))
        assertTrue(yaml == Yaml.Scalar("12345678901234567890"))
      },
      test("decode BigInt") {
        assertTrue(
          YamlCodec.bigIntCodec.decodeValue(Yaml.Scalar("12345678901234567890")) == BigInt("12345678901234567890")
        )
      },
      test("decode invalid BigInt fails") {
        assertTrue(Try(YamlCodec.bigIntCodec.decodeValue(Yaml.Scalar("not_number"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.bigIntCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("BigDecimal codec")(
      test("encode BigDecimal") {
        val yaml = YamlCodec.bigDecimalCodec.encodeValue(BigDecimal("3.14159265358979"))
        assertTrue(yaml == Yaml.Scalar("3.14159265358979"))
      },
      test("decode BigDecimal") {
        assertTrue(
          YamlCodec.bigDecimalCodec.decodeValue(Yaml.Scalar("3.14159265358979")) == BigDecimal("3.14159265358979")
        )
      },
      test("decode invalid BigDecimal fails") {
        assertTrue(Try(YamlCodec.bigDecimalCodec.decodeValue(Yaml.Scalar("abc"))).toEither.isLeft)
      },
      test("decode non-scalar fails") {
        assertTrue(Try(YamlCodec.bigDecimalCodec.decodeValue(Yaml.NullValue)).toEither.isLeft)
      }
    ),
    suite("string-level encode/decode")(
      test("encodeToString and decode(String)") {
        val codec   = YamlCodec.intCodec
        val str     = codec.encodeToString(42)
        val decoded = codec.decode(str)
        assertTrue(decoded == Right(42))
      }
    ),
    suite("byte-level encode/decode")(
      test("encode and decode through Array[Byte]") {
        val codec   = YamlCodec.stringCodec
        val bytes   = codec.encode("hello")
        val decoded = codec.decode(bytes)
        assertTrue(decoded == Right("hello"))
      }
    ),
    suite("ByteBuffer encode/decode")(
      test("encode and decode through ByteBuffer") {
        val codec = YamlCodec.intCodec
        val buf   = java.nio.ByteBuffer.allocate(64)
        codec.encode(42, buf)
        buf.flip()
        val decoded = codec.decode(buf)
        assertTrue(decoded == Right(42))
      }
    ),
    suite("error handling")(
      test("decode invalid YAML returns Left") {
        val codec  = YamlCodec.intCodec
        val result = codec.decode("{invalid: [yaml".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      },
      test("decode throws non-fatal exception") {
        val badCodec = new YamlCodec[String]() {
          def decodeValue(yaml: Yaml): String = throw new RuntimeException("boom")
          def encodeValue(x: String): Yaml    = Yaml.Scalar(x)
        }
        val result = badCodec.decode("test".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      },
      test("decode(String) throws non-fatal exception") {
        val badCodec = new YamlCodec[String]() {
          def decodeValue(yaml: Yaml): String = throw new RuntimeException("boom")
          def encodeValue(x: String): Yaml    = Yaml.Scalar(x)
        }
        val result = badCodec.decode("test")
        assertTrue(result.isLeft)
      }
    ),
    suite("toSchemaError coverage")(
      test("YamlError converts to SchemaError with message") {
        val codec  = YamlCodec.intCodec
        val result = codec.decode("not-a-number".getBytes("UTF-8"))
        assertTrue(result.isLeft)
      }
    )
  )
}
