package zio.blocks.schema

/**
 * PrimitiveOp represents delta operations on primitive values.
 * These allow representing changes as increments rather than full replacements,
 * enabling more efficient patches and better merge semantics.
 */
sealed trait PrimitiveOp

object PrimitiveOp {
  // Numeric deltas
  final case class IntDelta(delta: Int) extends PrimitiveOp
  final case class LongDelta(delta: Long) extends PrimitiveOp
  final case class DoubleDelta(delta: Double) extends PrimitiveOp
  final case class FloatDelta(delta: Float) extends PrimitiveOp
  final case class ShortDelta(delta: Short) extends PrimitiveOp
  final case class ByteDelta(delta: Byte) extends PrimitiveOp
  final case class BigIntDelta(delta: BigInt) extends PrimitiveOp
  final case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp
  
  // String edits (LCS-based)
  final case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp
  
  // Temporal deltas
  final case class InstantDelta(duration: java.time.Duration) extends PrimitiveOp
  final case class DurationDelta(duration: java.time.Duration) extends PrimitiveOp
  final case class LocalDateDelta(period: java.time.Period) extends PrimitiveOp
  final case class LocalDateTimeDelta(period: java.time.Period, duration: java.time.Duration) extends PrimitiveOp
  final case class PeriodDelta(period: java.time.Period) extends PrimitiveOp

  /**
   * Apply a primitive operation to a PrimitiveValue.
   */
  def apply(value: PrimitiveValue, op: PrimitiveOp): Either[SchemaError, PrimitiveValue] = op match {
    case IntDelta(delta) => value match {
      case PrimitiveValue.Int(v) => Right(PrimitiveValue.Int(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Int", value.getClass.getSimpleName)))
    }
    case LongDelta(delta) => value match {
      case PrimitiveValue.Long(v) => Right(PrimitiveValue.Long(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Long", value.getClass.getSimpleName)))
    }
    case DoubleDelta(delta) => value match {
      case PrimitiveValue.Double(v) => Right(PrimitiveValue.Double(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Double", value.getClass.getSimpleName)))
    }
    case FloatDelta(delta) => value match {
      case PrimitiveValue.Float(v) => Right(PrimitiveValue.Float(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Float", value.getClass.getSimpleName)))
    }
    case ShortDelta(delta) => value match {
      case PrimitiveValue.Short(v) => Right(PrimitiveValue.Short((v + delta).toShort))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Short", value.getClass.getSimpleName)))
    }
    case ByteDelta(delta) => value match {
      case PrimitiveValue.Byte(v) => Right(PrimitiveValue.Byte((v + delta).toByte))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Byte", value.getClass.getSimpleName)))
    }
    case BigIntDelta(delta) => value match {
      case PrimitiveValue.BigInt(v) => Right(PrimitiveValue.BigInt(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("BigInt", value.getClass.getSimpleName)))
    }
    case BigDecimalDelta(delta) => value match {
      case PrimitiveValue.BigDecimal(v) => Right(PrimitiveValue.BigDecimal(v + delta))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("BigDecimal", value.getClass.getSimpleName)))
    }
    case StringEdit(ops) => value match {
      case PrimitiveValue.String(v) => 
        StringOp.applyAll(v, ops).map(PrimitiveValue.String(_))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("String", value.getClass.getSimpleName)))
    }
    case InstantDelta(duration) => value match {
      case PrimitiveValue.Instant(v) => Right(PrimitiveValue.Instant(v.plus(duration)))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Instant", value.getClass.getSimpleName)))
    }
    case DurationDelta(duration) => value match {
      case PrimitiveValue.Duration(v) => Right(PrimitiveValue.Duration(v.plus(duration)))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Duration", value.getClass.getSimpleName)))
    }
    case LocalDateDelta(period) => value match {
      case PrimitiveValue.LocalDate(v) => Right(PrimitiveValue.LocalDate(v.plus(period)))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("LocalDate", value.getClass.getSimpleName)))
    }
    case LocalDateTimeDelta(period, duration) => value match {
      case PrimitiveValue.LocalDateTime(v) => Right(PrimitiveValue.LocalDateTime(v.plus(period).plus(duration)))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("LocalDateTime", value.getClass.getSimpleName)))
    }
    case PeriodDelta(period) => value match {
      case PrimitiveValue.Period(v) => Right(PrimitiveValue.Period(v.plus(period)))
      case _ => Left(SchemaError(SchemaError.TypeMismatch("Period", value.getClass.getSimpleName)))
    }
  }
}
