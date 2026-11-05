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

package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import org.apache.thrift.transport.TMemoryTransport
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.Json
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal
import java.math.{BigInteger, MathContext, RoundingMode}

/**
 * Encodes values of type `A` with Apache Thrift's binary protocol
 * (`TBinaryProtocol`) and decodes them back. Get one with
 * `Schema#derive(ThriftFormat)`. Use it to exchange values with services and
 * tools that read Thrift binary data, without writing a `.thrift` file. A
 * record is a Thrift struct whose fields have the ids 1, 2, 3 and so on, in
 * declaration order; the decoder reads the fields in any order and skips field
 * ids that it does not know, so a reader can be older than the writer. A known
 * bug breaks this for a field of type `Unit`, or of a wrapper type (such as one
 * built with `Schema#transform`) over a type that is not a struct: the field's
 * header names the wrong Thrift type, so a reader that does not know the field
 * fails or misreads it.
 *
 * Decoding returns `Left` with a `SchemaError` whose message ends with the path
 * of the part that failed; errors that come from the Thrift library start with
 * `Thrift protocol error:`. Known bugs: a field missing from the input decodes
 * as `null`, `0` or `false` instead of failing, so a missing `Option` field
 * comes back as `null`, not `None`; the decoder does not check the Thrift type
 * written before a field, so a field of another type can decode without an
 * error as a wrong value; and `decode` ignores any bytes after the first value.
 *
 * {{{
 * import java.time.Instant
 * import zio.blocks.schema._
 *
 * case class LogEvent(service: String, level: Int, at: Instant)
 * object LogEvent {
 *   implicit val schema: Schema[LogEvent] = Schema.derived
 * }
 *
 * val codec: ThriftCodec[LogEvent] = LogEvent.schema.derive(ThriftFormat)
 * val bytes   = codec.encode(LogEvent("billing", 3, Instant.parse("2026-11-05T10:00:00Z"))) // 49 bytes
 * val decoded = codec.decode(bytes) // Right(LogEvent(billing,3,2026-11-05T10:00:00Z))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/thrift/ Thrift Codec Module]]
 *
 * @tparam A
 *   The type of values this codec encodes and decodes.
 */
abstract class ThriftCodec[A] extends BinaryCodec[A] {

  /**
   * Reads one value from `protocol`, and throws when the input is malformed;
   * `decode` turns the exception into a `Left`. Implement it, together with
   * [[encodeValue]], to write a codec by hand, and call `error(expectation)` to
   * reject a value:
   *
   * {{{
   * import org.apache.thrift.protocol.TProtocol
   *
   * val severity: ThriftCodec[Int] = new ThriftCodec[Int] {
   *   def decodeValue(protocol: TProtocol): Int = {
   *     val level = protocol.readI32()
   *     if (level >= 0 && level <= 7) level else error("Expected a severity from 0 to 7")
   *   }
   *
   *   def encodeValue(value: Int, protocol: TProtocol): Unit = protocol.writeI32(value)
   * }
   *
   * val rejected = severity.decode(ThriftCodec.intCodec.encode(9)).left.map(_.getMessage)
   * // Left(Expected a severity from 0 to 7 at: .)
   * }}}
   */
  def decodeValue(protocol: TProtocol): A

  /**
   * Writes `value` to `protocol`. With a protocol over your own transport, it
   * writes several values one after another, which [[decodeValue]] reads back
   * one per call:
   *
   * {{{
   * import org.apache.thrift.protocol.TBinaryProtocol
   * import org.apache.thrift.transport.TMemoryBuffer
   * import zio.blocks.schema._
   *
   * case class Heartbeat(host: String, sequence: Long)
   * object Heartbeat {
   *   implicit val schema: Schema[Heartbeat] = Schema.derived
   * }
   *
   * val codec    = Heartbeat.schema.derive(ThriftFormat)
   * val protocol = new TBinaryProtocol(new TMemoryBuffer(64))
   * codec.encodeValue(Heartbeat("api-1", 41L), protocol)
   * codec.encodeValue(Heartbeat("api-1", 42L), protocol)
   *
   * val first  = codec.decodeValue(protocol) // Heartbeat(api-1,41)
   * val second = codec.decodeValue(protocol) // Heartbeat(api-1,42)
   * }}}
   */
  def encodeValue(value: A, protocol: TProtocol): Unit

  /**
   * Throws a decode error with the message `expectation`. `decode` returns it
   * as a `Left` whose message is `expectation`, then ` at: ` and the path of
   * the failing part, such as `Unknown variant case index: 5 at: .`.
   */
  def error(expectation: String): Nothing = throw new ThriftCodecError(Nil, expectation)

  /**
   * Rethrows `error` as a decode error with `span` added to the front of its
   * path. Derived codecs call it when the codec of a field, element, map entry
   * or case fails, so the final message names where decoding stopped. Another
   * exception becomes a decode error with that exception's message, prefixed
   * with `Thrift protocol error: ` for a Thrift `TException`, or
   * `Unexpected end of input` for a `java.io.EOFException`.
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ThriftCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Decodes one value from a copy of the bytes between the buffer's position
   * and limit. The position of a buffer with an accessible backing array
   * (`hasArray`) does not move; the position of any other buffer, such as a
   * direct or read-only one, moves to its limit. A known bug: for a heap buffer
   * whose content does not start at index 0 of its backing array, such as one
   * made by `slice()`, it copies from the wrong offset. Bytes after the first
   * value are ignored, a known bug shared with the byte-array `decode`.
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) bs = input.array()
    else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    decode(java.util.Arrays.copyOfRange(bs, pos, pos + len))
  }

  /**
   * Encodes `value` into a new array, then copies it to the buffer at its
   * position and moves the position past it. Throws
   * `java.nio.BufferOverflowException` when the buffer has too little room, and
   * then writes nothing.
   */
  override def encode(value: A, output: ByteBuffer): Unit = output.put(encode(value))

  /**
   * Decodes one value from `input`. Returns `Left` when the bytes run out or
   * hold a value that the type does not accept. Known bugs: bytes after the
   * first value are ignored, and a record field missing from the input decodes
   * as `null`, `0` or `false`:
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class AuditEntry(actor: String, action: String)
   * object AuditEntry {
   *   implicit val schema: Schema[AuditEntry] = Schema.derived
   * }
   *
   * val codec     = AuditEntry.schema.derive(ThriftFormat)
   * val bytes     = codec.encode(AuditEntry("milad", "login"))
   * val decoded   = codec.decode(bytes) // Right(AuditEntry(milad,login))
   * val truncated = codec.decode(bytes.take(8)).left.map(_.getMessage)
   * // Left(Thrift protocol error: There's only 1bytes, but it asks for 5 at: .actor)
   * val noFields = codec.decode(Array[Byte](0)) // Right(AuditEntry(null,null)), a known bug
   * }}}
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try {
      val transport = new TMemoryTransport(input)
      val protocol  = new TBinaryProtocol(transport)
      new Right(decodeValue(protocol))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes `value` into a new byte array.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * val bytes = Schema[List[Int]].derive(ThriftFormat).encode(List(1, 2))
   * // Array(8, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 2): element type i32 (8), size 2, then each i32
   * }}}
   */
  def encode(value: A): Array[Byte] = {
    val transport = new TMemoryTransport(Array.emptyByteArray)
    encodeValue(value, new TBinaryProtocol(transport))
    transport.getOutput.toByteArray
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: ThriftCodecError =>
          var list  = e.spans
          val array = new Array[DynamicOptic.Node](list.size)
          var idx   = 0
          while (list ne Nil) {
            array(idx) = list.head
            idx += 1
            list = list.tail
          }
          new ExpectationMismatch(new DynamicOptic(Chunk.fromArray(array)), e.getMessage)
        case _ => new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException         => "Unexpected end of input"
    case _: org.apache.thrift.TException => s"Thrift protocol error: ${error.getMessage}"
    case _                               =>
      var msg = error.getMessage
      if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
      msg
  }
}

/**
 * The built-in codecs for the primitive types, which [[ThriftCodecDeriver]]
 * uses for primitive fields and values: `Schema[Int].derive(ThriftFormat)`
 * returns [[intCodec]] itself. Each one names the Thrift type that it writes.
 * Most `java.time` types, `UUID` and `Currency` are Thrift strings.
 */
object ThriftCodec {

  /**
   * 2147483639 (`Int.MaxValue - 8`). The derived Thrift codecs do not check it;
   * `AvroCodec.maxCollectionSize` is the limit that the Avro codecs check.
   */
  val maxCollectionSize: Int = Integer.MAX_VALUE - 8

  /**
   * Writes `Unit` as no bytes. As a struct field its type is Thrift `void`,
   * which libthrift cannot skip: a known bug makes a reader that does not know
   * the field fail with `Thrift protocol error: Unrecognized type 1`.
   */
  val unitCodec: ThriftCodec[Unit] = new ThriftCodec[Unit] {
    def decodeValue(protocol: TProtocol): Unit = ()

    def encodeValue(value: Unit, protocol: TProtocol): Unit = ()
  }

  /** Writes a `Boolean` as Thrift `bool`, one byte. */
  val booleanCodec: ThriftCodec[Boolean] = new ThriftCodec[Boolean] {
    def decodeValue(protocol: TProtocol): Boolean = protocol.readBool()

    def encodeValue(value: Boolean, protocol: TProtocol): Unit = protocol.writeBool(value)
  }

  /** Writes a `Byte` as Thrift `byte`, one byte. */
  val byteCodec: ThriftCodec[Byte] = new ThriftCodec[Byte] {
    def decodeValue(protocol: TProtocol): Byte = protocol.readByte()

    def encodeValue(value: Byte, protocol: TProtocol): Unit = protocol.writeByte(value)
  }

  /** Writes a `Short` as Thrift `i16`, two bytes, big-endian. */
  val shortCodec: ThriftCodec[Short] = new ThriftCodec[Short] {
    def decodeValue(protocol: TProtocol): Short = protocol.readI16()

    def encodeValue(value: Short, protocol: TProtocol): Unit = protocol.writeI16(value)
  }

  /** Writes an `Int` as Thrift `i32`, four bytes, big-endian. */
  val intCodec: ThriftCodec[Int] = new ThriftCodec[Int] {
    def decodeValue(protocol: TProtocol): Int = protocol.readI32()

    def encodeValue(value: Int, protocol: TProtocol): Unit = protocol.writeI32(value)
  }

  /** Writes a `Long` as Thrift `i64`, eight bytes, big-endian. */
  val longCodec: ThriftCodec[Long] = new ThriftCodec[Long] {
    def decodeValue(protocol: TProtocol): Long = protocol.readI64()

    def encodeValue(value: Long, protocol: TProtocol): Unit = protocol.writeI64(value)
  }

  /**
   * Writes a `Float` as Thrift `double`, eight bytes, since Thrift has no
   * 32-bit floating-point type; decoding narrows the `double` back to a
   * `Float`.
   */
  val floatCodec: ThriftCodec[Float] = new ThriftCodec[Float] {
    def decodeValue(protocol: TProtocol): Float = protocol.readDouble().toFloat

    def encodeValue(value: Float, protocol: TProtocol): Unit = protocol.writeDouble(value.toDouble)
  }

  /** Writes a `Double` as Thrift `double`, eight bytes. */
  val doubleCodec: ThriftCodec[Double] = new ThriftCodec[Double] {
    def decodeValue(protocol: TProtocol): Double = protocol.readDouble()

    def encodeValue(value: Double, protocol: TProtocol): Unit = protocol.writeDouble(value)
  }

  /** Writes a `Char` as Thrift `i16`, its UTF-16 code unit. */
  val charCodec: ThriftCodec[Char] = new ThriftCodec[Char] {
    def decodeValue(protocol: TProtocol): Char = protocol.readI16().toChar

    def encodeValue(value: Char, protocol: TProtocol): Unit = protocol.writeI16(value.toShort)
  }

  /**
   * Writes a `String` as Thrift `string`: a four-byte length, then the UTF-8
   * bytes.
   */
  val stringCodec: ThriftCodec[String] = new ThriftCodec[String] {
    def decodeValue(protocol: TProtocol): String = protocol.readString()

    def encodeValue(value: String, protocol: TProtocol): Unit = protocol.writeString(value)
  }

  /**
   * Writes a `BigInt` as Thrift `binary`, a four-byte length and then its
   * two's-complement big-endian bytes.
   */
  val bigIntCodec: ThriftCodec[BigInt] = new ThriftCodec[BigInt] {
    def decodeValue(protocol: TProtocol): BigInt = {
      val buf = protocol.readBinary()
      val arr = new Array[Byte](buf.remaining())
      buf.get(arr)
      BigInt(new BigInteger(arr))
    }

    def encodeValue(value: BigInt, protocol: TProtocol): Unit = protocol.writeBinary(ByteBuffer.wrap(value.toByteArray))
  }

  /**
   * Writes a `BigDecimal` as a Thrift struct with the fields `mantissa`
   * (`binary`, id 1, the unscaled value), `scale` (`i32`, id 2), `precision`
   * (`i32`, id 3) and `roundingMode` (`byte`, id 4, the ordinal of its
   * `RoundingMode`); the last two come from its `MathContext`. A known bug:
   * decoding rounds the value to the precision of that `MathContext`, so a
   * value with more significant digits than the precision, such as one made
   * with `BigDecimal(java.math.BigDecimal)`, comes back rounded.
   */
  val bigDecimalCodec: ThriftCodec[BigDecimal] = new ThriftCodec[BigDecimal] {
    private[this] val mantissaField     = new TField("mantissa", TType.STRING, 1)
    private[this] val scaleField        = new TField("scale", TType.I32, 2)
    private[this] val precisionField    = new TField("precision", TType.I32, 3)
    private[this] val roundingModeField = new TField("roundingMode", TType.BYTE, 4)

    def decodeValue(protocol: TProtocol): BigDecimal = {
      protocol.readFieldBegin()
      val mantissaBuf = protocol.readBinary()
      val mantissaArr = new Array[Byte](mantissaBuf.remaining())
      mantissaBuf.get(mantissaArr)
      val mantissa = new BigInteger(mantissaArr)
      protocol.readFieldBegin()
      val scale = protocol.readI32()
      protocol.readFieldBegin()
      val precision = protocol.readI32()
      protocol.readFieldBegin()
      val roundingMode = protocol.readByte()
      protocol.readFieldBegin() // read STOP
      new BigDecimal(
        new java.math.BigDecimal(mantissa, scale, new MathContext(precision, RoundingMode.valueOf(roundingMode)))
      )
    }

    def encodeValue(value: BigDecimal, protocol: TProtocol): Unit = {
      val bd = value.underlying()
      val mc = value.mc
      protocol.writeFieldBegin(mantissaField)
      protocol.writeBinary(ByteBuffer.wrap(bd.unscaledValue().toByteArray))
      protocol.writeFieldBegin(scaleField)
      protocol.writeI32(bd.scale())
      protocol.writeFieldBegin(precisionField)
      protocol.writeI32(mc.getPrecision)
      protocol.writeFieldBegin(roundingModeField)
      protocol.writeByte(mc.getRoundingMode.ordinal.toByte)
      protocol.writeFieldStop()
    }
  }

  /** Writes a `DayOfWeek` as Thrift `byte`, from 1 (Monday) to 7 (Sunday). */
  val dayOfWeekCodec: ThriftCodec[DayOfWeek] = new ThriftCodec[DayOfWeek] {
    def decodeValue(protocol: TProtocol): DayOfWeek = DayOfWeek.of(protocol.readByte().toInt)

    def encodeValue(value: DayOfWeek, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  /**
   * Writes a `Duration` as a Thrift `string` in ISO 8601 form, such as
   * `PT1.5S`.
   */
  val durationCodec: ThriftCodec[Duration] = new ThriftCodec[Duration] {
    def decodeValue(protocol: TProtocol): Duration = Json.durationRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: Duration, protocol: TProtocol): Unit =
      protocol.writeString(Json.durationRawCodec.encodeToString(value))
  }

  /**
   * Writes an `Instant` as a Thrift `string`, such as
   * `2026-11-05T10:00:00.500Z`.
   */
  val instantCodec: ThriftCodec[Instant] = new ThriftCodec[Instant] {
    def decodeValue(protocol: TProtocol): Instant = Json.instantRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: Instant, protocol: TProtocol): Unit =
      protocol.writeString(Json.instantRawCodec.encodeToString(value))
  }

  /** Writes a `LocalDate` as a Thrift `string`, such as `2026-11-05`. */
  val localDateCodec: ThriftCodec[LocalDate] = new ThriftCodec[LocalDate] {
    def decodeValue(protocol: TProtocol): LocalDate = Json.localDateRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalDate, protocol: TProtocol): Unit =
      protocol.writeString(Json.localDateRawCodec.encodeToString(value))
  }

  /** Writes a `LocalTime` as a Thrift `string`, such as `10:30`. */
  val localTimeCodec: ThriftCodec[LocalTime] = new ThriftCodec[LocalTime] {
    def decodeValue(protocol: TProtocol): LocalTime = Json.localTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.localTimeRawCodec.encodeToString(value))
  }

  /**
   * Writes a `LocalDateTime` as a Thrift `string`, such as `2026-11-05T10:30`.
   */
  val localDateTimeCodec: ThriftCodec[LocalDateTime] = new ThriftCodec[LocalDateTime] {
    def decodeValue(protocol: TProtocol): LocalDateTime = Json.localDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.localDateTimeRawCodec.encodeToString(value))
  }

  /** Writes a `Month` as Thrift `byte`, from 1 (January) to 12 (December). */
  val monthCodec: ThriftCodec[Month] = new ThriftCodec[Month] {
    def decodeValue(protocol: TProtocol): Month = Month.of(protocol.readByte().toInt)

    def encodeValue(value: Month, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  /** Writes a `MonthDay` as a Thrift `string`, such as `--11-05`. */
  val monthDayCodec: ThriftCodec[MonthDay] = new ThriftCodec[MonthDay] {
    def decodeValue(protocol: TProtocol): MonthDay = Json.monthDayRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: MonthDay, protocol: TProtocol): Unit =
      protocol.writeString(Json.monthDayRawCodec.encodeToString(value))
  }

  /** Writes an `OffsetTime` as a Thrift `string`, such as `10:30+03:00`. */
  val offsetTimeCodec: ThriftCodec[OffsetTime] = new ThriftCodec[OffsetTime] {
    def decodeValue(protocol: TProtocol): OffsetTime = Json.offsetTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: OffsetTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.offsetTimeRawCodec.encodeToString(value))
  }

  /**
   * Writes an `OffsetDateTime` as a Thrift `string`, such as
   * `2026-11-05T10:30+03:00`.
   */
  val offsetDateTimeCodec: ThriftCodec[OffsetDateTime] = new ThriftCodec[OffsetDateTime] {
    def decodeValue(protocol: TProtocol): OffsetDateTime =
      Json.offsetDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: OffsetDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.offsetDateTimeRawCodec.encodeToString(value))
  }

  /**
   * Writes a `Period` as a Thrift `string` in ISO 8601 form, such as `P1Y2M3D`.
   */
  val periodCodec: ThriftCodec[Period] = new ThriftCodec[Period] {
    def decodeValue(protocol: TProtocol): Period = Json.periodRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: Period, protocol: TProtocol): Unit =
      protocol.writeString(Json.periodRawCodec.encodeToString(value))
  }

  /** Writes a `Year` as Thrift `i32`. */
  val yearCodec: ThriftCodec[Year] = new ThriftCodec[Year] {
    def decodeValue(protocol: TProtocol): Year = Year.of(protocol.readI32())

    def encodeValue(value: Year, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
  }

  /** Writes a `YearMonth` as a Thrift `string`, such as `2026-11`. */
  val yearMonthCodec: ThriftCodec[YearMonth] = new ThriftCodec[YearMonth] {
    def decodeValue(protocol: TProtocol): YearMonth = YearMonth.parse(protocol.readString())

    def encodeValue(value: YearMonth, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  /** Writes a `ZoneId` as a Thrift `string`, its ID such as `Asia/Tehran`. */
  val zoneIdCodec: ThriftCodec[ZoneId] = new ThriftCodec[ZoneId] {
    def decodeValue(protocol: TProtocol): ZoneId = ZoneId.of(protocol.readString())

    def encodeValue(value: ZoneId, protocol: TProtocol): Unit = protocol.writeString(value.getId)
  }

  /** Writes a `ZoneOffset` as Thrift `i32`, the offset in seconds. */
  val zoneOffsetCodec: ThriftCodec[ZoneOffset] = new ThriftCodec[ZoneOffset] {
    def decodeValue(protocol: TProtocol): ZoneOffset = ZoneOffset.ofTotalSeconds(protocol.readI32())

    def encodeValue(value: ZoneOffset, protocol: TProtocol): Unit = protocol.writeI32(value.getTotalSeconds)
  }

  /**
   * Writes a `ZonedDateTime` as a Thrift `string`, such as
   * `2026-11-05T10:30+03:30[Asia/Tehran]`.
   */
  val zonedDateTimeCodec: ThriftCodec[ZonedDateTime] = new ThriftCodec[ZonedDateTime] {
    def decodeValue(protocol: TProtocol): ZonedDateTime = Json.zonedDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: ZonedDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.zonedDateTimeRawCodec.encodeToString(value))
  }

  /**
   * Writes a `Currency` as a Thrift `string`, its ISO 4217 code such as `EUR`.
   */
  val currencyCodec: ThriftCodec[Currency] = new ThriftCodec[java.util.Currency] {
    def decodeValue(protocol: TProtocol): java.util.Currency = java.util.Currency.getInstance(protocol.readString())

    def encodeValue(value: java.util.Currency, protocol: TProtocol): Unit = protocol.writeString(value.getCurrencyCode)
  }

  /**
   * Writes a `UUID` as a Thrift `string` in its 36-character form, such as
   * `00000000-0000-0001-0000-000000000002`.
   */
  val uuidCodec: ThriftCodec[UUID] = new ThriftCodec[java.util.UUID] {
    def decodeValue(protocol: TProtocol): java.util.UUID = java.util.UUID.fromString(protocol.readString())

    def encodeValue(value: java.util.UUID, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }
}

/**
 * Internal error class for tracking the path during error propagation; it has
 * no stack trace.
 */
private class ThriftCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false)
