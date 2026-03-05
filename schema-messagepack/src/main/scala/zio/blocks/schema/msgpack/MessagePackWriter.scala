package zio.blocks.schema.msgpack

import java.nio.charset.StandardCharsets.UTF_8

final class MessagePackWriter private[msgpack] (
  private[this] var buf: Array[Byte] = new Array[Byte](32768),
  private[this] var count: Int = -1
) {
  import MessagePackWriter._

  private[msgpack] def isInUse: Boolean = count >= 0

  private[msgpack] def release(): Unit = count = -1

  def reset(): Unit = count = 0

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)

  def writeNil(): Unit = writeRawByte(NIL)

  def writeBoolean(x: Boolean): Unit = writeRawByte {
    if (x) TRUE
    else FALSE
  }

  def writeByte(x: Byte): Unit =
    if (x >= 0) writePositiveFixintOrUint8(x.toInt)
    else writeNegativeFixintOrInt8(x.toInt)

  def writeRawByte(b: Byte): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b
    count += 1
  }

  def writeShort(x: Short): Unit =
    if (x >= 0) writePositiveInt(x.toInt)
    else writeNegativeInt(x.toInt)

  def writeInt(x: Int): Unit =
    if (x >= 0) writePositiveInt(x)
    else writeNegativeInt(x)

  def writeLong(x: Long): Unit =
    if (x >= 0) writePositiveLong(x)
    else writeNegativeLong(x)

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

  def writeString(x: String): Unit = {
    val bytes = x.getBytes(UTF_8)
    writeStringHeader(bytes.length)
    writeRaw(bytes)
  }

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

  def writeBinary(x: Array[Byte]): Unit = {
    writeBinaryHeader(x.length)
    writeRaw(x)
  }

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

  def writeBigInt(x: BigInt): Unit = writeBinary(x.toByteArray)

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

object MessagePackWriter {
  val NIL: Byte            = 0xc0.toByte
  val FALSE: Byte          = 0xc2.toByte
  val TRUE: Byte           = 0xc3.toByte
  val BIN8: Byte           = 0xc4.toByte
  val BIN16: Byte          = 0xc5.toByte
  val BIN32: Byte          = 0xc6.toByte
  val FLOAT32: Byte        = 0xca.toByte
  val FLOAT64: Byte        = 0xcb.toByte
  val UINT8: Byte          = 0xcc.toByte
  val UINT16: Byte         = 0xcd.toByte
  val UINT32: Byte         = 0xce.toByte
  val UINT64: Byte         = 0xcf.toByte
  val INT8: Byte           = 0xd0.toByte
  val INT16: Byte          = 0xd1.toByte
  val INT32: Byte          = 0xd2.toByte
  val INT64: Byte          = 0xd3.toByte
  val STR8: Byte           = 0xd9.toByte
  val STR16: Byte          = 0xda.toByte
  val STR32: Byte          = 0xdb.toByte
  val ARRAY16: Byte        = 0xdc.toByte
  val ARRAY32: Byte        = 0xdd.toByte
  val MAP16: Byte          = 0xde.toByte
  val MAP32: Byte          = 0xdf.toByte
  val FIXSTR_PREFIX: Int   = 0xa0
  val FIXARRAY_PREFIX: Int = 0x90
  val FIXMAP_PREFIX: Int   = 0x80

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
