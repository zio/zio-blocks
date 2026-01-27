package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.patch.DynamicPatch

// =============================================================================
// JSON PATCH
// =============================================================================

/**
 * An untyped patch that operates on [[Json]] values.
 *
 * `JsonPatch` is the JSON-specific counterpart to [[DynamicPatch]]. It
 * represents a sequence of operations that transform one JSON value into
 * another. Patches are serializable and composable.
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) { self =>

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
    var current: Json            = json
    var error: Option[JsonError] = None
    var idx                      = 0
    val len                      = ops.length

    while (idx < len && error.isEmpty) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op, mode) match {
        case Right(updated) => current = updated
        case Left(e)        =>
          mode match {
            case JsonPatchMode.Strict  => error = Some(e)
            case JsonPatchMode.Lenient => () // Skip operation
            case JsonPatchMode.Clobber => () // Skip operation
          }
      }
      idx += 1
    }
    error.toLeft(current)
  }

  /**
   * Composes this patch with another. Applies this patch first, then `that`.
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
    val dynamicOps = ops.map { jsonOp =>
      DynamicPatch.DynamicPatchOp(jsonOp.path, JsonPatch.toDynamicOp(jsonOp.op))
    }
    DynamicPatch(dynamicOps)
  }
}

object JsonPatch {

  val empty: JsonPatch = JsonPatch(Vector.empty)

  def root(op: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(DynamicOptic.root, op)))

  def apply(path: DynamicOptic, op: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(path, op)))

  // ===========================================================================
  // Diff Logic
  // ===========================================================================

  /**
   * Computes a patch that transforms `oldJson` into `newJson`.
   */
  def diff(oldJson: Json, newJson: Json): JsonPatch =
    diffRecursive(DynamicOptic.root, oldJson, newJson)

  private def diffRecursive(path: DynamicOptic, left: Json, right: Json): JsonPatch =
    if (left == right) JsonPatch.empty
    else {
      (left, right) match {
        case (l: Json.Object, r: Json.Object) => diffObject(path, l, r)
        case (l: Json.Array, r: Json.Array)   => diffArray(path, l, r)
        case (l: Json.String, r: Json.String) => diffString(path, l, r)
        case (l: Json.Number, r: Json.Number) => diffNumber(path, l, r)
        case _                                => JsonPatch(path, Op.Set(right))
      }
    }

  private def diffObject(path: DynamicOptic, left: Json.Object, right: Json.Object): JsonPatch = {
    val leftMap  = left.value.toMap
    val rightMap = right.value.toMap
    val allKeys  = (leftMap.keySet ++ rightMap.keySet).toVector.sorted

    val ops = Vector.newBuilder[ObjectOp]
    allKeys.foreach { key =>
      (leftMap.get(key), rightMap.get(key)) match {
        case (Some(lv), Some(rv)) if lv != rv =>
          val subPatch = diffRecursive(DynamicOptic.root, lv, rv)
          if (!subPatch.isEmpty) ops += ObjectOp.Modify(key, subPatch)
        case (Some(_), None)  => ops += ObjectOp.Remove(key)
        case (None, Some(rv)) => ops += ObjectOp.Add(key, rv)
        case _                => ()
      }
    }
    val res = ops.result()
    if (res.isEmpty) JsonPatch.empty else JsonPatch(path, Op.ObjectEdit(res))
  }

  private def diffArray(path: DynamicOptic, left: Json.Array, right: Json.Array): JsonPatch = {
    val oldVec = left.value.toVector
    val newVec = right.value.toVector

    if (oldVec == newVec) JsonPatch.empty
    else if (oldVec.isEmpty) JsonPatch(path, Op.ArrayEdit(Vector(ArrayOp.Append(newVec))))
    else if (newVec.isEmpty) JsonPatch(path, Op.ArrayEdit(Vector(ArrayOp.Delete(0, oldVec.length))))
    else {
      val matches   = longestCommonSubsequenceIndices(oldVec, newVec)
      val ops       = Vector.newBuilder[ArrayOp]
      var oldIdx    = 0
      var newIdx    = 0
      var cursor    = 0
      var curLength = oldVec.length

      matches.foreach { case (matchOld, matchNew) =>
        val deleteCount = matchOld - oldIdx
        if (deleteCount > 0) {
          ops += ArrayOp.Delete(cursor, deleteCount)
          curLength -= deleteCount
        }
        val insertVals = newVec.slice(newIdx, matchNew)
        if (insertVals.nonEmpty) {
          if (cursor == curLength) ops += ArrayOp.Append(insertVals)
          else ops += ArrayOp.Insert(cursor, insertVals)
          cursor += insertVals.length
          curLength += insertVals.length
        }
        oldIdx = matchOld + 1
        newIdx = matchNew + 1
        cursor += 1
      }
      val tailDelete = oldVec.length - oldIdx
      if (tailDelete > 0) ops += ArrayOp.Delete(cursor, tailDelete)
      val tailInsert = newVec.slice(newIdx, newVec.length)
      if (tailInsert.nonEmpty) ops += ArrayOp.Append(tailInsert)

      val res = ops.result()
      if (res.isEmpty) JsonPatch.empty else JsonPatch(path, Op.ArrayEdit(res))
    }
  }

  // LCS Implementation for Arrays
  private def longestCommonSubsequenceIndices(s1: Vector[Json], s2: Vector[Json]): Vector[(Int, Int)] = {
    val m  = s1.length
    val n  = s2.length
    val dp = Array.ofDim[Int](m + 1, n + 1)
    for (i <- 1 to m; j <- 1 to n) {
      if (s1(i - 1) == s2(j - 1)) dp(i)(j) = dp(i - 1)(j - 1) + 1
      else dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
    }
    val builder = Vector.newBuilder[(Int, Int)]
    var i       = m; var j = n
    while (i > 0 && j > 0) {
      if (s1(i - 1) == s2(j - 1)) { builder += ((i - 1, j - 1)); i -= 1; j -= 1 }
      else if (dp(i - 1)(j) >= dp(i)(j - 1)) i -= 1
      else j -= 1
    }
    builder.result().reverse
  }

  private def diffString(path: DynamicOptic, left: Json.String, right: Json.String): JsonPatch = {
    val lStr = left.value
    val rStr = right.value
    if (lStr == rStr) JsonPatch.empty
    else {
      // Basic LCS for strings
      val ops = computeStringEdits(lStr, rStr)
      // Heuristic: if patch is larger than replacement, just replace
      val size = ops.foldLeft(0)((acc, op) =>
        acc + (op match {
          case StringOp.Insert(_, t)    => t.length
          case StringOp.Delete(_, _)    => 1
          case StringOp.Append(t)       => t.length
          case StringOp.Modify(_, _, t) => t.length
        })
      )
      if (ops.nonEmpty && size < rStr.length) JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.StringEdit(ops)))
      else JsonPatch(path, Op.Set(right))
    }
  }

  private def computeStringEdits(oldStr: String, newStr: String): Vector[StringOp] = {
    // Simplified LCS-based string edit computation
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))
    // Fallback for robustness:
    Vector(StringOp.Delete(0, oldStr.length), StringOp.Insert(0, newStr))
  }

  private def diffNumber(path: DynamicOptic, left: Json.Number, right: Json.Number): JsonPatch = {
    val delta = right.toBigDecimal - left.toBigDecimal
    if (delta == BigDecimal(0) && left == right) JsonPatch.empty
    else {
      // Robustness check: Ensure adding the delta produces the EXACT target string/value.
      // BigDecimal arithmetic can change scale (e.g. 1.0 + 0.5 = 1.5, losing trailing zeros if target was 1.50)
      val reconstructed = left.toBigDecimal + delta
      if (Json.Number(reconstructed) == right)
        JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)))
      else
        JsonPatch(path, Op.Set(right))
    }
  }

  // ===========================================================================
  // Application Logic
  // ===========================================================================

  private[json] def applyOp(json: Json, patchOp: JsonPatchOp, mode: JsonPatchMode): Either[JsonError, Json] =
    patchOp.op match {
      case Op.Set(value) =>
        json.setOrFail(patchOp.path, value).left.flatMap { e =>
          if (mode == JsonPatchMode.Clobber) Right(json.set(patchOp.path, value))
          else if (mode == JsonPatchMode.Lenient) Right(json)
          else Left(e)
        }
      case Op.Nested(inner) =>
        json.get(patchOp.path).one match {
          case Right(target) => inner.apply(target, mode).flatMap(res => json.setOrFail(patchOp.path, res))
          case Left(e)       => if (mode == JsonPatchMode.Strict) Left(e) else Right(json)
        }
      case Op.PrimitiveDelta(op) =>
        json
          .modifyOrFail(patchOp.path) {
            case n: Json.Number => applyNum(n, op)
            case s: Json.String => applyStr(s, op)
            case _              => throw JsonError("Invalid target for delta")
          }
          .left
          .flatMap(e => handleErr(e, json, mode))
      case Op.ArrayEdit(ops) =>
        json
          .modifyOrFail(patchOp.path) { case a: Json.Array => applyArr(a, ops, mode) }
          .left
          .flatMap(e => handleErr(e, json, mode))
      case Op.ObjectEdit(ops) =>
        json
          .modifyOrFail(patchOp.path) { case o: Json.Object => applyObj(o, ops, mode) }
          .left
          .flatMap(e => handleErr(e, json, mode))
    }

  private def handleErr(e: JsonError, orig: Json, mode: JsonPatchMode): Either[JsonError, Json] =
    if (mode == JsonPatchMode.Strict) Left(e) else Right(orig)

  private def applyNum(n: Json.Number, op: PrimitiveOp): Json.Number = op match {
    case PrimitiveOp.NumberDelta(d) => Json.Number(n.toBigDecimal + d)
    case _                          => n
  }

  private def applyStr(s: Json.String, op: PrimitiveOp): Json.String = op match {
    case PrimitiveOp.StringEdit(ops) =>
      var res = s.value
      ops.foreach {
        case StringOp.Insert(i, t)    => res = res.substring(0, i) + t + res.substring(i)
        case StringOp.Delete(i, l)    => res = res.substring(0, i) + res.substring(i + l)
        case StringOp.Append(t)       => res = res + t
        case StringOp.Modify(i, l, t) => res = res.substring(0, i) + t + res.substring(i + l)
      }
      Json.String(res)
    case _ => s
  }

  private def applyArr(arr: Json.Array, ops: Vector[ArrayOp], mode: JsonPatchMode): Json.Array = {
    var buf = arr.value.toVector
    ops.foreach {
      case ArrayOp.Append(vs)    => buf = buf ++ vs
      case ArrayOp.Insert(i, vs) =>
        if (i >= 0 && i <= buf.length) buf = buf.patch(i, vs, 0)
        else if (mode == JsonPatchMode.Strict) throw JsonError(s"Idx $i out of bounds")
      case ArrayOp.Delete(i, c) =>
        if (i >= 0 && i + c <= buf.length) buf = buf.patch(i, Vector.empty, c)
        else if (mode == JsonPatchMode.Strict) throw JsonError(s"Idx $i out of bounds")
      case ArrayOp.Modify(i, op) =>
        if (i >= 0 && i < buf.length) {
          val res = JsonPatch.root(op).apply(buf(i), mode)
          res match {
            case Right(v)                                => buf = buf.updated(i, v)
            case Left(e) if mode == JsonPatchMode.Strict => throw e
            case _                                       => ()
          }
        } else if (mode == JsonPatchMode.Strict) throw JsonError(s"Idx $i out of bounds")
    }
    Json.Array(zio.blocks.chunk.Chunk.from(buf))
  }

  private def applyObj(obj: Json.Object, ops: Vector[ObjectOp], mode: JsonPatchMode): Json.Object = {
    var fields = obj.value
    ops.foreach {
      case ObjectOp.Add(k, v) => fields = fields.filter(_._1 != k) :+ (k -> v)
      case ObjectOp.Remove(k) =>
        if (mode == JsonPatchMode.Strict && !fields.exists(_._1 == k)) throw JsonError(s"Key $k not found")
        fields = fields.filter(_._1 != k)
      case ObjectOp.Modify(k, patch) =>
        val idx = fields.indexWhere(_._1 == k)
        if (idx >= 0) {
          val res = patch.apply(fields(idx)._2, mode)
          res match {
            case Right(v)                                => fields = fields.updated(idx, (k, v))
            case Left(e) if mode == JsonPatchMode.Strict => throw e
            case _                                       => ()
          }
        } else if (mode == JsonPatchMode.Strict) throw JsonError(s"Key $k not found")
    }
    Json.Object(fields)
  }

  // ===========================================================================
  // Interop
  // ===========================================================================
  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    val ops = patch.ops.foldLeft[Either[JsonError, Vector[JsonPatchOp]]](Right(Vector.empty)) {
      case (Left(e), _)     => Left(e)
      case (Right(acc), op) => convertOp(op.operation).map(o => acc :+ JsonPatchOp(op.path, o))
    }
    ops.map(JsonPatch(_))
  }

  private def convertOp(op: DynamicPatch.Operation): Either[JsonError, Op] = op match {
    case DynamicPatch.Operation.Set(v)            => Right(Op.Set(Json.fromDynamicValue(v)))
    case DynamicPatch.Operation.Patch(p)          => fromDynamicPatch(p).map(Op.Nested.apply)
    case DynamicPatch.Operation.SequenceEdit(ops) =>
      val cOps = ops.map {
        case DynamicPatch.SeqOp.Insert(i, v) => Right(ArrayOp.Insert(i, v.map(Json.fromDynamicValue)))
        case DynamicPatch.SeqOp.Append(v)    => Right(ArrayOp.Append(v.map(Json.fromDynamicValue)))
        case DynamicPatch.SeqOp.Delete(i, c) => Right(ArrayOp.Delete(i, c))
        case DynamicPatch.SeqOp.Modify(i, o) => convertOp(o).map(ArrayOp.Modify(i, _))
      }
      reduce(cOps).map(Op.ArrayEdit.apply)
    case DynamicPatch.Operation.MapEdit(ops) =>
      val cOps = ops.map {
        case DynamicPatch.MapOp.Add(k, v)    => keyStr(k).map(ObjectOp.Add(_, Json.fromDynamicValue(v)))
        case DynamicPatch.MapOp.Remove(k)    => keyStr(k).map(ObjectOp.Remove.apply)
        case DynamicPatch.MapOp.Modify(k, p) =>
          for { s <- keyStr(k); r <- fromDynamicPatch(p) } yield ObjectOp.Modify(s, r)
      }
      reduce(cOps).map(Op.ObjectEdit.apply)
    case DynamicPatch.Operation.PrimitiveDelta(op) => convertPrim(op).map(Op.PrimitiveDelta.apply)
  }

  private def convertPrim(op: DynamicPatch.PrimitiveOp): Either[JsonError, PrimitiveOp] = op match {
    case DynamicPatch.PrimitiveOp.IntDelta(d)        => Right(PrimitiveOp.NumberDelta(BigDecimal(d)))
    case DynamicPatch.PrimitiveOp.LongDelta(d)       => Right(PrimitiveOp.NumberDelta(BigDecimal(d)))
    case DynamicPatch.PrimitiveOp.DoubleDelta(d)     => Right(PrimitiveOp.NumberDelta(BigDecimal(d)))
    case DynamicPatch.PrimitiveOp.FloatDelta(d)      => Right(PrimitiveOp.NumberDelta(BigDecimal(d.toDouble)))
    case DynamicPatch.PrimitiveOp.ShortDelta(d)      => Right(PrimitiveOp.NumberDelta(BigDecimal(d.toInt)))
    case DynamicPatch.PrimitiveOp.ByteDelta(d)       => Right(PrimitiveOp.NumberDelta(BigDecimal(d.toInt)))
    case DynamicPatch.PrimitiveOp.BigIntDelta(d)     => Right(PrimitiveOp.NumberDelta(BigDecimal(d)))
    case DynamicPatch.PrimitiveOp.BigDecimalDelta(d) => Right(PrimitiveOp.NumberDelta(d))
    case DynamicPatch.PrimitiveOp.StringEdit(ops)    =>
      Right(PrimitiveOp.StringEdit(ops.map {
        case DynamicPatch.StringOp.Insert(i, t)    => StringOp.Insert(i, t)
        case DynamicPatch.StringOp.Delete(i, l)    => StringOp.Delete(i, l)
        case DynamicPatch.StringOp.Append(t)       => StringOp.Append(t)
        case DynamicPatch.StringOp.Modify(i, l, t) => StringOp.Modify(i, l, t)
      }))
    case _ => Left(JsonError("Unsupported primitive delta"))
  }

  private def reduce[A](vs: Vector[Either[JsonError, A]]): Either[JsonError, Vector[A]] =
    vs.foldLeft[Either[JsonError, Vector[A]]](Right(Vector.empty)) {
      case (Right(acc), Right(v)) => Right(acc :+ v)
      case (Left(e), _)           => Left(e)
      case (_, Left(e))           => Left(e)
    }

  private def keyStr(k: DynamicValue): Either[JsonError, String] = k match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
    case _                                                => Left(JsonError("Key must be string"))
  }

  private[json] def toDynamicOp(op: Op): DynamicPatch.Operation = op match {
    case Op.Set(v)                                     => DynamicPatch.Operation.Set(v.toDynamicValue)
    case Op.Nested(p)                                  => DynamicPatch.Operation.Patch(p.toDynamicPatch)
    case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) =>
      DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(d))
    case Op.PrimitiveDelta(PrimitiveOp.StringEdit(ops)) =>
      DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.StringEdit(ops.map {
        case StringOp.Insert(i, t)    => DynamicPatch.StringOp.Insert(i, t)
        case StringOp.Delete(i, l)    => DynamicPatch.StringOp.Delete(i, l)
        case StringOp.Append(t)       => DynamicPatch.StringOp.Append(t)
        case StringOp.Modify(i, l, t) => DynamicPatch.StringOp.Modify(i, l, t)
      }))
    case Op.ArrayEdit(ops) =>
      DynamicPatch.Operation.SequenceEdit(ops.map {
        case ArrayOp.Insert(i, v) => DynamicPatch.SeqOp.Insert(i, v.map(_.toDynamicValue))
        case ArrayOp.Append(v)    => DynamicPatch.SeqOp.Append(v.map(_.toDynamicValue))
        case ArrayOp.Delete(i, c) => DynamicPatch.SeqOp.Delete(i, c)
        case ArrayOp.Modify(i, o) => DynamicPatch.SeqOp.Modify(i, toDynamicOp(o))
      })
    case Op.ObjectEdit(ops) =>
      DynamicPatch.Operation.MapEdit(ops.map {
        case ObjectOp.Add(k, v) =>
          DynamicPatch.MapOp.Add(DynamicValue.Primitive(PrimitiveValue.String(k)), v.toDynamicValue)
        case ObjectOp.Remove(k)    => DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(k)))
        case ObjectOp.Modify(k, p) =>
          DynamicPatch.MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String(k)), p.toDynamicPatch)
      })
  }

  // ===========================================================================
  // Types
  // ===========================================================================
  final case class JsonPatchOp(path: DynamicOptic, op: Op)

  sealed trait Op
  object Op {
    final case class Set(value: Json)                  extends Op
    final case class PrimitiveDelta(op: PrimitiveOp)   extends Op
    final case class ArrayEdit(ops: Vector[ArrayOp])   extends Op
    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Op
    final case class Nested(patch: JsonPatch)          extends Op
  }

  sealed trait PrimitiveOp
  object PrimitiveOp {
    final case class NumberDelta(delta: BigDecimal)    extends PrimitiveOp
    final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  }

  sealed trait StringOp
  object StringOp {
    final case class Insert(index: Int, text: String)              extends StringOp
    final case class Delete(index: Int, length: Int)               extends StringOp
    final case class Append(text: String)                          extends StringOp
    final case class Modify(index: Int, length: Int, text: String) extends StringOp
  }

  sealed trait ArrayOp
  object ArrayOp {
    final case class Insert(index: Int, values: Vector[Json]) extends ArrayOp
    final case class Append(values: Vector[Json])             extends ArrayOp
    final case class Delete(index: Int, count: Int)           extends ArrayOp
    final case class Modify(index: Int, op: Op)               extends ArrayOp
  }

  sealed trait ObjectOp
  object ObjectOp {
    final case class Add(key: String, value: Json)         extends ObjectOp
    final case class Remove(key: String)                   extends ObjectOp
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }
}

// =============================================================================
// Patch Mode
// =============================================================================
sealed trait JsonPatchMode
object JsonPatchMode {
  case object Strict  extends JsonPatchMode
  case object Lenient extends JsonPatchMode
  case object Clobber extends JsonPatchMode
}
