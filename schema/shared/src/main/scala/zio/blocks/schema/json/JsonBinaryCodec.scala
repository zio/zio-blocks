package zio.blocks.schema.json

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class JsonBinaryCodec[A](val valueType: Int = JsonBinaryCodec.objectType)
    extends BinaryCodec[A]
    with JsonValueCodec[A]
    with JsonKeyCodec[A] {
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

  def decodeError(expectation: String): Nothing = throw new JsonBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: JsonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new JsonBinaryCodecError(new ::(span, Nil), error.getMessage)
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: JsonBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new JsonBinaryCodecError(new ::(span1, new ::(span2, Nil)), error.getMessage)
  }

  def decodeValue(in: JsonReader, default: A): A

  def encodeValue(x: A, out: JsonWriter): Unit

  def nullValue: A = null.asInstanceOf[A]

  def decodeKey(in: JsonReader): A = in.decodeError("decoding as a JSON key is not supported")

  def encodeKey(x: A, out: JsonWriter): Unit = out.encodeError("encoding as a JSON key is not supported")

  override def decode(input: ByteBuffer): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, ReaderConfig))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  override def encode(value: A, output: ByteBuffer): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, WriterConfig)

  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, config)

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, 0, input.length, ReaderConfig))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A): Array[Byte] =
    JsonBinaryCodec.writerPool.get.write(this, value, WriterConfig)

  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, 0, input.length, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, config: WriterConfig): Array[Byte] =
    JsonBinaryCodec.writerPool.get.write(this, value, config)

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, ReaderConfig))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream): Unit =
    JsonBinaryCodec.writerPool.get.write(this, value, output, WriterConfig)

  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try new Right(JsonBinaryCodec.readerPool.get.read(this, input, config))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

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
