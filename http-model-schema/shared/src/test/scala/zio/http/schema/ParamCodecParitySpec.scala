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
import zio.blocks.schema.{DynamicOptic, Schema, SchemaError}
import zio.http.{Headers, QueryParams}
import zio.test._

import java.util.UUID

object ParamCodecParitySpec extends ZIOSpecDefault {
  final case class Point(x: Int, y: Int)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived[Point]
  }

  final case class Even(value: Int)
  object Even {
    implicit val schema: Schema[Even] =
      Schema[Int].transform(
        v => if (v % 2 == 0) Even(v) else throw new IllegalArgumentException("not even"),
        _.value
      )
  }

  final case class EvenBox(even: Even)
  object EvenBox {
    implicit val schema: Schema[EvenBox] = Schema.derived[EvenBox]
  }

  final case class ScoreBoard(ids: List[Int])
  object ScoreBoard {
    implicit val schema: Schema[ScoreBoard] = Schema.derived[ScoreBoard]
  }

  private val uuid: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

  def spec: Spec[TestEnvironment, Any] = suite("ParamCodecParity")(
    test("single-value boolean parsing agrees across query and header paths") {
      assertTrue(
        QueryParams("flag" -> "true").query[Boolean]("flag") == Right(true),
        Headers("flag" -> "true").header[Boolean]("flag") == Right(true),
        QueryParams("flag" -> "false").query[Boolean]("flag") == Right(false),
        Headers("flag" -> "false").header[Boolean]("flag") == Right(false),
        QueryParams("flag" -> "yes").query[Boolean]("flag") == Left(
          QueryParamError.Malformed("flag", "yes", "Cannot parse 'yes' as Boolean")
        ),
        Headers("flag" -> "yes").header[Boolean]("flag") == Left(
          HeaderError.Malformed("flag", "yes", "Cannot parse 'yes' as Boolean")
        ),
        QueryParams("flag" -> "1").query[Boolean]("flag") == Left(
          QueryParamError.Malformed("flag", "1", "Cannot parse '1' as Boolean")
        ),
        Headers("flag" -> "1").header[Boolean]("flag") == Left(
          HeaderError.Malformed("flag", "1", "Cannot parse '1' as Boolean")
        ),
        QueryParams.empty.query[Boolean]("flag") == Left(QueryParamError.Missing("flag")),
        Headers.empty.header[Boolean]("flag") == Left(HeaderError.Missing("flag"))
      )
    },
    test("query and header derivers agree on the same record shape") {
      val queryCodec  = Schema[Point].derive(DefaultQueryFormat)
      val headerCodec = Schema[Point].derive(DefaultHeaderFormat)
      val value       = Point(1, 2)
      val params      = queryCodec.encodeToQueryParams(value)
      val headers     = headerCodec.encodeToHeaders(value)

      assertTrue(
        params == QueryParams("x" -> "1", "y" -> "2"),
        headers.rawGet("X").contains("1"),
        headers.rawGet("Y").contains("2"),
        queryCodec.decode(params) == Right(value),
        headerCodec.decode(headers) == Right(value)
      )
    },
    test("equivalent query and header inputs decode to the same independently specified value and errors") {
      val queryCodec  = Schema[Point].derive(DefaultQueryFormat)
      val headerCodec = Schema[Point].derive(DefaultHeaderFormat)
      val expected    = Point(3, 4)

      assertTrue(
        queryCodec.decode(QueryParams("x" -> "3", "y" -> "4")) == Right(expected),
        headerCodec.decode(Headers("X" -> "3", "Y" -> "4")) == Right(expected),
        queryCodec.decode(QueryParams("x" -> "3", "y" -> "4")).toOption ==
          headerCodec.decode(Headers("X" -> "3", "Y" -> "4")).toOption,
        queryCodec.decode(QueryParams("x" -> "bad", "y" -> "4")) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("x")),
            "Malformed query parameter 'x' value 'bad': Cannot parse 'bad' as Int"
          )
        ),
        headerCodec.decode(Headers("X" -> "bad", "Y" -> "4")) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("X")),
            "Malformed header 'X' value 'bad': Cannot parse 'bad' as Int"
          )
        )
      )
    },
    test("record decode accumulates all bad fields with structured paths") {
      val queryCodec  = Schema[Point].derive(DefaultQueryFormat)
      val headerCodec = Schema[Point].derive(DefaultHeaderFormat)
      val badParams   = QueryParams("x" -> "bad", "y" -> "worse")
      val badHeaders  = Headers("X" -> "bad", "Y" -> "worse")

      assertTrue(
        queryCodec.decode(badParams) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("x")),
            "Malformed query parameter 'x' value 'bad': Cannot parse 'bad' as Int"
          ) ++ SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("y")),
            "Malformed query parameter 'y' value 'worse': Cannot parse 'worse' as Int"
          )
        ),
        headerCodec.decode(badHeaders) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("X")),
            "Malformed header 'X' value 'bad': Cannot parse 'bad' as Int"
          ) ++ SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("Y")),
            "Malformed header 'Y' value 'worse': Cannot parse 'worse' as Int"
          )
        )
      )
    },
    test("mixed missing and malformed fields report both errors in field order") {
      val queryCodec  = Schema[Point].derive(DefaultQueryFormat)
      val headerCodec = Schema[Point].derive(DefaultHeaderFormat)

      val queryResult  = queryCodec.decode(QueryParams("y" -> "bad"))
      val headerResult = headerCodec.decode(Headers("Y" -> "bad"))

      assertTrue(
        queryResult == Left(
          SchemaError.missingField(Nil, "x") ++ SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("y")),
            "Malformed query parameter 'y' value 'bad': Cannot parse 'bad' as Int"
          )
        ),
        headerResult == Left(
          SchemaError.missingField(Nil, "X") ++ SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("Y")),
            "Malformed header 'Y' value 'bad': Cannot parse 'bad' as Int"
          )
        ),
        queryResult.swap.exists { err =>
          val sources = err.errors.toList.map(_.source.nodes.toList)
          sources == List(Nil, List(DynamicOptic.Node.Field("y")))
        },
        headerResult.swap.exists { err =>
          val sources = err.errors.toList.map(_.source.nodes.toList)
          sources == List(Nil, List(DynamicOptic.Node.Field("Y")))
        },
        queryResult.swap.exists(_.errors.toList.head.isInstanceOf[SchemaError.MissingField]),
        headerResult.swap.exists(_.errors.toList.head.isInstanceOf[SchemaError.MissingField])
      )
    },
    test("wrapper failures carry the structured field path on both transports") {
      val queryCodec  = Schema[EvenBox].derive(DefaultQueryFormat)
      val headerCodec = Schema[EvenBox].derive(DefaultHeaderFormat)

      assertTrue(
        queryCodec.decode(QueryParams("even" -> "3")) == Left(
          SchemaError.conversionFailed(List(DynamicOptic.Node.Field("even")), "not even")
        ),
        queryCodec.decode(QueryParams("even" -> "4")) == Right(EvenBox(Even(4))),
        headerCodec.decode(Headers("EVEN" -> "3")) == Left(
          SchemaError.conversionFailed(List(DynamicOptic.Node.Field("EVEN")), "not even")
        ),
        headerCodec.decode(Headers("EVEN" -> "4")) == Right(EvenBox(Even(4)))
      )
    },
    test("sequence record round-trips and names the bad element value with its field path") {
      val queryCodec  = Schema[ScoreBoard].derive(DefaultQueryFormat)
      val headerCodec = Schema[ScoreBoard].derive(DefaultHeaderFormat)
      val value       = ScoreBoard(List(10, 20, 30))
      // Bad element at index 1; the error identifies it by value and field path.
      val badParams  = QueryParams("ids" -> "10", "ids" -> "bad", "ids" -> "30")
      val badHeaders = Headers("IDS" -> "10", "IDS" -> "bad", "IDS" -> "30")

      assertTrue(
        queryCodec.encodeToQueryParams(value) == QueryParams("ids" -> "10", "ids" -> "20", "ids" -> "30"),
        headerCodec.encodeToHeaders(value).rawGetAll("IDS") == Chunk("10", "20", "30"),
        queryCodec.decode(QueryParams("ids" -> "10", "ids" -> "20", "ids" -> "30")) == Right(value),
        headerCodec.decode(Headers("IDS" -> "10", "IDS" -> "20", "IDS" -> "30")) == Right(value),
        queryCodec.decode(badParams) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("ids")),
            "Malformed query parameter 'ids' value 'bad': Cannot parse 'bad' as Int"
          )
        ),
        headerCodec.decode(badHeaders) == Left(
          SchemaError.conversionFailed(
            List(DynamicOptic.Node.Field("IDS")),
            "Malformed header 'IDS' value 'bad': Cannot parse 'bad' as Int"
          )
        ),
        queryCodec.decode(badParams).swap.exists { err =>
          err.errors.size == 1 &&
          err.errors.head.source.nodes.toList == List(DynamicOptic.Node.Field("ids"))
        },
        headerCodec.decode(badHeaders).swap.exists { err =>
          err.errors.size == 1 &&
          err.errors.head.source.nodes.toList == List(DynamicOptic.Node.Field("IDS"))
        }
      )
    },
    test("primitive parsing agrees across transports for valid and invalid literals") {
      assertTrue(
        QueryParams("v" -> "42").query[Int]("v") == Right(42),
        Headers("v" -> "42").header[Int]("v") == Right(42),
        QueryParams("v" -> "9999999999").query[Int]("v") == Left(
          QueryParamError.Malformed("v", "9999999999", "Cannot parse '9999999999' as Int")
        ),
        Headers("v" -> "9999999999").header[Int]("v") == Left(
          HeaderError.Malformed("v", "9999999999", "Cannot parse '9999999999' as Int")
        ),
        QueryParams("v" -> "9223372036854775807").query[Long]("v") == Right(Long.MaxValue),
        Headers("v" -> "9223372036854775807").header[Long]("v") == Right(Long.MaxValue),
        QueryParams("v" -> "9223372036854775808").query[Long]("v") == Left(
          QueryParamError.Malformed("v", "9223372036854775808", "Cannot parse '9223372036854775808' as Long")
        ),
        Headers("v" -> "9223372036854775808").header[Long]("v") == Left(
          HeaderError.Malformed("v", "9223372036854775808", "Cannot parse '9223372036854775808' as Long")
        ),
        QueryParams("v" -> "123e4567-e89b-12d3-a456-426614174000").query[UUID]("v") == Right(uuid),
        Headers("v" -> "123e4567-e89b-12d3-a456-426614174000").header[UUID]("v") == Right(uuid),
        QueryParams("v" -> "not-a-uuid").query[UUID]("v") == Left(
          QueryParamError.Malformed("v", "not-a-uuid", "Cannot parse 'not-a-uuid' as UUID")
        ),
        Headers("v" -> "not-a-uuid").header[UUID]("v") == Left(
          HeaderError.Malformed("v", "not-a-uuid", "Cannot parse 'not-a-uuid' as UUID")
        ),
        QueryParams("v" -> "z").query[Char]("v") == Right('z'),
        Headers("v" -> "z").header[Char]("v") == Right('z'),
        QueryParams("v" -> "ab").query[Char]("v") == Left(
          QueryParamError.Malformed("v", "ab", "Expected single character but got 'ab'")
        ),
        Headers("v" -> "ab").header[Char]("v") == Left(
          HeaderError.Malformed("v", "ab", "Expected single character but got 'ab'")
        )
      )
    },
    test("top-level header codecs use the lowercase value key") {
      val intCodec = Schema[Int].derive(DefaultHeaderFormat)

      assertTrue(
        intCodec.encodeToHeaders(42).rawGet("value").contains("42"),
        intCodec.decode(Headers("value" -> "42")) == Right(42),
        intCodec.decode(Headers("VALUE" -> "42")) == Right(42)
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
