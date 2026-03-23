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
import zio.http.QueryParams
import zio.test._

object QueryParamsSchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("QueryParamsSchemaOps")(
    suite("query[T]")(
      test("decodes Int from valid string") {
        val qp = QueryParams("page" -> "42")
        assertTrue(qp.query[Int]("page") == Right(42))
      },
      test("decodes Long from valid string") {
        val qp = QueryParams("id" -> "123456789")
        assertTrue(qp.query[Long]("id") == Right(123456789L))
      },
      test("decodes String") {
        val qp = QueryParams("name" -> "alice")
        assertTrue(qp.query[String]("name") == Right("alice"))
      },
      test("decodes Boolean from valid string") {
        val qp = QueryParams("active" -> "true")
        assertTrue(qp.query[Boolean]("active") == Right(true))
      },
      test("decodes Double from valid string") {
        val qp = QueryParams("price" -> "9.99")
        assertTrue(qp.query[Double]("price") == Right(9.99))
      },
      test("decodes Float from valid string") {
        val qp = QueryParams("ratio" -> "0.5")
        assertTrue(qp.query[Float]("ratio") == Right(0.5f))
      },
      test("decodes UUID from valid string") {
        val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val qp   = QueryParams("id" -> "550e8400-e29b-41d4-a716-446655440000")
        assertTrue(qp.query[java.util.UUID]("id") == Right(uuid))
      },
      test("returns Missing for absent key") {
        assertTrue(QueryParams.empty.query[Int]("page") == Left(QueryParamError.Missing("page")))
      },
      test("returns Malformed for unparseable value") {
        val qp     = QueryParams("page" -> "abc")
        val result = qp.query[Int]("page")
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[QueryParamError.Malformed]))
      }
    ),
    suite("queryAll[T]")(
      test("decodes all String values for multi-valued key") {
        val qp = QueryParams("tag" -> "a", "tag" -> "b")
        assertTrue(qp.queryAll[String]("tag") == Right(Chunk("a", "b")))
      },
      test("decodes all Int values for multi-valued key") {
        val qp = QueryParams("id" -> "1", "id" -> "2", "id" -> "3")
        assertTrue(qp.queryAll[Int]("id") == Right(Chunk(1, 2, 3)))
      },
      test("returns Malformed if any value is invalid") {
        val qp     = QueryParams("id" -> "1", "id" -> "bad", "id" -> "3")
        val result = qp.queryAll[Int]("id")
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[QueryParamError.Malformed]))
      },
      test("returns Missing for absent key") {
        assertTrue(QueryParams.empty.queryAll[String]("missing") == Left(QueryParamError.Missing("missing")))
      }
    ),
    suite("queryOrElse[T]")(
      test("returns default when key is missing") {
        assertTrue(QueryParams.empty.queryOrElse[Int]("page", 1) == 1)
      },
      test("returns parsed value when key is present") {
        val qp = QueryParams("page" -> "42")
        assertTrue(qp.queryOrElse[Int]("page", 1) == 42)
      },
      test("returns default when value is malformed") {
        val qp = QueryParams("page" -> "abc")
        assertTrue(qp.queryOrElse[Int]("page", 1) == 1)
      }
    )
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
