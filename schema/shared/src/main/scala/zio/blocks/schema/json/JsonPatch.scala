package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.blocks.schema.patch.DynamicPatch.{DynamicPatchOp, MapOp, Operation, SeqOp}
import zio.blocks.schema.patch.DynamicPatch.{PrimitiveOp => DynPrimitiveOp, StringOp => DynStringOp}

// =============================================================================
// JSON PATCH MODE
// =============================================================================

/**
 * Controls how patch application handles failures.
 *
 * Mirrors [[zio.blocks.schema.patch.PatchMode]].
 */
sealed trait JsonPatchMode

object JsonPatchMode {
  /** Fail on precondition violations. */
  case object Strict extends JsonPatchMode

  /** Skip operations that fail preconditions. */
  case object Lenient extends JsonPatchMode

  /** Replace/overwrite on conflicts (create missing paths). */
  case object Clobber extends JsonPatchMode

  /** Converts JsonPatchMode to PatchMode. */
  def toPatchMode(mode: JsonPatchMode): PatchMode = mode match {
    case Strict  => PatchMode.Strict
    case Lenient => PatchMode.Lenient
    case Clobber => PatchMode.Clobber
  }

  /** Converts PatchMode to JsonPatchMode. */
  def fromPatchMode(mode: PatchMode): JsonPatchMode = mode match {
    case PatchMode.Strict  => Strict
    case PatchMode.Lenient => Lenient
    case PatchMode.Clobber => Clobber
  }
}

// =============================================================================
// JSON PATCH
// =============================================================================

/**
 * An untyped patch that operates on [[Json]] values.
 *
 * `JsonPatch` is the JSON-specific counterpart to [[DynamicPatch]]. It represents
 * a sequence of operations that transform one JSON value into another. Patches
 * are serializable and composable.
 *
 * ==Design==
 *
 * This type directly mirrors [[DynamicPatch]] but is specialized for JSON's
 * simpler data model:
 *  - JSON has 4 leaf types (String, Number, Boolean, Null) vs 30 PrimitiveValues
 *  - JSON objects have string keys only (no arbitrary-keyed maps)
 *  - JSON has no native Variant type
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
 * @param ops The sequence of patch operations
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) {
  import JsonPatch._

  /**
   * Applies this patch to a JSON value.
   *
   * @param json The JSON value to patch
   * @param mode The patch mode (default: Strict)
   * @return Either an error or the patched value
   */
  def apply(json: Json, mode: JsonPatchMode = JsonPatchMode.Strict): Either[JsonError, Json] = {
    var current: Json                   = json
    var idx                             = 0
    var error: Either[JsonError, Unit]  = Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      applyOp(current, op.path.nodes, op.op, mode) match {
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
   */
  def toDynamicPatch: DynamicPatch = {
    val dynOps = ops.map { jOp =>
      DynamicPatchOp(jOp.path, opToDynamicOperation(jOp.op))
    }
    DynamicPatch(dynOps)
  }

  private def opToDynamicOperation(op: Op): Operation = op match {
    case Op.Set(value) =>
      Operation.Set(value.toDynamicValue)

    case Op.PrimitiveDelta(primOp) =>
      primOp match {
        case JsonPatch.PrimitiveOp.NumberDelta(delta) =>
          Operation.PrimitiveDelta(DynPrimitiveOp.BigDecimalDelta(delta))
        case JsonPatch.PrimitiveOp.StringEdit(stringOps) =>
          val dynOps = stringOps.map {
            case JsonPatch.StringOp.Insert(index, text)        => DynStringOp.Insert(index, text)
            case JsonPatch.StringOp.Delete(index, length)      => DynStringOp.Delete(index, length)
            case JsonPatch.StringOp.Append(text)               => DynStringOp.Append(text)
            case JsonPatch.StringOp.Modify(index, length, text) => DynStringOp.Modify(index, length, text)
          }
          Operation.PrimitiveDelta(DynPrimitiveOp.StringEdit(dynOps))
      }

    case Op.ArrayEdit(arrayOps) =>
      val seqOps = arrayOps.map {
        case ArrayOp.Insert(index, values) =>
          SeqOp.Insert(index, values.map(_.toDynamicValue))
        case ArrayOp.Append(values) =>
          SeqOp.Append(values.map(_.toDynamicValue))
        case ArrayOp.Delete(index, count) =>
          SeqOp.Delete(index, count)
        case ArrayOp.Modify(index, nestedOp) =>
          SeqOp.Modify(index, opToDynamicOperation(nestedOp))
      }
      Operation.SequenceEdit(seqOps)

    case Op.ObjectEdit(objectOps) =>
      val mapOps = objectOps.map {
        case ObjectOp.Add(key, value) =>
          MapOp.Add(DynamicValue.Primitive(PrimitiveValue.String(key)), value.toDynamicValue)
        case ObjectOp.Remove(key) =>
          MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(key)))
        case ObjectOp.Modify(key, nestedPatch) =>
          MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String(key)), nestedPatch.toDynamicPatch)
      }
      Operation.MapEdit(mapOps)

    case Op.Nested(nestedPatch) =>
      Operation.Patch(nestedPatch.toDynamicPatch)
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
          // Type mismatch or simple replacement - use Set
          root(Op.Set(newJson))
      }
    }

  /**
   * Creates a JSON patch from a [[DynamicPatch]].
   *
   * May fail if the DynamicPatch contains operations not representable in JSON
   * (e.g., non-string map keys, temporal deltas, variant operations).
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    val results = patch.ops.map { dynOp =>
      dynamicOperationToOp(dynOp.operation).map { op =>
        JsonPatchOp(dynOp.path, op)
      }
    }

    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) {
      Left(errors.head)
    } else {
      Right(JsonPatch(results.collect { case Right(op) => op }))
    }
  }

  private def dynamicOperationToOp(operation: Operation): Either[JsonError, Op] = operation match {
    case Operation.Set(value) =>
      Right(Op.Set(Json.fromDynamicValue(value)))

    case Operation.PrimitiveDelta(primOp) =>
      primOp match {
        case DynPrimitiveOp.BigDecimalDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(delta)))
        case DynPrimitiveOp.IntDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta))))
        case DynPrimitiveOp.LongDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta))))
        case DynPrimitiveOp.DoubleDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta))))
        case DynPrimitiveOp.FloatDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble))))
        case DynPrimitiveOp.ShortDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta.toInt))))
        case DynPrimitiveOp.ByteDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta.toInt))))
        case DynPrimitiveOp.BigIntDelta(delta) =>
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(delta))))
        case DynPrimitiveOp.StringEdit(ops) =>
          val jsonOps = ops.map {
            case DynStringOp.Insert(index, text)        => JsonPatch.StringOp.Insert(index, text)
            case DynStringOp.Delete(index, length)      => JsonPatch.StringOp.Delete(index, length)
            case DynStringOp.Append(text)               => JsonPatch.StringOp.Append(text)
            case DynStringOp.Modify(index, length, text) => JsonPatch.StringOp.Modify(index, length, text)
          }
          Right(Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(jsonOps)))
        case _ =>
          Left(JsonError("Temporal delta operations are not representable in JSON"))
      }

    case Operation.SequenceEdit(seqOps) =>
      val results = seqOps.map {
        case SeqOp.Insert(index, values) =>
          Right(ArrayOp.Insert(index, values.map(Json.fromDynamicValue)))
        case SeqOp.Append(values) =>
          Right(ArrayOp.Append(values.map(Json.fromDynamicValue)))
        case SeqOp.Delete(index, count) =>
          Right(ArrayOp.Delete(index, count))
        case SeqOp.Modify(index, operation) =>
          dynamicOperationToOp(operation).map(op => ArrayOp.Modify(index, op))
      }
      val errors = results.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.head)
      else Right(Op.ArrayEdit(results.collect { case Right(op) => op }))

    case Operation.MapEdit(mapOps) =>
      val results = mapOps.map {
        case MapOp.Add(key, value) =>
          extractStringKey(key).map(k => ObjectOp.Add(k, Json.fromDynamicValue(value)))
        case MapOp.Remove(key) =>
          extractStringKey(key).map(ObjectOp.Remove)
        case MapOp.Modify(key, patch) =>
          for {
            k <- extractStringKey(key)
            p <- fromDynamicPatch(patch)
          } yield ObjectOp.Modify(k, p)
      }
      val errors = results.collect { case Left(e) => e }
      if (errors.nonEmpty) Left(errors.head)
      else Right(Op.ObjectEdit(results.collect { case Right(op) => op }))

    case Operation.Patch(nestedPatch) =>
      fromDynamicPatch(nestedPatch).map(Op.Nested)
  }

  private def extractStringKey(key: DynamicValue): Either[JsonError, String] = key match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
    case _ => Left(JsonError("JSON objects require string keys"))
  }

  // ===========================================================================
  // Diff Algorithms
  // ===========================================================================

  private def diffNumber(oldVal: String, newVal: String): JsonPatch = {
    val oldNum = BigDecimal(oldVal)
    val newNum = BigDecimal(newVal)
    val delta  = newNum - oldNum
    root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)))
  }

  private def diffString(oldStr: String, newStr: String): JsonPatch =
    if (oldStr == newStr) {
      empty
    } else {
      val edits = computeStringEdits(oldStr, newStr)

      // Calculate the "size" of the edit operations vs just setting the new string.
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

      val deleteStart = oldIdx
      while (oldIdx < oldStr.length && oldStr.charAt(oldIdx) != targetChar) {
        oldIdx += 1
      }
      val deleteLen = oldIdx - deleteStart
      if (deleteLen > 0) {
        edits += StringOp.Delete(cursor, deleteLen)
      }

      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits += StringOp.Insert(cursor, text)
        cursor += text.length
      }

      oldIdx += 1
      newIdx += 1
      cursor += 1
      lcsIdx += 1
    }

    if (oldIdx < oldStr.length) {
      val deleteLen = oldStr.length - oldIdx
      edits += StringOp.Delete(cursor, deleteLen)
    }

    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) {
        edits += StringOp.Insert(cursor, text)
      }
    }

    edits.result()
  }

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

  private def diffObject(
    oldFields: Vector[(String, Json)],
    newFields: Vector[(String, Json)]
  ): JsonPatch = {
    val oldMap = oldFields.toMap

    val ops = Vector.newBuilder[JsonPatchOp]

    // Check each field in the new object
    for ((fieldName, newValue) <- newFields) {
      oldMap.get(fieldName) match {
        case Some(oldValue) if oldValue != newValue =>
          // Field exists in both but has different value - recursively diff
          val fieldPatch = diff(oldValue, newValue)
          if (!fieldPatch.isEmpty) {
            // Prepend the field path to each operation
            for (op <- fieldPatch.ops) {
              ops += JsonPatchOp(
                new DynamicOptic(DynamicOptic.Node.Field(fieldName) +: op.path.nodes),
                op.op
              )
            }
          }
        case None =>
          // Field only exists in new object - set it
          ops += JsonPatchOp(
            new DynamicOptic(Vector(DynamicOptic.Node.Field(fieldName))),
            Op.Set(newValue)
          )
        case _ =>
        // Field unchanged - skip
      }
    }

    // Fields that exist in old but not in new - remove them
    val newMap = newFields.toMap
    for ((fieldName, _) <- oldFields) {
      if (!newMap.contains(fieldName)) {
        ops += JsonPatchOp(
          DynamicOptic.root,
          Op.ObjectEdit(Vector(ObjectOp.Remove(fieldName)))
        )
      }
    }

    JsonPatch(ops.result())
  }

  // ===========================================================================
  // Apply Operation
  // ===========================================================================

  private def applyOp(
    json: Json,
    path: IndexedSeq[DynamicOptic.Node],
    op: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Json] =
    if (path.isEmpty) {
      applyOperation(json, op, mode, Nil)
    } else {
      navigateAndApply(json, path, 0, op, mode, Nil)
    }

  private def navigateAndApply(
    json: Json,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    op: Op,
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Json] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case Json.Object(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              mode match {
                case JsonPatchMode.Strict =>
                  Left(JsonError(s"Missing field: $name", DynamicOptic(trace.reverse.toVector)))
                case JsonPatchMode.Lenient =>
                  Left(JsonError(s"Missing field: $name", DynamicOptic(trace.reverse.toVector)))
                case JsonPatchMode.Clobber =>
                  // In clobber mode, create the field
                  if (isLast) {
                    applyOperation(Json.Null, op, mode, DynamicOptic.Node.Field(name) :: trace).map { newValue =>
                      Json.Object(fields :+ (name, newValue))
                    }
                  } else {
                    Left(JsonError(s"Missing field: $name", DynamicOptic(trace.reverse.toVector)))
                  }
              }
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = DynamicOptic.Node.Field(name) :: trace

              if (isLast) {
                applyOperation(fieldValue, op, mode, newTrace).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              } else {
                navigateAndApply(fieldValue, path, pathIdx + 1, op, mode, newTrace).map { newFieldValue =>
                  Json.Object(fields.updated(fieldIdx, (fieldName, newFieldValue)))
                }
              }
            }

          case _ =>
            Left(JsonError(s"Expected Object but got ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case Json.Array(elements) =>
            if (index < 0 || index >= elements.length) {
              Left(JsonError(s"Index $index out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
            } else {
              val element  = elements(index)
              val newTrace = DynamicOptic.Node.AtIndex(index) :: trace

              if (isLast) {
                applyOperation(element, op, mode, newTrace).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              } else {
                navigateAndApply(element, path, pathIdx + 1, op, mode, newTrace).map { newElement =>
                  Json.Array(elements.updated(index, newElement))
                }
              }
            }

          case _ =>
            Left(JsonError(s"Expected Array but got ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
        }

      case DynamicOptic.Node.Elements =>
        json match {
          case Json.Array(elements) =>
            val newTrace = DynamicOptic.Node.Elements :: trace

            if (elements.isEmpty) {
              mode match {
                case JsonPatchMode.Strict =>
                  Left(JsonError("Encountered an empty array", DynamicOptic(newTrace.reverse.toVector)))
                case _ =>
                  Right(json)
              }
            } else if (isLast) {
              applyToAllElements(elements, op, mode, newTrace).map(Json.Array(_))
            } else {
              navigateAllElements(elements, path, pathIdx + 1, op, mode, newTrace).map(Json.Array(_))
            }

          case _ =>
            Left(JsonError(s"Expected Array but got ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
        }

      case _ =>
        Left(JsonError(s"Unsupported path node: $node", DynamicOptic(trace.reverse.toVector)))
    }
  }

  private def applyToAllElements(
    elements: Vector[Json],
    op: Op,
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[Json]] = {
    val results                   = new Array[Json](elements.length)
    var idx                       = 0
    var error: Option[JsonError]  = None

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      applyOperation(elements(idx), op, mode, elementTrace) match {
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

  private def navigateAllElements(
    elements: Vector[Json],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    op: Op,
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[Json]] = {
    val results                   = new Array[Json](elements.length)
    var idx                       = 0
    var error: Option[JsonError]  = None

    while (idx < elements.length && error.isEmpty) {
      val elementTrace = DynamicOptic.Node.AtIndex(idx) :: trace
      navigateAndApply(elements(idx), path, pathIdx, op, mode, elementTrace) match {
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

  private def applyOperation(
    json: Json,
    op: Op,
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Json] =
    op match {
      case Op.Set(newValue) =>
        Right(newValue)

      case Op.PrimitiveDelta(primOp) =>
        applyPrimitiveDelta(json, primOp, trace)

      case Op.ArrayEdit(arrayOps) =>
        applyArrayEdit(json, arrayOps, mode, trace)

      case Op.ObjectEdit(objectOps) =>
        applyObjectEdit(json, objectOps, mode, trace)

      case Op.Nested(nestedPatch) =>
        nestedPatch.apply(json, mode)
    }

  private def applyPrimitiveDelta(
    json: Json,
    op: PrimitiveOp,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Json] =
    (json, op) match {
      case (Json.Number(v), PrimitiveOp.NumberDelta(delta)) =>
        val newVal = BigDecimal(v) + delta
        Right(Json.Number(newVal.toString))

      case (Json.String(v), PrimitiveOp.StringEdit(ops)) =>
        applyStringEdits(v, ops, trace).map(Json.String)

      case _ =>
        Left(JsonError(s"Type mismatch: cannot apply ${op.getClass.getSimpleName} to ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
    }

  private def applyStringEdits(
    str: String,
    ops: Vector[StringOp],
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, String] = {
    var result                    = str
    var idx                       = 0
    var error: Option[JsonError]  = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            error = Some(JsonError(s"String insert index $index out of bounds for string of length ${result.length}", DynamicOptic(trace.reverse.toVector)))
          } else {
            result = result.substring(0, index) + text + result.substring(index)
          }

        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonError(s"String delete range [$index, ${index + length}) out of bounds for string of length ${result.length}", DynamicOptic(trace.reverse.toVector)))
          } else {
            result = result.substring(0, index) + result.substring(index + length)
          }

        case StringOp.Append(text) =>
          result = result + text

        case StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonError(s"String modify range [$index, ${index + length}) out of bounds for string of length ${result.length}", DynamicOptic(trace.reverse.toVector)))
          } else {
            result = result.substring(0, index) + text + result.substring(index + length)
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applyArrayEdit(
    json: Json,
    ops: Vector[ArrayOp],
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Json] =
    json match {
      case Json.Array(elements) =>
        applyArrayOps(elements, ops, mode, trace).map(Json.Array(_))
      case _ =>
        Left(JsonError(s"Expected Array but got ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
    }

  private def applyArrayOps(
    elements: Vector[Json],
    ops: Vector[ArrayOp],
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[Json]] = {
    var result                    = elements
    var idx                       = 0
    var error: Option[JsonError]  = None

    while (idx < ops.length && error.isEmpty) {
      applyArrayOp(result, ops(idx), mode, trace) match {
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
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[Json]] =
    op match {
      case ArrayOp.Append(values) =>
        Right(elements ++ values)

      case ArrayOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          mode match {
            case JsonPatchMode.Strict =>
              Left(JsonError(s"Insert index $index out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
            case JsonPatchMode.Lenient =>
              Left(JsonError(s"Insert index $index out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
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
              Left(JsonError(s"Delete range [$index, ${index + count}) out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
            case JsonPatchMode.Lenient =>
              Left(JsonError(s"Delete range [$index, ${index + count}) out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
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
          Left(JsonError(s"Modify index $index out of bounds for array of length ${elements.length}", DynamicOptic(trace.reverse.toVector)))
        } else {
          val element  = elements(index)
          val newTrace = DynamicOptic.Node.AtIndex(index) :: trace
          applyOperation(element, nestedOp, mode, newTrace).map { newElement =>
            elements.updated(index, newElement)
          }
        }
    }

  private def applyObjectEdit(
    json: Json,
    ops: Vector[ObjectOp],
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Json] =
    json match {
      case Json.Object(fields) =>
        applyObjectOps(fields, ops, mode, trace).map(Json.Object(_))
      case _ =>
        Left(JsonError(s"Expected Object but got ${json.getClass.getSimpleName}", DynamicOptic(trace.reverse.toVector)))
    }

  private def applyObjectOps(
    fields: Vector[(String, Json)],
    ops: Vector[ObjectOp],
    mode: JsonPatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[(String, Json)]] = {
    var result                    = fields
    var idx                       = 0
    var error: Option[JsonError]  = None

    while (idx < ops.length && error.isEmpty) {
      applyObjectOp(result, ops(idx), mode, trace) match {
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
    trace: List[DynamicOptic.Node]
  ): Either[JsonError, Vector[(String, Json)]] =
    op match {
      case ObjectOp.Add(key, value) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          mode match {
            case JsonPatchMode.Strict =>
              Left(JsonError(s"Key '$key' already exists in object", DynamicOptic(trace.reverse.toVector)))
            case JsonPatchMode.Lenient =>
              Left(JsonError(s"Key '$key' already exists in object", DynamicOptic(trace.reverse.toVector)))
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
              Left(JsonError(s"Key '$key' not found in object", DynamicOptic(trace.reverse.toVector)))
            case JsonPatchMode.Lenient =>
              Left(JsonError(s"Key '$key' not found in object", DynamicOptic(trace.reverse.toVector)))
            case JsonPatchMode.Clobber =>
              Right(fields)
          }
        } else {
          Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
        }

      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          Left(JsonError(s"Key '$key' not found in object", DynamicOptic(trace.reverse.toVector)))
        } else {
          val (k, v) = fields(existingIdx)
          nestedPatch.apply(v, mode).map { newValue =>
            fields.updated(existingIdx, (k, newValue))
          }
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
     * Used for numeric deltas and string edits.
     * Mirrors [[DynamicPatch.Operation.PrimitiveDelta]].
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
     * Used for adding, removing, or modifying object fields.
     * Mirrors [[DynamicPatch.Operation.MapEdit]] but with string keys.
     */
    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Op

    /**
     * Apply a nested patch.
     *
     * Used to group operations sharing a common path prefix.
     * Mirrors [[DynamicPatch.Operation.Patch]].
     */
    final case class Nested(patch: JsonPatch) extends Op
  }

  // ===========================================================================
  // PrimitiveOp — delta operations for JSON primitives
  // ===========================================================================

  /**
   * Delta operations for JSON primitive values.
   *
   * JSON has only one numeric type, so we use BigDecimal for deltas.
   * Boolean has no delta (use Set to toggle).
   * Null has no delta (use Set to change).
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

    /** Insert text at the given index. */
    final case class Insert(index: Int, text: String) extends StringOp

    /** Delete characters starting at the given index. */
    final case class Delete(index: Int, length: Int) extends StringOp

    /** Append text to the end of the string. */
    final case class Append(text: String) extends StringOp

    /** Replace characters starting at index with new text. */
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

    /** Insert values at the given index. */
    final case class Insert(index: Int, values: Vector[Json]) extends ArrayOp

    /** Append values to the end of the array. */
    final case class Append(values: Vector[Json]) extends ArrayOp

    /** Delete elements starting at the given index. */
    final case class Delete(index: Int, count: Int) extends ArrayOp

    /** Modify the element at the given index with a nested operation. */
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

    /** Add a field to the object. */
    final case class Add(key: String, value: Json) extends ObjectOp

    /** Remove a field from the object. */
    final case class Remove(key: String) extends ObjectOp

    /** Modify a field's value with a nested patch. */
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }
}
