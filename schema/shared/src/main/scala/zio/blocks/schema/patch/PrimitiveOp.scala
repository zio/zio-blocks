package zio.blocks.schema.patch

sealed trait PrimitiveOp

object PrimitiveOp {

  // Delta for Primitive values. Applied by adding delta to the current value.

  final case class IntDelta(delta: Int) extends PrimitiveOp

  final case class LongDelta(delta: Long) extends PrimitiveOp

  final case class DoubleDelta(delta: Double) extends PrimitiveOp

  final case class FloatDelta(delta: Float) extends PrimitiveOp

  final case class ShortDelta(delta: Short) extends PrimitiveOp

  final case class ByteDelta(delta: Byte) extends PrimitiveOp

  final case class BigIntDelta(delta: BigInt) extends PrimitiveOp

  final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp

  final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp

  final case class InstantDelta(delta: java.time.Duration) extends PrimitiveOp

  final case class DurationDelta(delta: java.time.Duration) extends PrimitiveOp

  final case class LocalDateDelta(delta: java.time.Period) extends PrimitiveOp

  // Delta for LocalDateTime values. Applied by adding period and duration.
  final case class LocalDateTimeDelta(periodDelta: java.time.Period, durationDelta: java.time.Duration)
      extends PrimitiveOp

  final case class PeriodDelta(delta: java.time.Period) extends PrimitiveOp
}
