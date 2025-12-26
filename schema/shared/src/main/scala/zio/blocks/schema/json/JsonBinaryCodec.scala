package zio.blocks.schema.json

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.nio.ByteBuffer
import scala.annotation.switch
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
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
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
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, config)

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
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, 0, input.length, config))
    catch {
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
  def encode(value: A, config: WriterConfig): Array[Byte] =
    JsonBinaryCodec.writerPool.get.write(this, value, config)

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
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
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
  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, config)

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
