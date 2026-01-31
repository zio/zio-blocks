package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}

/**
 * An untyped patch that operates on [[Json]] values. Patches are composable and
 * can be applied with different failure handling modes.
 *
 * @param ops
 *   The sequence of patch operations to apply
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) {

  /**
   * Composes two patches. The result applies this patch first, then that patch.
   *
   * @param that
   *   The patch to apply after this one
   * @return
   *   A new patch that applies both patches in sequence
   */
  def ++(that: JsonPatch): JsonPatch = JsonPatch(ops ++ that.ops)

  def isEmpty: Boolean = ops.isEmpty

  /**
   * Apply this patch to a Json value.
   *
   * @param value
   *   The Json value to patch
   * @param mode
   *   The patch mode controlling failure handling:
   *   - Strict: Fail on any precondition violation
   *   - Lenient: Skip operations that fail preconditions
   *   - Clobber: Overwrite/force on conflicts
   * @return
   *   Either an error or the patched value
   */
  def apply(value: Json, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json] = {
    var current: Json                    = value
    var idx                              = 0
    var error: Either[SchemaError, Unit] = Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op.path.nodes, op.operation, mode) match {
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

  /**
   * Converts this JsonPatch to a DynamicPatch.
   *
   * This allows JsonPatch operations to be used with the generic patching
   * infrastructure or serialized using DynamicPatch's schema.
   *
   * @return
   *   A DynamicPatch equivalent to this JsonPatch
   */
  def toDynamicPatch: DynamicPatch = {
    val dynamicOps = ops.map { jsonOp =>
      val dynamicOp = JsonPatch.opToDynamicOperation(jsonOp.operation)
      DynamicPatch.DynamicPatchOp(jsonOp.path, dynamicOp)
    }
    DynamicPatch(dynamicOps)
  }

  override def toString: String =
    if (ops.isEmpty) "JsonPatch {}"
    else {
      val sb = new StringBuilder("JsonPatch {\n")
      ops.foreach(op => JsonPatch.renderOp(sb, op, "  "))
      sb.append("}")
      sb.toString
    }
}

object JsonPatch extends JsonPatchCompanionVersionSpecific {

  /** Empty patch - identity element for composition. */
  val empty: JsonPatch = JsonPatch(Vector.empty)

  /**
   * Converts a DynamicPatch to a JsonPatch.
   *
   * Temporal Deltas and Map Key which are not Strings, fail.
   *
   * Numeric deltas from DynamicPatch (IntDelta, LongDelta, etc.) are widened to
   * NumberDelta (BigDecimal) since JSON has a single number type.
   *
   * @param patch
   *   The DynamicPatch to convert
   * @return
   *   Either an error for unsupported operations, or the equivalent JsonPatch
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch] = {
    val builder                    = Vector.newBuilder[JsonPatchOp]
    var error: Option[SchemaError] = None
    var idx                        = 0

    while (idx < patch.ops.length && error.isEmpty) {
      val dynOp = patch.ops(idx)
      operationFromDynamic(dynOp.operation) match {
        case Right(op) =>
          builder.addOne(JsonPatchOp(dynOp.path, op))
        case Left(err) =>
          error = Some(err)
      }
      idx += 1
    }

    error.toLeft(JsonPatch(builder.result()))
  }

  /**
   * Compute the diff between two Json values.
   *
   * Returns a JsonPatch that transforms `source` into `target`. The patch uses
   * minimal operations:
   *   - NumberDelta for number changes
   *   - StringEdit when more compact than Set
   *   - ArrayEdit with LCS-based Insert/Delete operations
   *   - ObjectEdit for field changes
   *
   * @param source
   *   The original Json value
   * @param target
   *   The desired Json value
   * @return
   *   A JsonPatch that transforms source into target
   */
  def diff(source: Json, target: Json): JsonPatch = JsonDiffer.diff(source, target)

  // Apply Implementation

  /**
   * Apply a single operation at a path within a value.
   */
  private[json] def applyOp(
    value: Json,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Op,
    mode: PatchMode
  ): Either[SchemaError, Json] =
    if (path.isEmpty) {
      applyOperation(value, operation, mode, Nil)
    } else {
      navigateAndApply(value, path, 0, operation, mode, Nil)
    }

  /**
   * Navigate to the target location and apply the operation. Uses a recursive
   * approach that rebuilds the structure on the way back.
   */
  private def navigateAndApply(
    value: Json,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case Json.Object(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              // Field not found
              Left(SchemaError.missingField(trace, name))
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = DynamicOptic.Node.Field(name) :: trace

              if (isLast) {
                applyOperation(fieldValue, operation, mode, newTrace).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Object but got ${value.jsonType}"))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        value match {
          case Json.Array(elements) =>
            if (index < 0 || index >= elements.length) {
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Index $index out of bounds for array of length ${elements.length}"
                )
              )
            } else {
              val element  = elements(index)
              val newTrace = DynamicOptic.Node.AtIndex(index) :: trace

              if (isLast) {
                applyOperation(element, operation, mode, newTrace).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, operation, mode, newTrace).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              }
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case Json.Array(elements) =>
            val newTrace = DynamicOptic.Node.Elements :: trace

            if (elements.isEmpty) {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.expectationMismatch(newTrace, "encountered an empty array"))
                case _ =>
                  Right(value)
              }
            } else if (isLast) {
              applyToAllElements(elements, operation, mode, newTrace).map(Json.Array(_))
            } else {
              navigateAllElements(elements, path, pathIdx + 1, operation, mode, newTrace).map(Json.Array(_))
            }

          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }

      case DynamicOptic.Node.Case(_) =>
        // JSON doesn't have variant types - case navigation is not supported
        Left(SchemaError.expectationMismatch(trace, "Case navigation not supported for JSON values"))

      case DynamicOptic.Node.Wrapped =>
        // JSON doesn't have wrapper types - pass through
        val newTrace = DynamicOptic.Node.Wrapped :: trace
        if (isLast) {
          applyOperation(value, operation, mode, newTrace)
        } else {
          navigateAndApply(value, path, pathIdx + 1, operation, mode, newTrace)
        }

      case DynamicOptic.Node.AtMapKey(_) =>
        Left(
          SchemaError.expectationMismatch(trace, "AtMapKey not supported for JSON - use Field navigation for objects")
        )

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

  /**
   * Apply operation to all elements in an array.
   */
  private def applyToAllElements(
    elements: Chunk[Json],
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] = {
    val results                    = new Array[Json](elements.length)
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      applyOperation(elements(idx), operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode != PatchMode.Strict) {
            results(idx) = elements(idx) // Keep original on error
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(Chunk.fromArray(results))
  }

  /**
   * Navigate deeper into all elements of an array.
   */
  private def navigateAllElements(
    elements: Chunk[Json],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] = {
    val results                    = new Array[Json](elements.length)
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      navigateAndApply(elements(idx), path, pathIdx, operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode != PatchMode.Strict) {
            results(idx) = elements(idx) // Keep original on error
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(Chunk.fromArray(results))
  }

  /**
   * Apply an operation to a value (at the current location).
   */
  private def applyOperation(
    value: Json,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    operation match {
      case Op.Set(newValue) =>
        Right(newValue)

      case Op.PrimitiveDelta(op) =>
        applyPrimitiveDelta(value, op, trace)

      case Op.ArrayEdit(arrayOps) =>
        applyArrayEdit(value, arrayOps, mode, trace)

      case Op.ObjectEdit(objectOps) =>
        applyObjectEdit(value, objectOps, mode, trace)

      case Op.Nested(nestedPatch) =>
        nestedPatch.apply(value, mode)
    }

  /**
   * Apply a primitive delta operation (number delta or string edits).
   */
  private def applyPrimitiveDelta(
    value: Json,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    op match {
      case PrimitiveOp.NumberDelta(delta) =>
        value match {
          case Json.Number(numStr) =>
            try {
              val current = BigDecimal(numStr)
              val result  = current + delta
              Right(Json.Number(result.toString))
            } catch {
              case _: NumberFormatException =>
                Left(SchemaError.expectationMismatch(trace, s"Invalid number format: $numStr"))
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected Number but got ${value.jsonType}"))
        }

      case PrimitiveOp.StringEdit(ops) =>
        value match {
          case Json.String(str) =>
            applyStringOps(str, ops, trace).map(Json.String(_))
          case _ =>
            Left(SchemaError.expectationMismatch(trace, s"Expected String but got ${value.jsonType}"))
        }
    }

  /**
   * Apply string edit operations to a string value.
   */
  private def applyStringOps(
    str: String,
    ops: Vector[StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, String] = {
    var result                     = str
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
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

        case StringOp.Delete(index, length) =>
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

        case StringOp.Append(text) =>
          result = result + text

        case StringOp.Modify(index, length, text) =>
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

  /**
   * Apply array edit operations.
   */
  private def applyArrayEdit(
    value: Json,
    ops: Vector[ArrayOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    value match {
      case Json.Array(elements) =>
        applyArrayOps(elements, ops, mode, trace).map(Json.Array(_))
      case _ =>
        Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
    }

  /**
   * Apply array operations to elements.
   */
  private def applyArrayOps(
    elements: Chunk[Json],
    ops: Vector[ArrayOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] = {
    var result                     = elements
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      applyArrayOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => () // Skip
            case PatchMode.Clobber => () // Already handled in applyArrayOp
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  /**
   * Apply a single array operation.
   */
  private def applyArrayOp(
    elements: Chunk[Json],
    op: ArrayOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] =
    op match {
      case ArrayOp.Append(values) =>
        Right(elements ++ values)

      case ArrayOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          mode match {
            case PatchMode.Strict =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Insert index $index out of bounds for array of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Insert index $index out of bounds for array of length ${elements.length}"
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

      case ArrayOp.Delete(index, count) =>
        if (index < 0 || index + count > elements.length) {
          mode match {
            case PatchMode.Strict =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Delete range [$index, ${index + count}) out of bounds for array of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Delete range [$index, ${index + count}) out of bounds for array of length ${elements.length}"
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

      case ArrayOp.Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length) {
          Left(
            SchemaError.expectationMismatch(
              trace,
              s"Modify index $index out of bounds for array of length ${elements.length}"
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

  /**
   * Apply object edit operations.
   */
  private def applyObjectEdit(
    value: Json,
    ops: Vector[ObjectOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    value match {
      case Json.Object(fields) =>
        applyObjectOps(fields, ops, mode, trace).map(Json.Object(_))
      case _ =>
        Left(SchemaError.expectationMismatch(trace, s"Expected Object but got ${value.jsonType}"))
    }

  /**
   * Apply object operations to fields.
   */
  private def applyObjectOps(
    fields: Chunk[(String, Json)],
    ops: Vector[ObjectOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[(String, Json)]] = {
    var result                     = fields
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      applyObjectOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => () // Skip
            case PatchMode.Clobber => () // Already handled in applyObjectOp
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  /**
   * Apply a single object operation.
   */
  private def applyObjectOp(
    fields: Chunk[(String, Json)],
    op: ObjectOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[(String, Json)]] =
    op match {
      case ObjectOp.Add(key, value) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          mode match {
            case PatchMode.Strict =>
              Left(SchemaError.expectationMismatch(trace, s"Key '$key' already exists in object"))
            case PatchMode.Lenient =>
              Left(SchemaError.expectationMismatch(trace, s"Key '$key' already exists in object"))
            case PatchMode.Clobber =>
              // Overwrite existing
              Right(fields.updated(existingIdx, (key, value)))
          }
        } else {
          Right(fields :+ (key, value))
        }

      case ObjectOp.Remove(key) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict =>
              Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
            case PatchMode.Lenient =>
              Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
            case PatchMode.Clobber =>
              // Nothing to remove, return unchanged
              Right(fields)
          }
        } else {
          Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
        }

      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
        } else {
          val (k, v) = fields(existingIdx)
          nestedPatch.apply(v, mode).map { newValue =>
            fields.updated(existingIdx, (k, newValue))
          }
        }
    }

  /**
   * Creates a patch with a single operation at the root.
   *
   * @param operation
   *   The operation to apply at the root
   * @return
   *   A new patch with the operation at the root path
   */
  def root(operation: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(DynamicOptic.root, operation)))

  /**
   * Creates a patch with a single operation at the given path.
   *
   * @param path
   *   The path where the operation should be applied
   * @param operation
   *   The operation to apply
   * @return
   *   A new patch with the operation at the specified path
   */
  def apply(path: DynamicOptic, operation: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(path, operation)))

  private[json] def renderOp(sb: StringBuilder, op: JsonPatchOp, indent: String): Unit = {
    val pathStr = renderPath(op.path.nodes)
    op.operation match {
      case Op.Set(value) =>
        sb.append(indent).append(pathStr).append(" = ").append(value).append("\n")

      case Op.PrimitiveDelta(primitiveOp) =>
        renderPrimitiveDelta(sb, pathStr, primitiveOp, indent)

      case Op.ArrayEdit(arrayOps) =>
        sb.append(indent).append(pathStr).append(":\n")
        arrayOps.foreach(ao => renderArrayOp(sb, ao, indent + "  "))

      case Op.ObjectEdit(objectOps) =>
        sb.append(indent).append(pathStr).append(":\n")
        objectOps.foreach(oo => renderObjectOp(sb, oo, indent + "  "))

      case Op.Nested(nestedPatch) =>
        sb.append(indent).append(pathStr).append(":\n")
        nestedPatch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }
  }

  private def renderPath(nodes: IndexedSeq[DynamicOptic.Node]): String = {
    if (nodes.isEmpty) return "root"
    val sb = new StringBuilder
    nodes.foreach {
      case DynamicOptic.Node.Field(name)    => sb.append('.').append(name)
      case DynamicOptic.Node.AtIndex(index) => sb.append('[').append(index).append(']')
      case other                            => sb.append(other.toString)
    }
    sb.toString
  }

  private def renderPrimitiveDelta(sb: StringBuilder, pathStr: String, op: PrimitiveOp, indent: String): Unit =
    op match {
      case PrimitiveOp.NumberDelta(d) =>
        if (d >= BigDecimal(0)) sb.append(indent).append(pathStr).append(" += ").append(d).append("\n")
        else sb.append(indent).append(pathStr).append(" -= ").append(-d).append("\n")

      case PrimitiveOp.StringEdit(ops) =>
        sb.append(indent).append(pathStr).append(":\n")
        ops.foreach {
          case StringOp.Insert(idx, text) =>
            sb.append(indent).append("  + [").append(idx).append(": ").append(escapeString(text)).append("]\n")
          case StringOp.Delete(idx, len) =>
            sb.append(indent).append("  - [").append(idx).append(", ").append(len).append("]\n")
          case StringOp.Append(text) =>
            sb.append(indent).append("  + ").append(escapeString(text)).append("\n")
          case StringOp.Modify(idx, len, text) =>
            sb.append(indent)
              .append("  ~ [")
              .append(idx)
              .append(", ")
              .append(len)
              .append(": ")
              .append(escapeString(text))
              .append("]\n")
        }
    }

  private def renderArrayOp(sb: StringBuilder, op: ArrayOp, indent: String): Unit =
    op match {
      case ArrayOp.Insert(index, values) =>
        values.zipWithIndex.foreach { case (v, i) =>
          sb.append(indent).append("+ [").append(index + i).append(": ").append(v).append("]\n")
        }
      case ArrayOp.Append(values) =>
        values.foreach { v =>
          sb.append(indent).append("+ ").append(v).append("\n")
        }
      case ArrayOp.Delete(index, count) =>
        if (count == 1) sb.append(indent).append("- [").append(index).append("]\n")
        else {
          val indices = (index until index + count).mkString(", ")
          sb.append(indent).append("- [").append(indices).append("]\n")
        }
      case ArrayOp.Modify(index, nestedOp) =>
        nestedOp match {
          case Op.Set(v) =>
            sb.append(indent).append("~ [").append(index).append(": ").append(v).append("]\n")
          case _ =>
            sb.append(indent).append("~ [").append(index).append("]:\n")
            renderOp(sb, JsonPatchOp(DynamicOptic.root, nestedOp), indent + "  ")
        }
    }

  private def renderObjectOp(sb: StringBuilder, op: ObjectOp, indent: String): Unit =
    op match {
      case ObjectOp.Add(k, v) =>
        sb.append(indent).append("+ {").append(escapeString(k)).append(": ").append(v).append("}\n")
      case ObjectOp.Remove(k) =>
        sb.append(indent).append("- {").append(escapeString(k)).append("}\n")
      case ObjectOp.Modify(k, patch) =>
        sb.append(indent).append("~ {").append(escapeString(k)).append("}:\n")
        patch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }

  private def escapeString(s: String): String = {
    val sb = new StringBuilder("\"")
    s.foreach {
      case '"'          => sb.append("\\\"")
      case '\\'         => sb.append("\\\\")
      case '\b'         => sb.append("\\b")
      case '\f'         => sb.append("\\f")
      case '\n'         => sb.append("\\n")
      case '\r'         => sb.append("\\r")
      case '\t'         => sb.append("\\t")
      case c if c < ' ' =>
        sb.append(f"\\u${c.toInt}%04x")
      case c =>
        sb.append(c)
    }
    sb.append("\"")
    sb.toString
  }

  // DynamicPatch Conversion Helpers

  /**
   * Converts a JsonPatch.Op to a DynamicPatch.Operation.
   */
  private[json] def opToDynamicOperation(op: Op): DynamicPatch.Operation =
    op match {
      case Op.Set(value) =>
        DynamicPatch.Operation.Set(value.toDynamicValue)

      case Op.PrimitiveDelta(primitiveOp) =>
        DynamicPatch.Operation.PrimitiveDelta(primitiveOpToDynamic(primitiveOp))

      case Op.ArrayEdit(arrayOps) =>
        DynamicPatch.Operation.SequenceEdit(arrayOps.map(arrayOpToDynamic))

      case Op.ObjectEdit(objectOps) =>
        DynamicPatch.Operation.MapEdit(objectOps.map(objectOpToDynamic))

      case Op.Nested(nestedPatch) =>
        DynamicPatch.Operation.Patch(nestedPatch.toDynamicPatch)
    }

  private def primitiveOpToDynamic(op: PrimitiveOp): DynamicPatch.PrimitiveOp =
    op match {
      case PrimitiveOp.NumberDelta(delta) =>
        DynamicPatch.PrimitiveOp.BigDecimalDelta(delta)

      case PrimitiveOp.StringEdit(ops) =>
        DynamicPatch.PrimitiveOp.StringEdit(ops.map(stringOpToDynamic))
    }

  private def stringOpToDynamic(op: StringOp): DynamicPatch.StringOp =
    op match {
      case StringOp.Insert(index, text)         => DynamicPatch.StringOp.Insert(index, text)
      case StringOp.Delete(index, length)       => DynamicPatch.StringOp.Delete(index, length)
      case StringOp.Append(text)                => DynamicPatch.StringOp.Append(text)
      case StringOp.Modify(index, length, text) => DynamicPatch.StringOp.Modify(index, length, text)
    }

  private def arrayOpToDynamic(op: ArrayOp): DynamicPatch.SeqOp =
    op match {
      case ArrayOp.Insert(index, values) =>
        DynamicPatch.SeqOp.Insert(index, values.map(_.toDynamicValue))

      case ArrayOp.Append(values) =>
        DynamicPatch.SeqOp.Append(values.map(_.toDynamicValue))

      case ArrayOp.Delete(index, count) =>
        DynamicPatch.SeqOp.Delete(index, count)

      case ArrayOp.Modify(index, nestedOp) =>
        DynamicPatch.SeqOp.Modify(index, opToDynamicOperation(nestedOp))
    }

  private def objectOpToDynamic(op: ObjectOp): DynamicPatch.MapOp =
    op match {
      case ObjectOp.Add(key, value) =>
        DynamicPatch.MapOp.Add(DynamicValue.string(key), value.toDynamicValue)

      case ObjectOp.Remove(key) =>
        DynamicPatch.MapOp.Remove(DynamicValue.string(key))

      case ObjectOp.Modify(key, nestedPatch) =>
        DynamicPatch.MapOp.Modify(DynamicValue.string(key), nestedPatch.toDynamicPatch)
    }

  /**
   * Converts a DynamicPatch.Operation to a JsonPatch.Op.
   */
  private def operationFromDynamic(op: DynamicPatch.Operation): Either[SchemaError, Op] =
    op match {
      case DynamicPatch.Operation.Set(value) =>
        Right(Op.Set(Json.fromDynamicValue(value)))

      case DynamicPatch.Operation.PrimitiveDelta(primitiveOp) =>
        primitiveOpFromDynamic(primitiveOp).map(Op.PrimitiveDelta(_))

      case DynamicPatch.Operation.SequenceEdit(seqOps) =>
        sequenceAll(seqOps.map(seqOpFromDynamic)).map(ops => Op.ArrayEdit(ops))

      case DynamicPatch.Operation.MapEdit(mapOps) =>
        sequenceAll(mapOps.map(mapOpFromDynamic)).map(ops => Op.ObjectEdit(ops))

      case DynamicPatch.Operation.Patch(nestedPatch) =>
        fromDynamicPatch(nestedPatch).map(Op.Nested(_))
    }

  private def primitiveOpFromDynamic(op: DynamicPatch.PrimitiveOp): Either[SchemaError, PrimitiveOp] =
    op match {
      // Numeric deltas - widen to BigDecimal
      case DynamicPatch.PrimitiveOp.IntDelta(delta)        => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.LongDelta(delta)       => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.ShortDelta(delta)      => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.ByteDelta(delta)       => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.FloatDelta(delta)      => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble)))
      case DynamicPatch.PrimitiveOp.DoubleDelta(delta)     => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.BigIntDelta(delta)     => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) => Right(PrimitiveOp.NumberDelta(delta))

      // String edits
      case DynamicPatch.PrimitiveOp.StringEdit(ops) =>
        Right(PrimitiveOp.StringEdit(ops.map(stringOpFromDynamic)))

      // Temporal deltas - not supported in JSON
      case _: DynamicPatch.PrimitiveOp.InstantDelta =>
        Left(SchemaError("Temporal operations (InstantDelta) are not supported in JsonPatch"))
      case _: DynamicPatch.PrimitiveOp.DurationDelta =>
        Left(SchemaError("Temporal operations (DurationDelta) are not supported in JsonPatch"))
      case _: DynamicPatch.PrimitiveOp.LocalDateDelta =>
        Left(SchemaError("Temporal operations (LocalDateDelta) are not supported in JsonPatch"))
      case _: DynamicPatch.PrimitiveOp.LocalDateTimeDelta =>
        Left(SchemaError("Temporal operations (LocalDateTimeDelta) are not supported in JsonPatch"))
      case _: DynamicPatch.PrimitiveOp.PeriodDelta =>
        Left(SchemaError("Temporal operations (PeriodDelta) are not supported in JsonPatch"))
    }

  private def stringOpFromDynamic(op: DynamicPatch.StringOp): StringOp =
    op match {
      case DynamicPatch.StringOp.Insert(index, text)         => StringOp.Insert(index, text)
      case DynamicPatch.StringOp.Delete(index, length)       => StringOp.Delete(index, length)
      case DynamicPatch.StringOp.Append(text)                => StringOp.Append(text)
      case DynamicPatch.StringOp.Modify(index, length, text) => StringOp.Modify(index, length, text)
    }

  private def seqOpFromDynamic(op: DynamicPatch.SeqOp): Either[SchemaError, ArrayOp] =
    op match {
      case DynamicPatch.SeqOp.Insert(index, values) =>
        Right(ArrayOp.Insert(index, values.map(Json.fromDynamicValue)))

      case DynamicPatch.SeqOp.Append(values) =>
        Right(ArrayOp.Append(values.map(Json.fromDynamicValue)))

      case DynamicPatch.SeqOp.Delete(index, count) =>
        Right(ArrayOp.Delete(index, count))

      case DynamicPatch.SeqOp.Modify(index, nestedOp) =>
        operationFromDynamic(nestedOp).map(op => ArrayOp.Modify(index, op))
    }

  private def mapOpFromDynamic(op: DynamicPatch.MapOp): Either[SchemaError, ObjectOp] =
    op match {
      case DynamicPatch.MapOp.Add(key, value) =>
        extractStringKey(key).map(k => ObjectOp.Add(k, Json.fromDynamicValue(value)))

      case DynamicPatch.MapOp.Remove(key) =>
        extractStringKey(key).map(ObjectOp.Remove(_))

      case DynamicPatch.MapOp.Modify(key, nestedPatch) =>
        for {
          k     <- extractStringKey(key)
          patch <- fromDynamicPatch(nestedPatch)
        } yield ObjectOp.Modify(k, patch)
    }

  private def extractStringKey(key: DynamicValue): Either[SchemaError, String] =
    key match {
      case DynamicValue.Primitive(pv: zio.blocks.schema.PrimitiveValue.String) =>
        Right(pv.value)
      case _ =>
        Left(SchemaError(s"JSON object keys must be strings, got: ${key.getClass.getSimpleName}"))
    }

  private def sequenceAll[A](results: Vector[Either[SchemaError, A]]): Either[SchemaError, Vector[A]] = {
    val builder                    = Vector.newBuilder[A]
    var error: Option[SchemaError] = None
    var idx                        = 0

    while (idx < results.length && error.isEmpty) {
      results(idx) match {
        case Right(value) => builder.addOne(value)
        case Left(err)    => error = Some(err)
      }
      idx += 1
    }

    error.toLeft(builder.result())
  }

  /**
   * A single patch operation paired with the path to apply it at.
   *
   * @param path
   *   The path indicating where to apply the operation
   * @param operation
   *   The operation to apply at the path
   */
  final case class JsonPatchOp(path: DynamicOptic, operation: Op)

  // Operations
  /**
   * Top-level operation type for JSON patches. Each operation describes a
   * change to be applied to a [[Json]] value.
   */
  sealed trait Op

  object Op {

    /**
     * Replaces the target value entirely.
     *
     * @param value
     *   The new JSON value to set
     */
    final case class Set(value: Json) extends Op

    /**
     * Applies a primitive delta operation. Used for numeric increments or
     * string edits.
     *
     * @param op
     *   The primitive operation to apply
     */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Op

    /**
     * Applies array edit operations. Used for inserting, appending, deleting,
     * or modifying array elements.
     *
     * @param ops
     *   The array operations to apply in sequence
     */
    final case class ArrayEdit(ops: Vector[ArrayOp]) extends Op

    /**
     * Applies object edit operations. Used for adding, removing, or modifying
     * object fields.
     *
     * @param ops
     *   The object operations to apply in sequence
     */
    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Op

    /**
     * Groups operations that share a common path prefix.
     *
     * @param patch
     *   The nested patch to apply
     */
    final case class Nested(patch: JsonPatch) extends Op
  }

  // Primitive Operations

  sealed trait PrimitiveOp

  object PrimitiveOp {

    /**
     * Adds a delta to a numeric value.
     *
     * @param delta
     *   The amount to add (can be negative for subtraction)
     */
    final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp

    /**
     * Applies string edit operations to a string value.
     *
     * @param ops
     *   The string operations to apply in sequence
     */
    final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  }

  // String Operations

  sealed trait StringOp

  object StringOp {

    /**
     * Inserts text at the given index.
     *
     * @param index
     *   The position to insert at (0-based)
     * @param text
     *   The text to insert
     */
    final case class Insert(index: Int, text: String) extends StringOp

    /**
     * Deletes characters starting at the given index.
     *
     * @param index
     *   The starting position (0-based)
     * @param length
     *   The number of characters to delete
     */
    final case class Delete(index: Int, length: Int) extends StringOp

    /**
     * Appends text to the end of the string.
     *
     * @param text
     *   The text to append
     */
    final case class Append(text: String) extends StringOp

    /**
     * Replaces a substring with new text.
     *
     * @param index
     *   The starting position (0-based)
     * @param length
     *   The number of characters to replace
     * @param text
     *   The replacement text
     */
    final case class Modify(index: Int, length: Int, text: String) extends StringOp
  }

  // Array Operations

  sealed trait ArrayOp

  object ArrayOp {

    /**
     * Inserts elements at the given index.
     *
     * @param index
     *   The position to insert at (0-based)
     * @param values
     *   The values to insert
     */
    final case class Insert(index: Int, values: Chunk[Json]) extends ArrayOp

    /**
     * Appends elements to the end of the array.
     *
     * @param values
     *   The values to append
     */
    final case class Append(values: Chunk[Json]) extends ArrayOp

    /**
     * Removes elements starting at the given index.
     *
     * @param index
     *   The starting position (0-based)
     * @param count
     *   The number of elements to remove
     */
    final case class Delete(index: Int, count: Int) extends ArrayOp

    /**
     * Applies an operation to the element at the given index.
     *
     * @param index
     *   The index of the element to modify (0-based)
     * @param op
     *   The operation to apply to the element
     */
    final case class Modify(index: Int, op: Op) extends ArrayOp
  }

  // Object Operations

  sealed trait ObjectOp

  object ObjectOp {

    /**
     * Adds a field to the object.
     *
     * @param key
     *   The field name
     * @param value
     *   The field value
     */
    final case class Add(key: String, value: Json) extends ObjectOp

    /**
     * Removes a field from the object.
     *
     * @param key
     *   The field name to remove
     */
    final case class Remove(key: String) extends ObjectOp

    /**
     * Applies a patch to the value at the given field.
     *
     * @param key
     *   The field name
     * @param patch
     *   The patch to apply to the field value
     */
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }
}
