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

package zio.blocks.schema.xml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object XmlCodecSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlCodecSpec")(
    suite("unitCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.unitCodec
        val result = codec.decode(codec.encode(()))
        assertTrue(result.isRight)
      },
      test("encodeToString produces valid XML") {
        val codec = XmlCodec.unitCodec
        val xml   = codec.encodeToString(())
        assertTrue(xml == "<unit/>")
      },
      test("decode from string") {
        val codec  = XmlCodec.unitCodec
        val xml    = "<unit/>"
        val result = codec.decode(xml)
        assertTrue(result.isRight)
      }
    ),
    suite("booleanCodec")(
      test("round-trip encode/decode true") {
        val codec  = XmlCodec.booleanCodec
        val result = codec.decode(codec.encode(true))
        assertTrue(result == Right(true))
      },
      test("round-trip encode/decode false") {
        val codec  = XmlCodec.booleanCodec
        val result = codec.decode(codec.encode(false))
        assertTrue(result == Right(false))
      },
      test("encodeToString produces valid XML") {
        val codec = XmlCodec.booleanCodec
        val xml   = codec.encodeToString(true)
        assertTrue(xml == "<value>true</value>")
      }
    ),
    suite("byteCodec")(
      test("round-trip encode/decode") {
        val codec       = XmlCodec.byteCodec
        val value: Byte = 42
        val result      = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.byteCodec
        val value  = Byte.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.byteCodec
        val value  = Byte.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("shortCodec")(
      test("round-trip encode/decode") {
        val codec        = XmlCodec.shortCodec
        val value: Short = 1234
        val result       = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.shortCodec
        val value  = Short.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.shortCodec
        val value  = Short.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("intCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.intCodec
        val value  = 123456
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.intCodec
        val value  = Int.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.intCodec
        val value  = Int.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("longCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.longCodec
        val value  = 123456789L
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.longCodec
        val value  = Long.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.longCodec
        val value  = Long.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("floatCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.floatCodec
        val value  = 3.14f
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.floatCodec
        val value  = Float.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.floatCodec
        val value  = Float.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("doubleCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.doubleCodec
        val value  = 3.14159265358979
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode min value") {
        val codec  = XmlCodec.doubleCodec
        val value  = Double.MinValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode max value") {
        val codec  = XmlCodec.doubleCodec
        val value  = Double.MaxValue
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("charCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.charCodec
        val value  = 'A'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode special char") {
        val codec  = XmlCodec.charCodec
        val value  = '\n'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode unicode") {
        val codec  = XmlCodec.charCodec
        val value  = '€'
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("stringCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.stringCodec
        val value  = "Hello, World!"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode empty string") {
        val codec  = XmlCodec.stringCodec
        val value  = ""
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode string with special chars") {
        val codec  = XmlCodec.stringCodec
        val value  = "Hello <>&\" World"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode unicode string") {
        val codec  = XmlCodec.stringCodec
        val value  = "Hello 世界 🌍"
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("bigIntCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.bigIntCodec
        val value  = BigInt("123456789012345678901234567890")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode small value") {
        val codec  = XmlCodec.bigIntCodec
        val value  = BigInt(42)
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode negative value") {
        val codec  = XmlCodec.bigIntCodec
        val value  = BigInt("-999999999999999999999")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    ),
    suite("bigDecimalCodec")(
      test("round-trip encode/decode") {
        val codec  = XmlCodec.bigDecimalCodec
        val value  = BigDecimal("123456789.123456789")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode small value") {
        val codec  = XmlCodec.bigDecimalCodec
        val value  = BigDecimal("3.14")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode negative value") {
        val codec  = XmlCodec.bigDecimalCodec
        val value  = BigDecimal("-999.999")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      },
      test("round-trip encode/decode scientific notation") {
        val codec  = XmlCodec.bigDecimalCodec
        val value  = BigDecimal("1.23E+10")
        val result = codec.decode(codec.encode(value))
        assertTrue(result == Right(value))
      }
    )
  )
}
