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
import zio.http.QueryParams
import zio.http.QueryParamsBuilder
import zio.test._

import java.util.UUID
import scala.util.Try

object QueryCodecSpec extends ZIOSpecDefault {
  final case class Simple(page: Int, name: String, active: Boolean)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived[Simple]
  }

  final case class OptionalFields(page: Option[Int], name: Option[String])
  object OptionalFields {
    implicit val schema: Schema[OptionalFields] = Schema.derived[OptionalFields]
  }

  final case class MultiValues(tags: List[String], ids: Chunk[Int])
  object MultiValues {
    implicit val schema: Schema[MultiValues] = Schema.derived[MultiValues]
  }

  final case class UserId(value: String) extends AnyVal
  object UserId {
    implicit val schema: Schema[UserId] = Schema[String].transform(UserId(_), _.value)
  }

  final case class Wrapped(id: UserId)
  object Wrapped {
    implicit val schema: Schema[Wrapped] = Schema.derived[Wrapped]
  }

  final case class RichPrimitives(
    shortValue: Short,
    byteValue: Byte,
    bigIntValue: BigInt,
    bigDecimalValue: BigDecimal,
    uuidValue: UUID,
    charValue: Char
  )
  object RichPrimitives {
    implicit val schema: Schema[RichPrimitives] = Schema.derived[RichPrimitives]
  }

  def spec: Spec[TestEnvironment, Any] = suite("QueryCodec")(
    test("round-trips simple primitive record") {
      val codec   = Schema[Simple].derive(DefaultQueryFormat)
      val value   = Simple(42, "alice", active = true)
      val encoded = codec.encodeToQueryParams(value)

      assertTrue(
        encoded == QueryParams("page" -> "42", "name" -> "alice", "active" -> "true"),
        codec.decode(encoded) == Right(value)
      )
    },
    test("round-trips option fields with absent key as None") {
      val codec   = Schema[OptionalFields].derive(DefaultQueryFormat)
      val value   = OptionalFields(page = Some(7), name = None)
      val encoded = codec.encodeToQueryParams(value)

      assertTrue(
        encoded == QueryParams("page" -> "7"),
        codec.decode(encoded) == Right(value),
        codec.decode(QueryParams.empty) == Right(OptionalFields(None, None))
      )
    },
    test("round-trips list and chunk fields as repeated params") {
      val codec   = Schema[MultiValues].derive(DefaultQueryFormat)
      val value   = MultiValues(List("a", "b"), Chunk(1, 2, 3))
      val encoded = codec.encodeToQueryParams(value)

      assertTrue(
        encoded == QueryParams("tags" -> "a", "tags" -> "b", "ids" -> "1", "ids" -> "2", "ids" -> "3"),
        codec.decode(encoded) == Right(value)
      )
    },
    test("round-trips wrapper fields") {
      val codec   = Schema[Wrapped].derive(DefaultQueryFormat)
      val value   = Wrapped(UserId("user-1"))
      val encoded = codec.encodeToQueryParams(value)

      assertTrue(
        encoded == QueryParams("id" -> "user-1"),
        codec.decode(encoded) == Right(value)
      )
    },
    test("round-trips remaining primitive branches") {
      val codec   = Schema[RichPrimitives].derive(DefaultQueryFormat)
      val uuid    = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
      val value   = RichPrimitives(7, 3, BigInt("1234567890123456789"), BigDecimal("12.34"), uuid, 'z')
      val encoded = codec.encodeToQueryParams(value)

      assertTrue(
        codec.decode(encoded) == Right(value),
        encoded.getFirst("shortValue").contains("7"),
        encoded.getFirst("byteValue").contains("3"),
        encoded.getFirst("bigIntValue").contains("1234567890123456789"),
        encoded.getFirst("bigDecimalValue").contains("12.34"),
        encoded.getFirst("uuidValue").contains(uuid.toString),
        encoded.getFirst("charValue").contains("z")
      )
    },
    test("supports top-level primitive, sequence, and wrapper codecs") {
      val intCodec     = Schema[Int].derive(DefaultQueryFormat)
      val listCodec    = Schema[List[Int]].derive(DefaultQueryFormat)
      val wrapperCodec = Schema[UserId].derive(DefaultQueryFormat)

      assertTrue(
        intCodec.encodeToQueryParams(42) == QueryParams("value" -> "42"),
        intCodec.decode(QueryParams("value" -> "42")) == Right(42),
        listCodec.encodeToQueryParams(List(1, 2)) == QueryParams("value" -> "1", "value" -> "2"),
        listCodec.decode(QueryParams("value" -> "1", "value" -> "2")) == Right(List(1, 2)),
        wrapperCodec.encodeToQueryParams(UserId("wrapped")) == QueryParams("value" -> "wrapped"),
        wrapperCodec.decode(QueryParams("value" -> "wrapped")) == Right(UserId("wrapped"))
      )
    },
    test("respects BindingInstance override for primitive codec") {
      val customIntCodec = new QueryCodec[Int] {
        def encode(value: Int, output: QueryParamsBuilder): Unit =
          output.add("value", s"custom-$value")

        def decode(input: QueryParams): Either[SchemaError, Int] =
          input.getFirst("value") match {
            case Some(s) if s.startsWith("custom-") =>
              Right(s.stripPrefix("custom-").toInt)
            case other =>
              Left(SchemaError(s"Expected custom- prefix, got: $other"))
          }
      }
      val codec = Schema[Int].deriving(QueryCodecDeriver).instance(TypeId.int, customIntCodec).derive

      val encoded = codec.encodeToQueryParams(42)

      assertTrue(
        encoded == QueryParams("value" -> "custom-42"),
        codec.decode(encoded) == Right(42)
      )
    },
    test("reports malformed top-level char values") {
      val codec = Schema[Char].derive(DefaultQueryFormat)

      assertTrue(
        codec.decode(QueryParams("value" -> "too-long")).swap.exists(_.message.contains("Expected single character"))
      )
    },
    test("rejects unsupported top-level option, map, and dynamic schemas") {
      val optionCodec  = Schema[Option[Int]].derive(DefaultQueryFormat)
      val mapCodec     = Schema[Map[String, Int]].derive(DefaultQueryFormat)
      val dynamicCodec = Schema[DynamicValue].derive(DefaultQueryFormat)
      val dynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(1))

      assertTrue(
        Try(optionCodec.encodeToQueryParams(None)).isFailure,
        Try(mapCodec.encodeToQueryParams(Map("a" -> 1))).isFailure,
        Try(dynamicCodec.encodeToQueryParams(dynamicValue)).isFailure
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
