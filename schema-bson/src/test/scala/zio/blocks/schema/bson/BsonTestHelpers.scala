package zio.blocks.schema.bson

import org.bson._
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.{Codec => BCodec, DecoderContext, EncoderContext}
import org.bson.io.BasicOutputBuffer
import zio.bson._
import zio.{Task, UIO, ZIO}

import scala.reflect.{ClassTag, classTag}

/**
 * Test helpers matching the old zio-schema-bson test infrastructure. Provides
 * comprehensive testing of BSON codecs through multiple paths:
 *   - toBsonValue/as (convenience methods)
 *   - BsonWriter/BsonReader (low-level codec provider)
 */
object BsonTestHelpers {

  val emptyCodecRegistry: CodecRegistry = new CodecRegistry {
    def get[T](clazz: Class[T]): BCodec[T]                          = null
    def get[T](clazz: Class[T], registry: CodecRegistry): BCodec[T] = null
  }

  /**
   * Write a value using BsonWriter via codec provider.
   * @param isDocument - true if value should be written as document, false to wrap in "v" field
   */
  def writeValue[T](value: T, codec: BCodec[T], writer: BsonWriter, isDocument: Boolean): UIO[Unit] =
    ZIO.succeed {
      if (isDocument) codec.encode(writer, value, EncoderContext.builder().build())
      else {
        writer.writeStartDocument()
        writer.writeName("v")
        codec.encode(writer, value, EncoderContext.builder().build())
        writer.writeEndDocument()
      }
    }

  /**
   * Try to read a value using BsonReader via codec provider.
   * @param isDocument - true if value is written as document, false if wrapped in "v" field
   */
  def tryReadValue[T](codec: BCodec[T], reader: BsonReader, isDocument: Boolean): Task[T] =
    ZIO.attempt {
      if (isDocument) codec.decode(reader, DecoderContext.builder().build())
      else {
        reader.readStartDocument()
        reader.readBsonType()
        reader.skipName()
        val res = codec.decode(reader, DecoderContext.builder().build())
        reader.readEndDocument()
        res
      }
    }

  /**
   * Read a value using BsonReader via codec provider (orDie on failure).
   */
  def readValue[T](codec: BCodec[T], reader: BsonReader, isDocument: Boolean): UIO[T] =
    tryReadValue[T](codec, reader, isDocument).orDie

  /**
   * Get the codec provider for type T. This creates a BCodec that uses our
   * BsonEncoder/BsonDecoder.
   */
  def getCodecProvider[T: ClassTag](implicit encoder: BsonEncoder[T], decoder: BsonDecoder[T]): BCodec[T] =
    zioBsonCodecProvider[T].get[T](classTag[T].runtimeClass.asInstanceOf[Class[T]], emptyCodecRegistry)

  /**
   * Round-trip test using BsonWriter/BsonReader path. Tests the low-level codec
   * provider infrastructure.
   */
  def roundTripWriterReader[T: ClassTag](
    value: T,
    isDocument: Boolean
  )(implicit encoder: BsonEncoder[T], decoder: BsonDecoder[T]): ZIO[zio.Scope, Throwable, Boolean] =
    for {
      buffer <- ZIO.fromAutoCloseable(ZIO.succeed(new BasicOutputBuffer()))
      writer <- ZIO.fromAutoCloseable(ZIO.succeed(new BsonBinaryWriter(buffer)))
      codec  <- ZIO.succeed(getCodecProvider[T])
      _      <- writeValue(value, codec, writer, isDocument)
      reader <- ZIO.fromAutoCloseable(ZIO.succeed(new BsonBinaryReader(buffer.getByteBuffers.get(0).asNIO())))
      res    <- readValue(codec, reader, isDocument)
    } yield res == value

  /**
   * Round-trip test using toBsonValue/as path (convenience methods). This is
   * the simple path we already use in current tests.
   */
  def roundTripToBsonValueAs[T](
    value: T
  )(implicit encoder: BsonEncoder[T], decoder: BsonDecoder[T]): Boolean = {
    val bson = encoder.toBsonValue(value)
    bson.as[T](decoder) match {
      case Right(decoded) => decoded == value
      case Left(_)        => false
    }
  }
}
