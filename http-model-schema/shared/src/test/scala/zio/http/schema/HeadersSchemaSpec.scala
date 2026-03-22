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
import zio.http.Headers
import zio.test._

object HeadersSchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("HeadersSchemaOps")(
    suite("header[T]")(
      test("decodes Int from valid header value") {
        val headers = Headers("x-page" -> "5")
        assertTrue(headers.header[Int]("x-page") == Right(5))
      },
      test("decodes String from header value") {
        val headers = Headers("x-request-id" -> "abc-123")
        assertTrue(headers.header[String]("x-request-id") == Right("abc-123"))
      },
      test("decodes Boolean from header value") {
        val headers = Headers("x-debug" -> "true")
        assertTrue(headers.header[Boolean]("x-debug") == Right(true))
      },
      test("returns Missing for absent header") {
        assertTrue(Headers.empty.header[Int]("x-page") == Left(HeaderError.Missing("x-page")))
      },
      test("returns Malformed for unparseable header value") {
        val headers = Headers("x-page" -> "abc")
        val result  = headers.header[Int]("x-page")
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[HeaderError.Malformed]))
      },
      test("matches header names case-insensitively") {
        val headers = Headers("X-Page" -> "5")
        assertTrue(headers.header[Int]("x-page") == Right(5))
      }
    ),
    suite("headerAll[T]")(
      test("returns all values for multi-valued header") {
        val headers = Headers("x-tag" -> "a", "x-tag" -> "b")
        assertTrue(headers.headerAll[String]("x-tag") == Right(Chunk("a", "b")))
      },
      test("returns Missing for absent header") {
        assertTrue(Headers.empty.headerAll[String]("x-missing") == Left(HeaderError.Missing("x-missing")))
      },
      test("returns Malformed if any value is invalid") {
        val headers = Headers("x-count" -> "1", "x-count" -> "bad")
        val result  = headers.headerAll[Int]("x-count")
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[HeaderError.Malformed]))
      }
    ),
    suite("headerOrElse[T]")(
      test("returns default when header is missing") {
        assertTrue(Headers.empty.headerOrElse[Int]("x-page", 1) == 1)
      },
      test("returns parsed value when header is present") {
        val headers = Headers("x-page" -> "10")
        assertTrue(headers.headerOrElse[Int]("x-page", 1) == 10)
      },
      test("returns default when header value is malformed") {
        val headers = Headers("x-page" -> "abc")
        assertTrue(headers.headerOrElse[Int]("x-page", 1) == 1)
      }
    )
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
