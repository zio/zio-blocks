package zio.blocks.schema

/**
 * Serialization support for DynamicPatch and related types.
 *
 * Since DynamicOptic.Node contains existential types (AtMapKey[K],
 * AtMapKeys[K]), we cannot use Schema.derived directly. Instead, we provide
 * conversion methods to/from DynamicValue which can be used with any
 * Schema[DynamicValue] codec.
 *
 * Usage:
 * {{{
 * // Serialize a DynamicPatch
 * val dynamicValue = PatchSerialization.toValue(patch)
 * val json = Schema.dynamic.encode(JsonCodec)(jsonOutput)(dynamicValue)
 *
 * // Deserialize a DynamicPatch
 * val dynamicValue = Schema.dynamic.decode(JsonCodec)(jsonInput)
 * val patch = PatchSerialization.fromValue(dynamicValue)
 * }}}
 */
object PatchSerialization {

  // ============================================================
  // PatchMode serialization
  // ============================================================

  def patchModeToValue(mode: PatchMode): DynamicValue = mode match {
    case PatchMode.Strict  => DynamicValue.Variant("Strict", DynamicValue.Record(Vector.empty))
    case PatchMode.Lenient => DynamicValue.Variant("Lenient", DynamicValue.Record(Vector.empty))
    case PatchMode.Clobber => DynamicValue.Variant("Clobber", DynamicValue.Record(Vector.empty))
  }

  def patchModeFromValue(value: DynamicValue): Either[String, PatchMode] = value match {
    case DynamicValue.Variant("Strict", _)  => Right(PatchMode.Strict)
    case DynamicValue.Variant("Lenient", _) => Right(PatchMode.Lenient)
    case DynamicValue.Variant("Clobber", _) => Right(PatchMode.Clobber)
    case _                                  => Left(s"Invalid PatchMode: $value")
  }

  // ============================================================
  // StringOp serialization
  // ============================================================

  def stringOpToValue(op: StringOp): DynamicValue = op match {
    case StringOp.Insert(index, text) =>
      DynamicValue.Variant(
        "Insert",
        DynamicValue.Record(
          Vector(
            "index" -> DynamicValue.Primitive(PrimitiveValue.Int(index)),
            "text"  -> DynamicValue.Primitive(PrimitiveValue.String(text))
          )
        )
      )
    case StringOp.Delete(index, length) =>
      DynamicValue.Variant(
        "Delete",
        DynamicValue.Record(
          Vector(
            "index"  -> DynamicValue.Primitive(PrimitiveValue.Int(index)),
            "length" -> DynamicValue.Primitive(PrimitiveValue.Int(length))
          )
        )
      )
  }

  def stringOpFromValue(value: DynamicValue): Either[String, StringOp] = value match {
    case DynamicValue.Variant("Insert", DynamicValue.Record(fields)) =>
      for {
        index <- extractInt(fields, "index")
        text  <- extractString(fields, "text")
      } yield StringOp.Insert(index, text)
    case DynamicValue.Variant("Delete", DynamicValue.Record(fields)) =>
      for {
        index  <- extractInt(fields, "index")
        length <- extractInt(fields, "length")
      } yield StringOp.Delete(index, length)
    case _ => Left(s"Invalid StringOp: $value")
  }

  // ============================================================
  // PrimitiveOp serialization
  // ============================================================

  def primitiveOpToValue(op: PrimitiveOp): DynamicValue = op match {
    case PrimitiveOp.IntDelta(delta) =>
      DynamicValue.Variant(
        "IntDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Int(delta))
          )
        )
      )
    case PrimitiveOp.LongDelta(delta) =>
      DynamicValue.Variant(
        "LongDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Long(delta))
          )
        )
      )
    case PrimitiveOp.DoubleDelta(delta) =>
      DynamicValue.Variant(
        "DoubleDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Double(delta))
          )
        )
      )
    case PrimitiveOp.FloatDelta(delta) =>
      DynamicValue.Variant(
        "FloatDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Float(delta))
          )
        )
      )
    case PrimitiveOp.ShortDelta(delta) =>
      DynamicValue.Variant(
        "ShortDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Short(delta))
          )
        )
      )
    case PrimitiveOp.ByteDelta(delta) =>
      DynamicValue.Variant(
        "ByteDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.Byte(delta))
          )
        )
      )
    case PrimitiveOp.BigIntDelta(delta) =>
      DynamicValue.Variant(
        "BigIntDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.BigInt(delta))
          )
        )
      )
    case PrimitiveOp.BigDecimalDelta(delta) =>
      DynamicValue.Variant(
        "BigDecimalDelta",
        DynamicValue.Record(
          Vector(
            "delta" -> DynamicValue.Primitive(PrimitiveValue.BigDecimal(delta))
          )
        )
      )
    case PrimitiveOp.StringEdit(ops) =>
      DynamicValue.Variant(
        "StringEdit",
        DynamicValue.Record(
          Vector(
            "ops" -> DynamicValue.Sequence(ops.map(stringOpToValue))
          )
        )
      )
    case PrimitiveOp.InstantDelta(duration) =>
      DynamicValue.Variant(
        "InstantDelta",
        DynamicValue.Record(
          Vector(
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
    case PrimitiveOp.DurationDelta(duration) =>
      DynamicValue.Variant(
        "DurationDelta",
        DynamicValue.Record(
          Vector(
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
    case PrimitiveOp.LocalDateDelta(period) =>
      DynamicValue.Variant(
        "LocalDateDelta",
        DynamicValue.Record(
          Vector(
            "period" -> DynamicValue.Primitive(PrimitiveValue.Period(period))
          )
        )
      )
    case PrimitiveOp.LocalTimeDelta(duration) =>
      DynamicValue.Variant(
        "LocalTimeDelta",
        DynamicValue.Record(
          Vector(
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
    case PrimitiveOp.LocalDateTimeDelta(period, duration) =>
      DynamicValue.Variant(
        "LocalDateTimeDelta",
        DynamicValue.Record(
          Vector(
            "period"   -> DynamicValue.Primitive(PrimitiveValue.Period(period)),
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
    case PrimitiveOp.YearDelta(years) =>
      DynamicValue.Variant(
        "YearDelta",
        DynamicValue.Record(
          Vector(
            "years" -> DynamicValue.Primitive(PrimitiveValue.Int(years))
          )
        )
      )
    case PrimitiveOp.YearMonthDelta(months) =>
      DynamicValue.Variant(
        "YearMonthDelta",
        DynamicValue.Record(
          Vector(
            "months" -> DynamicValue.Primitive(PrimitiveValue.Int(months))
          )
        )
      )
    case PrimitiveOp.MonthDayDelta(days) =>
      DynamicValue.Variant(
        "MonthDayDelta",
        DynamicValue.Record(
          Vector(
            "days" -> DynamicValue.Primitive(PrimitiveValue.Int(days))
          )
        )
      )
    case PrimitiveOp.PeriodDelta(period) =>
      DynamicValue.Variant(
        "PeriodDelta",
        DynamicValue.Record(
          Vector(
            "period" -> DynamicValue.Primitive(PrimitiveValue.Period(period))
          )
        )
      )
    case PrimitiveOp.OffsetDateTimeDelta(period, duration) =>
      DynamicValue.Variant(
        "OffsetDateTimeDelta",
        DynamicValue.Record(
          Vector(
            "period"   -> DynamicValue.Primitive(PrimitiveValue.Period(period)),
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
    case PrimitiveOp.ZonedDateTimeDelta(period, duration) =>
      DynamicValue.Variant(
        "ZonedDateTimeDelta",
        DynamicValue.Record(
          Vector(
            "period"   -> DynamicValue.Primitive(PrimitiveValue.Period(period)),
            "duration" -> DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          )
        )
      )
  }

  def primitiveOpFromValue(value: DynamicValue): Either[String, PrimitiveOp] = value match {
    case DynamicValue.Variant("IntDelta", DynamicValue.Record(fields)) =>
      extractInt(fields, "delta").map(PrimitiveOp.IntDelta(_))
    case DynamicValue.Variant("LongDelta", DynamicValue.Record(fields)) =>
      extractLong(fields, "delta").map(PrimitiveOp.LongDelta(_))
    case DynamicValue.Variant("DoubleDelta", DynamicValue.Record(fields)) =>
      extractDouble(fields, "delta").map(PrimitiveOp.DoubleDelta(_))
    case DynamicValue.Variant("StringEdit", DynamicValue.Record(fields)) =>
      extractSequence(fields, "ops").flatMap { seq =>
        val results = seq.map(stringOpFromValue)
        results.collectFirst { case Left(err) => Left(err) }.getOrElse {
          Right(PrimitiveOp.StringEdit(results.collect { case Right(op) => op }))
        }
      }
    case _ => Left(s"Invalid or unsupported PrimitiveOp: $value")
  }

  // ============================================================
  // SeqOp serialization
  // ============================================================

  def seqOpToValue(op: SeqOp): DynamicValue = op match {
    case SeqOp.Insert(index, values) =>
      DynamicValue.Variant(
        "Insert",
        DynamicValue.Record(
          Vector(
            "index"  -> DynamicValue.Primitive(PrimitiveValue.Int(index)),
            "values" -> DynamicValue.Sequence(values)
          )
        )
      )
    case SeqOp.Append(values) =>
      DynamicValue.Variant(
        "Append",
        DynamicValue.Record(
          Vector(
            "values" -> DynamicValue.Sequence(values)
          )
        )
      )
    case SeqOp.Delete(index, count) =>
      DynamicValue.Variant(
        "Delete",
        DynamicValue.Record(
          Vector(
            "index" -> DynamicValue.Primitive(PrimitiveValue.Int(index)),
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(count))
          )
        )
      )
    case SeqOp.Modify(index, operation) =>
      DynamicValue.Variant(
        "Modify",
        DynamicValue.Record(
          Vector(
            "index"     -> DynamicValue.Primitive(PrimitiveValue.Int(index)),
            "operation" -> operationToValue(operation)
          )
        )
      )
  }

  def seqOpFromValue(value: DynamicValue): Either[String, SeqOp] = value match {
    case DynamicValue.Variant("Insert", DynamicValue.Record(fields)) =>
      for {
        index  <- extractInt(fields, "index")
        values <- extractSequence(fields, "values")
      } yield SeqOp.Insert(index, values)
    case DynamicValue.Variant("Append", DynamicValue.Record(fields)) =>
      extractSequence(fields, "values").map(SeqOp.Append(_))
    case DynamicValue.Variant("Delete", DynamicValue.Record(fields)) =>
      for {
        index <- extractInt(fields, "index")
        count <- extractInt(fields, "count")
      } yield SeqOp.Delete(index, count)
    case DynamicValue.Variant("Modify", DynamicValue.Record(fields)) =>
      for {
        index     <- extractInt(fields, "index")
        opValue   <- fields.find(_._1 == "operation").map(_._2).toRight("Missing field: operation")
        operation <- operationFromValue(opValue)
      } yield SeqOp.Modify(index, operation)
    case _ => Left(s"Invalid SeqOp: $value")
  }

  // ============================================================
  // MapOp serialization
  // ============================================================

  def mapOpToValue(op: MapOp): DynamicValue = op match {
    case MapOp.Add(key, value) =>
      DynamicValue.Variant(
        "Add",
        DynamicValue.Record(
          Vector(
            "key"   -> key,
            "value" -> value
          )
        )
      )
    case MapOp.Remove(key) =>
      DynamicValue.Variant(
        "Remove",
        DynamicValue.Record(
          Vector(
            "key" -> key
          )
        )
      )
    case MapOp.Modify(key, operation) =>
      DynamicValue.Variant(
        "Modify",
        DynamicValue.Record(
          Vector(
            "key"       -> key,
            "operation" -> operationToValue(operation)
          )
        )
      )
  }

  def mapOpFromValue(value: DynamicValue): Either[String, MapOp] = value match {
    case DynamicValue.Variant("Add", DynamicValue.Record(fields)) =>
      for {
        key <- fields.find(_._1 == "key").map(_._2).toRight("Missing field: key")
        v   <- fields.find(_._1 == "value").map(_._2).toRight("Missing field: value")
      } yield MapOp.Add(key, v)
    case DynamicValue.Variant("Remove", DynamicValue.Record(fields)) =>
      fields.find(_._1 == "key").map(_._2).toRight("Missing field: key").map(MapOp.Remove(_))
    case DynamicValue.Variant("Modify", DynamicValue.Record(fields)) =>
      for {
        key       <- fields.find(_._1 == "key").map(_._2).toRight("Missing field: key")
        opValue   <- fields.find(_._1 == "operation").map(_._2).toRight("Missing field: operation")
        operation <- operationFromValue(opValue)
      } yield MapOp.Modify(key, operation)
    case _ => Left(s"Invalid MapOp: $value")
  }

  // ============================================================
  // Operation serialization
  // ============================================================

  def operationToValue(op: Operation): DynamicValue = op match {
    case Operation.Set(value) =>
      DynamicValue.Variant(
        "Set",
        DynamicValue.Record(
          Vector(
            "value" -> value
          )
        )
      )
    case Operation.PrimitiveDelta(primitiveOp) =>
      DynamicValue.Variant(
        "PrimitiveDelta",
        DynamicValue.Record(
          Vector(
            "op" -> primitiveOpToValue(primitiveOp)
          )
        )
      )
    case Operation.SequenceEdit(ops) =>
      DynamicValue.Variant(
        "SequenceEdit",
        DynamicValue.Record(
          Vector(
            "ops" -> DynamicValue.Sequence(ops.map(seqOpToValue))
          )
        )
      )
    case Operation.MapEdit(ops) =>
      DynamicValue.Variant(
        "MapEdit",
        DynamicValue.Record(
          Vector(
            "ops" -> DynamicValue.Sequence(ops.map(mapOpToValue))
          )
        )
      )
  }

  def operationFromValue(value: DynamicValue): Either[String, Operation] = value match {
    case DynamicValue.Variant("Set", DynamicValue.Record(fields)) =>
      fields.find(_._1 == "value").map(_._2).toRight("Missing field: value").map(Operation.Set(_))
    case DynamicValue.Variant("PrimitiveDelta", DynamicValue.Record(fields)) =>
      for {
        opValue <- fields.find(_._1 == "op").map(_._2).toRight("Missing field: op")
        op      <- primitiveOpFromValue(opValue)
      } yield Operation.PrimitiveDelta(op)
    case DynamicValue.Variant("SequenceEdit", DynamicValue.Record(fields)) =>
      extractSequence(fields, "ops").flatMap { seq =>
        val results = seq.map(seqOpFromValue)
        results.collectFirst { case Left(err) => Left(err) }.getOrElse {
          Right(Operation.SequenceEdit(results.collect { case Right(op) => op }))
        }
      }
    case DynamicValue.Variant("MapEdit", DynamicValue.Record(fields)) =>
      extractSequence(fields, "ops").flatMap { seq =>
        val results = seq.map(mapOpFromValue)
        results.collectFirst { case Left(err) => Left(err) }.getOrElse {
          Right(Operation.MapEdit(results.collect { case Right(op) => op }))
        }
      }
    case _ => Left(s"Invalid Operation: $value")
  }

  // ============================================================
  // DynamicOptic.Node serialization
  // ============================================================

  def nodeToValue(node: DynamicOptic.Node): DynamicValue = {
    import DynamicOptic.Node._
    node match {
      case Field(name) =>
        DynamicValue.Variant(
          "Field",
          DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String(name))
            )
          )
        )
      case Case(name) =>
        DynamicValue.Variant(
          "Case",
          DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String(name))
            )
          )
        )
      case AtIndex(index) =>
        DynamicValue.Variant(
          "AtIndex",
          DynamicValue.Record(
            Vector(
              "index" -> DynamicValue.Primitive(PrimitiveValue.Int(index))
            )
          )
        )
      case AtMapKey(key) =>
        DynamicValue.Variant(
          "AtMapKey",
          DynamicValue.Record(
            Vector(
              "key" -> anyToValue(key)
            )
          )
        )
      case AtIndices(indices) =>
        DynamicValue.Variant(
          "AtIndices",
          DynamicValue.Record(
            Vector(
              "indices" -> DynamicValue.Sequence(
                indices.map(i => DynamicValue.Primitive(PrimitiveValue.Int(i))).toVector
              )
            )
          )
        )
      case AtMapKeys(keys) =>
        DynamicValue.Variant(
          "AtMapKeys",
          DynamicValue.Record(
            Vector(
              "keys" -> DynamicValue.Sequence(keys.map(anyToValue).toVector)
            )
          )
        )
      case Elements =>
        DynamicValue.Variant("Elements", DynamicValue.Record(Vector.empty))
      case MapKeys =>
        DynamicValue.Variant("MapKeys", DynamicValue.Record(Vector.empty))
      case MapValues =>
        DynamicValue.Variant("MapValues", DynamicValue.Record(Vector.empty))
      case Wrapped =>
        DynamicValue.Variant("Wrapped", DynamicValue.Record(Vector.empty))
    }
  }

  def nodeFromValue(value: DynamicValue): Either[String, DynamicOptic.Node] = {
    import DynamicOptic.Node._
    value match {
      case DynamicValue.Variant("Field", DynamicValue.Record(fields)) =>
        extractString(fields, "name").map(Field(_))
      case DynamicValue.Variant("Case", DynamicValue.Record(fields)) =>
        extractString(fields, "name").map(Case(_))
      case DynamicValue.Variant("AtIndex", DynamicValue.Record(fields)) =>
        extractInt(fields, "index").map(AtIndex(_))
      case DynamicValue.Variant("AtMapKey", DynamicValue.Record(fields)) =>
        fields.find(_._1 == "key").map(_._2).toRight("Missing field: key").map(AtMapKey(_))
      case DynamicValue.Variant("AtIndices", DynamicValue.Record(fields)) =>
        extractSequence(fields, "indices").map { seq =>
          AtIndices(seq.collect { case DynamicValue.Primitive(PrimitiveValue.Int(i)) => i })
        }
      case DynamicValue.Variant("AtMapKeys", DynamicValue.Record(fields)) =>
        extractSequence(fields, "keys").map(AtMapKeys(_))
      case DynamicValue.Variant("Elements", _)  => Right(Elements)
      case DynamicValue.Variant("MapKeys", _)   => Right(MapKeys)
      case DynamicValue.Variant("MapValues", _) => Right(MapValues)
      case DynamicValue.Variant("Wrapped", _)   => Right(Wrapped)
      case _                                    => Left(s"Invalid DynamicOptic.Node: $value")
    }
  }

  // ============================================================
  // DynamicOptic serialization
  // ============================================================

  def opticToValue(optic: DynamicOptic): DynamicValue =
    DynamicValue.Record(
      Vector(
        "nodes" -> DynamicValue.Sequence(optic.nodes.map(nodeToValue).toVector)
      )
    )

  def opticFromValue(value: DynamicValue): Either[String, DynamicOptic] = value match {
    case DynamicValue.Record(fields) =>
      extractSequence(fields, "nodes").flatMap { seq =>
        val results = seq.map(nodeFromValue)
        results.collectFirst { case Left(err) => Left(err) }.getOrElse {
          Right(DynamicOptic(results.collect { case Right(n) => n }))
        }
      }
    case _ => Left(s"Invalid DynamicOptic: $value")
  }

  // ============================================================
  // DynamicPatchOp serialization
  // ============================================================

  def patchOpToValue(op: DynamicPatchOp): DynamicValue =
    DynamicValue.Record(
      Vector(
        "optic"     -> opticToValue(op.optic),
        "operation" -> operationToValue(op.operation)
      )
    )

  def patchOpFromValue(value: DynamicValue): Either[String, DynamicPatchOp] = value match {
    case DynamicValue.Record(fields) =>
      for {
        opticValue <- fields.find(_._1 == "optic").map(_._2).toRight("Missing field: optic")
        optic      <- opticFromValue(opticValue)
        opValue    <- fields.find(_._1 == "operation").map(_._2).toRight("Missing field: operation")
        operation  <- operationFromValue(opValue)
      } yield DynamicPatchOp(optic, operation)
    case _ => Left(s"Invalid DynamicPatchOp: $value")
  }

  // ============================================================
  // DynamicPatch serialization (main entry points)
  // ============================================================

  /** Convert a DynamicPatch to a DynamicValue for serialization */
  def toValue(patch: DynamicPatch): DynamicValue =
    DynamicValue.Record(
      Vector(
        "ops" -> DynamicValue.Sequence(patch.ops.map(patchOpToValue))
      )
    )

  /** Convert a DynamicValue back to a DynamicPatch */
  def fromValue(value: DynamicValue): Either[String, DynamicPatch] = value match {
    case DynamicValue.Record(fields) =>
      extractSequence(fields, "ops").flatMap { seq =>
        val results = seq.map(patchOpFromValue)
        results.collectFirst { case Left(err) => Left(err) }.getOrElse {
          Right(DynamicPatch(results.collect { case Right(op) => op }))
        }
      }
    case _ => Left(s"Invalid DynamicPatch: $value")
  }

  // ============================================================
  // Helper methods
  // ============================================================

  private def anyToValue(any: Any): DynamicValue = any match {
    case dv: DynamicValue => dv
    case s: String        => DynamicValue.Primitive(PrimitiveValue.String(s))
    case i: Int           => DynamicValue.Primitive(PrimitiveValue.Int(i))
    case l: Long          => DynamicValue.Primitive(PrimitiveValue.Long(l))
    case d: Double        => DynamicValue.Primitive(PrimitiveValue.Double(d))
    case b: Boolean       => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case other            => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
  }

  private def extractInt(fields: Vector[(String, DynamicValue)], name: String): Either[String, Int] =
    fields.find(_._1 == name).map(_._2) match {
      case Some(DynamicValue.Primitive(PrimitiveValue.Int(i))) => Right(i)
      case _                                                   => Left(s"Missing or invalid Int field: $name")
    }

  private def extractLong(fields: Vector[(String, DynamicValue)], name: String): Either[String, Long] =
    fields.find(_._1 == name).map(_._2) match {
      case Some(DynamicValue.Primitive(PrimitiveValue.Long(l))) => Right(l)
      case _                                                    => Left(s"Missing or invalid Long field: $name")
    }

  private def extractDouble(fields: Vector[(String, DynamicValue)], name: String): Either[String, Double] =
    fields.find(_._1 == name).map(_._2) match {
      case Some(DynamicValue.Primitive(PrimitiveValue.Double(d))) => Right(d)
      case _                                                      => Left(s"Missing or invalid Double field: $name")
    }

  private def extractString(fields: Vector[(String, DynamicValue)], name: String): Either[String, String] =
    fields.find(_._1 == name).map(_._2) match {
      case Some(DynamicValue.Primitive(PrimitiveValue.String(s))) => Right(s)
      case _                                                      => Left(s"Missing or invalid String field: $name")
    }

  private def extractSequence(
    fields: Vector[(String, DynamicValue)],
    name: String
  ): Either[String, Vector[DynamicValue]] =
    fields.find(_._1 == name).map(_._2) match {
      case Some(DynamicValue.Sequence(seq)) => Right(seq)
      case _                                => Left(s"Missing or invalid Sequence field: $name")
    }
}
