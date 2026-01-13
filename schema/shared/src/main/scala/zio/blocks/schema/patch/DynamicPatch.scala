package zio.blocks.schema.patch

import zio.blocks.schema._

// An untyped patch that operates on DynamicValue. Patches are serializable and can be composed.
final case class DynamicPatch(ops: Vector[Patch.DynamicPatchOp]) {

  // Apply this patch to a DynamicValue.`value` and `mode`
  // Returns - Either an error or the patched value
  def apply(value: DynamicValue, mode: PatchMode = PatchMode.Strict): Either[SchemaError, DynamicValue] = {
    var current: DynamicValue            = value
    var idx                              = 0
    var error: Either[SchemaError, Unit] = Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      DynamicPatch.applyOp(current, op.path, op.operation, mode) match {
        case Right(updated) =>
          current = updated
        case Left(err) =>
          mode match {
            case PatchMode.Strict  => error = Left(err)
            case PatchMode.Lenient => () // Skip this operation
            case PatchMode.Clobber => () // Clobber mode errors are handled by applyOp
          }
      }
      idx += 1
    }

    error.map(_ => current)
  }

  // Compose two patches. The result applies this patch first, then that patch.
  def ++(that: DynamicPatch): DynamicPatch = DynamicPatch(ops ++ that.ops)

  // Check if this patch is empty (no operations).
  def isEmpty: Boolean = ops.isEmpty
}

object DynamicPatch {

  // Empty patch - identity element for composition.
  val empty: DynamicPatch = DynamicPatch(Vector.empty)

  // Create a patch with a single operation at the root.
  def apply(operation: Patch.Operation): DynamicPatch =
    DynamicPatch(Vector(Patch.DynamicPatchOp(Vector.empty, operation)))

  // Create a patch with a single operation at the given path.
  def apply(path: Vector[DynamicOptic.Node], operation: Patch.Operation): DynamicPatch =
    DynamicPatch(Vector(Patch.DynamicPatchOp(path, operation)))

  // Apply a single operation at a path within a value.
  private[schema] def applyOp(
    value: DynamicValue,
    path: Vector[DynamicOptic.Node],
    operation: Patch.Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] =
    if (path.isEmpty) {
      applyOperation(value, operation, mode, Nil)
    } else {
      navigateAndApply(value, path, 0, operation, mode, Nil)
    }

  // Navigate to the target location and apply the operation.
  // Uses a recursive approach that rebuilds the structure on the way back.
  private def navigateAndApply(
    value: DynamicValue,
    path: Vector[DynamicOptic.Node],
    pathIdx: Int,
    operation: Patch.Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              // Field not found
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.missingField(trace, name))
                case PatchMode.Lenient =>
                  // In lenient mode, skip the operation
                  Left(SchemaError.missingField(trace, name))
                case PatchMode.Clobber =>
                  // In clobber mode, we could add the field, but for navigation
                  // we still need the field to exist to continue
                  Left(SchemaError.missingField(trace, name))
              }
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = DynamicOptic.Node.Field(name) :: trace

              if (isLast) {
                applyOperation(fieldValue, operation, mode, newTrace).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Record but got ${value.getClass.getSimpleName}"))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (index < 0 || index >= elements.length) {
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Index $index out of bounds for sequence of length ${elements.length}"
                )
              )
            } else {
              val element  = elements(index)
              val newTrace = DynamicOptic.Node.AtIndex(index) :: trace

              if (isLast) {
                applyOperation(element, operation, mode, newTrace).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, operation, mode, newTrace).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Sequence but got ${value.getClass.getSimpleName}"))
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        value match {
          case DynamicValue.Map(entries) =>
            val entryIdx = entries.indexWhere(_._1 == key)
            if (entryIdx < 0) {
              Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
            } else {
              val (k, v)   = entries(entryIdx)
              val newTrace = DynamicOptic.Node.AtMapKey(key) :: trace

              if (isLast) {
                applyOperation(v, operation, mode, newTrace).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, newValue)))
                }
              } else {
                navigateAndApply(v, path, pathIdx + 1, operation, mode, newTrace).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (k, newValue)))
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Map but got ${value.getClass.getSimpleName}"))
        }

      case DynamicOptic.Node.Case(expectedCase) =>
        value match {
          case DynamicValue.Variant(caseName, innerValue) =>
            if (caseName != expectedCase) {
              // Case doesn't match - this is an error in Strict mode
              val newTrace = DynamicOptic.Node.Case(expectedCase) :: trace
              Left(
                SchemaError.expectationMismatch(
                  newTrace,
                  s"Expected case $expectedCase but got $caseName"
                )
              )
            } else {
              val newTrace = DynamicOptic.Node.Case(expectedCase) :: trace

              if (isLast) {
                applyOperation(innerValue, operation, mode, newTrace).map { newInnerValue =>
                  DynamicValue.Variant(caseName, newInnerValue)
                }
              } else {
                navigateAndApply(innerValue, path, pathIdx + 1, operation, mode, newTrace).map { newInnerValue =>
                  DynamicValue.Variant(caseName, newInnerValue)
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Variant but got ${value.getClass.getSimpleName}"))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case DynamicValue.Sequence(elements) =>
            val newTrace = DynamicOptic.Node.Elements :: trace

            if (elements.isEmpty) {
              // Empty sequence - in Strict mode, this is an error (can't apply patch to empty list)
              // In Lenient/Clobber mode, return unchanged
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.expectationMismatch(newTrace, "encountered an empty sequence"))
                case _ =>
                  Right(value)
              }
            } else if (isLast) {
              // Apply operation to all elements
              applyToAllElements(elements, operation, mode, newTrace).map(DynamicValue.Sequence(_))
            } else {
              // Navigate deeper into all elements
              navigateAllElements(elements, path, pathIdx + 1, operation, mode, newTrace).map(DynamicValue.Sequence(_))
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Sequence but got ${value.getClass.getSimpleName}"))
        }

      case DynamicOptic.Node.Wrapped =>
        // Wrappers in DynamicValue are typically represented as the unwrapped value directly
        // or as a Record with a single field. For now, we pass through.
        val newTrace = DynamicOptic.Node.Wrapped :: trace
        if (isLast) {
          applyOperation(value, operation, mode, newTrace)
        } else {
          navigateAndApply(value, path, pathIdx + 1, operation, mode, newTrace)
        }

      case DynamicOptic.Node.AtIndices(_) =>
        Left(SchemaError.expectationMismatch(trace, "AtIndices not supported in patches"))

      case DynamicOptic.Node.AtMapKeys(_) =>
        Left(SchemaError.expectationMismatch(trace, "AtMapKeys not supported in patches"))

      case DynamicOptic.Node.MapKeys =>
        Left(SchemaError.expectationMismatch(trace, "MapKeys not supported in patches"))

      case DynamicOptic.Node.MapValues =>
        Left(SchemaError.expectationMismatch(trace, "MapValues not supported in patches"))
    }
  }

  // Apply operation to all elements in a sequence.
  private def applyToAllElements(
    elements: Vector[DynamicValue],
    operation: Patch.Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[DynamicValue]] = {
    val results                    = new Array[DynamicValue](elements.length)
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      applyOperation(elements(idx), operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          // For Case mismatches within traversals, skip the element even in Strict mode
          val shouldSkip = isCaseMismatch(err)
          if (shouldSkip || mode != PatchMode.Strict) {
            results(idx) = elements(idx) // Keep original on error
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(results.toVector)
  }

  // Navigate deeper into all elements of a sequence.
  private def navigateAllElements(
    elements: Vector[DynamicValue],
    path: Vector[DynamicOptic.Node],
    pathIdx: Int,
    operation: Patch.Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[DynamicValue]] = {
    val results                    = new Array[DynamicValue](elements.length)
    var idx                        = 0
    var error: Option[SchemaError] = None

    // Check if the next path node is a Case - if so, we should skip non-matching elements
    // rather than fail, even in Strict mode (this is Traversal semantics)
    val nextIsCase = pathIdx < path.length && path(pathIdx).isInstanceOf[DynamicOptic.Node.Case]

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      navigateAndApply(elements(idx), path, pathIdx, operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          // For Case mismatches within traversals, skip the element even in Strict mode
          val shouldSkip = nextIsCase && isCaseMismatch(err)
          if (shouldSkip || mode != PatchMode.Strict) {
            results(idx) = elements(idx) // Keep original on error
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(results.toVector)
  }

  // Check if an error is a Case mismatch (variant case doesn't match expected).
  private def isCaseMismatch(err: SchemaError): Boolean =
    err.errors.headOption.exists { single =>
      single.message.contains("Expected case") && single.message.contains("but got")
    }

  // Apply an operation to a value (at the current location).
  private def applyOperation(
    value: DynamicValue,
    operation: Patch.Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    operation match {
      case Patch.Operation.Set(newValue) =>
        Right(newValue)

      case Patch.Operation.PrimitiveDelta(op) =>
        applyPrimitiveDelta(value, op, trace)

      case Patch.Operation.SequenceEdit(seqOps) =>
        applySequenceEdit(value, seqOps, mode, trace)

      case Patch.Operation.MapEdit(mapOps) =>
        applyMapEdit(value, mapOps, mode, trace)
    }

  private def applyPrimitiveDelta(
    value: DynamicValue,
    op: Patch.PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case DynamicValue.Primitive(pv) =>
        applyPrimitiveOpToValue(pv, op, trace).map(DynamicValue.Primitive(_))
      case _ =>
        Left(SchemaError.expectationMismatch(trace, s"Expected Primitive but got ${value.getClass.getSimpleName}"))
    }

  private def applyPrimitiveOpToValue(
    pv: PrimitiveValue,
    op: Patch.PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, PrimitiveValue] =
    (pv, op) match {
      // Numeric deltas
      case (PrimitiveValue.Int(v), Patch.PrimitiveOp.IntDelta(delta)) =>
        Right(PrimitiveValue.Int(v + delta))

      case (PrimitiveValue.Long(v), Patch.PrimitiveOp.LongDelta(delta)) =>
        Right(PrimitiveValue.Long(v + delta))

      case (PrimitiveValue.Double(v), Patch.PrimitiveOp.DoubleDelta(delta)) =>
        Right(PrimitiveValue.Double(v + delta))

      case (PrimitiveValue.Float(v), Patch.PrimitiveOp.FloatDelta(delta)) =>
        Right(PrimitiveValue.Float(v + delta))

      case (PrimitiveValue.Short(v), Patch.PrimitiveOp.ShortDelta(delta)) =>
        Right(PrimitiveValue.Short((v + delta).toShort))

      case (PrimitiveValue.Byte(v), Patch.PrimitiveOp.ByteDelta(delta)) =>
        Right(PrimitiveValue.Byte((v + delta).toByte))

      case (PrimitiveValue.BigInt(v), Patch.PrimitiveOp.BigIntDelta(delta)) =>
        Right(PrimitiveValue.BigInt(v + delta))

      case (PrimitiveValue.BigDecimal(v), Patch.PrimitiveOp.BigDecimalDelta(delta)) =>
        Right(PrimitiveValue.BigDecimal(v + delta))

      // String edits
      case (PrimitiveValue.String(v), Patch.PrimitiveOp.StringEdit(ops)) =>
        applyStringEdits(v, ops, trace).map(PrimitiveValue.String(_))

      // Temporal deltas
      case (PrimitiveValue.Instant(v), Patch.PrimitiveOp.InstantDelta(delta)) =>
        Right(PrimitiveValue.Instant(v.plus(delta)))

      case (PrimitiveValue.Duration(v), Patch.PrimitiveOp.DurationDelta(delta)) =>
        Right(PrimitiveValue.Duration(v.plus(delta)))

      case (PrimitiveValue.LocalDate(v), Patch.PrimitiveOp.LocalDateDelta(delta)) =>
        Right(PrimitiveValue.LocalDate(v.plus(delta)))

      case (PrimitiveValue.LocalDateTime(v), Patch.PrimitiveOp.LocalDateTimeDelta(periodDelta, durationDelta)) =>
        Right(PrimitiveValue.LocalDateTime(v.plus(periodDelta).plus(durationDelta)))

      case (PrimitiveValue.Period(v), Patch.PrimitiveOp.PeriodDelta(delta)) =>
        Right(PrimitiveValue.Period(v.plus(delta)))

      case _ =>
        Left(
          SchemaError.expectationMismatch(
            trace,
            s"Type mismatch: cannot apply ${op.getClass.getSimpleName} to ${pv.getClass.getSimpleName}"
          )
        )
    }

  private def applyStringEdits(
    str: String,
    ops: Vector[Patch.StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, String] = {
    var result                     = str
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case Patch.StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            error = Some(
              SchemaError.expectationMismatch(
                trace,
                s"String insert index $index out of bounds for string of length ${result.length}"
              )
            )
          } else {
            result = result.substring(0, index) + text + result.substring(index)
          }

        case Patch.StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            error = Some(
              SchemaError.expectationMismatch(
                trace,
                s"String delete range [$index, ${index + length}) out of bounds for string of length ${result.length}"
              )
            )
          } else {
            result = result.substring(0, index) + result.substring(index + length)
          }

        case Patch.StringOp.Append(text) =>
          result = result + text

        case Patch.StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length) {
            error = Some(
              SchemaError.expectationMismatch(
                trace,
                s"String modify range [$index, ${index + length}) out of bounds for string of length ${result.length}"
              )
            )
          } else {
            result = result.substring(0, index) + text + result.substring(index + length)
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applySequenceEdit(
    value: DynamicValue,
    ops: Vector[Patch.SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case DynamicValue.Sequence(elements) =>
        applySeqOps(elements, ops, mode, trace).map(DynamicValue.Sequence(_))
      case _ =>
        Left(SchemaError.expectationMismatch(trace, s"Expected Sequence but got ${value.getClass.getSimpleName}"))
    }

  private def applySeqOps(
    elements: Vector[DynamicValue],
    ops: Vector[Patch.SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[DynamicValue]] = {
    var result                     = elements
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      applySeqOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => () // Skip
            case PatchMode.Clobber => () // Already handled in applySeqOp
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applySeqOp(
    elements: Vector[DynamicValue],
    op: Patch.SeqOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[DynamicValue]] =
    op match {
      case Patch.SeqOp.Append(values) =>
        Right(elements ++ values)

      case Patch.SeqOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          mode match {
            case PatchMode.Strict =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Insert index $index out of bounds for sequence of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Insert index $index out of bounds for sequence of length ${elements.length}"
                )
              )
            case PatchMode.Clobber =>
              // In clobber mode, clamp the index
              val clampedIndex    = Math.max(0, Math.min(index, elements.length))
              val (before, after) = elements.splitAt(clampedIndex)
              Right(before ++ values ++ after)
          }
        } else {
          val (before, after) = elements.splitAt(index)
          Right(before ++ values ++ after)
        }

      case Patch.SeqOp.Delete(index, count) =>
        if (index < 0 || index + count > elements.length) {
          mode match {
            case PatchMode.Strict =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Delete range [$index, ${index + count}) out of bounds for sequence of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Delete range [$index, ${index + count}) out of bounds for sequence of length ${elements.length}"
                )
              )
            case PatchMode.Clobber =>
              // In clobber mode, delete what we can
              val clampedIndex = Math.max(0, Math.min(index, elements.length))
              val clampedEnd   = Math.max(0, Math.min(index + count, elements.length))
              Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
          }
        } else {
          Right(elements.take(index) ++ elements.drop(index + count))
        }

      case Patch.SeqOp.Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length) {
          Left(
            SchemaError.expectationMismatch(
              trace,
              s"Modify index $index out of bounds for sequence of length ${elements.length}"
            )
          )
        } else {
          val element  = elements(index)
          val newTrace = DynamicOptic.Node.AtIndex(index) :: trace
          applyOperation(element, nestedOp, mode, newTrace).map { newElement =>
            elements.updated(index, newElement)
          }
        }
    }

  private def applyMapEdit(
    value: DynamicValue,
    ops: Vector[Patch.MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] =
    value match {
      case DynamicValue.Map(entries) =>
        applyMapOps(entries, ops, mode, trace).map(DynamicValue.Map(_))
      case _ =>
        Left(SchemaError.expectationMismatch(trace, s"Expected Map but got ${value.getClass.getSimpleName}"))
    }

  private def applyMapOps(
    entries: Vector[(DynamicValue, DynamicValue)],
    ops: Vector[Patch.MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
    var result                     = entries
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      applyMapOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => () // Skip
            case PatchMode.Clobber => () // Already handled in applyMapOp
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applyMapOp(
    entries: Vector[(DynamicValue, DynamicValue)],
    op: Patch.MapOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] =
    op match {
      case Patch.MapOp.Add(key, value) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          mode match {
            case PatchMode.Strict =>
              Left(SchemaError.expectationMismatch(trace, s"Key already exists in map"))
            case PatchMode.Lenient =>
              Left(SchemaError.expectationMismatch(trace, s"Key already exists in map"))
            case PatchMode.Clobber =>
              // Overwrite existing
              Right(entries.updated(existingIdx, (key, value)))
          }
        } else {
          Right(entries :+ (key, value))
        }

      case Patch.MapOp.Remove(key) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict =>
              Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
            case PatchMode.Lenient =>
              Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
            case PatchMode.Clobber =>
              // Nothing to remove, return unchanged
              Right(entries)
          }
        } else {
          Right(entries.take(existingIdx) ++ entries.drop(existingIdx + 1))
        }

      case Patch.MapOp.Modify(key, nestedPatch) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          Left(SchemaError.expectationMismatch(trace, s"Key not found in map"))
        } else {
          val (k, v) = entries(existingIdx)
          nestedPatch.apply(v, mode).map { newValue =>
            entries.updated(existingIdx, (k, newValue))
          }
        }
    }
}
