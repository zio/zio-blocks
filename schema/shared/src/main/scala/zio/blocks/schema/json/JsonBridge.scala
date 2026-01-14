package zio.blocks.schema.json

import scala.collection.immutable.VectorBuilder

/**
 * Internal utilities for bridging between the Json ADT and JsonReader/JsonWriter.
 * 
 * These methods enable efficient conversion without round-tripping through byte arrays.
 */
private[json] object JsonBridge {

  /**
   * Reads a Json value from a JsonReader.
   * 
   * This is used internally to avoid encoding Json -> bytes -> parsing back.
   */
  def readJson(in: JsonReader): Json = {
    val b = in.nextToken()
    
    if (b == '"') {
      // String
      in.rollbackToken()
      Json.String(in.readString(null))
    } else if (b == 'f' || b == 't') {
      // Boolean
      in.rollbackToken()
      Json.Boolean(in.readBoolean())
    } else if (b >= '0' && b <= '9' || b == '-') {
      // Number - store as string to preserve precision
      in.rollbackToken()
      val decimal = in.readBigDecimal(null)
      Json.Number(decimal.toString)
    } else if (b == '[') {
      // Array
      if (in.isNextToken(']')) {
        Json.Array.empty
      } else {
        in.rollbackToken()
        val builder = new VectorBuilder[Json]
        while ({
          builder.addOne(readJson(in))
          in.isNextToken(',')
        }) ()
        if (in.isCurrentToken(']')) {
          Json.Array(builder.result())
        } else {
          in.arrayEndOrCommaError()
        }
      }
    } else if (b == '{') {
      // Object
      if (in.isNextToken('}')) {
        Json.Object.empty
      } else {
        in.rollbackToken()
        val builder = new VectorBuilder[(String, Json)]
        while ({
          val key = in.readKeyAsString()
          val value = readJson(in)
          builder.addOne((key, value))
          in.isNextToken(',')
        }) ()
        if (in.isCurrentToken('}')) {
          Json.Object(builder.result())
        } else {
          in.objectEndOrCommaError()
        }
      }
    } else {
      // Null
      in.rollbackToken()
      in.readNullOrError(Json.Null, "expected JSON value")
    }
  }

  /**
   * Writes a Json value to a JsonWriter.
   * 
   * This is used internally to avoid encoding Json -> bytes in memory.
   */
  def writeJson(json: Json, out: JsonWriter): Unit = json match {
    case Json.Null =>
      out.writeNull()
      
    case Json.Boolean(value) =>
      out.writeVal(value)
      
    case Json.Number(value) =>
      // Write the number string directly
      // The JsonWriter will handle it as a raw numeric value
      try {
        // Try as various numeric types for optimal encoding
        val decimal = BigDecimal(value)
        val longValue = decimal.toLongExact
        val intValue = longValue.toInt
        if (longValue == intValue) {
          out.writeVal(intValue)
        } else {
          out.writeVal(longValue)
        }
      } catch {
        case _: ArithmeticException =>
          // Not an exact long, use BigDecimal
          out.writeVal(BigDecimal(value))
      }
      
    case Json.String(value) =>
      out.writeVal(value)
      
    case Json.Array(elements) =>
      out.writeArrayStart()
      elements.foreach(elem => writeJson(elem, out))
      out.writeArrayEnd()
      
    case Json.Object(fields) =>
      out.writeObjectStart()
      fields.foreach { case (key, value) =>
        out.writeKey(key)
        writeJson(value, out)
      }
      out.writeObjectEnd()
  }

  /**
   * Decodes a value of type A from a Json value using a JsonBinaryCodec.
   * 
   * This uses writeJson to convert the Json ADT to bytes via JsonWriter,
   * then uses the codec's decode method. While this involves serialization,
   * it avoids creating a custom JsonReader implementation and reuses the
   * optimized codec path.
   */
  def decodeJsonWith[A](json: Json, codec: JsonBinaryCodec[A]): Either[JsonError, A] = {
    try {
      // Convert Json to bytes using JsonWriter, then use the codec's existing decode path
      val bytes = jsonToBytes(json)
      codec.decode(bytes, ReaderConfig) match {
        case Right(value) => Right(value)
        case Left(err) => Left(JsonError.fromSchemaError(err))
      }
    } catch {
      case e: JsonBinaryCodecError =>
        Left(JsonError(e.getMessage))
      case e: Exception =>
        Left(JsonError(s"Failed to decode: ${e.getMessage}"))
    }
  }

  /**
   * Encodes a value of type A to Json using a JsonBinaryCodec.
   * 
   * This uses the codec to encode to bytes via JsonWriter, then uses readJson
   * to parse back to the Json ADT via JsonReader. While this involves serialization,
   * it avoids creating a custom JsonWriter implementation and reuses the optimized
   * codec path.
   */
  def encodeJsonWith[A](value: A, codec: JsonBinaryCodec[A]): Json = {
    try {
      // Use the codec to encode to bytes, then parse back using JsonReader
      val bytes = codec.encode(value, WriterConfig)
      bytesToJson(bytes)
    } catch {
      case e: Exception =>
        // Fallback to null on error
        Json.Null
    }
  }

  /**
   * Helper: Convert Json to UTF-8 bytes.
   */
  private def jsonToBytes(json: Json): Array[Byte] = {
    val writer = new JsonWriter(
      buf = Array.emptyByteArray,
      limit = 0,
      config = WriterConfig,
      stack = zio.blocks.schema.binding.Registers(0)
    )
    writeJson(json, writer)
    writer.toByteArray
  }

  /**
   * Helper: Parse UTF-8 bytes to Json.
   */
  private def bytesToJson(bytes: Array[Byte]): Json = {
    val reader = new JsonReader(
      buf = bytes,
      charBuf = new Array[Char](ReaderConfig.preferredCharBufSize),
      config = ReaderConfig,
      stack = zio.blocks.schema.binding.Registers(0)
    )
    readJson(reader)
  }
}