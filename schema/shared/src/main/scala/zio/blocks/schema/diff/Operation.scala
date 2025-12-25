package zio.blocks.schema.diff

import zio.blocks.schema.DynamicValue

sealed trait Operation

object Operation {

  /** Replace the target value entirely */
  final case class Set(value: DynamicValue) extends Operation

  /** Apply a delta to a primitive value */
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation

  /** Apply string edit operations (Insert/Delete) */
  final case class StringEdit(ops: Vector[StringOp]) extends Operation

  /** Apply sequence edit operations */
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation

  /** Apply map edit operations */
  final case class MapEdit(ops: Vector[MapOp]) extends Operation

  /** No-op */
  case object Identity extends Operation
}

sealed trait PrimitiveOp
object PrimitiveOp {
  // Numeric
  final case class ByteDelta(delta: Byte)             extends PrimitiveOp
  final case class ShortDelta(delta: Short)           extends PrimitiveOp
  final case class IntDelta(delta: Int)               extends PrimitiveOp
  final case class LongDelta(delta: Long)             extends PrimitiveOp
  final case class FloatDelta(delta: Float)           extends PrimitiveOp
  final case class DoubleDelta(delta: Double)         extends PrimitiveOp
  final case class BigIntDelta(delta: BigInt)         extends PrimitiveOp
  final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp

  // Temporal
  final case class DurationDelta(nanos: Long)                      extends PrimitiveOp
  final case class PeriodDelta(years: Int, months: Int, days: Int) extends PrimitiveOp
  final case class InstantDelta(seconds: Long, nanos: Int)         extends PrimitiveOp
  final case class LocalDateDelta(days: Long)                      extends PrimitiveOp
  final case class LocalTimeDelta(nanos: Long)                     extends PrimitiveOp
  final case class LocalDateTimeDelta(nanos: Long)                 extends PrimitiveOp
  final case class OffsetDateTimeDelta(seconds: Long, nanos: Int)  extends PrimitiveOp
  final case class OffsetTimeDelta(nanos: Long)                    extends PrimitiveOp
  final case class ZonedDateTimeDelta(seconds: Long, nanos: Int)   extends PrimitiveOp

  // Other
  final case class YearDelta(years: Int) extends PrimitiveOp
}

sealed trait StringOp
object StringOp {
  final case class Insert(index: Int, value: String) extends StringOp
  final case class Delete(index: Int, length: Int)   extends StringOp
}

sealed trait SeqOp
object SeqOp {
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp
  final case class Append(values: Vector[DynamicValue])             extends SeqOp
  final case class Delete(index: Int, count: Int)                   extends SeqOp
  // Recursively patch an element at a specific index
  final case class Modify(index: Int, patch: DynamicPatch) extends SeqOp
}

sealed trait MapOp
object MapOp {
  final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp
  final case class Remove(key: DynamicValue)                   extends MapOp
  // Recursively patch a value for a specific key
  final case class Modify(key: DynamicValue, patch: DynamicPatch) extends MapOp
}
