package zio.blocks.schema.json

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.binding.Registers
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from JSON
 * formats. Encapsulates logic to map JSON representations into native type
 * structures and vice versa, handling serialization and deserialization as per
 * JSON encoding standards using UTF-8 character set.
 *
 * @param valueType
 *   Integer representing the type of the value for encoding/decoding. Default
 *   is set to `JsonBinaryCodec.objectType`.
 *
 * This class requires an implementation for two core operations: decoding a
 * value of type `A` from a JSON representation and encoding a value of type `A`
 * into a JSON representation.
 */
abstract class JsonBinaryCodec[A](val valueType: Int = JsonBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Computes the appropriate `RegisterOffset` based on the value type defined
   * in `JsonBinaryCodec`.
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
   * Attempts to decode a value of type `A` from the specified `JsonReader`, but
   * may fail with `JsonBinaryCodecError` error if the JSON input does not
   * encode a value of this type.
   *
   * @param in
   *   an instance of `JsonReader` which provides access to the JSON input to
   *   parse a JSON value to value of type `A`
   * @param default
   *   the placeholder value provided to initialize some possible local
   *   variables
   */
  def decodeValue(in: JsonReader, default: A): A

  /**
   * Encodes the specified value using provided `JsonWriter`, but may fail with
   * `JsonBinaryCodecError` if it cannot be encoded properly according to
   * RFC-8259 requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` which provides access to JSON output to
   *   serialize the specified value as a JSON value
   */
  def encodeValue(x: A, out: JsonWriter): Unit

  /**
   * Returns some value that will be passes as the default parameter in
   * `decodeValue`.
   */
  def nullValue: A = null.asInstanceOf[A]

  /**
   * Attempts to decode a value of type `A` from the specified `JsonReader`, but
   * may fail with `JsonBinaryCodecError` error if the JSON input is not a key
   * or does not encode a value of this type.
   *
   * @param in
   *   an instance of `JsonReader` which provides access to the JSON input to
   *   parse a JSON key to value of type `A`
   */
  def decodeKey(in: JsonReader): A = in.decodeError("decoding as JSON key is not supported")

  /**
   * Encodes the specified value using provided `JsonWriter` as a JSON key, but
   * may fail with `JsonBinaryCodecError` if it cannot be encoded properly,
   * according to RFC-8259 requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` which provides access to JSON output to
   *   serialize the specified value as a JSON key
   */
  def encodeKey(x: A, out: JsonWriter): Unit = out.encodeError("encoding as JSON key is not supported")

  /** Returns the JSON Schema describing values this codec encodes/decodes. */
  def toJsonSchema: JsonSchema = JsonSchema.True

  /**
   * Decodes a value of type `A` from the given `ByteBuffer`. This method uses
   * the default `ReaderConfig` to configure the decoding process. If decoding
   * fails, a `SchemaError` is returned.
   *
   * @param input
   *   the `ByteBuffer` containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into binary format and writes it to
   * the provided `ByteBuffer`. This method uses the default `WriterConfig` for
   * encoding configuration.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `ByteBuffer` where the encoded binary data is written
   */
  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the given `ByteBuffer` using the specified
   * `ReaderConfig`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the `ByteBuffer` containing the binary data to be decoded
   * @param config
   *   the `ReaderConfig` instance used to configure the decoding process
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = JsonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into binary format and writes it to
   * the provided `ByteBuffer` using the specified `WriterConfig` for encoding
   * configuration.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `ByteBuffer` where the encoded binary data is written
   * @param config
   *   the `WriterConfig` instance used to configure the encoding process
   */
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    var writer = JsonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.write(this, value, output, config)
  }

  /**
   * Decodes a value of type `A` from the given byte array using the default
   * `ReaderConfig`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the byte array containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into a binary format using the
   * default `WriterConfig`.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @return
   *   an array of bytes representing the binary-encoded data
   */
  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided byte array using the
   * specified `ReaderConfig`. If decoding fails, an error of type `SchemaError`
   * is returned.
   *
   * @param input
   *   the byte array containing the binary data to be decoded
   * @param config
   *   the `ReaderConfig` instance used to configure the decoding process
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = JsonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(input, config)
      new Right(reader.read(this, input, 0, input.length, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into a binary format using the
   * provided `WriterConfig`.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param config
   *   the `WriterConfig` instance used to configure the encoding process
   * @return
   *   an array of bytes representing the binary-encoded data
   */
  def encode(value: A, config: WriterConfig): Array[Byte] = {
    var writer = JsonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.write(this, value, config)
  }

  /**
   * Decodes a value of type `A` from the provided `InputStream` using the
   * default `ReaderConfig`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the `InputStream` containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into a binary format and writes it
   * to the provided `OutputStream`. This method uses the default `WriterConfig`
   * for encoding configuration.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `OutputStream` where the encoded binary data is written
   */
  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided `InputStream` using the
   * specified `ReaderConfig`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the `InputStream` containing the binary data to be decoded
   * @param config
   *   the `ReaderConfig` instance used to configure the decoding process
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = JsonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into a binary format and writes it
   * to the given `OutputStream` using the provided `WriterConfig` for encoding
   * configuration.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `OutputStream` where the encoded binary data is written
   * @param config
   *   the `WriterConfig` instance used to configure the encoding process
   */
  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit = {
    var writer = JsonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.write(this, value, output, config)
  }

  /**
   * Decodes a value of type `A` from the given string using the default
   * `ReaderConfig`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the string containing the data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into a string using the default
   * `WriterConfig`.
   *
   * @param value
   *   the value of type `A` to be serialized into a string
   * @return
   *   a string representing the encoded data
   */
  def encodeToString(value: A): String = encodeToString(value, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided string using the specified
   * `ReaderConfig`. If decoding fails, an error of type `SchemaError` is
   * returned.
   *
   * @param input
   *   a string containing the data to be decoded
   * @param config
   *   the `ReaderConfig` instance used to configure the decoding process
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val buf    = input.getBytes(UTF_8)
      var reader = JsonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(buf, config)
      new Right(reader.read(this, buf, 0, buf.length, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into a string using the provided
   * `WriterConfig`.
   *
   * @param value
   *   the value of type `A` to be serialized into a string
   * @param config
   *   the `WriterConfig` instance used to configure the encoding process
   * @return
   *   a string representing the encoded data
   */
  def encodeToString(value: A, config: WriterConfig): String = {
    var writer = JsonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.writeToString(this, value, config)
  }

  private[this] def jsonReader(buf: Array[Byte], config: ReaderConfig): JsonReader =
    new JsonReader(
      buf = buf,
      charBuf = new Array[Char](config.preferredCharBufSize),
      config = config,
      stack = Registers(0)
    )

  private[this] def jsonWriter(config: WriterConfig): JsonWriter =
    new JsonWriter(buf = Array.emptyByteArray, limit = 0, config = config, stack = Registers(0))

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(
        error match {
          case e: JsonBinaryCodecError =>
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
        Option(error.getMessage).getOrElse(s"${error.getClass.getName}: (no message)")
      ),
      Nil
    )
  )
}

/**
 * A utility object that provides constants and thread-local pools for encoding
 * and decoding JSON structures. The object defines various type constants that
 * represent different data types and maintains `ThreadLocal` pools for
 * `JsonReader` and `JsonWriter` instances to ensure their efficient reuse.
 *
 * The `JsonBinaryCodec` is designed for use with methods from its associated
 * companion type, providing a lower-level abstraction to interact with
 * JSON-encoded binary data. These utility pools help manage reading and writing
 * operations in a thread-safe manner while also improving performance.
 */
object JsonBinaryCodec {
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

  private val readerPool: ThreadLocal[JsonReader] = new ThreadLocal[JsonReader] {
    override def initialValue(): JsonReader = new JsonReader
  }
  private val writerPool: ThreadLocal[JsonWriter] = new ThreadLocal[JsonWriter] {
    override def initialValue(): JsonWriter = new JsonWriter
  }

  val unitCodec: JsonBinaryCodec[Unit] = new JsonBinaryCodec[Unit](JsonBinaryCodec.unitType) {
    def decodeValue(in: JsonReader, default: Unit): Unit =
      if (in.isNextToken('{') && in.isNextToken('}')) ()
      else in.decodeError("expected an empty JSON object")

    def encodeValue(x: Unit, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeObjectEnd()
    }

    override def toJsonSchema: JsonSchema = JsonSchema.obj(
      properties = Some(ChunkMap.empty),
      additionalProperties = Some(JsonSchema.False)
    )
  }
  val booleanCodec: JsonBinaryCodec[Boolean] = new JsonBinaryCodec[Boolean](JsonBinaryCodec.booleanType) {
    def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Boolean = in.readKeyAsBoolean()

    override def encodeKey(x: Boolean, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.boolean
  }
  val byteCodec: JsonBinaryCodec[Byte] = new JsonBinaryCodec[Byte](JsonBinaryCodec.byteType) {
    def decodeValue(in: JsonReader, default: Byte): Byte = in.readByte()

    def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Byte = in.readKeyAsByte()

    override def encodeKey(x: Byte, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = Some(BigDecimal(Byte.MinValue)),
      maximum = Some(BigDecimal(Byte.MaxValue))
    )
  }
  val shortCodec: JsonBinaryCodec[Short] = new JsonBinaryCodec[Short](JsonBinaryCodec.shortType) {
    def decodeValue(in: JsonReader, default: Short): Short = in.readShort()

    def encodeValue(x: Short, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Short = in.readKeyAsShort()

    override def encodeKey(x: Short, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = Some(BigDecimal(Short.MinValue)),
      maximum = Some(BigDecimal(Short.MaxValue))
    )
  }
  val intCodec: JsonBinaryCodec[Int] = new JsonBinaryCodec[Int](JsonBinaryCodec.intType) {
    def decodeValue(in: JsonReader, default: Int): Int = in.readInt()

    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Int = in.readKeyAsInt()

    override def encodeKey(x: Int, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.integer()
  }
  val longCodec: JsonBinaryCodec[Long] = new JsonBinaryCodec[Long](JsonBinaryCodec.longType) {
    def decodeValue(in: JsonReader, default: Long): Long = in.readLong()

    def encodeValue(x: Long, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Long = in.readKeyAsLong()

    override def encodeKey(x: Long, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.integer()
  }
  val floatCodec: JsonBinaryCodec[Float] = new JsonBinaryCodec[Float](JsonBinaryCodec.floatType) {
    def decodeValue(in: JsonReader, default: Float): Float = in.readFloat()

    def encodeValue(x: Float, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Float = in.readKeyAsFloat()

    override def encodeKey(x: Float, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.number()
  }
  val doubleCodec: JsonBinaryCodec[Double] = new JsonBinaryCodec[Double](JsonBinaryCodec.doubleType) {
    def decodeValue(in: JsonReader, default: Double): Double = in.readDouble()

    def encodeValue(x: Double, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Double = in.readKeyAsDouble()

    override def encodeKey(x: Double, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.number()
  }
  val charCodec: JsonBinaryCodec[Char] = new JsonBinaryCodec[Char](JsonBinaryCodec.charType) {
    def decodeValue(in: JsonReader, default: Char): Char = in.readChar()

    def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): Char = in.readKeyAsChar()

    override def encodeKey(x: Char, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(
      minLength = Some(NonNegativeInt.one),
      maxLength = Some(NonNegativeInt.one)
    )
  }
  val stringCodec: JsonBinaryCodec[String] = new JsonBinaryCodec[String]() {
    def decodeValue(in: JsonReader, default: String): String = in.readString(default)

    def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): String = in.readKeyAsString()

    override def encodeKey(x: String, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val bigIntCodec: JsonBinaryCodec[BigInt] = new JsonBinaryCodec[BigInt]() {
    def decodeValue(in: JsonReader, default: BigInt): BigInt = in.readBigInt(default)

    def encodeValue(x: BigInt, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): BigInt = in.readKeyAsBigInt()

    override def encodeKey(x: BigInt, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.integer()
  }
  val bigDecimalCodec: JsonBinaryCodec[BigDecimal] = new JsonBinaryCodec[BigDecimal]() {
    def decodeValue(in: JsonReader, default: BigDecimal): BigDecimal = in.readBigDecimal(default)

    def encodeValue(x: BigDecimal, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): BigDecimal = in.readKeyAsBigDecimal()

    override def encodeKey(x: BigDecimal, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.number()
  }
  val dayOfWeekCodec: JsonBinaryCodec[DayOfWeek] = new JsonBinaryCodec[java.time.DayOfWeek]() {
    def decodeValue(in: JsonReader, default: java.time.DayOfWeek): java.time.DayOfWeek = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    def encodeValue(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.time.DayOfWeek = {
      val code = in.readKeyAsString()
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    override def encodeKey(x: java.time.DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val durationCodec: JsonBinaryCodec[Duration] = new JsonBinaryCodec[java.time.Duration]() {
    def decodeValue(in: JsonReader, default: java.time.Duration): java.time.Duration = in.readDuration(default)

    def encodeValue(x: java.time.Duration, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Duration = in.readKeyAsDuration()

    override def encodeKey(x: java.time.Duration, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("duration"))
  }
  val instantCodec: JsonBinaryCodec[Instant] = new JsonBinaryCodec[java.time.Instant]() {
    def decodeValue(in: JsonReader, default: java.time.Instant): java.time.Instant = in.readInstant(default)

    def encodeValue(x: java.time.Instant, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Instant = in.readKeyAsInstant()

    override def encodeKey(x: java.time.Instant, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("date-time"))
  }
  val localDateCodec: JsonBinaryCodec[LocalDate] = new JsonBinaryCodec[java.time.LocalDate]() {
    def decodeValue(in: JsonReader, default: java.time.LocalDate): java.time.LocalDate = in.readLocalDate(default)

    def encodeValue(x: java.time.LocalDate, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalDate = in.readKeyAsLocalDate()

    override def encodeKey(x: java.time.LocalDate, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("date"))
  }
  val localDateTimeCodec: JsonBinaryCodec[LocalDateTime] = new JsonBinaryCodec[java.time.LocalDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.LocalDateTime): java.time.LocalDateTime =
      in.readLocalDateTime(default)

    def encodeValue(x: java.time.LocalDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalDateTime = in.readKeyAsLocalDateTime()

    override def encodeKey(x: java.time.LocalDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("date-time"))
  }
  val localTimeCodec: JsonBinaryCodec[LocalTime] = new JsonBinaryCodec[java.time.LocalTime]() {
    def decodeValue(in: JsonReader, default: java.time.LocalTime): java.time.LocalTime = in.readLocalTime(default)

    def encodeValue(x: java.time.LocalTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.LocalTime = in.readKeyAsLocalTime()

    override def encodeKey(x: java.time.LocalTime, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("time"))
  }
  val monthCodec: JsonBinaryCodec[Month] = new JsonBinaryCodec[java.time.Month]() {
    def decodeValue(in: JsonReader, default: java.time.Month): java.time.Month = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    def encodeValue(x: java.time.Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.time.Month = {
      val code = in.readKeyAsString()
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    override def encodeKey(x: java.time.Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val monthDayCodec: JsonBinaryCodec[MonthDay] = new JsonBinaryCodec[java.time.MonthDay]() {
    def decodeValue(in: JsonReader, default: java.time.MonthDay): java.time.MonthDay = in.readMonthDay(default)

    def encodeValue(x: java.time.MonthDay, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.MonthDay = in.readKeyAsMonthDay()

    override def encodeKey(x: java.time.MonthDay, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val offsetDateTimeCodec: JsonBinaryCodec[OffsetDateTime] = new JsonBinaryCodec[java.time.OffsetDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime =
      in.readOffsetDateTime(default)

    def encodeValue(x: java.time.OffsetDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.OffsetDateTime = in.readKeyAsOffsetDateTime()

    override def encodeKey(x: java.time.OffsetDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("date-time"))
  }
  val offsetTimeCodec: JsonBinaryCodec[OffsetTime] = new JsonBinaryCodec[java.time.OffsetTime]() {
    def decodeValue(in: JsonReader, default: java.time.OffsetTime): java.time.OffsetTime = in.readOffsetTime(default)

    def encodeValue(x: java.time.OffsetTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.OffsetTime = in.readKeyAsOffsetTime()

    override def encodeKey(x: java.time.OffsetTime, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("time"))
  }
  val periodCodec: JsonBinaryCodec[Period] = new JsonBinaryCodec[java.time.Period]() {
    def decodeValue(in: JsonReader, default: java.time.Period): java.time.Period = in.readPeriod(default)

    def encodeValue(x: java.time.Period, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Period = in.readKeyAsPeriod()

    override def encodeKey(x: java.time.Period, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("duration"))
  }
  val yearCodec: JsonBinaryCodec[Year] = new JsonBinaryCodec[java.time.Year]() {
    def decodeValue(in: JsonReader, default: java.time.Year): java.time.Year = in.readYear(default)

    def encodeValue(x: java.time.Year, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.Year = in.readKeyAsYear()

    override def encodeKey(x: java.time.Year, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val yearMonthCodec: JsonBinaryCodec[YearMonth] = new JsonBinaryCodec[java.time.YearMonth]() {
    def decodeValue(in: JsonReader, default: java.time.YearMonth): java.time.YearMonth = in.readYearMonth(default)

    def encodeValue(x: java.time.YearMonth, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.YearMonth = in.readKeyAsYearMonth()

    override def encodeKey(x: java.time.YearMonth, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val zoneIdCodec: JsonBinaryCodec[ZoneId] = new JsonBinaryCodec[java.time.ZoneId]() {
    def decodeValue(in: JsonReader, default: java.time.ZoneId): java.time.ZoneId = in.readZoneId(default)

    def encodeValue(x: java.time.ZoneId, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZoneId = in.readKeyAsZoneId()

    override def encodeKey(x: java.time.ZoneId, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val zoneOffsetCodec: JsonBinaryCodec[ZoneOffset] = new JsonBinaryCodec[java.time.ZoneOffset]() {
    def decodeValue(in: JsonReader, default: java.time.ZoneOffset): java.time.ZoneOffset = in.readZoneOffset(default)

    def encodeValue(x: java.time.ZoneOffset, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZoneOffset = in.readKeyAsZoneOffset()

    override def encodeKey(x: java.time.ZoneOffset, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val zonedDateTimeCodec: JsonBinaryCodec[ZonedDateTime] = new JsonBinaryCodec[java.time.ZonedDateTime]() {
    def decodeValue(in: JsonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime =
      in.readZonedDateTime(default)

    def encodeValue(x: java.time.ZonedDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.time.ZonedDateTime = in.readKeyAsZonedDateTime()

    override def encodeKey(x: java.time.ZonedDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("date-time"))
  }
  val currencyCodec: JsonBinaryCodec[Currency] = new JsonBinaryCodec[java.util.Currency]() {
    def decodeValue(in: JsonReader, default: java.util.Currency): java.util.Currency = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency value")
      }
    }

    def encodeValue(x: java.util.Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: JsonReader): java.util.Currency = {
      val code = in.readKeyAsString()
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency value")
      }
    }

    override def encodeKey(x: java.util.Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def toJsonSchema: JsonSchema = JsonSchema.string()
  }
  val uuidCodec: JsonBinaryCodec[UUID] = new JsonBinaryCodec[java.util.UUID]() {
    def decodeValue(in: JsonReader, default: java.util.UUID): java.util.UUID = in.readUUID(default)

    def encodeValue(x: java.util.UUID, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: JsonReader): java.util.UUID = in.readKeyAsUUID()

    override def encodeKey(x: java.util.UUID, out: JsonWriter): Unit = out.writeKey(x)

    override def toJsonSchema: JsonSchema = JsonSchema.string(format = Some("uuid"))
  }
  val dynamicValueCodec: JsonBinaryCodec[DynamicValue] = new JsonBinaryCodec[DynamicValue]() {
    private[this] val falseValue       = new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
    private[this] val trueValue        = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
    private[this] val emptyArrayValue  = new DynamicValue.Sequence(Chunk.empty)
    private[this] val emptyObjectValue = new DynamicValue.Map(Chunk.empty)

    def decodeValue(in: JsonReader, default: DynamicValue): DynamicValue = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        new DynamicValue.Primitive(new PrimitiveValue.String(in.readString(null)))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        if (in.readBoolean()) trueValue
        else falseValue
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        val n = in.readBigDecimal(null)
        new DynamicValue.Primitive({
          val longValue = n.bigDecimal.longValue
          if (n == BigDecimal(longValue)) {
            val intValue = longValue.toInt
            if (longValue == intValue) new PrimitiveValue.Int(intValue)
            else new PrimitiveValue.Long(longValue)
          } else new PrimitiveValue.BigDecimal(n)
        })
      } else if (b == '[') {
        if (in.isNextToken(']')) emptyArrayValue
        else {
          in.rollbackToken()
          val builder = ChunkBuilder.make[DynamicValue]()
          while ({
            builder.addOne(decodeValue(in, default))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken(']')) new DynamicValue.Sequence(builder.result())
          else in.arrayEndOrCommaError()
        }
      } else if (b == '{') {
        if (in.isNextToken('}')) emptyObjectValue
        else {
          in.rollbackToken()
          val builder = ChunkBuilder.make[(String, DynamicValue)]()
          while ({
            builder.addOne((in.readKeyAsString(), decodeValue(in, default)))
            in.isNextToken(',')
          }) ()
          if (in.isCurrentToken('}')) new DynamicValue.Record(builder.result())
          else in.objectEndOrCommaError()
        }
      } else {
        in.rollbackToken()
        in.readNullOrError(DynamicValue.Null, "expected JSON value")
      }
    }

    def encodeValue(x: DynamicValue, out: JsonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => out.writeObjectStart(); out.writeObjectEnd()
          case v: PrimitiveValue.Boolean        => out.writeVal(v.value)
          case v: PrimitiveValue.Byte           => out.writeVal(v.value)
          case v: PrimitiveValue.Short          => out.writeVal(v.value)
          case v: PrimitiveValue.Int            => out.writeVal(v.value)
          case v: PrimitiveValue.Long           => out.writeVal(v.value)
          case v: PrimitiveValue.Float          => out.writeVal(v.value)
          case v: PrimitiveValue.Double         => out.writeVal(v.value)
          case v: PrimitiveValue.Char           => out.writeVal(v.value)
          case v: PrimitiveValue.String         => out.writeVal(v.value)
          case v: PrimitiveValue.BigInt         => out.writeVal(v.value)
          case v: PrimitiveValue.BigDecimal     => out.writeVal(v.value)
          case v: PrimitiveValue.DayOfWeek      => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.Duration       => out.writeVal(v.value)
          case v: PrimitiveValue.Instant        => out.writeVal(v.value)
          case v: PrimitiveValue.LocalDate      => out.writeVal(v.value)
          case v: PrimitiveValue.LocalDateTime  => out.writeVal(v.value)
          case v: PrimitiveValue.LocalTime      => out.writeVal(v.value)
          case v: PrimitiveValue.Month          => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.MonthDay       => out.writeVal(v.value)
          case v: PrimitiveValue.OffsetDateTime => out.writeVal(v.value)
          case v: PrimitiveValue.OffsetTime     => out.writeVal(v.value)
          case v: PrimitiveValue.Period         => out.writeVal(v.value)
          case v: PrimitiveValue.Year           => out.writeVal(v.value)
          case v: PrimitiveValue.YearMonth      => out.writeVal(v.value)
          case v: PrimitiveValue.ZoneId         => out.writeVal(v.value)
          case v: PrimitiveValue.ZoneOffset     => out.writeVal(v.value)
          case v: PrimitiveValue.ZonedDateTime  => out.writeVal(v.value)
          case v: PrimitiveValue.Currency       => out.writeNonEscapedAsciiVal(v.value.toString)
          case v: PrimitiveValue.UUID           => out.writeVal(v.value)
        }
      case record: DynamicValue.Record =>
        out.writeObjectStart()
        val fields = record.fields
        val it     = fields.iterator
        while (it.hasNext) {
          val kv = it.next()
          out.writeKey(kv._1)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case variant: DynamicValue.Variant =>
        out.writeObjectStart()
        out.writeKey(variant.caseNameValue)
        encodeValue(variant.value, out)
        out.writeObjectEnd()
      case sequence: DynamicValue.Sequence =>
        out.writeArrayStart()
        val elements = sequence.elements
        val it       = elements.iterator
        while (it.hasNext) {
          encodeValue(it.next(), out)
        }
        out.writeArrayEnd()
      case map: DynamicValue.Map =>
        out.writeObjectStart()
        val entries = map.entries
        val it      = entries.iterator
        while (it.hasNext) {
          val kv = it.next()
          encodeKey(kv._1, out)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case DynamicValue.Null =>
        out.writeNull()
    }

    override def encodeKey(x: DynamicValue, out: JsonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => out.encodeError("encoding as JSON key is not supported")
          case v: PrimitiveValue.Boolean        => out.writeKey(v.value)
          case v: PrimitiveValue.Byte           => out.writeKey(v.value)
          case v: PrimitiveValue.Short          => out.writeKey(v.value)
          case v: PrimitiveValue.Int            => out.writeKey(v.value)
          case v: PrimitiveValue.Long           => out.writeKey(v.value)
          case v: PrimitiveValue.Float          => out.writeKey(v.value)
          case v: PrimitiveValue.Double         => out.writeKey(v.value)
          case v: PrimitiveValue.Char           => out.writeKey(v.value)
          case v: PrimitiveValue.String         => out.writeKey(v.value)
          case v: PrimitiveValue.BigInt         => out.writeKey(v.value)
          case v: PrimitiveValue.BigDecimal     => out.writeKey(v.value)
          case v: PrimitiveValue.DayOfWeek      => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.Duration       => out.writeKey(v.value)
          case v: PrimitiveValue.Instant        => out.writeKey(v.value)
          case v: PrimitiveValue.LocalDate      => out.writeKey(v.value)
          case v: PrimitiveValue.LocalDateTime  => out.writeKey(v.value)
          case v: PrimitiveValue.LocalTime      => out.writeKey(v.value)
          case v: PrimitiveValue.Month          => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.MonthDay       => out.writeKey(v.value)
          case v: PrimitiveValue.OffsetDateTime => out.writeKey(v.value)
          case v: PrimitiveValue.OffsetTime     => out.writeKey(v.value)
          case v: PrimitiveValue.Period         => out.writeKey(v.value)
          case v: PrimitiveValue.Year           => out.writeKey(v.value)
          case v: PrimitiveValue.YearMonth      => out.writeKey(v.value)
          case v: PrimitiveValue.ZoneId         => out.writeKey(v.value)
          case v: PrimitiveValue.ZoneOffset     => out.writeKey(v.value)
          case v: PrimitiveValue.ZonedDateTime  => out.writeKey(v.value)
          case v: PrimitiveValue.Currency       => out.writeNonEscapedAsciiKey(v.value.toString)
          case v: PrimitiveValue.UUID           => out.writeKey(v.value)
        }
      case _ => out.encodeError("encoding as JSON key is not supported")
    }

    override def toJsonSchema: JsonSchema = JsonSchema.True
  }
}
