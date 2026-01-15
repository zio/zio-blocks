package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.patch.{Differ, DynamicPatch}

/**
 * A patch that operates on [[Json]] values.
 *
 * JsonPatch is composable (`++` with `empty`) and can be derived via [[JsonPatch.diff]].
 * Application supports [[JsonPatchMode]] (Strict / Lenient / Clobber).
 */
final case class JsonPatch(ops: Vector[JsonPatch.JsonPatchOp]) { self =>
  def isEmpty: Boolean = ops.isEmpty

  /** Compose two patches (apply this first, then that). */
  def ++(that: JsonPatch): JsonPatch = JsonPatch(self.ops ++ that.ops)

  /**
   * Apply this patch to a Json value.
   *
   * @return Either an error or the patched JSON
   */
  def apply(json: Json, mode: JsonPatchMode = JsonPatchMode.Strict): Either[JsonError, Json] =
    toDynamicPatch.flatMap { dp =>
      val dv0 = JsonPatch.jsonToDynamicForPatch(json)
      dp(dv0, JsonPatchMode.toPatchMode(mode))
        .left
        .map(JsonError.fromSchemaError)
        .flatMap(JsonPatch.dynamicToJsonFromPatch)
    }

  /**
   * Converts this JsonPatch into a [[zio.blocks.schema.patch.DynamicPatch]].
   *
   * The produced DynamicPatch expects JSON objects to be represented as `DynamicValue.Map`
   * with string keys (see internal `jsonToDynamicForPatch`).
   */
  def toDynamicPatch: Either[JsonError, DynamicPatch] = {
    val out = Vector.newBuilder[DynamicPatch.DynamicPatchOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < ops.length && err.isEmpty) {
      JsonPatch.opToDynamic(ops(idx)) match {
        case Left(e)   => err = Some(e)
        case Right(op) => out += op
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(DynamicPatch(out.result()))
    }
  }
}

object JsonPatch {
  // ===========================================================================
  // Public constructors
  // ===========================================================================

  val empty: JsonPatch = JsonPatch(Vector.empty)

  def root(operation: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(DynamicOptic.root, operation)))

  def apply(path: DynamicOptic, operation: Op): JsonPatch =
    JsonPatch(Vector(JsonPatchOp(path, operation)))

  /**
   * Compute a patch that transforms `source` into `target`.
   *
   * This uses the existing DynamicValue diffing logic on a JSON-shaped DynamicValue
   * (objects as maps, arrays as sequences).
   */
  def diff(source: Json, target: Json): JsonPatch = {
    if (source == target) return JsonPatch.empty
    val dp = Differ.diff(jsonToDynamicForPatch(source), jsonToDynamicForPatch(target))
    fromDynamicPatch(dp) match {
      case Right(p) => p
      case Left(_)  => JsonPatch.root(Op.Set(target)) // safe fallback for roundtrip
    }
  }

  /**
   * Convert a DynamicPatch into a JsonPatch.
   *
   * May fail if the DynamicPatch contains operations not representable for JSON.
   */
  def fromDynamicPatch(patch: DynamicPatch): Either[JsonError, JsonPatch] = {
    val out = Vector.newBuilder[JsonPatchOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < patch.ops.length && err.isEmpty) {
      dynamicOpToJson(patch.ops(idx)) match {
        case Left(e)   => err = Some(e)
        case Right(op) => out += op
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(JsonPatch(out.result()))
    }
  }

  // ===========================================================================
  // Core types
  // ===========================================================================

  final case class JsonPatchOp(path: DynamicOptic, operation: Op)

  sealed trait Op extends Product with Serializable
  object Op {
    final case class Set(value: Json) extends Op
    final case class PrimitiveDelta(op: PrimitiveOp) extends Op
    final case class ArrayEdit(ops: Vector[ArrayOp]) extends Op
    final case class ObjectEdit(ops: Vector[ObjectOp]) extends Op
    final case class Nested(patch: JsonPatch) extends Op
  }

  sealed trait PrimitiveOp extends Product with Serializable
  object PrimitiveOp {
    final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp
    final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  }

  sealed trait StringOp extends Product with Serializable
  object StringOp {
    final case class Insert(index: Int, text: String) extends StringOp
    final case class Delete(index: Int, length: Int) extends StringOp
    final case class Append(text: String) extends StringOp
    final case class Modify(index: Int, length: Int, text: String) extends StringOp
  }

  sealed trait ArrayOp extends Product with Serializable
  object ArrayOp {
    final case class Insert(index: Int, values: Vector[Json]) extends ArrayOp
    final case class Append(values: Vector[Json]) extends ArrayOp
    final case class Delete(index: Int, count: Int) extends ArrayOp
    final case class Modify(index: Int, op: Op) extends ArrayOp
  }

  sealed trait ObjectOp extends Product with Serializable
  object ObjectOp {
    final case class Add(key: String, value: Json) extends ObjectOp
    final case class Remove(key: String) extends ObjectOp
    final case class Modify(key: String, patch: JsonPatch) extends ObjectOp
  }

  // ===========================================================================
  // Internal: JSON-shaped DynamicValue representation for diff/apply
  // ===========================================================================

  private[json] def jsonToDynamicForPatch(json: Json): DynamicValue =
    json match {
      case Json.Null =>
        DynamicValue.Primitive(PrimitiveValue.Unit)
      case Json.Boolean(v) =>
        DynamicValue.Primitive(PrimitiveValue.Boolean(v))
      case Json.Number(v) =>
        DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(v)))
      case Json.String(v) =>
        DynamicValue.Primitive(PrimitiveValue.String(v))
      case Json.Array(elems) =>
        DynamicValue.Sequence(elems.map(jsonToDynamicForPatch))
      case Json.Object(flds) =>
        DynamicValue.Map(
          flds.map { case (k, v) =>
            DynamicValue.Primitive(PrimitiveValue.String(k)) -> jsonToDynamicForPatch(v)
          }
        )
    }

  private[json] def dynamicToJsonFromPatch(value: DynamicValue): Either[JsonError, Json] =
    value match {
      case DynamicValue.Primitive(pv) =>
        // Delegate primitive coverage to the existing DynamicValue->Json interop,
        // which defines how non-JSON Dynamic primitives are represented.
        Right(Json.fromDynamicValue(DynamicValue.Primitive(pv)))
      case DynamicValue.Sequence(elems) =>
        dynamicVectorToJson(elems).map(v => Json.Array(v))
      case DynamicValue.Map(entries) =>
        val out = Vector.newBuilder[(String, Json)]
        var idx = 0
        var err: Option[JsonError] = None
        while (idx < entries.length && err.isEmpty) {
          val (k, v) = entries(idx)
          keyToString(k) match {
            case Left(e) => err = Some(e)
            case Right(ks) =>
              dynamicToJsonFromPatch(v) match {
                case Left(e)  => err = Some(e)
                case Right(j) => out += (ks -> j)
              }
          }
          idx += 1
        }
        err match {
          case Some(e) => Left(e)
          case None    => Right(Json.Object(out.result()))
        }
      case DynamicValue.Record(fields) =>
        // Records are not part of the "json-shaped" representation used here.
        // Still, we can translate it into a JSON object losslessly.
        val out = Vector.newBuilder[(String, Json)]
        var idx = 0
        var err: Option[JsonError] = None
        while (idx < fields.length && err.isEmpty) {
          val (k, v) = fields(idx)
          dynamicToJsonFromPatch(v) match {
            case Left(e)  => err = Some(e)
            case Right(j) => out += (k -> j)
          }
          idx += 1
        }
        err match {
          case Some(e) => Left(e)
          case None    => Right(Json.Object(out.result()))
        }
      case DynamicValue.Variant(name, _) =>
        // Variants have no direct JSON equivalent in this patch model.
        // Fail fast to avoid silently changing semantics.
        Left(JsonError(s"DynamicValue.Variant($name, ...) is not representable as Json in JsonPatch"))
    }

  private def keyToString(key: DynamicValue): Either[JsonError, String] =
    key match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
      case other =>
        Left(JsonError(s"JsonPatch only supports string keys, got: ${other.getClass.getSimpleName}"))
    }

  // ===========================================================================
  // Internal: JsonPatch <-> DynamicPatch conversions
  // ===========================================================================

  private[json] def opToDynamic(op: JsonPatchOp): Either[JsonError, DynamicPatch.DynamicPatchOp] =
    operationToDynamic(op.operation).map { dyn =>
      DynamicPatch.DynamicPatchOp(op.path, dyn)
    }

  private def operationToDynamic(op: Op): Either[JsonError, DynamicPatch.Operation] =
    op match {
      case Op.Set(value) =>
        Right(DynamicPatch.Operation.Set(jsonToDynamicForPatch(value)))

      case Op.PrimitiveDelta(pop) =>
        primitiveToDynamic(pop).map(DynamicPatch.Operation.PrimitiveDelta(_))

      case Op.ArrayEdit(ops) =>
        arrayOpsToDynamic(ops).map(v => DynamicPatch.Operation.SequenceEdit(v))

      case Op.ObjectEdit(ops) =>
        objectOpsToDynamic(ops).map(v => DynamicPatch.Operation.MapEdit(v))

      case Op.Nested(patch) =>
        patch.toDynamicPatch.map(DynamicPatch.Operation.Patch(_))
    }

  private def primitiveToDynamic(op: PrimitiveOp): Either[JsonError, DynamicPatch.PrimitiveOp] =
    op match {
      case PrimitiveOp.NumberDelta(delta) =>
        Right(DynamicPatch.PrimitiveOp.BigDecimalDelta(delta))
      case PrimitiveOp.StringEdit(ops)   =>
        Right(DynamicPatch.PrimitiveOp.StringEdit(ops.map(stringOpToDynamic)))
    }

  private def stringOpToDynamic(op: StringOp): DynamicPatch.StringOp =
    op match {
      case StringOp.Insert(index, text)       => DynamicPatch.StringOp.Insert(index, text)
      case StringOp.Delete(index, length)     => DynamicPatch.StringOp.Delete(index, length)
      case StringOp.Append(text)              => DynamicPatch.StringOp.Append(text)
      case StringOp.Modify(index, length, t)  => DynamicPatch.StringOp.Modify(index, length, t)
    }

  private def dynamicStringOpToJson(op: DynamicPatch.StringOp): StringOp =
    op match {
      case DynamicPatch.StringOp.Insert(index, text)      => StringOp.Insert(index, text)
      case DynamicPatch.StringOp.Delete(index, length)    => StringOp.Delete(index, length)
      case DynamicPatch.StringOp.Append(text)             => StringOp.Append(text)
      case DynamicPatch.StringOp.Modify(index, length, t) => StringOp.Modify(index, length, t)
    }

  private def arrayOpsToDynamic(ops: Vector[ArrayOp]): Either[JsonError, Vector[DynamicPatch.SeqOp]] = {
    val out = Vector.newBuilder[DynamicPatch.SeqOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < ops.length && err.isEmpty) {
      ops(idx) match {
        case ArrayOp.Insert(index, values) =>
          out += DynamicPatch.SeqOp.Insert(index, values.map(jsonToDynamicForPatch))
        case ArrayOp.Append(values) =>
          out += DynamicPatch.SeqOp.Append(values.map(jsonToDynamicForPatch))
        case ArrayOp.Delete(index, count) =>
          out += DynamicPatch.SeqOp.Delete(index, count)
        case ArrayOp.Modify(index, op) =>
          operationToDynamic(op) match {
            case Left(e)   => err = Some(e)
            case Right(d)  => out += DynamicPatch.SeqOp.Modify(index, d)
          }
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(out.result())
    }
  }

  private def objectOpsToDynamic(ops: Vector[ObjectOp]): Either[JsonError, Vector[DynamicPatch.MapOp]] = {
    val out = Vector.newBuilder[DynamicPatch.MapOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < ops.length && err.isEmpty) {
      ops(idx) match {
        case ObjectOp.Add(key, value) =>
          out += DynamicPatch.MapOp.Add(DynamicValue.Primitive(PrimitiveValue.String(key)), jsonToDynamicForPatch(value))
        case ObjectOp.Remove(key) =>
          out += DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String(key)))
        case ObjectOp.Modify(key, patch) =>
          patch.toDynamicPatch match {
            case Left(e) => err = Some(e)
            case Right(p) =>
              out += DynamicPatch.MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String(key)), p)
          }
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(out.result())
    }
  }

  private def dynamicOpToJson(op: DynamicPatch.DynamicPatchOp): Either[JsonError, JsonPatchOp] =
    dynamicOperationToJson(op.operation).map { jop =>
      JsonPatchOp(op.path, jop)
    }

  private def dynamicOperationToJson(op: DynamicPatch.Operation): Either[JsonError, Op] =
    op match {
      case DynamicPatch.Operation.Set(value) =>
        dynamicToJsonFromPatch(value).map(Op.Set(_))

      case DynamicPatch.Operation.PrimitiveDelta(pop) =>
        dynamicPrimitiveToJson(pop).map(Op.PrimitiveDelta(_))

      case DynamicPatch.Operation.SequenceEdit(ops) =>
        dynamicSeqOpsToJson(ops).map(Op.ArrayEdit(_))

      case DynamicPatch.Operation.MapEdit(ops) =>
        dynamicMapOpsToJson(ops).map(Op.ObjectEdit(_))

      case DynamicPatch.Operation.Patch(patch) =>
        fromDynamicPatch(patch).map(Op.Nested(_))
    }

  private def dynamicPrimitiveToJson(op: DynamicPatch.PrimitiveOp): Either[JsonError, PrimitiveOp] =
    op match {
      case DynamicPatch.PrimitiveOp.BigDecimalDelta(delta) =>
        Right(PrimitiveOp.NumberDelta(delta))
      case DynamicPatch.PrimitiveOp.IntDelta(delta) =>
        Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.LongDelta(delta) =>
        Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.DoubleDelta(delta) =>
        Right(PrimitiveOp.NumberDelta(BigDecimal(delta)))
      case DynamicPatch.PrimitiveOp.FloatDelta(delta) =>
        Right(PrimitiveOp.NumberDelta(BigDecimal.decimal(delta)))
      case DynamicPatch.PrimitiveOp.StringEdit(ops) =>
        Right(PrimitiveOp.StringEdit(ops.map(dynamicStringOpToJson)))
      case other =>
        Left(JsonError(s"Unsupported DynamicPatch primitive op for JsonPatch: ${other.getClass.getSimpleName}"))
    }

  private def dynamicSeqOpsToJson(ops: Vector[DynamicPatch.SeqOp]): Either[JsonError, Vector[ArrayOp]] = {
    val out = Vector.newBuilder[ArrayOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < ops.length && err.isEmpty) {
      ops(idx) match {
        case DynamicPatch.SeqOp.Insert(index, values) =>
          dynamicVectorToJson(values) match {
            case Left(e)  => err = Some(e)
            case Right(v) => out += ArrayOp.Insert(index, v)
          }
        case DynamicPatch.SeqOp.Append(values) =>
          dynamicVectorToJson(values) match {
            case Left(e)  => err = Some(e)
            case Right(v) => out += ArrayOp.Append(v)
          }
        case DynamicPatch.SeqOp.Delete(index, count) =>
          out += ArrayOp.Delete(index, count)
        case DynamicPatch.SeqOp.Modify(index, op) =>
          dynamicOperationToJson(op) match {
            case Left(e)   => err = Some(e)
            case Right(jop) => out += ArrayOp.Modify(index, jop)
          }
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(out.result())
    }
  }

  private def dynamicMapOpsToJson(ops: Vector[DynamicPatch.MapOp]): Either[JsonError, Vector[ObjectOp]] = {
    val out = Vector.newBuilder[ObjectOp]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < ops.length && err.isEmpty) {
      ops(idx) match {
        case DynamicPatch.MapOp.Add(key, value) =>
          keyToString(key) match {
            case Left(e) => err = Some(e)
            case Right(k) =>
              dynamicToJsonFromPatch(value) match {
                case Left(e)  => err = Some(e)
                case Right(v) => out += ObjectOp.Add(k, v)
              }
          }
        case DynamicPatch.MapOp.Remove(key) =>
          keyToString(key) match {
            case Left(e) => err = Some(e)
            case Right(k) => out += ObjectOp.Remove(k)
          }
        case DynamicPatch.MapOp.Modify(key, patch) =>
          keyToString(key) match {
            case Left(e) => err = Some(e)
            case Right(k) =>
              fromDynamicPatch(patch) match {
                case Left(e)  => err = Some(e)
                case Right(p) => out += ObjectOp.Modify(k, p)
              }
          }
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(out.result())
    }
  }

  private def dynamicVectorToJson(values: Vector[DynamicValue]): Either[JsonError, Vector[Json]] = {
    val out = Vector.newBuilder[Json]
    var idx = 0
    var err: Option[JsonError] = None
    while (idx < values.length && err.isEmpty) {
      dynamicToJsonFromPatch(values(idx)) match {
        case Left(e)  => err = Some(e)
        case Right(j) => out += j
      }
      idx += 1
    }
    err match {
      case Some(e) => Left(e)
      case None    => Right(out.result())
    }
  }
}

