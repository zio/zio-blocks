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

import zio.blocks.chunk.Chunk
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

  final case class CustomHeader(count: Int, token: String)
  object CustomHeader {
    implicit val schema: Schema[CustomHeader] = Schema.derived[CustomHeader]
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
      val optionCodec  = Schema[Option[Int]].derive(DefaultHeaderFormat)

      assertTrue(
        intCodec.encodeToHeaders(42).rawGet("value").contains("42"),
        intCodec.decode(Headers("value" -> "42")) == Right(42),
        listCodec.encodeToHeaders(List(1, 2)).rawGetAll("value") == zio.blocks.chunk.Chunk("1", "2"),
        listCodec.decode(Headers("value" -> "1", "value" -> "2")) == Right(List(1, 2)),
        wrapperCodec.encodeToHeaders(UserId("wrapped")).rawGet("value").contains("wrapped"),
        wrapperCodec.decode(Headers("value" -> "wrapped")) == Right(UserId("wrapped")),
        optionCodec.encodeToHeaders(None).toList.isEmpty,
        optionCodec.encodeToHeaders(Some(3)).rawGet("value").contains("3"),
        optionCodec.decode(Headers.empty) == Right(None),
        optionCodec.decode(Headers("value" -> "3")) == Right(Some(3))
      )
    },
    test("respects BindingInstance override for primitive codec") {
      val customIntCodec = new HeaderCodec[Int] {
        def encode(value: Int, output: HeadersBuilder): Unit =
          output.add("value", s"custom-$value")

        def decode(input: Headers): Either[SchemaError, Int] = {
          val raw = input.rawGet("value")
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
        encoded.rawGet("value").contains("custom-42"),
        codec.decode(encoded) == Right(42)
      )
    },
    test("respects custom child codec for record fields case-insensitively") {
      val customIntCodec = new HeaderCodec[Int] {
        def encode(value: Int, output: HeadersBuilder): Unit =
          output.add("value", s"custom-$value")

        def decode(input: Headers): Either[SchemaError, Int] =
          input.rawGet("value") match {
            case Some(s) if s.startsWith("custom-") =>
              Right(s.stripPrefix("custom-").toInt)
            case other =>
              Left(SchemaError(s"Expected custom- prefix, got: $other"))
          }
      }
      val codec   = Schema[CustomHeader].deriving(HeaderCodecDeriver).instance(TypeId.int, customIntCodec).derive
      val value   = CustomHeader(7, "secret")
      val encoded = codec.encodeToHeaders(value)

      assertTrue(
        encoded.rawGet("COUNT").contains("custom-7"),
        encoded.rawGet("TOKEN").contains("secret"),
        codec.decode(encoded) == Right(value),
        codec.decode(Headers("count" -> "custom-7", "token" -> "secret")) == Right(value),
        codec.decode(Headers("COUNT" -> "7", "TOKEN" -> "secret")).isLeft
      )
    },
    test("honors custom Int inside optional, sequence and wrapper fields case-insensitively") {
      import CustomCodecFixtures._

      val codec =
        Schema[NestedInts].deriving(HeaderCodecDeriver).instance(TypeId.int, prefixedHeaderIntCodec()).derive
      val value   = NestedInts(Some(1), List(2, 3), Tag(4))
      val encoded = codec.encodeToHeaders(value)

      assertTrue(
        encoded.rawGetAll("MAYBE") == Chunk("custom-1"),
        encoded.rawGetAll("MANY") == Chunk("custom-2", "custom-3"),
        encoded.rawGet("TAG").contains("custom-4"),
        codec.decode(encoded) == Right(value),
        codec.decode(Headers("maybe" -> "custom-1", "many" -> "custom-2", "many" -> "custom-3", "tag" -> "custom-4")) ==
          Right(value),
        codec.decode(Headers("TAG" -> "custom-4")) == Right(NestedInts(None, Nil, Tag(4))),
        codec.decode(Headers("MAYBE" -> "1", "MANY" -> "2", "TAG" -> "4")).isLeft
      )
    },
    test("rejects off-key custom field codecs but accepts canonical casing") {
      import CustomCodecFixtures._

      val offKey  = Schema[CustomHeader].deriving(HeaderCodecDeriver).instance(TypeId.int, offKeyHeaderIntCodec).derive
      val capital =
        Schema[CustomHeader].deriving(HeaderCodecDeriver).instance(TypeId.int, capitalValueHeaderIntCodec()).derive
      val value   = CustomHeader(7, "secret")
      val encoded = capital.encodeToHeaders(value)

      assertTrue(
        Try(offKey.encodeToHeaders(value)).isFailure,
        encoded.rawGet("COUNT").contains("custom-7"),
        capital.decode(encoded) == Right(value),
        capital.decode(Headers("count" -> "custom-7", "token" -> "secret")) == Right(value)
      )
    },
    test("re-anchors only the root custom key to the field path") {
      import CustomCodecFixtures._
      import zio.blocks.schema.DynamicOptic.Node.Field

      val codec  = Schema[IntPair].deriving(HeaderCodecDeriver).instance(TypeId.int, pathHeaderIntCodec).derive
      val result = codec.decode(Headers("FIRST" -> "1", "SECOND" -> "2"))

      assertTrue(
        result.swap.exists(_.errors.size == 2),
        // Only the root "value" node is re-anchored; the nested one is preserved.
        result.swap.exists(
          _.errors.toList.map(_.source.nodes.toList) == List(
            List(Field("FIRST"), Field("value")),
            List(Field("SECOND"), Field("value"))
          )
        )
      )
    },
    test("composes custom Int through top-level List") {
      import CustomCodecFixtures._

      val codec =
        Schema[List[Int]].deriving(HeaderCodecDeriver).instance(TypeId.int, prefixedHeaderIntCodec()).derive
      val value = List(1, 2)

      assertTrue(
        codec.encodeToHeaders(value).rawGetAll("value") == Chunk("custom-1", "custom-2"),
        codec.decode(Headers("value" -> "custom-1", "value" -> "custom-2")) == Right(value),
        codec.decode(Headers("value" -> "1")).isLeft
      )
    },
    test("honors an installed derived wrapper codec with a customized child") {
      import CustomCodecFixtures._

      val installed =
        Schema[Tag].deriving(HeaderCodecDeriver).instance(TypeId.int, prefixedHeaderIntCodec()).derive
      val codec = Schema[TagBox].deriving(HeaderCodecDeriver).instance(TypeId.of[Tag], installed).derive
      val value = TagBox(Tag(9))

      assertTrue(
        codec.encodeToHeaders(value).rawGet("TAG").contains("custom-9"),
        codec.decode(Headers("TAG" -> "custom-9")) == Right(value),
        codec.decode(Headers("tag" -> "custom-9")) == Right(value),
        codec.decode(Headers("TAG" -> "9")).isLeft
      )
    },
    test("reports malformed top-level char values") {
      val codec = Schema[Char].derive(DefaultHeaderFormat)

      assertTrue(
        codec.decode(Headers("value" -> "too-long")).swap.exists(_.message.contains("Expected single character"))
      )
    },
    test("rejects unsupported top-level map and dynamic schemas") {
      val mapCodec     = Schema[Map[String, Int]].derive(DefaultHeaderFormat)
      val dynamicCodec = Schema[DynamicValue].derive(DefaultHeaderFormat)
      val dynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(1))

      assertTrue(
        Try(mapCodec.encodeToHeaders(Map("a" -> 1))).isFailure,
        Try(dynamicCodec.encodeToHeaders(dynamicValue)).isFailure
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
