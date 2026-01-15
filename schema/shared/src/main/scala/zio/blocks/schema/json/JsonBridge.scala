package zio.blocks.schema.json

import scala.collection.immutable.VectorBuilder

private[json] object JsonBridge {

  /**
   * Reads a Json value from a JsonReader.
   *
   * PLEASE NOTE!!!
   *   - Do NOT call rollbackToken() after nextToken() for primitives (it breaks
   *     JsonReader state).
   *   - For arrays/objects, the only safe rollback is the one used after
   *     isNextToken(']') / isNextToken('}'), to "unread" the first element
   *     token (matches dynamicValueCodec).
   */
  def readJson(in: JsonReader): Json = {
    def decodeValue(): Json = {
      val b = in.nextToken()

      if (b == '"') {
        // String: opening quote already consumed by nextToken, readString expects this
        in.rollbackToken()
        Json.String(in.readString(null))
      } else if (b == 'f' || b == 't') {
        // Boolean: rollback so readBoolean can read 'true' or 'false' from start
        in.rollbackToken()
        Json.Boolean(in.readBoolean())

      } else if ((b >= '0' && b <= '9') || b == '-') {
        // Number: rollback so readBigDecimal can read the full number including first digit/minus
        in.rollbackToken()
        val n = in.readBigDecimal(null)
        Json.Number(n.toString)

      } else if (b == '[') {
        if (in.isNextToken(']')) {
          Json.Array.empty
        } else {
          // SAFE rollback: only after isNextToken
          in.rollbackToken()

          val builder = new VectorBuilder[Json]
          while ({
            builder.addOne(decodeValue())
            in.isNextToken(',')
          }) ()

          if (in.isCurrentToken(']')) Json.Array(builder.result())
          else in.arrayEndOrCommaError()
        }

      } else if (b == '{') {
        if (in.isNextToken('}')) {
          Json.Object.empty
        } else {
          // SAFE rollback: only after isNextToken
          in.rollbackToken()

          val builder = new VectorBuilder[(java.lang.String, Json)]
          while ({
            val k = in.readKeyAsString()
            val v = decodeValue()
            builder.addOne((k, v))
            in.isNextToken(',')
          }) ()

          if (in.isCurrentToken('}')) Json.Object(builder.result())
          else in.objectEndOrCommaError()
        }

      } else if (b == 'n') {
        // Null: rollback so readNullOrError can read 'null' from start
        in.rollbackToken()
        in.readNullOrError(Json.Null, "expected JSON value")

      } else {
        in.decodeError("expected JSON value")
      }
    }

    val value = decodeValue()

    // Optional trailing whitespace + EOF handling
    var trailing: Byte = 0
    try {
      trailing = in.nextToken()
      while (trailing == ' ' || trailing == '\n' || trailing == '\r' || trailing == '\t') {
        trailing = in.nextToken()
      }
    } catch {
      case _: Throwable => trailing = (-1).toByte
    }

    if (trailing != 0 && trailing != (-1).toByte) {
      in.decodeError("unexpected trailing input after JSON value")
    }

    value
  }

  /**
   * Writes a Json value to a JsonWriter.
   */
  def writeJson(json: Json, out: JsonWriter): Unit = json match {
    case Json.Null           => out.writeNull()
    case Json.Boolean(value) => out.writeVal(value)
    case Json.Number(value)  =>
      try {
        val decimal   = BigDecimal(value)
        val longValue = decimal.toLongExact
        val intValue  = longValue.toInt
        if (longValue == intValue) out.writeVal(intValue)
        else out.writeVal(longValue)
      } catch {
        case _: ArithmeticException => out.writeVal(BigDecimal(value))
      }
    case Json.String(value)   => out.writeVal(value)
    case Json.Array(elements) =>
      out.writeArrayStart()
      elements.foreach(elem => writeJson(elem, out))
      out.writeArrayEnd()
    case Json.Object(fields) =>
      out.writeObjectStart()
      fields.foreach { case (key, value) =>
        out.writeKey(key: java.lang.String)
        writeJson(value, out)
      }
      out.writeObjectEnd()
  }

  def decodeJsonWith[A](json: Json, codec: JsonBinaryCodec[A]): Either[JsonError, A] =
    try {
      val jsonString = json.encode
      codec.decode(jsonString, ReaderConfig) match {
        case Right(v) => Right(v)
        case Left(e)  => Left(JsonError.fromSchemaError(e))
      }
    } catch {
      case e: JsonBinaryCodecError => Left(JsonError(e.getMessage))
      case e: Exception            => Left(JsonError(s"Failed to decode: ${e.getMessage}"))
    }

  def encodeJsonWith[A](value: A, codec: JsonBinaryCodec[A]): Json =
    try {
      val jsonString = codec.encodeToString(value, WriterConfig)
      Json.parse(jsonString) match {
        case Right(json) => json
        case Left(_)     => Json.Null
      }
    } catch {
      case _: Exception => Json.Null
    }

  private[json] def jsonToString(json: Json, config: WriterConfig): java.lang.String = {
    val bytes = jsonToBytes(json, config)
    new java.lang.String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  }

  private[json] def jsonToBytes(json: Json, config: WriterConfig): scala.Array[Byte] = {
    // Pin the type parameter to avoid Scala 3 inferring JsonBinaryCodec[Nothing]
    val codec: JsonBinaryCodec[Json] =
      new JsonBinaryCodec[Json](JsonBinaryCodec.objectType) {
        override def encodeValue(j: Json, out: JsonWriter): Unit      = writeJson(j, out)
        override def decodeValue(in: JsonReader, default: Json): Json = readJson(in)
      }

    codec.encode(json, config)
  }
}
