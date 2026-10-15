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

object HexSpec extends ZIOSpecDefault {

  def spec = suite("Hex")(
    suite("TraceId.toHex")(
      test("renders zeros") {
        assertTrue(TraceId.toHex(0L, 0L) == "00000000000000000000000000000000")
      },
      test("renders a known value") {
        assertTrue(TraceId.toHex(0x0123456789abcdefL, 0xfedcba9876543210L) == "0123456789abcdeffedcba9876543210")
      },
      test("renders all-ones") {
        assertTrue(TraceId.toHex(-1L, -1L) == "ffffffffffffffffffffffffffffffff")
      },
      test("renders extreme values") {
        assertTrue(TraceId.toHex(Long.MinValue, Long.MaxValue) == "80000000000000007fffffffffffffff")
      },
      test("round-trips through fromHex") {
        val pairs = List(
          (0L, 1L),
          (1L, 0L),
          (-1L, -1L),
          (Long.MinValue, Long.MaxValue),
          (0x0123456789abcdefL, 0xfedcba9876543210L)
        )
        assertTrue(pairs.forall { case (hi, lo) =>
          TraceId.fromHex(TraceId.toHex(hi, lo)).contains((hi, lo))
        })
      }
    ),
    suite("SpanId.toHex")(
      test("renders zeros") {
        assertTrue(SpanId(0L).toHex == "0000000000000000")
      },
      test("renders a known value") {
        assertTrue(SpanId(0x0123456789abcdefL).toHex == "0123456789abcdef")
      },
      test("renders all-ones") {
        assertTrue(SpanId(-1L).toHex == "ffffffffffffffff")
      },
      test("renders extreme values") {
        assertTrue(SpanId(Long.MinValue).toHex == "8000000000000000") &&
        assertTrue(SpanId(Long.MaxValue).toHex == "7fffffffffffffff")
      },
      test("round-trips through fromHex") {
        val values = List(0L, 1L, -1L, Long.MinValue, Long.MaxValue, 0x0123456789abcdefL)
        assertTrue(values.forall(v => SpanId.fromHex(SpanId(v).toHex).contains(SpanId(v))))
      }
    ),
    suite("TraceFlags.toHex")(
      test("renders none and sampled") {
        assertTrue(TraceFlags.none.toHex == "00") &&
        assertTrue(TraceFlags.sampled.toHex == "01")
      },
      test("renders full byte") {
        assertTrue(TraceFlags(0xff.toByte).toHex == "ff") &&
        assertTrue(TraceFlags(0x10.toByte).toHex == "10")
      }
    )
  )
}
