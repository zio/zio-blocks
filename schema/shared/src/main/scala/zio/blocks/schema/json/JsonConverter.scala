package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue, SchemaError}

object JsonConverter {

  def toDynamicValue(json: Json): DynamicValue = json match {
    case Json.Null =>
      DynamicValue.Primitive(
        PrimitiveValue.Unit
      ) // Or None/Null representation? DynamicValue doesn't have specific Null primitive usually?
    // Wait, DynamicValue has PrimitiveValue.Unit? Or Option support?
    // Let's assume Unit for Null or handle as special case.
    // Standard practice: Option is Wrapped.
    // Let's use PrimitiveValue.Unit for now or maybe there is a better representation?
    // PrimitiveValue has Unit, Boolean, Int, etc.
    // Let's use Unit for Null.
    case Json.Bool(b)       => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case Json.Num(n)        => DynamicValue.Primitive(PrimitiveValue.BigDecimal(n.bigDecimal))
    case Json.Str(s)        => DynamicValue.Primitive(PrimitiveValue.String(s))
    case Json.Arr(elements) => DynamicValue.Sequence(elements.map(toDynamicValue))
    case Json.Obj(fields)   => DynamicValue.Record(fields.map { case (k, v) => (k, toDynamicValue(v)) })
  }

  def fromDynamicValue(dv: DynamicValue): Either[SchemaError, Json] = dv match {
    case DynamicValue.Primitive(p) =>
      p match {
        case PrimitiveValue.Unit          => Right(Json.Null) // Assuming Unit corresponds to Null
        case PrimitiveValue.Boolean(b)    => Right(Json.Bool(b))
        case PrimitiveValue.Byte(v)       => Right(Json.Num(BigDecimal(v.toInt)))
        case PrimitiveValue.Short(v)      => Right(Json.Num(BigDecimal(v.toInt)))
        case PrimitiveValue.Int(v)        => Right(Json.Num(BigDecimal(v)))
        case PrimitiveValue.Long(v)       => Right(Json.Num(BigDecimal(v)))
        case PrimitiveValue.Float(v)      => Right(Json.Num(BigDecimal(v.toDouble)))
        case PrimitiveValue.Double(v)     => Right(Json.Num(BigDecimal(v)))
        case PrimitiveValue.BigDecimal(v) => Right(Json.Num(v))
        case PrimitiveValue.BigInt(v)     => Right(Json.Num(BigDecimal(v)))
        case PrimitiveValue.String(s)     => Right(Json.Str(s))
        case PrimitiveValue.Char(c)       => Right(Json.Str(c.toString))
        case _                            => Left(SchemaError.expectationMismatch(Nil, s"Unsupported primitive for Json: $p"))
      }
    case DynamicValue.Record(fields) =>
      // Record represents object
      val jsonFieldsE = fields.map { case (k, v) => fromDynamicValue(v).map(j => (k, j)) }
      // sequence Either
      val (lefts, rights) = jsonFieldsE.partitionMap(identity)
      if (lefts.nonEmpty) Left(lefts.head)
      else Right(Json.Obj(rights))

    case DynamicValue.Sequence(elements) =>
      val elemsE          = elements.map(fromDynamicValue)
      val (lefts, rights) = elemsE.partitionMap(identity)
      if (lefts.nonEmpty) Left(lefts.head)
      else Right(Json.Arr(rights))

    case DynamicValue.Map(entries) =>
      // Map works if keys are strings
      // keys are DynamicValue
      val entriesE = entries.map { case (k, v) =>
        for {
          jsonKey <- fromDynamicValue(k)
          keyStr  <- jsonKey match {
                      case Json.Str(s) => Right(s)
                      case _           => Left(SchemaError.expectationMismatch(Nil, "Map key must be string for Json conversion"))
                    }
          jsonVal <- fromDynamicValue(v)
        } yield (keyStr, jsonVal)
      }
      val (lefts, rights) = entriesE.partitionMap(identity)
      if (lefts.nonEmpty) Left(lefts.head)
      else Right(Json.Obj(rights))

    case _ => Left(SchemaError.expectationMismatch(Nil, s"Unsupported DynamicValue type: ${dv.getClass.getSimpleName}"))
  }
}
