package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.blocks.typeid.TypeId
import java.lang

/**
 * An untyped patch that operates on [[Json]] values. Patches are composable and
 * can be applied with different failure handling modes.
 *
 * @param ops
 *   The sequence of patch operations to apply
 */
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {

  /**
   * Composes two patches. The result applies this patch first, then that patch.
   *
   * @param that
   *   The patch to apply after this one
   * @return
   *   A new patch that applies both patches in sequence
   */
  def ++(that: JsonPatch): JsonPatch = new JsonPatch(ops ++ that.ops)

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
    var current = value
    val len     = ops.length
    var idx     = 0
    while (idx < len) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op.path.nodes, op.operation, mode) match {
        case Right(updated) => current = updated
        case l              => if (mode eq PatchMode.Strict) return l
      }
      idx += 1
    }
    new Right(current)
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
  def toDynamicPatch: DynamicPatch = new DynamicPatch(ops.map { jsonOp =>
    new DynamicPatch.DynamicPatchOp(jsonOp.path, JsonPatch.opToDynamicOperation(jsonOp.operation))
  })

  override def toString: String =
    if (ops.isEmpty) "JsonPatch {}"
    else {
      val sb = new lang.StringBuilder("JsonPatch {\n")
      ops.foreach(op => JsonPatch.renderOp(sb, op, "  "))
      sb.append('}')
      sb.toString
    }
}

object JsonPatch {

  /** Empty patch - identity element for composition. */
  val empty: JsonPatch = new JsonPatch(Chunk.empty)

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
    val builder = ChunkBuilder.make[JsonPatchOp]()
    val ops     = patch.ops
    val len     = ops.length
    var idx     = 0
    while (idx < len) {
      val dynOp = ops(idx)
      operationFromDynamic(dynOp.operation) match {
        case Right(op) => builder.addOne(JsonPatchOp(dynOp.path, op))
        case l         => return l.asInstanceOf[Either[SchemaError, JsonPatch]]
      }
      idx += 1
    }
    new Right(new JsonPatch(builder.result()))
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
  private def applyOp(
    value: Json,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Op,
    mode: PatchMode
  ): Either[SchemaError, Json] =
    if (path.isEmpty) applyOperation(value, operation, mode, Nil)
    else navigateAndApply(value, path, 0, operation, mode, Nil)

  /**
   * Navigate to the target location and apply the operation. Uses a recursive
   * approach that rebuilds the structure on the way back.
   */
  private[this] def navigateAndApply(
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
      case f: DynamicOptic.Node.Field =>
        val name = f.name
        value match {
          case obj: Json.Object =>
            val fields   = obj.value
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              // Field not found
              new Left(SchemaError.missingField(trace, name))
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = f :: trace
              if (isLast) {
                applyOperation(fieldValue, operation, mode, newTrace).map { newFieldValue =>
                  new Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                  new Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }
          case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Object but got ${value.jsonType}"))
        }
      case ai: DynamicOptic.Node.AtIndex =>
        val index = ai.index
        value match {
          case arr: Json.Array =>
            val elements = arr.value
            if (index < 0 || index >= elements.length) {
              new Left(
                SchemaError.expectationMismatch(
                  trace,
                  s"Index $index out of bounds for array of length ${elements.length}"
                )
              )
            } else {
              val element  = elements(index)
              val newTrace = ai :: trace
              if (isLast) {
                applyOperation(element, operation, mode, newTrace).map { newElement =>
                  new Json.Array(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, operation, mode, newTrace).map { newElement =>
                  new Json.Array(elements.updated(index, newElement))
                }
              }
            }
          case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }
      case e: DynamicOptic.Node.Elements.type =>
        value match {
          case arr: Json.Array =>
            val elements = arr.value
            val newTrace = e :: trace
            if (elements.isEmpty) {
              if (mode ne PatchMode.Strict) new Right(value)
              else new Left(SchemaError.expectationMismatch(newTrace, "encountered an empty array"))
            } else if (isLast) applyToAllElements(elements, operation, mode, newTrace)
            else navigateAllElements(elements, path, pathIdx + 1, operation, mode, newTrace)
          case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }
      case _: DynamicOptic.Node.Case =>
        // JSON doesn't have variant types - case navigation is not supported
        new Left(SchemaError.expectationMismatch(trace, "Case navigation not supported for JSON values"))
      case w: DynamicOptic.Node.Wrapped.type =>
        // JSON doesn't have wrapper types - pass through
        val newTrace = w :: trace
        if (isLast) applyOperation(value, operation, mode, newTrace)
        else navigateAndApply(value, path, pathIdx + 1, operation, mode, newTrace)
      case _: DynamicOptic.Node.AtMapKey =>
        new Left(
          SchemaError.expectationMismatch(trace, "AtMapKey not supported for JSON - use Field navigation for objects")
        )
      case _: DynamicOptic.Node.AtIndices =>
        new Left(SchemaError.expectationMismatch(trace, "AtIndices not supported in patches"))
      case _: DynamicOptic.Node.AtMapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "AtMapKeys not supported in patches"))
      case DynamicOptic.Node.MapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "MapKeys not supported in patches"))
      case DynamicOptic.Node.MapValues =>
        new Left(SchemaError.expectationMismatch(trace, "MapValues not supported in patches"))
      case _: DynamicOptic.Node.TypeSearch =>
        new Left(
          SchemaError.expectationMismatch(trace, "TypeSearch requires Schema context, not supported for JSON patches")
        )
      case DynamicOptic.Node.SchemaSearch(pattern) =>
        val newTrace = node :: trace
        if (isLast) {
          schemaSearchApplyOperationJson(value, pattern, operation, mode, newTrace)
        } else {
          schemaSearchNavigateJson(value, pattern, path, pathIdx + 1, operation, mode, newTrace)
        }
    }
  }

  /**
   * Apply operation to all elements in an array.
   */
  private[this] def applyToAllElements(
    elements: Chunk[Json],
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    val len     = elements.length
    val results = new Array[Json](len)
    var idx     = 0
    while (idx < len) {
      applyOperation(elements(idx), operation, mode, new DynamicOptic.Node.AtIndex(idx) :: trace) match {
        case Right(updated) => results(idx) = updated
        case l              =>
          if (mode eq PatchMode.Strict) return l
          results(idx) = elements(idx)
      }
      idx += 1
    }
    new Right(new Json.Array(Chunk.fromArray(results)))
  }

  /**
   * Navigate deeper into all elements of an array.
   */
  private[this] def navigateAllElements(
    elements: Chunk[Json],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    val len     = elements.length
    val results = new Array[Json](len)
    var idx     = 0
    while (idx < len) {
      val elementTrace = new DynamicOptic.Node.AtIndex(idx) :: trace
      navigateAndApply(elements(idx), path, pathIdx, operation, mode, elementTrace) match {
        case Right(updated) => results(idx) = updated
        case l              =>
          if (mode eq PatchMode.Strict) return l
          results(idx) = elements(idx)
      }
      idx += 1
    }
    new Right(new Json.Array(Chunk.fromArray(results)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SchemaSearch Helper Functions
  // ─────────────────────────────────────────────────────────────────────────

  private def schemaSearchApplyOperationJson(
    value: Json,
    pattern: SchemaRepr,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var found       = false
    var globalError = Option.empty[SchemaError]

    val result = Json.iterativeTransform(value) { json =>
      if (globalError.isDefined && mode == PatchMode.Strict) json
      else if (JsonMatch.matches(pattern, json)) {
        applyOperation(json, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            mode match {
              case PatchMode.Strict =>
                if (globalError.isEmpty) globalError = Some(err)
                json
              case PatchMode.Lenient | PatchMode.Clobber =>
                json
            }
        }
      } else {
        json
      }
    }

    globalError match {
      case Some(err) => Left(err)
      case None      =>
        if (!found && mode == PatchMode.Strict) {
          Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
        } else {
          Right(result)
        }
    }
  }

  private def schemaSearchNavigateJson(
    value: Json,
    pattern: SchemaRepr,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var found       = false
    var globalError = Option.empty[SchemaError]

    val result = Json.iterativeTransform(value) { json =>
      if (globalError.isDefined && mode == PatchMode.Strict) json
      else if (JsonMatch.matches(pattern, json)) {
        navigateAndApply(json, path, pathIdx, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            mode match {
              case PatchMode.Strict =>
                if (globalError.isEmpty) globalError = Some(err)
                json
              case PatchMode.Lenient | PatchMode.Clobber =>
                json
            }
        }
      } else {
        json
      }
    }

    globalError match {
      case Some(err) => Left(err)
      case None      =>
        if (!found && mode == PatchMode.Strict) {
          Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
        } else {
          Right(result)
        }
    }
  }

  /**
   * Apply an operation to a value (at the current location).
   */
  private[this] def applyOperation(
    value: Json,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = operation match {
    case Op.Set(newValue)         => new Right(newValue)
    case Op.PrimitiveDelta(op)    => applyPrimitiveDelta(value, op, trace)
    case Op.ArrayEdit(arrayOps)   => applyArrayEdit(value, arrayOps, mode, trace)
    case Op.ObjectEdit(objectOps) => applyObjectEdit(value, objectOps, mode, trace)
    case Op.Nested(nestedPatch)   => nestedPatch.apply(value, mode)
  }

  /**
   * Apply a primitive delta operation (number delta or string edits).
   */
  private[this] def applyPrimitiveDelta(
    value: Json,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = op match {
    case PrimitiveOp.NumberDelta(delta) =>
      value match {
        case Json.Number(current) => new Right(new Json.Number(current + delta))
        case _                    => new Left(SchemaError.expectationMismatch(trace, s"Expected Number but got ${value.jsonType}"))
      }
    case PrimitiveOp.StringEdit(ops) =>
      value match {
        case Json.String(str) => applyStringOps(str, ops, trace)
        case _                => new Left(SchemaError.expectationMismatch(trace, s"Expected String but got ${value.jsonType}"))
      }
  }

  /**
   * Apply string edit operations to a string value.
   */
  private[this] def applyStringOps(
    str: String,
    ops: Chunk[StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var result = str
    var idx    = 0
    while (idx < ops.length) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            return new Left(
              SchemaError.expectationMismatch(
                trace,
                s"String insert index $index out of bounds for string of length ${result.length}"
              )
            )
          } else result = result.substring(0, index) + text + result.substring(index)
        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            return new Left(
              SchemaError.expectationMismatch(
                trace,
                s"String delete range [$index, ${index + length}) out of bounds for string of length ${result.length}"
              )
            )
          } else result = result.substring(0, index) + result.substring(index + length)
        case StringOp.Append(text)                => result = result + text
        case StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length) {
            return new Left(
              SchemaError.expectationMismatch(
                trace,
                s"String modify range [$index, ${index + length}) out of bounds for string of length ${result.length}"
              )
            )
          } else result = result.substring(0, index) + text + result.substring(index + length)
      }
      idx += 1
    }
    new Right(new Json.String(result))
  }

  /**
   * Apply array edit operations.
   */
  private[this] def applyArrayEdit(
    value: Json,
    ops: Chunk[ArrayOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = value match {
    case arr: Json.Array => applyArrayOps(arr.value, ops, mode, trace)
    case _               => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
  }

  /**
   * Apply array operations to elements.
   */
  private[this] def applyArrayOps(
    elements: Chunk[Json],
    ops: Chunk[ArrayOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    val len    = ops.length
    var result = elements
    var idx    = 0
    while (idx < len) {
      applyArrayOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case l              => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, Json]]
      }
      idx += 1
    }
    new Right(new Json.Array(result))
  }

  /**
   * Apply a single array operation.
   */
  private[this] def applyArrayOp(
    elements: Chunk[Json],
    op: ArrayOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] =
    op match {
      case ArrayOp.Append(values)        => new Right(elements ++ values)
      case ArrayOp.Insert(index, values) =>
        val len = elements.length
        if (index < 0 || index > len) {
          mode match {
            case PatchMode.Clobber =>
              // In clobber mode, clamp the index
              val clampedIndex    = Math.max(0, Math.min(index, len))
              val (before, after) = elements.splitAt(clampedIndex)
              new Right(before ++ values ++ after)
            case _ =>
              val msg = s"Insert index $index out of bounds for array of length $len"
              new Left(SchemaError.expectationMismatch(trace, msg))
          }
        } else {
          val (before, after) = elements.splitAt(index)
          new Right(before ++ values ++ after)
        }
      case ArrayOp.Delete(index, count) =>
        val limit = index + count
        val len   = elements.length
        if (index < 0 || limit > len) {
          mode match {
            case PatchMode.Clobber =>
              // In clobber mode, delete what we can
              val clampedIndex = Math.max(0, Math.min(index, len))
              val clampedEnd   = Math.max(0, Math.min(limit, len))
              new Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
            case _ =>
              val msg = s"Delete range [$index, $limit) out of bounds for array of length $len"
              new Left(SchemaError.expectationMismatch(trace, msg))
          }
        } else new Right(elements.take(index) ++ elements.drop(limit))
      case ArrayOp.Modify(index, nestedOp) =>
        val len = elements.length
        if (index < 0 || index >= len) {
          val msg = s"Modify index $index out of bounds for array of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        } else {
          val element  = elements(index)
          val newTrace = new DynamicOptic.Node.AtIndex(index) :: trace
          applyOperation(element, nestedOp, mode, newTrace).map(elements.updated(index, _))
        }
    }

  /**
   * Apply object edit operations.
   */
  private[this] def applyObjectEdit(
    value: Json,
    ops: Chunk[ObjectOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = value match {
    case obj: Json.Object => applyObjectOps(obj.value, ops, mode, trace)
    case _                => new Left(SchemaError.expectationMismatch(trace, s"Expected Object but got ${value.jsonType}"))
  }

  /**
   * Apply object operations to fields.
   */
  private[this] def applyObjectOps(
    fields: Chunk[(String, Json)],
    ops: Chunk[ObjectOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var result = fields
    val len    = ops.length
    var idx    = 0
    while (idx < len) {
      applyObjectOp(result, ops(idx), mode, trace) match {
        case Right(updated) => result = updated
        case l              => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, Json]]
      }
      idx += 1
    }
    new Right(new Json.Object(result))
  }

  /**
   * Apply a single object operation.
   */
  private[this] def applyObjectOp(
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
            case PatchMode.Clobber => new Right(fields.updated(existingIdx, (key, value))) // Overwrite existing
            case _                 => new Left(SchemaError.expectationMismatch(trace, s"Key '$key' already exists in object"))
          }
        } else new Right(fields :+ (key, value))
      case ObjectOp.Remove(key) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Clobber => new Right(fields) // Nothing to remove, return unchanged
            case _                 => new Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
          }
        } else new Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) new Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
        else {
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
  def root(operation: Op): JsonPatch = new JsonPatch(Chunk.single(new JsonPatchOp(DynamicOptic.root, operation)))

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
    new JsonPatch(Chunk.single(new JsonPatchOp(path, operation)))

  private def renderOp(sb: lang.StringBuilder, op: JsonPatchOp, indent: String): Unit = {
    val pathStr = renderPath(op.path.nodes)
    op.operation match {
      case Op.Set(value) =>
        sb.append(indent).append(pathStr).append(" = ").append(value).append('\n')
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

  private[this] def renderPath(nodes: IndexedSeq[DynamicOptic.Node]): String = {
    if (nodes.isEmpty) return "root"
    val sb = new lang.StringBuilder
    nodes.foreach {
      case DynamicOptic.Node.Field(name)    => sb.append('.').append(name)
      case DynamicOptic.Node.AtIndex(index) => sb.append('[').append(index).append(']')
      case other                            => sb.append(other.toString)
    }
    sb.toString
  }

  private[this] def renderPrimitiveDelta(
    sb: lang.StringBuilder,
    pathStr: String,
    op: PrimitiveOp,
    indent: String
  ): Unit = op match {
    case PrimitiveOp.NumberDelta(d) =>
      sb.append(indent)
      if (d >= BigDecimal(0)) sb.append(pathStr).append(" += ").append(d).append('\n')
      else sb.append(pathStr).append(" -= ").append(-d).append('\n')
    case PrimitiveOp.StringEdit(ops) =>
      sb.append(indent).append(pathStr).append(":\n")
      ops.foreach {
        case StringOp.Insert(idx, text) =>
          sb.append(indent).append("  + [").append(idx).append(": ").append(escapeString(text)).append("]\n")
        case StringOp.Delete(idx, len) =>
          sb.append(indent).append("  - [").append(idx).append(", ").append(len).append("]\n")
        case StringOp.Append(text) =>
          sb.append(indent).append("  + ").append(escapeString(text)).append('\n')
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

  private[this] def renderArrayOp(sb: lang.StringBuilder, op: ArrayOp, indent: String): Unit = op match {
    case ArrayOp.Insert(index, values) =>
      values.foreach {
        var idx = -1
        v =>
          idx += 1
          sb.append(indent).append("+ [").append(index + idx).append(": ").append(v).append("]\n")
      }
    case ArrayOp.Append(values) =>
      values.foreach(v => sb.append(indent).append("+ ").append(v).append('\n'))
    case ArrayOp.Delete(index, count) =>
      sb.append(indent)
      if (count == 1) sb.append("- [").append(index).append("]\n")
      else {
        sb.append("- [")
        val limit = index + count
        var idx   = index
        while (idx < limit) {
          if (idx > index) sb.append(", ")
          sb.append(idx)
          idx += 1
        }
        sb.append("]\n")
      }
    case ArrayOp.Modify(index, nestedOp) =>
      sb.append(indent)
      nestedOp match {
        case Op.Set(v) => sb.append("~ [").append(index).append(": ").append(v).append("]\n")
        case _         =>
          sb.append("~ [").append(index).append("]:\n")
          renderOp(sb, JsonPatchOp(DynamicOptic.root, nestedOp), indent + "  ")
      }
  }

  private[this] def renderObjectOp(sb: lang.StringBuilder, op: ObjectOp, indent: String): Unit = {
    sb.append(indent)
    op match {
      case ObjectOp.Add(k, v)        => sb.append("+ {").append(escapeString(k)).append(": ").append(v).append("}\n")
      case ObjectOp.Remove(k)        => sb.append("- {").append(escapeString(k)).append("}\n")
      case ObjectOp.Modify(k, patch) =>
        sb.append("~ {").append(escapeString(k)).append("}:\n")
        patch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }
  }

  private[this] def escapeString(s: String): String = JsonBinaryCodec.stringCodec.encodeToString(s)

  // DynamicPatch Conversion Helpers

  /**
   * Converts a JsonPatch.Op to a DynamicPatch.Operation.
   */
  private def opToDynamicOperation(op: Op): DynamicPatch.Operation = op match {
    case Op.Set(value)                  => new DynamicPatch.Operation.Set(value.toDynamicValue)
    case Op.PrimitiveDelta(primitiveOp) => new DynamicPatch.Operation.PrimitiveDelta(primitiveOpToDynamic(primitiveOp))
    case Op.ArrayEdit(arrayOps)         => new DynamicPatch.Operation.SequenceEdit(arrayOps.map(arrayOpToDynamic))
    case Op.ObjectEdit(objectOps)       => new DynamicPatch.Operation.MapEdit(objectOps.map(objectOpToDynamic))
    case Op.Nested(nestedPatch)         => new DynamicPatch.Operation.Patch(nestedPatch.toDynamicPatch)
  }

  private[this] def primitiveOpToDynamic(op: PrimitiveOp): DynamicPatch.PrimitiveOp = op match {
    case PrimitiveOp.NumberDelta(delta) => new DynamicPatch.PrimitiveOp.BigDecimalDelta(delta)
    case PrimitiveOp.StringEdit(ops)    => new DynamicPatch.PrimitiveOp.StringEdit(ops.map(stringOpToDynamic))
  }

  private[this] def stringOpToDynamic(op: StringOp): DynamicPatch.StringOp = op match {
    case StringOp.Insert(index, text)         => new DynamicPatch.StringOp.Insert(index, text)
    case StringOp.Delete(index, length)       => new DynamicPatch.StringOp.Delete(index, length)
    case StringOp.Append(text)                => new DynamicPatch.StringOp.Append(text)
    case StringOp.Modify(index, length, text) => new DynamicPatch.StringOp.Modify(index, length, text)
  }

  private[this] def arrayOpToDynamic(op: ArrayOp): DynamicPatch.SeqOp = op match {
    case ArrayOp.Insert(index, values)   => new DynamicPatch.SeqOp.Insert(index, values.map(_.toDynamicValue))
    case ArrayOp.Append(values)          => new DynamicPatch.SeqOp.Append(values.map(_.toDynamicValue))
    case ArrayOp.Delete(index, count)    => new DynamicPatch.SeqOp.Delete(index, count)
    case ArrayOp.Modify(index, nestedOp) => new DynamicPatch.SeqOp.Modify(index, opToDynamicOperation(nestedOp))
  }

  private[this] def objectOpToDynamic(op: ObjectOp): DynamicPatch.MapOp = op match {
    case ObjectOp.Add(key, value)          => new DynamicPatch.MapOp.Add(DynamicValue.string(key), value.toDynamicValue)
    case ObjectOp.Remove(key)              => new DynamicPatch.MapOp.Remove(DynamicValue.string(key))
    case ObjectOp.Modify(key, nestedPatch) =>
      new DynamicPatch.MapOp.Modify(DynamicValue.string(key), nestedPatch.toDynamicPatch)
  }

  /**
   * Converts a DynamicPatch.Operation to a JsonPatch.Op.
   */
  private[this] def operationFromDynamic(op: DynamicPatch.Operation): Either[SchemaError, Op] = op match {
    case DynamicPatch.Operation.Set(value)                  => new Right(new Op.Set(Json.fromDynamicValue(value)))
    case DynamicPatch.Operation.PrimitiveDelta(primitiveOp) =>
      primitiveOpFromDynamic(primitiveOp).map(new Op.PrimitiveDelta(_))
    case DynamicPatch.Operation.SequenceEdit(seqOps) =>
      sequenceAll(seqOps.map(seqOpFromDynamic)).map(ops => new Op.ArrayEdit(ops))
    case DynamicPatch.Operation.MapEdit(mapOps) =>
      sequenceAll(mapOps.map(mapOpFromDynamic)).map(ops => new Op.ObjectEdit(ops))
    case DynamicPatch.Operation.Patch(nestedPatch) => fromDynamicPatch(nestedPatch).map(new Op.Nested(_))
  }

  private[this] def primitiveOpFromDynamic(op: DynamicPatch.PrimitiveOp): Either[SchemaError, PrimitiveOp] = op match {
    case DynamicPatch.PrimitiveOp.IntDelta(delta)   => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta)))
    case DynamicPatch.PrimitiveOp.LongDelta(delta)  => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta)))
    case DynamicPatch.PrimitiveOp.ShortDelta(delta) => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
    case DynamicPatch.PrimitiveOp.ByteDelta(delta)  => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
    case DynamicPatch.PrimitiveOp.FloatDelta(delta) =>
      new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble)))
    case DynamicPatch.PrimitiveOp.DoubleDelta(delta)     => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta)))
    case DynamicPatch.PrimitiveOp.BigIntDelta(delta)     => new Right(new PrimitiveOp.NumberDelta(BigDecimal(delta)))
    case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) => new Right(new PrimitiveOp.NumberDelta(delta))
    case DynamicPatch.PrimitiveOp.StringEdit(ops)        => new Right(new PrimitiveOp.StringEdit(ops.map(stringOpFromDynamic)))
    case _: DynamicPatch.PrimitiveOp.InstantDelta        =>
      new Left(SchemaError("Temporal operations (InstantDelta) are not supported in JsonPatch"))
    case _: DynamicPatch.PrimitiveOp.DurationDelta =>
      new Left(SchemaError("Temporal operations (DurationDelta) are not supported in JsonPatch"))
    case _: DynamicPatch.PrimitiveOp.LocalDateDelta =>
      new Left(SchemaError("Temporal operations (LocalDateDelta) are not supported in JsonPatch"))
    case _: DynamicPatch.PrimitiveOp.LocalDateTimeDelta =>
      new Left(SchemaError("Temporal operations (LocalDateTimeDelta) are not supported in JsonPatch"))
    case _: DynamicPatch.PrimitiveOp.PeriodDelta =>
      new Left(SchemaError("Temporal operations (PeriodDelta) are not supported in JsonPatch"))
  }

  private[this] def stringOpFromDynamic(op: DynamicPatch.StringOp): StringOp = op match {
    case DynamicPatch.StringOp.Insert(index, text)         => new StringOp.Insert(index, text)
    case DynamicPatch.StringOp.Delete(index, length)       => new StringOp.Delete(index, length)
    case DynamicPatch.StringOp.Append(text)                => new StringOp.Append(text)
    case DynamicPatch.StringOp.Modify(index, length, text) => new StringOp.Modify(index, length, text)
  }

  private[this] def seqOpFromDynamic(op: DynamicPatch.SeqOp): Either[SchemaError, ArrayOp] = op match {
    case DynamicPatch.SeqOp.Insert(index, values) =>
      new Right(new ArrayOp.Insert(index, values.map(Json.fromDynamicValue)))
    case DynamicPatch.SeqOp.Append(values)          => new Right(new ArrayOp.Append(values.map(Json.fromDynamicValue)))
    case DynamicPatch.SeqOp.Delete(index, count)    => new Right(new ArrayOp.Delete(index, count))
    case DynamicPatch.SeqOp.Modify(index, nestedOp) =>
      operationFromDynamic(nestedOp).map(op => new ArrayOp.Modify(index, op))
  }

  private[this] def mapOpFromDynamic(op: DynamicPatch.MapOp): Either[SchemaError, ObjectOp] = op match {
    case DynamicPatch.MapOp.Add(key, value) =>
      extractStringKey(key).map(k => new ObjectOp.Add(k, Json.fromDynamicValue(value)))
    case DynamicPatch.MapOp.Remove(key)              => extractStringKey(key).map(new ObjectOp.Remove(_))
    case DynamicPatch.MapOp.Modify(key, nestedPatch) =>
      for {
        k     <- extractStringKey(key)
        patch <- fromDynamicPatch(nestedPatch)
      } yield new ObjectOp.Modify(k, patch)
  }

  private[this] def extractStringKey(key: DynamicValue): Either[SchemaError, String] = key match {
    case DynamicValue.Primitive(pv: zio.blocks.schema.PrimitiveValue.String) => new Right(pv.value)
    case _                                                                   => new Left(SchemaError(s"JSON object keys must be strings, got: ${key.getClass.getSimpleName}"))
  }

  private[this] def sequenceAll[A](results: Chunk[Either[SchemaError, A]]): Either[SchemaError, Chunk[A]] = {
    val builder = ChunkBuilder.make[A]()
    val len     = results.length
    var idx     = 0
    while (idx < len) {
      results(idx) match {
        case Right(value) => builder.addOne(value)
        case l            => return l.asInstanceOf[Either[SchemaError, Chunk[A]]]
      }
      idx += 1
    }
    new Right(builder.result())
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
    final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op

    /**
     * Applies object edit operations. Used for adding, removing, or modifying
     * object fields.
     *
     * @param ops
     *   The object operations to apply in sequence
     */
    final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op

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
    final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
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

  // Schema Definitions, Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  // StringOp Schemas

  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Insert](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Insert =
              StringOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.text)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("length")),
        typeId = TypeId.of[StringOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Delete =
              StringOp.Delete(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset))
              )
          },
          deconstructor = new Deconstructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Append](
        fields = Chunk.single(Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Append =
              StringOp.Append(in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Append): Unit =
              out.setObject(offset, in.text)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Modify](
        fields = Chunk(
          Schema[Int].reflect.asTerm("index"),
          Schema[Int].reflect.asTerm("length"),
          Schema[String].reflect.asTerm("text")
        ),
        typeId = TypeId.of[StringOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Modify] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Modify =
              StringOp.Modify(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset)),
                in.getObject(offset).asInstanceOf[String]
              )
          },
          deconstructor = new Deconstructor[StringOp.Modify] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
              out.setObject(offset, in.text)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringOpSchema: Schema[StringOp] = new Schema(
    reflect = new Reflect.Variant[Binding, StringOp](
      cases = Chunk(
        stringOpInsertSchema.reflect.asTerm("Insert"),
        stringOpDeleteSchema.reflect.asTerm("Delete"),
        stringOpAppendSchema.reflect.asTerm("Append"),
        stringOpModifySchema.reflect.asTerm("Modify")
      ),
      typeId = TypeId.of[StringOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[StringOp] {
          def discriminate(a: StringOp): Int = a match {
            case _: StringOp.Insert => 0
            case _: StringOp.Delete => 1
            case _: StringOp.Append => 2
            case _: StringOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[StringOp.Insert] {
            def downcastOrNull(a: Any): StringOp.Insert = a match {
              case x: StringOp.Insert => x
              case _                  => null.asInstanceOf[StringOp.Insert]
            }
          },
          new Matcher[StringOp.Delete] {
            def downcastOrNull(a: Any): StringOp.Delete = a match {
              case x: StringOp.Delete => x
              case _                  => null.asInstanceOf[StringOp.Delete]
            }
          },
          new Matcher[StringOp.Append] {
            def downcastOrNull(a: Any): StringOp.Append = a match {
              case x: StringOp.Append => x
              case _                  => null.asInstanceOf[StringOp.Append]
            }
          },
          new Matcher[StringOp.Modify] {
            def downcastOrNull(a: Any): StringOp.Modify = a match {
              case x: StringOp.Modify => x
              case _                  => null.asInstanceOf[StringOp.Modify]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // PrimitiveOp Schemas

  implicit lazy val primitiveOpNumberDeltaSchema: Schema[PrimitiveOp.NumberDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.NumberDelta](
        fields = Chunk.single(Schema[BigDecimal].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.NumberDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.NumberDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.NumberDelta =
              PrimitiveOp.NumberDelta(in.getObject(offset).asInstanceOf[BigDecimal])
          },
          deconstructor = new Deconstructor[PrimitiveOp.NumberDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.NumberDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.StringEdit](
        fields = Chunk.single(Schema[Chunk[StringOp]].reflect.asTerm("ops")),
        typeId = TypeId.of[PrimitiveOp.StringEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.StringEdit =
              PrimitiveOp.StringEdit(in.getObject(offset).asInstanceOf[Chunk[StringOp]])
          },
          deconstructor = new Deconstructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.StringEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveOp](
      cases = Chunk(
        primitiveOpNumberDeltaSchema.reflect.asTerm("NumberDelta"),
        primitiveOpStringEditSchema.reflect.asTerm("StringEdit")
      ),
      typeId = TypeId.of[PrimitiveOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveOp] {
          def discriminate(a: PrimitiveOp): Int = a match {
            case _: PrimitiveOp.NumberDelta => 0
            case _: PrimitiveOp.StringEdit  => 1
          }
        },
        matchers = Matchers(
          new Matcher[PrimitiveOp.NumberDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.NumberDelta = a match {
              case x: PrimitiveOp.NumberDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.NumberDelta]
            }
          },
          new Matcher[PrimitiveOp.StringEdit] {
            def downcastOrNull(a: Any): PrimitiveOp.StringEdit = a match {
              case x: PrimitiveOp.StringEdit => x
              case _                         => null.asInstanceOf[PrimitiveOp.StringEdit]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ArrayOp Schemas

  implicit lazy val arrayOpInsertSchema: Schema[ArrayOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Insert](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Chunk[Json]].reflect.asTerm("values")),
        typeId = TypeId.of[ArrayOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Insert] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Insert =
              ArrayOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[Json]])
          },
          deconstructor = new Deconstructor[ArrayOp.Insert] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.values)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val arrayOpAppendSchema: Schema[ArrayOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Append](
        fields = Chunk.single(Schema[Chunk[Json]].reflect.asTerm("values")),
        typeId = TypeId.of[ArrayOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Append] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Append =
              ArrayOp.Append(in.getObject(offset).asInstanceOf[Chunk[Json]])
          },
          deconstructor = new Deconstructor[ArrayOp.Append] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Append): Unit =
              out.setObject(offset, in.values)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val arrayOpDeleteSchema: Schema[ArrayOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("count")),
        typeId = TypeId.of[ArrayOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Delete] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Delete =
              ArrayOp.Delete(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset))
              )
          },
          deconstructor = new Deconstructor[ArrayOp.Delete] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.count)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  // ArrayOp.Modify uses Reflect.Deferred for the Op field due to recursion
  implicit lazy val arrayOpModifySchema: Schema[ArrayOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Modify](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Reflect.Deferred(() => opSchema.reflect).asTerm("op")),
        typeId = TypeId.of[ArrayOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Modify] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Modify =
              ArrayOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Op])
          },
          deconstructor = new Deconstructor[ArrayOp.Modify] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.op)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val arrayOpSchema: Schema[ArrayOp] = new Schema(
    reflect = new Reflect.Variant[Binding, ArrayOp](
      cases = Chunk(
        arrayOpInsertSchema.reflect.asTerm("Insert"),
        arrayOpAppendSchema.reflect.asTerm("Append"),
        arrayOpDeleteSchema.reflect.asTerm("Delete"),
        Reflect.Deferred(() => arrayOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[ArrayOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[ArrayOp] {
          def discriminate(a: ArrayOp): Int = a match {
            case _: ArrayOp.Insert => 0
            case _: ArrayOp.Append => 1
            case _: ArrayOp.Delete => 2
            case _: ArrayOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[ArrayOp.Insert] {
            def downcastOrNull(a: Any): ArrayOp.Insert = a match {
              case x: ArrayOp.Insert => x
              case _                 => null.asInstanceOf[ArrayOp.Insert]
            }
          },
          new Matcher[ArrayOp.Append] {
            def downcastOrNull(a: Any): ArrayOp.Append = a match {
              case x: ArrayOp.Append => x
              case _                 => null.asInstanceOf[ArrayOp.Append]
            }
          },
          new Matcher[ArrayOp.Delete] {
            def downcastOrNull(a: Any): ArrayOp.Delete = a match {
              case x: ArrayOp.Delete => x
              case _                 => null.asInstanceOf[ArrayOp.Delete]
            }
          },
          new Matcher[ArrayOp.Modify] {
            def downcastOrNull(a: Any): ArrayOp.Modify = a match {
              case x: ArrayOp.Modify => x
              case _                 => null.asInstanceOf[ArrayOp.Modify]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ObjectOp Schemas

  implicit lazy val objectOpAddSchema: Schema[ObjectOp.Add] =
    new Schema(
      reflect = new Reflect.Record[Binding, ObjectOp.Add](
        fields = Chunk(Schema[String].reflect.asTerm("key"), Schema[Json].reflect.asTerm("value")),
        typeId = TypeId.of[ObjectOp.Add],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ObjectOp.Add] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): ObjectOp.Add =
              ObjectOp.Add(
                in.getObject(offset).asInstanceOf[String],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Json]
              )
          },
          deconstructor = new Deconstructor[ObjectOp.Add] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Add): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.value)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val objectOpRemoveSchema: Schema[ObjectOp.Remove] =
    new Schema(
      reflect = new Reflect.Record[Binding, ObjectOp.Remove](
        fields = Chunk.single(Schema[String].reflect.asTerm("key")),
        typeId = TypeId.of[ObjectOp.Remove],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ObjectOp.Remove] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ObjectOp.Remove =
              ObjectOp.Remove(in.getObject(offset).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[ObjectOp.Remove] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Remove): Unit =
              out.setObject(offset, in.key)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  // ObjectOp.Modify uses Reflect.Deferred for the JsonPatch field due to recursion
  implicit lazy val objectOpModifySchema: Schema[ObjectOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, ObjectOp.Modify](
        fields = Chunk(Schema[String].reflect.asTerm("key"), Reflect.Deferred(() => schema.reflect).asTerm("patch")),
        typeId = TypeId.of[ObjectOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ObjectOp.Modify] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): ObjectOp.Modify =
              ObjectOp.Modify(
                in.getObject(offset).asInstanceOf[String],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[JsonPatch]
              )
          },
          deconstructor = new Deconstructor[ObjectOp.Modify] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Modify): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.patch)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val objectOpSchema: Schema[ObjectOp] = new Schema(
    reflect = new Reflect.Variant[Binding, ObjectOp](
      cases = Chunk(
        objectOpAddSchema.reflect.asTerm("Add"),
        objectOpRemoveSchema.reflect.asTerm("Remove"),
        Reflect.Deferred(() => objectOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[ObjectOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[ObjectOp] {
          def discriminate(a: ObjectOp): Int = a match {
            case _: ObjectOp.Add    => 0
            case _: ObjectOp.Remove => 1
            case _: ObjectOp.Modify => 2
          }
        },
        matchers = Matchers(
          new Matcher[ObjectOp.Add] {
            def downcastOrNull(a: Any): ObjectOp.Add = a match {
              case x: ObjectOp.Add => x
              case _               => null.asInstanceOf[ObjectOp.Add]
            }
          },
          new Matcher[ObjectOp.Remove] {
            def downcastOrNull(a: Any): ObjectOp.Remove = a match {
              case x: ObjectOp.Remove => x
              case _                  => null.asInstanceOf[ObjectOp.Remove]
            }
          },
          new Matcher[ObjectOp.Modify] {
            def downcastOrNull(a: Any): ObjectOp.Modify = a match {
              case x: ObjectOp.Modify => x
              case _                  => null.asInstanceOf[ObjectOp.Modify]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // Op Schemas

  implicit lazy val opSetSchema: Schema[Op.Set] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.Set](
        fields = Chunk.single(Schema[Json].reflect.asTerm("value")),
        typeId = TypeId.of[Op.Set],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.Set] {
            def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.Set =
              Op.Set(in.getObject(offset).asInstanceOf[Json])
          },
          deconstructor = new Deconstructor[Op.Set] {
            def usedRegisters: RegisterOffset                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Op.Set): Unit =
              out.setObject(offset, in.value)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val opPrimitiveDeltaSchema: Schema[Op.PrimitiveDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.PrimitiveDelta](
        fields = Chunk.single(primitiveOpSchema.reflect.asTerm("op")),
        typeId = TypeId.of[Op.PrimitiveDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.PrimitiveDelta =
              Op.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
          },
          deconstructor = new Deconstructor[Op.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Op.PrimitiveDelta): Unit =
              out.setObject(offset, in.op)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val opArrayEditSchema: Schema[Op.ArrayEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.ArrayEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[ArrayOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Op.ArrayEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.ArrayEdit] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.ArrayEdit =
              Op.ArrayEdit(in.getObject(offset).asInstanceOf[Chunk[ArrayOp]])
          },
          deconstructor = new Deconstructor[Op.ArrayEdit] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Op.ArrayEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val opObjectEditSchema: Schema[Op.ObjectEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.ObjectEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[ObjectOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Op.ObjectEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.ObjectEdit] {
            def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.ObjectEdit =
              Op.ObjectEdit(in.getObject(offset).asInstanceOf[Chunk[ObjectOp]])
          },
          deconstructor = new Deconstructor[Op.ObjectEdit] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Op.ObjectEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  // Op.Nested uses Reflect.Deferred for the JsonPatch field due to recursion
  implicit lazy val opNestedSchema: Schema[Op.Nested] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.Nested](
        fields = Chunk.single(Reflect.Deferred(() => schema.reflect).asTerm("patch")),
        typeId = TypeId.of[Op.Nested],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.Nested] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.Nested =
              Op.Nested(in.getObject(offset).asInstanceOf[JsonPatch])
          },
          deconstructor = new Deconstructor[Op.Nested] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Op.Nested): Unit =
              out.setObject(offset, in.patch)
          }
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val opSchema: Schema[Op] = new Schema(
    reflect = new Reflect.Variant[Binding, Op](
      cases = Chunk(
        opSetSchema.reflect.asTerm("Set"),
        opPrimitiveDeltaSchema.reflect.asTerm("PrimitiveDelta"),
        opArrayEditSchema.reflect.asTerm("ArrayEdit"),
        opObjectEditSchema.reflect.asTerm("ObjectEdit"),
        Reflect.Deferred(() => opNestedSchema.reflect).asTerm("Nested")
      ),
      typeId = TypeId.of[Op],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Op] {
          def discriminate(a: Op): Int = a match {
            case _: Op.Set            => 0
            case _: Op.PrimitiveDelta => 1
            case _: Op.ArrayEdit      => 2
            case _: Op.ObjectEdit     => 3
            case _: Op.Nested         => 4
          }
        },
        matchers = Matchers(
          new Matcher[Op.Set] {
            def downcastOrNull(a: Any): Op.Set = a match {
              case x: Op.Set => x
              case _         => null.asInstanceOf[Op.Set]
            }
          },
          new Matcher[Op.PrimitiveDelta] {
            def downcastOrNull(a: Any): Op.PrimitiveDelta = a match {
              case x: Op.PrimitiveDelta => x
              case _                    => null.asInstanceOf[Op.PrimitiveDelta]
            }
          },
          new Matcher[Op.ArrayEdit] {
            def downcastOrNull(a: Any): Op.ArrayEdit = a match {
              case x: Op.ArrayEdit => x
              case _               => null.asInstanceOf[Op.ArrayEdit]
            }
          },
          new Matcher[Op.ObjectEdit] {
            def downcastOrNull(a: Any): Op.ObjectEdit = a match {
              case x: Op.ObjectEdit => x
              case _                => null.asInstanceOf[Op.ObjectEdit]
            }
          },
          new Matcher[Op.Nested] {
            def downcastOrNull(a: Any): Op.Nested = a match {
              case x: Op.Nested => x
              case _            => null.asInstanceOf[Op.Nested]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // JsonPatchOp Schema

  implicit lazy val jsonPatchOpSchema: Schema[JsonPatchOp] =
    new Schema(
      reflect = new Reflect.Record[Binding, JsonPatchOp](
        fields = Chunk(
          Schema[DynamicOptic].reflect.asTerm("path"),
          Reflect.Deferred(() => opSchema.reflect).asTerm("operation")
        ),
        typeId = TypeId.of[JsonPatchOp],
        recordBinding = new Binding.Record(
          constructor = new Constructor[JsonPatchOp] {
            def usedRegisters: RegisterOffset                                 = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): JsonPatchOp =
              JsonPatchOp(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Op]
              )
          },
          deconstructor = new Deconstructor[JsonPatchOp] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: JsonPatchOp): Unit = {
              out.setObject(offset, in.path)
              out.setObject(RegisterOffset.incrementObjects(offset), in.operation)
            }
          }
        ),
        modifiers = Chunk.empty
      )
    )

  // JsonPatch Schema

  implicit lazy val schema: Schema[JsonPatch] =
    new Schema(
      reflect = new Reflect.Record[Binding, JsonPatch](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[JsonPatchOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[JsonPatch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[JsonPatch] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): JsonPatch =
              JsonPatch(in.getObject(offset).asInstanceOf[Chunk[JsonPatchOp]])
          },
          deconstructor = new Deconstructor[JsonPatch] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: JsonPatch): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Chunk.empty
      )
    )
}
