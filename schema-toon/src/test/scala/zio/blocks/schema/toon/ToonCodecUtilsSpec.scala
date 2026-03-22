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

package zio.blocks.schema.toon

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object ToonCodecUtilsSpec extends SchemaBaseSpec {
  def spec: Spec[Any, Any] = suite("ToonCodecUtilsSpec")(
    suite("unescapeQuoted")(
      test("returns unquoted string unchanged") {
        val result = ToonCodecUtils.unescapeQuoted("hello")
        assertTrue(result == "hello")
      },
      test("returns simple quoted string without escapes") {
        val result = ToonCodecUtils.unescapeQuoted("\"hello\"")
        assertTrue(result == "hello")
      },
      test("unescapes backslash-quote") {
        val result = ToonCodecUtils.unescapeQuoted("\"hello\\\"world\"")
        assertTrue(result == "hello\"world")
      },
      test("unescapes double backslash") {
        val result = ToonCodecUtils.unescapeQuoted("\"path\\\\to\\\\file\"")
        assertTrue(result == "path\\to\\file")
      },
      test("unescapes newline") {
        val result = ToonCodecUtils.unescapeQuoted("\"line1\\nline2\"")
        assertTrue(result == "line1\nline2")
      },
      test("unescapes carriage return") {
        val result = ToonCodecUtils.unescapeQuoted("\"line1\\rline2\"")
        assertTrue(result == "line1\rline2")
      },
      test("unescapes tab") {
        val result = ToonCodecUtils.unescapeQuoted("\"col1\\tcol2\"")
        assertTrue(result == "col1\tcol2")
      },
      test("preserves unknown escape sequences") {
        val result = ToonCodecUtils.unescapeQuoted("\"hello\\xworld\"")
        assertTrue(result == "hello\\xworld")
      },
      test("handles trailing backslash at end of string") {
        val result = ToonCodecUtils.unescapeQuoted("\"hello\\\"")
        assertTrue(result == "hello\\")
      }
    ),
    suite("createReaderForValue")(
      test("creates reader that can read value") {
        val reader = ToonCodecUtils.createReaderForValue("key:value")
        assertTrue(reader ne null)
      }
    )
  )
}
