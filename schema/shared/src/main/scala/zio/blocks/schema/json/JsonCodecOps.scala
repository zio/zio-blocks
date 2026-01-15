package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}

/**
 * Extension methods for deriving JsonEncoder and JsonDecoder from Schema[A].
 */
object JsonCodecOps {

  /**
   * Derives a JsonEncoder[A] from Schema[A] using DynamicValue as an
   * intermediate representation.
   *
   * Example:
   * {{{
   *   val personSchema: Schema[Person] = Schema.derived
   *   val personEncoder: JsonEncoder[Person] = personSchema.deriveJsonEncoder
   * }}}
   */
  def deriveJsonEncoder[A](schema: Schema[A]): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(a: A): Json = {
        val dynamicValue = schema.toDynamicValue(a)
        dynamicValueToJson(dynamicValue)
      }
    }

  /**
   * Derives a JsonDecoder[A] from Schema[A] using DynamicValue as an
   * intermediate representation.
   *
   * Example:
   * {{{
   *   val personSchema: Schema[Person] = Schema.derived
   *   val personDecoder: JsonDecoder[Person] = personSchema.deriveJsonDecoder
   * }}}
   */
  def deriveJsonDecoder[A](schema: Schema[A]): JsonDecoder[A] =
    new JsonDecoder[A] {
      def decode(json: Json): Either[JsonDecoderError, A] =
        jsonToDynamicValue(json) match {
          case Right(dynamicValue) =>
            schema.fromDynamicValue(dynamicValue).left.map(err => JsonDecoderError(err.message))
          case Left(error) =>
            Left(error)
        }
    }

  /**
   * Converts a DynamicValue to a Json representation.
   */
  private def dynamicValueToJson(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(pv)  => primitiveValueToJson(pv)
    case DynamicValue.Record(fields) =>
      val jsonFields: scala.collection.immutable.Map[Predef.String, Json] =
        fields.map { case (name: Predef.String, value: DynamicValue) =>
          (name, dynamicValueToJson(value))
        }.toMap
      Json.Object(jsonFields)
    case DynamicValue.Variant(caseName, value) =>
      // Encode variant as a JSON object with a single field: the case name
      Json.Object(scala.collection.immutable.Map[Predef.String, Json](caseName -> dynamicValueToJson(value)))
    case DynamicValue.Sequence(elements) =>
      Json.Array(elements.map(dynamicValueToJson))
    case DynamicValue.Map(entries) =>
      // Convert map to JSON object - keys must be strings
      val jsonFields: scala.collection.immutable.Map[Predef.String, Json] =
        entries.map { case (key, value) =>
          val keyStr: Predef.String = dynamicValueToString(key)
          (keyStr, dynamicValueToJson(value))
        }.toMap
      Json.Object(jsonFields)
  }

  /**
   * Converts a PrimitiveValue to a Json representation.
   */
  private def primitiveValueToJson(pv: PrimitiveValue): Json = pv match {
    case PrimitiveValue.Unit          => Json.Null
    case PrimitiveValue.Boolean(b)    => Json.Boolean(b)
    case PrimitiveValue.Byte(b)       => Json.Number(BigDecimal(b))
    case PrimitiveValue.Short(s)      => Json.Number(BigDecimal(s))
    case PrimitiveValue.Int(i)        => Json.Number(BigDecimal(i))
    case PrimitiveValue.Long(l)       => Json.Number(BigDecimal(l))
    case PrimitiveValue.Float(f)      => Json.Number(BigDecimal(f.toDouble))
    case PrimitiveValue.Double(d)     => Json.Number(BigDecimal(d))
    case PrimitiveValue.Char(c)       => Json.String(c.toString)
    case PrimitiveValue.String(s)     => Json.String(s)
    case PrimitiveValue.BigInt(b)     => Json.Number(BigDecimal(b))
    case PrimitiveValue.BigDecimal(b) => Json.Number(b)
    case other                        => Json.String(other.toString)
  }

  /**
   * Converts a DynamicValue to a String for use as a JSON object key.
   */
  private def dynamicValueToString(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(pv) => primitiveValueToString(pv)
    case other                      => other.toString
  }

  /**
   * Converts a PrimitiveValue to a String.
   */
  private def primitiveValueToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.String(s) => s
    case PrimitiveValue.Char(c)   => c.toString
    case other                    => other.toString
  }

  /**
   * Converts a Json representation to a DynamicValue.
   */
  private def jsonToDynamicValue(json: Json): Either[JsonDecoderError, DynamicValue] = json match {
    case Json.Null =>
      Right(DynamicValue.Primitive(PrimitiveValue.Unit))
    case Json.Boolean(b) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Boolean(b)))
    case Json.Number(n) =>
      // Choose the primitive type based on the number's characteristics
      // This matches what the Schema would expect based on the actual Scala type
      if (n.scale <= 0 && n.isValidInt) {
        Right(DynamicValue.Primitive(PrimitiveValue.Int(n.toInt)))
      } else if (n.scale <= 0 && n.isValidLong) {
        Right(DynamicValue.Primitive(PrimitiveValue.Long(n.toLong)))
      } else {
        // For decimal numbers or very large integers, use BigDecimal
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)))
      }
    case Json.String(s) =>
      Right(DynamicValue.Primitive(PrimitiveValue.String(s)))
    case Json.Array(elements) =>
      val converted = elements.map(jsonToDynamicValue)
      val errors    = converted.collect { case Left(err) => err }
      if (errors.nonEmpty) {
        Left(errors.head)
      } else {
        val values = converted.collect { case Right(v) => v }
        Right(DynamicValue.Sequence(values))
      }
    case Json.Object(fields) =>
      // Heuristic: Single-key objects with uppercase first letter are likely variants
      // (e.g., Some, None, or sealed trait cases)
      if (fields.size == 1) {
        val (key, value) = fields.head
        if (key.nonEmpty && key.head.isUpper) {
          // Likely a variant - decode as such
          jsonToDynamicValue(value).map { dv =>
            DynamicValue.Variant(key, dv)
          }
        } else {
          // Single field record
          jsonToDynamicValue(value).map { dv =>
            DynamicValue.Record(Vector((key, dv)))
          }
        }
      } else {
        // Multi-key object is definitely a record
        val converted: Vector[Either[JsonDecoderError, (Predef.String, DynamicValue)]] =
          fields.toVector.map { case (name: Predef.String, value: Json) =>
            jsonToDynamicValue(value).map { (dv: DynamicValue) =>
              (name: Predef.String, dv)
            }
          }
        val errors = converted.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(errors.head)
        } else {
          val fieldValues: Vector[(Predef.String, DynamicValue)] = converted.collect { case Right(v) => v }
          Right(DynamicValue.Record(fieldValues))
        }
      }
  }

  /**
   * Implicit extension to add deriveJsonEncoder to Schema[A].
   */
  implicit class SchemaJsonEncoderOps[A](schema: Schema[A]) {
    def deriveJsonEncoder: JsonEncoder[A] = JsonCodecOps.deriveJsonEncoder(schema)
  }

  /**
   * Implicit extension to add deriveJsonDecoder to Schema[A].
   */
  implicit class SchemaJsonDecoderOps[A](schema: Schema[A]) {
    def deriveJsonDecoder: JsonDecoder[A] = JsonCodecOps.deriveJsonDecoder(schema)
  }
}
