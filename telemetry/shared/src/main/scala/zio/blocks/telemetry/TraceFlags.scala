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
 * The trace flags of a span, one byte in which bit 0 is the sampled flag. As an
 * `AnyVal`, it is stored as a plain `Byte` in fields such as
 * `SpanContext#traceFlags`.
 *
 * {{{
 * TraceFlags.sampled.toHex // 01
 * TraceFlags.sampled.withSampled(false).isSampled // false
 * TraceFlags.fromHex("01").exists(_.isSampled) // true
 * }}}
 *
 * @param byte
 *   the underlying byte value
 */
final class TraceFlags(val byte: Byte) extends AnyVal {

  /** Returns `true` when the sampled flag, bit 0, is set. */
  def isSampled: Boolean = (byte & 0x01) != 0

  /**
   * Returns these flags with the sampled bit set or cleared; the other bits
   * stay as they are.
   */
  def withSampled(sampled: Boolean): TraceFlags = {
    val newByte =
      if (sampled) (byte | 0x01).toByte
      else (byte & 0xfe).toByte
    new TraceFlags(newByte)
  }

  /** Returns the byte as 2 lowercase hex digits. */
  def toHex: String = String.format("%02x", byte & 0xff)

  /** Returns the byte, the same as `byte`. */
  def toByte: Byte = byte

  override def toString: String = s"TraceFlags(${toHex})"
}

/** Creates trace flags. */
object TraceFlags {

  /** Wraps `byte` as trace flags. */
  def apply(byte: Byte): TraceFlags = new TraceFlags(byte)

  /** The flags `00`: no bit set, so not sampled. */
  val none: TraceFlags = new TraceFlags(0x00.toByte)

  /** The flags `01`: only the sampled bit set. */
  val sampled: TraceFlags = new TraceFlags(0x01.toByte)

  /**
   * Parses 2 hex digits, in either case, into trace flags, or returns `None`
   * when `s` is not 2 characters long or not hexadecimal.
   *
   * A known bug: a sign is accepted in place of the first digit, so `"-1"`
   * gives the flags `ff` and `"+1"` gives `01`.
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
