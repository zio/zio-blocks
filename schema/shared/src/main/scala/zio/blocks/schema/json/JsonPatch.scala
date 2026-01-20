package zio.blocks.schema.json

import zio.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}

/**
 * An immutable patch representing transformations between JSON values.
 *
 * JsonPatch supports computing diffs between JSON values, applying patches,
 * and composing patches. It maintains the algebraic properties expected of
 * a patch system:
 *
 * ==Algebraic Laws==
 *  - '''L1 (Left Identity)''': `JsonPatch.empty ++ p == p`
 *  - '''L2 (Right Identity)''': `p ++ JsonPatch.empty == p`
 *  - '''L3 (Associativity)''': `(p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)`
 *  - '''L4 (Roundtrip)''': `source.diff(target).apply(source) == Right(target)`
 *  - '''L5 (Identity Diff)''': `json.diff(json) == JsonPatch.empty`
 *  - '''L6 (Composition)''': `a.diff(b) ++ b.diff(c) ~= a.diff(c)` (semantically equivalent)
 *  - '''L7 (Lenient Subsumes Strict)''': If strict succeeds, lenient produces same result
 *
 * ==Example Usage==
 * {{{
 * val source = Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30))
 * val target = Json.obj("name" -> Json.str("Bob"), "age" -> Json.num(31))
 *
 * val patch = JsonPatch.diff(source, target)
 * patch.apply(source) // Right(target)
 * }}}
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) {

  /**
   * Apply this patch to a JSON value.
   *
   * @param json The source JSON value to patch
   * @param mode The patch mode controlling error behavior
   * @return Either an error or the patched JSON value
   */
  def apply(json: Json, mode: PatchMode = PatchMode.Strict): Either[JsonPatch.JsonPatchError, Json] = {
    var current = json
    var idx = 0
    var error: Option[JsonPatch.JsonPatchError] = None

    while (idx < ops.length && error.isEmpty) {
      val op = ops(idx)
      JsonPatch.applyOp(current, op.path, op.operation, mode) match {
        case Right(updated) =>
          current = updated
        case Left(err) =>
          mode match {
            case PatchMode.Strict  => error = Some(err)
            case PatchMode.Lenient => ()
            case PatchMode.Clobber => ()
          }
      }
      idx += 1
    }

    error.toLeft(current)
  }

  /** Compose two patches. Result applies this patch first, then that patch. */
  def ++(that: JsonPatch): JsonPatch = JsonPatch(ops ++ that.ops)

  /** Check if this patch has no operations. */
  def isEmpty: Boolean = ops.isEmpty

  /** Check if this patch has operations. */
  def nonEmpty: Boolean = ops.nonEmpty

  /** Number of operations in this patch. */
  def size: Int = ops.length

  /** Convert to DynamicPatch for interop with schema system. */
  def toDynamicPatch: DynamicPatch = {
    val dynamicOps = ops.map { op =>
      val dynamicPath = op.path.toDynamicOptic
      val dynamicOp = op.operation.toDynamicOperation
      DynamicPatch.DynamicPatchOp(dynamicPath, dynamicOp)
    }
    DynamicPatch(dynamicOps)
  }
}

object JsonPatch {

  /** Empty patch - identity element for composition. */
  val empty: JsonPatch = JsonPatch(Vector.empty)

  /** Single operation patch at root. */
  def root(operation: Operation): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(JsonPath.root, operation)))

  /** Single operation patch at a given path. */
  def at(path: JsonPath, operation: Operation): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(path, operation)))

  /**
   * Compute a patch that transforms source into target.
   * Uses efficient diff algorithms for strings (LCS) and arrays.
   */
  def diff(source: Json, target: Json): JsonPatch =
    if (source == target) empty
    else diffValues(source, target, JsonPath.root)

  private def diffValues(source: Json, target: Json, path: JsonPath): JsonPatch =
    (source, target) match {
      case (s, t) if s == t => empty

      case (Json.Num(oldVal), Json.Num(newVal)) =>
        val delta = newVal.subtract(oldVal)
        if (delta.compareTo(java.math.BigDecimal.ZERO) == 0) empty
        else at(path, Operation.NumberDelta(delta))

      case (Json.Str(oldStr), Json.Str(newStr)) =>
        diffString(oldStr, newStr, path)

      case (Json.Arr(oldElems), Json.Arr(newElems)) =>
        diffArray(oldElems, newElems, path)

      case (Json.Obj(oldFields), Json.Obj(newFields)) =>
        diffObject(oldFields, newFields, path)

      case _ =>
        at(path, Operation.Set(target))
    }

  private def diffString(oldStr: String, newStr: String, path: JsonPath): JsonPatch = {
    if (oldStr == newStr) return empty

    val edits = computeStringEdits(oldStr, newStr)
    val editSize = edits.foldLeft(0) {
      case (acc, StringOp.Insert(_, text))    => acc + text.length
      case (acc, StringOp.Delete(_, _))       => acc + 1
      case (acc, StringOp.Modify(_, _, text)) => acc + text.length
    }

    if (edits.nonEmpty && editSize < newStr.length)
      at(path, Operation.StringEdit(edits))
    else
      at(path, Operation.Set(Json.str(newStr)))
  }

  private def computeStringEdits(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))

    val lcs = longestCommonSubsequence(oldStr, newStr)
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
      edits += StringOp.Delete(cursor, oldStr.length - oldIdx)
    }
    if (newIdx < newStr.length) {
      edits += StringOp.Insert(cursor, newStr.substring(newIdx))
    }

    edits.result()
  }

  private def longestCommonSubsequence(a: String, b: String): String = {
    val m = a.length
    val n = b.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 1 to m; j <- 1 to n) {
      if (a.charAt(i - 1) == b.charAt(j - 1))
        dp(i)(j) = dp(i - 1)(j - 1) + 1
      else
        dp(i)(j) = math.max(dp(i - 1)(j), dp(i)(j - 1))
    }

    val sb = new StringBuilder
    var i = m
    var j = n
    while (i > 0 && j > 0) {
      if (a.charAt(i - 1) == b.charAt(j - 1)) {
        sb.append(a.charAt(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }
    sb.reverse.toString
  }

  private def diffArray(oldElems: Chunk[Json], newElems: Chunk[Json], path: JsonPath): JsonPatch = {
    if (oldElems == newElems) return empty

    val seqOps = computeArrayEdits(oldElems, newElems)
    val editComplexity = seqOps.foldLeft(0) {
      case (acc, ArrayOp.Insert(_, _))  => acc + 1
      case (acc, ArrayOp.Delete(_, _))  => acc + 1
      case (acc, ArrayOp.Replace(_, _)) => acc + 1
    }

    if (seqOps.nonEmpty && editComplexity <= newElems.length)
      at(path, Operation.ArrayEdit(seqOps))
    else
      at(path, Operation.Set(Json.Arr(newElems)))
  }

  private def computeArrayEdits(oldElems: Chunk[Json], newElems: Chunk[Json]): Vector[ArrayOp] = {
    if (oldElems == newElems) return Vector.empty
    if (oldElems.isEmpty) {
      return newElems.zipWithIndex.map { case (elem, i) => ArrayOp.Insert(i, elem) }.toVector
    }
    if (newElems.isEmpty) {
      return Vector(ArrayOp.Delete(0, oldElems.length))
    }

    val edits = Vector.newBuilder[ArrayOp]
    val lcs = arrayLCS(oldElems, newElems)

    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var cursor = 0

    while (lcsIdx < lcs.length) {
      val target = lcs(lcsIdx)

      while (oldIdx < oldElems.length && oldElems(oldIdx) != target) {
        edits += ArrayOp.Delete(cursor, 1)
        oldIdx += 1
      }

      while (newIdx < newElems.length && newElems(newIdx) != target) {
        edits += ArrayOp.Insert(cursor, newElems(newIdx))
        cursor += 1
        newIdx += 1
      }

      oldIdx += 1
      newIdx += 1
      cursor += 1
      lcsIdx += 1
    }

    while (oldIdx < oldElems.length) {
      edits += ArrayOp.Delete(cursor, 1)
      oldIdx += 1
    }

    while (newIdx < newElems.length) {
      edits += ArrayOp.Insert(cursor, newElems(newIdx))
      cursor += 1
      newIdx += 1
    }

    edits.result()
  }

  private def arrayLCS(a: Chunk[Json], b: Chunk[Json]): Vector[Json] = {
    val m = a.length
    val n = b.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 1 to m; j <- 1 to n) {
      if (a(i - 1) == b(j - 1))
        dp(i)(j) = dp(i - 1)(j - 1) + 1
      else
        dp(i)(j) = math.max(dp(i - 1)(j), dp(i)(j - 1))
    }

    val result = Vector.newBuilder[Json]
    var i = m
    var j = n
    while (i > 0 && j > 0) {
      if (a(i - 1) == b(j - 1)) {
        result += a(i - 1)
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }
    result.result().reverse
  }

  private def diffObject(oldFields: Chunk[(String, Json)], newFields: Chunk[(String, Json)], path: JsonPath): JsonPatch = {
    if (oldFields == newFields) return empty

    val oldMap = oldFields.toMap
    val newMap = newFields.toMap
    val allKeys = (oldMap.keySet ++ newMap.keySet).toVector.sorted

    val ops = Vector.newBuilder[ObjectOp]

    for (key <- allKeys) {
      (oldMap.get(key), newMap.get(key)) match {
        case (Some(oldVal), Some(newVal)) if oldVal != newVal =>
          ops += ObjectOp.Update(key, newVal)
        case (Some(_), None) =>
          ops += ObjectOp.Remove(key)
        case (None, Some(newVal)) =>
          ops += ObjectOp.Add(key, newVal)
        case _ => ()
      }
    }

    val result = ops.result()
    if (result.nonEmpty) at(path, Operation.ObjectEdit(result))
    else empty
  }

  private def applyOp(
    json: Json,
    path: JsonPath,
    operation: Operation,
    mode: PatchMode
  ): Either[JsonPatchError, Json] =
    if (path.isEmpty) applyOperation(json, operation, mode, Nil)
    else navigateAndApply(json, path.segments, 0, operation, mode, Nil)

  private def navigateAndApply(
    json: Json,
    segments: Vector[JsonPath.Segment],
    idx: Int,
    operation: Operation,
    mode: PatchMode,
    trace: List[JsonPath.Segment]
  ): Either[JsonPatchError, Json] = {
    val segment = segments(idx)
    val isLast = idx == segments.length - 1

    segment match {
      case JsonPath.Segment.Field(name) =>
        json match {
          case Json.Obj(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) {
              Left(JsonPatchError.MissingField(name, trace.reverse))
            } else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val newTrace = segment :: trace
              if (isLast) {
                applyOperation(fieldValue, operation, mode, newTrace).map { newValue =>
                  Json.Obj(fields.updated(fieldIdx, (fieldName, newValue)))
                }
              } else {
                navigateAndApply(fieldValue, segments, idx + 1, operation, mode, newTrace).map { newValue =>
                  Json.Obj(fields.updated(fieldIdx, (fieldName, newValue)))
                }
              }
            }
          case _ =>
            Left(JsonPatchError.TypeMismatch("Object", json.getClass.getSimpleName, trace.reverse))
        }

      case JsonPath.Segment.Index(i) =>
        json match {
          case Json.Arr(elements) =>
            if (i < 0 || i >= elements.length) {
              Left(JsonPatchError.IndexOutOfBounds(i, elements.length, trace.reverse))
            } else {
              val elem = elements(i)
              val newTrace = segment :: trace
              if (isLast) {
                applyOperation(elem, operation, mode, newTrace).map { newValue =>
                  Json.Arr(elements.updated(i, newValue))
                }
              } else {
                navigateAndApply(elem, segments, idx + 1, operation, mode, newTrace).map { newValue =>
                  Json.Arr(elements.updated(i, newValue))
                }
              }
            }
          case _ =>
            Left(JsonPatchError.TypeMismatch("Array", json.getClass.getSimpleName, trace.reverse))
        }
    }
  }

  private def applyOperation(
    json: Json,
    operation: Operation,
    mode: PatchMode,
    trace: List[JsonPath.Segment]
  ): Either[JsonPatchError, Json] =
    operation match {
      case Operation.Set(newValue) =>
        Right(newValue)

      case Operation.NumberDelta(delta) =>
        json match {
          case Json.Num(value) => Right(Json.Num(value.add(delta)))
          case _ => Left(JsonPatchError.TypeMismatch("Number", json.getClass.getSimpleName, trace.reverse))
        }

      case Operation.StringEdit(ops) =>
        json match {
          case Json.Str(value) => applyStringEdits(value, ops, trace).map(Json.str)
          case _ => Left(JsonPatchError.TypeMismatch("String", json.getClass.getSimpleName, trace.reverse))
        }

      case Operation.ArrayEdit(ops) =>
        json match {
          case Json.Arr(elements) => applyArrayEdits(elements, ops, trace).map(Json.Arr(_))
          case _ => Left(JsonPatchError.TypeMismatch("Array", json.getClass.getSimpleName, trace.reverse))
        }

      case Operation.ObjectEdit(ops) =>
        json match {
          case Json.Obj(fields) => applyObjectEdits(fields, ops, trace).map(Json.Obj(_))
          case _ => Left(JsonPatchError.TypeMismatch("Object", json.getClass.getSimpleName, trace.reverse))
        }

      case Operation.Nested(nestedPatch) =>
        nestedPatch.apply(json, mode).left.map(identity)
    }

  private def applyStringEdits(str: String, ops: Vector[StringOp], trace: List[JsonPath.Segment]): Either[JsonPatchError, String] = {
    var result = str
    var idx = 0
    var error: Option[JsonPatchError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.substring(0, index) + text + result.substring(index)
          }
        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.substring(0, index) + result.substring(index + length)
          }
        case StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.substring(0, index) + text + result.substring(index + length)
          }
      }
      idx += 1
    }

    error.toLeft(result)
  }

  private def applyArrayEdits(elements: Chunk[Json], ops: Vector[ArrayOp], trace: List[JsonPath.Segment]): Either[JsonPatchError, Chunk[Json]] = {
    var result = elements.toVector
    var idx = 0
    var error: Option[JsonPatchError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case ArrayOp.Insert(index, value) =>
          if (index < 0 || index > result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.take(index) ++ Vector(value) ++ result.drop(index)
          }
        case ArrayOp.Delete(index, count) =>
          if (index < 0 || index + count > result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.take(index) ++ result.drop(index + count)
          }
        case ArrayOp.Replace(index, value) =>
          if (index < 0 || index >= result.length) {
            error = Some(JsonPatchError.IndexOutOfBounds(index, result.length, trace.reverse))
          } else {
            result = result.updated(index, value)
          }
      }
      idx += 1
    }

    error.toLeft(Chunk.fromIterable(result))
  }

  private def applyObjectEdits(fields: Chunk[(String, Json)], ops: Vector[ObjectOp], trace: List[JsonPath.Segment]): Either[JsonPatchError, Chunk[(String, Json)]] = {
    var result = fields.toMap
    var idx = 0

    while (idx < ops.length) {
      ops(idx) match {
        case ObjectOp.Add(key, value) =>
          result = result.updated(key, value)
        case ObjectOp.Remove(key) =>
          result = result - key
        case ObjectOp.Update(key, value) =>
          result = result.updated(key, value)
      }
      idx += 1
    }

    Right(Chunk.fromIterable(result.toSeq))
  }

  /** A single patch operation at a specific path. */
  final case class JsonPatchOp(path: JsonPath, operation: Operation)

  /** Patch operations that can be applied to JSON values. */
  sealed trait Operation {
    def toDynamicOperation: DynamicPatch.Operation
  }

  object Operation {
    final case class Set(value: Json) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation = DynamicPatch.Operation.Set(value.toDynamicValue)
    }

    final case class NumberDelta(delta: java.math.BigDecimal) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation =
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal(delta)))
    }

    final case class StringEdit(ops: Vector[StringOp]) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation =
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.StringEdit(ops.map(_.toSchemaOp)))
    }

    final case class ArrayEdit(ops: Vector[ArrayOp]) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation =
        DynamicPatch.Operation.SequenceEdit(ops.flatMap(_.toSchemaOps))
    }

    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation =
        DynamicPatch.Operation.MapEdit(ops.flatMap(_.toSchemaOps))
    }

    final case class Nested(patch: JsonPatch) extends Operation {
      def toDynamicOperation: DynamicPatch.Operation =
        DynamicPatch.Operation.Patch(patch.toDynamicPatch)
    }
  }

  /** String edit operations. */
  sealed trait StringOp {
    def toSchemaOp: DynamicPatch.StringOp
  }

  object StringOp {
    final case class Insert(index: Int, text: String) extends StringOp {
      def toSchemaOp: DynamicPatch.StringOp = DynamicPatch.StringOp.Insert(index, text)
    }
    final case class Delete(index: Int, length: Int) extends StringOp {
      def toSchemaOp: DynamicPatch.StringOp = DynamicPatch.StringOp.Delete(index, length)
    }
    final case class Modify(index: Int, length: Int, text: String) extends StringOp {
      def toSchemaOp: DynamicPatch.StringOp = DynamicPatch.StringOp.Modify(index, length, text)
    }
  }

  /** Array edit operations. */
  sealed trait ArrayOp {
    def toSchemaOps: Vector[DynamicPatch.SeqOp]
  }

  object ArrayOp {
    final case class Insert(index: Int, value: Json) extends ArrayOp {
      def toSchemaOps: Vector[DynamicPatch.SeqOp] =
        Vector(DynamicPatch.SeqOp.Insert(index, Vector(value.toDynamicValue)))
    }
    final case class Delete(index: Int, count: Int) extends ArrayOp {
      def toSchemaOps: Vector[DynamicPatch.SeqOp] = Vector(DynamicPatch.SeqOp.Delete(index, count))
    }
    final case class Replace(index: Int, value: Json) extends ArrayOp {
      def toSchemaOps: Vector[DynamicPatch.SeqOp] = Vector(
        DynamicPatch.SeqOp.Delete(index, 1),
        DynamicPatch.SeqOp.Insert(index, Vector(value.toDynamicValue))
      )
    }
  }

  /** Object edit operations. */
  sealed trait ObjectOp {
    def toSchemaOps: Vector[DynamicPatch.MapOp]
  }

  object ObjectOp {
    final case class Add(key: String, value: Json) extends ObjectOp {
      def toSchemaOps: Vector[DynamicPatch.MapOp] = Vector(DynamicPatch.MapOp.Add(
        DynamicValue.Primitive(PrimitiveValue.String(key)),
        value.toDynamicValue
      ))
    }
    final case class Remove(key: String) extends ObjectOp {
      def toSchemaOps: Vector[DynamicPatch.MapOp] = Vector(DynamicPatch.MapOp.Remove(
        DynamicValue.Primitive(PrimitiveValue.String(key))
      ))
    }
    final case class Update(key: String, value: Json) extends ObjectOp {
      def toSchemaOps: Vector[DynamicPatch.MapOp] = Vector(DynamicPatch.MapOp.Add(
        DynamicValue.Primitive(PrimitiveValue.String(key)),
        value.toDynamicValue
      ))
    }
  }

  /** Errors that can occur during patch application. */
  sealed trait JsonPatchError {
    def message: String
    def path: List[JsonPath.Segment]

    def pathString: String = path.map {
      case JsonPath.Segment.Field(name) => s".$name"
      case JsonPath.Segment.Index(i)    => s"[$i]"
    }.mkString

    override def toString: String = s"$message at path: $pathString"
  }

  object JsonPatchError {
    final case class MissingField(field: String, path: List[JsonPath.Segment]) extends JsonPatchError {
      def message: String = s"Missing field: $field"
    }
    final case class TypeMismatch(expected: String, actual: String, path: List[JsonPath.Segment]) extends JsonPatchError {
      def message: String = s"Expected $expected but found $actual"
    }
    final case class IndexOutOfBounds(index: Int, length: Int, path: List[JsonPath.Segment]) extends JsonPatchError {
      def message: String = s"Index $index out of bounds for length $length"
    }
  }

  /** Convert from DynamicPatch. */
  def fromDynamicPatch(dp: DynamicPatch): Option[JsonPatch] = {
    val ops = dp.ops.flatMap { op =>
      for {
        path <- JsonPath.fromDynamicOptic(op.path)
        operation <- operationFromDynamic(op.operation)
      } yield JsonPatchOp(path, operation)
    }
    if (ops.length == dp.ops.length) Some(JsonPatch(ops))
    else None
  }

  private def operationFromDynamic(op: DynamicPatch.Operation): Option[Operation] = op match {
    case DynamicPatch.Operation.Set(dv) =>
      Json.fromDynamicValue(dv).map(Operation.Set(_))
    case DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(delta)) =>
      Some(Operation.NumberDelta(delta.bigDecimal))
    case _ => None
  }
}

/** Path through a JSON structure. */
final case class JsonPath(segments: Vector[JsonPath.Segment]) {
  def isEmpty: Boolean = segments.isEmpty

  def /(field: String): JsonPath = JsonPath(segments :+ JsonPath.Segment.Field(field))
  def /(index: Int): JsonPath = JsonPath(segments :+ JsonPath.Segment.Index(index))

  def toDynamicOptic: DynamicOptic = {
    val nodes = segments.map {
      case JsonPath.Segment.Field(name) => DynamicOptic.Node.Field(name)
      case JsonPath.Segment.Index(i)    => DynamicOptic.Node.AtIndex(i)
    }
    DynamicOptic(nodes)
  }

  override def toString: String = segments.map {
    case JsonPath.Segment.Field(name) => s".$name"
    case JsonPath.Segment.Index(i)    => s"[$i]"
  }.mkString("$", "", "")
}

object JsonPath {
  val root: JsonPath = JsonPath(Vector.empty)

  def field(name: String): JsonPath = JsonPath(Vector(Segment.Field(name)))
  def index(i: Int): JsonPath = JsonPath(Vector(Segment.Index(i)))

  sealed trait Segment
  object Segment {
    final case class Field(name: String) extends Segment
    final case class Index(index: Int) extends Segment
  }

  def fromDynamicOptic(optic: DynamicOptic): Option[JsonPath] = {
    val segments = optic.nodes.flatMap {
      case DynamicOptic.Node.Field(name)   => Some(Segment.Field(name))
      case DynamicOptic.Node.AtIndex(i)    => Some(Segment.Index(i))
      case _                               => None
    }
    if (segments.length == optic.nodes.length) Some(JsonPath(segments.toVector))
    else None
  }
}
