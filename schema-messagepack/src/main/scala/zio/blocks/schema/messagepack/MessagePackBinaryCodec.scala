package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePack, MessagePacker, MessageUnpacker}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract base class for MessagePack binary codecs.
 *
 * This class provides the foundation for encoding and decoding values in
 * MessagePack binary format. It handles value type specialization for primitive
 * types to optimize performance by avoiding boxing/unboxing overhead.
 *
 * @param valueType
 *   The type identifier for primitive value handling. Use constants from
 *   MessagePackBinaryCodec companion object.
 */
abstract class MessagePackBinaryCodec[A](val valueType: Int = MessagePackBinaryCodec.objectType)
    extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case MessagePackBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case MessagePackBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case MessagePackBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case MessagePackBinaryCodec.charType    => RegisterOffset(chars = 1)
    case MessagePackBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case MessagePackBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case MessagePackBinaryCodec.intType     => RegisterOffset(ints = 1)
    case MessagePackBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case MessagePackBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                                  => RegisterOffset.Zero
  }

  /**
   * Decode a value from the given MessageUnpacker.
   *
   * @param unpacker
   *   The MessageUnpacker to read from
   * @return
   *   The decoded value
   * @throws MessagePackBinaryCodecError
   *   if decoding fails
   */
  def decodeUnsafe(unpacker: MessageUnpacker): A

  /**
   * Encode a value to the given MessagePacker.
   *
   * @param value
   *   The value to encode
   * @param packer
   *   The MessagePacker to write to
   */
  def encodeUnsafe(value: A, packer: MessagePacker): Unit

  /**
   * Throw a decode error with the given expectation message.
   */
  def decodeError(expectation: String): Nothing =
    throw new MessagePackBinaryCodecError(Nil, expectation)

  /**
   * Throw a decode error with a path span and cause.
   */
  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new MessagePackBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Throw a decode error with two path spans and a cause.
   */
  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new MessagePackBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val len = input.remaining()
    if (input.hasArray) {
      val bs  = input.array()
      val pos = input.arrayOffset() + input.position()
      // Keep behavior consistent with the non-array path: consume the buffer.
      input.position(input.limit())
      decode(MessagePack.newDefaultUnpacker(bs, pos, len))
    } else {
      val bs = new Array[Byte](len)
      input.get(bs)
      decode(MessagePack.newDefaultUnpacker(bs, 0, len))
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = encode(
    value,
    new OutputStream {
      override def write(b: Int): Unit = output.put(b.toByte)

      override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
    }
  )

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(MessagePack.newDefaultUnpacker(input, 0, input.length))

  def encode(value: A): Array[Byte] = {
    val packer = MessagePack.newDefaultBufferPacker()
    try {
      encodeUnsafe(value, packer)
      packer.toByteArray
    } finally {
      packer.close()
    }
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    decode(MessagePack.newDefaultUnpacker(input))

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val packer = MessagePack.newDefaultPacker(output)
    try {
      encodeUnsafe(value, packer)
      packer.flush()
    } finally {
      packer.close()
    }
  }

  private[this] def decode(unpacker: MessageUnpacker): Either[SchemaError, A] =
    try new Right(decodeUnsafe(unpacker))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      unpacker.close()
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: MessagePackBinaryCodecError =>
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
    case _: java.io.EOFException                                => "Unexpected end of input"
    case _: org.msgpack.core.MessageInsufficientBufferException => "Unexpected end of input"
    case e                                                      =>
      val msg = e.getMessage
      if ((msg ne null) && msg.nonEmpty) msg else e.getClass.getName
  }
}

object MessagePackBinaryCodec {

  /** Value type for objects (reference types) */
  val objectType = 0

  /** Value type for booleans */
  val booleanType = 1

  /** Value type for bytes */
  val byteType = 2

  /** Value type for chars */
  val charType = 3

  /** Value type for shorts */
  val shortType = 4

  /** Value type for floats */
  val floatType = 5

  /** Value type for ints */
  val intType = 6

  /** Value type for doubles */
  val doubleType = 7

  /** Value type for longs */
  val longType = 8

  /** Value type for unit */
  val unitType = 9

  /**
   * Maximum collection size supported to prevent memory issues. This is
   * intentionally set well below Integer.MAX_VALUE to avoid attempting to
   * allocate extremely large collections that could cause OutOfMemoryError or
   * denial-of-service conditions.
   */
  val maxCollectionSize: Int = 10_000_000

  /**
   * Maximum binary data size supported to prevent memory issues. This is
   * intentionally limited to prevent denial-of-service attacks through
   * malicious input specifying huge binary sizes.
   */
  val maxBinarySize: Int = 100_000_000
}

/**
 * Internal error class for MessagePack decoding errors. Stores path spans for
 * error location reporting.
 */
private[messagepack] class MessagePackBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}

/**
 * Optimized ByteArrayOutputStream for MessagePack encoding.
 *
 * Uses a doubling buffer growth strategy to minimize allocations and copies,
 * especially for large or unpredictable output sizes.
 */
private[messagepack] class MessagePackByteArrayOutputStream extends java.io.OutputStream {
  private[this] var buf   = new Array[Byte](64)
  private[this] var count = 0

  override def write(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(bs: Array[Byte], off: Int, len: Int): Unit = {
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, off, buf, count, len)
    count = newLen
  }

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)
}
