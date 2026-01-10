package zio.blocks.schema

/**
 * An untyped patch that operates on DynamicValue.
 * This is the core representation that the typed Patch[A] wraps.
 *
 * @param ops The sequence of patch operations to apply
 */
final case class DynamicPatch(ops: Vector[DynamicPatchOp]) {

  /**
   * Compose this patch with another patch (monoid operation).
   * The operations are applied sequentially: first this, then that.
   */
  def ++(that: DynamicPatch): DynamicPatch = new DynamicPatch(this.ops ++ that.ops)

  /**
   * Apply this patch to a DynamicValue with the specified mode.
   *
   * @param value The value to patch
   * @param mode The application mode (Strict, Lenient, or Clobber)
   * @return Either an error or the patched value
   */
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    var current = value
    val len     = ops.length
    var idx     = 0
    while (idx < len) {
      val op = ops(idx)
      DynamicPatch.applyOp(current, op.optic, op.operation, mode) match {
        case Right(v) => current = v
        case Left(err) =>
          mode match {
            case PatchMode.Strict  => return Left(err)
            case PatchMode.Lenient => // skip and continue
            case PatchMode.Clobber => // should not reach here for clobber
          }
      }
      idx += 1
    }
    Right(current)
  }
}

object DynamicPatch {

  /** Empty patch (monoid identity) */
  val empty: DynamicPatch = new DynamicPatch(Vector.empty)

  /** Create a patch with a single operation */
  def single(optic: DynamicOptic, operation: Operation): DynamicPatch =
    new DynamicPatch(Vector(new DynamicPatchOp(optic, operation)))

  /**
   * Apply a single operation at the specified path.
   */
  private[schema] def applyOp(
    value: DynamicValue,
    optic: DynamicOptic,
    operation: Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] =
    if (optic.nodes.isEmpty) {
      applyOperation(value, operation, mode, Nil)
    } else {
      applyAtPath(value, optic, 0, operation, mode, Nil)
    }

  /**
   * Navigate to the path and apply the operation.
   */
  private def applyAtPath(
    value: DynamicValue,
    optic: DynamicOptic,
    nodeIdx: Int,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    if (nodeIdx >= optic.nodes.length) {
      applyOperation(value, operation, mode, trace)
    } else {
      val node     = optic.nodes(nodeIdx)
      val newTrace = node :: trace
      node match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(SchemaError.missingField(trace, name))
              } else {
                applyAtPath(fields(fieldIdx)._2, optic, nodeIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (name, newFieldValue)))
                }
              }
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Record"))
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(name, innerValue) =>
              if (name == caseName) {
                applyAtPath(innerValue, optic, nodeIdx + 1, operation, mode, newTrace).map { newInnerValue =>
                  DynamicValue.Variant(name, newInnerValue)
                }
              } else {
                Left(SchemaError.expectationMismatch(trace, s"Expected case $caseName but got $name"))
              }
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Variant"))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) {
                Left(SchemaError.expectationMismatch(trace, s"Index $index out of bounds (size: ${elements.length})"))
              } else {
                applyAtPath(elements(index), optic, nodeIdx + 1, operation, mode, newTrace).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              }
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Sequence"))
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val keyDv = key match {
                case dv: DynamicValue => dv
                case s: String        => DynamicValue.Primitive(PrimitiveValue.String(s))
                case i: Int           => DynamicValue.Primitive(PrimitiveValue.Int(i))
                case other            => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
              }
              val entryIdx = entries.indexWhere(_._1 == keyDv)
              if (entryIdx < 0) {
                Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
              } else {
                applyAtPath(entries(entryIdx)._2, optic, nodeIdx + 1, operation, mode, newTrace).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (keyDv, newValue)))
                }
              }
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Map"))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              var idx       = 0
              val len       = elements.length
              val newElems  = new Array[DynamicValue](len)
              while (idx < len) {
                applyAtPath(elements(idx), optic, nodeIdx + 1, operation, mode, newTrace) match {
                  case Right(v)  => newElems(idx) = v
                  case Left(err) => return Left(err)
                }
                idx += 1
              }
              Right(DynamicValue.Sequence(newElems.toVector))
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Sequence"))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              var idx        = 0
              val len        = entries.length
              val newEntries = new Array[(DynamicValue, DynamicValue)](len)
              while (idx < len) {
                val (k, v) = entries(idx)
                applyAtPath(v, optic, nodeIdx + 1, operation, mode, newTrace) match {
                  case Right(newV) => newEntries(idx) = (k, newV)
                  case Left(err)   => return Left(err)
                }
                idx += 1
              }
              Right(DynamicValue.Map(newEntries.toVector))
            case _ =>
              Left(SchemaError.expectationMismatch(trace, "Expected Map"))
          }

        case DynamicOptic.Node.Wrapped =>
          // For wrapped values, we just pass through
          applyAtPath(value, optic, nodeIdx + 1, operation, mode, newTrace)

        case _ =>
          Left(SchemaError.expectationMismatch(trace, s"Unsupported optic node: $node"))
      }
    }
  }

  /**
   * Apply an operation to a value at the current location.
   */
  private def applyOperation(
    value: DynamicValue,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = operation match {

    case Operation.Set(newValue) =>
      Right(newValue)

    case Operation.PrimitiveDelta(op) =>
      applyPrimitiveDelta(value, op, mode, trace)

    case Operation.SequenceEdit(seqOps) =>
      applySequenceEdit(value, seqOps, mode, trace)

    case Operation.MapEdit(mapOps) =>
      applyMapEdit(value, mapOps, mode, trace)
  }

  /**
   * Apply a primitive delta operation.
   */
  private def applyPrimitiveDelta(
    value: DynamicValue,
    op: PrimitiveOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = value match {
    case DynamicValue.Primitive(pv) =>
      (pv, op) match {
        case (PrimitiveValue.Int(v), PrimitiveOp.IntDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v + d)))

        case (PrimitiveValue.Long(v), PrimitiveOp.LongDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v + d)))

        case (PrimitiveValue.Double(v), PrimitiveOp.DoubleDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v + d)))

        case (PrimitiveValue.Float(v), PrimitiveOp.FloatDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v + d)))

        case (PrimitiveValue.Short(v), PrimitiveOp.ShortDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short((v + d).toShort)))

        case (PrimitiveValue.Byte(v), PrimitiveOp.ByteDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte((v + d).toByte)))

        case (PrimitiveValue.BigInt(v), PrimitiveOp.BigIntDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigInt(v + d)))

        case (PrimitiveValue.BigDecimal(v), PrimitiveOp.BigDecimalDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(v + d)))

        case (PrimitiveValue.String(v), PrimitiveOp.StringEdit(ops)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(applyStringOps(v, ops))))

        case (PrimitiveValue.Instant(v), PrimitiveOp.InstantDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Instant(v.plus(d))))

        case (PrimitiveValue.Duration(v), PrimitiveOp.DurationDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Duration(v.plus(d))))

        case (PrimitiveValue.LocalDate(v), PrimitiveOp.LocalDateDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.LocalDate(v.plus(d))))

        case (PrimitiveValue.LocalTime(v), PrimitiveOp.LocalTimeDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.LocalTime(v.plus(d))))

        case (PrimitiveValue.LocalDateTime(v), PrimitiveOp.LocalDateTimeDelta(period, duration)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(v.plus(period).plus(duration))))

        case (PrimitiveValue.Year(v), PrimitiveOp.YearDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Year(v.plusYears(d.toLong))))

        case (PrimitiveValue.YearMonth(v), PrimitiveOp.YearMonthDelta(d)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.YearMonth(v.plusMonths(d.toLong))))

        case _ =>
          Left(SchemaError.expectationMismatch(trace, s"Cannot apply $op to $pv"))
      }

    case _ =>
      Left(SchemaError.expectationMismatch(trace, "Expected Primitive for delta operation"))
  }

  /**
   * Apply string edit operations.
   */
  private def applyStringOps(s: String, ops: Vector[StringOp]): String = {
    val sb  = new StringBuilder(s)
    var idx = 0
    while (idx < ops.length) {
      ops(idx) match {
        case StringOp.Insert(i, text) =>
          sb.insert(i, text)
        case StringOp.Delete(i, len) =>
          sb.delete(i, i + len)
      }
      idx += 1
    }
    sb.toString
  }

  /**
   * Apply sequence edit operations.
   */
  private def applySequenceEdit(
    value: DynamicValue,
    ops: Vector[SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = value match {
    case DynamicValue.Sequence(elements) =>
      var current = elements
      var idx     = 0
      while (idx < ops.length) {
        ops(idx) match {
          case SeqOp.Insert(i, values) =>
            if (i < 0 || i > current.length) {
              if (mode == PatchMode.Strict) {
                return Left(
                  SchemaError.expectationMismatch(trace, s"Insert index $i out of bounds (size: ${current.length})")
                )
              }
            } else {
              val (before, after) = current.splitAt(i)
              current = before ++ values ++ after
            }

          case SeqOp.Append(values) =>
            current = current ++ values

          case SeqOp.Delete(i, count) =>
            if (i < 0 || i + count > current.length) {
              if (mode == PatchMode.Strict) {
                return Left(
                  SchemaError.expectationMismatch(
                    trace,
                    s"Delete range [$i, ${i + count}) out of bounds (size: ${current.length})"
                  )
                )
              }
            } else {
              current = current.take(i) ++ current.drop(i + count)
            }

          case SeqOp.Modify(i, op) =>
            if (i < 0 || i >= current.length) {
              if (mode == PatchMode.Strict) {
                return Left(
                  SchemaError.expectationMismatch(trace, s"Modify index $i out of bounds (size: ${current.length})")
                )
              }
            } else {
              applyOperation(current(i), op, mode, trace) match {
                case Right(v) => current = current.updated(i, v)
                case Left(e)  => if (mode == PatchMode.Strict) return Left(e)
              }
            }
        }
        idx += 1
      }
      Right(DynamicValue.Sequence(current))

    case _ =>
      Left(SchemaError.expectationMismatch(trace, "Expected Sequence for sequence edit"))
  }

  /**
   * Apply map edit operations.
   */
  private def applyMapEdit(
    value: DynamicValue,
    ops: Vector[MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = value match {
    case DynamicValue.Map(entries) =>
      var current = entries
      var idx     = 0
      while (idx < ops.length) {
        ops(idx) match {
          case MapOp.Add(key, v) =>
            val existingIdx = current.indexWhere(_._1 == key)
            if (existingIdx >= 0) {
              mode match {
                case PatchMode.Strict =>
                  return Left(SchemaError.expectationMismatch(trace, "Key already exists in map"))
                case PatchMode.Clobber =>
                  current = current.updated(existingIdx, (key, v))
                case PatchMode.Lenient =>
                // skip
              }
            } else {
              current = current :+ (key, v)
            }

          case MapOp.Remove(key) =>
            val existingIdx = current.indexWhere(_._1 == key)
            if (existingIdx < 0) {
              if (mode == PatchMode.Strict) {
                return Left(SchemaError.expectationMismatch(trace, "Key not found in map"))
              }
            } else {
              current = current.take(existingIdx) ++ current.drop(existingIdx + 1)
            }

          case MapOp.Modify(key, op) =>
            val existingIdx = current.indexWhere(_._1 == key)
            if (existingIdx < 0) {
              if (mode == PatchMode.Strict) {
                return Left(SchemaError.expectationMismatch(trace, "Key not found in map"))
              }
            } else {
              applyOperation(current(existingIdx)._2, op, mode, trace) match {
                case Right(newV) => current = current.updated(existingIdx, (key, newV))
                case Left(e)     => if (mode == PatchMode.Strict) return Left(e)
              }
            }
        }
        idx += 1
      }
      Right(DynamicValue.Map(current))

    case _ =>
      Left(SchemaError.expectationMismatch(trace, "Expected Map for map edit"))
  }
}
