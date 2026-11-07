/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.blocks.typeid.TypeId
import java.lang

/**
 * A list of changes to a [[Json]] value, each an [[JsonPatch.Op]] at a
 * [[zio.blocks.schema.DynamicOptic]] path. Compute one with [[JsonPatch.diff]]
 * or build one by hand, combine patches with `++`, and apply one with [[apply]]
 * in a [[zio.blocks.schema.patch.PatchMode]]. A patch is data: its schema
 * stores it in any format, and [[toDynamicPatch]] converts it for typed values.
 * It is not RFC 6902 JSON Patch.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * val v1    = Json.parseUnsafe("""{"title":"Zionomicon","year":2021}""")
 * val v2    = Json.parseUnsafe("""{"title":"Zionomicon","year":2022,"edition":2}""")
 * val patch = JsonPatch.diff(v1, v2)
 *
 * patch.apply(v1) == Right(v2) // true
 * patch.toString
 * // JsonPatch {
 * //   root:
 * //     ~ {"year"}:
 * //       root += 1
 * //     + {"edition": 2}
 * // }
 * }}}
 *
 * @param ops
 *   the operations, applied in order
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/json/json-patch/ JsonPatch]]
 */
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {

  /** Returns a patch that applies this patch, then `that`. */
  def ++(that: JsonPatch): JsonPatch = new JsonPatch(ops ++ that.ops)

  /**
   * Returns `true` if this patch has no operations, so applying it changes
   * nothing.
   */
  def isEmpty: Boolean = ops.isEmpty

  /**
   * Applies the operations to `value` in order. In `Strict` mode (the default)
   * the first failure returns `Left`; in `Lenient` and `Clobber` mode a failing
   * operation is skipped, so the result is always `Right`.
   *
   * A path follows field and index steps, `[*]` (every element; an empty array
   * fails in `Strict` mode only), wrapper steps and schema searches (every
   * match; none fails in `Strict` mode). Any other step fails. At the target:
   *   - `Set` replaces the value, and `NumberDelta` adds to a number;
   *   - a `StringEdit` fails as a whole, in every mode, when one of its steps
   *     is out of range;
   *   - in an `ArrayEdit` or `ObjectEdit`, a step that fails is skipped outside
   *     `Strict` mode and the others still apply. `Clobber` also clamps an
   *     out-of-range array `Insert` or `Delete`, replaces the value on an `Add`
   *     of an existing key, and ignores a `Remove` of a missing one. A `Modify`
   *     of a missing index or key fails in every mode.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   * import zio.blocks.schema.json._
   * import zio.blocks.schema.json.JsonPatch._
   * import zio.blocks.schema.patch.PatchMode
   *
   * val book   = Json.Object("title" -> Json.String("Zionomicon"))
   * val rename = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("title", Json.String("ZIO")))))
   *
   * rename(book)                                 // Left(zio.blocks.schema.SchemaError: Key 'title' already exists in object at: .)
   * rename(book, PatchMode.Lenient).map(_.print) // Right({"title":"Zionomicon"})
   * rename(book, PatchMode.Clobber).map(_.print) // Right({"title":"ZIO"})
   * }}}
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
   * Converts this patch to a [[zio.blocks.schema.patch.DynamicPatch]]: values
   * become dynamic values, a `NumberDelta` a `BigDecimalDelta`, an `ObjectEdit`
   * a map edit with string keys, and a `Nested` patch a nested one.
   * [[JsonPatch.fromDynamicPatch]] converts it back.
   */
  def toDynamicPatch: DynamicPatch = new DynamicPatch(ops.map { op =>
    new DynamicPatch.DynamicPatchOp(op.path, JsonPatch.opToDynamicOperation(op.operation))
  })

  /**
   * Prints the operations one per line: `= value` for a set, `+=` or `-=` for a
   * number delta, and `+`, `-` and `~` for added, removed and changed elements,
   * fields and characters. Paths print only their field and index steps.
   */
  override def toString: String =
    if (ops.isEmpty) "JsonPatch {}"
    else {
      val sb = new lang.StringBuilder("JsonPatch {\n")
      ops.foreach(op => JsonPatch.renderOp(sb, op, 1))
      sb.append('}').toString
    }
}

/** Constructors, operation types and schemas of [[JsonPatch]]. */
object JsonPatch {

  /** The patch with no operations, the identity of `++`. */
  val empty: JsonPatch = new JsonPatch(Chunk.empty)

  /**
   * Converts a [[zio.blocks.schema.patch.DynamicPatch]] to a `JsonPatch`. Every
   * numeric delta becomes a `NumberDelta`, values become JSON as
   * [[Json.fromDynamicValue]] makes them, and map edits become object edits.
   * Returns `Left` for a temporal delta or a map key that is not a string.
   * Paths are kept as they are, so a path step that a `JsonPatch` cannot follow
   * fails only when the patch is applied.
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch] = {
    val ops     = patch.ops
    val len     = ops.length
    val results = new Array[JsonPatchOp](len)
    var idx     = 0
    while (idx < len) {
      val dynOp = ops(idx)
      operationFromDynamic(dynOp.operation) match {
        case Right(op) => results(idx) = new JsonPatchOp(dynOp.path, op)
        case l         => return l.asInstanceOf[Either[SchemaError, JsonPatch]]
      }
      idx += 1
    }
    new Right(new JsonPatch(Chunk.fromArray(results)))
  }

  /**
   * Returns a patch that turns `source` into `target`, as [[JsonDiffer.diff]]
   * does: a `NumberDelta` for numbers, a `StringEdit` when shorter than the new
   * string, an LCS-based `ArrayEdit` for arrays, a per-field `ObjectEdit` for
   * objects, and a `Set` when the kinds differ.
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
            if (fieldIdx < 0) new Left(SchemaError.missingField(trace, name))
            else {
              val kv     = fields(fieldIdx)
              val trace_ = f :: trace
              (if (isLast) applyOperation(kv._2, operation, mode, trace_)
               else navigateAndApply(kv._2, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(v) => new Right(new Json.Object(fields.updated(fieldIdx, (kv._1, v))))
                case l        => l
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
              val msg = s"Index $index out of bounds for array of length ${elements.length}"
              new Left(SchemaError.expectationMismatch(trace, msg))
            } else {
              val element = elements(index)
              val trace_  = ai :: trace
              (if (isLast) applyOperation(element, operation, mode, trace_)
               else navigateAndApply(element, path, pathIdx + 1, operation, mode, trace_)) match {
                case Right(e) => new Right(new Json.Array(elements.updated(index, e)))
                case l        => l
              }
            }
          case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }
      case e: DynamicOptic.Node.Elements.type =>
        value match {
          case arr: Json.Array =>
            val elements = arr.value
            val trace_   = e :: trace
            if (elements.isEmpty) {
              if (mode ne PatchMode.Strict) new Right(value)
              else new Left(SchemaError.expectationMismatch(trace_, "encountered an empty array"))
            } else if (isLast) applyToAllElements(elements, operation, mode, trace_)
            else navigateAllElements(elements, path, pathIdx + 1, operation, mode, trace_)
          case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
        }
      case _: DynamicOptic.Node.Case =>
        new Left(SchemaError.expectationMismatch(trace, "Case navigation not supported for JSON values"))
      case w: DynamicOptic.Node.Wrapped.type =>
        val trace_ = w :: trace
        if (isLast) applyOperation(value, operation, mode, trace_)
        else navigateAndApply(value, path, pathIdx + 1, operation, mode, trace_)
      case _: DynamicOptic.Node.AtMapKey =>
        val msg = "AtMapKey not supported for JSON - use Field navigation for objects"
        new Left(SchemaError.expectationMismatch(trace, msg))
      case _: DynamicOptic.Node.AtIndices =>
        new Left(SchemaError.expectationMismatch(trace, "AtIndices not supported in patches"))
      case _: DynamicOptic.Node.AtMapKeys =>
        new Left(SchemaError.expectationMismatch(trace, "AtMapKeys not supported in patches"))
      case _: DynamicOptic.Node.MapKeys.type =>
        new Left(SchemaError.expectationMismatch(trace, "MapKeys not supported in patches"))
      case _: DynamicOptic.Node.MapValues.type =>
        new Left(SchemaError.expectationMismatch(trace, "MapValues not supported in patches"))
      case _: DynamicOptic.Node.TypeSearch =>
        val msg = "TypeSearch requires Schema context, not supported for JSON patches"
        new Left(SchemaError.expectationMismatch(trace, msg))
      case DynamicOptic.Node.SchemaSearch(pattern) =>
        val trace_ = node :: trace
        if (isLast) schemaSearchApplyOperationJson(value, pattern, operation, mode, trace_)
        else schemaSearchNavigateJson(value, pattern, path, pathIdx + 1, operation, mode, trace_)
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
      var element = elements(idx)
      applyOperation(element, operation, mode, new DynamicOptic.Node.AtIndex(idx) :: trace) match {
        case Right(e) => element = e
        case l        => if (mode eq PatchMode.Strict) return l
      }
      results(idx) = element
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
      var element = elements(idx)
      navigateAndApply(element, path, pathIdx, operation, mode, new DynamicOptic.Node.AtIndex(idx) :: trace) match {
        case Right(e) => element = e
        case l        => if (mode eq PatchMode.Strict) return l
      }
      results(idx) = element
      idx += 1
    }
    new Right(new Json.Array(Chunk.fromArray(results)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // SchemaSearch Helper Functions
  // ─────────────────────────────────────────────────────────────────────────

  private[this] def schemaSearchApplyOperationJson(
    value: Json,
    pattern: SchemaRepr,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var found                    = false
    var globalError: SchemaError = null
    val result                   = Json.iterativeTransform(value) { json =>
      if ((globalError ne null) && mode == PatchMode.Strict) json
      else if (JsonMatch.matches(pattern, json)) {
        applyOperation(json, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            mode match {
              case PatchMode.Strict =>
                if (globalError eq null) globalError = err
                json
              case _ => json
            }
        }
      } else json
    }
    if (globalError ne null) new Left(globalError)
    else if (!found && mode == PatchMode.Strict) {
      new Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
    } else new Right(result)
  }

  private[this] def schemaSearchNavigateJson(
    value: Json,
    pattern: SchemaRepr,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Json] = {
    var found                    = false
    var globalError: SchemaError = null
    val result                   = Json.iterativeTransform(value) { json =>
      if ((globalError ne null) && mode == PatchMode.Strict) json
      else if (JsonMatch.matches(pattern, json)) {
        navigateAndApply(json, path, pathIdx, operation, mode, trace) match {
          case Right(modified) =>
            found = true
            modified
          case Left(err) =>
            mode match {
              case PatchMode.Strict =>
                if (globalError eq null) globalError = err
                json
              case _ => json
            }
        }
      } else json
    }
    if (globalError ne null) new Left(globalError)
    else if (!found && mode == PatchMode.Strict) {
      new Left(SchemaError.expectationMismatch(trace, "No values matched the SchemaSearch pattern"))
    } else new Right(result)
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
    case s: Op.Set             => new Right(s.value)
    case pd: Op.PrimitiveDelta =>
      pd.op match {
        case nd: PrimitiveOp.NumberDelta =>
          value match {
            case n: Json.Number => new Right(new Json.Number(n.value + nd.delta))
            case _              => new Left(SchemaError.expectationMismatch(trace, s"Expected Number but got ${value.jsonType}"))
          }
        case se: PrimitiveOp.StringEdit =>
          value match {
            case s: Json.String => applyStringOps(s.value, se.ops, trace)
            case _              => new Left(SchemaError.expectationMismatch(trace, s"Expected String but got ${value.jsonType}"))
          }
      }
    case ae: Op.ArrayEdit =>
      value match {
        case arr: Json.Array =>
          var result = arr.value
          val len    = ae.ops.length
          var idx    = 0
          while (idx < len) {
            applyArrayOp(result, ae.ops(idx), mode, trace) match {
              case Right(es) => result = es
              case l         => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, Json]]
            }
            idx += 1
          }
          new Right(new Json.Array(result))
        case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Array but got ${value.jsonType}"))
      }
    case oe: Op.ObjectEdit =>
      value match {
        case obj: Json.Object =>
          var result = obj.value
          val len    = oe.ops.length
          var idx    = 0
          while (idx < len) {
            applyObjectOp(result, oe.ops(idx), mode, trace) match {
              case Right(fs) => result = fs
              case l         => if (mode eq PatchMode.Strict) return l.asInstanceOf[Either[SchemaError, Json]]
            }
            idx += 1
          }
          new Right(new Json.Object(result))
        case _ => new Left(SchemaError.expectationMismatch(trace, s"Expected Object but got ${value.jsonType}"))
      }
    case n: Op.Nested => n.patch.apply(value, mode)
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
        case i: StringOp.Insert =>
          val index = i.index
          if (index < 0 || index > result.length) {
            val msg = s"String insert index $index out of bounds for string of length ${result.length}"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + i.text + result.substring(index)
        case d: StringOp.Delete =>
          val index = d.index
          val limit = index + d.length
          if (index < 0 || limit > result.length) {
            val msg = s"String delete range [$index, $limit) out of bounds for string of length ${result.length}"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + result.substring(limit)
        case a: StringOp.Append => result = result + a.text
        case m: StringOp.Modify =>
          val index = m.index
          val limit = index + m.length
          if (index < 0 || limit > result.length) {
            val msg = s"String modify range [$index, $limit) out of bounds for string of length ${result.length}"
            return new Left(SchemaError.expectationMismatch(trace, msg))
          } else result = result.substring(0, index) + m.text + result.substring(limit)
      }
      idx += 1
    }
    new Right(new Json.String(result))
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
      case a: ArrayOp.Append => new Right(elements ++ a.values)
      case i: ArrayOp.Insert =>
        val index = i.index
        val len   = elements.length
        if ((index < 0 || index > len) && (mode ne PatchMode.Clobber)) {
          val msg = s"Insert index $index out of bounds for array of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        } else new Right(elements.take(index) ++ i.values ++ elements.drop(index))
      case d: ArrayOp.Delete =>
        val index = d.index
        val limit = index + d.count
        val len   = elements.length
        if ((index < 0 || limit > len) && (mode ne PatchMode.Clobber)) {
          val msg = s"Delete range [$index, $limit) out of bounds for array of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        } else new Right(elements.take(index) ++ elements.drop(limit))
      case m: ArrayOp.Modify =>
        val index = m.index
        val len   = elements.length
        if (index < 0 || index >= len) {
          val msg = s"Modify index $index out of bounds for array of length $len"
          new Left(SchemaError.expectationMismatch(trace, msg))
        } else {
          applyOperation(elements(index), m.op, mode, new DynamicOptic.Node.AtIndex(index) :: trace) match {
            case Right(v) => new Right(elements.updated(index, v))
            case l        => l.asInstanceOf[Either[SchemaError, Chunk[Json]]]
          }
        }
    }

  /**
   * Apply a single object operation.
   */
  private[this] def applyObjectOp(
    fields: Chunk[(String, Json)],
    op: ObjectOp,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, Chunk[(String, Json)]] = op match {
    case a: ObjectOp.Add =>
      val key         = a.key
      val value       = a.value
      val existingIdx = fields.indexWhere(_._1 == key)
      if (existingIdx >= 0) {
        if (mode eq PatchMode.Clobber) new Right(fields.updated(existingIdx, (key, value)))
        else new Left(SchemaError.expectationMismatch(trace, s"Key '$key' already exists in object"))
      } else new Right(fields.appended((key, value)))
    case r: ObjectOp.Remove =>
      val key         = r.key
      val existingIdx = fields.indexWhere(_._1 == key)
      if (existingIdx < 0) {
        if (mode eq PatchMode.Clobber) new Right(fields)
        else new Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
      } else new Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
    case m: ObjectOp.Modify =>
      val key         = m.key
      val existingIdx = fields.indexWhere(_._1 == key)
      if (existingIdx < 0) new Left(SchemaError.expectationMismatch(trace, s"Key '$key' not found in object"))
      else {
        val kv = fields(existingIdx)
        m.patch.apply(kv._2, mode) match {
          case Right(v) => new Right(fields.updated(existingIdx, (kv._1, v)))
          case l        => l.asInstanceOf[Either[SchemaError, Chunk[(String, Json)]]]
        }
      }
  }

  /**
   * Returns a patch of one operation at the root.
   *
   * {{{
   * import zio.blocks.schema.json._
   * import zio.blocks.schema.json.JsonPatch._
   *
   * JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))).apply(Json.Number(2021))
   * // Right(2022)
   * }}}
   */
  def root(operation: Op): JsonPatch = new JsonPatch(Chunk.single(new JsonPatchOp(DynamicOptic.root, operation)))

  /** Returns a patch of one operation at `path`. */
  def apply(path: DynamicOptic, operation: Op): JsonPatch =
    new JsonPatch(Chunk.single(new JsonPatchOp(path, operation)))

  private def renderOp(sb: lang.StringBuilder, op: JsonPatchOp, indent: Int): Unit = {
    appendIndent(sb, indent)
    appendPath(sb, op.path.nodes)
    op.operation match {
      case s: Op.Set =>
        sb.append(" = ").append(s.value).append('\n')
      case pd: Op.PrimitiveDelta =>
        renderPrimitiveDelta(sb, pd.op, indent)
      case ae: Op.ArrayEdit =>
        sb.append(":\n")
        ae.ops.foreach(ao => renderArrayOp(sb, ao, indent + 1))
      case oe: Op.ObjectEdit =>
        sb.append(":\n")
        oe.ops.foreach(oo => renderObjectOp(sb, oo, indent + 1))
      case n: Op.Nested =>
        sb.append(":\n")
        n.patch.ops.foreach(op => renderOp(sb, op, indent + 1))
    }
  }

  private[this] def appendPath(sb: lang.StringBuilder, nodes: IndexedSeq[DynamicOptic.Node]): Unit =
    if (nodes.isEmpty) sb.append("root")
    else {
      nodes.foreach {
        case f: DynamicOptic.Node.Field    => sb.append('.').append(f.name)
        case ai: DynamicOptic.Node.AtIndex => sb.append('[').append(ai.index).append(']')
        case _                             =>
      }
    }

  private[this] def renderPrimitiveDelta(sb: lang.StringBuilder, op: PrimitiveOp, indent: Int): Unit = {
    op match {
      case nd: PrimitiveOp.NumberDelta =>
        val d = nd.delta
        if (d >= BigDecimal(0)) sb.append(" += ").append(d)
        else sb.append(" -= ").append(-d)
      case se: PrimitiveOp.StringEdit =>
        sb.append(":\n")
        se.ops.foreach {
          var idx = 0
          op =>
            if (idx > 0) sb.append('\n')
            idx += 1
            appendIndent(sb, indent)
            op match {
              case i: StringOp.Insert =>
                sb.append("  + [").append(i.index).append(": ")
                escapeString(sb, i.text)
                sb.append(']')
              case d: StringOp.Delete =>
                sb.append("  - [").append(d.index).append(", ").append(d.length).append(']')
              case a: StringOp.Append =>
                sb.append("  + ")
                escapeString(sb, a.text)
              case m: StringOp.Modify =>
                sb.append("  ~ [").append(m.index).append(", ").append(m.length).append(": ")
                escapeString(sb, m.text)
                sb.append(']')
            }
        }
    }
    sb.append('\n')
  }

  private[this] def renderArrayOp(sb: lang.StringBuilder, op: ArrayOp, indent: Int): Unit = op match {
    case i: ArrayOp.Insert =>
      i.values.foreach {
        var idx = i.index
        v =>
          appendIndent(sb, indent)
          sb.append("+ [").append(idx).append(": ").append(v).append("]\n")
          idx += 1
      }
    case a: ArrayOp.Append =>
      a.values.foreach { v =>
        appendIndent(sb, indent)
        sb.append("+ ").append(v).append('\n')
      }
    case d: ArrayOp.Delete =>
      appendIndent(sb, indent)
      val index = d.index
      val count = d.count
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
    case m: ArrayOp.Modify =>
      appendIndent(sb, indent)
      val op    = m.op
      val index = m.index
      op match {
        case s: Op.Set => sb.append("~ [").append(index).append(": ").append(s.value).append("]\n")
        case _         =>
          sb.append("~ [").append(index).append("]:\n")
          renderOp(sb, new JsonPatchOp(DynamicOptic.root, op), indent + 1)
      }
  }

  private[this] def renderObjectOp(sb: lang.StringBuilder, op: ObjectOp, indent: Int): Unit = {
    appendIndent(sb, indent)
    op match {
      case a: ObjectOp.Add =>
        sb.append("+ {")
        escapeString(sb, a.key)
        sb.append(": ").append(a.value).append("}\n")
      case r: ObjectOp.Remove =>
        sb.append("- {")
        escapeString(sb, r.key)
        sb.append("}\n")
      case m: ObjectOp.Modify =>
        sb.append("~ {")
        escapeString(sb, m.key)
        sb.append("}:\n")
        m.patch.ops.foreach(op => renderOp(sb, op, indent + 1))
    }
  }

  private[this] def escapeString(sb: lang.StringBuilder, s: String): Unit = {
    sb.append('"')
    val len = s.length
    var idx = 0
    while (idx < len) {
      val ch = s.charAt(idx)
      idx += 1
      if (ch >= ' ' && ch != '"' && ch != '\\') sb.append(ch)
      else {
        sb.append('\\')
        ch match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '\b' => sb.append('b')
          case '\f' => sb.append('f')
          case '\n' => sb.append('n')
          case '\r' => sb.append('r')
          case '\t' => sb.append('t')
          case _    =>
            sb.append('u')
              .append(hexDigit((ch >> 12) & 0xf))
              .append(hexDigit((ch >> 8) & 0xf))
              .append(hexDigit((ch >> 4) & 0xf))
              .append(hexDigit(ch & 0xf))
        }
      }
    }
    sb.append('"')
  }

  private[this] def hexDigit(n: Int): Char = (n + (if (n < 10) 48 else 87)).toChar

  private[this] def appendIndent(sb: lang.StringBuilder, indent: Int): Unit = {
    var idx = indent
    while (idx > 0) {
      sb.append(' ').append(' ')
      idx -= 1
    }
  }

  // DynamicPatch Conversion Helpers

  /**
   * Converts a JsonPatch.Op to a DynamicPatch.Operation.
   */
  private def opToDynamicOperation(op: Op): DynamicPatch.Operation = op match {
    case s: Op.Set             => new DynamicPatch.Operation.Set(s.value.toDynamicValue)
    case pd: Op.PrimitiveDelta => new DynamicPatch.Operation.PrimitiveDelta(primitiveOpToDynamic(pd.op))
    case ae: Op.ArrayEdit      => new DynamicPatch.Operation.SequenceEdit(ae.ops.map(arrayOpToDynamic))
    case oe: Op.ObjectEdit     => new DynamicPatch.Operation.MapEdit(oe.ops.map(objectOpToDynamic))
    case n: Op.Nested          => new DynamicPatch.Operation.Patch(n.patch.toDynamicPatch)
  }

  private[this] def primitiveOpToDynamic(op: PrimitiveOp): DynamicPatch.PrimitiveOp = op match {
    case nd: PrimitiveOp.NumberDelta => new DynamicPatch.PrimitiveOp.BigDecimalDelta(nd.delta)
    case se: PrimitiveOp.StringEdit  => new DynamicPatch.PrimitiveOp.StringEdit(se.ops.map(stringOpToDynamic))
  }

  private[this] def stringOpToDynamic(op: StringOp): DynamicPatch.StringOp = op match {
    case i: StringOp.Insert => new DynamicPatch.StringOp.Insert(i.index, i.text)
    case d: StringOp.Delete => new DynamicPatch.StringOp.Delete(d.index, d.length)
    case a: StringOp.Append => new DynamicPatch.StringOp.Append(a.text)
    case m: StringOp.Modify => new DynamicPatch.StringOp.Modify(m.index, m.length, m.text)
  }

  private[this] def arrayOpToDynamic(op: ArrayOp): DynamicPatch.SeqOp = op match {
    case i: ArrayOp.Insert => new DynamicPatch.SeqOp.Insert(i.index, i.values.map(_.toDynamicValue))
    case a: ArrayOp.Append => new DynamicPatch.SeqOp.Append(a.values.map(_.toDynamicValue))
    case d: ArrayOp.Delete => new DynamicPatch.SeqOp.Delete(d.index, d.count)
    case m: ArrayOp.Modify => new DynamicPatch.SeqOp.Modify(m.index, opToDynamicOperation(m.op))
  }

  private[this] def objectOpToDynamic(op: ObjectOp): DynamicPatch.MapOp = op match {
    case a: ObjectOp.Add    => new DynamicPatch.MapOp.Add(DynamicValue.string(a.key), a.value.toDynamicValue)
    case r: ObjectOp.Remove => new DynamicPatch.MapOp.Remove(DynamicValue.string(r.key))
    case m: ObjectOp.Modify => new DynamicPatch.MapOp.Modify(DynamicValue.string(m.key), m.patch.toDynamicPatch)
  }

  /**
   * Converts a DynamicPatch.Operation to a JsonPatch.Op.
   */
  private[this] def operationFromDynamic(op: DynamicPatch.Operation): Either[SchemaError, Op] = op match {
    case s: DynamicPatch.Operation.Set             => new Right(new Op.Set(Json.fromDynamicValue(s.value)))
    case pd: DynamicPatch.Operation.PrimitiveDelta =>
      primitiveOpFromDynamic(pd.op) match {
        case Right(op) => new Right(new Op.PrimitiveDelta(op))
        case l         => l.asInstanceOf[Either[SchemaError, Op]]
      }
    case se: DynamicPatch.Operation.SequenceEdit =>
      sequenceAll(se.ops.map(seqOpFromDynamic)) match {
        case Right(ops) => new Right(new Op.ArrayEdit(ops))
        case l          => l.asInstanceOf[Either[SchemaError, Op]]
      }
    case me: DynamicPatch.Operation.MapEdit =>
      sequenceAll(me.ops.map(mapOpFromDynamic)) match {
        case Right(ops) => new Right(new Op.ObjectEdit(ops))
        case l          => l.asInstanceOf[Either[SchemaError, Op]]
      }
    case p: DynamicPatch.Operation.Patch =>
      fromDynamicPatch(p.patch) match {
        case Right(patch) => new Right(new Op.Nested(patch))
        case l            => l.asInstanceOf[Either[SchemaError, Op]]
      }
  }

  private[this] def primitiveOpFromDynamic(op: DynamicPatch.PrimitiveOp): Either[SchemaError, PrimitiveOp] = new Right(
    op match {
      case d: DynamicPatch.PrimitiveOp.IntDelta        => new PrimitiveOp.NumberDelta(BigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.LongDelta       => new PrimitiveOp.NumberDelta(BigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.ShortDelta      => new PrimitiveOp.NumberDelta(BigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.ByteDelta       => new PrimitiveOp.NumberDelta(BigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.FloatDelta      => new PrimitiveOp.NumberDelta(JsonWriter.toBigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.DoubleDelta     => new PrimitiveOp.NumberDelta(JsonWriter.toBigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.BigIntDelta     => new PrimitiveOp.NumberDelta(BigDecimal(d.delta))
      case d: DynamicPatch.PrimitiveOp.BigDecimalDelta => new PrimitiveOp.NumberDelta(d.delta)
      case se: DynamicPatch.PrimitiveOp.StringEdit     => new PrimitiveOp.StringEdit(se.ops.map(stringOpFromDynamic))
      case _                                           =>
        return new Left(SchemaError("Temporal operations are not supported in JsonPatch"))
    }
  )

  private[this] def stringOpFromDynamic(op: DynamicPatch.StringOp): StringOp = op match {
    case i: DynamicPatch.StringOp.Insert => new StringOp.Insert(i.index, i.text)
    case d: DynamicPatch.StringOp.Delete => new StringOp.Delete(d.index, d.length)
    case a: DynamicPatch.StringOp.Append => new StringOp.Append(a.text)
    case m: DynamicPatch.StringOp.Modify => new StringOp.Modify(m.index, m.length, m.text)
  }

  private[this] def seqOpFromDynamic(op: DynamicPatch.SeqOp): Either[SchemaError, ArrayOp] = new Right(op match {
    case i: DynamicPatch.SeqOp.Insert => new ArrayOp.Insert(i.index, i.values.map(Json.fromDynamicValue))
    case a: DynamicPatch.SeqOp.Append => new ArrayOp.Append(a.values.map(Json.fromDynamicValue))
    case d: DynamicPatch.SeqOp.Delete => new ArrayOp.Delete(d.index, d.count)
    case m: DynamicPatch.SeqOp.Modify =>
      operationFromDynamic(m.op) match {
        case Right(op) => new ArrayOp.Modify(m.index, op)
        case l         => return l.asInstanceOf[Either[SchemaError, ArrayOp]]
      }
  })

  private[this] def mapOpFromDynamic(op: DynamicPatch.MapOp): Either[SchemaError, ObjectOp] = op match {
    case a: DynamicPatch.MapOp.Add =>
      extractStringKey(a.key) match {
        case Right(k) => new Right(new ObjectOp.Add(k, Json.fromDynamicValue(a.value)))
        case l        => l.asInstanceOf[Either[SchemaError, ObjectOp]]
      }
    case r: DynamicPatch.MapOp.Remove =>
      extractStringKey(r.key) match {
        case Right(k) => new Right(new ObjectOp.Remove(k))
        case l        => l.asInstanceOf[Either[SchemaError, ObjectOp]]
      }
    case m: DynamicPatch.MapOp.Modify =>
      extractStringKey(m.key) match {
        case Right(k) =>
          fromDynamicPatch(m.patch) match {
            case Right(p) => new Right(new ObjectOp.Modify(k, p))
            case l        => l.asInstanceOf[Either[SchemaError, ObjectOp]]
          }
        case l => l.asInstanceOf[Either[SchemaError, ObjectOp]]
      }
  }

  private[this] def extractStringKey(key: DynamicValue): Either[SchemaError, String] = key match {
    case DynamicValue.Primitive(pv: zio.blocks.schema.PrimitiveValue.String) => new Right(pv.value)
    case _                                                                   => new Left(SchemaError(s"JSON object keys must be strings, got: ${key.getClass.getSimpleName}"))
  }

  private[this] def sequenceAll[A](results: Chunk[Either[SchemaError, A]]): Either[SchemaError, Chunk[A]] = {
    val len     = results.length
    val builder = ChunkBuilder.make[A](len)
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
   * One operation of a patch and the path of the value it changes.
   *
   * @param path
   *   the path from the patched value to the target
   * @param operation
   *   the change to make at the target
   */
  final case class JsonPatchOp(path: DynamicOptic, operation: Op)

  // Operations
  /** A change to the value at a path of a [[JsonPatch]]. */
  sealed trait Op

  /** The kinds of [[Op]]. */
  object Op {

    /**
     * Replaces the target, whatever it is, in every mode.
     *
     * @param value
     *   the new value
     */
    final case class Set(value: Json) extends Op

    /**
     * Changes a number or a string in place.
     *
     * @param op
     *   a `NumberDelta` for a number or a `StringEdit` for a string
     */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Op

    /**
     * Edits the elements of an array, one step after another; each step sees
     * the array as the steps before it left it.
     *
     * @param ops
     *   the array steps, applied in order
     */
    final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op

    /**
     * Edits the fields of an object, one step after another.
     *
     * @param ops
     *   the object steps, applied in order
     */
    final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op

    /**
     * Applies a whole patch to the target, with paths relative to it and in the
     * same mode. [[JsonPatch.diff]] never produces it.
     *
     * @param patch
     *   the patch to apply to the target
     */
    final case class Nested(patch: JsonPatch) extends Op
  }

  // Primitive Operations

  /** A change to a number or a string. */
  sealed trait PrimitiveOp

  /** The kinds of [[PrimitiveOp]]. */
  object PrimitiveOp {

    /**
     * Adds `delta` to a number, with `BigDecimal` arithmetic; it fails on a
     * value that is not a number.
     *
     * @param delta
     *   the amount to add (negative to subtract)
     */
    final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp

    /**
     * Edits a string with steps applied in order, each with indices into the
     * string the steps before it left. When one step is out of range, the whole
     * edit fails, in every mode.
     *
     * @param ops
     *   the string steps, applied in order
     */
    final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
  }

  // String Operations

  /** A step of a [[PrimitiveOp.StringEdit]]. Indices count UTF-16 chars. */
  sealed trait StringOp

  /** The kinds of [[StringOp]]. */
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

  /** A step of an [[Op.ArrayEdit]]. */
  sealed trait ArrayOp

  /** The kinds of [[ArrayOp]]. */
  object ArrayOp {

    /**
     * Inserts elements before `index` (0 to the array's size). Out of range it
     * fails, except in `Clobber` mode, which inserts at the nearer end.
     *
     * @param index
     *   the position of the first inserted element
     * @param values
     *   the values to insert
     */
    final case class Insert(index: Int, values: Chunk[Json]) extends ArrayOp

    /**
     * Appends elements to the end of the array; it never fails.
     *
     * @param values
     *   the values to append
     */
    final case class Append(values: Chunk[Json]) extends ArrayOp

    /**
     * Removes `count` elements from `index`. Out of range it fails, except in
     * `Clobber` mode, which removes the elements that are in range.
     *
     * @param index
     *   the position of the first removed element
     * @param count
     *   the number of elements to remove
     */
    final case class Delete(index: Int, count: Int) extends ArrayOp

    /**
     * Applies `op` to the element at `index`; a missing index fails in every
     * mode.
     *
     * @param index
     *   the position of the element
     * @param op
     *   the change to make to it
     */
    final case class Modify(index: Int, op: Op) extends ArrayOp
  }

  // Object Operations

  /** A step of an [[Op.ObjectEdit]]. */
  sealed trait ObjectOp

  /** The kinds of [[ObjectOp]]. */
  object ObjectOp {

    /**
     * Adds a field as the last one. An existing key fails, except in `Clobber`
     * mode, which replaces its value.
     *
     * @param key
     *   the field name
     * @param value
     *   the field value
     */
    final case class Add(key: String, value: Json) extends ObjectOp

    /**
     * Removes the field `key`. A missing key fails, except in `Clobber` mode,
     * which ignores it.
     *
     * @param key
     *   the field name
     */
    final case class Remove(key: String) extends ObjectOp

    /**
     * Applies `patch` to the value of the field `key`, in the same mode; a
     * missing key fails in every mode.
     *
     * @param key
     *   the field name
     * @param patch
     *   the patch for the field's value, with paths relative to it
     */
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }

  // Schema Definitions, Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  // StringOp Schemas

  /** The schema of `StringOp.Insert`. */
  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Insert](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Insert] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Insert =
              new StringOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[String])
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

  /** The schema of `StringOp.Delete`. */
  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("length")),
        typeId = TypeId.of[StringOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Delete] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Delete =
              new StringOp.Delete(
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

  /** The schema of `StringOp.Append`. */
  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] =
    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Append](
        fields = Chunk.single(Schema[String].reflect.asTerm("text")),
        typeId = TypeId.of[StringOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[StringOp.Append] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): StringOp.Append =
              new StringOp.Append(in.getObject(offset).asInstanceOf[String])
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

  /** The schema of `StringOp.Modify`. */
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
              new StringOp.Modify(
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

  /** The schema of `StringOp`. */
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

  /** The schema of `PrimitiveOp.NumberDelta`. */
  implicit lazy val primitiveOpNumberDeltaSchema: Schema[PrimitiveOp.NumberDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.NumberDelta](
        fields = Chunk.single(Schema[BigDecimal].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.NumberDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.NumberDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.NumberDelta =
              new PrimitiveOp.NumberDelta(in.getObject(offset).asInstanceOf[BigDecimal])
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

  /** The schema of `PrimitiveOp.StringEdit`. */
  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.StringEdit](
        fields = Chunk.single(Schema[Chunk[StringOp]].reflect.asTerm("ops")),
        typeId = TypeId.of[PrimitiveOp.StringEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.StringEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.StringEdit =
              new PrimitiveOp.StringEdit(in.getObject(offset).asInstanceOf[Chunk[StringOp]])
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

  /** The schema of `PrimitiveOp`. */
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

  /** The schema of `ArrayOp.Insert`. */
  implicit lazy val arrayOpInsertSchema: Schema[ArrayOp.Insert] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Insert](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Chunk[Json]].reflect.asTerm("values")),
        typeId = TypeId.of[ArrayOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Insert] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Insert =
              new ArrayOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[Json]])
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

  /** The schema of `ArrayOp.Append`. */
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

  /** The schema of `ArrayOp.Delete`. */
  implicit lazy val arrayOpDeleteSchema: Schema[ArrayOp.Delete] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Delete](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Schema[Int].reflect.asTerm("count")),
        typeId = TypeId.of[ArrayOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Delete] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Delete =
              new ArrayOp.Delete(
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
  /** The schema of `ArrayOp.Modify`. */
  implicit lazy val arrayOpModifySchema: Schema[ArrayOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, ArrayOp.Modify](
        fields = Chunk(Schema[Int].reflect.asTerm("index"), Reflect.Deferred(() => opSchema.reflect).asTerm("op")),
        typeId = TypeId.of[ArrayOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ArrayOp.Modify] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): ArrayOp.Modify =
              new ArrayOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Op])
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

  /** The schema of `ArrayOp`. */
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

  /** The schema of `ObjectOp.Add`. */
  implicit lazy val objectOpAddSchema: Schema[ObjectOp.Add] =
    new Schema(
      reflect = new Reflect.Record[Binding, ObjectOp.Add](
        fields = Chunk(Schema[String].reflect.asTerm("key"), Schema[Json].reflect.asTerm("value")),
        typeId = TypeId.of[ObjectOp.Add],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ObjectOp.Add] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): ObjectOp.Add =
              new ObjectOp.Add(
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

  /** The schema of `ObjectOp.Remove`. */
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
  /** The schema of `ObjectOp.Modify`. */
  implicit lazy val objectOpModifySchema: Schema[ObjectOp.Modify] =
    new Schema(
      reflect = new Reflect.Record[Binding, ObjectOp.Modify](
        fields = Chunk(Schema[String].reflect.asTerm("key"), Reflect.Deferred(() => schema.reflect).asTerm("patch")),
        typeId = TypeId.of[ObjectOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[ObjectOp.Modify] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): ObjectOp.Modify =
              new ObjectOp.Modify(
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

  /** The schema of `ObjectOp`. */
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

  /** The schema of `Op.Set`. */
  implicit lazy val opSetSchema: Schema[Op.Set] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.Set](
        fields = Chunk.single(Schema[Json].reflect.asTerm("value")),
        typeId = TypeId.of[Op.Set],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.Set] {
            def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.Set =
              new Op.Set(in.getObject(offset).asInstanceOf[Json])
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

  /** The schema of `Op.PrimitiveDelta`. */
  implicit lazy val opPrimitiveDeltaSchema: Schema[Op.PrimitiveDelta] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.PrimitiveDelta](
        fields = Chunk.single(primitiveOpSchema.reflect.asTerm("op")),
        typeId = TypeId.of[Op.PrimitiveDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.PrimitiveDelta =
              new Op.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
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

  /** The schema of `Op.ArrayEdit`. */
  implicit lazy val opArrayEditSchema: Schema[Op.ArrayEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.ArrayEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[ArrayOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Op.ArrayEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.ArrayEdit] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.ArrayEdit =
              new Op.ArrayEdit(in.getObject(offset).asInstanceOf[Chunk[ArrayOp]])
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

  /** The schema of `Op.ObjectEdit`. */
  implicit lazy val opObjectEditSchema: Schema[Op.ObjectEdit] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.ObjectEdit](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[ObjectOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Op.ObjectEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.ObjectEdit] {
            def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.ObjectEdit =
              new Op.ObjectEdit(in.getObject(offset).asInstanceOf[Chunk[ObjectOp]])
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
  /** The schema of `Op.Nested`. */
  implicit lazy val opNestedSchema: Schema[Op.Nested] =
    new Schema(
      reflect = new Reflect.Record[Binding, Op.Nested](
        fields = Chunk.single(Reflect.Deferred(() => schema.reflect).asTerm("patch")),
        typeId = TypeId.of[Op.Nested],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Op.Nested] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Op.Nested =
              new Op.Nested(in.getObject(offset).asInstanceOf[JsonPatch])
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

  /** The schema of `Op`. */
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

  /** The schema of `JsonPatchOp`. */
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
              new JsonPatchOp(
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

  /** The schema of `JsonPatch`, so that patches can be stored in any format. */
  implicit lazy val schema: Schema[JsonPatch] =
    new Schema(
      reflect = new Reflect.Record[Binding, JsonPatch](
        fields = Chunk.single(Reflect.Deferred(() => Schema[Chunk[JsonPatchOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[JsonPatch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[JsonPatch] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): JsonPatch =
              new JsonPatch(in.getObject(offset).asInstanceOf[Chunk[JsonPatchOp]])
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
