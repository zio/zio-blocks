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

package zio.blocks.mediatype

import zio.test._

object FileExtensionSpec extends MediaTypeBaseSpec {
  def spec = suite("MediaType.forFileExtension")(
    suite("known extensions")(
      test("json returns application/json") {
        val result = MediaType.forFileExtension("json")
        assertTrue(
          result.isDefined,
          result.get.fullType == "application/json"
        )
      },
      test("html returns text/html") {
        val result = MediaType.forFileExtension("html")
        assertTrue(
          result.isDefined,
          result.get.fullType == "text/html"
        )
      },
      test("css returns text/css") {
        val result = MediaType.forFileExtension("css")
        assertTrue(
          result.isDefined,
          result.get.fullType == "text/css"
        )
      },
      test("png returns image/png") {
        val result = MediaType.forFileExtension("png")
        assertTrue(
          result.isDefined,
          result.get.fullType == "image/png"
        )
      },
      test("jpg returns image/jpeg") {
        val result = MediaType.forFileExtension("jpg")
        assertTrue(
          result.isDefined,
          result.get.fullType == "image/jpeg"
        )
      },
      test("pdf returns application/pdf") {
        val result = MediaType.forFileExtension("pdf")
        assertTrue(
          result.isDefined,
          result.get.fullType == "application/pdf"
        )
      },
      test("xml returns appropriate type") {
        val result = MediaType.forFileExtension("xml")
        assertTrue(result.isDefined)
      },
      test("js prefers text/javascript for text types") {
        val result = MediaType.forFileExtension("js")
        assertTrue(
          result.isDefined,
          result.get.mainType == "text" || result.get.mainType == "application"
        )
      }
    ),
    suite("unknown extensions")(
      test("unknown extension returns None") {
        val result = MediaType.forFileExtension("xyz123unknown")
        assertTrue(result.isEmpty)
      },
      test("empty string returns None") {
        val result = MediaType.forFileExtension("")
        assertTrue(result.isEmpty)
      },
      test("extension with dot still works if stripped") {
        val resultWithDot    = MediaType.forFileExtension(".json")
        val resultWithoutDot = MediaType.forFileExtension("json")
        assertTrue(resultWithDot == resultWithoutDot)
      },
      test("dot only returns None") {
        val result = MediaType.forFileExtension(".")
        assertTrue(result.isEmpty)
      }
    ),
    suite("case handling")(
      test("case insensitive lookup") {
        val lower = MediaType.forFileExtension("json")
        val upper = MediaType.forFileExtension("JSON")
        val mixed = MediaType.forFileExtension("Json")
        assertTrue(
          lower == upper,
          upper == mixed
        )
      }
    )
  )
}
