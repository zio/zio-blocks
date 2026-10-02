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

import zio.blocks.schema.Schema
import zio.http.{Headers, QueryParams}
import zio.test._

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

  def spec: Spec[TestEnvironment, Any] = suite("ParamCodecParity")(
    test("single-value boolean parsing agrees across query and header paths") {
      assertTrue(
        QueryParams("flag" -> "true").query[Boolean]("flag") == Right(true),
        Headers("flag" -> "true").header[Boolean]("flag") == Right(true),
        QueryParams("flag" -> "false").query[Boolean]("flag") == Right(false),
        Headers("flag" -> "false").header[Boolean]("flag") == Right(false),
        QueryParams("flag" -> "yes").query[Boolean]("flag").isLeft,
        Headers("flag" -> "yes").header[Boolean]("flag").isLeft,
        QueryParams("flag" -> "1").query[Boolean]("flag").isLeft,
        Headers("flag" -> "1").header[Boolean]("flag").isLeft
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
    test("record decode accumulates all bad fields with path context") {
      val queryCodec  = Schema[Point].derive(DefaultQueryFormat)
      val headerCodec = Schema[Point].derive(DefaultHeaderFormat)
      val badParams   = QueryParams("x" -> "bad", "y" -> "worse")
      val badHeaders  = Headers("X" -> "bad", "Y" -> "worse")

      assertTrue(
        queryCodec.decode(badParams).swap.exists(err => err.errors.size == 2),
        queryCodec.decode(badParams).swap.exists(err => err.message.contains("'x'") && err.message.contains("'y'")),
        queryCodec.decode(badParams).swap.exists(_.message.contains("at:")),
        headerCodec.decode(badHeaders).swap.exists(err => err.errors.size == 2),
        headerCodec.decode(badHeaders).swap.exists(err => err.message.contains("'X'") && err.message.contains("'Y'")),
        headerCodec.decode(badHeaders).swap.exists(_.message.contains("at:"))
      )
    },
    test("wrapper failures carry the field name in the error path") {
      val queryCodec  = Schema[EvenBox].derive(DefaultQueryFormat)
      val headerCodec = Schema[EvenBox].derive(DefaultHeaderFormat)

      assertTrue(
        queryCodec.decode(QueryParams("even" -> "3")).swap.exists(err => err.message.contains("not even")),
        queryCodec
          .decode(QueryParams("even" -> "3"))
          .swap
          .exists(err => err.message.contains("even") && err.message.contains("at:")),
        queryCodec.decode(QueryParams("even" -> "4")) == Right(EvenBox(Even(4))),
        headerCodec.decode(Headers("EVEN" -> "3")).swap.exists(err => err.message.contains("not even")),
        headerCodec.decode(Headers("EVEN" -> "4")) == Right(EvenBox(Even(4)))
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
