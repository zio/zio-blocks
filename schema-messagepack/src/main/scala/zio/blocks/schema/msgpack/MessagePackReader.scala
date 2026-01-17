package zio.blocks.schema.msgpack

import java.nio.charset.StandardCharsets.UTF_8

final class MessagePackReader private (
  private[this] var buf: Array[Byte],
  private[this] var pos: Int,
  private[this] var limit: Int
) {
  import MessagePackReader._

  def reset(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    buf = bytes
    pos = offset
    limit = offset + length
  }

  def hasRemaining: Boolean = pos < limit

  def remaining: Int = limit - pos

  def position: Int = pos

  def peekType: Byte = if (pos < limit) buf(pos) else decodeError("Unexpected end of input")

  def readNil(): Unit = {
    val b = readByte()
    if (b != NIL) decodeError("Expected nil (0xc0), got: " + (b & 0xff))
  }

  def tryReadNil(): Boolean =
    if (pos < limit && buf(pos) == NIL) { pos += 1; true }
    else false

  def readBoolean(): Boolean = {
    val b = readByte()
    if (b == TRUE) true
    else if (b == FALSE) false
    else decodeError("Expected boolean, got: " + (b & 0xff))
  }

  def readByteValue(): Byte = {
    val b = peekByte()
    if ((b & 0x80) == 0) { pos += 1; b }
    else if ((b & 0xe0) == 0xe0) { pos += 1; b }
    else if (b == INT8) { pos += 1; readByte() }
    else if (b == UINT8) { pos += 1; (readByte() & 0xff).toByte }
    else decodeError("Expected byte value, got: " + (b & 0xff))
  }

  def readShortValue(): Short = {
    val b = peekByte()
    if ((b & 0x80) == 0) { pos += 1; b.toShort }
    else if ((b & 0xe0) == 0xe0) { pos += 1; b.toShort }
    else if (b == INT8) { pos += 1; readByte().toShort }
    else if (b == INT16) { pos += 1; readShortBE() }
    else if (b == UINT8) { pos += 1; (readByte() & 0xff).toShort }
    else if (b == UINT16) {
      pos += 1
      val v = readShortBE() & 0xffff
      if (v > Short.MaxValue) decodeError("Value " + v + " exceeds Short range")
      v.toShort
    } else decodeError("Expected short value, got: " + (b & 0xff))
  }

  def readIntValue(): Int = {
    val b = peekByte()
    if ((b & 0x80) == 0) { pos += 1; b.toInt }
    else if ((b & 0xe0) == 0xe0) { pos += 1; b.toInt }
    else if (b == INT8) { pos += 1; readByte().toInt }
    else if (b == INT16) { pos += 1; readShortBE().toInt }
    else if (b == INT32) { pos += 1; readIntBE() }
    else if (b == UINT8) { pos += 1; readByte() & 0xff }
    else if (b == UINT16) { pos += 1; readShortBE() & 0xffff }
    else if (b == UINT32) {
      pos += 1
      val v = readIntBE().toLong & 0xffffffffL
      if (v > Int.MaxValue) decodeError("Value " + v + " exceeds Int range")
      v.toInt
    } else decodeError("Expected int value, got: " + (b & 0xff))
  }

  def readLongValue(): Long = {
    val b = peekByte()
    if ((b & 0x80) == 0) { pos += 1; b.toLong }
    else if ((b & 0xe0) == 0xe0) { pos += 1; b.toLong }
    else if (b == INT8) { pos += 1; readByte().toLong }
    else if (b == INT16) { pos += 1; readShortBE().toLong }
    else if (b == INT32) { pos += 1; readIntBE().toLong }
    else if (b == INT64) { pos += 1; readLongBE() }
    else if (b == UINT8) { pos += 1; (readByte() & 0xff).toLong }
    else if (b == UINT16) { pos += 1; (readShortBE() & 0xffff).toLong }
    else if (b == UINT32) { pos += 1; readIntBE().toLong & 0xffffffffL }
    else if (b == UINT64) {
      pos += 1
      val v = readLongBE()
      if (v < 0) decodeError("Value exceeds Long range (unsigned 64-bit)")
      v
    } else decodeError("Expected long value, got: " + (b & 0xff))
  }

  def readFloatValue(): Float = {
    val b = peekByte()
    if (b == FLOAT32) {
      pos += 1
      java.lang.Float.intBitsToFloat(readIntBE())
    } else if (b == FLOAT64) {
      pos += 1
      java.lang.Double.longBitsToDouble(readLongBE()).toFloat
    } else readLongValue().toFloat
  }

  def readDoubleValue(): Double = {
    val b = peekByte()
    if (b == FLOAT64) {
      pos += 1
      java.lang.Double.longBitsToDouble(readLongBE())
    } else if (b == FLOAT32) {
      pos += 1
      java.lang.Float.intBitsToFloat(readIntBE()).toDouble
    } else readLongValue().toDouble
  }

  def readString(): String = {
    val len = readStringHeader()
    if ((len | (remaining - len)) < 0) decodeError("String length " + len + " exceeds remaining bytes " + remaining)
    val str = new String(buf, pos, len, UTF_8)
    pos += len
    str
  }

  def readFieldNameEquals(expected: Array[Byte]): Boolean = {
    val savedPos = pos
    try {
      val b   = readByte() & 0xff
      val len =
        if ((b & 0xe0) == 0xa0) b & 0x1f
        else if (b == 0xd9) readByte() & 0xff
        else if (b == 0xda) readShortBE() & 0xffff
        else if (b == 0xdb) readIntBE()
        else -1

      if (len != expected.length || len == -1) {
        pos = savedPos
        false
      } else {
        var i = 0
        while (i < len) {
          if (buf(pos + i) != expected(i)) {
            pos = savedPos
            return false
          }
          i += 1
        }
        pos += len
        true
      }
    } catch {
      case _: MessagePackCodecError =>
        pos = savedPos
        false
    }
  }

  def readChar(): Char = {
    val len = readStringHeader()
    if (len == 1) {
      val b = buf(pos)
      if ((b & 0x80) == 0) { pos += 1; b.toChar }
      else decodeError("Multi-byte UTF-8 char in single-byte string")
    } else if (len == 2) {
      val b1 = buf(pos) & 0xff
      val b2 = buf(pos + 1) & 0xff
      if ((b1 & 0xe0) == 0xc0) {
        pos += 2
        (((b1 & 0x1f) << 6) | (b2 & 0x3f)).toChar
      } else decodeError("Invalid 2-byte UTF-8 sequence")
    } else if (len == 3) {
      val b1 = buf(pos) & 0xff
      val b2 = buf(pos + 1) & 0xff
      val b3 = buf(pos + 2) & 0xff
      if ((b1 & 0xf0) == 0xe0) {
        pos += 3
        (((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f)).toChar
      } else decodeError("Invalid 3-byte UTF-8 sequence")
    } else decodeError("Expected single char, got string of length " + len + " bytes")
  }

  def readStringHeader(): Int = {
    val b = readByte() & 0xff
    if ((b & 0xe0) == 0xa0) b & 0x1f
    else if (b == 0xd9) readByte() & 0xff
    else if (b == 0xda) readShortBE() & 0xffff
    else if (b == 0xdb) readIntBE()
    else decodeError("Expected string header, got: " + b)
  }

  def readBinary(): Array[Byte] = {
    val len = readBinaryHeader()
    if ((len | (remaining - len)) < 0) decodeError("Binary length " + len + " exceeds remaining bytes " + remaining)
    val bytes = new Array[Byte](len)
    System.arraycopy(buf, pos, bytes, 0, len)
    pos += len
    bytes
  }

  def readBinaryHeader(): Int = {
    val b = readByte() & 0xff
    if (b == 0xc4) readByte() & 0xff
    else if (b == 0xc5) readShortBE() & 0xffff
    else if (b == 0xc6) readIntBE()
    else decodeError("Expected binary header, got: " + b)
  }

  def readArrayHeader(): Int = {
    val b = readByte() & 0xff
    if ((b & 0xf0) == 0x90) b & 0x0f
    else if (b == 0xdc) readShortBE() & 0xffff
    else if (b == 0xdd) readIntBE()
    else decodeError("Expected array header, got: " + b)
  }

  def readMapHeader(): Int = {
    val b = readByte() & 0xff
    if ((b & 0xf0) == 0x80) b & 0x0f
    else if (b == 0xde) readShortBE() & 0xffff
    else if (b == 0xdf) readIntBE()
    else decodeError("Expected map header, got: " + b)
  }

  def readBigInt(): BigInt = {
    val bytes = readBinary()
    BigInt(bytes)
  }

  def readBigDecimal(): BigDecimal = {
    val len = readMapHeader()
    if (len != 3) decodeError("Expected BigDecimal map of 3, got: " + len)
    var unscaled: BigInt = null
    var precision        = 0
    var scale            = 0
    var i                = 0
    while (i < 3) {
      if (readFieldNameEquals(UnscaledName)) unscaled = readBigInt()
      else if (readFieldNameEquals(PrecisionName)) precision = readIntValue()
      else if (readFieldNameEquals(ScaleName)) scale = readIntValue()
      else decodeError("Unknown BigDecimal field")
      i += 1
    }
    if (unscaled eq null) decodeError("Missing 'unscaled' field in BigDecimal")
    BigDecimal(new java.math.BigDecimal(unscaled.bigInteger, scale, new java.math.MathContext(precision)))
  }

  def skipValue(): Unit = {
    val b = readByte() & 0xff
    if ((b & 0x80) == 0 || (b & 0xe0) == 0xe0) return
    if ((b & 0xe0) == 0xa0) { pos += (b & 0x1f); return }
    if ((b & 0xf0) == 0x90) { skipArrayElements(b & 0x0f); return }
    if ((b & 0xf0) == 0x80) { skipMapElements(b & 0x0f); return }
    b match {
      case 0xc0 | 0xc2 | 0xc3 => // nil, false, true
      case 0xc4               => pos += (readByte() & 0xff)
      case 0xc5               => pos += (readShortBE() & 0xffff)
      case 0xc6               => pos += readIntBE()
      case 0xca               => pos += 4
      case 0xcb               => pos += 8
      case 0xcc               => pos += 1
      case 0xcd               => pos += 2
      case 0xce               => pos += 4
      case 0xcf               => pos += 8
      case 0xd0               => pos += 1
      case 0xd1               => pos += 2
      case 0xd2               => pos += 4
      case 0xd3               => pos += 8
      case 0xd9               => pos += (readByte() & 0xff)
      case 0xda               => pos += (readShortBE() & 0xffff)
      case 0xdb               => pos += readIntBE()
      case 0xdc               => skipArrayElements(readShortBE() & 0xffff)
      case 0xdd               => skipArrayElements(readIntBE())
      case 0xde               => skipMapElements(readShortBE() & 0xffff)
      case 0xdf               => skipMapElements(readIntBE())
      case _                  => decodeError("Unknown MessagePack type: " + b)
    }
  }

  private[this] def skipArrayElements(count: Int): Unit = {
    var i = 0
    while (i < count) { skipValue(); i += 1 }
  }

  private[this] def skipMapElements(count: Int): Unit = {
    var i = 0
    while (i < count) { skipValue(); skipValue(); i += 1 }
  }

  @inline
  private def peekByte(): Byte =
    if (pos < limit) buf(pos) else decodeError("Unexpected end of input")

  @inline
  private def readByte(): Byte =
    if (pos < limit) { val b = buf(pos); pos += 1; b }
    else decodeError("Unexpected end of input")

  private def readShortBE(): Short = {
    if (pos + 2 > limit) decodeError("Unexpected end of input")
    val result = ((buf(pos) & 0xff) << 8) | (buf(pos + 1) & 0xff)
    pos += 2
    result.toShort
  }

  private def readIntBE(): Int = {
    if (pos + 4 > limit) decodeError("Unexpected end of input")
    val result = ((buf(pos) & 0xff) << 24) |
      ((buf(pos + 1) & 0xff) << 16) |
      ((buf(pos + 2) & 0xff) << 8) |
      (buf(pos + 3) & 0xff)
    pos += 4
    result
  }

  private def readLongBE(): Long = {
    if (pos + 8 > limit) decodeError("Unexpected end of input")
    val result = ((buf(pos) & 0xffL) << 56) |
      ((buf(pos + 1) & 0xffL) << 48) |
      ((buf(pos + 2) & 0xffL) << 40) |
      ((buf(pos + 3) & 0xffL) << 32) |
      ((buf(pos + 4) & 0xffL) << 24) |
      ((buf(pos + 5) & 0xffL) << 16) |
      ((buf(pos + 6) & 0xffL) << 8) |
      (buf(pos + 7) & 0xffL)
    pos += 8
    result
  }

  def decodeError(msg: String): Nothing =
    throw new MessagePackCodecError(Nil, msg)
}

object MessagePackReader {
  final val NIL: Byte     = 0xc0.toByte
  final val FALSE: Byte   = 0xc2.toByte
  final val TRUE: Byte    = 0xc3.toByte
  final val FLOAT32: Byte = 0xca.toByte
  final val FLOAT64: Byte = 0xcb.toByte
  final val UINT8: Byte   = 0xcc.toByte
  final val UINT16: Byte  = 0xcd.toByte
  final val UINT32: Byte  = 0xce.toByte
  final val UINT64: Byte  = 0xcf.toByte
  final val INT8: Byte    = 0xd0.toByte
  final val INT16: Byte   = 0xd1.toByte
  final val INT32: Byte   = 0xd2.toByte
  final val INT64: Byte   = 0xd3.toByte

  private[msgpack] val SecondsName: Array[Byte]   = Array('s', 'e', 'c', 'o', 'n', 'd', 's')
  private[msgpack] val NanosName: Array[Byte]     = Array('n', 'a', 'n', 'o', 's')
  private[msgpack] val MonthName: Array[Byte]     = Array('m', 'o', 'n', 't', 'h')
  private[msgpack] val DayName: Array[Byte]       = Array('d', 'a', 'y')
  private[msgpack] val YearName: Array[Byte]      = Array('y', 'e', 'a', 'r')
  private[msgpack] val YearsName: Array[Byte]     = Array('y', 'e', 'a', 'r', 's')
  private[msgpack] val MonthsName: Array[Byte]    = Array('m', 'o', 'n', 't', 'h', 's')
  private[msgpack] val DaysName: Array[Byte]      = Array('d', 'a', 'y', 's')
  private[msgpack] val UnscaledName: Array[Byte]  = Array('u', 'n', 's', 'c', 'a', 'l', 'e', 'd')
  private[msgpack] val PrecisionName: Array[Byte] = Array('p', 'r', 'e', 'c', 'i', 's', 'i', 'o', 'n')
  private[msgpack] val ScaleName: Array[Byte]     = Array('s', 'c', 'a', 'l', 'e')

  private val pool: ThreadLocal[MessagePackReader] = new ThreadLocal[MessagePackReader] {
    override def initialValue(): MessagePackReader = new MessagePackReader(null, 0, 0)
  }

  def apply(): MessagePackReader = pool.get()

  def fresh(): MessagePackReader = new MessagePackReader(null, 0, 0)
}
