package zio.blocks.schema

/**
 * DynamicPatch represents an untyped patch that operates on DynamicValue. This
 * is the core implementation that Patch[A] wraps.
 *
 * All operation types are stored in the companion object to avoid polluting the
 * top-level namespace.
 */
final case class DynamicPatch(ops: Vector[DynamicPatch.DynamicPatchOp]) {
  import DynamicPatch._

  /**
   * Apply this patch to a DynamicValue with the specified mode.
   */
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    var result = value
    var i      = 0
    while (i < ops.length) {
      val op = ops(i)
      applyOp(result, op, mode) match {
        case Right(r) => result = r
        case left     =>
          if (mode == PatchMode.Lenient) () // skip and continue
          else return left
      }
      i += 1
    }
    Right(result)
  }

  /**
   * Apply this patch to a DynamicValue with Strict mode.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
    apply(value, PatchMode.Strict)

  /**
   * Compose this patch with another patch sequentially.
   */
  def ++(that: DynamicPatch): DynamicPatch =
    DynamicPatch(this.ops ++ that.ops)
}

object DynamicPatch {

  // ============================================================================
  // Core Types
  // ============================================================================

  /**
   * A single operation in a dynamic patch, consisting of an optic path and an
   * operation.
   */
  final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation)

  // ============================================================================
  // Operation ADT - All operations stored in companion object per jdegoes feedback
  // ============================================================================

  /**
   * Sealed trait for all patch operations.
   */
  sealed trait Operation

  object Operation {

    /** Set a value directly (clobber semantics) */
    final case class Set(value: DynamicValue) extends Operation

    /** Apply a primitive delta operation */
    final case class PrimitiveDelta(op: PrimitiveOp) extends Operation

    /** Apply a sequence of string edit operations */
    final case class StringEdit(ops: Vector[StringOp]) extends Operation

    /** Apply a sequence of sequence edit operations */
    final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation

    /** Apply a sequence of map edit operations */
    final case class MapEdit(ops: Vector[MapOp]) extends Operation

    /** Patch individual fields of a record */
    final case class RecordPatch(fieldOps: Vector[(String, Operation)]) extends Operation
  }

  // ============================================================================
  // PrimitiveOp - Numeric and temporal delta operations
  // ============================================================================

  sealed trait PrimitiveOp

  object PrimitiveOp {
    // Numeric deltas
    final case class IntDelta(delta: Int)               extends PrimitiveOp
    final case class LongDelta(delta: Long)             extends PrimitiveOp
    final case class DoubleDelta(delta: Double)         extends PrimitiveOp
    final case class FloatDelta(delta: Float)           extends PrimitiveOp
    final case class ShortDelta(delta: Short)           extends PrimitiveOp
    final case class ByteDelta(delta: Byte)             extends PrimitiveOp
    final case class BigIntDelta(delta: BigInt)         extends PrimitiveOp
    final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp

    // String edits (LCS-based) - now part of Operation.StringEdit
    final case class StringEditOp(ops: Vector[StringOp]) extends PrimitiveOp

    // Temporal deltas
    final case class InstantDelta(duration: java.time.Duration)  extends PrimitiveOp
    final case class DurationDelta(duration: java.time.Duration) extends PrimitiveOp
    final case class LocalDateDelta(period: java.time.Period)    extends PrimitiveOp
    final case class PeriodDelta(period: java.time.Period)       extends PrimitiveOp
  }

  // ============================================================================
  // StringOp - String edit operations with error handling
  // ============================================================================

  sealed trait StringOp

  object StringOp {

    /** Insert text at index */
    final case class Insert(index: Int, text: String) extends StringOp

    /** Delete length characters starting at index */
    final case class Delete(index: Int, length: Int) extends StringOp

    /**
     * Apply a sequence of string operations with error handling. Returns Either
     * to preserve error information per jdegoes feedback.
     */
    def applyAll(s: String, ops: Vector[StringOp]): Either[SchemaError, String] = {
      var result = s
      var i      = 0
      while (i < ops.length) {
        ops(i) match {
          case Insert(idx, text) =>
            if (idx < 0 || idx > result.length) {
              return Left(SchemaError(SchemaError.IndexOutOfBounds(idx, result.length)))
            }
            result = result.substring(0, idx) + text + result.substring(idx)
          case Delete(idx, len) =>
            if (idx < 0 || idx + len > result.length) {
              return Left(SchemaError(SchemaError.IndexOutOfBounds(idx, result.length)))
            }
            result = result.substring(0, idx) + result.substring(idx + len)
        }
        i += 1
      }
      Right(result)
    }
  }

  // ============================================================================
  // SeqOp - Sequence operations
  // ============================================================================

  sealed trait SeqOp

  object SeqOp {

    /** Insert values at index */
    final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp

    /** Append values to end */
    final case class Append(values: Vector[DynamicValue]) extends SeqOp

    /** Delete count elements starting at index */
    final case class Delete(index: Int, count: Int) extends SeqOp

    /** Modify element at index with nested operation */
    final case class Modify(index: Int, op: Operation) extends SeqOp
  }

  // ============================================================================
  // MapOp - Map operations
  // ============================================================================

  sealed trait MapOp

  object MapOp {

    /** Add key-value pair */
    final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp

    /** Remove key */
    final case class Remove(key: DynamicValue) extends MapOp

    /** Modify value at key with nested operation */
    final case class Modify(key: DynamicValue, op: Operation) extends MapOp
  }

  // ============================================================================
  // Constructors - Comprehensive constructors for all operations per jdegoes feedback
  // ============================================================================

  /** Empty patch (monoid identity) */
  val empty: DynamicPatch = DynamicPatch(Vector.empty)

  /** Create a patch that sets a value at the root */
  def set(value: DynamicValue): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.Set(value))))

  /** Create a patch that sets a value at a specific path */
  def setAt(optic: DynamicOptic, value: DynamicValue): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(optic, Operation.Set(value))))

  /** Create a patch with a single operation at the root */
  def apply(operation: Operation): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, operation)))

  /** Create a patch with a single operation at a specific path */
  def at(optic: DynamicOptic, operation: Operation): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(optic, operation)))

  // Primitive delta constructors
  def incrementInt(delta: Int): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(delta)))))

  def incrementLong(delta: Long): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(delta)))))

  def incrementDouble(delta: Double): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(delta)))))

  // String edit constructors
  def editString(ops: Vector[StringOp]): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.StringEdit(ops))))

  def insertString(index: Int, text: String): DynamicPatch =
    editString(Vector(StringOp.Insert(index, text)))

  def deleteString(index: Int, length: Int): DynamicPatch =
    editString(Vector(StringOp.Delete(index, length)))

  // Sequence edit constructors
  def sequenceEdit(ops: Vector[SeqOp]): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.SequenceEdit(ops))))

  def appendToSequence(values: Vector[DynamicValue]): DynamicPatch =
    sequenceEdit(Vector(SeqOp.Append(values)))

  def insertIntoSequence(index: Int, values: Vector[DynamicValue]): DynamicPatch =
    sequenceEdit(Vector(SeqOp.Insert(index, values)))

  def deleteFromSequence(index: Int, count: Int): DynamicPatch =
    sequenceEdit(Vector(SeqOp.Delete(index, count)))

  // Map edit constructors
  def mapEdit(ops: Vector[MapOp]): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.MapEdit(ops))))

  def addToMap(key: DynamicValue, value: DynamicValue): DynamicPatch =
    mapEdit(Vector(MapOp.Add(key, value)))

  def removeFromMap(key: DynamicValue): DynamicPatch =
    mapEdit(Vector(MapOp.Remove(key)))

  // Record field constructors
  def recordPatch(fieldOps: Vector[(String, Operation)]): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, Operation.RecordPatch(fieldOps))))

  def setField(fieldName: String, value: DynamicValue): DynamicPatch =
    recordPatch(Vector((fieldName, Operation.Set(value))))

  // ============================================================================
  // Application Logic
  // ============================================================================

  private def applyOp(
    value: DynamicValue,
    patchOp: DynamicPatchOp,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] =
    if (patchOp.optic.nodes.isEmpty) {
      applyOperation(value, patchOp.operation, mode)
    } else {
      navigateAndApply(value, patchOp.optic.nodes.toList, patchOp.operation, mode)
    }

  private[schema] def applyOperation(
    value: DynamicValue,
    operation: Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = operation match {
    case Operation.Set(newValue) =>
      Right(newValue)

    case Operation.PrimitiveDelta(op) =>
      applyPrimitiveOp(value, op, mode)

    case Operation.StringEdit(ops) =>
      value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          StringOp.applyAll(s, ops).map(r => DynamicValue.Primitive(PrimitiveValue.String(r)))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("String", value.getClass.getSimpleName)))
      }

    case Operation.SequenceEdit(ops) =>
      value match {
        case DynamicValue.Sequence(elements) =>
          applySeqOps(elements, ops, mode).map(DynamicValue.Sequence(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Sequence", value.getClass.getSimpleName)))
      }

    case Operation.MapEdit(ops) =>
      value match {
        case DynamicValue.Map(entries) =>
          applyMapOps(entries, ops, mode).map(DynamicValue.Map(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Map", value.getClass.getSimpleName)))
      }

    case Operation.RecordPatch(fieldOps) =>
      value match {
        case DynamicValue.Record(fields) =>
          applyRecordPatch(fields, fieldOps, mode).map(DynamicValue.Record(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Record", value.getClass.getSimpleName)))
      }
  }

  private def applyPrimitiveOp(
    value: DynamicValue,
    op: PrimitiveOp,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = (value, op) match {
    case (DynamicValue.Primitive(PrimitiveValue.Int(v)), PrimitiveOp.IntDelta(delta)) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Int(v + delta)))
    case (DynamicValue.Primitive(PrimitiveValue.Long(v)), PrimitiveOp.LongDelta(delta)) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Long(v + delta)))
    case (DynamicValue.Primitive(PrimitiveValue.Double(v)), PrimitiveOp.DoubleDelta(delta)) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Double(v + delta)))
    case (DynamicValue.Primitive(PrimitiveValue.Float(v)), PrimitiveOp.FloatDelta(delta)) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Float(v + delta)))
    case (DynamicValue.Primitive(PrimitiveValue.String(s)), PrimitiveOp.StringEditOp(ops)) =>
      StringOp.applyAll(s, ops).map(r => DynamicValue.Primitive(PrimitiveValue.String(r)))
    case _ =>
      if (mode == PatchMode.Lenient) Right(value)
      else Left(SchemaError(SchemaError.TypeMismatch("matching primitive", value.getClass.getSimpleName)))
  }

  private def applySeqOps(
    elements: Vector[DynamicValue],
    ops: Vector[SeqOp],
    mode: PatchMode
  ): Either[SchemaError, Vector[DynamicValue]] = {
    var result = elements
    var i      = 0
    while (i < ops.length) {
      ops(i) match {
        case SeqOp.Insert(idx, values) =>
          if (idx < 0 || idx > result.length) {
            if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.IndexOutOfBounds(idx, result.length)))
          } else {
            val (prefix, suffix) = result.splitAt(idx)
            result = prefix ++ values ++ suffix
          }
        case SeqOp.Append(values) =>
          result = result ++ values
        case SeqOp.Delete(idx, count) =>
          if (idx < 0 || idx + count > result.length) {
            if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.IndexOutOfBounds(idx, result.length)))
          } else {
            result = result.take(idx) ++ result.drop(idx + count)
          }
        case SeqOp.Modify(idx, op) =>
          if (idx < 0 || idx >= result.length) {
            if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.IndexOutOfBounds(idx, result.length)))
          } else {
            applyOperation(result(idx), op, mode) match {
              case Right(modified) => result = result.updated(idx, modified)
              case Left(err)       => if (mode != PatchMode.Lenient) return Left(err)
            }
          }
      }
      i += 1
    }
    Right(result)
  }

  private def applyMapOps(
    entries: Vector[(DynamicValue, DynamicValue)],
    ops: Vector[MapOp],
    mode: PatchMode
  ): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
    var result = entries
    var i      = 0
    while (i < ops.length) {
      ops(i) match {
        case MapOp.Add(key, value) =>
          val idx = result.indexWhere(_._1 == key)
          if (idx >= 0) {
            if (mode == PatchMode.Clobber) result = result.updated(idx, (key, value))
            else if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.KeyAlreadyExists(key.toString)))
          } else {
            result = result :+ (key, value)
          }
        case MapOp.Remove(key) =>
          val idx = result.indexWhere(_._1 == key)
          if (idx < 0) {
            if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
          } else {
            result = result.take(idx) ++ result.drop(idx + 1)
          }
        case MapOp.Modify(key, op) =>
          val idx = result.indexWhere(_._1 == key)
          if (idx < 0) {
            if (mode != PatchMode.Lenient)
              return Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
          } else {
            applyOperation(result(idx)._2, op, mode) match {
              case Right(modified) => result = result.updated(idx, (key, modified))
              case Left(err)       => if (mode != PatchMode.Lenient) return Left(err)
            }
          }
      }
      i += 1
    }
    Right(result)
  }

  private def applyRecordPatch(
    fields: Vector[(String, DynamicValue)],
    fieldOps: Vector[(String, Operation)],
    mode: PatchMode
  ): Either[SchemaError, Vector[(String, DynamicValue)]] = {
    var result = fields
    var i      = 0
    while (i < fieldOps.length) {
      val (name, op) = fieldOps(i)
      val idx        = result.indexWhere(_._1 == name)
      if (idx < 0) {
        if (mode != PatchMode.Lenient)
          return Left(SchemaError(SchemaError.FieldNotFound(name)))
      } else {
        applyOperation(result(idx)._2, op, mode) match {
          case Right(modified) => result = result.updated(idx, (name, modified))
          case Left(err)       => if (mode != PatchMode.Lenient) return Left(err)
        }
      }
      i += 1
    }
    Right(result)
  }

  private def navigateAndApply(
    value: DynamicValue,
    path: List[DynamicOptic.Node],
    operation: Operation,
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = path match {
    case Nil =>
      applyOperation(value, operation, mode)

    case DynamicOptic.Node.Field(name) :: rest =>
      value match {
        case DynamicValue.Record(fields) =>
          val fieldIdx = fields.indexWhere(_._1 == name)
          if (fieldIdx < 0) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.FieldNotFound(name)))
          } else {
            navigateAndApply(fields(fieldIdx)._2, rest, operation, mode).map { modified =>
              DynamicValue.Record(fields.updated(fieldIdx, (name, modified)))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Record", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.AtIndex(index) :: rest =>
      value match {
        case DynamicValue.Sequence(elements) =>
          if (index < 0 || index >= elements.length) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.IndexOutOfBounds(index, elements.length)))
          } else {
            navigateAndApply(elements(index), rest, operation, mode).map { modified =>
              DynamicValue.Sequence(elements.updated(index, modified))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Sequence", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.AtMapKey(key) :: rest =>
      val keyDv = key.asInstanceOf[DynamicValue]
      value match {
        case DynamicValue.Map(entries) =>
          val entryIdx = entries.indexWhere(_._1 == keyDv)
          if (entryIdx < 0) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
          } else {
            navigateAndApply(entries(entryIdx)._2, rest, operation, mode).map { modified =>
              DynamicValue.Map(entries.updated(entryIdx, (keyDv, modified)))
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Map", value.getClass.getSimpleName)))
      }

    case DynamicOptic.Node.Case(name) :: rest =>
      value match {
        case DynamicValue.Variant(caseName, innerValue) =>
          if (caseName != name) {
            if (mode == PatchMode.Lenient) Right(value)
            else Left(SchemaError(SchemaError.TypeMismatch(name, caseName)))
          } else {
            navigateAndApply(innerValue, rest, operation, mode).map { modified =>
              DynamicValue.Variant(caseName, modified)
            }
          }
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Variant", value.getClass.getSimpleName)))
      }

    case _ :: rest =>
      navigateAndApply(value, rest, operation, mode)
  }
}
