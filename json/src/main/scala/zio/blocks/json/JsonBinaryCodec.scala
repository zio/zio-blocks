package zio.blocks.json

import com.github.plokhotnyuk.jsoniter_scala.core._
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
      throw new JsonBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: JsonBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new JsonBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeValue(in: JsonReader, default: A): A

  def encodeValue(x: A, out: JsonWriter): Unit

  def nullValue: A = null.asInstanceOf[A]

  def decodeKey(in: JsonReader): A = in.decodeError("Decoding as a JSON key is not supported")

  def encodeKey(x: A, out: JsonWriter): Unit = out.encodeError("Encoding as a JSON key is not supported")

  override def decode(input: ByteBuffer): Either[SchemaError, A] =
    try new Right(readFromByteBuffer(input, readerConfig)(this))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  override def encode(value: A, output: ByteBuffer): Unit = writeToByteBuffer(value, output)(this)

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(readFromArray(input, readerConfig)(this))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A): Array[Byte] = writeToArray(value)(this)

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    try new Right(readFromStream(input, readerConfig)(this))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream): Unit = writeToStream(value, output)(this)

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
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
          new ExpectationMismatch(new DynamicOptic(ArraySeq.unsafeWrapArray(array)), e.getMessage)
        case _ => new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case re: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException =>
      val msg = re.getMessage
      msg.substring(0, msg.length - 20)
    case e => e.getMessage
  }

  private[this] val readerConfig: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)
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
}

private class JsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
