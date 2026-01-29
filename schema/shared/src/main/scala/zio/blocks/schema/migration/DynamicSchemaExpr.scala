package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A serializable expression that operates on DynamicValue.
 *
 * This is used for all value-level transformations in migrations. Unlike
 * SchemaExpr which operates on typed values, DynamicSchemaExpr works with
 * the untyped DynamicValue representation.
 */
sealed trait DynamicSchemaExpr

object DynamicSchemaExpr {

  /**
   * A literal value.
   */
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr

  /**
   * A path to extract a value from the input.
   */
  final case class Path(path: DynamicOptic) extends DynamicSchemaExpr

  /**
   * A relational operation (==, !=, <, >, <=, >=).
   */
  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: SchemaExpr.RelationalOperator
  ) extends DynamicSchemaExpr

  /**
   * A logical operation (&&, ||).
   */
  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: SchemaExpr.LogicalOperator
  ) extends DynamicSchemaExpr

  /**
   * Logical negation.
   */
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr

  /**
   * An arithmetic operation (+, -, *, /, %).
   */
  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: SchemaExpr.ArithmeticOperator
  ) extends DynamicSchemaExpr

  /**
   * String concatenation.
   */
  final case class StringConcat(left: DynamicSchemaExpr, right: DynamicSchemaExpr) extends DynamicSchemaExpr

  /**
   * Regex matching on strings.
   */
  final case class StringRegexMatch(regex: DynamicSchemaExpr, string: DynamicSchemaExpr) extends DynamicSchemaExpr

  /**
   * String length.
   */
  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr

  /**
   * Conditional expression (if-then-else).
   */
  final case class Condition(
    predicate: DynamicSchemaExpr,
    ifTrue: DynamicSchemaExpr,
    ifFalse: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  /**
   * Default value from schema.
   */
  final case class DefaultValue(schema: Schema[_]) extends DynamicSchemaExpr

  // ==========================================================================
  // Evaluation
  // ==========================================================================

  /**
   * Evaluates a DynamicSchemaExpr against an input DynamicValue.
   *
   * @param expr the expression to evaluate
   * @param input the input value
   * @return either an error or the resulting DynamicValue
   */
  def eval(expr: DynamicSchemaExpr, input: DynamicValue): Either[MigrationError, DynamicValue] =
    expr match {
      case Literal(value) =>
        Right(value)

      case Path(path) =>
        val selection = input.get(path)
        selection.values.flatMap(_.headOption) match {
          case Some(value) => Right(value)
          case None        => Left(MigrationError.PathNotFound(path))
        }

      case Relational(left, right, op) =>
        for {
          l <- eval(left, input)
          r <- eval(right, input)
        } yield evalRelational(l, r, op)

      case Logical(left, right, op) =>
        for {
          l  <- eval(left, input)
          r  <- eval(right, input)
          lb <- asBoolean(l).toRight(MigrationError.ValidationError("Expected Boolean in left operand"))
          rb <- asBoolean(r).toRight(MigrationError.ValidationError("Expected Boolean in right operand"))
        } yield DynamicValue.Primitive(PrimitiveValue.Boolean(evalLogical(lb, rb, op)))

      case Not(subExpr) =>
        eval(subExpr, input).flatMap { v =>
          asBoolean(v)
            .map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(!b)))
            .toRight(MigrationError.ValidationError("Expected Boolean for negation"))
        }

      case Arithmetic(left, right, op) =>
        for {
          l <- eval(left, input)
          r <- eval(right, input)
          result <- evalArithmetic(l, r, op)
        } yield result

      case StringConcat(left, right) =>
        for {
          l <- eval(left, input)
          r <- eval(right, input)
          ls <- asString(l).toRight(MigrationError.ValidationError("Expected String in left operand"))
          rs <- asString(r).toRight(MigrationError.ValidationError("Expected String in right operand"))
        } yield DynamicValue.Primitive(PrimitiveValue.String(ls + rs))

      case StringRegexMatch(regex, string) =>
        for {
          r <- eval(regex, input)
          s <- eval(string, input)
          rs <- asString(r).toRight(MigrationError.ValidationError("Expected String regex"))
          ss <- asString(s).toRight(MigrationError.ValidationError("Expected String"))
        } yield DynamicValue.Primitive(PrimitiveValue.Boolean(ss.matches(rs)))

      case StringLength(string) =>
        eval(string, input).flatMap { s =>
          asString(s)
            .map(str => DynamicValue.Primitive(PrimitiveValue.Int(str.length)))
            .toRight(MigrationError.ValidationError("Expected String for length"))
        }

      case Condition(predicate, ifTrue, ifFalse) =>
        eval(predicate, input).flatMap { p =>
          asBoolean(p) match {
            case Some(true)  => eval(ifTrue, input)
            case Some(false) => eval(ifFalse, input)
            case None        => Left(MigrationError.ValidationError("Expected Boolean in condition"))
          }
        }

      case DefaultValue(schema) =>
        schema.defaultValue match {
          case Some(value) => Right(schema.toDynamicValue(value))
          case None        => Left(MigrationError.ValidationError(s"Schema has no default value"))
        }
    }

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  private def asBoolean(dv: DynamicValue): Option[Boolean] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => Some(b)
    case _                                                 => None
  }

  private def asString(dv: DynamicValue): Option[String] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
    case _                                                => None
  }

  private def asInt(dv: DynamicValue): Option[Int] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Int(i)) => Some(i)
    case _                                             => None
  }

  private def asLong(dv: DynamicValue): Option[Long] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Long(l)) => Some(l)
    case _                                              => None
  }

  private def asDouble(dv: DynamicValue): Option[Double] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Double(d)) => Some(d)
    case _                                                => None
  }

  private def evalRelational(
    left: DynamicValue,
    right: DynamicValue,
    op: SchemaExpr.RelationalOperator
  ): DynamicValue = {
    val cmp = left.compare(right)
    val result = op match {
      case SchemaExpr.RelationalOperator.Equal              => cmp == 0
      case SchemaExpr.RelationalOperator.NotEqual           => cmp != 0
      case SchemaExpr.RelationalOperator.LessThan           => cmp < 0
      case SchemaExpr.RelationalOperator.GreaterThan        => cmp > 0
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => cmp <= 0
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => cmp >= 0
    }
    DynamicValue.Primitive(PrimitiveValue.Boolean(result))
  }

  private def evalLogical(left: Boolean, right: Boolean, op: SchemaExpr.LogicalOperator): Boolean =
    op match {
      case SchemaExpr.LogicalOperator.And => left && right
      case SchemaExpr.LogicalOperator.Or  => left || right
    }

  private def evalArithmetic(
    left: DynamicValue,
    right: DynamicValue,
    op: SchemaExpr.ArithmeticOperator
  ): Either[MigrationError, DynamicValue] = {
    // Try to handle numeric types
    (asInt(left), asInt(right)) match {
      case (Some(l), Some(r)) =>
        val result = op match {
          case SchemaExpr.ArithmeticOperator.Add      => DynamicValue.Primitive(PrimitiveValue.Int(l + r))
          case SchemaExpr.ArithmeticOperator.Subtract => DynamicValue.Primitive(PrimitiveValue.Int(l - r))
          case SchemaExpr.ArithmeticOperator.Multiply => DynamicValue.Primitive(PrimitiveValue.Int(l * r))
          case SchemaExpr.ArithmeticOperator.Divide   =>
            if (r == 0) return Left(MigrationError.ValidationError("Division by zero"))
            DynamicValue.Primitive(PrimitiveValue.Int(l / r))
          case SchemaExpr.ArithmeticOperator.Modulo =>
            if (r == 0) return Left(MigrationError.ValidationError("Modulo by zero"))
            DynamicValue.Primitive(PrimitiveValue.Int(l % r))
        }
        Right(result)
      case _ =>
        (asLong(left), asLong(right)) match {
          case (Some(l), Some(r)) =>
            val result = op match {
              case SchemaExpr.ArithmeticOperator.Add      => DynamicValue.Primitive(PrimitiveValue.Long(l + r))
              case SchemaExpr.ArithmeticOperator.Subtract => DynamicValue.Primitive(PrimitiveValue.Long(l - r))
              case SchemaExpr.ArithmeticOperator.Multiply => DynamicValue.Primitive(PrimitiveValue.Long(l * r))
              case SchemaExpr.ArithmeticOperator.Divide   =>
                if (r == 0) return Left(MigrationError.ValidationError("Division by zero"))
                DynamicValue.Primitive(PrimitiveValue.Long(l / r))
              case SchemaExpr.ArithmeticOperator.Modulo =>
                if (r == 0) return Left(MigrationError.ValidationError("Modulo by zero"))
                DynamicValue.Primitive(PrimitiveValue.Long(l % r))
            }
            Right(result)
          case _ =>
            (asDouble(left), asDouble(right)) match {
              case (Some(l), Some(r)) =>
                val result = op match {
                  case SchemaExpr.ArithmeticOperator.Add      => DynamicValue.Primitive(PrimitiveValue.Double(l + r))
                  case SchemaExpr.ArithmeticOperator.Subtract => DynamicValue.Primitive(PrimitiveValue.Double(l - r))
                  case SchemaExpr.ArithmeticOperator.Multiply => DynamicValue.Primitive(PrimitiveValue.Double(l * r))
                  case SchemaExpr.ArithmeticOperator.Divide   =>
                    if (r == 0.0) return Left(MigrationError.ValidationError("Division by zero"))
                    DynamicValue.Primitive(PrimitiveValue.Double(l / r))
                  case SchemaExpr.ArithmeticOperator.Modulo =>
                    DynamicValue.Primitive(PrimitiveValue.Double(l % r))
                }
                Right(result)
              case _ => Left(MigrationError.ValidationError("Arithmetic operations require numeric operands"))
            }
        }
    }
  }

  // ==========================================================================
  // Conversion from SchemaExpr
  // ==========================================================================

  /**
   * Converts a typed SchemaExpr to a DynamicSchemaExpr.
   */
  def fromSchemaExpr[A, B](expr: SchemaExpr[A, B]): DynamicSchemaExpr = expr match {
    case lit: SchemaExpr.Literal[A, b] =>
      Literal(lit.schema.toDynamicValue(lit.value))

    case SchemaExpr.Optic(optic) =>
      Path(optic.toDynamic)

    case SchemaExpr.Relational(left, right, op) =>
      Relational(fromSchemaExpr(left), fromSchemaExpr(right), op)

    case SchemaExpr.Logical(left, right, op) =>
      Logical(fromSchemaExpr(left), fromSchemaExpr(right), op)

    case SchemaExpr.Not(expr) =>
      Not(fromSchemaExpr(expr))

    case SchemaExpr.Arithmetic(left, right, op, _) =>
      Arithmetic(fromSchemaExpr(left), fromSchemaExpr(right), op)

    case SchemaExpr.StringConcat(left, right) =>
      StringConcat(fromSchemaExpr(left), fromSchemaExpr(right))

    case SchemaExpr.StringRegexMatch(regex, string) =>
      StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))

    case SchemaExpr.StringLength(string) =>
      StringLength(fromSchemaExpr(string))

    case _ =>
      // For unsupported expressions, return a literal null
      Literal(DynamicValue.Null)
  }
}
