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

import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.json.Json
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from
 * MessagePack format. Encapsulates logic to map MessagePack binary
 * representations into native type structures and vice versa.
 *
 * This class requires an implementation for two core operations: decoding a
 * value of type `A` from a MessagePack representation and encoding a value of
 * type `A` into a MessagePack representation.
 */
abstract class MessagePackCodec[A] extends BinaryCodec[A] {

  /**
   * Attempts to decode a value of type `A` from the specified
   * `MessagePackReader`, but may fail with `MessagePackCodecError` error if the
   * MessagePack input does not encode a value of this type.
   *
   * @param in
   *   an instance of `MessagePackReader` which provides access to the
   *   MessagePack input to parse a MessagePack value to value of type `A`
   */
  def decodeValue(in: MessagePackReader): A

  /**
   * Encodes the specified value using provided `MessagePackWriter`, but may
   * fail with `MessagePackCodecError` if it cannot be encoded properly
   * according to MessagePack specification requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `MessagePackWriter` which provides access to MessagePack
   *   output to serialize the specified value as a MessagePack value
   */
  def encodeValue(x: A, out: MessagePackWriter): Unit

  /**
   * Decodes a value of type `A` from the given `ByteBuffer`. If decoding fails,
   * a `SchemaError` is returned.
   *
   * @param input
   *   the `ByteBuffer` containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var reader = MessagePackCodec.readerPool.get
    if (reader.isInUse) reader = new MessagePackReader()
    try {
      var bytes: Array[Byte] = null
      var offset             = input.position()
      val length             = input.remaining()
      if (input.hasArray) {
        bytes = input.array()
        offset = input.arrayOffset() + offset
      } else {
        bytes = new Array[Byte](length)
        input.get(bytes)
        offset = 0
      }
      reader.reset(bytes, offset, length)
      new Right(decodeValue(reader))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally reader.release()
  }

  /**
   * Encodes the specified value of type `A` into binary format and writes it to
   * the provided `ByteBuffer`.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `ByteBuffer` where the encoded binary data is written
   */
  override def encode(value: A, output: ByteBuffer): Unit = {
    var writer = MessagePackCodec.writerPool.get
    if (writer.isInUse) writer = new MessagePackWriter()
    writer.reset()
    try {
      encodeValue(value, writer)
      output.put(writer.toByteArray)
    } finally writer.release()
  }

  /**
   * Decodes a value of type `A` from the given byte array. If decoding fails, a
   * `SchemaError` is returned.
   *
   * @param input
   *   the byte array containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] = {
    var reader = MessagePackCodec.readerPool.get
    if (reader.isInUse) reader = new MessagePackReader()
    try {
      reader.reset(input, 0, input.length)
      new Right(decodeValue(reader))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally reader.release()
  }

  /**
   * Encodes the specified value of type `A` into a binary format.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @return
   *   an array of bytes representing the binary-encoded data
   */
  def encode(value: A): Array[Byte] = {
    var writer = MessagePackCodec.writerPool.get
    if (writer.isInUse) writer = new MessagePackWriter()
    writer.reset()
    try {
      encodeValue(value, writer)
      writer.toByteArray
    } finally writer.release()
  }

  def error(msg: String): Nothing = throw new MessagePackCodecError(Nil, msg)

  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackCodecError => throw e.copy(spans = span :: e.spans)
    case _                        =>
      var msg = error.getMessage
      if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
      throw new MessagePackCodecError(span :: Nil, msg)
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      ExpectationMismatch(
        error match {
          case e: MessagePackCodecError =>
            var list  = e.spans
            val array = new Array[DynamicOptic.Node](list.size)
            var idx   = 0
            while (list ne Nil) {
              array(idx) = list.head
              idx += 1
              list = list.tail
            }
            new DynamicOptic(ArraySeq.unsafeWrapArray(array))
          case _ => DynamicOptic.root
        },
        error.getMessage
      ),
      Nil
    )
  )
}

object MessagePackCodec {
  private val readerPool: ThreadLocal[MessagePackReader] = new ThreadLocal[MessagePackReader] {
    override def initialValue(): MessagePackReader = new MessagePackReader(null, 0, 0)
  }
  private val writerPool: ThreadLocal[MessagePackWriter] = new ThreadLocal[MessagePackWriter] {
    override def initialValue(): MessagePackWriter = new MessagePackWriter(new Array[Byte](32768), -1)
  }
  val unitCodec: MessagePackCodec[Unit] = new MessagePackCodec[Unit] {
    def decodeValue(in: MessagePackReader): Unit = in.readNil()

    def encodeValue(x: Unit, out: MessagePackWriter): Unit = out.writeNil()
  }
  val booleanCodec: MessagePackCodec[Boolean] = new MessagePackCodec[Boolean] {
    def decodeValue(in: MessagePackReader): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: MessagePackWriter): Unit = out.writeBoolean(x)
  }
  val byteCodec: MessagePackCodec[Byte] = new MessagePackCodec[Byte] {
    def decodeValue(in: MessagePackReader): Byte = in.readByteValue()

    def encodeValue(x: Byte, out: MessagePackWriter): Unit = out.writeByte(x)
  }
  val shortCodec: MessagePackCodec[Short] = new MessagePackCodec[Short] {
    def decodeValue(in: MessagePackReader): Short = in.readShortValue()

    def encodeValue(x: Short, out: MessagePackWriter): Unit = out.writeShort(x)
  }
  val intCodec: MessagePackCodec[Int] = new MessagePackCodec[Int] {
    def decodeValue(in: MessagePackReader): Int = in.readIntValue()

    def encodeValue(x: Int, out: MessagePackWriter): Unit = out.writeInt(x)
  }
  val longCodec: MessagePackCodec[Long] = new MessagePackCodec[Long] {
    def decodeValue(in: MessagePackReader): Long = in.readLongValue()

    def encodeValue(x: Long, out: MessagePackWriter): Unit = out.writeLong(x)
  }
  val floatCodec: MessagePackCodec[Float] = new MessagePackCodec[Float] {
    def decodeValue(in: MessagePackReader): Float = in.readFloatValue()

    def encodeValue(x: Float, out: MessagePackWriter): Unit = out.writeFloat(x)
  }
  val doubleCodec: MessagePackCodec[Double] = new MessagePackCodec[Double] {
    def decodeValue(in: MessagePackReader): Double = in.readDoubleValue()

    def encodeValue(x: Double, out: MessagePackWriter): Unit = out.writeDouble(x)
  }
  val charCodec: MessagePackCodec[Char] = new MessagePackCodec[Char] {
    def decodeValue(in: MessagePackReader): Char = in.readChar()

    def encodeValue(x: Char, out: MessagePackWriter): Unit = out.writeChar(x)
  }
  val stringCodec: MessagePackCodec[String] = new MessagePackCodec[String] {
    def decodeValue(in: MessagePackReader): String = in.readString()

    def encodeValue(x: String, out: MessagePackWriter): Unit = out.writeString(x)
  }
  val bigIntCodec: MessagePackCodec[BigInt] = new MessagePackCodec[BigInt] {
    def decodeValue(in: MessagePackReader): BigInt = in.readBigInt()

    def encodeValue(x: BigInt, out: MessagePackWriter): Unit = out.writeBigInt(x)
  }
  val bigDecimalCodec: MessagePackCodec[BigDecimal] = new MessagePackCodec[BigDecimal] {
    def decodeValue(in: MessagePackReader): BigDecimal = in.readBigDecimal()

    def encodeValue(x: BigDecimal, out: MessagePackWriter): Unit = out.writeBigDecimal(x)
  }
  val dayOfWeekCodec: MessagePackCodec[DayOfWeek] = new MessagePackCodec[DayOfWeek] {
    def decodeValue(in: MessagePackReader): DayOfWeek = DayOfWeek.of(in.readIntValue())

    def encodeValue(x: DayOfWeek, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }
  val durationCodec: MessagePackCodec[Duration] = new MessagePackCodec[Duration] {
    def decodeValue(in: MessagePackReader): Duration = {
      val len = in.readMapHeader()
      if (len != 2) error(s"Expected Duration map of 2, got: $len")
      var seconds: Long = 0L
      var nanos: Int    = 0
      var i             = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.SecondsName)) seconds = in.readLongValue()
        else if (in.readFieldNameEquals(MessagePackReader.NanosName)) nanos = in.readIntValue()
        else error(s"Unexpected Duration field")
        i += 1
      }
      Duration.ofSeconds(seconds, nanos.toLong)
    }

    def encodeValue(x: Duration, out: MessagePackWriter): Unit = {
      out.writeMapHeader(2)
      out.writeRaw(MessagePackWriter.SecondsBytes)
      out.writeLong(x.getSeconds)
      out.writeRaw(MessagePackWriter.NanosBytes)
      out.writeInt(x.getNano)
    }
  }
  val instantCodec: MessagePackCodec[Instant] = new MessagePackCodec[Instant] {
    def decodeValue(in: MessagePackReader): Instant = Json.instantRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: Instant, out: MessagePackWriter): Unit = out.writeString(Json.instantRawCodec.encodeToString(x))
  }
  val localDateCodec: MessagePackCodec[LocalDate] = new MessagePackCodec[LocalDate] {
    def decodeValue(in: MessagePackReader): LocalDate = Json.localDateRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalDate, out: MessagePackWriter): Unit =
      out.writeString(Json.localDateRawCodec.encodeToString(x))
  }
  val localDateTimeCodec: MessagePackCodec[LocalDateTime] = new MessagePackCodec[LocalDateTime] {
    def decodeValue(in: MessagePackReader): LocalDateTime = Json.localDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.localDateTimeRawCodec.encodeToString(x))
  }
  val localTimeCodec: MessagePackCodec[LocalTime] = new MessagePackCodec[LocalTime] {
    def decodeValue(in: MessagePackReader): LocalTime = Json.localTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalTime, out: MessagePackWriter): Unit =
      out.writeString(Json.localTimeRawCodec.encodeToString(x))
  }
  val monthCodec: MessagePackCodec[Month] = new MessagePackCodec[Month] {
    def decodeValue(in: MessagePackReader): Month = Month.of(in.readIntValue())

    def encodeValue(x: Month, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }
  val monthDayCodec: MessagePackCodec[MonthDay] = new MessagePackCodec[MonthDay] {
    def decodeValue(in: MessagePackReader): MonthDay = {
      val len = in.readMapHeader()
      if (len != 2) error(s"Expected MonthDay map of 2, got: $len")
      var month: Int = 0
      var day: Int   = 0
      var i          = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.MonthName)) month = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.DayName)) day = in.readIntValue()
        else error(s"Unexpected MonthDay field")
        i += 1
      }
      MonthDay.of(month, day)
    }

    def encodeValue(x: MonthDay, out: MessagePackWriter): Unit = {
      out.writeMapHeader(2)
      out.writeRaw(MessagePackWriter.MonthBytes)
      out.writeInt(x.getMonthValue)
      out.writeRaw(MessagePackWriter.DayBytes)
      out.writeInt(x.getDayOfMonth)
    }
  }
  val offsetDateTimeCodec: MessagePackCodec[OffsetDateTime] = new MessagePackCodec[OffsetDateTime] {
    def decodeValue(in: MessagePackReader): OffsetDateTime = Json.offsetDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: OffsetDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.offsetDateTimeRawCodec.encodeToString(x))
  }
  val offsetTimeCodec: MessagePackCodec[OffsetTime] = new MessagePackCodec[OffsetTime] {
    def decodeValue(in: MessagePackReader): OffsetTime = Json.offsetTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: OffsetTime, out: MessagePackWriter): Unit =
      out.writeString(Json.offsetTimeRawCodec.encodeToString(x))
  }
  val periodCodec: MessagePackCodec[Period] = new MessagePackCodec[Period] {
    def decodeValue(in: MessagePackReader): Period = {
      val len = in.readMapHeader()
      if (len != 3) error(s"Expected Period map of 3, got: $len")
      var years: Int  = 0
      var months: Int = 0
      var days: Int   = 0
      var i           = 0
      while (i < 3) {
        if (in.readFieldNameEquals(MessagePackReader.YearsName)) years = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.MonthsName)) months = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.DaysName)) days = in.readIntValue()
        else error(s"Unexpected Period field")
        i += 1
      }
      Period.of(years, months, days)
    }

    def encodeValue(x: Period, out: MessagePackWriter): Unit = {
      out.writeMapHeader(3)
      out.writeRaw(MessagePackWriter.YearsBytes)
      out.writeInt(x.getYears)
      out.writeRaw(MessagePackWriter.MonthsBytes)
      out.writeInt(x.getMonths)
      out.writeRaw(MessagePackWriter.DaysBytes)
      out.writeInt(x.getDays)
    }
  }
  val yearCodec: MessagePackCodec[Year] = new MessagePackCodec[Year] {
    def decodeValue(in: MessagePackReader): Year = Year.of(in.readIntValue())

    def encodeValue(x: Year, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }
  val yearMonthCodec: MessagePackCodec[YearMonth] = new MessagePackCodec[YearMonth] {
    def decodeValue(in: MessagePackReader): YearMonth = {
      val len = in.readMapHeader()
      if (len != 2) error(s"Expected YearMonth map of 2, got: $len")
      var year: Int  = 0
      var month: Int = 0
      var i          = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.YearName)) year = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.MonthName)) month = in.readIntValue()
        else error(s"Unexpected YearMonth field")
        i += 1
      }
      YearMonth.of(year, month)
    }

    def encodeValue(x: YearMonth, out: MessagePackWriter): Unit = {
      out.writeMapHeader(2)
      out.writeRaw(MessagePackWriter.YearBytes)
      out.writeInt(x.getYear)
      out.writeRaw(MessagePackWriter.MonthBytes)
      out.writeInt(x.getMonthValue)
    }
  }
  val zoneIdCodec: MessagePackCodec[ZoneId] = new MessagePackCodec[ZoneId] {
    def decodeValue(in: MessagePackReader): ZoneId = ZoneId.of(in.readString())

    def encodeValue(x: ZoneId, out: MessagePackWriter): Unit = out.writeString(x.getId)
  }
  val zoneOffsetCodec: MessagePackCodec[ZoneOffset] = new MessagePackCodec[ZoneOffset] {
    def decodeValue(in: MessagePackReader): ZoneOffset = ZoneOffset.ofTotalSeconds(in.readIntValue())

    def encodeValue(x: ZoneOffset, out: MessagePackWriter): Unit = out.writeInt(x.getTotalSeconds)
  }
  val zonedDateTimeCodec: MessagePackCodec[ZonedDateTime] = new MessagePackCodec[ZonedDateTime] {
    def decodeValue(in: MessagePackReader): ZonedDateTime = Json.zonedDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: ZonedDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.zonedDateTimeRawCodec.encodeToString(x))
  }
  val currencyCodec: MessagePackCodec[Currency] = new MessagePackCodec[Currency] {
    def decodeValue(in: MessagePackReader): Currency = Currency.getInstance(in.readString())

    def encodeValue(x: Currency, out: MessagePackWriter): Unit = out.writeString(x.getCurrencyCode)
  }
  val uuidCodec: MessagePackCodec[UUID] = new MessagePackCodec[UUID] {
    def decodeValue(in: MessagePackReader): UUID = UUID.fromString(in.readString())

    def encodeValue(x: UUID, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }
  val binaryCodec: MessagePackCodec[Array[Byte]] = new MessagePackCodec[Array[Byte]] {
    def decodeValue(in: MessagePackReader): Array[Byte] = in.readBinary()

    def encodeValue(x: Array[Byte], out: MessagePackWriter): Unit = out.writeBinary(x)
  }
}

private case class MessagePackCodecError(spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false)
