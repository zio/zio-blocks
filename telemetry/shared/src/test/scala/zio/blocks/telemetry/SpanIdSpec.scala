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

package zio.blocks.telemetry

import zio.test._

object SpanIdSpec extends ZIOSpecDefault {

  def spec = suite("SpanId")(
    suite("invalid")(
      test("is zero") {
        assertTrue(SpanId.invalid.value == 0L)
      },
      test("isValid returns false") {
        assertTrue(!SpanId.invalid.isValid)
      }
    ),
    suite("random")(
      test("generates valid SpanIds (non-zero)") {
        val s = SpanId.random
        assertTrue(s.isValid)
      },
      test("never returns zero span") {
        val spans = (1 to 100).map(_ => SpanId.random)
        assertTrue(spans.forall(_.isValid))
      },
      test("generates different spans") {
        val s1 = SpanId.random
        val s2 = SpanId.random
        assertTrue(s1 != s2)
      }
    ),
    suite("fromHex")(
      test("parses valid 16-char hex string") {
        val hex    = "0102030405060708"
        val result = SpanId.fromHex(hex)
        assertTrue(result.isDefined)
      },
      test("returns None for non-hex characters") {
        val hex    = "010203040506070g"
        val result = SpanId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too short)") {
        val hex    = "010203040506070"
        val result = SpanId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too long)") {
        val hex    = "01020304050607081"
        val result = SpanId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("handles uppercase hex") {
        val hex    = "0102030405060708".toUpperCase
        val result = SpanId.fromHex(hex)
        assertTrue(result.isDefined)
      },
      test("handles all-zero hex (invalid)") {
        val hex    = "0000000000000000"
        val result = SpanId.fromHex(hex)
        assertTrue(result.isDefined && !result.get.isValid)
      }
    ),
    suite("toHex")(
      test("produces 16-char lowercase hex") {
        val s   = SpanId.invalid
        val hex = s.toHex
        assertTrue(hex.length == 16 && hex == "0000000000000000")
      },
      test("is zero-padded") {
        val s   = SpanId(value = 1L)
        val hex = s.toHex
        assertTrue(hex.length == 16 && hex == "0000000000000001")
      },
      test("roundtrips with fromHex") {
        val s      = SpanId.random
        val hex    = s.toHex
        val parsed = SpanId.fromHex(hex)
        assertTrue(parsed.contains(s))
      }
    ),
    suite("isValid")(
      test("returns true for non-zero spans") {
        val s = SpanId(value = 1L)
        assertTrue(s.isValid)
      },
      test("returns false when zero") {
        val s = SpanId(value = 0L)
        assertTrue(!s.isValid)
      }
    ),
    suite("toByteArray")(
      test("produces 8 bytes") {
        val s     = SpanId.random
        val bytes = s.toByteArray
        assertTrue(bytes.length == 8)
      },
      test("is big-endian") {
        val s     = SpanId(value = 0x0102030405060708L)
        val bytes = s.toByteArray
        assertTrue(
          bytes(0) == 0x01.toByte &&
            bytes(1) == 0x02.toByte &&
            bytes(7) == 0x08.toByte
        )
      }
    ),
    suite("equality")(
      test("equal spans compare equal") {
        val s1 = SpanId(value = 123L)
        val s2 = SpanId(value = 123L)
        assertTrue(s1 == s2)
      },
      test("different spans compare not equal") {
        val s1 = SpanId(value = 123L)
        val s2 = SpanId(value = 124L)
        assertTrue(s1 != s2)
      }
    )
  )
}
