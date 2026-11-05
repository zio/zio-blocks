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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.json.Json
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Reads and writes values of type `A` as MessagePack bytes. Get the codec of a
 * type that has a schema with `Schema#derive(MessagePackFormat)`, take one of
 * the primitive codecs of the companion object, or write one by implementing
 * `decodeValue(MessagePackReader)` and `encodeValue(A, MessagePackWriter)`.
 *
 * Every `decode` method returns `Left` with one
 * [[zio.blocks.schema.SchemaError]] for the first problem found: an expectation
 * mismatch that carries the reader's message and the path to the value that
 * failed, such as `Expected string header, got: 1 at: .sensor`, where the
 * number after `got:` is the unexpected format byte in decimal. A `decode`
 * method reads one value from the start of its input and ignores any bytes
 * after it. The `encode` methods do not validate the value: they throw only
 * what the codecs of its parts throw.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Reading(sensor: String, celsius: Double, humidity: Option[Int])
 * object Reading {
 *   implicit val schema: Schema[Reading] = Schema.derived
 * }
 *
 * val codec: MessagePackCodec[Reading] = Schema[Reading].derive(MessagePackFormat)
 * val bytes = codec.encode(Reading("t1", 21.5, Some(40)))
 *
 * codec.decode(bytes)              // Right(Reading(t1,21.5,Some(40)))
 * codec.decode(bytes.dropRight(1)) // Left(zio.blocks.schema.SchemaError: Unexpected end of input at: .humidity.when[Some])
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/messagepack/ MessagePack]]
 */
abstract class MessagePackCodec[A] extends BinaryCodec[A] {

  /**
   * Reads one value of type `A` from `in`, which is positioned at its first
   * byte, and leaves `in` positioned after it. To fail, call `error`; `decode`
   * turns that, and any other non-fatal exception, into a `Left`. Implement it
   * together with `encodeValue` to write a codec by hand.
   *
   * {{{
   * // A reading as a two-element array: sensor number, then tenths of a degree
   * val readingCodec: MessagePackCodec[(Int, Int)] = new MessagePackCodec[(Int, Int)] {
   *   def decodeValue(in: MessagePackReader): (Int, Int) = {
   *     val size = in.readArrayHeader()
   *     if (size != 2) error("Expected 2 elements, got: " + size)
   *     (in.readIntValue(), in.readIntValue())
   *   }
   *
   *   def encodeValue(x: (Int, Int), out: MessagePackWriter): Unit = {
   *     out.writeArrayHeader(2)
   *     out.writeInt(x._1)
   *     out.writeInt(x._2)
   *   }
   * }
   *
   * readingCodec.decode(readingCodec.encode((7, 215))) // Right((7,215))
   * readingCodec.decode(Array[Byte](0x91.toByte, 7))   // Left(zio.blocks.schema.SchemaError: Expected 2 elements, got: 1 at: .)
   * }}}
   */
  def decodeValue(in: MessagePackReader): A

  /**
   * Writes `x` to `out`. A hand-written codec writes exactly one MessagePack
   * value, so that a reader can skip past it. An exception thrown here escapes
   * `encode` unchanged.
   */
  def encodeValue(x: A, out: MessagePackWriter): Unit

  /**
   * Decodes one value from the bytes between the buffer's position and its
   * limit. A buffer with an accessible array (`hasArray`) is read in place and
   * keeps its position; any other buffer, such as a direct or read-only one, is
   * copied first, which moves its position to its limit.
   *
   * {{{
   * import java.nio.ByteBuffer
   *
   * val buffer = ByteBuffer.wrap(MessagePackCodec.floatCodec.encode(21.5f))
   *
   * MessagePackCodec.floatCodec.decode(buffer) // Right(21.5)
   * buffer.position()                          // 0
   * }}}
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
   * Encodes `value` and puts its bytes into `output` at its position, which
   * moves past them. The value is encoded in full before anything is put, so
   * when `output` has too little room this throws
   * `java.nio.BufferOverflowException` and leaves `output` unchanged.
   *
   * {{{
   * import java.nio.ByteBuffer
   *
   * val buffer = ByteBuffer.allocate(16)
   * MessagePackCodec.doubleCodec.encode(21.5, buffer)
   *
   * buffer.position() // 9
   * buffer.flip()
   * MessagePackCodec.doubleCodec.decode(buffer) // Right(21.5)
   * }}}
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
   * Decodes one value from the start of `input`, or returns `Left` with the
   * first problem found. Bytes after the value are ignored.
   *
   * {{{
   * MessagePackCodec.intCodec.decode(Array[Byte](0xcd.toByte, 0x03, 0xf5.toByte)) // Right(1013)
   * MessagePackCodec.intCodec.decode(MessagePackCodec.stringCodec.encode("1013")) // Left(zio.blocks.schema.SchemaError: Expected int value, got: 164 at: .)
   * }}}
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
   * Encodes `value` into a new byte array that holds exactly its bytes.
   *
   * {{{
   * val bytes = MessagePackCodec.intCodec.encode(1013) // a pressure reading in hPa
   *
   * bytes.map(b => "%02x".format(b & 0xff)).mkString(" ") // cd 03 f5
   * }}}
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

  /**
   * Fails with `msg` by throwing the module's codec error. In `decodeValue`,
   * `decode` turns it into a `Left` whose path starts at the root (`.`) and
   * gains a segment for each derived codec that contains this one; in
   * `encodeValue`, it escapes `encode`.
   */
  def error(msg: String): Nothing = throw new MessagePackCodecError(Nil, msg)

  /**
   * Rethrows `error` with `span` added to the front of its path. Derived codecs
   * call it around each nested value, so a failure deep inside a value reports
   * the whole path, such as `.readings.at(1).sensor`. An exception that is not
   * a codec error keeps its message, or, when it has none, gets its class name
   * followed by `: (no message)`, as in
   * `java.lang.RuntimeException: (no message)`.
   */
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
            new DynamicOptic(Chunk.fromArray(array))
          case _ => DynamicOptic.root
        },
        error.getMessage
      ),
      Nil
    )
  )
}

/**
 * The MessagePack codecs of the primitive types. [[MessagePackCodecDeriver]]
 * uses them for primitive values, so `Schema[Int].derive(MessagePackFormat)`
 * returns `intCodec` itself. The `decode` and `encode` methods of every codec
 * reuse one reader and one writer per thread.
 */
object MessagePackCodec {
  private val readerPool: ThreadLocal[MessagePackReader] = new ThreadLocal[MessagePackReader] {
    override def initialValue(): MessagePackReader = new MessagePackReader(null, 0, 0)
  }
  private val writerPool: ThreadLocal[MessagePackWriter] = new ThreadLocal[MessagePackWriter] {
    override def initialValue(): MessagePackWriter = new MessagePackWriter(new Array[Byte](32768), -1)
  }

  /**
   * Writes `()` as nil (`0xc0`) and reads only nil, failing with
   * `Expected nil (0xc0), got: ...` for anything else.
   */
  val unitCodec: MessagePackCodec[Unit] = new MessagePackCodec[Unit] {
    def decodeValue(in: MessagePackReader): Unit = in.readNil()

    def encodeValue(x: Unit, out: MessagePackWriter): Unit = out.writeNil()
  }

  /**
   * Writes `true` as `0xc3` and `false` as `0xc2`, and reads only those two
   * bytes, failing with `Expected boolean, got: ...` for anything else.
   */
  val booleanCodec: MessagePackCodec[Boolean] = new MessagePackCodec[Boolean] {
    def decodeValue(in: MessagePackReader): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: MessagePackWriter): Unit = out.writeBoolean(x)
  }

  /**
   * Writes a `Byte` as a fixint, or as `int8` below -32. Reads a fixint, `int8`
   * or `uint8`, and fails with `Expected byte value, got: ...` for any other
   * format, `int16` included. A `uint8` above 127 wraps to a negative `Byte`
   * (200 reads as -56), a known bug: the wider integer codecs reject values out
   * of their range.
   */
  val byteCodec: MessagePackCodec[Byte] = new MessagePackCodec[Byte] {
    def decodeValue(in: MessagePackReader): Byte = in.readByteValue()

    def encodeValue(x: Byte, out: MessagePackWriter): Unit = out.writeByte(x)
  }

  /**
   * Writes a `Short` in the smallest of fixint, `uint8`, `uint16`, `int8` and
   * `int16` that holds it. Reads those formats, and fails with
   * `Value 40000 exceeds Short range` for a `uint16` above 32767 and with
   * `Expected short value, got: ...` for any other format.
   */
  val shortCodec: MessagePackCodec[Short] = new MessagePackCodec[Short] {
    def decodeValue(in: MessagePackReader): Short = in.readShortValue()

    def encodeValue(x: Short, out: MessagePackWriter): Unit = out.writeShort(x)
  }

  /**
   * Writes an `Int` in the smallest integer format that holds it: a fixint from
   * -32 to 127, `uint8`, `uint16` or `uint32` above that, and `int8`, `int16`
   * or `int32` below. Reads every integer format up to 32 bits, and fails with
   * `Value 4294967295 exceeds Int range` for a `uint32` above `Int.MaxValue`
   * and with `Expected int value, got: ...` for any other format, including
   * `int64` and `uint64` whose value would fit.
   */
  val intCodec: MessagePackCodec[Int] = new MessagePackCodec[Int] {
    def decodeValue(in: MessagePackReader): Int = in.readIntValue()

    def encodeValue(x: Int, out: MessagePackWriter): Unit = out.writeInt(x)
  }

  /**
   * Writes a `Long` that fits in an `Int` as `intCodec` does, and any other
   * value as 9 bytes of `uint64` or `int64`, even when `uint32` would hold it.
   * Reads every integer format, and fails with
   * `Value exceeds Long range (unsigned 64-bit)` for a `uint64` above
   * `Long.MaxValue`.
   */
  val longCodec: MessagePackCodec[Long] = new MessagePackCodec[Long] {
    def decodeValue(in: MessagePackReader): Long = in.readLongValue()

    def encodeValue(x: Long, out: MessagePackWriter): Unit = out.writeLong(x)
  }

  /**
   * Writes a `Float` as `float32` (5 bytes). Reads a `float32`, a `float64`
   * rounded to the nearest `Float`, or any integer format; any other input
   * fails with `Expected long value, got: ...`.
   */
  val floatCodec: MessagePackCodec[Float] = new MessagePackCodec[Float] {
    def decodeValue(in: MessagePackReader): Float = in.readFloatValue()

    def encodeValue(x: Float, out: MessagePackWriter): Unit = out.writeFloat(x)
  }

  /**
   * Writes a `Double` as `float64` (9 bytes). Reads a `float64`, a `float32`
   * widened to `Double` (so `1.1f` reads as `1.100000023841858`), or any
   * integer format; any other input fails with `Expected long value, got: ...`.
   */
  val doubleCodec: MessagePackCodec[Double] = new MessagePackCodec[Double] {
    def decodeValue(in: MessagePackReader): Double = in.readDoubleValue()

    def encodeValue(x: Double, out: MessagePackWriter): Unit = out.writeDouble(x)
  }

  /**
   * Writes a `Char` as a string of its one to three UTF-8 bytes (a lone
   * surrogate gets three bytes that are not valid UTF-8). Reads a string of one
   * to three bytes whose first byte starts a sequence of that length, and fails
   * otherwise, for example with
   * `Expected single char, got string of length 0 bytes`. A known bug: it
   * checks neither the end of the input nor the bytes after the first, so a
   * truncated string fails with an index-out-of-bounds message, a heap
   * `ByteBuffer` is read past its limit, and malformed UTF-8 such as `c3 41`
   * reads as one char.
   */
  val charCodec: MessagePackCodec[Char] = new MessagePackCodec[Char] {
    def decodeValue(in: MessagePackReader): Char = in.readChar()

    def encodeValue(x: Char, out: MessagePackWriter): Unit = out.writeChar(x)
  }

  /**
   * Writes a `String` as its UTF-8 bytes behind the smallest string header:
   * fixstr up to 31 bytes, then `str8`, `str16` or `str32`. Reads any string
   * format, and fails with `Expected string header, got: ...` for every other
   * format, `bin` included.
   */
  val stringCodec: MessagePackCodec[String] = new MessagePackCodec[String] {
    def decodeValue(in: MessagePackReader): String = in.readString()

    def encodeValue(x: String, out: MessagePackWriter): Unit = out.writeString(x)
  }

  /**
   * Writes a `BigInt` as `bin` holding its big-endian two's-complement bytes
   * (`BigInt#toByteArray`), so 255 is `c4 02 00 ff`. Reads only `bin`.
   */
  val bigIntCodec: MessagePackCodec[BigInt] = new MessagePackCodec[BigInt] {
    def decodeValue(in: MessagePackReader): BigInt = in.readBigInt()

    def encodeValue(x: BigInt, out: MessagePackWriter): Unit = out.writeBigInt(x)
  }

  /**
   * Writes a `BigDecimal` as a map of three entries: `unscaled` (as
   * `bigIntCodec` writes it), `precision` and `scale` (ints). Reads the three
   * keys in any order and rounds the unscaled value to `precision` digits (0
   * means unlimited); a map of another size fails with
   * `Expected BigDecimal map of 3, got: ...`.
   */
  val bigDecimalCodec: MessagePackCodec[BigDecimal] = new MessagePackCodec[BigDecimal] {
    def decodeValue(in: MessagePackReader): BigDecimal = in.readBigDecimal()

    def encodeValue(x: BigDecimal, out: MessagePackWriter): Unit = out.writeBigDecimal(x)
  }

  /**
   * Writes a `DayOfWeek` as an int from 1 (Monday) to 7 (Sunday). Reading any
   * other int fails, as in `Invalid value for DayOfWeek: 9`.
   */
  val dayOfWeekCodec: MessagePackCodec[DayOfWeek] = new MessagePackCodec[DayOfWeek] {
    def decodeValue(in: MessagePackReader): DayOfWeek = DayOfWeek.of(in.readIntValue())

    def encodeValue(x: DayOfWeek, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  /**
   * Writes a `Duration` as a map of two entries, `seconds` (a long) and `nanos`
   * (an int), and reads the two keys in any order.
   */
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

  /**
   * Writes an `Instant` as the ISO-8601 string that the JSON codec writes, such
   * as `1970-01-01T00:00:00Z`.
   */
  val instantCodec: MessagePackCodec[Instant] = new MessagePackCodec[Instant] {
    def decodeValue(in: MessagePackReader): Instant = Json.instantRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: Instant, out: MessagePackWriter): Unit = out.writeString(Json.instantRawCodec.encodeToString(x))
  }

  /** Writes a `LocalDate` as the ISO-8601 string that the JSON codec writes. */
  val localDateCodec: MessagePackCodec[LocalDate] = new MessagePackCodec[LocalDate] {
    def decodeValue(in: MessagePackReader): LocalDate = Json.localDateRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalDate, out: MessagePackWriter): Unit =
      out.writeString(Json.localDateRawCodec.encodeToString(x))
  }

  /**
   * Writes a `LocalDateTime` as the ISO-8601 string that the JSON codec writes,
   * such as `2026-11-05T09:30`.
   */
  val localDateTimeCodec: MessagePackCodec[LocalDateTime] = new MessagePackCodec[LocalDateTime] {
    def decodeValue(in: MessagePackReader): LocalDateTime = Json.localDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.localDateTimeRawCodec.encodeToString(x))
  }

  /** Writes a `LocalTime` as the ISO-8601 string that the JSON codec writes. */
  val localTimeCodec: MessagePackCodec[LocalTime] = new MessagePackCodec[LocalTime] {
    def decodeValue(in: MessagePackReader): LocalTime = Json.localTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: LocalTime, out: MessagePackWriter): Unit =
      out.writeString(Json.localTimeRawCodec.encodeToString(x))
  }

  /** Writes a `Month` as an int from 1 (January) to 12 (December). */
  val monthCodec: MessagePackCodec[Month] = new MessagePackCodec[Month] {
    def decodeValue(in: MessagePackReader): Month = Month.of(in.readIntValue())

    def encodeValue(x: Month, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  /**
   * Writes a `MonthDay` as a map of two entries, `month` and `day` (ints), and
   * reads the two keys in any order.
   */
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

  /**
   * Writes an `OffsetDateTime` as the ISO-8601 string that the JSON codec
   * writes.
   */
  val offsetDateTimeCodec: MessagePackCodec[OffsetDateTime] = new MessagePackCodec[OffsetDateTime] {
    def decodeValue(in: MessagePackReader): OffsetDateTime = Json.offsetDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: OffsetDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.offsetDateTimeRawCodec.encodeToString(x))
  }

  /**
   * Writes an `OffsetTime` as the ISO-8601 string that the JSON codec writes.
   */
  val offsetTimeCodec: MessagePackCodec[OffsetTime] = new MessagePackCodec[OffsetTime] {
    def decodeValue(in: MessagePackReader): OffsetTime = Json.offsetTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: OffsetTime, out: MessagePackWriter): Unit =
      out.writeString(Json.offsetTimeRawCodec.encodeToString(x))
  }

  /**
   * Writes a `Period` as a map of three entries, `years`, `months` and `days`
   * (ints), and reads the three keys in any order.
   */
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

  /** Writes a `Year` as an int, its year number. */
  val yearCodec: MessagePackCodec[Year] = new MessagePackCodec[Year] {
    def decodeValue(in: MessagePackReader): Year = Year.of(in.readIntValue())

    def encodeValue(x: Year, out: MessagePackWriter): Unit = out.writeInt(x.getValue)
  }

  /**
   * Writes a `YearMonth` as a map of two entries, `year` and `month` (ints),
   * and reads the two keys in any order.
   */
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

  /**
   * Writes a `ZoneId` as its ID string, such as `Europe/Paris`; reading fails
   * for an ID that `ZoneId.of` rejects.
   */
  val zoneIdCodec: MessagePackCodec[ZoneId] = new MessagePackCodec[ZoneId] {
    def decodeValue(in: MessagePackReader): ZoneId = ZoneId.of(in.readString())

    def encodeValue(x: ZoneId, out: MessagePackWriter): Unit = out.writeString(x.getId)
  }

  /** Writes a `ZoneOffset` as an int, its total seconds (`+01:00` is 3600). */
  val zoneOffsetCodec: MessagePackCodec[ZoneOffset] = new MessagePackCodec[ZoneOffset] {
    def decodeValue(in: MessagePackReader): ZoneOffset = ZoneOffset.ofTotalSeconds(in.readIntValue())

    def encodeValue(x: ZoneOffset, out: MessagePackWriter): Unit = out.writeInt(x.getTotalSeconds)
  }

  /**
   * Writes a `ZonedDateTime` as the ISO-8601 string that the JSON codec writes,
   * with the zone ID, such as `2026-11-05T09:30+01:00[Europe/Paris]`.
   */
  val zonedDateTimeCodec: MessagePackCodec[ZonedDateTime] = new MessagePackCodec[ZonedDateTime] {
    def decodeValue(in: MessagePackReader): ZonedDateTime = Json.zonedDateTimeRawCodec.decodeUnsafe(in.readString())

    def encodeValue(x: ZonedDateTime, out: MessagePackWriter): Unit =
      out.writeString(Json.zonedDateTimeRawCodec.encodeToString(x))
  }

  /** Writes a `Currency` as its ISO 4217 code, such as `EUR`. */
  val currencyCodec: MessagePackCodec[Currency] = new MessagePackCodec[Currency] {
    def decodeValue(in: MessagePackReader): Currency = Currency.getInstance(in.readString())

    def encodeValue(x: Currency, out: MessagePackWriter): Unit = out.writeString(x.getCurrencyCode)
  }

  /**
   * Writes a `UUID` as its 36-character string; reading a malformed one fails
   * with `Invalid UUID string: ...`.
   */
  val uuidCodec: MessagePackCodec[UUID] = new MessagePackCodec[UUID] {
    def decodeValue(in: MessagePackReader): UUID = UUID.fromString(in.readString())

    def encodeValue(x: UUID, out: MessagePackWriter): Unit = out.writeString(x.toString)
  }

  /**
   * Writes an `Array[Byte]` as `bin` (`bin8`, `bin16` or `bin32`, the smallest
   * that holds it) and reads only `bin`. No derived codec uses it: a
   * `Chunk[Byte]` field, for example, is written as an array of integers.
   */
  val binaryCodec: MessagePackCodec[Array[Byte]] = new MessagePackCodec[Array[Byte]] {
    def decodeValue(in: MessagePackReader): Array[Byte] = in.readBinary()

    def encodeValue(x: Array[Byte], out: MessagePackWriter): Unit = out.writeBinary(x)
  }
}

/**
 * The exception that `error` throws; `decode` turns it into a `SchemaError`
 * whose path is `spans`.
 */
private case class MessagePackCodecError(spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false)
