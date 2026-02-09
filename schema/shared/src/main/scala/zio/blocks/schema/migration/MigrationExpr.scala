package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A pure-data, fully serializable expression for transforming `DynamicValue`s.
 *
 * `MigrationExpr` represents value-level transformations as a serializable ADT
 * with **zero closures**. Every expression is introspectable, optimizable, and
 * storable alongside migrations in databases or registries.
 *
 * Constraints (per spec):
 *   - Primitive → primitive only
 *   - No record / enum construction
 */
// format: off
sealed trait MigrationExpr {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def reverse: MigrationExpr
}
// format: on

object MigrationExpr {

  /**
   * Identity expression — returns the input unchanged.
   */
  case object Identity extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: MigrationExpr                                           = Identity
  }

  /**
   * Constant expression — always returns the same value.
   */
  final case class Const(value: DynamicValue, originalValue: DynamicValue) extends MigrationExpr {
    def apply(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
    def reverse: MigrationExpr                                           = Const(originalValue, value)
  }

  object Const {
    def apply(value: DynamicValue): Const = Const(value, DynamicValue.Null)
  }

  // ──────────────── Primitive Coercions ────────────────

  /**
   * Convert an Int primitive to a String.
   */
  case object IntToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.string(n.toString))
      case _ =>
        Left(MigrationError.TypeMismatch("Int", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = StringToInt
  }

  /**
   * Convert a String primitive to an Int.
   */
  case object StringToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.int(s.toInt))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.TransformFailed(s"Cannot parse '$s' as Int", DynamicOptic.root))
        }
      case _ =>
        Left(MigrationError.TypeMismatch("String", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = IntToString
  }

  /**
   * Convert an Int primitive to a Long.
   */
  case object IntToLong extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.long(n.toLong))
      case _ =>
        Left(MigrationError.TypeMismatch("Int", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = LongToInt
  }

  /**
   * Convert a Long primitive to an Int (truncating).
   */
  case object LongToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        Right(DynamicValue.int(n.toInt))
      case _ =>
        Left(MigrationError.TypeMismatch("Long", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = IntToLong
  }

  /**
   * Convert an Int primitive to a Double.
   */
  case object IntToDouble extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.double(n.toDouble))
      case _ =>
        Left(MigrationError.TypeMismatch("Int", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = DoubleToInt
  }

  /**
   * Convert a Double primitive to an Int (truncating).
   */
  case object DoubleToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.int(d.toInt))
      case _ =>
        Left(MigrationError.TypeMismatch("Double", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = IntToDouble
  }

  /**
   * Convert a Long primitive to a String.
   */
  case object LongToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        Right(DynamicValue.string(n.toString))
      case _ =>
        Left(MigrationError.TypeMismatch("Long", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = StringToLong
  }

  /**
   * Convert a String primitive to a Long.
   */
  case object StringToLong extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.long(s.toLong))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.TransformFailed(s"Cannot parse '$s' as Long", DynamicOptic.root))
        }
      case _ =>
        Left(MigrationError.TypeMismatch("String", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = LongToString
  }

  /**
   * Convert a Double primitive to a String.
   */
  case object DoubleToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.string(d.toString))
      case _ =>
        Left(MigrationError.TypeMismatch("Double", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = StringToDouble
  }

  /**
   * Convert a String primitive to a Double.
   */
  case object StringToDouble extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.double(s.toDouble))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.TransformFailed(s"Cannot parse '$s' as Double", DynamicOptic.root))
        }
      case _ =>
        Left(MigrationError.TypeMismatch("String", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = DoubleToString
  }

  /**
   * Convert a Boolean primitive to an Int (true=1, false=0).
   */
  case object BoolToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        Right(DynamicValue.int(if (b) 1 else 0))
      case _ =>
        Left(MigrationError.TypeMismatch("Boolean", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = IntToBool
  }

  /**
   * Convert an Int primitive to a Boolean (0=false, nonzero=true).
   */
  case object IntToBool extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.boolean(n != 0))
      case _ =>
        Left(MigrationError.TypeMismatch("Int", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = BoolToInt
  }

  /**
   * Convert a Boolean primitive to a String ("true"/"false").
   */
  case object BoolToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        Right(DynamicValue.string(b.toString))
      case _ =>
        Left(MigrationError.TypeMismatch("Boolean", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = StringToBool
  }

  /**
   * Convert a String primitive to a Boolean ("true"/"false").
   */
  case object StringToBool extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toLowerCase match {
          case "true"  => Right(DynamicValue.boolean(true))
          case "false" => Right(DynamicValue.boolean(false))
          case _       => Left(MigrationError.TransformFailed(s"Cannot parse '$s' as Boolean", DynamicOptic.root))
        }
      case _ =>
        Left(MigrationError.TypeMismatch("String", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = BoolToString
  }

  /**
   * Convert a Float primitive to a String.
   */
  case object FloatToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Float(f)) =>
        Right(DynamicValue.string(f.toString))
      case _ =>
        Left(MigrationError.TypeMismatch("Float", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = StringToFloat
  }

  /**
   * Convert a String primitive to a Float.
   */
  case object StringToFloat extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.float(s.toFloat))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.TransformFailed(s"Cannot parse '$s' as Float", DynamicOptic.root))
        }
      case _ =>
        Left(MigrationError.TypeMismatch("String", value.getClass.getSimpleName, DynamicOptic.root))
    }
    def reverse: MigrationExpr = FloatToString
  }

  // ──────────────── Composite Expressions ────────────────

  /**
   * Compose two expressions sequentially: apply `first`, then `second`.
   */
  final case class Compose(first: MigrationExpr, second: MigrationExpr) extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      first(value).flatMap(second(_))
    def reverse: MigrationExpr = Compose(second.reverse, first.reverse)
  }

  /**
   * Apply a migration as an expression.
   */
  final case class FromMigration(migration: DynamicMigration) extends MigrationExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      migration.migrate(value)
    def reverse: MigrationExpr = FromMigration(migration.reverse)
  }
}
