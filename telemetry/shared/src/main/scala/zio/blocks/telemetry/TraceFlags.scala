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

/**
 * Represents OpenTelemetry trace flags - a single byte where bit 0 represents
 * the sampled flag.
 *
 * Implemented as an AnyVal for zero heap allocation when used as a field in a
 * case class.
 *
 * @param byte
 *   the underlying byte value
 */
final class TraceFlags(val byte: Byte) extends AnyVal {

  /**
   * Checks if the sampled flag (bit 0) is set.
   */
  def isSampled: Boolean = (byte & 0x01) != 0

  /**
   * Creates a new TraceFlags with the sampled flag set or cleared.
   */
  def withSampled(sampled: Boolean): TraceFlags = {
    val newByte =
      if (sampled) (byte | 0x01).toByte
      else (byte & 0xfe).toByte
    new TraceFlags(newByte)
  }

  /**
   * Converts this trace flags to a 2-character lowercase hexadecimal string.
   */
  def toHex: String = String.format("%02x", byte & 0xff)

  /**
   * Returns the underlying byte value.
   */
  def toByte: Byte = byte

  override def toString: String = s"TraceFlags(${toHex})"
}

object TraceFlags {

  def apply(byte: Byte): TraceFlags = new TraceFlags(byte)

  /**
   * TraceFlags with no flags set (0x00).
   */
  val none: TraceFlags = new TraceFlags(0x00.toByte)

  /**
   * TraceFlags with the sampled flag set (0x01).
   */
  val sampled: TraceFlags = new TraceFlags(0x01.toByte)

  /**
   * Parses a 2-character hexadecimal string into TraceFlags.
   *
   * Returns None if the string is not exactly 2 characters or contains non-hex
   * characters.
   */
  def fromHex(s: String): Option[TraceFlags] =
    if (s.length != 2) None
    else {
      try {
        val value = java.lang.Integer.parseInt(s, 16) & 0xff
        Some(new TraceFlags(value.toByte))
      } catch {
        case _: NumberFormatException => None
      }
    }
}
