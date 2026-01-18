package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.patch.DynamicPatch

/**
 * An untyped patch that operates on [[Json]] values.
 *
 * `JsonPatch` is the JSON-specific counterpart to [[DynamicPatch]]. It
 * represents a sequence of operations that transform one JSON value into
 * another. Patches are serializable and composable.
 *
 * ==Design==
 *
 * This type directly mirrors [[DynamicPatch]] but is specialized for JSON's
 * simpler data model:
 *   - JSON has 4 leaf types (String, Number, Boolean, Null) vs 30+
 *     PrimitiveValues
 *   - JSON objects have string keys only (no arbitrary-keyed maps)
 *   - JSON has no native Variant type
 *
 * ==Algebraic Laws==
 *
 * '''Monoid Laws''' (under `++` composition):
 * {{{
 * // 1. LEFT IDENTITY
 * ∀ p: JsonPatch, j: Json.
 *   (JsonPatch.empty ++ p)(j, mode) == p(j, mode)
 *
 * // 2. RIGHT IDENTITY
 * ∀ p: JsonPatch, j: Json.
 *   (p ++ JsonPatch.empty)(j, mode) == p(j, mode)
 *
 * // 3. ASSOCIATIVITY
 * ∀ p1, p2, p3: JsonPatch, j: Json.
 *   ((p1 ++ p2) ++ p3)(j, mode) == (p1 ++ (p2 ++ p3))(j, mode)
 * }}}
 *
 * '''Diff/Apply Laws''':
 * {{{
 * // 4. ROUNDTRIP
 * ∀ source, target: Json.
 *   JsonPatch.diff(source, target)(source, Strict) == Right(target)
 *
 * // 5. IDENTITY DIFF
 * ∀ j: Json.
 *   JsonPatch.diff(j, j).isEmpty == true
 *
 * // 6. DIFF COMPOSITION
 * ∀ a, b, c: Json.
 *   (JsonPatch.diff(a, b) ++ JsonPatch.diff(b, c))(a, Strict) == Right(c)
 * }}}
 *
 * '''PatchMode Laws''':
 * {{{
 * // 7. LENIENT SUBSUMES STRICT
 * ∀ p: JsonPatch, j: Json.
 *   p(j, Strict) == Right(r) implies p(j, Lenient) == Right(r)
 * }}}
 *
 * ==Usage==
 *
 * Diff and apply:
 * {{{
 * val source = Json.Object("x" -> Json.Number(1))
 * val target = Json.Object("x" -> Json.Number(5))
 *
 * val patch  = JsonPatch.diff(source, target)
 * val result = patch(source, JsonPatchMode.Strict)
 * // result == Right(target)
 * }}}
 *
 * Patch modes:
 * {{{
 * val json = Json.Object("a" -> Json.Number(1))
 *
 * // Missing path:
 * val missing = JsonPatch(DynamicOptic.root.field("b"), JsonPatch.Op.Set(Json.Number(2)))
 * missing(json, JsonPatchMode.Strict)   // Left(...)
 * missing(json, JsonPatchMode.Lenient)  // Right(json)
 * missing(json, JsonPatchMode.Clobber)  // Right(json)
 *
 * // Conflict overwrite (clobber):
 * val overwrite = JsonPatch.root(
 *   JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", Json.Number(2))))
 * )
 * overwrite(json, JsonPatchMode.Strict) // Left(...)
 * overwrite(json, JsonPatchMode.Clobber) // Right({"a":2})
 * }}}
 *
 * @param ops
 *   The sequence of patch operations
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) {

  /**
   * Applies this patch to a JSON value.
   *
   * @param json
   *   The JSON value to patch
   * @param mode
   *   The patch mode (default: Strict)
   * @return
   *   Either an error or the patched value
   */
  def apply(json: Json, mode: JsonPatchMode = JsonPatchMode.Strict): Either[JsonError, Json] = {
    var current: Json                  = json
    var idx                            = 0
    var error: Either[JsonError, Unit] = Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op.path.nodes, op.op, mode) match {
        case Right(updated) =>
          current = updated
        case Left(err) =>
          mode match {
            case JsonPatchMode.Strict  => error = Left(err)
            case JsonPatchMode.Lenient => () // Skip this operation
            case JsonPatchMode.Clobber => () // Clobber mode errors are handled by applyOp
          }
      }
      idx += 1
    }

    error.map(_ => current)
  }

  /**
   * Composes this patch with another. Applies this patch first, then `that`.
   *
   * This is the monoid `combine` operation.
   */
  def ++(that: JsonPatch): JsonPatch = JsonPatch(ops ++ that.ops)

  /**
   * Returns true if this patch contains no operations.
   */
  def isEmpty: Boolean = ops.isEmpty

  /**
   * Converts this JSON patch to a [[DynamicPatch]].
   *
   * The conversion maps JSON-specific operations to their DynamicPatch
   * equivalents.
   */
  def toDynamicPatch: DynamicPatch = {
    val dynamicOps = ops.map { jsonOp =>
      DynamicPatch.DynamicPatchOp(jsonOp.path, JsonPatch.opToDynamicOperation(jsonOp.op))
    }
    DynamicPatch(dynamicOps)
  }
}

object JsonPatch {

  /**
   * Empty patch — the identity element for `++` composition.
   */
  val empty: JsonPatch = JsonPatch(Vector.empty)

  /**
   * Creates a patch with a single operation at the root path.
   */
  def root(op: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(DynamicOptic.root, op)))

  /**
   * Creates a patch with a single operation at the given path.
   */
  def apply(path: DynamicOptic, op: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(path, op)))

  /**
   * Computes a patch that transforms `oldJson` into `newJson`.
   *
   * Law: `diff(old, new)(old, Strict) == Right(new)`
   *
   * The algorithm:
   *   1. If values are equal, return empty patch
   *   2. If types differ, use Set to replace entirely
   *   3. For same types, compute minimal diff operations
   */
  def diff(oldJson: Json, newJson: Json): JsonPatch =
    if (oldJson == newJson) {
      empty
    } else {
      (oldJson, newJson) match {
        case (Json.Number(oldVal), Json.Number(newVal)) =>
          diffNumber(oldVal, newVal)

        case (Json.String(oldVal), Json.String(newVal)) =>
          diffString(oldVal, newVal)

        case (Json.Array(oldElems), Json.Array(newElems)) =>
          diffArray(oldElems, newElems)

        case (Json.Object(oldFields), Json.Object(newFields)) =>
          diffObject(oldFields, newFields)

        case _ =>
          // Type mismatch or Boolean/Null change - use Set to replace
          root(Op.Set(newJson))
      }
    }

  /**
   * Creates a JSON patch from a [[DynamicPatch]].
   *
   * May fail if the DynamicPatch contains operations not representable in JSON
   * (e.g., temporal deltas, non-BigDecimal numeric types that can't be
   * unified).
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    val results = patch.ops.map { dynamicOp =>
      dynamicOperationToOp(dynamicOp.operation).map(op => JsonPatchOp(dynamicOp.path, op))
    }

    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) {
      Left(errors.reduce(_ ++ _))
    } else {
      Right(JsonPatch(results.collect { case Right(op) => op }))
    }
  }

  // ===========================================================================
  // JsonPatchOp — a single operation at a path
  // ===========================================================================

  /**
   * A single patch operation: a path and what to do there.
   *
   * Mirrors [[DynamicPatch.DynamicPatchOp]].
   */
  final case class JsonPatchOp(path: DynamicOptic, op: Op)

  // ===========================================================================
  // Op — the operation to perform at a path
  // ===========================================================================

  /**
   * The operation to perform at a target location.
   *
   * Mirrors [[DynamicPatch.Operation]] but specialized for JSON.
   */
  sealed trait Op

  object Op {

    /**
     * Set a value directly (replacement).
     *
     * Mirrors [[DynamicPatch.Operation.Set]].
     */
    final case class Set(value: Json) extends Op

    /**
     * Apply a primitive delta operation.
     *
     * Used for numeric deltas and string edits. Mirrors
     * [[DynamicPatch.Operation.PrimitiveDelta]].
     */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Op

    /**
     * Apply array edit operations.
     *
     * Used for inserting, appending, deleting, or modifying array elements.
     * Mirrors [[DynamicPatch.Operation.SequenceEdit]].
     */
    final case class ArrayEdit(ops: Vector[ArrayOp]) extends Op

    /**
     * Apply object edit operations.
     *
     * Used for adding, removing, or modifying object fields. Mirrors
     * [[DynamicPatch.Operation.MapEdit]] but with string keys.
     */
    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Op

    /**
     * Apply a nested patch.
     *
     * Used to group operations sharing a common path prefix. Mirrors
     * [[DynamicPatch.Operation.Patch]].
     */
    final case class Nested(patch: JsonPatch) extends Op
  }

  // ===========================================================================
  // PrimitiveOp — delta operations for JSON primitives
  // ===========================================================================

  /**
   * Delta operations for JSON primitive values.
   *
   * JSON has only one numeric type, so we use BigDecimal for deltas. Boolean
   * has no delta (use Set to toggle). Null has no delta (use Set to change).
   *
   * Mirrors [[DynamicPatch.PrimitiveOp]] but simplified for JSON's type system.
   */
  sealed trait PrimitiveOp

  object PrimitiveOp {

    /**
     * Add a delta to a JSON number.
     *
     * Applied by: `currentValue + delta`
     *
     * Mirrors the numeric delta operations in [[DynamicPatch.PrimitiveOp]]
     * (IntDelta, LongDelta, DoubleDelta, etc.) unified into one type.
     */
    final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp

    /**
     * Apply string edit operations.
     *
     * Mirrors [[DynamicPatch.PrimitiveOp.StringEdit]].
     */
    final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  }

  // ===========================================================================
  // StringOp — edit operations for strings
  // ===========================================================================

  /**
   * Edit operations for JSON strings.
   *
   * Mirrors [[DynamicPatch.StringOp]] exactly.
   */
  sealed trait StringOp

  object StringOp {

    /**
     * Insert text at the given index.
     */
    final case class Insert(index: Int, text: String) extends StringOp

    /**
     * Delete characters starting at the given index.
     */
    final case class Delete(index: Int, length: Int) extends StringOp

    /**
     * Append text to the end of the string.
     */
    final case class Append(text: String) extends StringOp

    /**
     * Replace characters starting at index with new text.
     */
    final case class Modify(index: Int, length: Int, text: String) extends StringOp
  }

  // ===========================================================================
  // ArrayOp — edit operations for arrays
  // ===========================================================================

  /**
   * Edit operations for JSON arrays.
   *
   * Mirrors [[DynamicPatch.SeqOp]] but with Json values.
   */
  sealed trait ArrayOp

  object ArrayOp {

    /**
     * Insert values at the given index.
     */
    final case class Insert(index: Int, values: Vector[Json]) extends ArrayOp

    /**
     * Append values to the end of the array.
     */
    final case class Append(values: Vector[Json]) extends ArrayOp

    /**
     * Delete elements starting at the given index.
     */
    final case class Delete(index: Int, count: Int) extends ArrayOp

    /**
     * Modify the element at the given index with a nested operation.
     */
    final case class Modify(index: Int, op: Op) extends ArrayOp
  }

  // ===========================================================================
  // ObjectOp — edit operations for objects
  // ===========================================================================

  /**
   * Edit operations for JSON objects.
   *
   * Mirrors [[DynamicPatch.MapOp]] but with string keys (JSON constraint).
   */
  sealed trait ObjectOp

  object ObjectOp {

    /**
     * Add a field to the object.
     */
    final case class Add(key: String, value: Json) extends ObjectOp

    /**
     * Remove a field from the object.
     */
    final case class Remove(key: String) extends ObjectOp

    /**
     * Modify a field's value with a nested patch.
     */
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }

  // ===========================================================================
  // Private implementation - Diff algorithms
  // ===========================================================================

  /**
   * Diff two numbers. Uses delta operation.
   */
  private def diffNumber(oldVal: BigDecimal, newVal: BigDecimal): JsonPatch = {
    val delta = newVal - oldVal
    root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)))
  }

  /**
   * Diff two strings using LCS algorithm. Uses StringEdit if the edit
   * operations are more compact than replacing the entire string.
   */
  private def diffString(oldStr: String, newStr: String): JsonPatch =
    if (oldStr == newStr) {
      empty
    } else {
      val edits = computeStringEdits(oldStr, newStr)

      // Calculate the "size" of the edit operations vs just setting the new string
      val editSize = edits.foldLeft(0) {
        case (acc, StringOp.Insert(_, text))    => acc + text.length
        case (acc, StringOp.Delete(_, _))       => acc + 1
        case (acc, StringOp.Append(text))       => acc + text.length
        case (acc, StringOp.Modify(_, _, text)) => acc + text.length
      }

      if (edits.nonEmpty && editSize < newStr.length) {
        root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(edits)))
      } else {
        root(Op.Set(Json.String(newStr)))
      }
    }

  /**
   * Compute string edits using LCS algorithm.
   */
  private def computeStringEdits(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))

    val lcs   = longestCommonSubsequence(oldStr, newStr)
    val edits = Vector.newBuilder[StringOp]

    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var cursor = 0

    while (lcsIdx < lcs.length) {
      val targetChar = lcs.charAt(lcsIdx)

      // Delete characters in old string before the next LCS character
      val deleteStart = oldIdx
      while (oldIdx < oldStr.length && oldStr.charAt(oldIdx) != targetChar) {
        oldIdx += 1
      }
      val deleteLen = oldIdx - deleteStart
      if (deleteLen > 0) {
        edits += StringOp.Delete(cursor, deleteLen)
      }

      // Insert characters from new string before the next LCS character
      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits += StringOp.Insert(cursor, text)
        cursor += text.length
      }

      // Consume the matching LCS character
      oldIdx += 1
      newIdx += 1
      cursor += 1
      lcsIdx += 1
    }

    // Delete remaining characters in old string
    if (oldIdx < oldStr.length) {
      val deleteLen = oldStr.length - oldIdx
      edits += StringOp.Delete(cursor, deleteLen)
    }

    // Insert remaining characters from new string
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) {
        edits += StringOp.Insert(cursor, text)
      }
    }

    edits.result()
  }

  /**
   * Compute the longest common subsequence of two strings.
   */
  private def longestCommonSubsequence(s1: String, s2: String): String = {
    val m = s1.length
    val n = s2.length

    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (s1(i - 1) == s2(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val result = new StringBuilder
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1(i - 1) == s2(j - 1)) {
        result.insert(0, s1(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }

    result.toString
  }

  /**
   * Diff two arrays using LCS-based alignment.
   */
  private def diffArray(oldElems: Vector[Json], newElems: Vector[Json]): JsonPatch =
    if (oldElems == newElems) {
      empty
    } else if (oldElems.isEmpty) {
      root(Op.ArrayEdit(Vector(ArrayOp.Append(newElems))))
    } else if (newElems.isEmpty) {
      root(Op.ArrayEdit(Vector(ArrayOp.Delete(0, oldElems.length))))
    } else {
      val arrayOps = computeArrayOps(oldElems, newElems)
      if (arrayOps.isEmpty) empty
      else root(Op.ArrayEdit(arrayOps))
    }

  /**
   * Compute array operations using LCS alignment.
   */
  private def computeArrayOps(oldElems: Vector[Json], newElems: Vector[Json]): Vector[ArrayOp] = {
    val ops       = Vector.newBuilder[ArrayOp]
    val matches   = longestCommonSubsequenceIndices(oldElems, newElems)
    var oldIdx    = 0
    var newIdx    = 0
    var cursor    = 0
    var curLength = oldElems.length

    def emitDelete(count: Int): Unit =
      if (count > 0) {
        ops += ArrayOp.Delete(cursor, count)
        curLength -= count
      }

    def emitInsert(values: Vector[Json]): Unit =
      if (values.nonEmpty) {
        val insertionIndex = cursor
        if (insertionIndex == curLength) ops += ArrayOp.Append(values)
        else ops += ArrayOp.Insert(insertionIndex, values)
        cursor += values.length
        curLength += values.length
      }

    matches.foreach { case (matchOld, matchNew) =>
      emitDelete(matchOld - oldIdx)
      emitInsert(newElems.slice(newIdx, matchNew))

      oldIdx = matchOld + 1
      newIdx = matchNew + 1
      cursor += 1
    }

    emitDelete(oldElems.length - oldIdx)
    emitInsert(newElems.slice(newIdx, newElems.length))

    ops.result()
  }

  /**
   * LCS helper that returns the indices of aligned elements.
   */
  private def longestCommonSubsequenceIndices(
    oldElems: Vector[Json],
    newElems: Vector[Json]
  ): Vector[(Int, Int)] = {
    val m  = oldElems.length
    val n  = newElems.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (oldElems(i - 1) == newElems(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val builder = Vector.newBuilder[(Int, Int)]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (oldElems(i - 1) == newElems(j - 1)) {
        builder += ((i - 1, j - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) >= dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }

    builder.result().reverse
  }

  /**
   * Diff two objects by comparing fields.
   */
  private def diffObject(
    oldFields: Vector[(String, Json)],
    newFields: Vector[(String, Json)]
  ): JsonPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap

    val objectOps = Vector.newBuilder[ObjectOp]

    // Find added and modified keys
    for ((key, newValue) <- newFields) {
      oldMap.get(key) match {
        case Some(oldValue) if oldValue != newValue =>
          // Key exists in both but value changed - recursively diff
          val valuePatch = diff(oldValue, newValue)
          if (!valuePatch.isEmpty) {
            objectOps += ObjectOp.Modify(key, valuePatch)
          }
        case None =>
          // Key only in new object - add it
          objectOps += ObjectOp.Add(key, newValue)
        case _ =>
        // Key unchanged - skip
      }
    }

    // Find removed keys
    for ((key, _) <- oldFields) {
      if (!newMap.contains(key)) {
        objectOps += ObjectOp.Remove(key)
      }
    }

    val ops = objectOps.result()
    if (ops.isEmpty) empty
    else root(Op.ObjectEdit(ops))
  }

  // ===========================================================================
  // Private implementation - Apply operations
  // ===========================================================================

  /**
   * Apply a single operation at a path within a JSON value.
   */
  private[json] def applyOp(
    value: Json,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Json] =
    if (path.isEmpty) {
      applyOperation(value, operation, mode, DynamicOptic.root)
    } else {
      navigateAndApply(value, path, 0, operation, mode, DynamicOptic.root)
    }

  /**
   * Navigate to the target location and apply the operation.
   */
  private def navigateAndApply(
    value: Json,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: JsonPatchMode,
    currentPath: DynamicOptic
  ): Either[JsonError, Json] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case Json.Object(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              mode match {
                case JsonPatchMode.Strict =>
                  Left(JsonError.missingField(currentPath, name))
                case JsonPatchMode.Lenient =>
                  Left(JsonError.missingField(currentPath, name))
                case JsonPatchMode.Clobber =>
                  Left(JsonError.missingField(currentPath, name))
              }
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newPath                 = currentPath.field(name)

              if (isLast) {
                applyOperation(fieldValue, operation, mode, newPath).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode, newPath).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }

          case _ =>
            Left(JsonError.typeMismatch(currentPath, "Object", value.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        value match {
          case Json.Array(elements) =>
            if (index < 0 || index >= elements.length) {
              Left(JsonError.indexOutOfBounds(currentPath, index, elements.length))
            } else {
              val element = elements(index)
              val newPath = currentPath.at(index)

              if (isLast) {
                applyOperation(element, operation, mode, newPath).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, operation, mode, newPath).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              }
            }

          case _ =>
            Left(JsonError.typeMismatch(currentPath, "Array", value.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case Json.Array(elements) =>
            if (elements.isEmpty) {
              mode match {
                case JsonPatchMode.Strict =>
                  Left(JsonError.invalidOperation(currentPath, "Cannot apply patch to empty array"))
                case _ =>
                  Right(value)
              }
            } else if (isLast) {
              applyToAllElements(elements, operation, mode, currentPath).map(Json.Array(_))
            } else {
              navigateAllElements(elements, path, pathIdx + 1, operation, mode, currentPath).map(Json.Array(_))
            }

          case _ =>
            Left(JsonError.typeMismatch(currentPath, "Array", value.getClass.getSimpleName))
        }

      // JSON-incompatible path nodes - provide clear error messages
      case DynamicOptic.Node.Case(caseName) =>
        Left(JsonError.invalidOperation(currentPath, s"JSON does not support Variant types (case: $caseName)"))

      case DynamicOptic.Node.AtMapKey(_) =>
        Left(JsonError.invalidOperation(currentPath, "JSON objects use field() for string keys, not atMapKey()"))

      case DynamicOptic.Node.Wrapped =>
        Left(JsonError.invalidOperation(currentPath, "JSON does not support wrapped/newtype navigation"))

      case DynamicOptic.Node.AtIndices(_) =>
        Left(JsonError.invalidOperation(currentPath, "AtIndices is not supported in patches"))

      case DynamicOptic.Node.AtMapKeys(_) =>
        Left(JsonError.invalidOperation(currentPath, "AtMapKeys is not supported in patches"))

      case DynamicOptic.Node.MapKeys =>
        Left(JsonError.invalidOperation(currentPath, "MapKeys is not supported in patches"))

      case DynamicOptic.Node.MapValues =>
        Left(JsonError.invalidOperation(currentPath, "MapValues is not supported in patches"))
    }
  }

  /**
   * Apply operation to all elements in an array.
   */
  private def applyToAllElements(
    elements: Vector[Json],
    operation: Op,
    mode: JsonPatchMode,
    basePath: DynamicOptic
  ): Either[JsonError, Vector[Json]] = {
    val results                  = new Array[Json](elements.length)
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < elements.length && error.isEmpty) {
      val elementPath = basePath.at(idx)
      applyOperation(elements(idx), operation, mode, elementPath) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode != JsonPatchMode.Strict) {
            results(idx) = elements(idx)
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(results.toVector)
  }

  /**
   * Navigate deeper into all elements of an array.
   */
  private def navigateAllElements(
    elements: Vector[Json],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: JsonPatchMode,
    basePath: DynamicOptic
  ): Either[JsonError, Vector[Json]] = {
    val results                  = new Array[Json](elements.length)
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < elements.length && error.isEmpty) {
      val elementPath = basePath.at(idx)
      navigateAndApply(elements(idx), path, pathIdx, operation, mode, elementPath) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode != JsonPatchMode.Strict) {
            results(idx) = elements(idx)
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error.toLeft(results.toVector)
  }

  /**
   * Apply an operation to a value at the current location.
   */
  private def applyOperation(
    value: Json,
    operation: Op,
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Json] =
    operation match {
      case Op.Set(newValue) =>
        Right(newValue)

      case Op.PrimitiveDelta(op) =>
        applyPrimitiveDelta(value, op, path)

      case Op.ArrayEdit(arrayOps) =>
        applyArrayEdit(value, arrayOps, mode, path)

      case Op.ObjectEdit(objectOps) =>
        applyObjectEdit(value, objectOps, mode, path)

      case Op.Nested(nestedPatch) =>
        nestedPatch.apply(value, toPatchMode(mode))
    }

  private def toPatchMode(mode: JsonPatchMode): JsonPatchMode = mode

  /**
   * Apply a primitive delta operation.
   */
  private def applyPrimitiveDelta(
    value: Json,
    op: PrimitiveOp,
    path: DynamicOptic
  ): Either[JsonError, Json] =
    (value, op) match {
      case (Json.Number(v), PrimitiveOp.NumberDelta(delta)) =>
        Right(Json.Number(v + delta))

      case (Json.String(v), PrimitiveOp.StringEdit(ops)) =>
        applyStringEdits(v, ops, path).map(Json.String(_))

      case _ =>
        Left(JsonError.typeMismatch(path, op.getClass.getSimpleName, value.getClass.getSimpleName))
    }

  /**
   * Apply string edit operations.
   */
  private def applyStringEdits(
    str: String,
    ops: Vector[StringOp],
    path: DynamicOptic
  ): Either[JsonError, String] = {
    var result                   = str
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            error = Some(JsonError.invalidOperation(path, s"String insert index $index out of bounds"))
          } else {
            result = result.substring(0, index) + text + result.substring(index)
          }

        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonError.invalidOperation(path, s"String delete range out of bounds"))
          } else {
            result = result.substring(0, index) + result.substring(index + length)
          }

        case StringOp.Append(text) =>
          result = result + text

        case StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonError.invalidOperation(path, s"String modify range out of bounds"))
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
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Json] =
    value match {
      case Json.Array(elements) =>
        applyArrayOps(elements, ops, mode, path).map(Json.Array(_))
      case _ =>
        Left(JsonError.typeMismatch(path, "Array", value.getClass.getSimpleName))
    }

  private def applyArrayOps(
    elements: Vector[Json],
    ops: Vector[ArrayOp],
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Vector[Json]] = {
    var result                   = elements
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      applyArrayOp(result, ops(idx), mode, path) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case JsonPatchMode.Strict  => error = Some(err)
            case JsonPatchMode.Lenient => ()
            case JsonPatchMode.Clobber => ()
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applyArrayOp(
    elements: Vector[Json],
    op: ArrayOp,
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Vector[Json]] =
    op match {
      case ArrayOp.Append(values) =>
        Right(elements ++ values)

      case ArrayOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          mode match {
            case JsonPatchMode.Strict =>
              Left(JsonError.indexOutOfBounds(path, index, elements.length))
            case JsonPatchMode.Lenient =>
              Left(JsonError.indexOutOfBounds(path, index, elements.length))
            case JsonPatchMode.Clobber =>
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
            case JsonPatchMode.Strict =>
              Left(JsonError.invalidOperation(path, s"Delete range out of bounds"))
            case JsonPatchMode.Lenient =>
              Left(JsonError.invalidOperation(path, s"Delete range out of bounds"))
            case JsonPatchMode.Clobber =>
              val clampedIndex = Math.max(0, Math.min(index, elements.length))
              val clampedEnd   = Math.max(0, Math.min(index + count, elements.length))
              Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
          }
        } else {
          Right(elements.take(index) ++ elements.drop(index + count))
        }

      case ArrayOp.Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length) {
          Left(JsonError.indexOutOfBounds(path, index, elements.length))
        } else {
          val element = elements(index)
          val newPath = path.at(index)
          applyOperation(element, nestedOp, mode, newPath).map { newElement =>
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
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Json] =
    value match {
      case Json.Object(fields) =>
        applyObjectOps(fields, ops, mode, path).map(Json.Object(_))
      case _ =>
        Left(JsonError.typeMismatch(path, "Object", value.getClass.getSimpleName))
    }

  private def applyObjectOps(
    fields: Vector[(String, Json)],
    ops: Vector[ObjectOp],
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Vector[(String, Json)]] = {
    var result                   = fields
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      applyObjectOp(result, ops(idx), mode, path) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case JsonPatchMode.Strict  => error = Some(err)
            case JsonPatchMode.Lenient => ()
            case JsonPatchMode.Clobber => ()
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applyObjectOp(
    fields: Vector[(String, Json)],
    op: ObjectOp,
    mode: JsonPatchMode,
    path: DynamicOptic
  ): Either[JsonError, Vector[(String, Json)]] =
    op match {
      case ObjectOp.Add(key, value) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          mode match {
            case JsonPatchMode.Strict =>
              Left(JsonError.invalidOperation(path, s"Key already exists: $key"))
            case JsonPatchMode.Lenient =>
              Left(JsonError.invalidOperation(path, s"Key already exists: $key"))
            case JsonPatchMode.Clobber =>
              Right(fields.updated(existingIdx, (key, value)))
          }
        } else {
          Right(fields :+ (key, value))
        }

      case ObjectOp.Remove(key) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case JsonPatchMode.Strict =>
              Left(JsonError.keyNotFound(path, key))
            case JsonPatchMode.Lenient =>
              Left(JsonError.keyNotFound(path, key))
            case JsonPatchMode.Clobber =>
              Right(fields)
          }
        } else {
          Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
        }

      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          Left(JsonError.keyNotFound(path, key))
        } else {
          val (k, v) = fields(existingIdx)
          nestedPatch.apply(v, toPatchMode(mode)).map { newValue =>
            fields.updated(existingIdx, (k, newValue))
          }
        }
    }

  // ===========================================================================
  // Private implementation - Conversion helpers
  // ===========================================================================

  /**
   * Convert a JsonPatch.Op to a DynamicPatch.Operation.
   */
  private def opToDynamicOperation(op: Op): DynamicPatch.Operation =
    op match {
      case Op.Set(value) =>
        DynamicPatch.Operation.Set(value.toDynamicValue)

      case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) =>
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(delta))

      case Op.PrimitiveDelta(PrimitiveOp.StringEdit(ops)) =>
        val dynamicOps = ops.map {
          case StringOp.Insert(idx, text)      => DynamicPatch.StringOp.Insert(idx, text)
          case StringOp.Delete(idx, len)       => DynamicPatch.StringOp.Delete(idx, len)
          case StringOp.Append(text)           => DynamicPatch.StringOp.Append(text)
          case StringOp.Modify(idx, len, text) => DynamicPatch.StringOp.Modify(idx, len, text)
        }
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.StringEdit(dynamicOps))

      case Op.ArrayEdit(ops) =>
        val seqOps = ops.map {
          case ArrayOp.Insert(idx, values) =>
            DynamicPatch.SeqOp.Insert(idx, values.map(_.toDynamicValue))
          case ArrayOp.Append(values) =>
            DynamicPatch.SeqOp.Append(values.map(_.toDynamicValue))
          case ArrayOp.Delete(idx, count) =>
            DynamicPatch.SeqOp.Delete(idx, count)
          case ArrayOp.Modify(idx, nestedOp) =>
            DynamicPatch.SeqOp.Modify(idx, opToDynamicOperation(nestedOp))
        }
        DynamicPatch.Operation.SequenceEdit(seqOps)

      case Op.ObjectEdit(ops) =>
        val mapOps = ops.map {
          case ObjectOp.Add(key, value) =>
            DynamicPatch.MapOp.Add(
              DynamicValue.Primitive(PrimitiveValue.String(key)),
              value.toDynamicValue
            )
          case ObjectOp.Remove(key) =>
            DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(key)))
          case ObjectOp.Modify(key, patch) =>
            DynamicPatch.MapOp.Modify(
              DynamicValue.Primitive(PrimitiveValue.String(key)),
              patch.toDynamicPatch
            )
        }
        DynamicPatch.Operation.MapEdit(mapOps)

      case Op.Nested(patch) =>
        DynamicPatch.Operation.Patch(patch.toDynamicPatch)
    }

  /**
   * Convert a DynamicPatch.Operation to a JsonPatch.Op.
   */
  private def dynamicOperationToOp(operation: DynamicPatch.Operation): Either[JsonError, Op] =
    operation match {
      case DynamicPatch.Operation.Set(value) =>
        Right(Op.Set(Json.fromDynamicValue(value)))

      case DynamicPatch.Operation.PrimitiveDelta(primOp) =>
        primOp match {
          case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)))
          case DynamicPatch.PrimitiveOp.IntDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta))))
          case DynamicPatch.PrimitiveOp.LongDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta))))
          case DynamicPatch.PrimitiveOp.DoubleDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta))))
          case DynamicPatch.PrimitiveOp.FloatDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble))))
          case DynamicPatch.PrimitiveOp.ShortDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt))))
          case DynamicPatch.PrimitiveOp.ByteDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt))))
          case DynamicPatch.PrimitiveOp.BigIntDelta(delta) =>
            Right(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(delta))))
          case DynamicPatch.PrimitiveOp.StringEdit(ops) =>
            val jsonOps = ops.map {
              case DynamicPatch.StringOp.Insert(idx, text)      => StringOp.Insert(idx, text)
              case DynamicPatch.StringOp.Delete(idx, len)       => StringOp.Delete(idx, len)
              case DynamicPatch.StringOp.Append(text)           => StringOp.Append(text)
              case DynamicPatch.StringOp.Modify(idx, len, text) => StringOp.Modify(idx, len, text)
            }
            Right(Op.PrimitiveDelta(PrimitiveOp.StringEdit(jsonOps)))
          case _ =>
            Left(JsonError(s"Unsupported primitive operation for JSON: ${primOp.getClass.getSimpleName}"))
        }

      case DynamicPatch.Operation.SequenceEdit(ops) =>
        val results = ops.map {
          case DynamicPatch.SeqOp.Insert(idx, values) =>
            Right(ArrayOp.Insert(idx, values.map(Json.fromDynamicValue)))
          case DynamicPatch.SeqOp.Append(values) =>
            Right(ArrayOp.Append(values.map(Json.fromDynamicValue)))
          case DynamicPatch.SeqOp.Delete(idx, count) =>
            Right(ArrayOp.Delete(idx, count))
          case DynamicPatch.SeqOp.Modify(idx, nestedOp) =>
            dynamicOperationToOp(nestedOp).map(op => ArrayOp.Modify(idx, op))
        }
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.reduce(_ ++ _))
        else Right(Op.ArrayEdit(results.collect { case Right(op) => op }))

      case DynamicPatch.Operation.MapEdit(ops) =>
        val results = ops.map {
          case DynamicPatch.MapOp.Add(key, value) =>
            extractStringKey(key).map(k => ObjectOp.Add(k, Json.fromDynamicValue(value)))
          case DynamicPatch.MapOp.Remove(key) =>
            extractStringKey(key).map(k => ObjectOp.Remove(k))
          case DynamicPatch.MapOp.Modify(key, nestedPatch) =>
            for {
              k     <- extractStringKey(key)
              patch <- fromDynamicPatch(nestedPatch)
            } yield ObjectOp.Modify(k, patch)
        }
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) Left(errors.reduce(_ ++ _))
        else Right(Op.ObjectEdit(results.collect { case Right(op) => op }))

      case DynamicPatch.Operation.Patch(nestedPatch) =>
        fromDynamicPatch(nestedPatch).map(Op.Nested(_))
    }

  /**
   * Extract a string key from a DynamicValue (for JSON object keys).
   */
  private def extractStringKey(value: DynamicValue): Either[JsonError, String] =
    value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
      case _                                                => Left(JsonError(s"JSON object keys must be strings, got: ${value.getClass.getSimpleName}"))
    }
}

// =============================================================================
// PATCH MODE
// =============================================================================

/**
 * Controls how patch application handles failures.
 *
 * Mirrors [[zio.blocks.schema.patch.PatchMode]] but specialized for JSON.
 */
sealed trait JsonPatchMode

object JsonPatchMode {

  /**
   * Fail on precondition violations.
   *
   * In Strict mode:
   *   - Missing fields cause failure
   *   - Type mismatches cause failure
   *   - Out-of-bounds indices cause failure
   *   - Adding duplicate keys causes failure
   *   - Removing non-existent keys causes failure
   */
  case object Strict extends JsonPatchMode

  /**
   * Skip operations that fail preconditions.
   *
   * In Lenient mode:
   *   - Operations that would fail are silently skipped
   *   - The original value is preserved where operations fail
   *   - Always returns Right (never fails)
   */
  case object Lenient extends JsonPatchMode

  /**
   * Replace/overwrite on conflicts.
   *
   * In Clobber mode:
   *   - Adding duplicate keys overwrites existing values
   *   - Out-of-bounds operations are clamped to valid ranges
   *   - Missing paths are skipped (like Lenient)
   *   - Always returns Right (never fails)
   */
  case object Clobber extends JsonPatchMode
}
