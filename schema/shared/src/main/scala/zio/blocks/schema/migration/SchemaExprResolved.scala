package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}

/**
 * A resolved, serializable expression for use in migration actions.
 *
 * Unlike [[zio.blocks.schema.SchemaExpr]] which operates on typed values and uses
 * optics with bindings, `Resolved` expressions operate on [[DynamicValue]] and are
 * fully serializable as pure data. This enables migrations to be:
 *   - Stored in schema registries
 *   - Transmitted over the network
 *   - Applied without reflection or runtime code generation
 *
 * Each variant represents a specific pure operation:
 *   - [[Resolved.Literal]] - Constant values
 *   - [[Resolved.Identity]] - Pass-through the input
 *   - [[Resolved.FieldAccess]] - Extract a field from a record
 *   - [[Resolved.DefaultValue]] - Use a schema's default value
 *   - [[Resolved.Convert]] - Primitive type conversion
 *   - [[Resolved.Concat]] - String concatenation for joins
 *   - [[Resolved.SplitString]] - String splitting for splits
 *   - [[Resolved.Fail]] - Marker for non-reversible operations
 */
sealed trait Resolved { self =>

  /**
   * Evaluate this expression without input.
   *
   * Returns Left for expressions that require input (Identity, FieldAccess, etc.)
   */
  def evalDynamic: Either[String, DynamicValue]

  /**
   * Evaluate this expression with the given input value.
   *
   * @param input The DynamicValue to evaluate against
   * @return Right containing the result, or Left with an error message
   */
  def evalDynamic(input: DynamicValue): Either[String, DynamicValue]
}

object Resolved {

  // ─────────────────────────────────────────────────────────────────────────
  // Value Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A literal constant value.
   *
   * The value is stored as a DynamicValue, making it fully serializable.
   * Evaluation ignores any input and always returns this constant.
   */
  final case class Literal(value: DynamicValue) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] = Right(value)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  object Literal {

    /** Create a literal from a typed value using its schema */
    def apply[A](value: A, schema: Schema[A]): Literal =
      Literal(schema.toDynamicValue(value))

    /** Create a string literal */
    def string(value: String): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.String(value)))

    /** Create an int literal */
    def int(value: Int): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Int(value)))

    /** Create a long literal */
    def long(value: Long): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Long(value)))

    /** Create a boolean literal */
    def boolean(value: Boolean): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(value)))

    /** Create a double literal */
    def double(value: Double): Literal =
      Literal(DynamicValue.Primitive(PrimitiveValue.Double(value)))

    /** Unit literal */
    val unit: Literal = Literal(DynamicValue.Primitive(PrimitiveValue.Unit))
  }

  /**
   * Reference to the input value itself (identity function).
   *
   * Returns the input unchanged. Useful as a base for composition.
   */
  case object Identity extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Identity requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      Right(input)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Access Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Extract a field from a record.
   *
   * The field name is stored as a string, and nested access is supported
   * through the `inner` expression which is applied to the field value.
   */
  final case class FieldAccess(fieldName: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("FieldAccess requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      input match {
        case DynamicValue.Record(fields) =>
          fields.collectFirst { case (name, v) if name == fieldName => v }
            .toRight(s"Field '$fieldName' not found")
            .flatMap(inner.evalDynamic)

        case _ =>
          Left(s"Expected record for field access '$fieldName', got ${input.valueType}")
      }
  }

  /**
   * Access a value at a path specified by a DynamicOptic.
   *
   * Provides full path-based navigation including nested fields,
   * sequence elements, and variant cases.
   */
  final case class OpticAccess(path: DynamicOptic, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("OpticAccess requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = {
      val selection = input.get(path)
      selection.one match {
        case Right(value) => inner.evalDynamic(value)
        case Left(error)  => Left(s"Path $path: ${error.message}")
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Default Value Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Use a schema's default value.
   *
   * The default value is stored as a DynamicValue at construction time,
   * ensuring the expression remains serializable without storing the schema.
   * If the schema has no default, evaluation will fail.
   */
  final case class DefaultValue(defaultDynamic: Either[String, DynamicValue]) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] = defaultDynamic

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = defaultDynamic
  }

  object DefaultValue {

    /**
     * Create a DefaultValue from an explicit value and schema.
     *
     * Use this when you have a known default value.
     */
    def fromValue[A](value: A, schema: Schema[A]): DefaultValue =
      DefaultValue(Right(schema.toDynamicValue(value)))

    /**
     * Create a DefaultValue that always fails.
     *
     * Use this when no default value is available.
     */
    def noDefault: DefaultValue =
      DefaultValue(Left("No default value defined"))

    /**
     * Create a DefaultValue with a custom error message.
     */
    def fail(message: String): DefaultValue =
      DefaultValue(Left(message))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Conversion Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Convert between primitive types.
   *
   * Type conversion is identified by string names (e.g., "Int", "Long", "String")
   * to maintain serializability. Actual conversion is delegated to
   * [[PrimitiveConversions]].
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
        PrimitiveConversions.convert(value, fromTypeName, toTypeName)
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // String Operations (for Join/Split)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Concatenate multiple expressions into a single string.
   *
   * Used for join operations that combine multiple fields into one.
   * Each part is evaluated and converted to a string, then joined with
   * the specified separator.
   */
  final case class Concat(parts: Vector[Resolved], separator: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Concat requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = {
      val results = parts.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (Right(acc), part) =>
          part.evalDynamic(input).map { value =>
            val str = value match {
              case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
              case other                                            => other.toString
            }
            acc :+ str
          }
        case (left, _) => left
      }
      results.map(strings => DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(separator))))
    }
  }

  /**
   * Split a string into multiple parts.
   *
   * Used for split operations that divide one field into multiple fields.
   * The result is a Sequence of Primitive strings.
   */
  final case class SplitString(separator: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("SplitString requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).map {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(java.util.regex.Pattern.quote(separator), -1)
          DynamicValue.Sequence(parts.map(p => DynamicValue.Primitive(PrimitiveValue.String(p))).toVector)

        case other =>
          // Non-string values become single-element sequences
          DynamicValue.Sequence(Vector(other))
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Option Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Wrap a value in Some.
   *
   * Used by optionalize operations to convert mandatory fields to optional.
   */
  final case class WrapSome(inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      inner.evalDynamic.map(v => DynamicValue.Variant("Some", v))

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).map(v => DynamicValue.Variant("Some", v))
  }

  /**
   * Unwrap Some, using a fallback for None.
   *
   * Used by mandate operations to convert optional fields to mandatory.
   */
  final case class UnwrapOption(inner: Resolved, fallback: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("UnwrapOption requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Variant("Some", value) => Right(value)
        case DynamicValue.Variant("None", _)     => fallback.evalDynamic
        case DynamicValue.Null                   => fallback.evalDynamic
        case other                               => Right(other) // Already non-optional
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Composition
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Compose two expressions: apply inner, then outer.
   *
   * Enables building complex transformations from simple parts.
   */
  final case class Compose(outer: Resolved, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      inner.evalDynamic.flatMap(outer.evalDynamic)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap(outer.evalDynamic)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Failure Marker
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Marker for non-reversible operations.
   *
   * Used in reverse migrations when the forward operation loses information
   * that cannot be recovered. Evaluation always fails with the specified message.
   */
  final case class Fail(message: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] = Left(message)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = Left(message)
  }
}
