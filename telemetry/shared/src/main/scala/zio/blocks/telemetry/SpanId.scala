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
 * The 64-bit id of a span; zero is the invalid id. As an `AnyVal`, it is stored
 * as a plain `Long` in fields such as `SpanContext#spanId`.
 *
 * {{{
 * val id = SpanId(0x00f067aa0ba902b7L)
 *
 * id.toHex // 00f067aa0ba902b7
 * SpanId.fromHex("00f067aa0ba902b7").contains(id) // true
 * SpanId.invalid.isValid // false
 * }}}
 *
 * @param value
 *   the span ID as a 64-bit long
 */
final class SpanId(val value: Long) extends AnyVal {

  /** Returns `true` when the id is not zero. */
  def isValid: Boolean = value != 0L

  /** Returns the id as 16 lowercase hex digits, with leading zeros. */
  def toHex: String = String.format("%016x", value)

  /** Returns the id as 8 bytes, most significant first. */
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

  override def toString: String = s"SpanId(${toHex})"
}

/** Creates span ids. */
object SpanId {

  /** Wraps `value` as a span id; `0` gives the invalid id. */
  def apply(value: Long): SpanId = new SpanId(value)

  /** The invalid span id, zero, which stands for no span. */
  val invalid: SpanId = new SpanId(0L)

  /** Returns a random non-zero span id, drawn from `scala.util.Random`. */
  def random: SpanId = {
    var value = Random.nextLong()
    while (value == 0L) {
      value = Random.nextLong()
    }
    new SpanId(value)
  }

  /**
   * Parses 16 hex digits, in either case, into a span id, or returns `None`
   * when `s` is not 16 characters long or not hexadecimal. Sixteen zeros give
   * `Some(SpanId.invalid)`, so check `isValid`.
   *
   * A known bug: a leading `+` is accepted in place of the first digit, so
   * `"+0f067aa0ba902b7"` gives `Some(SpanId(00f067aa0ba902b7))`.
   */
  def fromHex(s: String): Option[SpanId] =
    if (s.length != 16) None
    else {
      try {
        val value = java.lang.Long.parseUnsignedLong(s, 16)
        Some(new SpanId(value))
      } catch {
        case _: NumberFormatException => None
      }
    }
}
