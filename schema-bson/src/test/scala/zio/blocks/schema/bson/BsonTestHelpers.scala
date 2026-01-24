package zio.blocks.schema.bson

import org.bson._
import org.bson.io.BasicOutputBuffer

import zio.{UIO, ZIO}

/**
 * Provides comprehensive testing of BSON codecs through multiple paths:
 *   - toBsonValue/as (convenience methods)
 *   - BsonWriter/BsonReader (low-level encoding/decoding)
 */
object BsonTestHelpers {

  /**
   * Write a value using BsonWriter.
   * @param isDocument - true if value should be written as document, false to wrap in "v" field
   */
  def writeValue[T](value: T, encoder: BsonEncoder[T], writer: BsonWriter, isDocument: Boolean): UIO[Unit] =
    ZIO.succeed {
      if (isDocument) encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
      else {
        writer.writeStartDocument()
        writer.writeName("v")
        encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
        writer.writeEndDocument()
      }
    }

  /**
   * Read a value using BsonReader.
   * @param isDocument - true if value is written as document, false if wrapped in "v" field
   */
  def readValue[T](decoder: BsonDecoder[T], reader: BsonReader, isDocument: Boolean): Either[BsonDecoder.Error, T] =
    if (isDocument) decoder.decode(reader)
    else {
      reader.readStartDocument()
      reader.readBsonType()
      reader.skipName()
      val res = decoder.decode(reader)
      reader.readEndDocument()
      res
    }

  /**
   * Round-trip test using BsonWriter/BsonReader path. Tests the low-level codec
   * infrastructure.
   */
  def roundTripWriterReader[T](
    value: T,
    isDocument: Boolean
  )(implicit encoder: BsonEncoder[T], decoder: BsonDecoder[T]): ZIO[zio.Scope, Throwable, Boolean] =
    for {
      buffer <- ZIO.fromAutoCloseable(ZIO.succeed(new BasicOutputBuffer()))
      writer <- ZIO.fromAutoCloseable(ZIO.succeed(new BsonBinaryWriter(buffer)))
      _      <- writeValue(value, encoder, writer, isDocument)
      reader <- ZIO.fromAutoCloseable(ZIO.succeed(new BsonBinaryReader(buffer.getByteBuffers.get(0).asNIO())))
      res    <- ZIO.succeed(readValue(decoder, reader, isDocument))
    } yield res == Right(value)

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
