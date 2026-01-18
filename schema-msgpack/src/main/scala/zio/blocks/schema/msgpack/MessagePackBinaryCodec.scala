/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import org.msgpack.core.{MessagePack, MessagePacker, MessageUnpacker}
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class MessagePackBinaryCodec[A] extends BinaryCodec[A] {
  def encode(packer: MessagePacker, a: A): Unit
  def decodeUnsafe(unpacker: MessageUnpacker): A

  def decodeError(expectation: String): Nothing = throw new MessagePackCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new MessagePackCodecError(new ::(span, Nil), getMessage(error))
  }

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) bs = input.array()
    else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    decode(MessagePack.newDefaultUnpacker(bs, pos, len))
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encode(value)
    output.put(bytes)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(MessagePack.newDefaultUnpacker(input))

  def encode(value: A): Array[Byte] = {
    val output = new MessagePackByteArrayOutputStream
    val packer = MessagePack.newDefaultPacker(output)
    encode(packer, value)
    packer.close()
    output.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    decode(MessagePack.newDefaultUnpacker(input))

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val packer = MessagePack.newDefaultPacker(output)
    encode(packer, value)
    packer.close()
  }

  private[this] def decode(unpacker: MessageUnpacker): Either[SchemaError, A] =
    try new Right(decodeUnsafe(unpacker))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
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
          new ExpectationMismatch(new DynamicOptic(ArraySeq.unsafeWrapArray(array)), e.getMessage)
        case _ => new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case e                       => e.getMessage
  }
}

object MessagePackBinaryCodec {
  val maxCollectionSize: Int = Integer.MAX_VALUE - 8
}

private class MessagePackCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}

private class MessagePackByteArrayOutputStream extends java.io.OutputStream {
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
