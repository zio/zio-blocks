package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue
import zio.blocks.schema.patch.DynamicPatch

/**
 * A patch that operates on [[Json]] values.
 *
 * `JsonPatch` is the JSON-specific counterpart to
 * [[zio.blocks.schema.patch.DynamicPatch]]. It represents a sequence of
 * operations that transform one JSON value into another. Patches are
 * serializable and composable using the `++` operator.
 *
 * ==Relationship to DynamicPatch==
 *
 * This type mirrors [[zio.blocks.schema.patch.DynamicPatch]] but is specialized
 * for JSON's simpler data model:
 *   - JSON has 4 leaf types (string, number, boolean, null) vs 30+
 *     PrimitiveValues
 *   - JSON objects have string keys only (no arbitrary-keyed maps)
 *   - JSON has no native Variant type
 *
 * Bidirectional conversion is supported via `toDynamicPatch` and
 * `fromDynamicPatch`.
 *
 * ==Algebraic Laws==
 *
 * '''L1. Left Identity''': `(JsonPatch.empty ++ p)(j, mode) == p(j, mode)`
 *
 * '''L2. Right Identity''': `(p ++ JsonPatch.empty)(j, mode) == p(j, mode)`
 *
 * '''L3. Associativity''':
 * `((p1 ++ p2) ++ p3)(j, mode) == (p1 ++ (p2 ++ p3))(j, mode)`
 *
 * '''L4. Roundtrip''': `JsonPatch.diff(a, b)(a, Strict) == Right(b)`
 *
 * '''L5. Identity Diff''': `JsonPatch.diff(j, j).isEmpty == true`
 *
 * '''L6. Diff Composition''':
 * `(JsonPatch.diff(a, b) ++ JsonPatch.diff(b, c))(a, Strict) == Right(c)`
 *
 * '''L7. Lenient Subsumes Strict''': If `p(j, Strict) == Right(r)` then
 * `p(j, Lenient) == Right(r)`
 *
 * @param ops
 *   The sequence of patch operations
 * @see
 *   [[zio.blocks.schema.patch.DynamicPatch]] for the underlying implementation
 * @see
 *   [[JsonPatchMode]] for patch application modes
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) {
  import JsonPatch._

  def apply(json: Json, mode: JsonPatchMode = JsonPatchMode.Strict): Either[JsonError, Json] = {
    var current: Json                  = json
    var idx                            = 0
    var error: Either[JsonError, Unit] = Right(())

    while (idx < ops.length && error.isRight) {
      val op = ops(idx)
      applyOp(current, op.path.nodes, op.op, mode) match {
        case Right(updated) => current = updated
        case Left(err)      =>
          mode match {
            case JsonPatchMode.Strict  => error = Left(err)
            case JsonPatchMode.Lenient => ()
            case JsonPatchMode.Clobber => ()
          }
      }
      idx += 1
    }
    error.map(_ => current)
  }

  def ++(that: JsonPatch): JsonPatch = JsonPatch(ops ++ that.ops)

  def isEmpty: Boolean = ops.isEmpty

  def toDynamicPatch: DynamicPatch = {
    val dynamicOps = ops.map { op =>
      DynamicPatch.DynamicPatchOp(op.path, opToDynamicOperation(op.op))
    }
    DynamicPatch(dynamicOps)
  }

  private def opToDynamicOperation(op: Op): DynamicPatch.Operation = op match {
    case Op.Set(value)             => DynamicPatch.Operation.Set(jsonToDynamicValue(value))
    case Op.PrimitiveDelta(primOp) =>
      val dynamicPrimOp = primOp match {
        case PrimitiveOp.NumberDelta(delta)    => DynamicPatch.PrimitiveOp.BigDecimalDelta(delta)
        case PrimitiveOp.StringEdit(stringOps) =>
          DynamicPatch.PrimitiveOp.StringEdit(stringOps.map {
            case StringOp.Insert(idx, text)      => DynamicPatch.StringOp.Insert(idx, text)
            case StringOp.Delete(idx, len)       => DynamicPatch.StringOp.Delete(idx, len)
            case StringOp.Append(text)           => DynamicPatch.StringOp.Append(text)
            case StringOp.Modify(idx, len, text) => DynamicPatch.StringOp.Modify(idx, len, text)
          })
      }
      DynamicPatch.Operation.PrimitiveDelta(dynamicPrimOp)
    case Op.ArrayEdit(arrayOps) =>
      DynamicPatch.Operation.SequenceEdit(arrayOps.map {
        case ArrayOp.Insert(idx, values)   => DynamicPatch.SeqOp.Insert(idx, values.map(jsonToDynamicValue))
        case ArrayOp.Append(values)        => DynamicPatch.SeqOp.Append(values.map(jsonToDynamicValue))
        case ArrayOp.Delete(idx, count)    => DynamicPatch.SeqOp.Delete(idx, count)
        case ArrayOp.Modify(idx, nestedOp) => DynamicPatch.SeqOp.Modify(idx, opToDynamicOperation(nestedOp))
      })
    case Op.ObjectEdit(objectOps) =>
      DynamicPatch.Operation.MapEdit(objectOps.map {
        case ObjectOp.Add(key, value) =>
          DynamicPatch.MapOp.Add(DynamicValue.Primitive(PrimitiveValue.String(key)), jsonToDynamicValue(value))
        case ObjectOp.Remove(key) =>
          DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(key)))
        case ObjectOp.Modify(key, patch) =>
          DynamicPatch.MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String(key)), patch.toDynamicPatch)
      })
    case Op.Nested(nestedPatch) => DynamicPatch.Operation.Patch(nestedPatch.toDynamicPatch)
  }

  private def jsonToDynamicValue(json: Json): DynamicValue = json match {
    case Json.Null        => DynamicValue.Primitive(PrimitiveValue.Unit)
    case Json.Bool(b)     => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case Json.Num(s)      => DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(s)))
    case Json.Str(s)      => DynamicValue.Primitive(PrimitiveValue.String(s))
    case Json.Arr(elems)  => DynamicValue.Sequence(elems.map(jsonToDynamicValue))
    case Json.Obj(fields) =>
      DynamicValue.Map(fields.map { case (k, v) =>
        (DynamicValue.Primitive(PrimitiveValue.String(k)), jsonToDynamicValue(v))
      })
  }
}

object JsonPatch {

  val empty: JsonPatch = JsonPatch(Vector.empty)

  def root(op: Op): JsonPatch = JsonPatch(Vector(JsonPatchOp(DynamicOptic.root, op)))

  def apply(path: DynamicOptic, op: Op): JsonPatch = JsonPatch(Vector(JsonPatchOp(path, op)))

  def diff(oldJson: Json, newJson: Json): JsonPatch = JsonDiffer.diff(oldJson, newJson)

  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    val builder                  = Vector.newBuilder[JsonPatchOp]
    var error: Option[JsonError] = None

    patch.ops.foreach { dynamicOp =>
      if (error.isEmpty) {
        dynamicOperationToOp(dynamicOp.operation) match {
          case Right(op) => builder += JsonPatchOp(dynamicOp.path, op)
          case Left(err) => error = Some(err)
        }
      }
    }
    error.toLeft(JsonPatch(builder.result()))
  }

  private def dynamicOperationToOp(operation: DynamicPatch.Operation): Either[JsonError, Op] =
    operation match {
      case DynamicPatch.Operation.Set(value)             => dynamicValueToJson(value).map(Op.Set.apply)
      case DynamicPatch.Operation.PrimitiveDelta(primOp) =>
        dynamicPrimitiveOpToJsonPrimitiveOp(primOp).map(Op.PrimitiveDelta.apply)
      case DynamicPatch.Operation.SequenceEdit(seqOps) => convertSeqOps(seqOps).map(Op.ArrayEdit.apply)
      case DynamicPatch.Operation.MapEdit(mapOps)      => convertMapOps(mapOps).map(Op.ObjectEdit.apply)
      case DynamicPatch.Operation.Patch(nestedPatch)   => fromDynamicPatch(nestedPatch).map(Op.Nested.apply)
    }

  private def dynamicPrimitiveOpToJsonPrimitiveOp(op: DynamicPatch.PrimitiveOp): Either[JsonError, PrimitiveOp] =
    op match {
      case DynamicPatch.PrimitiveOp.IntDelta(delta)        => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.LongDelta(delta)       => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.DoubleDelta(delta)     => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.FloatDelta(delta)      => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toDouble)))
      case DynamicPatch.PrimitiveOp.ShortDelta(delta)      => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.ByteDelta(delta)       => Right(PrimitiveOp.NumberDelta(BigDecimal(delta.toInt)))
      case DynamicPatch.PrimitiveOp.BigIntDelta(delta)     => Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) => Right(PrimitiveOp.NumberDelta(delta))
      case DynamicPatch.PrimitiveOp.StringEdit(strOps)     =>
        Right(PrimitiveOp.StringEdit(strOps.map {
          case DynamicPatch.StringOp.Insert(idx, text)      => StringOp.Insert(idx, text)
          case DynamicPatch.StringOp.Delete(idx, len)       => StringOp.Delete(idx, len)
          case DynamicPatch.StringOp.Append(text)           => StringOp.Append(text)
          case DynamicPatch.StringOp.Modify(idx, len, text) => StringOp.Modify(idx, len, text)
        }))
      case _ =>
        Left(JsonError.incompatibleDynamicPatch(s"Unsupported primitive operation: ${op.getClass.getSimpleName}"))
    }

  private def convertSeqOps(seqOps: Vector[DynamicPatch.SeqOp]): Either[JsonError, Vector[ArrayOp]] = {
    val builder                  = Vector.newBuilder[ArrayOp]
    var error: Option[JsonError] = None

    seqOps.foreach { seqOp =>
      if (error.isEmpty) seqOp match {
        case DynamicPatch.SeqOp.Insert(idx, values) =>
          val converted = values.map(dynamicValueToJson)
          converted.collectFirst { case Left(e) => e } match {
            case Some(e) => error = Some(e)
            case None    => builder += ArrayOp.Insert(idx, converted.collect { case Right(j) => j })
          }
        case DynamicPatch.SeqOp.Append(values) =>
          val converted = values.map(dynamicValueToJson)
          converted.collectFirst { case Left(e) => e } match {
            case Some(e) => error = Some(e)
            case None    => builder += ArrayOp.Append(converted.collect { case Right(j) => j })
          }
        case DynamicPatch.SeqOp.Delete(idx, count)    => builder += ArrayOp.Delete(idx, count)
        case DynamicPatch.SeqOp.Modify(idx, nestedOp) =>
          dynamicOperationToOp(nestedOp) match {
            case Right(op) => builder += ArrayOp.Modify(idx, op)
            case Left(err) => error = Some(err)
          }
      }
    }
    error.toLeft(builder.result())
  }

  private def convertMapOps(mapOps: Vector[DynamicPatch.MapOp]): Either[JsonError, Vector[ObjectOp]] = {
    val builder                  = Vector.newBuilder[ObjectOp]
    var error: Option[JsonError] = None

    mapOps.foreach { mapOp =>
      if (error.isEmpty) mapOp match {
        case DynamicPatch.MapOp.Add(key, value) =>
          getStringKey(key) match {
            case Some(k) =>
              dynamicValueToJson(value) match {
                case Right(v) => builder += ObjectOp.Add(k, v)
                case Left(e)  => error = Some(e)
              }
            case None => error = Some(JsonError.incompatibleDynamicPatch("Map key must be a string"))
          }
        case DynamicPatch.MapOp.Remove(key) =>
          getStringKey(key) match {
            case Some(k) => builder += ObjectOp.Remove(k)
            case None    => error = Some(JsonError.incompatibleDynamicPatch("Map key must be a string"))
          }
        case DynamicPatch.MapOp.Modify(key, nestedPatch) =>
          getStringKey(key) match {
            case Some(k) =>
              fromDynamicPatch(nestedPatch) match {
                case Right(p) => builder += ObjectOp.Modify(k, p)
                case Left(e)  => error = Some(e)
              }
            case None => error = Some(JsonError.incompatibleDynamicPatch("Map key must be a string"))
          }
      }
    }
    error.toLeft(builder.result())
  }

  private def getStringKey(key: DynamicValue): Option[String] = key match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
    case _                                                => None
  }

  private def dynamicValueToJson(value: DynamicValue): Either[JsonError, Json] = value match {
    case DynamicValue.Primitive(pv)   => primitiveValueToJson(pv)
    case DynamicValue.Sequence(elems) =>
      val converted = elems.map(dynamicValueToJson)
      converted.collectFirst { case Left(e) => e }.toLeft(Json.Arr(converted.collect { case Right(j) => j }))
    case DynamicValue.Map(entries) =>
      val builder                  = Vector.newBuilder[(String, Json)]
      var error: Option[JsonError] = None
      entries.foreach { case (k, v) =>
        if (error.isEmpty) getStringKey(k) match {
          case Some(key) =>
            dynamicValueToJson(v) match {
              case Right(json) => builder += (key -> json)
              case Left(e)     => error = Some(e)
            }
          case None => error = Some(JsonError.incompatibleDynamicPatch("Map key must be a string"))
        }
      }
      error.toLeft(Json.Obj(builder.result()))
    case DynamicValue.Record(fields) =>
      val builder                  = Vector.newBuilder[(String, Json)]
      var error: Option[JsonError] = None
      fields.foreach { case (name, v) =>
        if (error.isEmpty) dynamicValueToJson(v) match {
          case Right(json) => builder += (name -> json)
          case Left(e)     => error = Some(e)
        }
      }
      error.toLeft(Json.Obj(builder.result()))
    case DynamicValue.Variant(_, _) =>
      Left(JsonError.incompatibleDynamicPatch("Variant types are not supported in JSON"))
  }

  private def primitiveValueToJson(pv: PrimitiveValue): Either[JsonError, Json] = pv match {
    case PrimitiveValue.Unit           => Right(Json.Null)
    case PrimitiveValue.Boolean(b)     => Right(Json.Bool(b))
    case PrimitiveValue.String(s)      => Right(Json.Str(s))
    case PrimitiveValue.Int(i)         => Right(Json.Num(i.toString))
    case PrimitiveValue.Long(l)        => Right(Json.Num(l.toString))
    case PrimitiveValue.Float(f)       => Right(Json.Num(f.toString))
    case PrimitiveValue.Double(d)      => Right(Json.Num(d.toString))
    case PrimitiveValue.Short(s)       => Right(Json.Num(s.toString))
    case PrimitiveValue.Byte(b)        => Right(Json.Num(b.toString))
    case PrimitiveValue.Char(c)        => Right(Json.Str(c.toString))
    case PrimitiveValue.BigInt(bi)     => Right(Json.Num(bi.toString))
    case PrimitiveValue.BigDecimal(bd) => Right(Json.Num(bd.toString))
    case _                             => Left(JsonError.incompatibleDynamicPatch(s"Unsupported primitive type: ${pv.getClass.getSimpleName}"))
  }

  private def applyOp(
    json: Json,
    path: IndexedSeq[DynamicOptic.Node],
    operation: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Json] =
    if (path.isEmpty) applyOperation(json, operation, mode)
    else navigateAndApply(json, path, 0, operation, mode)

  private def navigateAndApply(
    json: Json,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Json] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        json match {
          case obj: Json.Obj =>
            val fieldIdx = obj.fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) Left(JsonError.missingField(name))
            else {
              val (fieldName, fieldValue) = obj.fields(fieldIdx)
              val result                  =
                if (isLast) applyOperation(fieldValue, operation, mode)
                else navigateAndApply(fieldValue, path, pathIdx + 1, operation, mode)
              result.map(newValue => Json.Obj(obj.fields.updated(fieldIdx, (fieldName, newValue))))
            }
          case _ => Left(JsonError.typeMismatch("Object", json.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        json match {
          case arr: Json.Arr =>
            if (index < 0 || index >= arr.elements.length) Left(JsonError.indexOutOfBounds(index, arr.elements.length))
            else {
              val element = arr.elements(index)
              val result  =
                if (isLast) applyOperation(element, operation, mode)
                else navigateAndApply(element, path, pathIdx + 1, operation, mode)
              result.map(newElement => Json.Arr(arr.elements.updated(index, newElement)))
            }
          case _ => Left(JsonError.typeMismatch("Array", json.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        getStringKey(key) match {
          case Some(keyStr) =>
            json match {
              case obj: Json.Obj =>
                val fieldIdx = obj.fields.indexWhere(_._1 == keyStr)
                if (fieldIdx < 0) Left(JsonError.keyNotFound(keyStr))
                else {
                  val (k, v) = obj.fields(fieldIdx)
                  val result =
                    if (isLast) applyOperation(v, operation, mode)
                    else navigateAndApply(v, path, pathIdx + 1, operation, mode)
                  result.map(newValue => Json.Obj(obj.fields.updated(fieldIdx, (k, newValue))))
                }
              case _ => Left(JsonError.typeMismatch("Object", json.getClass.getSimpleName))
            }
          case None => Left(JsonError.incompatibleDynamicPatch("Map key must be a string"))
        }

      case DynamicOptic.Node.Elements =>
        json match {
          case arr: Json.Arr =>
            if (arr.elements.isEmpty) {
              if (mode == JsonPatchMode.Strict) Left(JsonError("Cannot apply operation to empty array"))
              else Right(json)
            } else if (isLast) applyToAllElements(arr.elements, operation, mode).map(Json.Arr(_))
            else navigateAllElements(arr.elements, path, pathIdx + 1, operation, mode).map(Json.Arr(_))
          case _ => Left(JsonError.typeMismatch("Array", json.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Wrapped =>
        if (isLast) applyOperation(json, operation, mode)
        else navigateAndApply(json, path, pathIdx + 1, operation, mode)

      case _ => Left(JsonError.unsupportedOperation(s"Path node type not supported: ${node.getClass.getSimpleName}"))
    }
  }

  private def applyToAllElements(
    elements: Vector[Json],
    operation: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Vector[Json]] = {
    val results                  = new Array[Json](elements.length)
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < elements.length && error.isEmpty) {
      applyOperation(elements(idx), operation, mode) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode == JsonPatchMode.Strict) error = Some(err.prependIndex(idx))
          else results(idx) = elements(idx)
      }
      idx += 1
    }
    error.toLeft(results.toVector)
  }

  private def navigateAllElements(
    elements: Vector[Json],
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    operation: Op,
    mode: JsonPatchMode
  ): Either[JsonError, Vector[Json]] = {
    val results                  = new Array[Json](elements.length)
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < elements.length && error.isEmpty) {
      navigateAndApply(elements(idx), path, pathIdx, operation, mode) match {
        case Right(updated) => results(idx) = updated
        case Left(err)      =>
          if (mode == JsonPatchMode.Strict) error = Some(err.prependIndex(idx))
          else results(idx) = elements(idx)
      }
      idx += 1
    }
    error.toLeft(results.toVector)
  }

  private def applyOperation(json: Json, operation: Op, mode: JsonPatchMode): Either[JsonError, Json] =
    operation match {
      case Op.Set(newValue)          => Right(newValue)
      case Op.PrimitiveDelta(primOp) => applyPrimitiveDelta(json, primOp)
      case Op.ArrayEdit(arrayOps)    => applyArrayEdit(json, arrayOps, mode)
      case Op.ObjectEdit(objectOps)  => applyObjectEdit(json, objectOps, mode)
      case Op.Nested(nestedPatch)    => nestedPatch.apply(json, mode)
    }

  private def applyPrimitiveDelta(json: Json, op: PrimitiveOp): Either[JsonError, Json] =
    (json, op) match {
      case (Json.Num(s), PrimitiveOp.NumberDelta(delta)) => Right(Json.Num((BigDecimal(s) + delta).toString))
      case (Json.Str(s), PrimitiveOp.StringEdit(strOps)) => applyStringEdits(s, strOps).map(Json.Str.apply)
      case _                                             => Left(JsonError.typeMismatch(s"${op.getClass.getSimpleName} target", json.getClass.getSimpleName))
    }

  private def applyStringEdits(str: String, ops: Vector[StringOp]): Either[JsonError, String] = {
    var result                   = str
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      ops(idx) match {
        case StringOp.Insert(index, text) =>
          if (index < 0 || index > result.length) error = Some(JsonError.stringIndexOutOfBounds(index, result.length))
          else result = result.substring(0, index) + text + result.substring(index)
        case StringOp.Delete(index, length) =>
          if (index < 0 || index + length > result.length)
            error = Some(JsonError.invalidRange(index, index + length, result.length))
          else result = result.substring(0, index) + result.substring(index + length)
        case StringOp.Append(text)                => result = result + text
        case StringOp.Modify(index, length, text) =>
          if (index < 0 || index + length > result.length)
            error = Some(JsonError.invalidRange(index, index + length, result.length))
          else result = result.substring(0, index) + text + result.substring(index + length)
      }
      idx += 1
    }
    error.toLeft(result)
  }

  private def applyArrayEdit(json: Json, ops: Vector[ArrayOp], mode: JsonPatchMode): Either[JsonError, Json] =
    json match {
      case arr: Json.Arr => applyArrayOps(arr.elements, ops, mode).map(Json.Arr(_))
      case _             => Left(JsonError.typeMismatch("Array", json.getClass.getSimpleName))
    }

  private def applyArrayOps(
    elements: Vector[Json],
    ops: Vector[ArrayOp],
    mode: JsonPatchMode
  ): Either[JsonError, Vector[Json]] = {
    var result                   = elements
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      applyArrayOp(result, ops(idx), mode) match {
        case Right(updated) => result = updated
        case Left(err)      => if (mode == JsonPatchMode.Strict) error = Some(err)
      }
      idx += 1
    }
    error.toLeft(result)
  }

  private def applyArrayOp(
    elements: Vector[Json],
    op: ArrayOp,
    mode: JsonPatchMode
  ): Either[JsonError, Vector[Json]] =
    op match {
      case ArrayOp.Append(values)        => Right(elements ++ values)
      case ArrayOp.Insert(index, values) =>
        if (index < 0 || index > elements.length) {
          if (mode == JsonPatchMode.Clobber) {
            val clampedIndex    = Math.max(0, Math.min(index, elements.length))
            val (before, after) = elements.splitAt(clampedIndex)
            Right(before ++ values ++ after)
          } else Left(JsonError.indexOutOfBounds(index, elements.length))
        } else {
          val (before, after) = elements.splitAt(index)
          Right(before ++ values ++ after)
        }
      case ArrayOp.Delete(index, count) =>
        if (index < 0 || index + count > elements.length) {
          if (mode == JsonPatchMode.Clobber) {
            val clampedIndex = Math.max(0, Math.min(index, elements.length))
            val clampedEnd   = Math.max(0, Math.min(index + count, elements.length))
            Right(elements.take(clampedIndex) ++ elements.drop(clampedEnd))
          } else Left(JsonError.invalidRange(index, index + count, elements.length))
        } else Right(elements.take(index) ++ elements.drop(index + count))
      case ArrayOp.Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length) Left(JsonError.indexOutOfBounds(index, elements.length))
        else applyOperation(elements(index), nestedOp, mode).map(newElement => elements.updated(index, newElement))
    }

  private def applyObjectEdit(json: Json, ops: Vector[ObjectOp], mode: JsonPatchMode): Either[JsonError, Json] =
    json match {
      case obj: Json.Obj => applyObjectOps(obj.fields, ops, mode).map(Json.Obj(_))
      case _             => Left(JsonError.typeMismatch("Object", json.getClass.getSimpleName))
    }

  private def applyObjectOps(
    fields: Vector[(String, Json)],
    ops: Vector[ObjectOp],
    mode: JsonPatchMode
  ): Either[JsonError, Vector[(String, Json)]] = {
    var result                   = fields
    var idx                      = 0
    var error: Option[JsonError] = None

    while (idx < ops.length && error.isEmpty) {
      applyObjectOp(result, ops(idx), mode) match {
        case Right(updated) => result = updated
        case Left(err)      => if (mode == JsonPatchMode.Strict) error = Some(err)
      }
      idx += 1
    }
    error.toLeft(result)
  }

  private def applyObjectOp(
    fields: Vector[(String, Json)],
    op: ObjectOp,
    mode: JsonPatchMode
  ): Either[JsonError, Vector[(String, Json)]] =
    op match {
      case ObjectOp.Add(key, value) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          if (mode == JsonPatchMode.Clobber) Right(fields.updated(existingIdx, (key, value)))
          else Left(JsonError.fieldExists(key))
        } else Right(fields :+ (key, value))
      case ObjectOp.Remove(key) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          if (mode == JsonPatchMode.Clobber) Right(fields)
          else Left(JsonError.keyNotFound(key))
        } else Right(fields.take(existingIdx) ++ fields.drop(existingIdx + 1))
      case ObjectOp.Modify(key, nestedPatch) =>
        val existingIdx = fields.indexWhere(_._1 == key)
        if (existingIdx < 0) Left(JsonError.keyNotFound(key))
        else {
          val (k, v) = fields(existingIdx)
          nestedPatch.apply(v, mode).map(newValue => fields.updated(existingIdx, (k, newValue)))
        }
    }

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
