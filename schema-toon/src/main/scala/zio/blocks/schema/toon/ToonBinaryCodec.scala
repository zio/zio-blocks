package zio.blocks.schema.toon

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import zio.blocks.chunk.{Chunk, ChunkBuilder}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from TOON
 * formats. Encapsulates logic to map TOON representations into native type
 * structures and vice versa, handling serialization and deserialization as per
 * TOON encoding standards using UTF-8 character set.
 *
 * @param valueType
 *   Integer representing the type of the value for encoding/decoding. Default
 *   is set to `ToonBinaryCodec.objectType`.
 *
 * This class requires an implementation for two core operations: decoding a
 * value of type `A` from a TOON representation and encoding a value of type `A`
 * into a TOON representation.
 */
abstract class ToonBinaryCodec[A](val valueType: Int = ToonBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Computes the appropriate `RegisterOffset` based on the value type defined
   * in `ToonBinaryCodec`.
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
   * Attempts to decode a value of type `A` from the specified `ToonReader`, but
   * may fail with `ToonBinaryCodecError` error if the TOON input does not
   * encode a value of this type.
   *
   * @param in
   *   an instance of `ToonReader` which provides access to the TOON input to
   *   parse a TOON value to value of type `A`
   * @param default
   *   the placeholder value provided to initialize some possible local
   *   variables
   */
  def decodeValue(in: ToonReader, default: A): A

  /**
   * Encodes the specified value using provided `ToonWriter`, but may fail with
   * `ToonBinaryCodecError` if it cannot be encoded properly according to TOON
   * specification requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` which provides access to TOON output to
   *   serialize the specified value as a TOON value
   */
  def encodeValue(x: A, out: ToonWriter): Unit

  /**
   * Returns some value that will be passed as the default parameter in
   * `decodeValue`.
   */
  def nullValue: A = null.asInstanceOf[A]

  /**
   * Attempts to decode a value of type `A` from the specified `ToonReader`, but
   * may fail with `ToonBinaryCodecError` error if the TOON input is not a key
   * or does not encode a value of this type.
   *
   * @param in
   *   an instance of `ToonReader` which provides access to the TOON input to
   *   parse a TOON key to value of type `A`
   */
  def decodeKey(in: ToonReader): A = in.decodeError("decoding as TOON key is not supported")

  /**
   * Encodes the specified value using provided `ToonWriter` as a TOON key, but
   * may fail with `ToonBinaryCodecError` if it cannot be encoded properly
   * according to TOON specification requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` which provides access to TOON output to
   *   serialize the specified value as a TOON key
   */
  def encodeKey(x: A, out: ToonWriter): Unit = out.encodeError("encoding as TOON key is not supported")

  /**
   * Encodes the specified value as a field within a TOON record.
   *
   * @param fieldName
   *   the name of the field
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` which provides access to TOON output
   */
  def encodeAsField(fieldName: String, x: A, out: ToonWriter): Unit = {
    out.writeKey(fieldName)
    encodeValue(x, out)
    out.newLine()
  }

  /**
   * Decodes an inline array from comma-separated string values. Used for TOON's
   * compact inline array format: `xs[3]: 1,2,3`
   *
   * @param values
   *   array of string values parsed from the inline array
   * @param expectedLength
   *   the expected length from the array header
   */
  def decodeInlineArray(in: ToonReader, values: Array[String], expectedLength: Int): A =
    in.decodeError("decodeInlineArray not supported for this type")

  /**
   * Decodes a list-format array where each item is prefixed with `- `. Used for
   * non-uniform objects or complex nested structures.
   *
   * @param in
   *   the ToonReader positioned at the first list item line
   * @param expectedLength
   *   the expected number of items from the array header
   */
  def decodeListArray(in: ToonReader, expectedLength: Int): A =
    in.decodeError("decodeListArray not supported for this type")

  /**
   * Decodes a tabular array from field-value row data. Used for TOON's tabular
   * format: `items[2]{sku,qty,price}:` followed by rows.
   *
   * @param in
   *   the ToonReader positioned after the header line
   * @param fieldNames
   *   the field names from the header
   * @param expectedLength
   *   the expected number of rows
   * @param delimiter
   *   the delimiter to use for splitting rows
   */
  def decodeTabularArray(in: ToonReader, fieldNames: Array[String], expectedLength: Int, delimiter: Delimiter): A =
    in.decodeError("decodeTabularArray not supported for this type")

  /**
   * Decodes a single tabular row into a record value. Used when this codec's
   * type is an element in a tabular array.
   *
   * @param values
   *   the values parsed from the row (one per field)
   * @param fieldNames
   *   the field names from the tabular header
   */
  def decodeTabularRow(in: ToonReader, values: Array[String], fieldNames: Array[String]): A =
    in.decodeError("decodeTabularRow not supported for this type")

  /**
   * Returns the field names for this codec if it represents a record type. Used
   * for tabular array format encoding.
   *
   * @return
   *   Array of field names, or null if not a record type
   */
  def getFieldNames: Array[String] = null

  /**
   * Encodes a value as a single row in tabular format (values only, no keys).
   * Used when this codec's values are elements in a tabular array.
   *
   * @param x
   *   the value to encode
   * @param out
   *   the writer to output to
   * @param delimiter
   *   the delimiter to use between values
   */
  def encodeTabularRow(x: A, out: ToonWriter, delimiter: Delimiter): Unit =
    throw new ToonBinaryCodecError(Nil, "encodeTabularRow not supported for this type")

  /**
   * Returns true if this codec represents a record type. Used to determine if a
   * tabular format can be applied.
   */
  def isRecordCodec: Boolean = false

  /**
   * Returns true if this codec represents a primitive type (Int, Long, String,
   * etc.). Used to determine if an inline array format can be applied.
   */
  def isPrimitive: Boolean = false

  /**
   * Returns true if this is a record codec where all fields are primitives.
   * Used to determine if an array of these records can use a tabular format in
   * the list item context.
   */
  def hasOnlyPrimitiveFields: Boolean = false

  /**
   * Decodes a value of type `A` from the specified `ByteBuffer` using the
   * default configuration.
   *
   * @param input
   *   the byte buffer containing TOON input
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value to the given `ByteBuffer` using the default
   * configuration.
   *
   * @param value
   *   the value to encode
   * @param output
   *   the byte buffer to write TOON output to
   */
  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the specified `ByteBuffer` using the
   * provided configuration.
   *
   * @param input
   *   the byte buffer containing TOON input
   * @param config
   *   the reader configuration to use for parsing
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val reader             = ToonReader(config)
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
      try new Right(decodeValue(reader, nullValue))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    output.put(writer.toByteArrayTrimmed)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      val reader = ToonReader(config)
      reader.reset(input, 0, input.length)
      try new Right(decodeValue(reader, nullValue))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, config: WriterConfig): Array[Byte] = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    writer.toByteArrayTrimmed
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val bytes  = input.readAllBytes()
      val reader = ToonReader(config)
      reader.reset(bytes, 0, bytes.length)
      try new Right(decodeValue(reader, nullValue))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    writer.writeToTrimmed(output)
  }

  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encodeToString(value: A): String = encodeToString(value, WriterConfig)

  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val reader = ToonReader(config)
      reader.reset(input)
      try new Right(decodeValue(reader, nullValue))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encodeToString(value: A, config: WriterConfig): String = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    new String(writer.toByteArrayTrimmed, UTF_8)
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      ExpectationMismatch(
        error match {
          case e: ToonBinaryCodecError =>
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

object ToonBinaryCodec {
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

  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit](ToonBinaryCodec.unitType) {
    def decodeValue(in: ToonReader, default: Unit): Unit = {
      in.skipBlankLines()
      in.readNull()
    }

    def encodeValue(x: Unit, out: ToonWriter): Unit = out.writeNull()
  }
  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean](ToonBinaryCodec.booleanType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Boolean): Boolean = {
      in.skipBlankLines()
      in.readBoolean()
    }

    def encodeValue(x: Boolean, out: ToonWriter): Unit = out.writeBoolean(x)

    override def decodeKey(in: ToonReader): Boolean = in.readBoolean()

    override def encodeKey(x: Boolean, out: ToonWriter): Unit = out.writeBoolean(x)
  }
  val byteCodec: ToonBinaryCodec[Byte] = new ToonBinaryCodec[Byte](ToonBinaryCodec.byteType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Byte): Byte = {
      in.skipBlankLines()
      in.readByte()
    }

    def encodeValue(x: Byte, out: ToonWriter): Unit = out.writeInt(x.toInt)

    override def decodeKey(in: ToonReader): Byte = in.readInt().toByte

    override def encodeKey(x: Byte, out: ToonWriter): Unit = out.writeInt(x.toInt)
  }
  val shortCodec: ToonBinaryCodec[Short] = new ToonBinaryCodec[Short](ToonBinaryCodec.shortType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Short): Short = {
      in.skipBlankLines()
      in.readShort()
    }

    def encodeValue(x: Short, out: ToonWriter): Unit = out.writeInt(x.toInt)

    override def decodeKey(in: ToonReader): Short = in.readInt().toShort

    override def encodeKey(x: Short, out: ToonWriter): Unit = out.writeInt(x.toInt)
  }
  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int](ToonBinaryCodec.intType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Int): Int = {
      in.skipBlankLines()
      in.readInt()
    }

    def encodeValue(x: Int, out: ToonWriter): Unit = out.writeInt(x)

    override def decodeKey(in: ToonReader): Int = in.readInt()

    override def encodeKey(x: Int, out: ToonWriter): Unit = out.writeInt(x)
  }
  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long](ToonBinaryCodec.longType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Long): Long = {
      in.skipBlankLines()
      in.readLong()
    }

    def encodeValue(x: Long, out: ToonWriter): Unit = out.writeLong(x)

    override def decodeKey(in: ToonReader): Long = in.readLong()

    override def encodeKey(x: Long, out: ToonWriter): Unit = out.writeLong(x)
  }
  val floatCodec: ToonBinaryCodec[Float] = new ToonBinaryCodec[Float](ToonBinaryCodec.floatType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Float): Float = {
      in.skipBlankLines()
      in.readFloat()
    }

    def encodeValue(x: Float, out: ToonWriter): Unit = out.writeFloat(x)

    override def decodeKey(in: ToonReader): Float = in.readFloat()

    override def encodeKey(x: Float, out: ToonWriter): Unit = out.writeFloat(x)
  }
  val doubleCodec: ToonBinaryCodec[Double] = new ToonBinaryCodec[Double](ToonBinaryCodec.doubleType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Double): Double = {
      in.skipBlankLines()
      in.readDouble()
    }

    def encodeValue(x: Double, out: ToonWriter): Unit = out.writeDouble(x)

    override def decodeKey(in: ToonReader): Double = in.readDouble()

    override def encodeKey(x: Double, out: ToonWriter): Unit = out.writeDouble(x)
  }
  val charCodec: ToonBinaryCodec[Char] = new ToonBinaryCodec[Char](ToonBinaryCodec.charType) {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: Char): Char = {
      in.skipBlankLines()
      val s = in.readString()
      if (s.length == 1) s.charAt(0)
      else in.decodeError(s"Expected single char, got: $s")
    }

    def encodeValue(x: Char, out: ToonWriter): Unit = out.writeChar(x)

    override def decodeKey(in: ToonReader): Char = {
      val s = in.readString()
      if (s.length == 1) s.charAt(0)
      else in.decodeError(s"Expected single char, got: $s")
    }

    override def encodeKey(x: Char, out: ToonWriter): Unit = out.writeChar(x)
  }
  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: String): String = {
      in.skipBlankLines()
      in.readString()
    }

    def encodeValue(x: String, out: ToonWriter): Unit = out.writeString(x)

    override def decodeKey(in: ToonReader): String = in.readString()

    override def encodeKey(x: String, out: ToonWriter): Unit = out.writeString(x)
  }
  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: BigInt): BigInt = {
      in.skipBlankLines()
      in.readBigInt()
    }

    def encodeValue(x: BigInt, out: ToonWriter): Unit = out.writeBigInt(x)

    override def decodeKey(in: ToonReader): BigInt = in.readBigInt()

    override def encodeKey(x: BigInt, out: ToonWriter): Unit = out.writeBigInt(x)
  }
  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: BigDecimal): BigDecimal = {
      in.skipBlankLines()
      in.readBigDecimal()
    }

    def encodeValue(x: BigDecimal, out: ToonWriter): Unit = out.writeBigDecimal(x)

    override def decodeKey(in: ToonReader): BigDecimal = in.readBigDecimal()

    override def encodeKey(x: BigDecimal, out: ToonWriter): Unit = out.writeBigDecimal(x)
  }
  val dayOfWeekCodec: ToonBinaryCodec[DayOfWeek] = new ToonBinaryCodec[java.time.DayOfWeek]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.DayOfWeek): java.time.DayOfWeek = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.DayOfWeek.valueOf(s)
      catch { case _: IllegalArgumentException => in.decodeError(s"Invalid day of week: $s") }
    }

    def encodeValue(x: java.time.DayOfWeek, out: ToonWriter): Unit = out.writeString(x.name)
  }
  val durationCodec: ToonBinaryCodec[Duration] = new ToonBinaryCodec[java.time.Duration]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.Duration): java.time.Duration = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.Duration.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid duration: $s") }
    }

    def encodeValue(x: java.time.Duration, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val instantCodec: ToonBinaryCodec[Instant] = new ToonBinaryCodec[java.time.Instant]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.Instant): java.time.Instant = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.Instant.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid instant: $s") }
    }

    def encodeValue(x: java.time.Instant, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val localDateCodec: ToonBinaryCodec[LocalDate] = new ToonBinaryCodec[java.time.LocalDate]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.LocalDate): java.time.LocalDate = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.LocalDate.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid local date: $s") }
    }

    def encodeValue(x: java.time.LocalDate, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val localDateTimeCodec: ToonBinaryCodec[LocalDateTime] = new ToonBinaryCodec[java.time.LocalDateTime]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.LocalDateTime): java.time.LocalDateTime = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.LocalDateTime.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid local date time: $s") }
    }

    def encodeValue(x: java.time.LocalDateTime, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val localTimeCodec: ToonBinaryCodec[LocalTime] = new ToonBinaryCodec[java.time.LocalTime]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.LocalTime): java.time.LocalTime = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.LocalTime.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid local time: $s") }
    }

    def encodeValue(x: java.time.LocalTime, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val monthCodec: ToonBinaryCodec[Month] = new ToonBinaryCodec[java.time.Month]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.Month): java.time.Month = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.Month.valueOf(s)
      catch { case _: IllegalArgumentException => in.decodeError(s"Invalid month: $s") }
    }

    def encodeValue(x: java.time.Month, out: ToonWriter): Unit = out.writeString(x.name)
  }
  val monthDayCodec: ToonBinaryCodec[MonthDay] = new ToonBinaryCodec[java.time.MonthDay]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.MonthDay): java.time.MonthDay = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.MonthDay.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid month-day: $s") }
    }

    def encodeValue(x: java.time.MonthDay, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val offsetDateTimeCodec: ToonBinaryCodec[OffsetDateTime] = new ToonBinaryCodec[java.time.OffsetDateTime]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.OffsetDateTime.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid offset date time: $s") }
    }

    def encodeValue(x: java.time.OffsetDateTime, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val offsetTimeCodec: ToonBinaryCodec[OffsetTime] = new ToonBinaryCodec[java.time.OffsetTime]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.OffsetTime): java.time.OffsetTime = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.OffsetTime.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid offset time: $s") }
    }

    def encodeValue(x: java.time.OffsetTime, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val periodCodec: ToonBinaryCodec[Period] = new ToonBinaryCodec[java.time.Period]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.Period): java.time.Period = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.Period.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid period: $s") }
    }

    def encodeValue(x: java.time.Period, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val yearCodec: ToonBinaryCodec[Year] = new ToonBinaryCodec[java.time.Year]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.Year): java.time.Year = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.Year.of(s.toInt)
      catch {
        case _: NumberFormatException =>
          try java.time.Year.parse(s)
          catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid year: $s") }
      }
    }

    def encodeValue(x: java.time.Year, out: ToonWriter): Unit = out.writeInt(x.getValue)
  }
  val yearMonthCodec: ToonBinaryCodec[YearMonth] = new ToonBinaryCodec[java.time.YearMonth]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.YearMonth): java.time.YearMonth = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.YearMonth.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid year-month: $s") }
    }

    def encodeValue(x: java.time.YearMonth, out: ToonWriter): Unit = out.writeString {
      var s = x.toString
      if (x.getYear >= 10000) s = "+" + s
      s
    }
  }
  val zoneIdCodec: ToonBinaryCodec[ZoneId] = new ToonBinaryCodec[java.time.ZoneId]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.ZoneId): java.time.ZoneId = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.ZoneId.of(s)
      catch { case _: java.time.DateTimeException => in.decodeError(s"Invalid zone id: $s") }
    }

    def encodeValue(x: java.time.ZoneId, out: ToonWriter): Unit = out.writeString(x.getId)
  }
  val zoneOffsetCodec: ToonBinaryCodec[ZoneOffset] = new ToonBinaryCodec[java.time.ZoneOffset]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.ZoneOffset): java.time.ZoneOffset = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.ZoneOffset.of(s)
      catch { case _: java.time.DateTimeException => in.decodeError(s"Invalid zone offset: $s") }
    }

    def encodeValue(x: java.time.ZoneOffset, out: ToonWriter): Unit = out.writeString(x.getId)
  }
  val zonedDateTimeCodec: ToonBinaryCodec[ZonedDateTime] = new ToonBinaryCodec[java.time.ZonedDateTime]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime = {
      in.skipBlankLines()
      val s = in.readString()
      try java.time.ZonedDateTime.parse(s)
      catch { case _: java.time.format.DateTimeParseException => in.decodeError(s"Invalid zoned date time: $s") }
    }

    def encodeValue(x: java.time.ZonedDateTime, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val currencyCodec: ToonBinaryCodec[Currency] = new ToonBinaryCodec[java.util.Currency]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.util.Currency): java.util.Currency = {
      in.skipBlankLines()
      val s = in.readString()
      try java.util.Currency.getInstance(s)
      catch { case _: Exception => in.decodeError(s"Invalid currency: $s") }
    }

    def encodeValue(x: java.util.Currency, out: ToonWriter): Unit = out.writeString(x.getCurrencyCode)
  }
  val uuidCodec: ToonBinaryCodec[UUID] = new ToonBinaryCodec[java.util.UUID]() {
    override def isPrimitive: Boolean = true

    def decodeValue(in: ToonReader, default: java.util.UUID): java.util.UUID = {
      in.skipBlankLines()
      val s = in.readString()
      try java.util.UUID.fromString(s)
      catch { case _: IllegalArgumentException => in.decodeError(s"Invalid UUID: $s") }
    }

    def encodeValue(x: java.util.UUID, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val dynamicValueCodec: ToonBinaryCodec[DynamicValue] = new ToonBinaryCodec[DynamicValue]() {
    private[this] val falseValue = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
    private[this] val trueValue  = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
    private[this] val unitValue  = DynamicValue.Primitive(PrimitiveValue.Unit)

    private def encodePrimitive(p: PrimitiveValue, out: ToonWriter): Unit = p match {
      case _: PrimitiveValue.Unit.type      => out.writeNull()
      case v: PrimitiveValue.Boolean        => out.writeBoolean(v.value)
      case v: PrimitiveValue.Byte           => out.writeInt(v.value.toInt)
      case v: PrimitiveValue.Short          => out.writeInt(v.value.toInt)
      case v: PrimitiveValue.Int            => out.writeInt(v.value)
      case v: PrimitiveValue.Long           => out.writeLong(v.value)
      case v: PrimitiveValue.Float          => out.writeFloat(v.value)
      case v: PrimitiveValue.Double         => out.writeDouble(v.value)
      case v: PrimitiveValue.Char           => out.writeString(v.value.toString)
      case v: PrimitiveValue.String         => out.writeString(v.value)
      case v: PrimitiveValue.BigInt         => out.writeBigInt(v.value)
      case v: PrimitiveValue.BigDecimal     => out.writeBigDecimal(v.value)
      case v: PrimitiveValue.DayOfWeek      => out.writeString(v.value.name)
      case v: PrimitiveValue.Duration       => out.writeString(v.value.toString)
      case v: PrimitiveValue.Instant        => out.writeString(v.value.toString)
      case v: PrimitiveValue.LocalDate      => out.writeString(v.value.toString)
      case v: PrimitiveValue.LocalDateTime  => out.writeString(v.value.toString)
      case v: PrimitiveValue.LocalTime      => out.writeString(v.value.toString)
      case v: PrimitiveValue.Month          => out.writeString(v.value.name)
      case v: PrimitiveValue.MonthDay       => out.writeString(v.value.toString)
      case v: PrimitiveValue.OffsetDateTime => out.writeString(v.value.toString)
      case v: PrimitiveValue.OffsetTime     => out.writeString(v.value.toString)
      case v: PrimitiveValue.Period         => out.writeString(v.value.toString)
      case v: PrimitiveValue.Year           => out.writeString(v.value.toString)
      case v: PrimitiveValue.YearMonth      => out.writeString(v.value.toString)
      case v: PrimitiveValue.ZoneId         => out.writeString(v.value.getId)
      case v: PrimitiveValue.ZoneOffset     => out.writeString(v.value.getId)
      case v: PrimitiveValue.ZonedDateTime  => out.writeString(v.value.toString)
      case v: PrimitiveValue.Currency       => out.writeString(v.value.getCurrencyCode)
      case v: PrimitiveValue.UUID           => out.writeString(v.value.toString)
    }

    private def inferredToDynamicValue(inferred: Any, rawString: String): DynamicValue =
      inferred match {
        case null       => unitValue
        case b: Boolean => if (b) trueValue else falseValue
        case l: Long    =>
          val intVal = l.toInt
          if (l == intVal) DynamicValue.Primitive(PrimitiveValue.Int(intVal))
          else DynamicValue.Primitive(PrimitiveValue.Long(l))
        case d: Double  => DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(d)))
        case bi: BigInt => DynamicValue.Primitive(PrimitiveValue.BigInt(bi))
        case s: String  => DynamicValue.Primitive(PrimitiveValue.String(s))
        case _          => DynamicValue.Primitive(PrimitiveValue.String(rawString))
      }

    private def parseLengthAndDelimiter(bracketContent: String): (Int, Delimiter) =
      if (bracketContent.isEmpty) (0, Delimiter.Comma)
      else if (bracketContent.endsWith("\t")) (bracketContent.dropRight(1).trim.toInt, Delimiter.Tab)
      else if (bracketContent.endsWith("|")) (bracketContent.dropRight(1).trim.toInt, Delimiter.Pipe)
      else (bracketContent.trim.toInt, Delimiter.Comma)

    private def parseFieldNames(content: String, delim: Delimiter): Array[String] = {
      val fieldSeparator = delim match {
        case Delimiter.Pipe => "\\|"
        case Delimiter.Tab  => "\t"
        case _              => ","
      }
      content.split(fieldSeparator).map(_.trim)
    }

    private def decodeTabularRows(
      in: ToonReader,
      fields: Array[String],
      length: Int,
      startDepth: Int,
      builder: ChunkBuilder[DynamicValue]
    ): Unit = {
      var count = 0
      while (count < length && in.hasMoreLines && in.getDepth >= startDepth) {
        in.skipBlankLinesInArray(count == 0)
        if (in.hasMoreLines && in.getDepth >= startDepth) {
          val values = in.readInlineArray()
          val record = ChunkBuilder.make[(String, DynamicValue)]()
          var i      = 0
          while (i < fields.length && i < values.length) {
            record.addOne((fields(i), inferredToDynamicValue(in.inferType(values(i)), values(i))))
            i += 1
          }
          builder.addOne(DynamicValue.Record(record.result()))
          count += 1
        }
      }
    }

    private def decodeInlinePrimitives(
      in: ToonReader,
      values: Array[String],
      builder: ChunkBuilder[DynamicValue]
    ): Unit = {
      var i = 0
      while (i < values.length) {
        builder.addOne(inferredToDynamicValue(in.inferType(values(i)), values(i)))
        i += 1
      }
    }

    private def decodeListItems(
      in: ToonReader,
      length: Int,
      startDepth: Int,
      builder: ChunkBuilder[DynamicValue]
    ): Unit = {
      var count = 0
      while (count < length && in.hasMoreLines && in.getDepth >= startDepth) {
        in.skipBlankLinesInArray(count == 0)
        if (in.hasMoreLines && in.getDepth >= startDepth && in.isListItem) {
          in.consumeListItemMarker()
          builder.addOne(decodeValue(in, unitValue))
          count += 1
        } else if (in.hasMoreLines && in.getDepth >= startDepth) count = length
      }
    }

    def decodeValue(in: ToonReader, default: DynamicValue): DynamicValue = {
      in.skipBlankLines()
      if (!in.hasMoreLines) return DynamicValue.Record.empty
      val content = in.peekTrimmedContent
      if (content.isEmpty) {
        val currentDepth = in.getDepth
        in.advanceLine()
        in.skipBlankLines()
        if (in.hasMoreLines && in.getDepth > currentDepth) {
          return decodeValue(in, default)
        }
        return DynamicValue.Record.empty
      }
      if (content.startsWith("[")) {
        return decodeRootArray(in)
      }
      val inferred = in.inferType(content)
      inferred match {
        case null =>
          in.advanceLine()
          unitValue
        case b: Boolean =>
          in.advanceLine()
          if (b) trueValue
          else falseValue
        case l: Long =>
          in.advanceLine()
          val intVal = l.toInt
          if (l == intVal) DynamicValue.Primitive(PrimitiveValue.Int(intVal))
          else DynamicValue.Primitive(PrimitiveValue.Long(l))
        case d: Double =>
          in.advanceLine()
          DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(d)))
        case bi: BigInt =>
          in.advanceLine()
          DynamicValue.Primitive(PrimitiveValue.BigInt(bi))
        case s: String =>
          if (content.indexOf(':') >= 0) decodeRecordFields(in)
          else { in.advanceLine(); DynamicValue.Primitive(PrimitiveValue.String(s)) }
        case _ =>
          in.advanceLine()
          DynamicValue.Primitive(PrimitiveValue.String(content))
      }
    }

    private def decodeRecordFields(in: ToonReader): DynamicValue = {
      val builder    = ChunkBuilder.make[((String, Boolean), DynamicValue)]()
      val startDepth = in.getDepth
      in.skipBlankLines()
      while (in.hasMoreLines && in.getDepth >= startDepth && !in.isListItem) {
        val (rawKey, wasQuoted) = in.readKeyWithQuoteInfo()
        val bracketStart        = rawKey.indexOf('[')
        val hasArrayNotation    = bracketStart >= 0 && rawKey.indexOf(']', bracketStart) > bracketStart
        val (fieldName, value)  = if (wasQuoted && bracketStart > 0) {
          val quotedPart = rawKey.substring(0, bracketStart)
          val name       =
            if (quotedPart.startsWith("\"") && quotedPart.endsWith("\""))
              quotedPart.substring(1, quotedPart.length - 1)
            else quotedPart
          (name, decodeArrayFieldValue(in, rawKey.substring(bracketStart)))
        } else if (!wasQuoted && hasArrayNotation) {
          val name = if (bracketStart > 0) rawKey.substring(0, bracketStart) else rawKey
          (name, decodeArrayFieldValue(in, rawKey))
        } else {
          (rawKey, decodeValue(in, unitValue))
        }
        builder.addOne(((fieldName, wasQuoted), value))
        in.skipBlankLines()
      }
      val fieldsWithQuoteInfo = builder.result()
      in.expandPaths match {
        case PathExpansion.Safe => expandAndMergeFieldsWithQuoteInfo(in, fieldsWithQuoteInfo)
        case PathExpansion.Off  =>
          val record = DynamicValue.Record(fieldsWithQuoteInfo.map { case ((k, _), v) => (k, v) })
          maybeExtractVariant(record, in)
      }
    }

    private def expandAndMergeFieldsWithQuoteInfo(in: ToonReader, fields: Chunk[((String, Boolean), DynamicValue)]) = {
      val keyMap = scala.collection.mutable.LinkedHashMap.empty[String, DynamicValue]
      fields.foreach { case ((key, wasQuoted), value) =>
        val (expandedKey, expandedValue) =
          if (wasQuoted) (key, value) // Quoted keys are preserved as literals
          else expandDottedKey(key, value)
        mergeIntoMap(in, keyMap, expandedKey, expandedValue)
      }
      val result = ChunkBuilder.make[(String, DynamicValue)]()
      keyMap.foreach(result.addOne)
      maybeExtractVariant(DynamicValue.Record(result.result()), in)
    }

    private def maybeExtractVariant(record: DynamicValue.Record, in: ToonReader): DynamicValue =
      in.discriminatorField match {
        case Some(fieldName) =>
          record.fields.find(_._1 == fieldName) match {
            case Some((_, DynamicValue.Primitive(PrimitiveValue.String(caseName)))) =>
              val otherFields = record.fields.filterNot(_._1 == fieldName)
              otherFields match {
                case Chunk(("value", innerValue)) =>
                  DynamicValue.Variant(caseName, innerValue)
                case _ =>
                  DynamicValue.Variant(caseName, DynamicValue.Record(otherFields))
              }
            case _ => record
          }
        case None => record
      }

    private def expandDottedKey(key: String, value: DynamicValue): (String, DynamicValue) =
      if (key.indexOf('.') < 0) (key, value)
      else {
        val segments = key.split('.').toList
        if (segments.forall(ToonWriter.isIdentifierSegment)) {
          val nested = segments.tail.foldRight(value) { (segment, acc) =>
            DynamicValue.Record(Chunk((segment, acc)))
          }
          (segments.head, nested)
        } else (key, value)
      }

    private def mergeIntoMap(
      in: ToonReader,
      map: scala.collection.mutable.LinkedHashMap[String, DynamicValue],
      key: String,
      value: DynamicValue
    ): Unit =
      map.get(key) match {
        case None           => map.put(key, value)
        case Some(existing) =>
          (existing, value) match {
            case (existingRecord: DynamicValue.Record, newRecord: DynamicValue.Record) =>
              val merged = deepMergeRecords(existingRecord, newRecord, in)
              map.put(key, merged)
            case _ =>
              // A conflict is when we would overwrite a non-record with a different value
              if (in.isStrict) {
                in.decodeError(
                  s"Path expansion conflict at key '$key': cannot overwrite existing value with new value in strict mode"
                )
              }
              map.put(key, value)
          }
      }

    private def deepMergeRecords(
      existing: DynamicValue.Record,
      incoming: DynamicValue.Record,
      in: ToonReader
    ): DynamicValue.Record = {
      val mergedMap = scala.collection.mutable.LinkedHashMap.empty[String, DynamicValue]
      existing.fields.foreach { case (k, v) => mergedMap.put(k, v) }
      incoming.fields.foreach { case (k, v) => mergeIntoMap(in, mergedMap, k, v) }
      val result = ChunkBuilder.make[(String, DynamicValue)]()
      mergedMap.foreach(result.addOne)
      DynamicValue.Record(result.result())
    }

    private def decodeRootArray(in: ToonReader): DynamicValue.Sequence = {
      val content      = in.peekTrimmedContent
      val bracketStart = content.indexOf('[')
      val bracketEnd   = content.indexOf(']', bracketStart)
      if (bracketStart < 0 || bracketEnd < 0) {
        in.decodeError("Expected array header with []")
      }
      val bracketContent        = content.substring(bracketStart + 1, bracketEnd)
      val (length, delim)       = parseLengthAndDelimiter(bracketContent)
      var afterBracket          = bracketEnd + 1
      var fields: Array[String] = null
      if (afterBracket < content.length && content.charAt(afterBracket) == '{') {
        val braceEnd = content.indexOf('}', afterBracket)
        if (braceEnd < 0) in.decodeError("Expected closing } in field list")
        fields = parseFieldNames(content.substring(afterBracket + 1, braceEnd), delim)
        afterBracket = braceEnd + 1
      }
      if (afterBracket >= content.length || content.charAt(afterBracket) != ':') {
        in.decodeError("Expected : after array header")
      }
      val inlineContent = content.substring(afterBracket + 1).trim
      in.advanceLine()
      in.setActiveDelimiter(delim)
      val builder    = ChunkBuilder.make[DynamicValue]()
      val startDepth = in.getDepth
      if (fields != null && fields.nonEmpty) {
        in.skipBlankLinesInArray(true)
        decodeTabularRows(in, fields, length, startDepth, builder)
      } else if (inlineContent.nonEmpty) {
        decodeInlinePrimitives(in, splitInlineValues(inlineContent, delim), builder)
      } else {
        in.skipBlankLinesInArray(true)
        decodeListItems(in, length, startDepth, builder)
      }
      DynamicValue.Sequence(builder.result())
    }

    private def splitInlineValues(s: String, delim: Delimiter): Array[String] = {
      val result  = new scala.collection.mutable.ArrayBuffer[String]()
      var start   = 0
      var inQuote = false
      var i       = 0
      while (i < s.length) {
        val c = s.charAt(i)
        if (c == '"') inQuote = !inQuote
        else if (!inQuote && c == delim.char) {
          result.addOne(s.substring(start, i).trim)
          start = i + 1
        }
        i += 1
      }
      if (start <= s.length) result.addOne(s.substring(start).trim)
      result.toArray
    }

    private def decodeArrayFieldValue(in: ToonReader, rawKey: String): DynamicValue.Sequence = {
      val bracketStart          = rawKey.indexOf('[')
      val bracketEnd            = rawKey.indexOf(']', bracketStart)
      val bracketContent        = rawKey.substring(bracketStart + 1, bracketEnd)
      val (length, delim)       = parseLengthAndDelimiter(bracketContent)
      var fields: Array[String] = null
      val braceStart            = rawKey.indexOf('{', bracketEnd)
      val braceEnd              = rawKey.indexOf('}', braceStart)
      if (braceStart > 0 && braceEnd > braceStart) {
        fields = parseFieldNames(rawKey.substring(braceStart + 1, braceEnd), delim)
      }
      val builder    = ChunkBuilder.make[DynamicValue]()
      val startDepth = in.getDepth
      in.setActiveDelimiter(delim)
      if (fields != null && fields.nonEmpty) {
        in.advanceLine()
        in.skipBlankLinesInArray(true)
        decodeTabularRows(in, fields, length, startDepth, builder)
      } else {
        val remaining = in.peekTrimmedContent
        if (remaining.nonEmpty) {
          val values = splitInlineValues(remaining, delim)
          in.advanceLine()
          decodeInlinePrimitives(in, values, builder)
        } else {
          in.advanceLine()
          in.skipBlankLinesInArray(true)
          decodeListItems(in, length, startDepth, builder)
        }
      }
      DynamicValue.Sequence(builder.result())
    }

    def encodeValue(x: DynamicValue, out: ToonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive => encodePrimitive(primitive.value, out)
      case record: DynamicValue.Record       =>
        out.keyFolding match {
          case KeyFolding.Safe =>
            val rootLiteralDottedKeys = record.fields.map(_._1).filter(_.indexOf('.') >= 0).toSet
            encodeRecordWithFolding(record, out, out.flattenDepth, "", rootLiteralDottedKeys)
          case KeyFolding.Off =>
            val fields = record.fields
            val it     = fields.iterator
            while (it.hasNext) {
              val (key, value) = it.next()
              encodeRecordPlain(key, value, out)
            }
        }
      case variant: DynamicValue.Variant =>
        out.discriminatorField match {
          case Some(fieldName) => encodeVariantWithDiscriminator(variant, fieldName, out)
          case None            => encodeRecordPlain(variant.caseNameValue, variant.value, out)
        }
      case sequence: DynamicValue.Sequence =>
        val elements = sequence.elements
        if (elements.isEmpty) {
          out.writeArrayHeader(0)
        } else {
          classifySequence(elements) match {
            case ArrayClassification.Primitives =>
              out.writeArrayHeaderInline(null, elements.size)
              out.enterInlineContext()
              var first = true
              val it    = elements.iterator
              while (it.hasNext) {
                if (!first) out.writeDelimiter()
                first = false
                encodePrimitiveInline(it.next(), out)
              }
              out.exitInlineContext()
            case ArrayClassification.UniformRecords(keys) =>
              out.writeTabularHeader(elements.size, keys)
              out.newLine()
              out.incrementDepth()
              val it = elements.iterator
              while (it.hasNext) {
                out.ensureIndent()
                encodeTabularRow(it.next().asInstanceOf[DynamicValue.Record], keys, out)
                out.newLine()
              }
              out.decrementDepth()
            case ArrayClassification.Mixed =>
              out.writeArrayHeader(elements.size)
              out.newLine()
              out.incrementDepth()
              val it = elements.iterator
              while (it.hasNext) {
                encodeListItem(it.next(), out)
              }
              out.decrementDepth()
          }
        }
      case map: DynamicValue.Map =>
        val entries = map.entries
        val it      = entries.iterator
        while (it.hasNext) {
          val (k, v) = it.next()
          k match {
            case DynamicValue.Primitive(PrimitiveValue.String(keyStr)) =>
              encodeRecordPlain(keyStr, v, out)
            case _ =>
              out.ensureIndent()
              encodeValue(k, out)
              out.writeColonSpace()
              encodeValue(v, out)
              out.newLine()
          }
        }
      case DynamicValue.Null =>
        out.writeNull()
    }

    private def encodeVariantWithDiscriminator(
      variant: DynamicValue.Variant,
      fieldName: String,
      out: ToonWriter
    ): Unit =
      variant.value match {
        case record: DynamicValue.Record =>
          out.writeKey(fieldName)
          out.writeString(variant.caseNameValue)
          out.newLine()
          val it = record.fields.iterator
          while (it.hasNext) {
            val (k, v) = it.next()
            encodeRecordPlain(k, v, out)
          }
        case other =>
          out.writeKey(fieldName)
          out.writeString(variant.caseNameValue)
          out.newLine()
          out.writeKey("value")
          encodeValue(other, out)
          out.newLine()
      }

    private def encodeRecordPlain(key: String, value: DynamicValue, out: ToonWriter): Unit =
      value match {
        case nestedRecord: DynamicValue.Record if nestedRecord.fields.nonEmpty =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          val fields = nestedRecord.fields
          val it     = fields.iterator
          while (it.hasNext) {
            val (k, v) = it.next()
            encodeRecordPlain(k, v, out)
          }
          out.decrementDepth()
        case _: DynamicValue.Record =>
          out.writeKeyOnly(key)
        case variant: DynamicValue.Variant =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          out.discriminatorField match {
            case Some(fieldName) => encodeVariantWithDiscriminator(variant, fieldName, out)
            case None            => encodeRecordPlain(variant.caseNameValue, variant.value, out)
          }
          out.decrementDepth()
        case map: DynamicValue.Map if map.entries.nonEmpty =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          val entries = map.entries
          val it      = entries.iterator
          while (it.hasNext) {
            val (k, v) = it.next()
            k match {
              case DynamicValue.Primitive(PrimitiveValue.String(keyStr)) =>
                encodeRecordPlain(keyStr, v, out)
              case _ =>
                out.ensureIndent()
                encodeValue(k, out)
                out.writeColonSpace()
                encodeValue(v, out)
                out.newLine()
            }
          }
          out.decrementDepth()
        case _: DynamicValue.Map =>
          out.writeKeyOnly(key)
        case seq: DynamicValue.Sequence =>
          out.ensureIndent()
          encodeSequenceField(key, seq, out)
        case _ =>
          out.writeKey(key)
          encodeValue(value, out)
          out.newLine()
      }

    private def encodeRecordWithFolding(
      record: DynamicValue.Record,
      out: ToonWriter,
      remainingDepth: Int,
      ancestorPath: String,
      rootLiteralDottedKeys: Set[String]
    ): Unit = {
      val fields       = record.fields
      val topLevelKeys = fields.map(_._1).toSet
      val it           = fields.iterator
      while (it.hasNext) {
        val (key, value) = it.next()
        if (remainingDepth > 1 && ToonWriter.isIdentifierSegment(key)) {
          val (foldedKey, leafValue, depth) = collectFoldableChain(key, value, remainingDepth)
          val firstSegment                  = foldedKey.split('.')(0)
          val siblingCollision              = foldedKey.indexOf('.') >= 0 && topLevelKeys.exists {
            val prefix = firstSegment + '.'
            otherKey => otherKey != key && otherKey.startsWith(prefix)
          }
          val fullFoldedPath =
            if (ancestorPath.isEmpty) foldedKey
            else ancestorPath + '.' + foldedKey
          val ancestorCollision = rootLiteralDottedKeys.exists {
            val prefix1 = fullFoldedPath + '.'
            val prefix2 = fullFoldedPath + '.'
            literalKey =>
              literalKey.startsWith(prefix1) || literalKey == fullFoldedPath || fullFoldedPath.startsWith(prefix2)
          }
          if (siblingCollision || ancestorCollision || depth == 1) {
            writeFieldWithNesting(key, value, out, remainingDepth, ancestorPath, rootLiteralDottedKeys)
          } else {
            leafValue match {
              case nestedRecord: DynamicValue.Record if nestedRecord.fields.nonEmpty =>
                out.writeKeyOnly(foldedKey)
                out.incrementDepth()
                encodeRecordWithFolding(
                  nestedRecord,
                  out,
                  remainingDepth - depth,
                  fullFoldedPath,
                  rootLiteralDottedKeys
                )
                out.decrementDepth()
              case _: DynamicValue.Record =>
                out.writeKeyOnly(foldedKey)
              case variant: DynamicValue.Variant =>
                out.writeKeyOnly(foldedKey)
                out.incrementDepth()
                writeFieldWithNesting(
                  variant.caseNameValue,
                  variant.value,
                  out,
                  remainingDepth - depth,
                  fullFoldedPath,
                  rootLiteralDottedKeys
                )
                out.decrementDepth()
              case map: DynamicValue.Map if map.entries.nonEmpty =>
                out.writeKeyOnly(foldedKey)
                out.incrementDepth()
                val entries = map.entries
                val it      = entries.iterator
                while (it.hasNext) {
                  val (k, v) = it.next()
                  k match {
                    case DynamicValue.Primitive(PrimitiveValue.String(keyStr)) =>
                      writeFieldWithNesting(
                        keyStr,
                        v,
                        out,
                        remainingDepth - depth,
                        fullFoldedPath,
                        rootLiteralDottedKeys
                      )
                    case _ =>
                      out.ensureIndent()
                      encodeValue(k, out)
                      out.writeColonSpace()
                      encodeValue(v, out)
                      out.newLine()
                  }
                }
                out.decrementDepth()
              case _: DynamicValue.Map =>
                out.writeKeyOnly(foldedKey)
              case seq: DynamicValue.Sequence =>
                out.ensureIndent()
                encodeSequenceField(foldedKey, seq, out)
              case _ =>
                out.writeKey(foldedKey)
                encodeValue(leafValue, out)
                out.newLine()
            }
          }
        } else writeFieldWithNesting(key, value, out, remainingDepth, ancestorPath, rootLiteralDottedKeys)
      }
    }

    private def writeFieldWithNesting(
      key: String,
      value: DynamicValue,
      out: ToonWriter,
      remainingDepth: Int,
      ancestorPath: String,
      rootLiteralDottedKeys: Set[String]
    ): Unit = {
      val newAncestorPath =
        if (ancestorPath.isEmpty) key
        else ancestorPath + '.' + key
      value match {
        case nestedRecord: DynamicValue.Record if nestedRecord.fields.nonEmpty =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          encodeRecordWithFolding(nestedRecord, out, remainingDepth - 1, newAncestorPath, rootLiteralDottedKeys)
          out.decrementDepth()
        case _: DynamicValue.Record =>
          out.writeKeyOnly(key)
        case variant: DynamicValue.Variant =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          writeFieldWithNesting(
            variant.caseNameValue,
            variant.value,
            out,
            remainingDepth - 1,
            newAncestorPath,
            rootLiteralDottedKeys
          )
          out.decrementDepth()
        case map: DynamicValue.Map if map.entries.nonEmpty =>
          out.writeKeyOnly(key)
          out.incrementDepth()
          val entries = map.entries
          val it      = entries.iterator
          while (it.hasNext) {
            val (k, v) = it.next()
            k match {
              case DynamicValue.Primitive(PrimitiveValue.String(keyStr)) =>
                writeFieldWithNesting(keyStr, v, out, remainingDepth - 1, newAncestorPath, rootLiteralDottedKeys)
              case _ =>
                out.ensureIndent()
                encodeValue(k, out)
                out.writeColonSpace()
                encodeValue(v, out)
                out.newLine()
            }
          }
          out.decrementDepth()
        case _: DynamicValue.Map =>
          out.writeKeyOnly(key)
        case seq: DynamicValue.Sequence =>
          out.ensureIndent()
          encodeSequenceField(key, seq, out)
        case _ =>
          out.writeKey(key)
          encodeValue(value, out)
          out.newLine()
      }
    }

    private def collectFoldableChain(
      key: String,
      value: DynamicValue,
      maxDepth: Int
    ): (String, DynamicValue, Int) =
      if (maxDepth <= 1) {
        (key, value, 1)
      } else {
        value match {
          case record: DynamicValue.Record if record.fields.size == 1 =>
            val (nestedKey, nestedValue) = record.fields.head
            if (ToonWriter.isIdentifierSegment(nestedKey)) {
              val (restKey, leafValue, depth) = collectFoldableChain(nestedKey, nestedValue, maxDepth - 1)
              (key + '.' + restKey, leafValue, depth + 1)
            } else {
              (key, value, 1)
            }
          case _ =>
            (key, value, 1)
        }
      }

    private sealed trait ArrayClassification
    private object ArrayClassification {
      case object Primitives                         extends ArrayClassification
      case class UniformRecords(keys: Chunk[String]) extends ArrayClassification
      case object Mixed                              extends ArrayClassification
    }

    private def classifySequence(elements: Chunk[DynamicValue]): ArrayClassification = {
      var allPrimitives                  = true
      var allRecords                     = true
      var firstRecordKeys: Chunk[String] = null

      val it = elements.iterator
      while (it.hasNext && (allPrimitives || allRecords)) {
        it.next() match {
          case _: DynamicValue.Primitive =>
            allRecords = false
          case r: DynamicValue.Record =>
            allPrimitives = false
            val keys = r.fields.map(_._1)
            if (firstRecordKeys == null) {
              firstRecordKeys = keys
            } else if (keys != firstRecordKeys) {
              allRecords = false
            }
            if (allRecords && !r.fields.forall { case (_, v) => v.isInstanceOf[DynamicValue.Primitive] }) {
              allRecords = false
            }
          case _ =>
            allPrimitives = false
            allRecords = false
        }
      }

      if (allPrimitives) ArrayClassification.Primitives
      else if (allRecords && firstRecordKeys != null) ArrayClassification.UniformRecords(firstRecordKeys)
      else ArrayClassification.Mixed
    }

    private def encodePrimitiveInline(value: DynamicValue, out: ToonWriter): Unit = value match {
      case primitive: DynamicValue.Primitive => encodePrimitive(primitive.value, out)
      case _                                 => out.writeString(value.toString)
    }

    private def encodeTabularRow(record: DynamicValue.Record, keys: Chunk[String], out: ToonWriter): Unit = {
      val fieldMap = record.fields.toMap
      var first    = true
      val it       = keys.iterator
      while (it.hasNext) {
        if (!first) out.writeDelimiter()
        first = false
        fieldMap.get(it.next()) match {
          case Some(value) => encodePrimitiveInline(value, out)
          case None        => out.writeNull()
        }
      }
    }

    private def encodeListItem(element: DynamicValue, out: ToonWriter): Unit = element match {
      case record: DynamicValue.Record if record.fields.nonEmpty =>
        out.writeListItemMarker()
        out.enterListItemContext()
        val fields = record.fields
        val it     = fields.iterator
        var first  = true
        out.incrementDepth()
        while (it.hasNext) {
          val (key, value) = it.next()
          if (first) {
            first = false
          } else {
            out.ensureIndent()
          }
          encodeRecordPlain(key, value, out)
        }
        out.decrementDepth()
        out.exitListItemContext()
      case _: DynamicValue.Record =>
        out.writeListItemMarker()
        out.newLine()
      case _ =>
        out.writeListItemMarker()
        encodeValue(element, out)
        out.newLine()
    }

    private def encodeSequenceField(key: String, seq: DynamicValue.Sequence, out: ToonWriter): Unit = {
      val elements = seq.elements
      if (elements.isEmpty) {
        out.writeArrayHeader(key, 0)
        out.newLine()
      } else {
        classifySequence(elements) match {
          case ArrayClassification.Primitives =>
            out.writeArrayHeaderInline(key, elements.size)
            out.enterInlineContext()
            var first = true
            val it    = elements.iterator
            while (it.hasNext) {
              if (!first) out.writeDelimiter()
              first = false
              encodePrimitiveInline(it.next(), out)
            }
            out.exitInlineContext()
            out.newLine()
          case ArrayClassification.UniformRecords(keys) =>
            out.writeArrayHeader(key, elements.size, keys.toArray)
            out.newLine()
            out.incrementDepth()
            val it = elements.iterator
            while (it.hasNext) {
              out.ensureIndent()
              encodeTabularRow(it.next().asInstanceOf[DynamicValue.Record], keys, out)
              out.newLine()
            }
            out.decrementDepth()
          case ArrayClassification.Mixed =>
            out.writeArrayHeader(key, elements.size)
            out.newLine()
            out.incrementDepth()
            val it = elements.iterator
            while (it.hasNext) {
              encodeListItem(it.next(), out)
            }
            out.decrementDepth()
        }
      }
    }

    override def encodeKey(x: DynamicValue, out: ToonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case v: PrimitiveValue.Boolean    => out.writeBoolean(v.value)
          case v: PrimitiveValue.Byte       => out.writeInt(v.value.toInt)
          case v: PrimitiveValue.Short      => out.writeInt(v.value.toInt)
          case v: PrimitiveValue.Int        => out.writeInt(v.value)
          case v: PrimitiveValue.Long       => out.writeLong(v.value)
          case v: PrimitiveValue.Float      => out.writeFloat(v.value)
          case v: PrimitiveValue.Double     => out.writeDouble(v.value)
          case v: PrimitiveValue.Char       => out.writeString(v.value.toString)
          case v: PrimitiveValue.String     => out.writeString(v.value)
          case v: PrimitiveValue.BigInt     => out.writeBigInt(v.value)
          case v: PrimitiveValue.BigDecimal => out.writeBigDecimal(v.value)
          case _                            => out.encodeError("encoding as TOON key is not supported for this type")
        }
      case _ => out.encodeError("encoding as TOON key is not supported")
    }
  }
}
