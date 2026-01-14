package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A serializable expression for transforming DynamicValue instances.
 *
 * MigrationExpr represents value-level transformations that can be fully
 * serialized without runtime closures. All transformations operate on
 * DynamicValue, making them schema-agnostic and portable.
 */
sealed trait MigrationExpr {

  /**
   * Applies this expression to a DynamicValue.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]

  /**
   * Composes this expression with another, applying this first then that.
   */
  def andThen(that: MigrationExpr): MigrationExpr = MigrationExpr.Compose(this, that)
}

object MigrationExpr {

  // Core expressions

  /**
   * Identity transformation - returns the input unchanged.
   */
  case object Identity extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = Right(value)
  }

  /**
   * Returns a constant value, ignoring input.
   */
  final case class Constant(value: DynamicValue) extends MigrationExpr {
    def apply(input: DynamicValue): Either[SchemaError, DynamicValue] = Right(value)
  }

  /**
   * Returns input if present, otherwise returns default.
   */
  final case class DefaultValue(default: DynamicValue) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Variant("None", _) => Right(default)
      case _                               => Right(value)
    }
  }

  /**
   * Composes two expressions sequentially.
   */
  final case class Compose(first: MigrationExpr, second: MigrationExpr) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      first(value).flatMap(second.apply)
  }

  // Numeric operations

  final case class IntAdd(delta: Int) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(n + delta)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Int but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class IntMultiply(factor: Int) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(n * factor)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Int but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class LongAdd(delta: Long) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(n + delta)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Long but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class DoubleAdd(delta: Double) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(n + delta)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Double but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class DoubleMultiply(factor: Double) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(n * factor)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Double but got ${value.getClass.getSimpleName}"))
    }
  }

  // String operations

  final case class StringPrepend(prefix: String) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(prefix + s)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class StringAppend(suffix: String) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s + suffix)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  final case class StringReplace(target: String, replacement: String) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.replace(target, replacement))))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  case object StringToUpperCase extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.toUpperCase)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  case object StringToLowerCase extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.toLowerCase)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  case object StringTrim extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(s.trim)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  // Type conversion operations

  case object IntToLong extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(n.toLong)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Int but got ${value.getClass.getSimpleName}"))
    }
  }

  case object IntToDouble extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(n.toDouble)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Int but got ${value.getClass.getSimpleName}"))
    }
  }

  case object IntToString extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(n.toString)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Int but got ${value.getClass.getSimpleName}"))
    }
  }

  case object LongToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(n.toInt)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Long but got ${value.getClass.getSimpleName}"))
    }
  }

  case object DoubleToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(n.toInt)))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Double but got ${value.getClass.getSimpleName}"))
    }
  }

  case object StringToInt extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Int(s.toInt)))
        catch {
          case _: NumberFormatException =>
            Left(SchemaError.expectationMismatch(Nil, s"Cannot parse '$s' as Int"))
        }
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected String but got ${value.getClass.getSimpleName}"))
    }
  }

  // Option operations

  /**
   * Wraps a value in Some.
   */
  case object WrapSome extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      Right(DynamicValue.Variant("Some", DynamicValue.Record(Vector("value" -> value))))
  }

  /**
   * Unwraps a Some value, fails if None.
   */
  case object UnwrapSome extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
        fields.find(_._1 == "value").map(_._2) match {
          case Some(inner) => Right(inner)
          case None        => Left(SchemaError.expectationMismatch(Nil, "Some variant missing 'value' field"))
        }
      case DynamicValue.Variant("None", _) =>
        Left(SchemaError.expectationMismatch(Nil, "Cannot unwrap None"))
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Option but got ${value.getClass.getSimpleName}"))
    }
  }

  /**
   * Unwraps a Some value, or returns default if None.
   */
  final case class GetOrElse(default: DynamicValue) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
        fields.find(_._1 == "value").map(_._2) match {
          case Some(inner) => Right(inner)
          case None        => Right(default)
        }
      case DynamicValue.Variant("None", _) => Right(default)
      case _                               => Left(SchemaError.expectationMismatch(Nil, s"Expected Option but got ${value.getClass.getSimpleName}"))
    }
  }

  // Record operations

  /**
   * Extracts a field from a record.
   */
  final case class GetField(fieldName: String) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        fields.find(_._1 == fieldName).map(_._2) match {
          case Some(fieldValue) => Right(fieldValue)
          case None             => Left(SchemaError.missingField(Nil, fieldName))
        }
      case _ =>
        Left(SchemaError.expectationMismatch(Nil, s"Expected Record but got ${value.getClass.getSimpleName}"))
    }
  }

  /**
   * Builds a record from field expressions.
   */
  final case class BuildRecord(fieldExprs: Vector[(String, MigrationExpr)]) extends MigrationExpr {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      val builder                    = Vector.newBuilder[(String, DynamicValue)]
      var error: Option[SchemaError] = None
      var idx                        = 0
      while (idx < fieldExprs.length && error.isEmpty) {
        val (name, expr) = fieldExprs(idx)
        expr(value) match {
          case Right(v)  => builder.addOne((name, v))
          case Left(err) => error = Some(err)
        }
        idx += 1
      }
      error.toLeft(DynamicValue.Record(builder.result()))
    }
  }

  // Smart constructors

  def identity: MigrationExpr = Identity

  def constant[A](value: A)(implicit schema: Schema[A]): MigrationExpr =
    Constant(schema.toDynamicValue(value))

  def defaultValue[A](value: A)(implicit schema: Schema[A]): MigrationExpr =
    DefaultValue(schema.toDynamicValue(value))

  def getField(name: String): MigrationExpr = GetField(name)

  def intAdd(delta: Int): MigrationExpr = IntAdd(delta)

  def stringAppend(suffix: String): MigrationExpr = StringAppend(suffix)

  def stringPrepend(prefix: String): MigrationExpr = StringPrepend(prefix)
}
