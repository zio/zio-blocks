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

package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, NonEmptyChunk}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.binding.Registers
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Reads and writes values of type `A` as JSON: UTF-8 bytes, strings, streams,
 * byte buffers, or [[Json]] trees. Get the codec of a type with a schema from
 * `Schema#jsonCodec` (derived with the default [[JsonCodecDeriver]]), derive a
 * configured one with `Schema#derive(JsonCodecDeriver.with...)`, or write one
 * by implementing the two streaming methods, `decodeValue(JsonReader)` and
 * `encodeValue(A, JsonWriter)`.
 *
 * Every `decode` method returns `Left` with one
 * [[zio.blocks.schema.SchemaError]], for the first problem found, as an
 * expectation mismatch that carries the reader's message and the path, such as
 * `missing required field "year" at: .`. The `encode` methods throw a
 * [[JsonCodecError]] for a value they cannot write, such as NaN.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val codec: JsonCodec[Book] = Schema[Book].jsonCodec
 *
 * codec.encodeToString(Book("Zionomicon", 2021))          // {"title":"Zionomicon","year":2021}
 * codec.decode("""{"title":"Zionomicon","year":2021}""") // Right(Book(Zionomicon,2021))
 * codec.decode("""{"title":"Zionomicon"}""")             // Left(zio.blocks.schema.SchemaError: missing required field "year" at: .)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/json/ JSON]]
 */
abstract class JsonCodec[A] extends BinaryCodec[A] {

  /**
   * Attempts to decode a value of type `A` from the specified `JsonReader`, but
   * may fail with `JsonCodecError` error if the JSON input does not encode a
   * value of this type.
   *
   * @param in
   *   an instance of `JsonReader` that provides access to the JSON input to
   *   parse a JSON value to value of type `A`
   */
  def decodeValue(in: JsonReader): A

  /**
   * Encodes the specified value using provided `JsonWriter`, but may fail with
   * `JsonCodecError` if it cannot be encoded properly according to RFC-8259
   * requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` that provides access to JSON output to
   *   serialize the specified value as a JSON value
   */
  def encodeValue(x: A, out: JsonWriter): Unit

  /**
   * Decodes a [[Json]] tree into an `A`. The built-in and derived codecs
   * implement it; the default throws a [[JsonCodecError]] ("decoding from JSON
   * AST is not supported"), so a custom codec that implements only the
   * streaming methods cannot decode a `Json` value.
   */
  def decodeValue(json: Json): A = error("decoding from JSON AST is not supported")

  /**
   * Encodes an `A` as a [[Json]] tree. The built-in and derived codecs
   * implement it; the default throws a [[JsonCodecError]] ("encoding to JSON
   * AST is not supported").
   */
  def encodeValue(x: A): Json = error("encoding to JSON AST is not supported")

  /**
   * Decodes an object key into an `A`. The default parses the key's text as a
   * JSON value of `A`, the inverse of the default `encodeKey`; the codecs of
   * primitive types read the key directly, so `"12"` is an `Int` key.
   *
   * @param in
   *   the reader, positioned at the key
   */
  def decodeKey(in: JsonReader): A = decodeUnsafe(in.readKeyAsString())

  /**
   * Writes `x` as an object key. The default writes the compact JSON of `x` as
   * the key's text, so a record key becomes a string such as `"{\"id\":1}"`;
   * the codecs of primitive types write the plain value.
   *
   * @param x
   *   the key
   * @param out
   *   the writer, positioned where the key goes
   */
  def encodeKey(x: A, out: JsonWriter): Unit = out.writeKey(encodeToString(x))

  /**
   * Decodes the text of an object key into an `A`, as `decodeKey(JsonReader)`
   * does; it throws a [[JsonCodecError]] when the text is not a key of `A`.
   */
  def decodeKey(s: String): A = decodeUnsafe(s)

  /**
   * Returns the text of `x` as an object key, as `encodeKey(A, JsonWriter)`
   * writes it.
   */
  def encodeKey(x: A): String = encodeToString(x)

  /**
   * Returns a [[JsonSchema]] of the JSON this codec writes. The built-in and
   * derived codecs describe their types; the default, for a custom codec, is
   * [[JsonSchema.True]], which accepts anything.
   */
  def toJsonSchema: JsonSchema = JsonSchema.True

  /**
   * Decodes a value of type `A` from the UTF-8 bytes of `input`, from its
   * position, with the default `ReaderConfig`. If decoding fails, a
   * `SchemaError` is returned.
   *
   * @param input
   *   the `ByteBuffer` containing the binary data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Writes `value` as compact UTF-8 JSON into `output`, from its position.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `ByteBuffer` where the encoded binary data is written
   * @throws java.nio.BufferOverflowException
   *   when the JSON does not fit; `output` then holds part of it (a known bug)
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
      var reader = JsonCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
    }

  /**
   * Writes `value` as UTF-8 JSON formatted by `config` into `output`, from its
   * position.
   *
   * @param value
   *   the value of type `A` to be serialized into binary format
   * @param output
   *   the `ByteBuffer` where the encoded binary data is written
   * @param config
   *   the `WriterConfig` instance used to configure the encoding process
   * @throws java.nio.BufferOverflowException
   *   when the JSON does not fit; `output` then holds part of it (a known bug)
   */
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    var writer = JsonCodec.writerPool.get
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
   * Decodes a value of type `A` from the provided byte array using the
   * specified `from` and `to` slice indices. If decoding fails, an error of
   * type `SchemaError` is returned.
   *
   * @param input
   *   the byte array containing the binary data to be decoded
   * @param from
   *   the start index of the slice (inclusive)
   * @param to
   *   the end index of the slice (exclusive)
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Array[Byte], from: Int, to: Int): Either[SchemaError, A] = decode(input, from, to, ReaderConfig)

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
      var reader = JsonCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(input, config)
      new Right(reader.read(this, input, 0, input.length, config))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
    }

  /**
   * Decodes a value of type `A` from the provided byte array using the
   * specified `from` and `to` slice indices and `ReaderConfig`. If decoding
   * fails, an error of type `SchemaError` is returned.
   *
   * @param input
   *   the byte array containing the binary data to be decoded
   * @param from
   *   the start index of the slice (inclusive)
   * @param to
   *   the end index of the slice (exclusive)
   * @param config
   *   the `ReaderConfig` instance used to configure the decoding process
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Array[Byte], from: Int, to: Int, config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = JsonCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(input, config)
      new Right(reader.read(this, input, from, to, config))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
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
    var writer = JsonCodec.writerPool.get
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
   * Bytes read past the end of the value are dropped, so one stream cannot hold
   * several values (a known bug).
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
      var reader = JsonCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
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
    var writer = JsonCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.write(this, value, output, config)
  }

  /**
   * Decodes a value of type `A` from the given string using the default
   * `ReaderConfig`. If decoding fails, a `SchemaError` is returned. Text after
   * the value, other than whitespace, is an error.
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
      var reader = JsonCodec.readerPool.get
      if (reader.isInUse) reader = jsonReader(buf, config)
      new Right(reader.read(this, buf, 0, buf.length, config))
    } catch {
      case err if NonFatal(err) => new Left(toError(err))
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
    var writer = JsonCodec.writerPool.get
    if (writer.isInUse) writer = jsonWriter(config)
    writer.writeToString(this, value, config)
  }

  /**
   * Decodes a value of type `A` from the given `Json` value, with
   * `decodeValue(Json)`. If decoding fails, a `SchemaError` is returned.
   *
   * @param input
   *   the `Json` value containing the data to be decoded
   * @return
   *   `Either` where the `Left` contains a `SchemaError` if decoding fails, or
   *   the `Right` contains the successfully decoded value of type `A`
   */
  def decode(input: Json): Either[SchemaError, A] =
    try new Right(decodeValue(input))
    catch {
      case err if NonFatal(err) => new Left(toError(err))
    }

  /**
   * Encodes the specified value of type `A` into a `Json` value, with
   * `encodeValue(A)`; it throws a [[JsonCodecError]] for a custom codec that
   * does not implement it.
   *
   * @param value
   *   the value of type `A` to be serialized into `Json` value
   * @return
   *   a `Json` value representing the encoded data
   */
  def encodeToJson(value: A): Json = encodeValue(value)

  /**
   * Throws a [[JsonCodecError]] wrapping the given error and adding a span.
   *
   * @param span
   *   the span to add to the error
   * @param error
   *   the error to wrap
   * @throws JsonCodecError
   *   always
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: JsonCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ => throw new JsonCodecError(new ::(span, Nil), error.getMessage)
  }

  /**
   * Throws a [[JsonCodecError]] wrapping the given error and adding two spans.
   *
   * @param span1
   *   the first span to add to the error
   * @param span2
   *   the second span to add to the error
   * @param error
   *   the error to wrap
   * @throws JsonCodecError
   *   always
   */
  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: JsonCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ => throw new JsonCodecError(new ::(span1, new ::(span2, Nil)), error.getMessage)
  }

  /**
   * Throws a [[JsonCodecError]] wrapping the given error and adding spans.
   *
   * @param spans
   *   spans to add to the error
   * @param error
   *   the error to wrap
   * @throws JsonCodecError
   *   always
   */
  def error(spans: List[DynamicOptic.Node], error: Throwable): Nothing = error match {
    case e: JsonCodecError =>
      e.spans = spans.foldLeft(e.spans)((ss, s) => s :: ss)
      throw e
    case _ => throw new JsonCodecError(spans, error.getMessage)
  }

  /**
   * Throws a [[JsonCodecError]] with the given error message.
   *
   * @param message
   *   the error message
   * @throws JsonCodecError
   *   always
   */
  def error(message: String): Nothing = throw new JsonCodecError(Nil, message)

  private[schema] def decodeUnsafe(input: String): A = {
    val buf    = input.getBytes(UTF_8)
    var reader = JsonCodec.readerPool.get
    if (reader.isInUse) reader = jsonReader(buf, ReaderConfig)
    reader.read(this, buf, 0, buf.length, ReaderConfig)
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
          case e: JsonCodecError =>
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
 * The JSON codecs of the primitive types and of
 * [[zio.blocks.schema.DynamicValue]], which [[JsonCodecDeriver]] uses for the
 * fields of derived codecs. The `decode` and `encode` methods reuse one reader
 * and one writer per thread.
 */
object JsonCodec {
  private val readerPool: ThreadLocal[JsonReader] = new ThreadLocal[JsonReader] {
    override def initialValue(): JsonReader = new JsonReader
  }
  private val writerPool: ThreadLocal[JsonWriter] = new ThreadLocal[JsonWriter] {
    override def initialValue(): JsonWriter = new JsonWriter
  }

  /**
   * The codec of `Unit`: the empty object `{}`, and nothing else, on decoding.
   */
  val unitCodec: JsonCodec[Unit] = new JsonCodec[Unit] {
    def decodeValue(in: JsonReader): Unit =
      if (in.isNextToken('{') && in.isNextToken('}')) ()
      else error("expected an empty JSON object")

    def encodeValue(x: Unit, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeObjectEnd()
    }

    override def decodeValue(json: Json): Unit = json match {
      case o: Json.Object if o.value.isEmpty => ()
      case _                                 => error("expected an empty Json.Object")
    }

    override def encodeValue(x: Unit): Json = Json.Object.empty

    override val toJsonSchema: JsonSchema = JsonSchema.obj(maxProperties = NonNegativeInt(0))
  }

  /**
   * The codec of `Boolean`: `true` or `false`; as a key, `"true"` or `"false"`.
   */
  val booleanCodec: JsonCodec[Boolean] = new JsonCodec[Boolean] {
    def decodeValue(in: JsonReader): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Boolean = json match {
      case bool: Json.Boolean => bool.value
      case _                  => error("expected Json.Boolean")
    }

    override def encodeValue(x: Boolean): Json = Json.Boolean(x)

    override def decodeKey(in: JsonReader): Boolean = in.readKeyAsBoolean()

    override def encodeKey(x: Boolean, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Boolean =
      if (s == "true") true
      else if (s == "false") false
      else error("expected String with a Boolean value")

    override def encodeKey(x: Boolean): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.boolean
  }

  /**
   * The codec of `Byte`: a JSON integer from -128 to 127; a fraction or a value
   * out of range fails.
   */
  val byteCodec: JsonCodec[Byte] = new JsonCodec[Byte] {
    def decodeValue(in: JsonReader): Byte = in.readByte()

    def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Byte = json match {
      case num: Json.Number =>
        try num.value.bigDecimal.byteValueExact
        catch {
          case err if NonFatal(err) => error("value is too large for byte")
        }
      case _ => error("expected Json.Number with a Byte value")
    }

    override def encodeValue(x: Byte): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Byte = in.readKeyAsByte()

    override def encodeKey(x: Byte, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Byte =
      try java.lang.Byte.parseByte(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Byte value")
      }

    override def encodeKey(x: Byte): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = new Some(BigDecimal(Byte.MinValue)),
      maximum = new Some(BigDecimal(Byte.MaxValue))
    )
  }

  /**
   * The codec of `Short`: a JSON integer in the `Short` range; a fraction or a
   * value out of range fails.
   */
  val shortCodec: JsonCodec[Short] = new JsonCodec[Short] {
    def decodeValue(in: JsonReader): Short = in.readShort()

    def encodeValue(x: Short, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Short = json match {
      case num: Json.Number =>
        try num.value.bigDecimal.shortValueExact
        catch {
          case err if NonFatal(err) => error("value is too large for short")
        }
      case _ => error("expected Json.Number with a Short value")
    }

    override def encodeValue(x: Short): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Short = in.readKeyAsShort()

    override def encodeKey(x: Short, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Short =
      try java.lang.Short.parseShort(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Short value")
      }

    override def encodeKey(x: Short): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = new Some(BigDecimal(Short.MinValue)),
      maximum = new Some(BigDecimal(Short.MaxValue))
    )
  }

  /**
   * The codec of `Int`: a JSON integer in the `Int` range. A fraction or a
   * value out of range fails; from a `Json` tree both report "value is too
   * large for int".
   */
  val intCodec: JsonCodec[Int] = new JsonCodec[Int] {
    def decodeValue(in: JsonReader): Int = in.readInt()

    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Int = json match {
      case num: Json.Number =>
        try num.value.bigDecimal.intValueExact
        catch {
          case err if NonFatal(err) => error("value is too large for int")
        }
      case _ => error("expected Json.Number with a Int value")
    }

    override def encodeValue(x: Int): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Int = in.readKeyAsInt()

    override def encodeKey(x: Int, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Int =
      try java.lang.Integer.parseInt(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Int value")
      }

    override def encodeKey(x: Int): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = new Some(BigDecimal(Int.MinValue)),
      maximum = new Some(BigDecimal(Int.MaxValue))
    )
  }

  /**
   * The codec of `Long`: a JSON integer in the `Long` range; a fraction or a
   * value out of range fails.
   */
  val longCodec: JsonCodec[Long] = new JsonCodec[Long] {
    def decodeValue(in: JsonReader): Long = in.readLong()

    def encodeValue(x: Long, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Long = json match {
      case num: Json.Number =>
        try num.value.bigDecimal.longValueExact
        catch {
          case err if NonFatal(err) => error("value is too large for long")
        }
      case _ => error("expected Json.Number with a Long value")
    }

    override def encodeValue(x: Long): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Long = in.readKeyAsLong()

    override def encodeKey(x: Long, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Long =
      try java.lang.Long.parseLong(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Long value")
      }

    override def encodeKey(x: Long): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.integer(
      minimum = new Some(BigDecimal(Long.MinValue)),
      maximum = new Some(BigDecimal(Long.MaxValue))
    )
  }

  /**
   * The codec of `Float`: a JSON number. A number beyond the `Float` range
   * decodes to an infinity, and NaN and the infinities fail to encode.
   */
  val floatCodec: JsonCodec[Float] = new JsonCodec[Float] {
    def decodeValue(in: JsonReader): Float = in.readFloat()

    def encodeValue(x: Float, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Float = json match {
      case num: Json.Number => num.value.bigDecimal.floatValue
      case _                => error("expected Json.Number with a Float value")
    }

    override def encodeValue(x: Float): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Float = in.readKeyAsFloat()

    override def encodeKey(x: Float, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Float =
      try java.lang.Float.parseFloat(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Float value")
      }

    override def encodeKey(x: Float): String = encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.number()
  }

  /**
   * The codec of `Double`: a JSON number. A number beyond the `Double` range,
   * such as `1e400`, decodes to an infinity, and NaN and the infinities fail to
   * encode.
   */
  val doubleCodec: JsonCodec[Double] = new JsonCodec[Double] {
    def decodeValue(in: JsonReader): Double = in.readDouble()

    def encodeValue(x: Double, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Double = json match {
      case num: Json.Number => num.value.bigDecimal.doubleValue
      case _                => error("expected Json.Number with a Double value")
    }

    override def encodeValue(x: Double): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): Double = in.readKeyAsDouble()

    override def encodeKey(x: Double, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Double =
      try java.lang.Double.parseDouble(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Double value")
      }

    override def encodeKey(x: Double): String = encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.number()
  }

  /** The codec of `Char`: a string of exactly one character. */
  val charCodec: JsonCodec[Char] = new JsonCodec[Char] {
    def decodeValue(in: JsonReader): Char = in.readChar()

    def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Char = {
      json match {
        case s: Json.String =>
          val str = s.value
          if (str.length == 1) return str.charAt(0)
        case _ =>
      }
      error("expected Json.String with a Char value")
    }

    override def encodeValue(x: Char): Json = new Json.String(String.valueOf(x))

    override def decodeKey(in: JsonReader): Char = in.readKeyAsChar()

    override def encodeKey(x: Char, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Char =
      if (s.length == 1) s.charAt(0)
      else error("expected String with a Char value")

    override def encodeKey(x: Char): String = String.valueOf(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(
      minLength = new Some(NonNegativeInt.one),
      maxLength = new Some(NonNegativeInt.one)
    )
  }

  /** The codec of `String`: a JSON string; as a key, the key itself. */
  val stringCodec: JsonCodec[String] = new JsonCodec[String] {
    def decodeValue(in: JsonReader): String = in.readString()

    def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): String = json match {
      case s: Json.String => s.value
      case _              => error("expected Json.String")
    }

    override def encodeValue(x: String): Json = new Json.String(x)

    override def decodeKey(in: JsonReader): String = in.readKeyAsString()

    override def encodeKey(x: String, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): String = s

    override def encodeKey(x: String): String = x

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /** The codec of `BigInt`: a JSON integer of up to 308 digits. */
  val bigIntCodec: JsonCodec[BigInt] = new JsonCodec[BigInt] {
    def decodeValue(in: JsonReader): BigInt = in.readBigInt()

    def encodeValue(x: BigInt, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): BigInt = {
      json match {
        case num: Json.Number =>
          try return BigInt(num.value.bigDecimal.toBigIntegerExact)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.Number with a BigInt value")
    }

    override def encodeValue(x: BigInt): Json = Json.Number(x)

    override def decodeKey(in: JsonReader): BigInt = in.readKeyAsBigInt()

    override def encodeKey(x: BigInt, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): BigInt =
      try BigInt(s)
      catch {
        case err if NonFatal(err) => error("expected String with a BigInt value")
      }

    override def encodeKey(x: BigInt): String = encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.integer()
  }

  /**
   * The codec of `BigDecimal`: a JSON number of up to 308 digits, rounded to 34
   * significant digits on decoding.
   */
  val bigDecimalCodec: JsonCodec[BigDecimal] = new JsonCodec[BigDecimal] {
    def decodeValue(in: JsonReader): BigDecimal = in.readBigDecimal()

    def encodeValue(x: BigDecimal, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): BigDecimal = json match {
      case num: Json.Number => num.value
      case _                => error("expected Json.Number")
    }

    override def encodeValue(x: BigDecimal): Json = new Json.Number(x)

    override def decodeKey(in: JsonReader): BigDecimal = in.readKeyAsBigDecimal()

    override def encodeKey(x: BigDecimal, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): BigDecimal =
      try BigDecimal(s)
      catch {
        case err if NonFatal(err) => error("expected String with a BigDecimal value")
      }

    override def encodeKey(x: BigDecimal): String = encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.number()
  }

  /**
   * The codec of `java.time.DayOfWeek`: its upper-case name, such as
   * `"MONDAY"`.
   */
  val dayOfWeekCodec: JsonCodec[DayOfWeek] = new JsonCodec[DayOfWeek] {
    def decodeValue(in: JsonReader): DayOfWeek = {
      val code = in.readString()
      try DayOfWeek.valueOf(code)
      catch {
        case err if NonFatal(err) => error("illegal day of week value")
      }
    }

    def encodeValue(x: DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeValue(json: Json): DayOfWeek = {
      json match {
        case s: Json.String =>
          try return DayOfWeek.valueOf(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a DayOfWeek value")
    }

    override def encodeValue(x: DayOfWeek): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): DayOfWeek =
      try DayOfWeek.valueOf(in.readKeyAsString())
      catch {
        case err if NonFatal(err) => error("illegal day of week value")
      }

    override def encodeKey(x: DayOfWeek, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def decodeKey(s: String): DayOfWeek =
      try DayOfWeek.valueOf(s)
      catch {
        case err if NonFatal(err) => error("expected String with a DayOfWeek value")
      }

    override def encodeKey(x: DayOfWeek): String = x.toString

    override val toJsonSchema: JsonSchema =
      JsonSchema.enumOfStrings(NonEmptyChunk.fromIterableOption(DayOfWeek.values().map(_.toString)).get)
  }

  /**
   * The codec of `java.time.Duration`: an ISO-8601 string, such as `"PT1H30M"`.
   */
  val durationCodec: JsonCodec[Duration] = new JsonCodec[Duration] {
    def decodeValue(in: JsonReader): Duration = in.readDuration()

    def encodeValue(x: Duration, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Duration = json match {
      case s: Json.String => Json.durationRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a Duration value")
    }

    override def encodeValue(x: Duration): Json = new Json.String(Json.durationRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): Duration = in.readKeyAsDuration()

    override def encodeKey(x: Duration, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Duration = Json.durationRawCodec.decodeUnsafe(s)

    override def encodeKey(x: Duration): String = Json.durationRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("duration"))
  }

  /**
   * The codec of `java.time.Instant`: an ISO-8601 string in UTC, such as
   * `"2021-03-14T09:30:00Z"`.
   */
  val instantCodec: JsonCodec[Instant] = new JsonCodec[Instant] {
    def decodeValue(in: JsonReader): Instant = in.readInstant()

    def encodeValue(x: Instant, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Instant = json match {
      case s: Json.String => Json.instantRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a Instant value")
    }

    override def encodeValue(x: Instant): Json = new Json.String(Json.instantRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): Instant = in.readKeyAsInstant()

    override def encodeKey(x: Instant, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Instant = Json.instantRawCodec.decodeUnsafe(s)

    override def encodeKey(x: Instant): String = Json.instantRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("date-time"))
  }

  /**
   * The codec of `java.time.LocalDate`: an ISO-8601 string, such as
   * `"2021-03-14"`.
   */
  val localDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
    def decodeValue(in: JsonReader): LocalDate = in.readLocalDate()

    def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): LocalDate = json match {
      case s: Json.String => Json.localDateRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a LocalDate value")
    }

    override def encodeValue(x: LocalDate): Json = new Json.String(Json.localDateRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): LocalDate = in.readKeyAsLocalDate()

    override def encodeKey(x: LocalDate, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): LocalDate = Json.localDateRawCodec.decodeUnsafe(s)

    override def encodeKey(x: LocalDate): String = Json.localDateRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("date"))
  }

  /**
   * The codec of `java.time.LocalDateTime`: an ISO-8601 string, such as
   * `"2021-03-14T09:30"`.
   */
  val localDateTimeCodec: JsonCodec[LocalDateTime] = new JsonCodec[LocalDateTime] {
    def decodeValue(in: JsonReader): LocalDateTime = in.readLocalDateTime()

    def encodeValue(x: LocalDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): LocalDateTime = json match {
      case s: Json.String => Json.localDateTimeRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a LocalDateTime value")
    }

    override def encodeValue(x: LocalDateTime): Json = new Json.String(Json.localDateTimeRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): LocalDateTime = in.readKeyAsLocalDateTime()

    override def encodeKey(x: LocalDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): LocalDateTime = Json.localDateTimeRawCodec.decodeUnsafe(s)

    override def encodeKey(x: LocalDateTime): String = Json.localDateTimeRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("date-time"))
  }

  /**
   * The codec of `java.time.LocalTime`: an ISO-8601 string, such as `"09:30"`.
   */
  val localTimeCodec: JsonCodec[LocalTime] = new JsonCodec[LocalTime] {
    def decodeValue(in: JsonReader): LocalTime = in.readLocalTime()

    def encodeValue(x: LocalTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): LocalTime = json match {
      case s: Json.String => Json.localTimeRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a LocalTime value")
    }

    override def encodeValue(x: LocalTime): Json = new Json.String(Json.localTimeRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): LocalTime = in.readKeyAsLocalTime()

    override def encodeKey(x: LocalTime, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): LocalTime = Json.localTimeRawCodec.decodeUnsafe(s)

    override def encodeKey(x: LocalTime): String = Json.localTimeRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("time"))
  }

  /** The codec of `java.time.Month`: its upper-case name, such as `"MARCH"`. */
  val monthCodec: JsonCodec[Month] = new JsonCodec[Month] {
    def decodeValue(in: JsonReader): Month = {
      val code = in.readString()
      try Month.valueOf(code)
      catch {
        case err if NonFatal(err) => error("illegal month value")
      }
    }

    def encodeValue(x: Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeValue(json: Json): Month = {
      json match {
        case s: Json.String =>
          try return Month.valueOf(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a Month value")
    }

    override def encodeValue(x: Month): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): Month = {
      val code = in.readKeyAsString()
      try Month.valueOf(code)
      catch {
        case err if NonFatal(err) => error("illegal month value")
      }
    }

    override def encodeKey(x: Month, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def decodeKey(s: String): Month =
      try Month.valueOf(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Month value")
      }

    override def encodeKey(x: Month): String = x.toString

    override val toJsonSchema: JsonSchema =
      JsonSchema.enumOfStrings(NonEmptyChunk.fromIterableOption(Month.values().map(_.toString)).get)
  }

  /**
   * The codec of `java.time.MonthDay`: an ISO-8601 string, such as `"--03-14"`.
   */
  val monthDayCodec: JsonCodec[MonthDay] = new JsonCodec[MonthDay] {
    def decodeValue(in: JsonReader): MonthDay = in.readMonthDay()

    def encodeValue(x: MonthDay, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): MonthDay = json match {
      case s: Json.String => Json.monthDayRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a MonthDay value")
    }

    override def encodeValue(x: MonthDay): Json = new Json.String(Json.monthDayRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): MonthDay = in.readKeyAsMonthDay()

    override def encodeKey(x: MonthDay, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): MonthDay = Json.monthDayRawCodec.decodeUnsafe(s)

    override def encodeKey(x: MonthDay): String = Json.monthDayRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /**
   * The codec of `java.time.OffsetDateTime`: an ISO-8601 string, such as
   * `"2021-03-14T09:30+01:00"`.
   */
  val offsetDateTimeCodec: JsonCodec[OffsetDateTime] = new JsonCodec[OffsetDateTime] {
    def decodeValue(in: JsonReader): OffsetDateTime = in.readOffsetDateTime()

    def encodeValue(x: OffsetDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): OffsetDateTime = json match {
      case s: Json.String => Json.offsetDateTimeRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a OffsetDateTime value")
    }

    override def encodeValue(x: OffsetDateTime): Json = new Json.String(Json.offsetDateTimeRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): OffsetDateTime = in.readKeyAsOffsetDateTime()

    override def encodeKey(x: OffsetDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): OffsetDateTime = Json.offsetDateTimeRawCodec.decodeUnsafe(s)

    override def encodeKey(x: OffsetDateTime): String = Json.offsetDateTimeRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("date-time"))
  }

  /**
   * The codec of `java.time.OffsetTime`: an ISO-8601 string, such as
   * `"09:30+01:00"`.
   */
  val offsetTimeCodec: JsonCodec[OffsetTime] = new JsonCodec[OffsetTime] {
    def decodeValue(in: JsonReader): OffsetTime = in.readOffsetTime()

    def encodeValue(x: OffsetTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): OffsetTime = json match {
      case s: Json.String => Json.offsetTimeRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a OffsetTime value")
    }

    override def encodeValue(x: OffsetTime): Json = new Json.String(Json.offsetTimeRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): OffsetTime = in.readKeyAsOffsetTime()

    override def encodeKey(x: OffsetTime, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): OffsetTime = Json.offsetTimeRawCodec.decodeUnsafe(s)

    override def encodeKey(x: OffsetTime): String = Json.offsetTimeRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("time"))
  }

  /**
   * The codec of `java.time.Period`: an ISO-8601 string, such as `"P1Y2M3D"`.
   */
  val periodCodec: JsonCodec[Period] = new JsonCodec[Period] {
    def decodeValue(in: JsonReader): Period = in.readPeriod()

    def encodeValue(x: Period, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Period = json match {
      case s: Json.String => Json.periodRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a Period value")
    }

    override def encodeValue(x: Period): Json = new Json.String(Json.periodRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): Period = in.readKeyAsPeriod()

    override def encodeKey(x: Period, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Period = Json.periodRawCodec.decodeUnsafe(s)

    override def encodeKey(x: Period): String = Json.periodRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("duration"))
  }

  /** The codec of `java.time.Year`: a string, such as `"2021"`. */
  val yearCodec: JsonCodec[Year] = new JsonCodec[Year] {
    def decodeValue(in: JsonReader): Year = in.readYear()

    def encodeValue(x: Year, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): Year = {
      json match {
        case s: Json.String =>
          try return Year.parse(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a Year value")
    }

    override def encodeValue(x: Year): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): Year = in.readKeyAsYear()

    override def encodeKey(x: Year, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): Year =
      try Year.parse(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Year value")
      }

    override def encodeKey(x: Year): String = x.toString

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /** The codec of `java.time.YearMonth`: a string, such as `"2021-03"`. */
  val yearMonthCodec: JsonCodec[YearMonth] = new JsonCodec[YearMonth] {
    def decodeValue(in: JsonReader): YearMonth = in.readYearMonth()

    def encodeValue(x: YearMonth, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): YearMonth = {
      json match {
        case s: Json.String =>
          try return YearMonth.parse(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a YearMonth value")
    }

    override def encodeValue(x: YearMonth): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): YearMonth = in.readKeyAsYearMonth()

    override def encodeKey(x: YearMonth, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): YearMonth =
      try YearMonth.parse(s)
      catch {
        case err if NonFatal(err) => error("expected String with a YearMonth value")
      }

    override def encodeKey(x: YearMonth): String = x.toString

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /** The codec of `java.time.ZoneId`: its ID, such as `"Europe/Paris"`. */
  val zoneIdCodec: JsonCodec[ZoneId] = new JsonCodec[ZoneId] {
    def decodeValue(in: JsonReader): ZoneId = in.readZoneId()

    def encodeValue(x: ZoneId, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): ZoneId = {
      json match {
        case s: Json.String =>
          try return ZoneId.of(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a ZoneId value")
    }

    override def encodeValue(x: ZoneId): Json = new Json.String(x.getId)

    override def decodeKey(in: JsonReader): ZoneId = in.readKeyAsZoneId()

    override def encodeKey(x: ZoneId, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): ZoneId =
      try ZoneId.of(s)
      catch {
        case err if NonFatal(err) => error("expected String with a ZoneId value")
      }

    override def encodeKey(x: ZoneId): String = x.getId

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /** The codec of `java.time.ZoneOffset`: its ID, such as `"+01:00"`. */
  val zoneOffsetCodec: JsonCodec[ZoneOffset] = new JsonCodec[ZoneOffset] {
    def decodeValue(in: JsonReader): ZoneOffset = in.readZoneOffset()

    def encodeValue(x: ZoneOffset, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): ZoneOffset = {
      json match {
        case s: Json.String =>
          try return ZoneOffset.of(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a ZoneOffset value")
    }

    override def encodeValue(x: ZoneOffset): Json = new Json.String(x.getId)

    override def decodeKey(in: JsonReader): ZoneOffset = in.readKeyAsZoneOffset()

    override def encodeKey(x: ZoneOffset, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): ZoneOffset =
      try ZoneOffset.of(s)
      catch {
        case err if NonFatal(err) => error("expected String with a ZoneOffset value")
      }

    override def encodeKey(x: ZoneOffset): String = x.getId

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /**
   * The codec of `java.time.ZonedDateTime`: an ISO-8601 string with the zone,
   * such as `"2021-03-14T09:30+01:00[Europe/Paris]"`.
   */
  val zonedDateTimeCodec: JsonCodec[ZonedDateTime] = new JsonCodec[ZonedDateTime] {
    def decodeValue(in: JsonReader): ZonedDateTime = in.readZonedDateTime()

    def encodeValue(x: ZonedDateTime, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): ZonedDateTime = json match {
      case s: Json.String => Json.zonedDateTimeRawCodec.decodeUnsafe(s.value)
      case _              => error("expected Json.String with a ZonedDateTime value")
    }

    override def encodeValue(x: ZonedDateTime): Json = new Json.String(Json.zonedDateTimeRawCodec.encodeToString(x))

    override def decodeKey(in: JsonReader): ZonedDateTime = in.readKeyAsZonedDateTime()

    override def encodeKey(x: ZonedDateTime, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): ZonedDateTime = Json.zonedDateTimeRawCodec.decodeUnsafe(s)

    override def encodeKey(x: ZonedDateTime): String = Json.zonedDateTimeRawCodec.encodeToString(x)

    override val toJsonSchema: JsonSchema = JsonSchema.string()
  }

  /** The codec of `java.util.Currency`: its ISO 4217 code, such as `"EUR"`. */
  val currencyCodec: JsonCodec[Currency] = new JsonCodec[Currency] {
    def decodeValue(in: JsonReader): Currency = {
      val code = in.readString()
      try Currency.getInstance(code)
      catch {
        case err if NonFatal(err) => error("illegal currency value")
      }
    }

    def encodeValue(x: Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeValue(json: Json): Currency = {
      json match {
        case s: Json.String =>
          try return Currency.getInstance(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a Currency value")
    }

    override def encodeValue(x: Currency): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): Currency = {
      val code = in.readKeyAsString()
      try Currency.getInstance(code)
      catch {
        case err if NonFatal(err) => error("illegal currency value")
      }
    }

    override def encodeKey(x: Currency, out: JsonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)

    override def decodeKey(s: String): Currency =
      try Currency.getInstance(s)
      catch {
        case err if NonFatal(err) => error("expected String with a Currency value")
      }

    override def encodeKey(x: Currency): String = x.toString

    override val toJsonSchema: JsonSchema = JsonSchema.string(
      minLength = NonNegativeInt(3),
      maxLength = NonNegativeInt(3)
    )
  }

  /** The codec of `java.util.UUID`: its canonical string form. */
  val uuidCodec: JsonCodec[UUID] = new JsonCodec[UUID] {
    def decodeValue(in: JsonReader): UUID = in.readUUID()

    def encodeValue(x: UUID, out: JsonWriter): Unit = out.writeVal(x)

    override def decodeValue(json: Json): UUID = {
      json match {
        case s: Json.String =>
          try return UUID.fromString(s.value)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      error("expected Json.String with a UUID value")
    }

    override def encodeValue(x: UUID): Json = new Json.String(x.toString)

    override def decodeKey(in: JsonReader): UUID = in.readKeyAsUUID()

    override def encodeKey(x: UUID, out: JsonWriter): Unit = out.writeKey(x)

    override def decodeKey(s: String): UUID =
      try UUID.fromString(s)
      catch {
        case err if NonFatal(err) => error("expected String with a UUID value")
      }

    override def encodeKey(x: UUID): String = x.toString

    override val toJsonSchema: JsonSchema = JsonSchema.string(format = new Some("uuid"))
  }

  /**
   * The codec of [[zio.blocks.schema.DynamicValue]], for any JSON. It decodes
   * an object to a `Record`, an array to a `Sequence`, and a number to an
   * `Int`, a `Long` or a `BigDecimal`, the first that holds it exactly. It
   * encodes a variant as `{"<case>": value}` and a map as an object, failing
   * for a key that is not a primitive.
   */
  val dynamicValueCodec: JsonCodec[DynamicValue] = new JsonCodec[DynamicValue] {
    private[this] val falseValue       = new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
    private[this] val trueValue        = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
    private[this] val emptyArrayValue  = new DynamicValue.Sequence(Chunk.empty)
    private[this] val emptyObjectValue = new DynamicValue.Record(Chunk.empty)

    def decodeValue(in: JsonReader): DynamicValue = {
      val b = in.nextToken()
      if (b == '"') {
        in.rollbackToken()
        new DynamicValue.Primitive(new PrimitiveValue.String(in.readString()))
      } else if (b == 'f' || b == 't') {
        in.rollbackToken()
        if (in.readBoolean()) trueValue
        else falseValue
      } else if (b >= '0' && b <= '9' || b == '-') {
        in.rollbackToken()
        val n = in.readBigDecimal()
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
            builder.addOne(decodeValue(in))
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
            builder.addOne((in.readKeyAsString(), decodeValue(in)))
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
          case _: PrimitiveValue.Unit.type =>
            out.writeObjectStart()
            out.writeObjectEnd()
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
        record.fields.foreach { kv =>
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
        sequence.elements.foreach(encodeValue(_, out))
        out.writeArrayEnd()
      case map: DynamicValue.Map =>
        out.writeObjectStart()
        map.entries.foreach { kv =>
          encodeKey(kv._1, out)
          encodeValue(kv._2, out)
        }
        out.writeObjectEnd()
      case DynamicValue.Null =>
        out.writeNull()
    }

    override def decodeValue(json: Json): DynamicValue = json match {
      case s: Json.String  => new DynamicValue.Primitive(new PrimitiveValue.String(s.value))
      case b: Json.Boolean => new DynamicValue.Primitive(new PrimitiveValue.Boolean(b.value))
      case n: Json.Number  =>
        val bd = n.value
        new DynamicValue.Primitive({
          val longValue = bd.bigDecimal.longValue
          if (bd == BigDecimal(longValue)) {
            val intValue = longValue.toInt
            if (longValue == intValue) new PrimitiveValue.Int(intValue)
            else new PrimitiveValue.Long(longValue)
          } else new PrimitiveValue.BigDecimal(bd)
        })
      case a: Json.Array =>
        new DynamicValue.Sequence(a.value.map(decodeValue))
      case o: Json.Object =>
        new DynamicValue.Record(o.value.map(kv => (kv._1, decodeValue(kv._2))))
      case _ => DynamicValue.Null
    }

    override def encodeValue(x: DynamicValue): Json = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => Json.Object.empty
          case v: PrimitiveValue.Boolean        => Json.Boolean(v.value)
          case v: PrimitiveValue.Byte           => Json.Number(v.value)
          case v: PrimitiveValue.Short          => Json.Number(v.value)
          case v: PrimitiveValue.Int            => Json.Number(v.value)
          case v: PrimitiveValue.Long           => Json.Number(v.value)
          case v: PrimitiveValue.Float          => Json.Number(v.value)
          case v: PrimitiveValue.Double         => Json.Number(v.value)
          case v: PrimitiveValue.Char           => new Json.String(String.valueOf(v.value))
          case v: PrimitiveValue.String         => new Json.String(v.value)
          case v: PrimitiveValue.BigInt         => Json.Number(v.value)
          case v: PrimitiveValue.BigDecimal     => new Json.Number(v.value)
          case v: PrimitiveValue.DayOfWeek      => new Json.String(v.value.toString)
          case v: PrimitiveValue.Duration       => new Json.String(Json.durationRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.Instant        => new Json.String(Json.instantRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.LocalDate      => new Json.String(Json.localDateRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.LocalDateTime  => new Json.String(Json.localDateTimeRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.LocalTime      => new Json.String(Json.localTimeRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.Month          => new Json.String(v.value.toString)
          case v: PrimitiveValue.MonthDay       => new Json.String(Json.monthDayRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.OffsetDateTime => new Json.String(Json.offsetDateTimeRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.OffsetTime     => new Json.String(Json.offsetTimeRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.Period         => new Json.String(Json.periodRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.Year           => new Json.String(v.value.toString)
          case v: PrimitiveValue.YearMonth      => new Json.String(v.value.toString)
          case v: PrimitiveValue.ZoneId         => new Json.String(v.value.getId)
          case v: PrimitiveValue.ZoneOffset     => new Json.String(v.value.getId)
          case v: PrimitiveValue.ZonedDateTime  => new Json.String(Json.zonedDateTimeRawCodec.encodeToString(v.value))
          case v: PrimitiveValue.Currency       => new Json.String(v.value.toString)
          case v: PrimitiveValue.UUID           => new Json.String(v.value.toString)
        }
      case record: DynamicValue.Record   => new Json.Object(record.fields.map(kv => (kv._1, encodeValue(kv._2))))
      case variant: DynamicValue.Variant =>
        new Json.Object(Chunk.single((variant.caseNameValue, encodeValue(variant.value))))
      case sequence: DynamicValue.Sequence => new Json.Array(sequence.elements.map(encodeValue))
      case map: DynamicValue.Map           => new Json.Object(map.entries.map(kv => (encodeKey(kv._1), encodeValue(kv._2))))
      case _                               => Json.Null
    }

    override def encodeKey(x: DynamicValue, out: JsonWriter): Unit = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => error("encoding as JSON key is not supported")
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
      case _ => error("encoding as JSON key is not supported")
    }

    override def encodeKey(x: DynamicValue): String = x match {
      case primitive: DynamicValue.Primitive =>
        primitive.value match {
          case _: PrimitiveValue.Unit.type      => error("encoding as JSON key is not supported")
          case v: PrimitiveValue.Boolean        => String.valueOf(v.value)
          case v: PrimitiveValue.Byte           => String.valueOf(v.value)
          case v: PrimitiveValue.Short          => String.valueOf(v.value)
          case v: PrimitiveValue.Int            => String.valueOf(v.value)
          case v: PrimitiveValue.Long           => String.valueOf(v.value)
          case v: PrimitiveValue.Float          => floatCodec.encodeToString(v.value)
          case v: PrimitiveValue.Double         => doubleCodec.encodeToString(v.value)
          case v: PrimitiveValue.Char           => String.valueOf(v.value)
          case v: PrimitiveValue.String         => v.value
          case v: PrimitiveValue.BigInt         => bigIntCodec.encodeToString(v.value)
          case v: PrimitiveValue.BigDecimal     => bigDecimalCodec.encodeToString(v.value)
          case v: PrimitiveValue.DayOfWeek      => v.value.toString
          case v: PrimitiveValue.Duration       => Json.durationRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.Instant        => Json.instantRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.LocalDate      => Json.localDateRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.LocalDateTime  => Json.localDateTimeRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.LocalTime      => Json.localTimeRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.Month          => v.value.toString
          case v: PrimitiveValue.MonthDay       => Json.monthDayRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.OffsetDateTime => Json.offsetDateTimeRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.OffsetTime     => Json.offsetTimeRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.Period         => Json.periodRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.Year           => v.value.toString
          case v: PrimitiveValue.YearMonth      => v.value.toString
          case v: PrimitiveValue.ZoneId         => v.value.getId
          case v: PrimitiveValue.ZoneOffset     => v.value.getId
          case v: PrimitiveValue.ZonedDateTime  => Json.zonedDateTimeRawCodec.encodeToString(v.value)
          case v: PrimitiveValue.Currency       => v.value.toString
          case v: PrimitiveValue.UUID           => v.value.toString
        }
      case _ => error("encoding as JSON key is not supported")
    }

    override def toJsonSchema: JsonSchema = JsonSchema.True
  }
}
