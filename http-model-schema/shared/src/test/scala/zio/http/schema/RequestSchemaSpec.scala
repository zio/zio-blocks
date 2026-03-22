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
import zio.http._
import zio.test._

object RequestSchemaSpec extends ZIOSpecDefault {

  private def requestWithQuery(pairs: (String, String)*): Request = {
    val qp = QueryParams(pairs: _*)
    Request.get(URL.fromPath(Path.root).copy(queryParams = qp))
  }

  private def requestWithHeaders(pairs: (String, String)*): Request =
    Request.get(URL.fromPath(Path.root)).copy(headers = Headers(pairs: _*))

  def spec: Spec[TestEnvironment, Any] = suite("RequestSchemaOps")(
    suite("query delegation")(
      test("query[Int] delegates to QueryParamsSchemaOps") {
        val request = requestWithQuery("page" -> "42")
        assertTrue(request.query[Int]("page") == Right(42))
      },
      test("queryAll[String] delegates to QueryParamsSchemaOps") {
        val request = requestWithQuery("tag" -> "a", "tag" -> "b")
        assertTrue(request.queryAll[String]("tag") == Right(Chunk("a", "b")))
      },
      test("queryOrElse[Int] returns default when missing") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.queryOrElse[Int]("page", 1) == 1)
      }
    ),
    suite("header delegation")(
      test("header[String] delegates to HeadersSchemaOps") {
        val request = requestWithHeaders("x-custom" -> "hello")
        val ops     = new RequestSchemaOps(request)
        assertTrue(ops.header[String]("x-custom") == Right("hello"))
      },
      test("headerAll[String] returns all header values") {
        val request = requestWithHeaders("x-tag" -> "a", "x-tag" -> "b")
        assertTrue(request.headerAll[String]("x-tag") == Right(Chunk("a", "b")))
      },
      test("headerOrElse[Int] returns default when missing") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.headerOrElse[Int]("x-page", 1) == 1)
      }
    )
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
