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

import java.util.concurrent.ThreadLocalRandom

/**
 * Represents an OpenTelemetry TraceId - a 128-bit identifier for a trace.
 *
 * @param hi
 *   the high 64 bits of the trace ID
 * @param lo
 *   the low 64 bits of the trace ID
 */
final case class TraceId(hi: Long, lo: Long) {

  /**
   * Checks if this trace ID is valid (not all zeros).
   */
  def isValid: Boolean = hi != 0L || lo != 0L

  /**
   * Converts this trace ID to a 32-character lowercase hexadecimal string.
   */
  def toHex: String = {
    val hiHex = String.format("%016x", hi)
    val loHex = String.format("%016x", lo)
    hiHex + loHex
  }

  /**
   * Converts this trace ID to a 16-byte big-endian array.
   */
  def toByteArray: Array[Byte] = {
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
}

object TraceId {

  /**
   * The invalid/zero trace ID (represents "no trace").
   */
  val invalid: TraceId = TraceId(hi = 0L, lo = 0L)

  /**
   * Generates a random valid trace ID.
   *
   * If both hi and lo are zero, regenerates until a valid one is obtained.
   */
  def random: TraceId = {
    var hi = ThreadLocalRandom.current().nextLong()
    var lo = ThreadLocalRandom.current().nextLong()
    while (hi == 0L && lo == 0L) {
      hi = ThreadLocalRandom.current().nextLong()
      lo = ThreadLocalRandom.current().nextLong()
    }
    TraceId(hi = hi, lo = lo)
  }

  /**
   * Parses a 32-character hexadecimal string into a TraceId.
   *
   * Returns None if the string is not exactly 32 characters or contains non-hex
   * characters.
   */
  def fromHex(s: String): Option[TraceId] =
    if (s.length != 32) None
    else {
      try {
        val hi = java.lang.Long.parseUnsignedLong(s.substring(0, 16), 16)
        val lo = java.lang.Long.parseUnsignedLong(s.substring(16, 32), 16)
        Some(TraceId(hi = hi, lo = lo))
      } catch {
        case _: NumberFormatException => None
      }
    }

  /**
   * Unscoped instance - TraceId is a safe data type that can escape scopes.
   */
}
