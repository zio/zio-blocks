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

package zio.blocks.schema.bson

import org.bson._
import org.bson.io.BasicOutputBuffer

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
  private def writeValue[T](value: T, encoder: BsonEncoder[T], writer: BsonWriter, isDocument: Boolean): Unit =
    if (isDocument) encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
    else {
      writer.writeStartDocument()
      writer.writeName("v")
      encoder.encode(writer, value, BsonEncoder.EncoderContext.default)
      writer.writeEndDocument()
    }

  /**
   * Read a value using BsonReader.
   * @param isDocument - true if value is written as document, false if wrapped in "v" field
   */
  private def readValue[T](
    decoder: BsonDecoder[T],
    reader: BsonReader,
    isDocument: Boolean
  ): Either[BsonDecoder.Error, T] =
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
  def roundTripWriterReader[T](value: T, codec: BsonCodec[T], isDocument: Boolean): Boolean = {
    val buffer = new BasicOutputBuffer()
    try {
      val writer = new BsonBinaryWriter(buffer)
      try {
        writeValue(value, codec.encoder, writer, isDocument)
        val reader = new BsonBinaryReader(buffer.getByteBuffers.get(0).asNIO())
        try readValue(codec.decoder, reader, isDocument) == Right(value)
        finally reader.close()
      } finally writer.close()
    } finally buffer.close()
  }

  /**
   * Round-trip test using toBsonValue/as path (convenience methods). This is
   * the simple path we already use in current tests.
   */
  def roundTripToBsonValueAs[T](value: T, codec: BsonCodec[T]): Boolean = {
    val bson = codec.encoder.toBsonValue(value)
    bson.as[T](codec.decoder) match {
      case Right(decoded) => decoded == value
      case Left(_)        => false
    }
  }
}
