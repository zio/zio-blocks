package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A pure, serializable description of a primitive-to-primitive conversion.
 *
 * These transforms operate on [[DynamicValue.Primitive]] values and can be
 * reversed to support bidirectional schema evolution.
 */
sealed trait PrimitiveTransform {

  /** Apply this transform to a DynamicValue. */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /** Reverse this transform. */
  def reverse: PrimitiveTransform
}

object PrimitiveTransform {

  private val root = DynamicOptic.root

  /** Identity transform — passes the value through unchanged. */
  case object Identity extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: PrimitiveTransform                                      = Identity
  }

  /** Convert Int to String. */
  case object IntToString extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "String", "Expected Int primitive"))
      }
    def reverse: PrimitiveTransform = StringToInt
  }

  /** Convert String to Int. */
  case object StringToInt extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
          try Right(new DynamicValue.Primitive(new PrimitiveValue.Int(pv.value.toInt)))
          catch {
            case _: NumberFormatException =>
              Left(MigrationError.ConversionFailed(root, "String", "Int", "Cannot parse '" + pv.value + "' as Int"))
          }
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "Int", "Expected String primitive"))
      }
    def reverse: PrimitiveTransform = IntToString
  }

  /** Convert Long to String. */
  case object LongToString extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "String", "Expected Long primitive"))
      }
    def reverse: PrimitiveTransform = StringToLong
  }

  /** Convert String to Long. */
  case object StringToLong extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
          try Right(new DynamicValue.Primitive(new PrimitiveValue.Long(pv.value.toLong)))
          catch {
            case _: NumberFormatException =>
              Left(MigrationError.ConversionFailed(root, "String", "Long", "Cannot parse '" + pv.value + "' as Long"))
          }
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "Long", "Expected String primitive"))
      }
    def reverse: PrimitiveTransform = LongToString
  }

  /** Convert Int to Long (widening). */
  case object IntToLong extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.Long(pv.value.toLong)))
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "Long", "Expected Int primitive"))
      }
    def reverse: PrimitiveTransform = LongToInt
  }

  /** Convert Long to Int (narrowing, may overflow). */
  case object LongToInt extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.Long) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.Int(pv.value.toInt)))
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "Int", "Expected Long primitive"))
      }
    def reverse: PrimitiveTransform = IntToLong
  }

  /** Convert Boolean to String ("true"/"false"). */
  case object BooleanToString extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.Boolean) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.String(pv.value.toString)))
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "String", "Expected Boolean primitive"))
      }
    def reverse: PrimitiveTransform = StringToBoolean
  }

  /** Convert String to Boolean ("true"/"false"). */
  case object StringToBoolean extends PrimitiveTransform {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Primitive(pv: PrimitiveValue.String) =>
          pv.value.toLowerCase match {
            case "true"  => Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(true)))
            case "false" => Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
            case _       =>
              Left(
                MigrationError.ConversionFailed(root, "String", "Boolean", "Cannot parse '" + pv.value + "' as Boolean")
              )
          }
        case _ =>
          Left(MigrationError.ConversionFailed(root, value.valueType.toString, "Boolean", "Expected String primitive"))
      }
    def reverse: PrimitiveTransform = BooleanToString
  }

  /** Replace with a constant value regardless of input. */
  final case class Const(value: DynamicValue) extends PrimitiveTransform {
    def apply(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: PrimitiveTransform                                      = Const(value)
  }
}
