package zio.blocks.schema.json

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class JsonBinaryCodec[A](val valueType: Int = JsonBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case JsonBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case JsonBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case JsonBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case JsonBinaryCodec.charType    => RegisterOffset(chars = 1)
    case JsonBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case JsonBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case JsonBinaryCodec.intType     => RegisterOffset(ints = 1)
    case JsonBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case JsonBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
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
   * `JsonWriterException` if it cannot be encoded properly according to
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
   * may fail with `JsonWriterException` if it cannot be encoded properly
   * according to RFC-8259 requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` which provides access to JSON output to
   *   serialize the specified value as a JSON key
   */
  def encodeKey(x: A, out: JsonWriter): Unit = out.encodeError("encoding as JSON key is not supported")

  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, config)

  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, 0, input.length, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, config: WriterConfig): Array[Byte] =
    JsonBinaryCodec.writerPool.get.write(this, value, config)

  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, config)

  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encodeString(value: A): String = new String(encode(value), StandardCharsets.UTF_8)

  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] = {
    val bytes = input.getBytes(StandardCharsets.UTF_8)
    decode(bytes, config)
  }

  def encodeString(value: A, config: WriterConfig): String = new String(encode(value, config), StandardCharsets.UTF_8)

  def decode(input: CharBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  def encodeToCharBuffer(value: A, output: CharBuffer): Unit = {
    val str = encodeString(value)
    output.append(str)
  }

  def decode(input: CharBuffer, config: ReaderConfig): Either[SchemaError, A] = {
    val str = input.toString()
    decode(str, config)
  }

  def encodeToCharBuffer(value: A, output: CharBuffer, config: WriterConfig): Unit = {
    val str = encodeString(value, config)
    output.append(str)
  }

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
        error.getMessage
      ),
      Nil
    )
  )
}

object JsonBinaryCodec {
  val objectType  = 0
  val booleanType = 1
  val byteType    = 2
  val charType    = 3
  val shortType   = 4
  val floatType   = 5
  val intType     = 6
  val doubleType  = 7
  val longType    = 8
  val unitType    = 9

  private final val readerPool: ThreadLocal[JsonReader] = new ThreadLocal[JsonReader] {
    override def initialValue(): JsonReader = new JsonReader
  }
  private final val writerPool: ThreadLocal[JsonWriter] = new ThreadLocal[JsonWriter] {
    override def initialValue(): JsonWriter = new JsonWriter
  }
}

private class JsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
