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

package zio.blocks.otel

import zio.test._

object TraceIdSpec extends ZIOSpecDefault {

  def spec = suite("TraceId")(
    suite("invalid")(
      test("is all zeros") {
        assertTrue(TraceId.invalid.hi == 0L && TraceId.invalid.lo == 0L)
      },
      test("isValid returns false") {
        assertTrue(!TraceId.invalid.isValid)
      }
    ),
    suite("random")(
      test("generates valid TraceIds (non-zero)") {
        val t = TraceId.random
        assertTrue(t.isValid)
      },
      test("never returns all-zero trace") {
        val traces = (1 to 100).map(_ => TraceId.random)
        assertTrue(traces.forall(_.isValid))
      },
      test("generates different traces") {
        val t1 = TraceId.random
        val t2 = TraceId.random
        assertTrue(t1 != t2)
      }
    ),
    suite("fromHex")(
      test("parses valid 32-char hex string") {
        val hex    = "0102030405060708090a0b0c0d0e0f10"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isDefined)
      },
      test("returns None for non-hex characters") {
        val hex    = "0102030405060708090a0b0c0d0e0f1g"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too short)") {
        val hex    = "0102030405060708090a0b0c0d0e0f"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too long)") {
        val hex    = "0102030405060708090a0b0c0d0e0f1011"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isEmpty)
      },
      test("handles uppercase hex") {
        val hex    = "0102030405060708090A0B0C0D0E0F10"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isDefined)
      },
      test("handles all-zero hex (invalid)") {
        val hex    = "00000000000000000000000000000000"
        val result = TraceId.fromHex(hex)
        assertTrue(result.isDefined && !result.get.isValid)
      }
    ),
    suite("toHex")(
      test("produces 32-char lowercase hex") {
        val t   = TraceId.invalid
        val hex = t.toHex
        assertTrue(hex.length == 32 && hex == "00000000000000000000000000000000")
      },
      test("is zero-padded") {
        val t   = TraceId(hi = 1L, lo = 0L)
        val hex = t.toHex
        assertTrue(hex.length == 32 && hex.startsWith("0000000000000001"))
      },
      test("roundtrips with fromHex") {
        val t      = TraceId.random
        val hex    = t.toHex
        val parsed = TraceId.fromHex(hex)
        assertTrue(parsed.contains(t))
      }
    ),
    suite("isValid")(
      test("returns true for non-zero traces") {
        val t = TraceId(hi = 1L, lo = 0L)
        assertTrue(t.isValid)
      },
      test("returns true when lo is non-zero") {
        val t = TraceId(hi = 0L, lo = 1L)
        assertTrue(t.isValid)
      },
      test("returns false when both are zero") {
        val t = TraceId(hi = 0L, lo = 0L)
        assertTrue(!t.isValid)
      }
    ),
    suite("toByteArray")(
      test("produces 16 bytes") {
        val t     = TraceId.random
        val bytes = t.toByteArray
        assertTrue(bytes.length == 16)
      },
      test("is big-endian") {
        val t     = TraceId(hi = 0x0102030405060708L, lo = 0x090a0b0c0d0e0f10L)
        val bytes = t.toByteArray
        assertTrue(
          bytes(0) == 0x01.toByte &&
            bytes(1) == 0x02.toByte &&
            bytes(8) == 0x09.toByte &&
            bytes(15) == 0x10.toByte
        )
      }
    ),
    suite("equality")(
      test("equal traces compare equal") {
        val t1 = TraceId(hi = 123L, lo = 456L)
        val t2 = TraceId(hi = 123L, lo = 456L)
        assertTrue(t1 == t2)
      },
      test("different traces compare not equal") {
        val t1 = TraceId(hi = 123L, lo = 456L)
        val t2 = TraceId(hi = 123L, lo = 457L)
        assertTrue(t1 != t2)
      }
    )
  )
}
