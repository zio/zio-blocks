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

package zio.http.schema

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.SchemaError
import zio.blocks.typeid.TypeId
import zio.http.Headers
import zio.http.HeadersBuilder
import zio.test._

import java.util.UUID
import scala.util.Try

object HeaderCodecSpec extends ZIOSpecDefault {
  final case class SimpleHeaders(traceId: String, apiKey: String)
  object SimpleHeaders {
    implicit val schema: Schema[SimpleHeaders] = Schema.derived[SimpleHeaders]
  }

  final case class UserId(value: String) extends AnyVal
  object UserId {
    implicit val schema: Schema[UserId] = Schema[String].transform(UserId(_), _.value)
  }

  final case class RichHeaders(
    shortValue: Short,
    byteValue: Byte,
    bigIntValue: BigInt,
    bigDecimalValue: BigDecimal,
    uuidValue: UUID,
    charValue: Char
  )
  object RichHeaders {
    implicit val schema: Schema[RichHeaders] = Schema.derived[RichHeaders]
  }

  def spec: Spec[TestEnvironment, Any] = suite("HeaderCodec")(
    test("round-trips simple string record") {
      val codec   = Schema[SimpleHeaders].derive(DefaultHeaderFormat)
      val value   = SimpleHeaders("trace-1", "secret")
      val encoded = codec.encodeToHeaders(value)

      assertTrue(
        codec.decode(encoded) == Right(value)
      )
    },
    test("encodes camelCase field names as Kebab-Case") {
      val codec   = Schema[SimpleHeaders].derive(DefaultHeaderFormat)
      val value   = SimpleHeaders("trace-1", "secret")
      val encoded = codec.encodeToHeaders(value)

      assertTrue(
        encoded.rawGet("Trace-Id").contains("trace-1"),
        encoded.rawGet("Api-Key").contains("secret")
      )
    },
    test("reads headers case-insensitively") {
      val codec   = Schema[SimpleHeaders].derive(DefaultHeaderFormat)
      val headers = Headers("trace-id" -> "trace-1", "API-KEY" -> "secret")

      assertTrue(codec.decode(headers) == Right(SimpleHeaders("trace-1", "secret")))
    },
    test("round-trips remaining primitive branches") {
      val codec   = Schema[RichHeaders].derive(DefaultHeaderFormat)
      val uuid    = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
      val value   = RichHeaders(7, 3, BigInt("1234567890123456789"), BigDecimal("12.34"), uuid, 'z')
      val encoded = codec.encodeToHeaders(value)

      assertTrue(
        codec.decode(encoded) == Right(value),
        encoded.rawGet("Short-Value").contains("7"),
        encoded.rawGet("Byte-Value").contains("3"),
        encoded.rawGet("Big-Int-Value").contains("1234567890123456789"),
        encoded.rawGet("Big-Decimal-Value").contains("12.34"),
        encoded.rawGet("Uuid-Value").contains(uuid.toString),
        encoded.rawGet("Char-Value").contains("z")
      )
    },
    test("supports top-level primitive, sequence, and wrapper codecs") {
      val intCodec     = Schema[Int].derive(DefaultHeaderFormat)
      val listCodec    = Schema[List[Int]].derive(DefaultHeaderFormat)
      val wrapperCodec = Schema[UserId].derive(DefaultHeaderFormat)

      assertTrue(
        intCodec.encodeToHeaders(42).rawGet("Value").contains("42"),
        intCodec.decode(Headers("value" -> "42")) == Right(42),
        listCodec.encodeToHeaders(List(1, 2)).rawGetAll("Value") == zio.blocks.chunk.Chunk("1", "2"),
        listCodec.decode(Headers("value" -> "1", "value" -> "2")) == Right(List(1, 2)),
        wrapperCodec.encodeToHeaders(UserId("wrapped")).rawGet("Value").contains("wrapped"),
        wrapperCodec.decode(Headers("value" -> "wrapped")) == Right(UserId("wrapped"))
      )
    },
    test("respects BindingInstance override for primitive codec") {
      val customIntCodec = new HeaderCodec[Int] {
        def encode(value: Int, output: HeadersBuilder): Unit =
          output.add("Value", s"custom-$value")

        def decode(input: Headers): Either[SchemaError, Int] = {
          val raw = input.rawGet("Value")
          raw match {
            case Some(s) if s.startsWith("custom-") =>
              Right(s.stripPrefix("custom-").toInt)
            case other =>
              Left(SchemaError(s"Expected custom- prefix, got: $other"))
          }
        }
      }
      val codec = Schema[Int].deriving(HeaderCodecDeriver).instance(TypeId.int, customIntCodec).derive

      val encoded = codec.encodeToHeaders(42)

      assertTrue(
        encoded.rawGet("Value").contains("custom-42"),
        codec.decode(encoded) == Right(42)
      )
    },
    test("reports malformed top-level char values") {
      val codec = Schema[Char].derive(DefaultHeaderFormat)

      assertTrue(
        codec.decode(Headers("value" -> "too-long")).swap.exists(_.message.contains("Expected single character"))
      )
    },
    test("rejects unsupported top-level option, map, and dynamic schemas") {
      val optionCodec  = Schema[Option[Int]].derive(DefaultHeaderFormat)
      val mapCodec     = Schema[Map[String, Int]].derive(DefaultHeaderFormat)
      val dynamicCodec = Schema[DynamicValue].derive(DefaultHeaderFormat)
      val dynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(1))

      assertTrue(
        Try(optionCodec.encodeToHeaders(None)).isFailure,
        Try(mapCodec.encodeToHeaders(Map("a" -> 1))).isFailure,
        Try(dynamicCodec.encodeToHeaders(dynamicValue)).isFailure
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
