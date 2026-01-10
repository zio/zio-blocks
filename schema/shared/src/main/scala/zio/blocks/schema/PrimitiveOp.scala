package zio.blocks.schema

/**
 * Represents a delta operation on a primitive value. These operations compute
 * the new value based on the old value plus a delta.
 */
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

  // String edits (LCS-based)
  final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp

  // Temporal deltas
  final case class InstantDelta(duration: java.time.Duration)                                  extends PrimitiveOp
  final case class DurationDelta(duration: java.time.Duration)                                 extends PrimitiveOp
  final case class LocalDateDelta(period: java.time.Period)                                    extends PrimitiveOp
  final case class LocalTimeDelta(duration: java.time.Duration)                                extends PrimitiveOp
  final case class LocalDateTimeDelta(period: java.time.Period, duration: java.time.Duration)  extends PrimitiveOp
  final case class PeriodDelta(period: java.time.Period)                                       extends PrimitiveOp
  final case class OffsetDateTimeDelta(period: java.time.Period, duration: java.time.Duration) extends PrimitiveOp
  final case class ZonedDateTimeDelta(period: java.time.Period, duration: java.time.Duration)  extends PrimitiveOp
  final case class YearDelta(years: Int)                                                       extends PrimitiveOp
  final case class YearMonthDelta(months: Int)                                                 extends PrimitiveOp
  final case class MonthDayDelta(days: Int)                                                    extends PrimitiveOp
}
