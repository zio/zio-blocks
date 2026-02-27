package zio.blocks.schema.msgpack

import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch

import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from
 * MessagePack format. Encapsulates logic to map MessagePack binary
 * representations into native type structures and vice versa.
 *
 * @param valueType
 *   Integer representing the type of the value for encoding/decoding. Default
 *   is set to `MessagePackBinaryCodec.objectType`.
 *
 * This class requires an implementation for two core operations: decoding a
 * value of type `A` from a MessagePack representation and encoding a value of
 * type `A` into a MessagePack representation.
 */
abstract class MessagePackBinaryCodec[A](val valueType: Int = MessagePackBinaryCodec.objectType)
    extends BinaryCodec[A] {

  /**
   * Computes the appropriate `RegisterOffset` based on the value type defined
   * in `MessagePackBinaryCodec`.
   */
  val valueOffset: RegisterOffset.RegisterOffset = (valueType: @switch) match {
    case 0 => RegisterOffset(objects = 1)
    case 1 => RegisterOffset(ints = 1)
    case 2 => RegisterOffset(longs = 1)
    case 3 => RegisterOffset(floats = 1)
    case 4 => RegisterOffset(doubles = 1)
    case 5 => RegisterOffset(booleans = 1)
    case 6 => RegisterOffset(bytes = 1)
    case 7 => RegisterOffset(chars = 1)
    case 8 => RegisterOffset(shorts = 1)
    case _ => RegisterOffset.Zero
  }

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
   * Returns some value that will be passed as the default parameter in
   * `decodeValue`.
   */
  def nullValue: A = null.asInstanceOf[A]

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
    var reader = MessagePackBinaryCodec.readerPool.get
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
    var writer = MessagePackBinaryCodec.writerPool.get
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
    var reader = MessagePackBinaryCodec.readerPool.get
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
    var writer = MessagePackBinaryCodec.writerPool.get
    if (writer.isInUse) writer = new MessagePackWriter()
    writer.reset()
    try {
      encodeValue(value, writer)
      writer.toByteArray
    } finally writer.release()
  }

  protected def decodeError(msg: String): Nothing = throw new MessagePackCodecError(Nil, msg)

  protected def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
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

object MessagePackBinaryCodec {
  val objectType  = 0
  val intType     = 1
  val longType    = 2
  val floatType   = 3
  val doubleType  = 4
  val booleanType = 5
  val byteType    = 6
  val charType    = 7
  val shortType   = 8
  val unitType    = 9

  val unitCodec: MessagePackBinaryCodec[Unit] = new MessagePackBinaryCodec[Unit](unitType) {
    def decodeValue(in: MessagePackReader): Unit = in.readNil()

    def encodeValue(x: Unit, out: MessagePackWriter): Unit = out.writeNil()
  }

  val booleanCodec: MessagePackBinaryCodec[Boolean] = new MessagePackBinaryCodec[Boolean](booleanType) {
    def decodeValue(in: MessagePackReader): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: MessagePackWriter): Unit = out.writeBoolean(x)
  }

  val byteCodec: MessagePackBinaryCodec[Byte] = new MessagePackBinaryCodec[Byte](byteType) {
    def decodeValue(in: MessagePackReader): Byte = in.readByteValue()

    def encodeValue(x: Byte, out: MessagePackWriter): Unit = out.writeByte(x)
  }

  val shortCodec: MessagePackBinaryCodec[Short] = new MessagePackBinaryCodec[Short](shortType) {
    def decodeValue(in: MessagePackReader): Short = in.readShortValue()

    def encodeValue(x: Short, out: MessagePackWriter): Unit = out.writeShort(x)
  }

  val intCodec: MessagePackBinaryCodec[Int] = new MessagePackBinaryCodec[Int](intType) {
    def decodeValue(in: MessagePackReader): Int = in.readIntValue()

    def encodeValue(x: Int, out: MessagePackWriter): Unit = out.writeInt(x)
  }

  val longCodec: MessagePackBinaryCodec[Long] = new MessagePackBinaryCodec[Long](longType) {
    def decodeValue(in: MessagePackReader): Long = in.readLongValue()

    def encodeValue(x: Long, out: MessagePackWriter): Unit = out.writeLong(x)
  }

  val floatCodec: MessagePackBinaryCodec[Float] = new MessagePackBinaryCodec[Float](floatType) {
    def decodeValue(in: MessagePackReader): Float = in.readFloatValue()

    def encodeValue(x: Float, out: MessagePackWriter): Unit = out.writeFloat(x)
  }

  val doubleCodec: MessagePackBinaryCodec[Double] = new MessagePackBinaryCodec[Double](doubleType) {
    def decodeValue(in: MessagePackReader): Double = in.readDoubleValue()

    def encodeValue(x: Double, out: MessagePackWriter): Unit = out.writeDouble(x)
  }

  val charCodec: MessagePackBinaryCodec[Char] = new MessagePackBinaryCodec[Char](charType) {
    def decodeValue(in: MessagePackReader): Char = in.readChar()

    def encodeValue(x: Char, out: MessagePackWriter): Unit = out.writeChar(x)
  }

  val stringCodec: MessagePackBinaryCodec[String] = new MessagePackBinaryCodec[String]() {
    def decodeValue(in: MessagePackReader): String = in.readString()

    def encodeValue(x: String, out: MessagePackWriter): Unit = out.writeString(x)
  }

  val bigIntCodec: MessagePackBinaryCodec[BigInt] = new MessagePackBinaryCodec[BigInt]() {
    def decodeValue(in: MessagePackReader): BigInt = in.readBigInt()

    def encodeValue(x: BigInt, out: MessagePackWriter): Unit = out.writeBigInt(x)
  }

  val bigDecimalCodec: MessagePackBinaryCodec[BigDecimal] = new MessagePackBinaryCodec[BigDecimal]() {
    def decodeValue(in: MessagePackReader): BigDecimal = in.readBigDecimal()

    def encodeValue(x: BigDecimal, out: MessagePackWriter): Unit = out.writeBigDecimal(x)
  }

  val dayOfWeekCodec: MessagePackBinaryCodec[DayOfWeek] = new MessagePackBinaryCodec[DayOfWeek]() {
    def decodeValue(in: MessagePackReader): DayOfWeek = DayOfWeek.of(in.readIntValue())

    def encodeValue(x: DayOfWeek, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  val durationCodec: MessagePackBinaryCodec[Duration] = new MessagePackBinaryCodec[Duration]() {
    def decodeValue(in: MessagePackReader): Duration = {
      val len = in.readMapHeader()
      if (len != 2) in.decodeError(s"Expected Duration map of 2, got: $len")
      var seconds: Long = 0L
      var nanos: Int    = 0
      var i             = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.SecondsName)) seconds = in.readLongValue()
        else if (in.readFieldNameEquals(MessagePackReader.NanosName)) nanos = in.readIntValue()
        else in.decodeError(s"Unexpected Duration field")
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

  val instantCodec: MessagePackBinaryCodec[Instant] = new MessagePackBinaryCodec[Instant]() {
    def decodeValue(in: MessagePackReader): Instant = Instant.parse(in.readString())

    def encodeValue(x: Instant, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val localDateCodec: MessagePackBinaryCodec[LocalDate] = new MessagePackBinaryCodec[LocalDate]() {
    def decodeValue(in: MessagePackReader): LocalDate = LocalDate.parse(in.readString())

    def encodeValue(x: LocalDate, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val localDateTimeCodec: MessagePackBinaryCodec[LocalDateTime] = new MessagePackBinaryCodec[LocalDateTime]() {
    def decodeValue(in: MessagePackReader): LocalDateTime = LocalDateTime.parse(in.readString())

    def encodeValue(x: LocalDateTime, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val localTimeCodec: MessagePackBinaryCodec[LocalTime] = new MessagePackBinaryCodec[LocalTime]() {
    def decodeValue(in: MessagePackReader): LocalTime = LocalTime.parse(in.readString())

    def encodeValue(x: LocalTime, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val monthCodec: MessagePackBinaryCodec[Month] = new MessagePackBinaryCodec[Month]() {
    def decodeValue(in: MessagePackReader): Month = Month.of(in.readIntValue())

    def encodeValue(x: Month, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  val monthDayCodec: MessagePackBinaryCodec[MonthDay] = new MessagePackBinaryCodec[MonthDay]() {
    def decodeValue(in: MessagePackReader): MonthDay = {
      val len = in.readMapHeader()
      if (len != 2) in.decodeError(s"Expected MonthDay map of 2, got: $len")
      var month: Int = 0
      var day: Int   = 0
      var i          = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.MonthName)) month = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.DayName)) day = in.readIntValue()
        else in.decodeError(s"Unexpected MonthDay field")
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

  val offsetDateTimeCodec: MessagePackBinaryCodec[OffsetDateTime] = new MessagePackBinaryCodec[OffsetDateTime]() {
    def decodeValue(in: MessagePackReader): OffsetDateTime = OffsetDateTime.parse(in.readString())

    def encodeValue(x: OffsetDateTime, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val offsetTimeCodec: MessagePackBinaryCodec[OffsetTime] = new MessagePackBinaryCodec[OffsetTime]() {
    def decodeValue(in: MessagePackReader): OffsetTime = OffsetTime.parse(in.readString())

    def encodeValue(x: OffsetTime, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val periodCodec: MessagePackBinaryCodec[Period] = new MessagePackBinaryCodec[Period]() {
    def decodeValue(in: MessagePackReader): Period = {
      val len = in.readMapHeader()
      if (len != 3) in.decodeError(s"Expected Period map of 3, got: $len")
      var years: Int  = 0
      var months: Int = 0
      var days: Int   = 0
      var i           = 0
      while (i < 3) {
        if (in.readFieldNameEquals(MessagePackReader.YearsName)) years = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.MonthsName)) months = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.DaysName)) days = in.readIntValue()
        else in.decodeError(s"Unexpected Period field")
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

  val yearCodec: MessagePackBinaryCodec[Year] = new MessagePackBinaryCodec[Year]() {
    def decodeValue(in: MessagePackReader): Year = Year.of(in.readIntValue())

    def encodeValue(x: Year, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  val yearMonthCodec: MessagePackBinaryCodec[YearMonth] = new MessagePackBinaryCodec[YearMonth]() {
    def decodeValue(in: MessagePackReader): YearMonth = {
      val len = in.readMapHeader()
      if (len != 2) in.decodeError(s"Expected YearMonth map of 2, got: $len")
      var year: Int  = 0
      var month: Int = 0
      var i          = 0
      while (i < 2) {
        if (in.readFieldNameEquals(MessagePackReader.YearName)) year = in.readIntValue()
        else if (in.readFieldNameEquals(MessagePackReader.MonthName)) month = in.readIntValue()
        else in.decodeError(s"Unexpected YearMonth field")
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

  val zoneIdCodec: MessagePackBinaryCodec[ZoneId] = new MessagePackBinaryCodec[ZoneId]() {
    def decodeValue(in: MessagePackReader): ZoneId = ZoneId.of(in.readString())

    def encodeValue(x: ZoneId, out: MessagePackWriter): Unit = out.writeString(x.getId)
  }

  val zoneOffsetCodec: MessagePackBinaryCodec[ZoneOffset] = new MessagePackBinaryCodec[ZoneOffset]() {
    def decodeValue(in: MessagePackReader): ZoneOffset = ZoneOffset.ofTotalSeconds(in.readIntValue())

    def encodeValue(x: ZoneOffset, out: MessagePackWriter): Unit = out.writeInt(x.getTotalSeconds)
  }

  val zonedDateTimeCodec: MessagePackBinaryCodec[ZonedDateTime] = new MessagePackBinaryCodec[ZonedDateTime]() {
    def decodeValue(in: MessagePackReader): ZonedDateTime = ZonedDateTime.parse(in.readString())

    def encodeValue(x: ZonedDateTime, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val currencyCodec: MessagePackBinaryCodec[Currency] = new MessagePackBinaryCodec[Currency]() {
    def decodeValue(in: MessagePackReader): Currency = Currency.getInstance(in.readString())

    def encodeValue(x: Currency, out: MessagePackWriter): Unit = out.writeString(x.getCurrencyCode)
  }

  val uuidCodec: MessagePackBinaryCodec[UUID] = new MessagePackBinaryCodec[UUID]() {
    def decodeValue(in: MessagePackReader): UUID = UUID.fromString(in.readString())

    def encodeValue(x: UUID, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  val binaryCodec: MessagePackBinaryCodec[Array[Byte]] = new MessagePackBinaryCodec[Array[Byte]]() {
    def decodeValue(in: MessagePackReader): Array[Byte] = in.readBinary()

    def encodeValue(x: Array[Byte], out: MessagePackWriter): Unit = out.writeBinary(x)
  }

  private val readerPool: ThreadLocal[MessagePackReader] = new ThreadLocal[MessagePackReader] {
    override def initialValue(): MessagePackReader = new MessagePackReader(null, 0, 0)
  }

  private val writerPool: ThreadLocal[MessagePackWriter] = new ThreadLocal[MessagePackWriter] {
    override def initialValue(): MessagePackWriter = new MessagePackWriter(new Array[Byte](32768), -1)
  }
}
