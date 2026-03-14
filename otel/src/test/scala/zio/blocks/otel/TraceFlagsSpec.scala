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

object TraceFlagsSpec extends ZIOSpecDefault {

  def spec = suite("TraceFlags")(
    suite("none")(
      test("is zero byte") {
        assertTrue(TraceFlags.none.byte == 0x00.toByte)
      },
      test("isSampled returns false") {
        assertTrue(!TraceFlags.none.isSampled)
      }
    ),
    suite("sampled")(
      test("is 0x01 byte") {
        assertTrue(TraceFlags.sampled.byte == 0x01.toByte)
      },
      test("isSampled returns true") {
        assertTrue(TraceFlags.sampled.isSampled)
      }
    ),
    suite("isSampled")(
      test("returns true for 0x01") {
        val flags = TraceFlags(byte = 0x01.toByte)
        assertTrue(flags.isSampled)
      },
      test("returns false for 0x00") {
        val flags = TraceFlags(byte = 0x00.toByte)
        assertTrue(!flags.isSampled)
      },
      test("respects bit 0 only") {
        val flags = TraceFlags(byte = 0x02.toByte)
        assertTrue(!flags.isSampled)
      }
    ),
    suite("withSampled")(
      test("sets sampled flag to true") {
        val flags = TraceFlags.none.withSampled(true)
        assertTrue(flags.isSampled)
      },
      test("sets sampled flag to false") {
        val flags = TraceFlags.sampled.withSampled(false)
        assertTrue(!flags.isSampled)
      },
      test("preserves other bits") {
        val flags = TraceFlags(byte = 0xfe.toByte).withSampled(true)
        assertTrue(flags.byte == 0xff.toByte)
      },
      test("clears sampled bit when false") {
        val flags = TraceFlags(byte = 0x01.toByte).withSampled(false)
        assertTrue(flags.byte == 0x00.toByte)
      }
    ),
    suite("toHex")(
      test("produces 2-char lowercase hex") {
        val flags = TraceFlags.none
        assertTrue(flags.toHex == "00")
      },
      test("produces correct hex for sampled") {
        val flags = TraceFlags.sampled
        assertTrue(flags.toHex == "01")
      },
      test("is zero-padded") {
        val flags = TraceFlags(byte = 0x0f.toByte)
        assertTrue(flags.toHex == "0f")
      }
    ),
    suite("fromHex")(
      test("parses valid 2-char hex") {
        val result = TraceFlags.fromHex("01")
        assertTrue(result.isDefined && result.get.isSampled)
      },
      test("returns None for non-hex") {
        val result = TraceFlags.fromHex("0g")
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too short)") {
        val result = TraceFlags.fromHex("0")
        assertTrue(result.isEmpty)
      },
      test("returns None for wrong length (too long)") {
        val result = TraceFlags.fromHex("001")
        assertTrue(result.isEmpty)
      },
      test("handles uppercase") {
        val result = TraceFlags.fromHex("FF")
        assertTrue(result.isDefined)
      },
      test("roundtrips with toHex") {
        val flags  = TraceFlags(byte = 0xab.toByte)
        val hex    = flags.toHex
        val parsed = TraceFlags.fromHex(hex)
        assertTrue(parsed.contains(flags))
      }
    ),
    suite("toByte")(
      test("returns underlying byte") {
        val flags = TraceFlags(byte = 0x42.toByte)
        assertTrue(flags.toByte == 0x42.toByte)
      }
    ),
    suite("equality")(
      test("equal flags compare equal") {
        val f1 = TraceFlags(byte = 0x01.toByte)
        val f2 = TraceFlags(byte = 0x01.toByte)
        assertTrue(f1 == f2)
      },
      test("different flags compare not equal") {
        val f1 = TraceFlags(byte = 0x01.toByte)
        val f2 = TraceFlags(byte = 0x00.toByte)
        assertTrue(f1 != f2)
      }
    )
  )
}
