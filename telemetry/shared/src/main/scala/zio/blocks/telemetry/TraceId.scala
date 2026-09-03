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
 * Utility object for OpenTelemetry TraceId operations.
 *
 * TraceId is a 128-bit identifier represented as two Long values (hi and lo).
 * Instead of wrapping them in a case class, the hi/lo values are inlined
 * directly into SpanContext for zero heap allocation.
 */
object TraceId {

  /**
   * Checks if a trace ID (hi, lo) pair is valid (not all zeros).
   */
  def isValid(hi: Long, lo: Long): Boolean = hi != 0L || lo != 0L

  /**
   * Converts a trace ID to a 32-character lowercase hexadecimal string.
   *
   * Hand-rolled nibble appends (see `Hex`) instead of `String.format`: this
   * runs per span on the inject/export hot path.
   */
  def toHex(hi: Long, lo: Long): String = {
    val sb = new StringBuilder(32)
    Hex.appendLong16(sb, hi)
    Hex.appendLong16(sb, lo)
    sb.toString
  }

  /**
   * Converts a trace ID to a 16-byte big-endian array.
   */
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
   * Generates a random valid trace ID as a (hi, lo) pair.
   *
   * If both hi and lo are zero, regenerates until a valid one is obtained.
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
   * Parses a 32-character hexadecimal string into a (hi, lo) pair.
   *
   * Returns None if the string is not exactly 32 characters or contains non-hex
   * characters.
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

/**
 * Hand-rolled lowercase hex formatting for hot paths.
 *
 * `String.format` parses a format string, boxes its arguments, and allocates
 * formatters on every call; these nibble appends do straight-line work with no
 * allocation beyond the caller's `StringBuilder`. Used by trace/span ID
 * rendering on the per-span hot path (inject + export) and by JSON string
 * escaping.
 */
private[telemetry] object Hex {
  private final val Digits = "0123456789abcdef"

  /** Appends `v` as 16 lowercase hex digits (big-endian nibble order). */
  def appendLong16(sb: StringBuilder, v: Long): Unit = {
    var i = 0
    while (i < 16) {
      sb.append(Digits.charAt(((v >>> ((15 - i) * 4)) & 0xfL).toInt))
      i += 1
    }
  }

  /** Appends the low 8 bits of `b` as 2 lowercase hex digits. */
  def appendByte2(sb: StringBuilder, b: Int): Unit = {
    sb.append(Digits.charAt((b >>> 4) & 0xf))
    sb.append(Digits.charAt(b & 0xf))
  }

  /** Appends `c` as 4 lowercase hex digits (for `\uXXXX` escapes). */
  def appendChar4(sb: StringBuilder, c: Int): Unit = {
    sb.append(Digits.charAt((c >>> 12) & 0xf))
    sb.append(Digits.charAt((c >>> 8) & 0xf))
    sb.append(Digits.charAt((c >>> 4) & 0xf))
    sb.append(Digits.charAt(c & 0xf))
  }
}
