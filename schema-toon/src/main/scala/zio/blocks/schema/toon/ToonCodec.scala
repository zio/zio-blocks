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

package zio.blocks.schema.toon

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.{Json, JsonWriter}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Encodes values of type `A` as TOON text and decodes them back, from and to
 * UTF-8 bytes, strings, streams and byte buffers. Get one with
 * `Schema#derive(ToonFormat)`, or pass a configured [[ToonCodecDeriver]] to
 * `Schema#derive`; the companion object holds the codecs for primitive types
 * and for `DynamicValue`.
 *
 * On bad input the `decode` methods return a `Left` with a
 * [[zio.blocks.schema.SchemaError]], instead of throwing; its message ends with
 * the path to the failing value, such as `at: .port`. Each `encode` and
 * `decode` method has an overload that takes a [[WriterConfig]] or a
 * [[ReaderConfig]]; the others use the defaults. The encoded output does not
 * end with a newline.
 *
 * To write a codec by hand, implement `decodeValue` and `encodeValue`.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Server(host: String, port: Int)
 * object Server {
 *   implicit val schema: Schema[Server] = Schema.derived
 * }
 *
 * val codec: ToonCodec[Server] = Server.schema.derive(ToonFormat)
 *
 * codec.encodeToString(Server("api.local", 8080))
 * // host: api.local
 * // port: 8080
 * codec.decode("host: api.local\nport: 8080") // Right(Server(api.local,8080))
 * codec.decode("host: api.local\nport: http") // Left(zio.blocks.schema.SchemaError: Expected int, got: http at: .port)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
abstract class ToonCodec[A] extends BinaryCodec[A] {

  /**
   * Decodes a value of type `A` from `in`, starting at the reader's current
   * position, and throws if the input does not encode one. Implement it in a
   * hand-written codec; to decode a whole input, call a `decode` method, which
   * catches the exception and returns it as a `Left`.
   *
   * @param in
   *   the reader that holds the TOON input
   */
  def decodeValue(in: ToonReader): A

  /**
   * Writes `x` to `out` as a TOON value, at the writer's current position.
   * Implement it in a hand-written codec; to encode a whole value, call an
   * `encode` or `encodeToString` method.
   *
   * @param x
   *   the value to write
   * @param out
   *   the writer that collects the TOON output
   */
  def encodeValue(x: A, out: ToonWriter): Unit

  /**
   * Decodes a value of type `A` written as a TOON key from `in`, and throws if
   * the input does not encode one. The default implementation throws
   * `decoding as TOON key is not supported`; the codecs for `Boolean`, the
   * numeric types, `Char` and `String` override it, and a derived codec of a
   * wrapper type delegates to the wrapped type's codec.
   *
   * @param in
   *   the reader that holds the TOON input
   */
  def decodeKey(in: ToonReader): A = error("decoding as TOON key is not supported")

  /**
   * Writes `x` to `out` as a TOON key. The default implementation throws
   * `encoding as TOON key is not supported`; the codecs for `Boolean`, the
   * numeric types, `Char`, `String` and `DynamicValue` override it, and a
   * derived codec of a wrapper type delegates to the wrapped type's codec.
   *
   * @param x
   *   the value to write
   * @param out
   *   the writer that collects the TOON output
   */
  def encodeKey(x: A, out: ToonWriter): Unit = error("encoding as TOON key is not supported")

  def error(msg: String): Nothing = throw new ToonCodecError(Nil, msg)

  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ToonCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ToonCodecError(new ::(span, Nil), error.getMessage)
  }

  /**
   * Writes `x` as the field `fieldName` of a TOON record. The default
   * implementation writes `fieldName: ` followed by the value and a newline;
   * the codecs for records, sequences, maps and options override it to write
   * their nested form.
   *
   * @param fieldName
   *   the name of the field
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` that provides access to TOON output
   */
  def encodeAsField(fieldName: String, x: A, out: ToonWriter): Unit = {
    out.writeKey(fieldName)
    encodeValue(x, out)
    out.newLine()
  }

  /**
   * Decodes an inline array, such as `xs[3]: 1,2,3`, from its values, already
   * split at the delimiter of the array's header. The default implementation
   * throws `decodeInlineArray not supported for this type`; a derived sequence
   * codec overrides it.
   *
   * @param in
   *   the reader that holds the TOON input
   * @param values
   *   the values of the inline array, still quoted where the input quotes them
   * @param expectedLength
   *   the expected length from the array header
   */
  def decodeInlineArray(in: ToonReader, values: Array[String], expectedLength: Int): A =
    error("decodeInlineArray not supported for this type")

  /**
   * Decodes a list-format array, where each item starts with `- `. The default
   * implementation throws `decodeListArray not supported for this type`; a
   * derived sequence codec overrides it.
   *
   * @param in
   *   the ToonReader positioned at the first list item line
   * @param expectedLength
   *   the expected number of items from the array header
   */
  def decodeListArray(in: ToonReader, expectedLength: Int): A =
    error("decodeListArray not supported for this type")

  /**
   * Decodes a tabular array, such as `items[2]{sku,qty,price}:` followed by one
   * row per element. The default implementation throws
   * `decodeTabularArray not supported for this type`; a derived sequence codec
   * overrides it.
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
    error("decodeTabularArray not supported for this type")

  /**
   * Decodes one row of a tabular array into a record. The default
   * implementation throws `decodeTabularRow not supported for this type`; a
   * derived record codec overrides it.
   *
   * @param in
   *   the reader that holds the TOON input
   * @param values
   *   the values parsed from the row (one per field)
   * @param fieldNames
   *   the field names from the tabular header
   */
  def decodeTabularRow(in: ToonReader, values: Array[String], fieldNames: Array[String]): A =
    error("decodeTabularRow not supported for this type")

  /**
   * Writes `x` as one row of a tabular array: its field values, separated by
   * `delimiter`, without keys. The default implementation throws
   * `encodeTabularRow not supported for this type`; a derived record codec
   * overrides it.
   *
   * @param x
   *   the value to encode
   * @param out
   *   the writer to output to
   * @param delimiter
   *   the delimiter to use between values
   */
  def encodeTabularRow(x: A, out: ToonWriter, delimiter: Delimiter): Unit =
    error("encodeTabularRow not supported for this type")

  /**
   * Decodes a value of type `A` from the remaining bytes of `input`, read as
   * UTF-8, with the default [[ReaderConfig]].
   *
   * @param input
   *   the byte buffer containing TOON input
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Writes `value` as UTF-8 TOON into `output` at its position, with the
   * default [[WriterConfig]]. It throws `java.nio.BufferOverflowException` when
   * `output` has too little room left.
   *
   * @param value
   *   the value to encode
   * @param output
   *   the byte buffer to write TOON output to
   */
  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the remaining bytes of `input`, read as
   * UTF-8, with `config`.
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
      try new Right(decodeValue(reader))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Writes `value` as UTF-8 TOON into `output` at its position, with `config`.
   * It throws `java.nio.BufferOverflowException` when `output` has too little
   * room left.
   */
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    output.put(writer.toByteArrayTrimmed)
  }

  /**
   * Decodes a value of type `A` from the UTF-8 bytes `input`, with the default
   * [[ReaderConfig]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Reading(sensor: String, celsius: Double)
   * object Reading {
   *   implicit val schema: Schema[Reading] = Schema.derived
   * }
   *
   * val codec = Reading.schema.derive(ToonFormat)
   * val bytes = "sensor: cellar\ncelsius: 12.5".getBytes("UTF-8")
   *
   * codec.decode(bytes) // Right(Reading(cellar,12.5))
   * }}}
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes `value` as UTF-8 TOON bytes, with the default [[WriterConfig]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Reading(sensor: String, celsius: Double)
   * object Reading {
   *   implicit val schema: Schema[Reading] = Schema.derived
   * }
   *
   * val bytes = Reading.schema.derive(ToonFormat).encode(Reading("attic", 31.0))
   *
   * new String(bytes, "UTF-8")
   * // sensor: attic
   * // celsius: 31
   * }}}
   */
  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  /**
   * Decodes a value of type `A` from the UTF-8 bytes `input`, with `config`.
   */
  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      val reader = ToonReader(config)
      reader.reset(input, 0, input.length)
      try new Right(decodeValue(reader))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /** Encodes `value` as UTF-8 TOON bytes, with `config`. */
  def encode(value: A, config: WriterConfig): Array[Byte] = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    writer.toByteArrayTrimmed
  }

  /**
   * Reads `input` to its end and decodes a value of type `A` from its UTF-8
   * bytes, with the default [[ReaderConfig]]. It does not close `input`.
   */
  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Writes `value` as UTF-8 TOON to `output`, with the default
   * [[WriterConfig]]. It neither flushes nor closes `output`.
   *
   * {{{
   * import zio.blocks.schema._
   * import java.io.ByteArrayOutputStream
   *
   * case class Order(id: Long, total: BigDecimal)
   * object Order {
   *   implicit val schema: Schema[Order] = Schema.derived
   * }
   *
   * val out = new ByteArrayOutputStream()
   * Order.schema.derive(ToonFormat).encode(Order(1042L, BigDecimal("59.90")), out)
   *
   * out.toString("UTF-8")
   * // id: 1042
   * // total: 59.9
   * }}}
   */
  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  /**
   * Reads `input` to its end and decodes a value of type `A` from its UTF-8
   * bytes, with `config`. It does not close `input`.
   */
  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val bytes  = input.readAllBytes()
      val reader = ToonReader(config)
      reader.reset(bytes, 0, bytes.length)
      try new Right(decodeValue(reader))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Writes `value` as UTF-8 TOON to `output`, with `config`. It neither flushes
   * nor closes `output`.
   */
  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    writer.writeToTrimmed(output)
  }

  /**
   * Decodes a value of type `A` from the TOON text `input`, with the default
   * [[ReaderConfig]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Profile(name: String, roles: List[String], email: Option[String])
   * object Profile {
   *   implicit val schema: Schema[Profile] = Schema.derived
   * }
   *
   * val codec = Profile.schema.derive(ToonFormat)
   *
   * codec.decode("name: ada\nroles[2]: admin,dev") // Right(Profile(ada,List(admin, dev),None))
   * codec.decode("roles[2]: admin,dev")            // Left(zio.blocks.schema.SchemaError: Missing required field: name at: .)
   * }}}
   */
  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes `value` as TOON text, with the default [[WriterConfig]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Profile(name: String, roles: List[String], email: Option[String])
   * object Profile {
   *   implicit val schema: Schema[Profile] = Schema.derived
   * }
   *
   * Profile.schema.derive(ToonFormat).encodeToString(Profile("ada", List("admin", "dev"), None))
   * // name: ada
   * // roles[2]: admin,dev
   * }}}
   */
  def encodeToString(value: A): String = encodeToString(value, WriterConfig)

  /** Decodes a value of type `A` from the TOON text `input`, with `config`. */
  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val reader = ToonReader(config)
      reader.reset(input)
      try new Right(decodeValue(reader))
      finally reader.endUse()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /** Encodes `value` as TOON text, with `config`. */
  def encodeToString(value: A, config: WriterConfig): String = {
    val writer = ToonWriter(config)
    encodeValue(value, writer)
    new String(writer.toByteArrayTrimmed, UTF_8)
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      ExpectationMismatch(
        error match {
          case e: ToonCodecError =>
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
        }, {
          var msg = error.getMessage
          if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
          msg
        }
      ),
      Nil
    )
  )
}

/**
 * Predefined codecs: one for each primitive type, which [[ToonCodecDeriver]]
 * uses for the primitive parts of a schema, and `dynamicValueCodec`.
 */
object ToonCodec {
  val unitCodec: ToonCodec[Unit] = new ToonCodec[Unit] {
    def decodeValue(in: ToonReader): Unit = {
      in.skipBlankLines()
      in.readNull()
    }

    def encodeValue(x: Unit, out: ToonWriter): Unit = out.writeNull()
  }
  val booleanCodec: ToonCodec[Boolean] = new ToonCodec[Boolean] {
    def decodeValue(in: ToonReader): Boolean = {
      in.skipBlankLines()
      in.readBoolean()
    }

    def encodeValue(x: Boolean, out: ToonWriter): Unit = out.writeBoolean(x)

    override def decodeKey(in: ToonReader): Boolean = in.readBoolean()

    override def encodeKey(x: Boolean, out: ToonWriter): Unit = out.writeBoolean(x)
  }
  val byteCodec: ToonCodec[Byte] = new ToonCodec[Byte] {
    def decodeValue(in: ToonReader): Byte = {
      in.skipBlankLines()
      in.readByte()
    }

    def encodeValue(x: Byte, out: ToonWriter): Unit = out.writeInt(x.toInt)

    override def decodeKey(in: ToonReader): Byte = in.readInt().toByte

    override def encodeKey(x: Byte, out: ToonWriter): Unit = out.writeInt(x.toInt)
  }
  val shortCodec: ToonCodec[Short] = new ToonCodec[Short] {
    def decodeValue(in: ToonReader): Short = {
      in.skipBlankLines()
      in.readShort()
    }

    def encodeValue(x: Short, out: ToonWriter): Unit = out.writeInt(x.toInt)

    override def decodeKey(in: ToonReader): Short = in.readInt().toShort

    override def encodeKey(x: Short, out: ToonWriter): Unit = out.writeInt(x.toInt)
  }
  val intCodec: ToonCodec[Int] = new ToonCodec[Int] {
    def decodeValue(in: ToonReader): Int = {
      in.skipBlankLines()
      in.readInt()
    }

    def encodeValue(x: Int, out: ToonWriter): Unit = out.writeInt(x)

    override def decodeKey(in: ToonReader): Int = in.readInt()

    override def encodeKey(x: Int, out: ToonWriter): Unit = out.writeInt(x)
  }
  val longCodec: ToonCodec[Long] = new ToonCodec[Long] {
    def decodeValue(in: ToonReader): Long = {
      in.skipBlankLines()
      in.readLong()
    }

    def encodeValue(x: Long, out: ToonWriter): Unit = out.writeLong(x)

    override def decodeKey(in: ToonReader): Long = in.readLong()

    override def encodeKey(x: Long, out: ToonWriter): Unit = out.writeLong(x)
  }
  val floatCodec: ToonCodec[Float] = new ToonCodec[Float] {
    def decodeValue(in: ToonReader): Float = {
      in.skipBlankLines()
      in.readFloat()
    }

    def encodeValue(x: Float, out: ToonWriter): Unit = out.writeFloat(x)

    override def decodeKey(in: ToonReader): Float = in.readFloat()

    override def encodeKey(x: Float, out: ToonWriter): Unit = out.writeFloat(x)
  }
  val doubleCodec: ToonCodec[Double] = new ToonCodec[Double] {
    def decodeValue(in: ToonReader): Double = {
      in.skipBlankLines()
      in.readDouble()
    }

    def encodeValue(x: Double, out: ToonWriter): Unit = out.writeDouble(x)

    override def decodeKey(in: ToonReader): Double = in.readDouble()

    override def encodeKey(x: Double, out: ToonWriter): Unit = out.writeDouble(x)
  }
  val charCodec: ToonCodec[Char] = new ToonCodec[Char] {
    def decodeValue(in: ToonReader): Char = {
      in.skipBlankLines()
      val s = in.readString()
      if (s.length == 1) s.charAt(0)
      else error(s"Expected single char, got: $s")
    }

    def encodeValue(x: Char, out: ToonWriter): Unit = out.writeChar(x)

    override def decodeKey(in: ToonReader): Char = {
      val s = in.readString()
      if (s.length == 1) s.charAt(0)
      else error(s"Expected single char, got: $s")
    }

    override def encodeKey(x: Char, out: ToonWriter): Unit = out.writeChar(x)
  }
  val stringCodec: ToonCodec[String] = new ToonCodec[String] {
    def decodeValue(in: ToonReader): String = {
      in.skipBlankLines()
      in.readString()
    }

    def encodeValue(x: String, out: ToonWriter): Unit = out.writeString(x)

    override def decodeKey(in: ToonReader): String = in.readString()

    override def encodeKey(x: String, out: ToonWriter): Unit = out.writeString(x)
  }
  val bigIntCodec: ToonCodec[BigInt] = new ToonCodec[BigInt] {
    def decodeValue(in: ToonReader): BigInt = {
      in.skipBlankLines()
      in.readBigInt()
    }

    def encodeValue(x: BigInt, out: ToonWriter): Unit = out.writeBigInt(x)

    override def decodeKey(in: ToonReader): BigInt = in.readBigInt()

    override def encodeKey(x: BigInt, out: ToonWriter): Unit = out.writeBigInt(x)
  }
  val bigDecimalCodec: ToonCodec[BigDecimal] = new ToonCodec[BigDecimal] {
    def decodeValue(in: ToonReader): BigDecimal = {
      in.skipBlankLines()
      in.readBigDecimal()
    }

    def encodeValue(x: BigDecimal, out: ToonWriter): Unit = out.writeBigDecimal(x)

    override def decodeKey(in: ToonReader): BigDecimal = in.readBigDecimal()

    override def encodeKey(x: BigDecimal, out: ToonWriter): Unit = out.writeBigDecimal(x)
  }
  val dayOfWeekCodec: ToonCodec[DayOfWeek] = new ToonCodec[DayOfWeek] {
    def decodeValue(in: ToonReader): DayOfWeek = {
      in.skipBlankLines()
      DayOfWeek.valueOf(in.readString())
    }

    def encodeValue(x: DayOfWeek, out: ToonWriter): Unit = out.writeString(x.name)
  }
  val durationCodec: ToonCodec[Duration] = new ToonCodec[Duration] {
    def decodeValue(in: ToonReader): Duration = {
      in.skipBlankLines()
      Json.durationRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: Duration, out: ToonWriter): Unit = out.writeString(Json.durationRawCodec.encodeToString(x))
  }
  val instantCodec: ToonCodec[Instant] = new ToonCodec[Instant] {
    def decodeValue(in: ToonReader): Instant = {
      in.skipBlankLines()
      Json.instantRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: Instant, out: ToonWriter): Unit = out.writeString(Json.instantRawCodec.encodeToString(x))
  }
  val localDateCodec: ToonCodec[LocalDate] = new ToonCodec[LocalDate] {
    def decodeValue(in: ToonReader): LocalDate = {
      in.skipBlankLines()
      Json.localDateRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: LocalDate, out: ToonWriter): Unit = out.writeString(Json.localDateRawCodec.encodeToString(x))
  }
  val localDateTimeCodec: ToonCodec[LocalDateTime] = new ToonCodec[LocalDateTime] {
    def decodeValue(in: ToonReader): LocalDateTime = {
      in.skipBlankLines()
      Json.localDateTimeRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: LocalDateTime, out: ToonWriter): Unit =
      out.writeString(Json.localDateTimeRawCodec.encodeToString(x))
  }
  val localTimeCodec: ToonCodec[LocalTime] = new ToonCodec[LocalTime] {
    def decodeValue(in: ToonReader): LocalTime = {
      in.skipBlankLines()
      Json.localTimeRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: LocalTime, out: ToonWriter): Unit = out.writeString(Json.localTimeRawCodec.encodeToString(x))
  }
  val monthCodec: ToonCodec[Month] = new ToonCodec[Month] {
    def decodeValue(in: ToonReader): Month = {
      in.skipBlankLines()
      Month.valueOf(in.readString())
    }

    def encodeValue(x: Month, out: ToonWriter): Unit = out.writeString(x.name)
  }
  val monthDayCodec: ToonCodec[MonthDay] = new ToonCodec[MonthDay] {
    def decodeValue(in: ToonReader): MonthDay = {
      in.skipBlankLines()
      Json.monthDayRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: MonthDay, out: ToonWriter): Unit = out.writeString(Json.monthDayRawCodec.encodeToString(x))
  }
  val offsetDateTimeCodec: ToonCodec[OffsetDateTime] = new ToonCodec[OffsetDateTime] {
    def decodeValue(in: ToonReader): OffsetDateTime = {
      in.skipBlankLines()
      Json.offsetDateTimeRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: OffsetDateTime, out: ToonWriter): Unit =
      out.writeString(Json.offsetDateTimeRawCodec.encodeToString(x))
  }
  val offsetTimeCodec: ToonCodec[OffsetTime] = new ToonCodec[OffsetTime] {
    def decodeValue(in: ToonReader): OffsetTime = {
      in.skipBlankLines()
      Json.offsetTimeRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: OffsetTime, out: ToonWriter): Unit = out.writeString(Json.offsetTimeRawCodec.encodeToString(x))
  }
  val periodCodec: ToonCodec[Period] = new ToonCodec[Period] {
    def decodeValue(in: ToonReader): Period = {
      in.skipBlankLines()
      Json.periodRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: Period, out: ToonWriter): Unit = out.writeString(Json.periodRawCodec.encodeToString(x))
  }
  val yearCodec: ToonCodec[Year] = new ToonCodec[Year] {
    def decodeValue(in: ToonReader): Year = {
      in.skipBlankLines()
      val s = in.readString()
      try Year.of(s.toInt)
      catch {
        case _: NumberFormatException => Year.parse(s)
      }
    }

    def encodeValue(x: Year, out: ToonWriter): Unit = out.writeInt(x.getValue)
  }
  val yearMonthCodec: ToonCodec[YearMonth] = new ToonCodec[YearMonth] {
    def decodeValue(in: ToonReader): YearMonth = {
      in.skipBlankLines()
      YearMonth.parse(in.readString())
    }

    def encodeValue(x: YearMonth, out: ToonWriter): Unit = out.writeString(x.toString)
  }
  val zoneIdCodec: ToonCodec[ZoneId] = new ToonCodec[ZoneId] {
    def decodeValue(in: ToonReader): ZoneId = {
      in.skipBlankLines()
      ZoneId.of(in.readString())
    }

    def encodeValue(x: ZoneId, out: ToonWriter): Unit = out.writeString(x.getId)
  }
  val zoneOffsetCodec: ToonCodec[ZoneOffset] = new ToonCodec[ZoneOffset] {
    def decodeValue(in: ToonReader): ZoneOffset = {
      in.skipBlankLines()
      ZoneOffset.of(in.readString())
    }

    def encodeValue(x: ZoneOffset, out: ToonWriter): Unit = out.writeString(x.getId)
  }
  val zonedDateTimeCodec: ToonCodec[ZonedDateTime] = new ToonCodec[ZonedDateTime] {
    def decodeValue(in: ToonReader): ZonedDateTime = {
      in.skipBlankLines()
      Json.zonedDateTimeRawCodec.decodeUnsafe(in.readString())
    }

    def encodeValue(x: ZonedDateTime, out: ToonWriter): Unit =
      out.writeString(Json.zonedDateTimeRawCodec.encodeToString(x))
  }
  val currencyCodec: ToonCodec[Currency] = new ToonCodec[java.util.Currency] {
    def decodeValue(in: ToonReader): java.util.Currency = {
      in.skipBlankLines()
      java.util.Currency.getInstance(in.readString())
    }

    def encodeValue(x: java.util.Currency, out: ToonWriter): Unit = out.writeString(x.getCurrencyCode)
  }
  val uuidCodec: ToonCodec[UUID] = new ToonCodec[java.util.UUID] {
    def decodeValue(in: ToonReader): java.util.UUID = {
      in.skipBlankLines()
      java.util.UUID.fromString(in.readString())
    }

    def encodeValue(x: java.util.UUID, out: ToonWriter): Unit = out.writeString(x.toString)
  }

  /**
   * The codec of `DynamicValue`, which
   * `Schema[DynamicValue].derive(ToonFormat)` returns. It is the one codec that
   * reads the `DynamicValue` settings of [[WriterConfig]] (delimiter, key
   * folding, flatten depth, discriminator field) and [[ReaderConfig]] (path
   * expansion, discriminator field). It picks each array's form from the
   * elements: inline for primitives, a table for records with the same keys and
   * only primitive values, a list otherwise.
   *
   * Decoding infers each scalar's type: `null`, `true` and `false`; a number
   * with a whole value (`2.0` too) as `Int` or `Long`, or `BigInt` when an
   * integer is too large for `Long`; another number as a `BigDecimal` parsed
   * through `Double`, so digits beyond its precision are lost; anything else,
   * including numbers with leading zeros such as `007`, as a `String`. Without
   * a discriminator field it reads every record back as a record, so a variant
   * written with the default [[WriterConfig]] comes back as a record with one
   * field named after the case. That is why a `DynamicSchema` written as TOON
   * reads back as a schema that accepts any value, a known bug. Another known
   * bug: it does not check the length in an array header, so input with fewer
   * or more elements than the header says decodes without an error.
   */
  val dynamicValueCodec: ToonCodec[DynamicValue] = new ToonCodec[DynamicValue] {
    private[this] val falseValue = new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
    private[this] val trueValue  = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
    private[this] val unitValue  = new DynamicValue.Primitive(PrimitiveValue.Unit)

    private[this] def encodePrimitive(p: PrimitiveValue, out: ToonWriter): Unit = p match {
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
      case v: PrimitiveValue.Duration       => out.writeString(Json.durationRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.Instant        => out.writeString(Json.instantRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.LocalDate      => out.writeString(Json.localDateRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.LocalDateTime  => out.writeString(Json.localDateTimeRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.LocalTime      => out.writeString(Json.localTimeRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.Month          => out.writeString(v.value.name)
      case v: PrimitiveValue.MonthDay       => out.writeString(Json.monthDayRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.OffsetDateTime => out.writeString(Json.offsetDateTimeRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.OffsetTime     => out.writeString(Json.offsetTimeRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.Period         => out.writeString(Json.periodRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.Year           => out.writeString(v.value.toString)
      case v: PrimitiveValue.YearMonth      => out.writeString(v.value.toString)
      case v: PrimitiveValue.ZoneId         => out.writeString(v.value.getId)
      case v: PrimitiveValue.ZoneOffset     => out.writeString(v.value.getId)
      case v: PrimitiveValue.ZonedDateTime  => out.writeString(Json.zonedDateTimeRawCodec.encodeToString(v.value))
      case v: PrimitiveValue.Currency       => out.writeString(v.value.getCurrencyCode)
      case v: PrimitiveValue.UUID           => out.writeString(v.value.toString)
    }

    private[this] def inferredToDynamicValue(inferred: Any, rawString: String): DynamicValue =
      inferred match {
        case null       => unitValue
        case b: Boolean =>
          if (b) trueValue
          else falseValue
        case l: Long =>
          new DynamicValue.Primitive({
            val intVal = l.toInt
            if (l == intVal) new PrimitiveValue.Int(intVal)
            else new PrimitiveValue.Long(l)
          })
        case d: Double  => new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(JsonWriter.toBigDecimal(d)))
        case bi: BigInt => new DynamicValue.Primitive(new PrimitiveValue.BigInt(bi))
        case s: String  => new DynamicValue.Primitive(new PrimitiveValue.String(s))
        case _          => new DynamicValue.Primitive(new PrimitiveValue.String(rawString))
      }

    private[this] def parseLengthAndDelimiter(bracketContent: String): (Int, Delimiter) =
      if (bracketContent.isEmpty) (0, Delimiter.Comma)
      else if (bracketContent.endsWith("\t")) (bracketContent.dropRight(1).trim.toInt, Delimiter.Tab)
      else if (bracketContent.endsWith("|")) (bracketContent.dropRight(1).trim.toInt, Delimiter.Pipe)
      else (bracketContent.trim.toInt, Delimiter.Comma)

    private[this] def parseFieldNames(content: String, delim: Delimiter): Array[String] = {
      val fieldSeparator = delim match {
        case Delimiter.Pipe => "\\|"
        case Delimiter.Tab  => "\t"
        case _              => ","
      }
      content.split(fieldSeparator).map(_.trim)
    }

    private[this] def decodeTabularRows(
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

    private[this] def decodeInlinePrimitives(
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

    private[this] def decodeListItems(
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
          builder.addOne(decodeValue(in))
          count += 1
        } else if (in.hasMoreLines && in.getDepth >= startDepth) count = length
      }
    }

    def decodeValue(in: ToonReader): DynamicValue = {
      in.skipBlankLines()
      if (!in.hasMoreLines) return DynamicValue.Record.empty
      val content = in.peekTrimmedContent
      if (content.isEmpty) {
        val currentDepth = in.getDepth
        in.advanceLine()
        in.skipBlankLines()
        if (in.hasMoreLines && in.getDepth > currentDepth) return decodeValue(in)
        return DynamicValue.Record.empty
      }
      if (content.startsWith("[")) return decodeRootArray(in)
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
          new DynamicValue.Primitive({
            if (l == intVal) new PrimitiveValue.Int(intVal)
            else new PrimitiveValue.Long(l)
          })
        case d: Double =>
          in.advanceLine()
          new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(JsonWriter.toBigDecimal(d)))
        case bi: BigInt =>
          in.advanceLine()
          new DynamicValue.Primitive(new PrimitiveValue.BigInt(bi))
        case s: String =>
          if (content.indexOf(':') >= 0) decodeRecordFields(in)
          else {
            in.advanceLine()
            new DynamicValue.Primitive(new PrimitiveValue.String(s))
          }
        case _ =>
          in.advanceLine()
          new DynamicValue.Primitive(new PrimitiveValue.String(content))
      }
    }

    private[this] def decodeRecordFields(in: ToonReader): DynamicValue = {
      val builder    = ChunkBuilder.make[((String, Boolean), DynamicValue)]()
      val startDepth = in.getDepth
      in.skipBlankLines()
      while (in.hasMoreLines && in.getDepth >= startDepth && !in.isListItem) {
        val (rawKey, wasQuoted) = in.readKeyWithQuoteInfo()
        val bracketStart        = rawKey.indexOf('[')
        val hasArrayNotation    = bracketStart >= 0 && rawKey.indexOf(']', bracketStart) > bracketStart
        val (fieldName, value)  =
          if (wasQuoted && bracketStart > 0) {
            val quotedPart = rawKey.substring(0, bracketStart)
            val name       =
              if (quotedPart.startsWith("\"") && quotedPart.endsWith("\"")) {
                quotedPart.substring(1, quotedPart.length - 1)
              } else quotedPart
            (name, decodeArrayFieldValue(in, rawKey.substring(bracketStart)))
          } else if (!wasQuoted && hasArrayNotation) {
            val name =
              if (bracketStart > 0) rawKey.substring(0, bracketStart)
              else rawKey
            (name, decodeArrayFieldValue(in, rawKey))
          } else (rawKey, decodeValue(in))
        builder.addOne(((fieldName, wasQuoted), value))
        in.skipBlankLines()
      }
      val fieldsWithQuoteInfo = builder.result()
      in.expandPaths match {
        case PathExpansion.Safe => expandAndMergeFieldsWithQuoteInfo(in, fieldsWithQuoteInfo)
        case PathExpansion.Off  =>
          maybeExtractVariant(new DynamicValue.Record(fieldsWithQuoteInfo.map { case ((k, _), v) => (k, v) }), in)
      }
    }

    private[this] def expandAndMergeFieldsWithQuoteInfo(
      in: ToonReader,
      fields: Chunk[((String, Boolean), DynamicValue)]
    ) = {
      val keyMap = scala.collection.mutable.LinkedHashMap.empty[String, DynamicValue]
      fields.foreach { case ((key, wasQuoted), value) =>
        val (expandedKey, expandedValue) =
          if (wasQuoted) (key, value) // Quoted keys are preserved as literals
          else expandDottedKey(key, value)
        mergeIntoMap(in, keyMap, expandedKey, expandedValue)
      }
      val result = ChunkBuilder.make[(String, DynamicValue)]()
      keyMap.foreach(result.addOne)
      maybeExtractVariant(new DynamicValue.Record(result.result()), in)
    }

    private[this] def maybeExtractVariant(record: DynamicValue.Record, in: ToonReader): DynamicValue =
      in.discriminatorField match {
        case Some(fieldName) =>
          record.fields.find(_._1 == fieldName) match {
            case Some((_, DynamicValue.Primitive(PrimitiveValue.String(caseName)))) =>
              val otherFields = record.fields.filterNot(_._1 == fieldName)
              otherFields match {
                case Chunk(("value", innerValue)) => new DynamicValue.Variant(caseName, innerValue)
                case _                            => new DynamicValue.Variant(caseName, new DynamicValue.Record(otherFields))
              }
            case _ => record
          }
        case _ => record
      }

    private[this] def expandDottedKey(key: String, value: DynamicValue): (String, DynamicValue) =
      if (key.indexOf('.') < 0) (key, value)
      else {
        val segments = key.split('.').toList
        if (segments.forall(ToonWriter.isIdentifierSegment)) {
          (
            segments.head,
            segments.tail.foldRight(value) { (segment, acc) =>
              DynamicValue.Record(Chunk((segment, acc)))
            }
          )
        } else (key, value)
      }

    private[this] def mergeIntoMap(
      in: ToonReader,
      map: scala.collection.mutable.LinkedHashMap[String, DynamicValue],
      key: String,
      value: DynamicValue
    ): Unit = map.put(
      key,
      map.get(key) match {
        case Some(existing) =>
          (existing, value) match {
            case (existingRecord: DynamicValue.Record, newRecord: DynamicValue.Record) =>
              deepMergeRecords(existingRecord, newRecord, in)
            case _ =>
              // A conflict is when we would overwrite a non-record with a different value
              if (in.isStrict) {
                error(
                  s"Path expansion conflict at key '$key': cannot overwrite existing value with new value in strict mode"
                )
              }
              value
          }
        case _ => value
      }
    )

    private[this] def deepMergeRecords(
      existing: DynamicValue.Record,
      incoming: DynamicValue.Record,
      in: ToonReader
    ): DynamicValue.Record = {
      val mergedMap = scala.collection.mutable.LinkedHashMap.empty[String, DynamicValue]
      existing.fields.foreach { case (k, v) => mergedMap.put(k, v) }
      incoming.fields.foreach { case (k, v) => mergeIntoMap(in, mergedMap, k, v) }
      val result = ChunkBuilder.make[(String, DynamicValue)]()
      mergedMap.foreach(result.addOne)
      new DynamicValue.Record(result.result())
    }

    private[this] def decodeRootArray(in: ToonReader): DynamicValue.Sequence = {
      val content      = in.peekTrimmedContent
      val bracketStart = content.indexOf('[')
      val bracketEnd   = content.indexOf(']', bracketStart)
      if (bracketStart < 0 || bracketEnd < 0) error("Expected array header with []")
      val bracketContent        = content.substring(bracketStart + 1, bracketEnd)
      val (length, delim)       = parseLengthAndDelimiter(bracketContent)
      var afterBracket          = bracketEnd + 1
      var fields: Array[String] = null
      if (afterBracket < content.length && content.charAt(afterBracket) == '{') {
        val braceEnd = content.indexOf('}', afterBracket)
        if (braceEnd < 0) error("Expected closing } in field list")
        fields = parseFieldNames(content.substring(afterBracket + 1, braceEnd), delim)
        afterBracket = braceEnd + 1
      }
      if (afterBracket >= content.length || content.charAt(afterBracket) != ':') error("Expected : after array header")
      val inlineContent = content.substring(afterBracket + 1).trim
      in.advanceLine()
      in.setActiveDelimiter(delim)
      val builder    = ChunkBuilder.make[DynamicValue]()
      val startDepth = in.getDepth
      if (fields != null && fields.nonEmpty) {
        in.skipBlankLinesInArray(true)
        decodeTabularRows(in, fields, length, startDepth, builder)
      } else if (inlineContent.nonEmpty) decodeInlinePrimitives(in, splitInlineValues(inlineContent, delim), builder)
      else {
        in.skipBlankLinesInArray(true)
        decodeListItems(in, length, startDepth, builder)
      }
      new DynamicValue.Sequence(builder.result())
    }

    private[this] def splitInlineValues(s: String, delim: Delimiter): Array[String] = {
      val result  = new scala.collection.mutable.ArrayBuffer[String]
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

    private[this] def decodeArrayFieldValue(in: ToonReader, rawKey: String): DynamicValue.Sequence = {
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
      new DynamicValue.Sequence(builder.result())
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
              val (k, v) = it.next()
              encodeRecordPlain(k, v, out)
            }
        }
      case variant: DynamicValue.Variant =>
        out.discriminatorField match {
          case Some(fieldName) => encodeVariantWithDiscriminator(variant, fieldName, out)
          case _               => encodeRecordPlain(variant.caseNameValue, variant.value, out)
        }
      case sequence: DynamicValue.Sequence =>
        val elements = sequence.elements
        if (elements.isEmpty) out.writeArrayHeader(0)
        else {
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

    private[this] def encodeVariantWithDiscriminator(
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

    private[this] def encodeRecordPlain(key: String, value: DynamicValue, out: ToonWriter): Unit =
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
            case _               => encodeRecordPlain(variant.caseNameValue, variant.value, out)
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

    private[this] def encodeRecordWithFolding(
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

    private[this] def writeFieldWithNesting(
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

    private[this] def collectFoldableChain(
      key: String,
      value: DynamicValue,
      maxDepth: Int
    ): (String, DynamicValue, Int) = {
      if (maxDepth > 1) {
        value match {
          case record: DynamicValue.Record if record.fields.size == 1 =>
            val (nestedKey, nestedValue) = record.fields.head
            if (ToonWriter.isIdentifierSegment(nestedKey)) {
              val (restKey, leafValue, depth) = collectFoldableChain(nestedKey, nestedValue, maxDepth - 1)
              return (key + '.' + restKey, leafValue, depth + 1)
            }
          case _ =>
        }
      }
      (key, value, 1)
    }

    private sealed trait ArrayClassification

    private object ArrayClassification {
      case object Primitives extends ArrayClassification

      case class UniformRecords(keys: Chunk[String]) extends ArrayClassification

      case object Mixed extends ArrayClassification
    }

    private[this] def classifySequence(elements: Chunk[DynamicValue]): ArrayClassification = {
      var allPrimitives                  = true
      var allRecords                     = true
      var firstRecordKeys: Chunk[String] = null
      val it                             = elements.iterator
      while (it.hasNext && (allPrimitives || allRecords)) {
        it.next() match {
          case _: DynamicValue.Primitive =>
            allRecords = false
          case r: DynamicValue.Record =>
            allPrimitives = false
            val keys = r.fields.map(_._1)
            if (firstRecordKeys == null) firstRecordKeys = keys
            else if (keys != firstRecordKeys) allRecords = false
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

    private[this] def encodePrimitiveInline(value: DynamicValue, out: ToonWriter): Unit = value match {
      case primitive: DynamicValue.Primitive => encodePrimitive(primitive.value, out)
      case _                                 => out.writeString(value.toString)
    }

    private[this] def encodeTabularRow(record: DynamicValue.Record, keys: Chunk[String], out: ToonWriter): Unit = {
      val fieldMap = record.fields.toMap
      var first    = true
      val it       = keys.iterator
      while (it.hasNext) {
        if (!first) out.writeDelimiter()
        first = false
        fieldMap.get(it.next()) match {
          case Some(value) => encodePrimitiveInline(value, out)
          case _           => out.writeNull()
        }
      }
    }

    private[this] def encodeListItem(element: DynamicValue, out: ToonWriter): Unit = element match {
      case record: DynamicValue.Record if record.fields.nonEmpty =>
        out.writeListItemMarker()
        out.enterListItemContext()
        val fields = record.fields
        val it     = fields.iterator
        var first  = true
        out.incrementDepth()
        while (it.hasNext) {
          val (key, value) = it.next()
          if (first) first = false
          else out.ensureIndent()
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

    private[this] def encodeSequenceField(key: String, seq: DynamicValue.Sequence, out: ToonWriter): Unit = {
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
          case _                            => error("encoding as TOON key is not supported for this type")
        }
      case _ => error("encoding as TOON key is not supported")
    }
  }
}
