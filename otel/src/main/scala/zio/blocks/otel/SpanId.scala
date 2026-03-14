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
 * Represents an OpenTelemetry SpanId - a 64-bit identifier for a span.
 *
 * @param value
 *   the span ID as a 64-bit long
 */
final case class SpanId(value: Long) {

  /**
   * Checks if this span ID is valid (not zero).
   */
  def isValid: Boolean = value != 0L

  /**
   * Converts this span ID to a 16-character lowercase hexadecimal string.
   */
  def toHex: String = String.format("%016x", value)

  /**
   * Converts this span ID to an 8-byte big-endian array.
   */
  def toByteArray: Array[Byte] = {
    val bytes = new Array[Byte](8)
    bytes(0) = ((value >> 56) & 0xff).toByte
    bytes(1) = ((value >> 48) & 0xff).toByte
    bytes(2) = ((value >> 40) & 0xff).toByte
    bytes(3) = ((value >> 32) & 0xff).toByte
    bytes(4) = ((value >> 24) & 0xff).toByte
    bytes(5) = ((value >> 16) & 0xff).toByte
    bytes(6) = ((value >> 8) & 0xff).toByte
    bytes(7) = (value & 0xff).toByte
    bytes
  }
}

object SpanId {

  /**
   * The invalid/zero span ID (represents "no span").
   */
  val invalid: SpanId = SpanId(value = 0L)

  /**
   * Generates a random valid span ID.
   *
   * If the generated value is zero, regenerates until a valid one is obtained.
   */
  def random: SpanId = {
    var value = ThreadLocalRandom.current().nextLong()
    while (value == 0L) {
      value = ThreadLocalRandom.current().nextLong()
    }
    SpanId(value = value)
  }

  /**
   * Parses a 16-character hexadecimal string into a SpanId.
   *
   * Returns None if the string is not exactly 16 characters or contains non-hex
   * characters.
   */
  def fromHex(s: String): Option[SpanId] =
    if (s.length != 16) None
    else {
      try {
        val value = java.lang.Long.parseUnsignedLong(s, 16)
        Some(SpanId(value = value))
      } catch {
        case _: NumberFormatException => None
      }
    }

  /**
   * Unscoped instance - SpanId is a safe data type that can escape scopes.
   */
}
