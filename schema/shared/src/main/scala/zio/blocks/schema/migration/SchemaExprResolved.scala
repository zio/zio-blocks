package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}

/**
 * A resolved, serializable expression for use in migration actions.
 *
 * Unlike [[zio.blocks.schema.SchemaExpr]] which operates on typed values and
 * uses optics with bindings, `Resolved` expressions operate on [[DynamicValue]]
 * and are fully serializable as pure data. This enables migrations to be:
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
   * Returns Left for expressions that require input (Identity, FieldAccess,
   * etc.)
   */
  def evalDynamic: Either[String, DynamicValue]

  /**
   * Evaluate this expression with the given input value.
   *
   * @param input
   *   The DynamicValue to evaluate against
   * @return
   *   Right containing the result, or Left with an error message
   */
  def evalDynamic(input: DynamicValue): Either[String, DynamicValue]

  /**
   * Evaluate this expression with root document context for cross-branch
   * access.
   *
   * This method enables expressions like `RootAccess` to access values from
   * anywhere in the document, regardless of the current evaluation context.
   *
   * @param input
   *   The local context (record at current action path)
   * @param root
   *   The root document (for RootAccess expressions)
   * @return
   *   Right containing the result, or Left with an error message
   */
  def evalDynamicWithRoot(input: DynamicValue, @annotation.unused root: DynamicValue): Either[String, DynamicValue] =
    evalDynamic(input) // Default: ignore root, use local context
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
   * through the `inner` expression which is applied first to navigate to the
   * correct context.
   *
   * Semantics: Apply inner to get a record, then extract fieldName from that
   * record.
   */
  final case class FieldAccess(fieldName: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("FieldAccess requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      // First apply inner to navigate to the correct context, then extract the field
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Record(fields) =>
          fields.collectFirst { case (name, v) if name == fieldName => v }
            .toRight(s"Field '$fieldName' not found")

        case other =>
          Left(s"Expected record for field access '$fieldName', got ${other.valueType}")
      }

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap {
        case DynamicValue.Record(fields) =>
          fields.collectFirst { case (name, v) if name == fieldName => v }
            .toRight(s"Field '$fieldName' not found")
        case other =>
          Left(s"Expected record for field access '$fieldName', got ${other.valueType}")
      }
  }

  /**
   * Access a value at a path specified by a DynamicOptic.
   *
   * Provides full path-based navigation including nested fields, sequence
   * elements, and variant cases.
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

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] = {
      val selection = input.get(path)
      selection.one match {
        case Right(value) => inner.evalDynamicWithRoot(value, root)
        case Left(error)  => Left(s"Path $path: ${error.message}")
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Root Access (for Cross-Branch Operations)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Access a value at an absolute path from the root document.
   *
   * Enables cross-branch operations where source fields are in different
   * subtrees of the document. Unlike other access expressions that operate on
   * the local context, RootAccess always navigates from the document root.
   */
  final case class RootAccess(path: DynamicOptic) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("RootAccess requires root context")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      evalDynamicWithRoot(input, input) // Fallback: treat input as root

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      root.get(path).one match {
        case Right(value) => Right(value)
        case Left(error)  => Left(s"Root path $path: ${error.message}")
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Default Value Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Use a schema's default value.
   *
   * The default value is stored as a DynamicValue at construction time,
   * ensuring the expression remains serializable without storing the schema. If
   * the schema has no default, evaluation will fail.
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
   * Type conversion is identified by string names (e.g., "Int", "Long",
   * "String") to maintain serializability. Actual conversion is delegated to
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

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap { value =>
        PrimitiveConversions.convert(value, fromTypeName, toTypeName)
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // String Operations (for Join/Split)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Concatenate multiple expressions into a single string.
   *
   * Used for join operations that combine multiple fields into one. Each part
   * is evaluated and converted to a string, then joined with the specified
   * separator.
   */
  final case class Concat(parts: Vector[Resolved], separator: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] = {
      // Concat can work without input if all parts can evaluate without input (e.g., all literals)
      val results = parts.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (Right(acc), part) =>
          part.evalDynamic.map { value =>
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

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] = {
      val results = parts.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (Right(acc), part) =>
          part.evalDynamicWithRoot(input, root).map { value =>
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
   * Used for split operations that divide one field into multiple fields. The
   * result is a Sequence of Primitive strings.
   */
  final case class SplitString(separator: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("SplitString requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(java.util.regex.Pattern.quote(separator), -1)
          Right(DynamicValue.Sequence(parts.toSeq.map(p => DynamicValue.Primitive(PrimitiveValue.String(p))): _*))

        case other =>
          Left(s"SplitString requires String input, got ${other.valueType}")
      }

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(java.util.regex.Pattern.quote(separator), -1)
          Right(DynamicValue.Sequence(parts.toSeq.map(p => DynamicValue.Primitive(PrimitiveValue.String(p))): _*))
        case other =>
          Left(s"SplitString requires String input, got ${other.valueType}")
      }
  }

  /**
   * Extract an element at a specific index from a sequence, then apply inner.
   *
   * Semantics: Input must be a Sequence. Extract element at index, then apply
   * inner to the extracted element.
   *
   * For cases where you need to evaluate an expression that produces a sequence
   * first, use Compose: `Compose(At(index, Identity), sequenceProducingExpr)`
   */
  final case class At(index: Int, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("At requires sequence input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      extractAndApply(input, inner.evalDynamic)

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      extractAndApply(input, v => inner.evalDynamicWithRoot(v, root))

    private def extractAndApply(
      input: DynamicValue,
      applyInner: DynamicValue => Either[String, DynamicValue]
    ): Either[String, DynamicValue] =
      input match {
        case DynamicValue.Sequence(elements) if index >= 0 && index < elements.length =>
          applyInner(elements(index))
        case DynamicValue.Sequence(elements) =>
          Left(s"Index $index out of bounds (length: ${elements.length})")
        case other =>
          Left(s"Expected sequence for At, got ${other.valueType}")
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
      inner.evalDynamic.map(v => DynamicValue.Variant("Some", DynamicValue.Record(("value", v))))

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).map(v => DynamicValue.Variant("Some", DynamicValue.Record(("value", v))))

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).map(v => DynamicValue.Variant("Some", DynamicValue.Record(("value", v))))
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

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap {
        case DynamicValue.Variant("Some", value) => Right(value)
        case DynamicValue.Variant("None", _)     => fallback.evalDynamicWithRoot(input, root)
        case DynamicValue.Null                   => fallback.evalDynamicWithRoot(input, root)
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

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap(v => outer.evalDynamicWithRoot(v, root))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Failure Marker
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Marker for non-reversible operations.
   *
   * Used in reverse migrations when the forward operation loses information
   * that cannot be recovered. Evaluation always fails with the specified
   * message.
   */
  final case class Fail(message: String) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] = Left(message)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] = Left(message)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Record/Sequence Construction
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Construct a record from field name-expression pairs.
   *
   * Each field is evaluated and assembled into a Record DynamicValue. Used for
   * complex restructuring operations.
   */
  final case class Construct(fields: Vector[(String, Resolved)]) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      evalDynamic(DynamicValue.Primitive(PrimitiveValue.Unit))

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      fields
        .foldLeft[Either[String, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), (name, expr)) =>
            expr.evalDynamic(input).map(v => acc :+ (name -> v))
          case (left, _) => left
        }
        .map(v => DynamicValue.Record(v: _*))

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      fields
        .foldLeft[Either[String, Vector[(String, DynamicValue)]]](Right(Vector.empty)) {
          case (Right(acc), (name, expr)) =>
            expr.evalDynamicWithRoot(input, root).map(v => acc :+ (name -> v))
          case (left, _) => left
        }
        .map(v => DynamicValue.Record(v: _*))
  }

  /**
   * Construct a sequence from element expressions.
   *
   * Each element expression is evaluated and assembled into a Sequence.
   */
  final case class ConstructSeq(elements: Vector[Resolved]) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      evalDynamic(DynamicValue.Primitive(PrimitiveValue.Unit))

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      elements
        .foldLeft[Either[String, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (Right(acc), expr) =>
            expr.evalDynamic(input).map(v => acc :+ v)
          case (left, _) => left
        }
        .map(v => DynamicValue.Sequence(v: _*))

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      elements
        .foldLeft[Either[String, Vector[DynamicValue]]](Right(Vector.empty)) {
          case (Right(acc), expr) =>
            expr.evalDynamicWithRoot(input, root).map(v => acc :+ v)
          case (left, _) => left
        }
        .map(v => DynamicValue.Sequence(v: _*))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Sequence Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Extract the first element from a sequence.
   *
   * Returns the first element if the sequence is non-empty, otherwise fails.
   */
  final case class Head(inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("Head requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Sequence(elements) if elements.nonEmpty =>
          Right(elements.head)
        case DynamicValue.Sequence(_) =>
          Left("Cannot get head of empty sequence")
        case other =>
          Left(s"Expected sequence for head operation, got ${other.valueType}")
      }

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap {
        case DynamicValue.Sequence(elements) if elements.nonEmpty =>
          Right(elements.head)
        case DynamicValue.Sequence(_) =>
          Left("Cannot get head of empty sequence")
        case other =>
          Left(s"Expected sequence for head operation, got ${other.valueType}")
      }
  }

  /**
   * Join sequence elements into a string with a separator.
   *
   * Evaluates the inner expression to get a sequence, then joins all elements
   * into a single string using the specified separator.
   */
  final case class JoinStrings(separator: String, inner: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      Left("JoinStrings requires input")

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamic(input).flatMap {
        case DynamicValue.Sequence(elements) =>
          val strings = elements.map {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
            case other                                            => other.toString
          }
          Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(separator))))
        case other =>
          Left(s"Expected sequence for JoinStrings, got ${other.valueType}")
      }

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      inner.evalDynamicWithRoot(input, root).flatMap {
        case DynamicValue.Sequence(elements) =>
          val strings = elements.map {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
            case other                                            => other.toString
          }
          Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(separator))))
        case other =>
          Left(s"Expected sequence for JoinStrings, got ${other.valueType}")
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Fallback Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Return the first successful result from a list of expressions.
   *
   * Tries each expression in order until one succeeds. If all fail, returns the
   * last failure message.
   */
  final case class Coalesce(alternatives: Vector[Resolved]) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      evalWithAlternatives(None, None)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      evalWithAlternatives(Some(input), None)

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      evalWithAlternatives(Some(input), Some(root))

    private def evalWithAlternatives(
      input: Option[DynamicValue],
      root: Option[DynamicValue]
    ): Either[String, DynamicValue] =
      if (alternatives.isEmpty) {
        Left("Coalesce requires at least one alternative")
      } else {
        alternatives.iterator.map { alt =>
          (input, root) match {
            case (Some(in), Some(r)) => alt.evalDynamicWithRoot(in, r)
            case (Some(in), None)    => alt.evalDynamic(in)
            case (None, _)           => alt.evalDynamic
          }
        }.find {
          // Skip None values, find first Some or non-Option value
          case Right(DynamicValue.Variant("None", _)) => false
          case Right(_)                               => true
          case Left(_)                                => false
        }.getOrElse(Left("All alternatives were None or failed"))
      }
  }

  /**
   * Try an expression, returning a fallback value if it fails.
   *
   * Similar to Option.getOrElse - attempts to evaluate the primary expression,
   * and returns the fallback if the primary fails.
   */
  final case class GetOrElse(primary: Resolved, fallback: Resolved) extends Resolved {
    def evalDynamic: Either[String, DynamicValue] =
      primary.evalDynamic match {
        case Right(DynamicValue.Variant("Some", DynamicValue.Record(fields))) =>
          // Extract value from Some(Record(Vector(("value", inner))))
          fields.find(_._1 == "value").map(kv => Right(kv._2)).getOrElse(fallback.evalDynamic)
        case Right(DynamicValue.Variant("None", _)) =>
          // None case, use fallback
          fallback.evalDynamic
        case Right(DynamicValue.Null) =>
          // Null treated as None
          fallback.evalDynamic
        case Right(value) =>
          // Non-option value, return as-is
          Right(value)
        case Left(_) =>
          fallback.evalDynamic
      }

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      primary.evalDynamic(input) match {
        case Right(DynamicValue.Variant("Some", DynamicValue.Record(fields))) =>
          // Extract value from Some(Record(Vector(("value", inner))))
          fields.find(_._1 == "value").map(kv => Right(kv._2)).getOrElse(fallback.evalDynamic(input))
        case Right(DynamicValue.Variant("None", _)) =>
          // None case, use fallback
          fallback.evalDynamic(input)
        case Right(DynamicValue.Null) =>
          // Null treated as None
          fallback.evalDynamic(input)
        case Right(value) =>
          // Non-option value, return as-is
          Right(value)
        case Left(_) =>
          fallback.evalDynamic(input)
      }

    override def evalDynamicWithRoot(input: DynamicValue, root: DynamicValue): Either[String, DynamicValue] =
      primary.evalDynamicWithRoot(input, root) match {
        case Right(DynamicValue.Variant("Some", DynamicValue.Record(fields))) =>
          fields.find(_._1 == "value").map(kv => Right(kv._2)).getOrElse(fallback.evalDynamicWithRoot(input, root))
        case Right(DynamicValue.Variant("None", _)) =>
          fallback.evalDynamicWithRoot(input, root)
        case Right(DynamicValue.Null) =>
          fallback.evalDynamicWithRoot(input, root)
        case Right(value) =>
          Right(value)
        case Left(_) =>
          fallback.evalDynamicWithRoot(input, root)
      }
  }

  /**
   * Schema for Resolved enabling serialization to JSON, Protobuf, etc.
   */
  implicit def schema: zio.blocks.schema.Schema[Resolved] =
    MigrationSchemas.resolvedSchema
}
