package zio.blocks.schema

/**
 * Delta operations for primitive types.
 * These represent incremental changes rather than full replacements.
 */
sealed trait PrimitiveOp

object PrimitiveOp {
  
  // Numeric deltas - add/subtract from current value
  
  final case class ByteDelta(delta: Byte) extends PrimitiveOp
  
  final case class ShortDelta(delta: Short) extends PrimitiveOp
  
  final case class IntDelta(delta: Int) extends PrimitiveOp
  
  final case class LongDelta(delta: Long) extends PrimitiveOp
  
  final case class FloatDelta(delta: Float) extends PrimitiveOp
  
  final case class DoubleDelta(delta: Double) extends PrimitiveOp
  
  final case class BigIntDelta(delta: BigInt) extends PrimitiveOp
  
  final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp
  
  // String edits (LCS-based)
  
  final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  
  // Temporal deltas
  
  final case class InstantDelta(duration: java.time.Duration) extends PrimitiveOp
  
  final case class DurationDelta(duration: java.time.Duration) extends PrimitiveOp
  
  final case class LocalDateDelta(period: java.time.Period) extends PrimitiveOp
  
  final case class LocalTimeDelta(duration: java.time.Duration) extends PrimitiveOp
  
  final case class LocalDateTimeDelta(period: java.time.Period, duration: java.time.Duration) extends PrimitiveOp
  
  final case class PeriodDelta(period: java.time.Period) extends PrimitiveOp
  
  final case class YearDelta(years: Int) extends PrimitiveOp
  
  final case class YearMonthDelta(months: Int) extends PrimitiveOp
  
  final case class MonthDayDelta(days: Int) extends PrimitiveOp
  
  final case class ZonedDateTimeDelta(duration: java.time.Duration) extends PrimitiveOp
  
  final case class OffsetDateTimeDelta(duration: java.time.Duration) extends PrimitiveOp
  
  final case class OffsetTimeDelta(duration: java.time.Duration) extends PrimitiveOp
  
  /**
   * Apply a primitive operation to a primitive value.
   * Returns the modified value or an error.
   */
  def applyOp(value: PrimitiveValue, op: PrimitiveOp): Either[String, PrimitiveValue] = {
    (value, op) match {
      case (PrimitiveValue.Byte(v), ByteDelta(d)) => 
        Right(PrimitiveValue.Byte((v + d).toByte))
      case (PrimitiveValue.Short(v), ShortDelta(d)) => 
        Right(PrimitiveValue.Short((v + d).toShort))
      case (PrimitiveValue.Int(v), IntDelta(d)) => 
        Right(PrimitiveValue.Int(v + d))
      case (PrimitiveValue.Long(v), LongDelta(d)) => 
        Right(PrimitiveValue.Long(v + d))
      case (PrimitiveValue.Float(v), FloatDelta(d)) => 
        Right(PrimitiveValue.Float(v + d))
      case (PrimitiveValue.Double(v), DoubleDelta(d)) => 
        Right(PrimitiveValue.Double(v + d))
      case (PrimitiveValue.BigInt(v), BigIntDelta(d)) => 
        Right(PrimitiveValue.BigInt(v + d))
      case (PrimitiveValue.BigDecimal(v), BigDecimalDelta(d)) => 
        Right(PrimitiveValue.BigDecimal(v + d))
      case (PrimitiveValue.String(v), StringEdit(ops)) => 
        Right(PrimitiveValue.String(StringOp.applyOps(v, ops)))
      case (PrimitiveValue.Instant(v), InstantDelta(d)) => 
        Right(PrimitiveValue.Instant(v.plus(d)))
      case (PrimitiveValue.Duration(v), DurationDelta(d)) => 
        Right(PrimitiveValue.Duration(v.plus(d)))
      case (PrimitiveValue.LocalDate(v), LocalDateDelta(p)) => 
        Right(PrimitiveValue.LocalDate(v.plus(p)))
      case (PrimitiveValue.LocalTime(v), LocalTimeDelta(d)) => 
        Right(PrimitiveValue.LocalTime(v.plus(d)))
      case (PrimitiveValue.LocalDateTime(v), LocalDateTimeDelta(p, d)) => 
        Right(PrimitiveValue.LocalDateTime(v.plus(p).plus(d)))
      case (PrimitiveValue.Period(v), PeriodDelta(p)) => 
        Right(PrimitiveValue.Period(v.plus(p)))
      case (PrimitiveValue.Year(v), YearDelta(y)) => 
        Right(PrimitiveValue.Year(v.plusYears(y)))
      case (PrimitiveValue.YearMonth(v), YearMonthDelta(m)) => 
        Right(PrimitiveValue.YearMonth(v.plusMonths(m)))
      case (PrimitiveValue.ZonedDateTime(v), ZonedDateTimeDelta(d)) => 
        Right(PrimitiveValue.ZonedDateTime(v.plus(d)))
      case (PrimitiveValue.OffsetDateTime(v), OffsetDateTimeDelta(d)) => 
        Right(PrimitiveValue.OffsetDateTime(v.plus(d)))
      case (PrimitiveValue.OffsetTime(v), OffsetTimeDelta(d)) => 
        Right(PrimitiveValue.OffsetTime(v.plus(d)))
      case _ => 
        Left(s"Cannot apply ${op.getClass.getSimpleName} to ${value.getClass.getSimpleName}")
    }
  }
}
