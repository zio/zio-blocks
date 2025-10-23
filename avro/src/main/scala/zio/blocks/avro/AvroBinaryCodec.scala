package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DecoderFactory, EncoderFactory}
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.nio.ByteBuffer
import scala.util.control.NonFatal

abstract class AvroBinaryCodec[A](val valueType: Int = AvroBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case AvroBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case AvroBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case AvroBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case AvroBinaryCodec.charType    => RegisterOffset(chars = 1)
    case AvroBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case AvroBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case AvroBinaryCodec.intType     => RegisterOffset(ints = 1)
    case AvroBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case AvroBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def decode(d: BinaryDecoder): A

  def encode(x: A, e: BinaryEncoder): Unit

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
    val avroDecoder = DecoderFactory.get().binaryDecoder(bs, pos, len, null)
    try new Right(decode(avroDecoder))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit =
    encode(
      value,
      EncoderFactory
        .get()
        .directBinaryEncoder(
          new OutputStream {
            override def write(b: Int): Unit = output.put(b.toByte)

            override def write(bs: Array[Byte]): Unit = output.put(bs)

            override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
          },
          null
        )
    )

  def decode(input: Array[Byte]): Either[SchemaError, A] = {
    val avroDecoder = DecoderFactory.get().binaryDecoder(input, 0, input.length, null)
    try new Right(decode(avroDecoder))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }
  }

  def encode(value: A): Array[Byte] = {
    val output = new java.io.ByteArrayOutputStream(64)
    encode(value, EncoderFactory.get().directBinaryEncoder(output, null))
    output.close()
    output.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = {
    val avroDecoder = DecoderFactory.get().directBinaryDecoder(input, null)
    try new Right(decode(avroDecoder))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }
  }

  def encode(value: A, output: java.io.OutputStream): Unit =
    encode(value, EncoderFactory.get().directBinaryEncoder(output, null))

  private[this] def toError(error: Throwable) =
    new SchemaError(new ::(new SchemaError.InvalidType(DynamicOptic.root, error.getMessage), Nil))
}

object AvroBinaryCodec {
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
