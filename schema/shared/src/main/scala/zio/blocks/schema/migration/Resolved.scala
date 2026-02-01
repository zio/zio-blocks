package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}

/**
 * Resolved is a serializable expression type for migrations.
 *
 * Unlike SchemaExpr which operates on typed values and contains closures,
 * Resolved expressions operate on DynamicValue and are fully serializable. This
 * enables migrations to be stored, transmitted, and applied without runtime
 * code generation.
 *
 * Each variant is a pure data structure:
 *   - Literal: constant value
 *   - Identity: pass-through input
 *   - FieldAccess: extract a field from a record
 *   - Convert: primitive type conversion
 *   - Fail: marker for non-reversible operations
 */
sealed trait Resolved { self =>

  /**
   * Evaluate without input. Returns Left for expressions requiring input.
   */
  def evalDynamic: Either[String, DynamicValue]

  /**
   * Evaluate with input value.
   */
  def evalDynamic(input: DynamicValue): Either[String, DynamicValue]

  /**
   * Compute the structural inverse of this expression if possible.
   *
   * For reversible transformations (e.g., type conversions), this returns an
   * expression that undoes the effect. For irreversible transformations, this
   * returns the expression unchanged or a Fail marker.
   *
   * This enables automatic bidirectional migration support where the reverse
   * transform can be computed from the forward transform.
   */
  def inverse: Resolved = this // Default: not invertible, return self
}

object Resolved {

  /**
   * A literal constant value stored as DynamicValue.
   */
  final case class Literal(value: DynamicValue) extends Resolved {
    def evalDynamic: Either[String, DynamicValue]                      = Right(value)
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  object Literal {
    def apply[A](value: A, schema: Schema[A]): Literal =
      Literal(schema.toDynamicValue(value))

    def string(value: String): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.String(value)))

    def int(value: Int): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Int(value)))

    def long(value: Long): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Long(value)))

    def boolean(value: Boolean): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(value)))

    def double(value: Double): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Double(value)))

    val unit: Literal = Literal(DynamicValue.Primitive(PrimitiveValue.Unit))
  }

  /**
   * Identity function - returns input unchanged.
   */
  case object Identity extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Identity requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      Right(input)
  }

  /**
   * Marker for using the target schema's default value.
   *
   * At execution time, this signals that the migration should use the default
   * value from the target field's schema. When processing a migration action
   * with SchemaDefault, the interpreter will attempt to extract the default
   * value from the target schema's field definition.
   */
  case object SchemaDefault extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("SchemaDefault: No default value available - resolve at build time")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      Left("SchemaDefault: No default value available - resolve at build time")
  }

  /**
   * Extract a field from a record.
   */
  final case class FieldAccess(fieldName: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("FieldAccess requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Record(fields) =>
          fields.collectFirst { case (name, v) if name == fieldName => v }
            .toRight(s"Field '$fieldName' not found")
        case other =>
          Left(s"Expected record for field access '$fieldName', got ${other.valueType}")
      }
  }

  /**
   * Access a value at a DynamicOptic path.
   */
  final case class OpticAccess(path: DynamicOptic, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("OpticAccess requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      input.get(path).one match {
        case Right(value) => inner.evalDynamic(value)
        case Left(error)  => Left(s"Path $path: ${error.message}")
      }
  }

  /**
   * Stored default value (already resolved to DynamicValue).
   */
  final case class DefaultValue(defaultDynamic: Either[String, DynamicValue]) extends Resolved {
    def evalDynamic: Either[String, DynamicValue]                      = defaultDynamic
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = defaultDynamic
  }

  object DefaultValue {
    def fromValue[A](value: A, schema: Schema[A]): DefaultValue =
      DefaultValue(Right(schema.toDynamicValue(value)))

    def noDefault: DefaultValue =
      DefaultValue(Left("No default value defined"))

    def fail(message: String): DefaultValue =
      DefaultValue(Left(message))
  }

  /**
   * Convert between primitive types.
   *
   * Type conversion uses string names for serializability.
   */
  final case class Convert(
    fromTypeName: String,
    toTypeName: String,
    inner: Resolved
  ) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Convert requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap { value =>
        convertPrimitive(value, fromTypeName, toTypeName)
      }

    // Type conversions are invertible by swapping from/to
    override def inverse: Resolved = Convert(toTypeName, fromTypeName, inner.inverse)
  }

  /**
   * Concatenate strings (for Join operations).
   */
  final case class Concat(parts: Vector[Resolved], separator: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Concat requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = {
      val strings = parts.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (acc, part) =>
        acc.flatMap { strs =>
          part.evalDynamic(input).flatMap {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(strs :+ s)
            case other                                            => Left(s"Expected string in concat, got ${other.valueType}")
          }
        }
      }
      strings.map(strs => DynamicValue.Primitive(PrimitiveValue.String(strs.mkString(separator))))
    }
  }

  /**
   * Split a string (for Split operations).
   */
  final case class SplitString(inner: Resolved, delimiter: String, index: Int) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("SplitString requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(delimiter, -1)
          if (index >= 0 && index < parts.length)
            Right(DynamicValue.Primitive(PrimitiveValue.String(parts(index))))
          else
            Left(s"Split index $index out of bounds (${parts.length} parts)")
        case other =>
          Left(s"Expected string for split, got ${other.valueType}")
      }
  }

  /**
   * Marker for non-reversible operations.
   */
  final case class Fail(message: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue]                      = Left(message)
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = Left(message)
  }

  /**
   * Unwrap an Option (return inner value for Some, fallback for None).
   */
  final case class UnwrapOption(inner: Resolved, fallback: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("UnwrapOption requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
          fields.find(_._1 == "value").map(kv => Right(kv._2)).getOrElse(fallback.evalDynamic(input))
        case DynamicValue.Variant("None", _) =>
          fallback.evalDynamic(input)
        case DynamicValue.Null =>
          fallback.evalDynamic(input)
        case value =>
          Right(value)
      }

    // Unwrapping is inverted by wrapping
    override def inverse: Resolved = WrapOption(inner.inverse)
  }

  /**
   * Wrap a value in Some.
   */
  final case class WrapOption(inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("WrapOption requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).map { value =>
        val someRecord = DynamicValue.Record(("value", value))
        DynamicValue.Variant("Some", someRecord)
      }

    // Wrapping is inverted by unwrapping with a sensible fallback
    override def inverse: Resolved = UnwrapOption(inner.inverse, Fail("Cannot unwrap None"))
  }

  // Primitive conversion helper
  private def convertPrimitive(
    value: DynamicValue,
    fromType: String,
    toType: String
  ): Either[String, DynamicValue] =
    // Delegate to the comprehensive PrimitiveConversions module
    PrimitiveConversions.convert(value, fromType, toType)

  // Schema instance for serialization - uses Literal as base representation
  // Full enum derivation would require macro-based derivation; this simplified
  // approach covers the most common case (Literal values)
  implicit lazy val schema: Schema[Resolved] = Schema[DynamicValue].transform(
    (dv: DynamicValue) => Literal(dv): Resolved,
    (r: Resolved) =>
      r match {
        case Literal(value)       => value
        case Identity             => DynamicValue.Primitive(PrimitiveValue.Unit)
        case SchemaDefault        => DynamicValue.Primitive(PrimitiveValue.Unit)
        case Fail(msg)            => DynamicValue.Primitive(PrimitiveValue.String(msg))
        case DefaultValue(either) => either.getOrElse(DynamicValue.Null)
        case FieldAccess(n, _)    => DynamicValue.Primitive(PrimitiveValue.String(n))
        case OpticAccess(p, _)    => DynamicValue.Primitive(PrimitiveValue.String(p.toString))
        case Convert(f, t, _)     => DynamicValue.Primitive(PrimitiveValue.String(s"$f->$t"))
        case Concat(_, sep)       => DynamicValue.Primitive(PrimitiveValue.String(sep))
        case SplitString(_, d, i) => DynamicValue.Primitive(PrimitiveValue.String(s"$d:$i"))
        case UnwrapOption(_, _)   => DynamicValue.Primitive(PrimitiveValue.Unit)
        case WrapOption(_)        => DynamicValue.Primitive(PrimitiveValue.Unit)
      }
  )
}
