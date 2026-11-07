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

import scala.util.Random

/**
 * Functions on 128-bit trace ids. The module passes a trace id around as two
 * `Long` halves, `hi` (the first 16 hex digits) and `lo`, rather than as an
 * object, and [[SpanContext]] stores it the same way. An id of all zeros is
 * invalid.
 *
 * {{{
 * val parsed = TraceId.fromHex("4bf92f3577b34da6a3ce929d0e0e4736")
 *
 * parsed.map { case (hi, lo) => TraceId.toHex(hi, lo) } // Some(4bf92f3577b34da6a3ce929d0e0e4736)
 * TraceId.isValid(0L, 0L) // false
 * }}}
 */
object TraceId {

  /** Returns `true` unless both halves are zero. */
  def isValid(hi: Long, lo: Long): Boolean = hi != 0L || lo != 0L

  /** Returns the id as 32 lowercase hex digits, with leading zeros. */
  def toHex(hi: Long, lo: Long): String = {
    val hiHex = String.format("%016x", hi)
    val loHex = String.format("%016x", lo)
    hiHex + loHex
  }

  /** Returns the id as 16 bytes, most significant first, `hi` before `lo`. */
  def toByteArray(hi: Long, lo: Long): Array[Byte] = {
    val bytes = new Array[Byte](16)
    bytes(0) = ((hi >> 56) & 0xff).toByte
    bytes(1) = ((hi >> 48) & 0xff).toByte
    bytes(2) = ((hi >> 40) & 0xff).toByte
    bytes(3) = ((hi >> 32) & 0xff).toByte
    bytes(4) = ((hi >> 24) & 0xff).toByte
    bytes(5) = ((hi >> 16) & 0xff).toByte
    bytes(6) = ((hi >> 8) & 0xff).toByte
    bytes(7) = (hi & 0xff).toByte
    bytes(8) = ((lo >> 56) & 0xff).toByte
    bytes(9) = ((lo >> 48) & 0xff).toByte
    bytes(10) = ((lo >> 40) & 0xff).toByte
    bytes(11) = ((lo >> 32) & 0xff).toByte
    bytes(12) = ((lo >> 24) & 0xff).toByte
    bytes(13) = ((lo >> 16) & 0xff).toByte
    bytes(14) = ((lo >> 8) & 0xff).toByte
    bytes(15) = (lo & 0xff).toByte
    bytes
  }

  /**
   * Returns a random valid trace id as `(hi, lo)`, drawn from
   * `scala.util.Random`.
   */
  def random(): (Long, Long) = {
    var hi = Random.nextLong()
    var lo = Random.nextLong()
    while (hi == 0L && lo == 0L) {
      hi = Random.nextLong()
      lo = Random.nextLong()
    }
    (hi, lo)
  }

  /**
   * Parses 32 hex digits, in either case, into `(hi, lo)`, or returns `None`
   * when `s` is not 32 characters long or not hexadecimal. Thirty-two zeros
   * give `Some((0, 0))`, so check `isValid`.
   *
   * A known bug: a `+` is accepted in place of the first digit of either half,
   * so `"+bf92f3577b34da6a3ce929d0e0e4736"` parses as
   * `0bf92f3577b34da6a3ce929d0e0e4736`.
   */
  def fromHex(s: String): Option[(Long, Long)] =
    if (s.length != 32) None
    else {
      try {
        val hi = java.lang.Long.parseUnsignedLong(s.substring(0, 16), 16)
        val lo = java.lang.Long.parseUnsignedLong(s.substring(16, 32), 16)
        Some((hi, lo))
      } catch {
        case _: NumberFormatException => None
      }
    }
}
