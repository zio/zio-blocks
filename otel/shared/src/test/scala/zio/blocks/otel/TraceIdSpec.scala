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
        assertTrue(!TraceId.isValid(0L, 0L))
      },
      test("isValid returns false") {
        assertTrue(!TraceId.isValid(0L, 0L))
      }
    ),
    suite("random")(
      test("generates valid TraceIds (non-zero)") {
        val (hi, lo) = TraceId.random()
        assertTrue(TraceId.isValid(hi, lo))
      },
      test("never returns all-zero trace") {
        val traces = (1 to 100).map(_ => TraceId.random())
        assertTrue(traces.forall { case (hi, lo) => TraceId.isValid(hi, lo) })
      },
      test("generates different traces") {
        val t1 = TraceId.random()
        val t2 = TraceId.random()
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
        assertTrue(result.isDefined && !TraceId.isValid(result.get._1, result.get._2))
      }
    ),
    suite("toHex")(
      test("produces 32-char lowercase hex") {
        val hex = TraceId.toHex(0L, 0L)
        assertTrue(hex.length == 32 && hex == "00000000000000000000000000000000")
      },
      test("is zero-padded") {
        val hex = TraceId.toHex(1L, 0L)
        assertTrue(hex.length == 32 && hex.startsWith("0000000000000001"))
      },
      test("roundtrips with fromHex") {
        val (hi, lo) = TraceId.random()
        val hex      = TraceId.toHex(hi, lo)
        val parsed   = TraceId.fromHex(hex)
        assertTrue(parsed.contains((hi, lo)))
      }
    ),
    suite("isValid")(
      test("returns true for non-zero traces") {
        assertTrue(TraceId.isValid(1L, 0L))
      },
      test("returns true when lo is non-zero") {
        assertTrue(TraceId.isValid(0L, 1L))
      },
      test("returns false when both are zero") {
        assertTrue(!TraceId.isValid(0L, 0L))
      }
    ),
    suite("toByteArray")(
      test("produces 16 bytes") {
        val (hi, lo) = TraceId.random()
        val bytes    = TraceId.toByteArray(hi, lo)
        assertTrue(bytes.length == 16)
      },
      test("is big-endian") {
        val bytes = TraceId.toByteArray(0x0102030405060708L, 0x090a0b0c0d0e0f10L)
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
        assertTrue((123L, 456L) == (123L, 456L))
      },
      test("different traces compare not equal") {
        assertTrue((123L, 456L) != (123L, 457L))
      }
    )
  )
}
