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

package zio.blocks.schema.msgpack

import java.nio.charset.StandardCharsets.UTF_8

/**
 * Writes MessagePack values into a byte array that grows as needed; a
 * [[MessagePackCodec]] gets one in `encodeValue`, as the constructor is private
 * to the module. The integer and header methods pick the smallest format that
 * holds the value, except `writeLong` (see there). The writer does not check
 * the structure: after `writeArrayHeader(n)` or `writeMapHeader(n)`, the codec
 * must write `n` elements or `n` key-value pairs.
 */
final class MessagePackWriter private[msgpack] (
  private[this] var buf: Array[Byte] = new Array[Byte](32768),
  private[this] var count: Int = -1
) {
  import MessagePackWriter._

  private[msgpack] def isInUse: Boolean = count >= 0

  private[msgpack] def release(): Unit = count = -1

  /**
   * Discards everything written so far. `encode` calls it before `encodeValue`.
   */
  def reset(): Unit = count = 0

  /** Returns a copy of the bytes written since the last `reset`. */
  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)

  /** Writes nil (`0xc0`). */
  def writeNil(): Unit = writeRawByte(NIL)

  /** Writes `true` as `0xc3` or `false` as `0xc2`. */
  def writeBoolean(x: Boolean): Unit = writeRawByte {
    if (x) TRUE
    else FALSE
  }

  /** Writes `x` as a fixint, or as `int8` below -32. */
  def writeByte(x: Byte): Unit =
    if (x >= 0) writePositiveFixintOrUint8(x.toInt)
    else writeNegativeFixintOrInt8(x.toInt)

  /** Appends the byte `b` as it is, with no format byte. */
  def writeRawByte(b: Byte): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b
    count += 1
  }

  /**
   * Writes `x` in the smallest of fixint, `uint8`, `uint16`, `int8` and `int16`
   * that holds it.
   */
  def writeShort(x: Short): Unit =
    if (x >= 0) writePositiveInt(x.toInt)
    else writeNegativeInt(x.toInt)

  /**
   * Writes `x` in the smallest integer format that holds it: a fixint from -32
   * to 127, `uint8`, `uint16` or `uint32` above that, and `int8`, `int16` or
   * `int32` below.
   */
  def writeInt(x: Int): Unit =
    if (x >= 0) writePositiveInt(x)
    else writeNegativeInt(x)

  /**
   * Writes `x` as `writeInt` does when it fits in an `Int`, and otherwise as 9
   * bytes of `uint64` (positive) or `int64` (negative), even when `uint32`
   * would hold it.
   */
  def writeLong(x: Long): Unit =
    if (x >= 0) writePositiveLong(x)
    else writeNegativeLong(x)

  /** Writes `x` as `float32`: the format byte and 4 bytes, big-endian. */
  def writeFloat(x: Float): Unit = {
    ensureCapacity(5)
    buf(count) = FLOAT32
    val bits = java.lang.Float.floatToIntBits(x)
    buf(count + 1) = (bits >> 24).toByte
    buf(count + 2) = (bits >> 16).toByte
    buf(count + 3) = (bits >> 8).toByte
    buf(count + 4) = bits.toByte
    count += 5
  }

  /** Writes `x` as `float64`: the format byte and 8 bytes, big-endian. */
  def writeDouble(x: Double): Unit = {
    ensureCapacity(9)
    buf(count) = FLOAT64
    val bits = java.lang.Double.doubleToLongBits(x)
    buf(count + 1) = (bits >> 56).toByte
    buf(count + 2) = (bits >> 48).toByte
    buf(count + 3) = (bits >> 40).toByte
    buf(count + 4) = (bits >> 32).toByte
    buf(count + 5) = (bits >> 24).toByte
    buf(count + 6) = (bits >> 16).toByte
    buf(count + 7) = (bits >> 8).toByte
    buf(count + 8) = bits.toByte
    count += 9
  }

  /**
   * Writes `x` as a fixstr of its one to three UTF-8 bytes. A lone surrogate
   * gets the three bytes of its code unit, which are not valid UTF-8.
   */
  def writeChar(x: Char): Unit =
    if (x < 0x80) {
      ensureCapacity(2)
      buf(count) = (FIXSTR_PREFIX | 1).toByte
      buf(count + 1) = x.toByte
      count += 2
    } else if (x < 0x800) {
      ensureCapacity(3)
      buf(count) = (FIXSTR_PREFIX | 2).toByte
      buf(count + 1) = (0xc0 | (x >> 6)).toByte
      buf(count + 2) = (0x80 | (x & 0x3f)).toByte
      count += 3
    } else {
      ensureCapacity(4)
      buf(count) = (FIXSTR_PREFIX | 3).toByte
      buf(count + 1) = (0xe0 | (x >> 12)).toByte
      buf(count + 2) = (0x80 | ((x >> 6) & 0x3f)).toByte
      buf(count + 3) = (0x80 | (x & 0x3f)).toByte
      count += 4
    }

  /**
   * Writes `x` as its UTF-8 bytes behind the smallest string header: fixstr up
   * to 31 bytes, then `str8`, `str16` or `str32`.
   */
  def writeString(x: String): Unit = {
    val bytes = x.getBytes(UTF_8)
    writeStringHeader(bytes.length)
    writeRaw(bytes)
  }

  /**
   * Writes the header of a string of `len` bytes: fixstr up to 31, then `str8`,
   * `str16` or `str32`. Follow it with exactly `len` bytes of UTF-8, for
   * example with `writeRaw`.
   */
  def writeStringHeader(len: Int): Unit =
    if (len <= 31) writeRawByte((FIXSTR_PREFIX | len).toByte)
    else if (len <= 0xff) {
      ensureCapacity(2)
      buf(count) = STR8
      buf(count + 1) = len.toByte
      count += 2
    } else if (len <= 0xffff) {
      ensureCapacity(3)
      buf(count) = STR16
      buf(count + 1) = (len >> 8).toByte
      buf(count + 2) = len.toByte
      count += 3
    } else {
      ensureCapacity(5)
      buf(count) = STR32
      buf(count + 1) = (len >> 24).toByte
      buf(count + 2) = (len >> 16).toByte
      buf(count + 3) = (len >> 8).toByte
      buf(count + 4) = len.toByte
      count += 5
    }

  /**
   * Writes `x` as a `bin` value: the smallest of `bin8`, `bin16` and `bin32`,
   * then the bytes.
   */
  def writeBinary(x: Array[Byte]): Unit = {
    writeBinaryHeader(x.length)
    writeRaw(x)
  }

  /**
   * Writes the header of a `bin` value of `len` bytes: `bin8` up to 255, then
   * `bin16` or `bin32`. Follow it with exactly `len` bytes.
   */
  def writeBinaryHeader(len: Int): Unit =
    if (len <= 0xff) {
      ensureCapacity(2)
      buf(count) = BIN8
      buf(count + 1) = len.toByte
      count += 2
    } else if (len <= 0xffff) {
      ensureCapacity(3)
      buf(count) = BIN16
      buf(count + 1) = (len >> 8).toByte
      buf(count + 2) = len.toByte
      count += 3
    } else {
      ensureCapacity(5)
      buf(count) = BIN32
      buf(count + 1) = (len >> 24).toByte
      buf(count + 2) = (len >> 16).toByte
      buf(count + 3) = (len >> 8).toByte
      buf(count + 4) = len.toByte
      count += 5
    }

  /**
   * Writes the header of an array of `len` elements: fixarray up to 15, then
   * `array16` or `array32`. Follow it with exactly `len` values.
   */
  def writeArrayHeader(len: Int): Unit =
    if (len <= 15) writeRawByte((FIXARRAY_PREFIX | len).toByte)
    else if (len <= 0xffff) {
      ensureCapacity(3)
      buf(count) = ARRAY16
      buf(count + 1) = (len >> 8).toByte
      buf(count + 2) = len.toByte
      count += 3
    } else {
      ensureCapacity(5)
      buf(count) = ARRAY32
      buf(count + 1) = (len >> 24).toByte
      buf(count + 2) = (len >> 16).toByte
      buf(count + 3) = (len >> 8).toByte
      buf(count + 4) = len.toByte
      count += 5
    }

  /**
   * Writes the header of a map of `len` entries: fixmap up to 15, then `map16`
   * or `map32`. Follow it with exactly `len` keys, each followed by its value.
   */
  def writeMapHeader(len: Int): Unit =
    if (len <= 15) writeRawByte((FIXMAP_PREFIX | len).toByte)
    else if (len <= 0xffff) {
      ensureCapacity(3)
      buf(count) = MAP16
      buf(count + 1) = (len >> 8).toByte
      buf(count + 2) = len.toByte
      count += 3
    } else {
      ensureCapacity(5)
      buf(count) = MAP32
      buf(count + 1) = (len >> 24).toByte
      buf(count + 2) = (len >> 16).toByte
      buf(count + 3) = (len >> 8).toByte
      buf(count + 4) = len.toByte
      count += 5
    }

  /**
   * Writes `x` as a `bin` value holding its big-endian two's-complement bytes
   * (`BigInt#toByteArray`).
   */
  def writeBigInt(x: BigInt): Unit = writeBinary(x.toByteArray)

  /**
   * Writes `x` as a map of three entries, in this order: `unscaled` (as
   * `writeBigInt` writes it), `precision` and `scale` (ints).
   */
  def writeBigDecimal(x: BigDecimal): Unit = {
    val underlying = x.underlying()
    val unscaled   = underlying.unscaledValue()
    val precision  = underlying.precision()
    val scale      = underlying.scale()
    writeMapHeader(3)
    writeRaw(UnscaledBytes)
    writeBigInt(BigInt(unscaled))
    writeRaw(PrecisionBytes)
    writeInt(precision)
    writeRaw(ScaleBytes)
    writeInt(scale)
  }

  private[this] def writePositiveFixintOrUint8(x: Int): Unit = {
    if (x > 0x7f) writeRawByte(UINT8)
    writeRawByte(x.toByte)
  }

  private[this] def writeNegativeFixintOrInt8(x: Int): Unit = {
    if (x < -32) writeRawByte(INT8)
    writeRawByte(x.toByte)
  }

  private[this] def writePositiveInt(x: Int): Unit =
    if (x <= 0x7f) writeRawByte(x.toByte)
    else if (x <= 0xff) {
      writeRawByte(UINT8)
      writeRawByte(x.toByte)
    } else if (x <= 0xffff) {
      writeRawByte(UINT16)
      writeShortBE(x)
    } else {
      writeRawByte(UINT32)
      writeIntBE(x)
    }

  private[this] def writeNegativeInt(x: Int): Unit =
    if (x >= -32) writeRawByte(x.toByte)
    else if (x >= Byte.MinValue) {
      writeRawByte(INT8)
      writeRawByte(x.toByte)
    } else if (x >= Short.MinValue) {
      writeRawByte(INT16)
      writeShortBE(x)
    } else {
      writeRawByte(INT32)
      writeIntBE(x)
    }

  private[this] def writePositiveLong(x: Long): Unit =
    if (x <= Int.MaxValue) writePositiveInt(x.toInt)
    else {
      writeRawByte(UINT64)
      writeLongBE(x)
    }

  private[this] def writeNegativeLong(x: Long): Unit =
    if (x >= Int.MinValue) writeNegativeInt(x.toInt)
    else {
      writeRawByte(INT64)
      writeLongBE(x)
    }

  private[this] def writeShortBE(x: Int): Unit = {
    ensureCapacity(2)
    buf(count) = (x >> 8).toByte
    buf(count + 1) = x.toByte
    count += 2
  }

  private[this] def writeIntBE(x: Int): Unit = {
    ensureCapacity(4)
    buf(count) = (x >> 24).toByte
    buf(count + 1) = (x >> 16).toByte
    buf(count + 2) = (x >> 8).toByte
    buf(count + 3) = x.toByte
    count += 4
  }

  private[this] def writeLongBE(x: Long): Unit = {
    ensureCapacity(8)
    buf(count) = (x >> 56).toByte
    buf(count + 1) = (x >> 48).toByte
    buf(count + 2) = (x >> 40).toByte
    buf(count + 3) = (x >> 32).toByte
    buf(count + 4) = (x >> 24).toByte
    buf(count + 5) = (x >> 16).toByte
    buf(count + 6) = (x >> 8).toByte
    buf(count + 7) = x.toByte
    count += 8
  }

  /** Appends `bytes` as they are, with no header. */
  def writeRaw(bytes: Array[Byte]): Unit = {
    val len = bytes.length
    ensureCapacity(len)
    System.arraycopy(bytes, 0, buf, count, len)
    count += len
  }

  private[this] def ensureCapacity(needed: Int): Unit = {
    val required = count + needed
    if (required > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, required))
  }
}

/** The format bytes and prefixes that the writer uses. */
object MessagePackWriter {

  /** The nil format byte, `0xc0`. */
  val NIL: Byte = 0xc0.toByte

  /** The `false` format byte, `0xc2`. */
  val FALSE: Byte = 0xc2.toByte

  /** The `true` format byte, `0xc3`. */
  val TRUE: Byte = 0xc3.toByte

  /** The `bin8` format byte, `0xc4`, followed by a 1-byte length. */
  val BIN8: Byte = 0xc4.toByte

  /** The `bin16` format byte, `0xc5`, followed by a 2-byte length. */
  val BIN16: Byte = 0xc5.toByte

  /** The `bin32` format byte, `0xc6`, followed by a 4-byte length. */
  val BIN32: Byte = 0xc6.toByte

  /** The `float32` format byte, `0xca`, followed by 4 bytes. */
  val FLOAT32: Byte = 0xca.toByte

  /** The `float64` format byte, `0xcb`, followed by 8 bytes. */
  val FLOAT64: Byte = 0xcb.toByte

  /** The `uint8` format byte, `0xcc`, followed by 1 byte. */
  val UINT8: Byte = 0xcc.toByte

  /** The `uint16` format byte, `0xcd`, followed by 2 bytes. */
  val UINT16: Byte = 0xcd.toByte

  /** The `uint32` format byte, `0xce`, followed by 4 bytes. */
  val UINT32: Byte = 0xce.toByte

  /** The `uint64` format byte, `0xcf`, followed by 8 bytes. */
  val UINT64: Byte = 0xcf.toByte

  /** The `int8` format byte, `0xd0`, followed by 1 byte. */
  val INT8: Byte = 0xd0.toByte

  /** The `int16` format byte, `0xd1`, followed by 2 bytes. */
  val INT16: Byte = 0xd1.toByte

  /** The `int32` format byte, `0xd2`, followed by 4 bytes. */
  val INT32: Byte = 0xd2.toByte

  /** The `int64` format byte, `0xd3`, followed by 8 bytes. */
  val INT64: Byte = 0xd3.toByte

  /** The `str8` format byte, `0xd9`, followed by a 1-byte length. */
  val STR8: Byte = 0xd9.toByte

  /** The `str16` format byte, `0xda`, followed by a 2-byte length. */
  val STR16: Byte = 0xda.toByte

  /** The `str32` format byte, `0xdb`, followed by a 4-byte length. */
  val STR32: Byte = 0xdb.toByte

  /** The `array16` format byte, `0xdc`, followed by a 2-byte element count. */
  val ARRAY16: Byte = 0xdc.toByte

  /** The `array32` format byte, `0xdd`, followed by a 4-byte element count. */
  val ARRAY32: Byte = 0xdd.toByte

  /** The `map16` format byte, `0xde`, followed by a 2-byte entry count. */
  val MAP16: Byte = 0xde.toByte

  /** The `map32` format byte, `0xdf`, followed by a 4-byte entry count. */
  val MAP32: Byte = 0xdf.toByte

  /**
   * The high bits of a fixstr byte, `0xa0`; combine it with a length up to 31
   * (`FIXSTR_PREFIX | len`).
   */
  val FIXSTR_PREFIX: Int = 0xa0

  /**
   * The high bits of a fixarray byte, `0x90`; combine it with a count up to 15
   * (`FIXARRAY_PREFIX | len`).
   */
  val FIXARRAY_PREFIX: Int = 0x90

  /**
   * The high bits of a fixmap byte, `0x80`; combine it with a count up to 15
   * (`FIXMAP_PREFIX | len`).
   */
  val FIXMAP_PREFIX: Int = 0x80

  private[msgpack] val SecondsBytes: Array[Byte]   = Array(0xa7.toByte, 's', 'e', 'c', 'o', 'n', 'd', 's')
  private[msgpack] val NanosBytes: Array[Byte]     = Array(0xa5.toByte, 'n', 'a', 'n', 'o', 's')
  private[msgpack] val MonthBytes: Array[Byte]     = Array(0xa5.toByte, 'm', 'o', 'n', 't', 'h')
  private[msgpack] val DayBytes: Array[Byte]       = Array(0xa3.toByte, 'd', 'a', 'y')
  private[msgpack] val YearBytes: Array[Byte]      = Array(0xa4.toByte, 'y', 'e', 'a', 'r')
  private[msgpack] val YearsBytes: Array[Byte]     = Array(0xa5.toByte, 'y', 'e', 'a', 'r', 's')
  private[msgpack] val MonthsBytes: Array[Byte]    = Array(0xa6.toByte, 'm', 'o', 'n', 't', 'h', 's')
  private[msgpack] val DaysBytes: Array[Byte]      = Array(0xa4.toByte, 'd', 'a', 'y', 's')
  private[msgpack] val UnscaledBytes: Array[Byte]  = Array(0xa8.toByte, 'u', 'n', 's', 'c', 'a', 'l', 'e', 'd')
  private[msgpack] val PrecisionBytes: Array[Byte] = Array(0xa9.toByte, 'p', 'r', 'e', 'c', 'i', 's', 'i', 'o', 'n')
  private[msgpack] val ScaleBytes: Array[Byte]     = Array(0xa5.toByte, 's', 'c', 'a', 'l', 'e')
}
