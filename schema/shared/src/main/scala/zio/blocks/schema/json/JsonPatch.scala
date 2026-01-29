package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}

import scala.collection.immutable.IndexedSeq

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
 *   - JSON has 4 leaf types (String, Number, Boolean, Null) vs 30
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
 * @param ops
 *   The sequence of patch operations
 */
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) { self =>

  /**
   * Applies this patch to a JSON value.
   *
   * @example
   *   {{{
   *   val json = Json.Object(Chunk(
   *     ("name", Json.String("Alice")),
   *     ("age", Json.Number("30"))
   *   ))
   *   val patch = JsonPatch.root(JsonPatch.Op.Set(Json.String("Bob")))
   *   val result = patch(json, PatchMode.Strict)
   *   }}}
   *
   * @param json
   *   The JSON value to patch
   * @param mode
   *   The patch mode (default: Strict)
   * @return
   *   Either an error or the patched value
   */
  def apply(json: Json, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json] = {
    var current: Json                    = json
    var idx                              = 0
    var error: Either[SchemaError, Unit] = new Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op.path.nodes, op.op, mode) match {
        case Right(updated) =>
          current = updated
        case Left(err) =>
          mode match {
            case PatchMode.Strict  => error = new Left(err)
            case PatchMode.Lenient => () // Skip this operation
            case PatchMode.Clobber => () // Clobber mode errors are handled by applyOp
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
   *
   * @example
   *   {{{
   *   val p1 = JsonPatch.diff(a, b)
   *   val p2 = JsonPatch.diff(b, c)
   *   val composed = p1 ++ p2
   *   // Associativity: ((p1 ++ p2) ++ p3) == (p1 ++ (p2 ++ p3))
   *   }}}
   */
  def ++(that: JsonPatch): JsonPatch = new JsonPatch(ops ++ that.ops)

  /**
   * Returns true if this patch contains no operations.
   */
  def isEmpty: Boolean = ops.isEmpty

  /**
   * Converts this JSON patch to a [[DynamicPatch]].
   *
   * This enables interoperability with the typed `Patch[A]` system.
   */
  def toDynamicPatch: DynamicPatch =
    new DynamicPatch(ops.toVector.map(JsonPatch.jsonPatchOpToDynamicPatchOp))

  override def toString: String =
    if (ops.isEmpty) "JsonPatch {}"
    else {
      val sb = new StringBuilder("JsonPatch {\n")
      ops.foreach(op => JsonPatch.renderOp(sb, op, "  "))
      sb.append("}")
      sb.toString
    }
}

object JsonPatch {

  // ─────────────────────────────────────────────────────────────────────────
  // Factory Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Empty patch — the identity element for `++` composition.
   *
   * @example
   *   {{{
   *   // Left identity: (empty ++ p)(j) == p(j)
   *   assert((JsonPatch.empty ++ p)(j, Strict) == p(j, Strict))
   *   // Right identity: (p ++ empty)(j) == p(j)
   *   assert((p ++ JsonPatch.empty)(j, Strict) == p(j, Strict))
   *   }}}
   */
  val empty: JsonPatch = new JsonPatch(Chunk.empty)

  /**
   * Creates a patch with a single operation at the root path.
   *
   * @example
   *   {{{
   *   // Numeric delta: add 5 to the number
   *   val json = Json.Number("10")
   *   val patch = JsonPatch.root(
   *     JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
   *   )
   *   assert(patch(json, PatchMode.Strict) == Right(Json.Number("15")))
   *   }}}
   */
  def root(op: Op): JsonPatch =
    new JsonPatch(Chunk(new JsonPatchOp(DynamicOptic.root, op)))

  /**
   * Creates a patch with a single operation at the given path.
   *
   * @example
   *   {{{
   *   // Modify nested field user.age
   *   val patch = JsonPatch(
   *     DynamicOptic.root.field("user").field("age"),
   *     JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1)))
   *   )
   *   }}}
   */
  def apply(path: DynamicOptic, op: Op): JsonPatch =
    new JsonPatch(Chunk(new JsonPatchOp(path, op)))

  /**
   * Computes a patch that transforms `oldJson` into `newJson`.
   *
   * @example
   *   {{{
   *   val source = Json.Object("x" -> Json.Number("1"))
   *   val target = Json.Object("x" -> Json.Number("5"))
   *   val patch = JsonPatch.diff(source, target)
   *   assert(patch(source, PatchMode.Strict) == Right(target))
   *   }}}
   */
  def diff(oldJson: Json, newJson: Json): JsonPatch =
    JsonDiffer.diff(oldJson, newJson)

  /**
   * Creates a JSON patch from a [[DynamicPatch]].
   *
   * May fail if the DynamicPatch contains operations not representable in JSON
   * (e.g., non-string map keys, temporal deltas, variant operations).
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch] = {
    val builder                    = Chunk.newBuilder[JsonPatchOp]
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < patch.ops.length && error.isEmpty) {
      dynamicPatchOpToJsonPatchOp(patch.ops(idx)) match {
        case Right(jsonOp) => builder.addOne(jsonOp)
        case Left(err)     => error = Some(err)
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(new JsonPatch(builder.result()))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // JsonPatchOp
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A single patch operation: a path and what to do there.
   *
   * @param path
   *   The path to the target location
   * @param op
   *   The operation to perform at that location
   */
  final case class JsonPatchOp(path: DynamicOptic, op: Op)

  // ─────────────────────────────────────────────────────────────────────────
  // Op — Operation sealed trait
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * The operation to perform at a target location.
   *
   * Mirrors [[DynamicPatch.Operation]] but specialized for JSON.
   */
  sealed trait Op

  object Op {

    /**
     * Set a value directly (replacement).
     */
    final case class Set(value: Json) extends Op

    /**
     * Apply a primitive delta operation.
     *
     * Used for numeric deltas and string edits.
     */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Op

    /**
     * Apply array edit operations.
     *
     * Used for inserting, appending, deleting, or modifying array elements.
     */
    final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op

    /**
     * Apply object edit operations.
     *
     * Used for adding, removing, or modifying object fields.
     */
    final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op

    /**
     * Apply a nested patch.
     *
     * Used to group operations sharing a common path prefix.
     */
    final case class Nested(patch: JsonPatch) extends Op
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PrimitiveOp — Delta operations for JSON primitives
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Delta operations for JSON primitive values.
   *
   * JSON has only one numeric type, so we use BigDecimal for deltas. Boolean
   * has no delta (use Set to toggle). Null has no delta (use Set to change).
   */
  sealed trait PrimitiveOp

  object PrimitiveOp {

    /**
     * Add a delta to a JSON number.
     *
     * Applied by: `currentValue + delta`
     */
    final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp

    /**
     * Apply string edit operations.
     */
    final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
  }

  // ─────────────────────────────────────────────────────────────────────────
  // StringOp — Edit operations for strings
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Edit operations for JSON strings.
   *
   * Mirrors [[DynamicPatch.StringOp]] exactly.
   */
  sealed trait StringOp

  object StringOp {

    /** Insert text at the given index. */
    final case class Insert(index: Int, text: java.lang.String) extends StringOp

    /** Delete characters starting at the given index. */
    final case class Delete(index: Int, length: Int) extends StringOp

    /** Append text to the end of the string. */
    final case class Append(text: java.lang.String) extends StringOp

    /** Replace characters starting at index with new text. */
    final case class Modify(index: Int, length: Int, text: java.lang.String) extends StringOp
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ArrayOp — Edit operations for arrays
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Edit operations for JSON arrays.
   *
   * Mirrors [[DynamicPatch.SeqOp]] but with Json values.
   */
  sealed trait ArrayOp

  object ArrayOp {

    /** Insert values at the given index. */
    final case class Insert(index: Int, values: Chunk[Json]) extends ArrayOp

    /** Append values to the end of the array. */
    final case class Append(values: Chunk[Json]) extends ArrayOp

    /** Delete elements starting at the given index. */
    final case class Delete(index: Int, count: Int) extends ArrayOp

    /** Modify the element at the given index with a nested operation. */
    final case class Modify(index: Int, op: Op) extends ArrayOp
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ObjectOp — Edit operations for objects
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Edit operations for JSON objects.
   *
   * Mirrors [[DynamicPatch.MapOp]] but with string keys (JSON constraint).
   */
  sealed trait ObjectOp

  object ObjectOp {

    /** Add a field to the object. */
    final case class Add(key: java.lang.String, value: Json) extends ObjectOp

    /** Remove a field from the object. */
    final case class Remove(key: java.lang.String) extends ObjectOp

    /** Modify a field's value with a nested patch. */
    final case class Modify(key: java.lang.String, patch: JsonPatch) extends ObjectOp
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Apply Operation Implementation
  // ─────────────────────────────────────────────────────────────────────────

  private[json] def applyOp(
    json: Json,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Op,
    mode: PatchMode
  ): Either[SchemaError, Json] =
    if (path.isEmpty) {
      applyOperation(json, operation, mode, Nil)
    } else {
      navigateAndApply(json, path, 0, operation, mode, Nil)
    }

  private def navigateAndApply(
    json: Json,
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
        json match {
          case obj: Json.Object =>
            val fields   = obj.value
            val fieldIdx = indexOfField(fields, name)
            if (fieldIdx < 0) {
              mode match {
                case PatchMode.Strict =>
                  new Left(SchemaError.missingField(trace, name))
                case PatchMode.Lenient =>
                  // Lenient: skip the operation, return json unchanged
                  new Right(json)
                case PatchMode.Clobber =>
                  // Clobber: create the missing field with Json.Null and continue
                  val newTrace = DynamicOptic.Node.Field(name) :: trace
                  if (isLast) {
                    applyOperation(Json.Null, operation, mode, newTrace).map { newFieldValue =>
                      new Json.Object(fields :+ (name, newFieldValue))
                    }
                  } else {
                    navigateAndApply(Json.Null, path, pathIdx + 1, operation, mode, newTrace).map { newFieldValue =>
                      new Json.Object(fields :+ (name, newFieldValue))
                    }
                  }
              }
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace                = DynamicOptic.Node.Field(name) :: trace

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

          case _ =>
            new Left(SchemaError.message(s"Expected Object but got ${json.jsonType}"))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case arr: Json.Array =>
            val elements = arr.value
            if (index < 0 || index >= elements.length) {
              new Left(
                SchemaError.message(
                  s"Index $index out of bounds for array of length ${elements.length}"
                )
              )
            } else {
              val element  = elements(index)
              val newTrace = DynamicOptic.Node.AtIndex(index) :: trace

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

          case _ =>
            new Left(SchemaError.message(s"Expected Array but got ${json.jsonType}"))
        }

      case DynamicOptic.Node.Elements =>
        json match {
          case arr: Json.Array =>
            val elements = arr.value
            val newTrace = DynamicOptic.Node.Elements :: trace

            if (elements.isEmpty) {
              mode match {
                case PatchMode.Strict =>
                  new Left(SchemaError.message("Cannot apply patch to empty array"))
                case _ =>
                  new Right(json)
              }
            } else if (isLast) {
              applyToAllElements(elements, operation, mode, newTrace).map(new Json.Array(_))
            } else {
              navigateAllElements(elements, path, pathIdx + 1, operation, mode, newTrace).map(new Json.Array(_))
            }

          case _ =>
            new Left(SchemaError.message(s"Expected Array but got ${json.jsonType}"))
        }

      case _ =>
        new Left(SchemaError.message(s"Unsupported path node: ${node.getClass.getSimpleName}"))
    }
  }

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
            results(idx) = elements(idx)
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(Chunk.fromArray(results))
    }
  }

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
            results(idx) = elements(idx)
          } else {
            error = Some(err)
          }
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(Chunk.fromArray(results))
    }
  }

  private def applyOperation(
    json: Json,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    operation match {
      case Op.Set(newValue) =>
        new Right(newValue)

      case Op.PrimitiveDelta(op) =>
        applyPrimitiveDelta(json, op)

      case Op.ArrayEdit(arrayOps) =>
        applyArrayEdit(json, arrayOps, mode, trace)

      case Op.ObjectEdit(objectOps) =>
        applyObjectEdit(json, objectOps, mode)

      case Op.Nested(nestedPatch) =>
        nestedPatch.apply(json, mode)
    }

  private def applyPrimitiveDelta(
    json: Json,
    op: PrimitiveOp
  ): Either[SchemaError, Json] =
    (json, op) match {
      case (num: Json.Number, PrimitiveOp.NumberDelta(delta)) =>
        num.toBigDecimalOption match {
          case Some(value) =>
            val result = value + delta
            // Normalize: strip trailing zeros to get canonical representation
            // e.g., "-1.0" becomes "-1", "10.00" becomes "10"
            // Note: We use toPlainString first then normalize manually because
            // java.math.BigDecimal.stripTrailingZeros() behaves differently on Scala Native
            val plainStr   = result.bigDecimal.toPlainString
            val normalized = normalizeNumberString(plainStr)
            new Right(new Json.Number(normalized))
          case None =>
            new Left(SchemaError.message(s"Cannot parse number: ${num.value}"))
        }

      case (str: Json.String, PrimitiveOp.StringEdit(ops)) =>
        applyStringEdits(str.value, ops).map(new Json.String(_))

      case _ =>
        new Left(
          SchemaError.message(
            s"Type mismatch: cannot apply ${op.getClass.getSimpleName} to ${json.jsonType}"
          )
        )
    }

  private def applyStringEdits(
    str: java.lang.String,
    ops: Chunk[StringOp]
  ): Either[SchemaError, java.lang.String] = {
    var result                     = str
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            error = Some(
              SchemaError.message(
                s"String insert index $index out of bounds for string of length ${result.length}"
              )
            )
          } else {
            result = result.substring(0, index) + text + result.substring(index)
          }

        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            error = Some(
              SchemaError.message(
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
              SchemaError.message(
                s"String modify range [$index, ${index + length}) out of bounds for string of length ${result.length}"
              )
            )
          } else {
            result = result.substring(0, index) + text + result.substring(index + length)
          }
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(result)
    }
  }

  /**
   * Normalize a number string by stripping trailing zeros after decimal point.
   * This is a platform-safe alternative to
   * java.math.BigDecimal.stripTrailingZeros() which behaves differently on
   * Scala Native. Examples: "10.00" -> "10", "10.50" -> "10.5", "10" -> "10"
   */
  private def normalizeNumberString(s: java.lang.String): java.lang.String = {
    val dotIdx = s.indexOf('.')
    if (dotIdx < 0) {
      // No decimal point, return as is
      s
    } else {
      // Find last non-zero character after decimal point
      var endIdx = s.length
      while (endIdx > dotIdx + 1 && s.charAt(endIdx - 1) == '0') {
        endIdx -= 1
      }
      // If we're left with just the decimal point, remove it too
      if (endIdx == dotIdx + 1) {
        s.substring(0, dotIdx)
      } else {
        s.substring(0, endIdx)
      }
    }
  }

  private def applyArrayEdit(
    json: Json,
    ops: Chunk[ArrayOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] =
    json match {
      case arr: Json.Array =>
        applyArrayOps(arr.value, ops, mode, trace).map(new Json.Array(_))
      case _ =>
        new Left(SchemaError.message(s"Expected Array but got ${json.jsonType}"))
    }

  private def applyArrayOps(
    elements: Chunk[Json],
    ops: Chunk[ArrayOp],
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
            case PatchMode.Lenient => ()
            case PatchMode.Clobber => ()
          }
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(result)
    }
  }

  private def applyArrayOp(
    elements: Chunk[Json],
    op: ArrayOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[Json]] =
    op match {
      case ArrayOp.Append(values) =>
        new Right(elements ++ values)

      case ArrayOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          mode match {
            case PatchMode.Strict =>
              new Left(
                SchemaError.message(
                  s"Insert index $index out of bounds for array of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              // Lenient: skip the operation, return unchanged
              new Right(elements)
            case PatchMode.Clobber =>
              val clampedIndex    = Math.max(0, Math.min(index, elements.length))
              val (before, after) = elements.splitAt(clampedIndex)
              new Right(before ++ values ++ after)
          }
        } else {
          val (before, after) = elements.splitAt(index)
          new Right(before ++ values ++ after)
        }

      case ArrayOp.Delete(index, count) =>
        if (index < 0 || index + count > elements.length) {
          mode match {
            case PatchMode.Strict =>
              new Left(
                SchemaError.message(
                  s"Delete range [$index, ${index + count}) out of bounds for array of length ${elements.length}"
                )
              )
            case PatchMode.Lenient =>
              // Lenient: skip the operation, return unchanged
              new Right(elements)
            case PatchMode.Clobber =>
              val clampedIndex = Math.max(0, Math.min(index, elements.length))
              val clampedEnd   = Math.max(0, Math.min(index + count, elements.length))
              new Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
          }
        } else {
          new Right(elements.take(index) ++ elements.drop(index + count))
        }

      case ArrayOp.Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length) {
          new Left(
            SchemaError.message(
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

  private def applyObjectEdit(
    json: Json,
    ops: Chunk[ObjectOp],
    mode: PatchMode
  ): Either[SchemaError, Json] =
    json match {
      case obj: Json.Object =>
        applyObjectOps(obj.value, ops, mode).map(new Json.Object(_))
      case _ =>
        new Left(SchemaError.message(s"Expected Object but got ${json.jsonType}"))
    }

  private def applyObjectOps(
    fields: Chunk[(java.lang.String, Json)],
    ops: Chunk[ObjectOp],
    mode: PatchMode
  ): Either[SchemaError, Chunk[(java.lang.String, Json)]] = {
    var result                     = fields
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      applyObjectOp(result, ops(idx), mode) match {
        case Right(updated) => result = updated
        case Left(err)      =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => ()
            case PatchMode.Clobber => ()
          }
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(result)
    }
  }

  private def applyObjectOp(
    fields: Chunk[(java.lang.String, Json)],
    op: ObjectOp,
    mode: PatchMode
  ): Either[SchemaError, Chunk[(java.lang.String, Json)]] =
    op match {
      case ObjectOp.Add(key, value) =>
        val existingIdx = indexOfField(fields, key)
        if (existingIdx >= 0) {
          mode match {
            case PatchMode.Strict =>
              new Left(SchemaError.message(s"Field '$key' already exists in object"))
            case PatchMode.Lenient =>
              // Lenient: skip the operation, return unchanged
              new Right(fields)
            case PatchMode.Clobber =>
              new Right(fields.updated(existingIdx, (key, value)))
          }
        } else {
          new Right(fields :+ (key, value))
        }

      case ObjectOp.Remove(key) =>
        val existingIdx = indexOfField(fields, key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict =>
              new Left(SchemaError.message(s"Field '$key' not found in object"))
            case PatchMode.Lenient =>
              // Lenient: skip the operation, return unchanged
              new Right(fields)
            case PatchMode.Clobber =>
              new Right(fields)
          }
        } else {
          new Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
        }

      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = indexOfField(fields, key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict =>
              new Left(SchemaError.message(s"Field '$key' not found in object"))
            case PatchMode.Lenient =>
              // Lenient: skip the operation, return unchanged
              new Right(fields)
            case PatchMode.Clobber =>
              // Clobber: create the key with Json.Null and apply the nested patch
              nestedPatch.apply(Json.Null, mode).map { newFieldValue =>
                fields :+ (key, newFieldValue)
              }
          }
        } else {
          val (fieldKey, fieldValue) = fields(existingIdx)
          nestedPatch.apply(fieldValue, mode).map { newFieldValue =>
            fields.updated(existingIdx, (fieldKey, newFieldValue))
          }
        }
    }

  private def indexOfField(fields: Chunk[(java.lang.String, Json)], key: java.lang.String): Int = {
    var idx = 0
    while (idx < fields.length) {
      if (fields(idx)._1 == key) return idx
      idx += 1
    }
    -1
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Rendering
  // ─────────────────────────────────────────────────────────────────────────

  private[json] def renderOp(sb: StringBuilder, op: JsonPatchOp, indent: java.lang.String): Unit = {
    val pathStr = op.path.toString
    op.op match {
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

  private def renderPrimitiveDelta(
    sb: StringBuilder,
    pathStr: java.lang.String,
    op: PrimitiveOp,
    indent: java.lang.String
  ): Unit =
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

  private def renderArrayOp(sb: StringBuilder, op: ArrayOp, indent: java.lang.String): Unit =
    op match {
      case ArrayOp.Insert(index, values) =>
        var i = 0
        while (i < values.length) {
          sb.append(indent).append("+ [").append(index + i).append(": ").append(values(i)).append("]\n")
          i += 1
        }
      case ArrayOp.Append(values) =>
        var i = 0
        while (i < values.length) {
          sb.append(indent).append("+ ").append(values(i)).append("\n")
          i += 1
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
            renderOp(sb, new JsonPatchOp(DynamicOptic.root, nestedOp), indent + "  ")
        }
    }

  private def renderObjectOp(sb: StringBuilder, op: ObjectOp, indent: java.lang.String): Unit =
    op match {
      case ObjectOp.Add(k, v) =>
        sb.append(indent).append("+ {").append(escapeString(k)).append(": ").append(v).append("}\n")
      case ObjectOp.Remove(k) =>
        sb.append(indent).append("- {").append(escapeString(k)).append("}\n")
      case ObjectOp.Modify(k, patch) =>
        sb.append(indent).append("~ {").append(escapeString(k)).append("}:\n")
        patch.ops.foreach(op => renderOp(sb, op, indent + "  "))
    }

  private def escapeString(s: java.lang.String): java.lang.String = {
    val sb = new StringBuilder("\"")
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'          => sb.append("\\\"")
        case '\\'         => sb.append("\\\\")
        case '\b'         => sb.append("\\b")
        case '\f'         => sb.append("\\f")
        case '\n'         => sb.append("\\n")
        case '\r'         => sb.append("\\r")
        case '\t'         => sb.append("\\t")
        case c if c < ' ' => sb.append(f"\\u${c.toInt}%04x")
        case c            => sb.append(c)
      }
      i += 1
    }
    sb.append("\"")
    sb.toString
  }

  // ─────────────────────────────────────────────────────────────────────────
  // DynamicPatch Conversion
  // ─────────────────────────────────────────────────────────────────────────

  private def jsonPatchOpToDynamicPatchOp(jop: JsonPatchOp): DynamicPatch.DynamicPatchOp =
    new DynamicPatch.DynamicPatchOp(jop.path, opToDynamicOperation(jop.op))

  private def opToDynamicOperation(op: Op): DynamicPatch.Operation =
    op match {
      case Op.Set(value) =>
        DynamicPatch.Operation.Set(value.toDynamicValue)

      case Op.PrimitiveDelta(primitiveOp) =>
        primitiveOp match {
          case PrimitiveOp.NumberDelta(delta) =>
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(delta))
          case PrimitiveOp.StringEdit(ops) =>
            DynamicPatch.Operation.PrimitiveDelta(
              DynamicPatch.PrimitiveOp.StringEdit(ops.toVector.map(stringOpToDynamic))
            )
        }

      case Op.ArrayEdit(ops) =>
        DynamicPatch.Operation.SequenceEdit(ops.toVector.map(arrayOpToDynamic))

      case Op.ObjectEdit(ops) =>
        DynamicPatch.Operation.MapEdit(ops.toVector.map(objectOpToDynamic))

      case Op.Nested(patch) =>
        DynamicPatch.Operation.Patch(patch.toDynamicPatch)
    }

  private def stringOpToDynamic(op: StringOp): DynamicPatch.StringOp =
    op match {
      case StringOp.Insert(idx, text)      => DynamicPatch.StringOp.Insert(idx, text)
      case StringOp.Delete(idx, len)       => DynamicPatch.StringOp.Delete(idx, len)
      case StringOp.Append(text)           => DynamicPatch.StringOp.Append(text)
      case StringOp.Modify(idx, len, text) => DynamicPatch.StringOp.Modify(idx, len, text)
    }

  private def arrayOpToDynamic(op: ArrayOp): DynamicPatch.SeqOp =
    op match {
      case ArrayOp.Insert(idx, values) =>
        DynamicPatch.SeqOp.Insert(idx, Chunk.fromIterable(values.map(_.toDynamicValue)))
      case ArrayOp.Append(values) =>
        DynamicPatch.SeqOp.Append(Chunk.fromIterable(values.map(_.toDynamicValue)))
      case ArrayOp.Delete(idx, count) =>
        DynamicPatch.SeqOp.Delete(idx, count)
      case ArrayOp.Modify(idx, nestedOp) =>
        DynamicPatch.SeqOp.Modify(idx, opToDynamicOperation(nestedOp))
    }

  private def objectOpToDynamic(op: ObjectOp): DynamicPatch.MapOp =
    op match {
      case ObjectOp.Add(key, value) =>
        DynamicPatch.MapOp.Add(DynamicValue.Primitive(PrimitiveValue.String(key)), value.toDynamicValue)
      case ObjectOp.Remove(key) =>
        DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(key)))
      case ObjectOp.Modify(key, patch) =>
        DynamicPatch.MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String(key)), patch.toDynamicPatch)
    }

  private def dynamicPatchOpToJsonPatchOp(dop: DynamicPatch.DynamicPatchOp): Either[SchemaError, JsonPatchOp] =
    dynamicOperationToOp(dop.operation).map(op => new JsonPatchOp(dop.path, op))

  private def dynamicOperationToOp(dop: DynamicPatch.Operation): Either[SchemaError, Op] =
    dop match {
      case DynamicPatch.Operation.Set(value) =>
        new Right(Op.Set(Json.fromDynamicValue(value)))

      case DynamicPatch.Operation.PrimitiveDelta(primitiveOp) =>
        dynamicPrimitiveOpToJsonPrimitiveOp(primitiveOp).map(Op.PrimitiveDelta(_))

      case DynamicPatch.Operation.SequenceEdit(ops) =>
        sequenceOpsToArrayOps(ops).map(Op.ArrayEdit(_))

      case DynamicPatch.Operation.MapEdit(ops) =>
        mapOpsToObjectOps(ops).map(Op.ObjectEdit(_))

      case DynamicPatch.Operation.Patch(patch) =>
        fromDynamicPatch(patch).map(Op.Nested(_))
    }

  private def dynamicPrimitiveOpToJsonPrimitiveOp(op: DynamicPatch.PrimitiveOp): Either[SchemaError, PrimitiveOp] =
    op match {
      case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(delta))
      case DynamicPatch.PrimitiveOp.IntDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.LongDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.DoubleDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.FloatDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble)))
      case DynamicPatch.PrimitiveOp.ShortDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.ByteDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.BigIntDelta(delta) =>
        new Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.StringEdit(ops) =>
        new Right(PrimitiveOp.StringEdit(Chunk.fromIterable(ops.map(dynamicStringOpToJsonStringOp))))
      case _ =>
        new Left(SchemaError.message(s"Cannot convert ${op.getClass.getSimpleName} to JSON primitive operation"))
    }

  private def dynamicStringOpToJsonStringOp(op: DynamicPatch.StringOp): StringOp =
    op match {
      case DynamicPatch.StringOp.Insert(idx, text)      => StringOp.Insert(idx, text)
      case DynamicPatch.StringOp.Delete(idx, len)       => StringOp.Delete(idx, len)
      case DynamicPatch.StringOp.Append(text)           => StringOp.Append(text)
      case DynamicPatch.StringOp.Modify(idx, len, text) => StringOp.Modify(idx, len, text)
    }

  private def sequenceOpsToArrayOps(ops: Vector[DynamicPatch.SeqOp]): Either[SchemaError, Chunk[ArrayOp]] = {
    val builder                    = Chunk.newBuilder[ArrayOp]
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      dynamicSeqOpToArrayOp(ops(idx)) match {
        case Right(arrayOp) => builder.addOne(arrayOp)
        case Left(err)      => error = Some(err)
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(builder.result())
    }
  }

  private def dynamicSeqOpToArrayOp(op: DynamicPatch.SeqOp): Either[SchemaError, ArrayOp] =
    op match {
      case DynamicPatch.SeqOp.Insert(idx, values) =>
        dynamicValuesToJson(values).map(vs => ArrayOp.Insert(idx, vs))
      case DynamicPatch.SeqOp.Append(values) =>
        dynamicValuesToJson(values).map(vs => ArrayOp.Append(vs))
      case DynamicPatch.SeqOp.Delete(idx, count) =>
        new Right(ArrayOp.Delete(idx, count))
      case DynamicPatch.SeqOp.Modify(idx, nestedOp) =>
        dynamicOperationToOp(nestedOp).map(op => ArrayOp.Modify(idx, op))
    }

  private def mapOpsToObjectOps(ops: Vector[DynamicPatch.MapOp]): Either[SchemaError, Chunk[ObjectOp]] = {
    val builder                    = Chunk.newBuilder[ObjectOp]
    var idx                        = 0
    var error: Option[SchemaError] = None

    while (idx < ops.length && error.isEmpty) {
      dynamicMapOpToObjectOp(ops(idx)) match {
        case Right(objectOp) => builder.addOne(objectOp)
        case Left(err)       => error = Some(err)
      }
      idx += 1
    }

    error match {
      case Some(err) => new Left(err)
      case None      => new Right(builder.result())
    }
  }

  private def dynamicMapOpToObjectOp(op: DynamicPatch.MapOp): Either[SchemaError, ObjectOp] =
    op match {
      case DynamicPatch.MapOp.Add(key, value) =>
        extractStringKey(key).map { k =>
          ObjectOp.Add(k, Json.fromDynamicValue(value))
        }
      case DynamicPatch.MapOp.Remove(key) =>
        extractStringKey(key).map(k => ObjectOp.Remove(k))
      case DynamicPatch.MapOp.Modify(key, patch) =>
        extractStringKey(key).flatMap { k =>
          fromDynamicPatch(patch).map(p => ObjectOp.Modify(k, p))
        }
    }

  private def extractStringKey(dv: DynamicValue): Either[SchemaError, java.lang.String] =
    dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => new Right(s)
      case _                                                => new Left(SchemaError.message("JSON object keys must be strings"))
    }

  private def dynamicValuesToJson(values: Chunk[DynamicValue]): Either[SchemaError, Chunk[Json]] = {
    val builder = Chunk.newBuilder[Json]
    var idx     = 0

    while (idx < values.length) {
      builder.addOne(Json.fromDynamicValue(values(idx)))
      idx += 1
    }

    new Right(builder.result())
  }
}
