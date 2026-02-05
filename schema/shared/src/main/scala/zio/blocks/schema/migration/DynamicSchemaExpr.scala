package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A serializable expression that operates on [[DynamicValue]] values.
 *
 * Unlike [[SchemaExpr]], this is fully serializable and contains no runtime
 * type references, functions, or closures. It supports primitive-only
 * operations suitable for schema migrations.
 */
sealed trait DynamicSchemaExpr { self =>

  /**
   * Evaluate this expression on the given input value.
   */
  def eval(input: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Compose with another expression using logical AND.
   */
  final def &&(that: DynamicSchemaExpr): DynamicSchemaExpr =
    DynamicSchemaExpr.Logical(self, that, DynamicSchemaExpr.LogicalOperator.And)

  /**
   * Compose with another expression using logical OR.
   */
  final def ||(that: DynamicSchemaExpr): DynamicSchemaExpr =
    DynamicSchemaExpr.Logical(self, that, DynamicSchemaExpr.LogicalOperator.Or)
}

object DynamicSchemaExpr {

  /**
   * Convert a typed [[SchemaExpr]] into a fully serializable
   * [[DynamicSchemaExpr]].
   *
   * Notes:
   *   - Only the subset of SchemaExpr operations supported by DynamicSchemaExpr
   *     can be converted.
   *   - [[SchemaExpr.StringRegexMatch]] is currently not supported.
   */
  def fromSchemaExpr[A, B](expr: SchemaExpr[A, B]): Either[String, DynamicSchemaExpr] = expr match {
    // Scala 2 needs the existential type captured via the case class instance
    // to keep `value` and `schema` tied to the same type parameter.
    case lit: SchemaExpr.Literal[_, _] =>
      Right(DynamicSchemaExpr.Literal(lit.schema.toDynamicValue(lit.value)))

    case SchemaExpr.Optic(optic) =>
      Right(DynamicSchemaExpr.Path(optic.toDynamic))

    case SchemaExpr.Not(inner) =>
      fromSchemaExpr(inner).map(DynamicSchemaExpr.Not(_))

    case SchemaExpr.Logical(left, right, op) =>
      for {
        l <- fromSchemaExpr(left)
        r <- fromSchemaExpr(right)
      } yield {
        val dynOp = op match {
          case SchemaExpr.LogicalOperator.And => DynamicSchemaExpr.LogicalOperator.And
          case SchemaExpr.LogicalOperator.Or  => DynamicSchemaExpr.LogicalOperator.Or
        }
        DynamicSchemaExpr.Logical(l, r, dynOp)
      }

    case SchemaExpr.Relational(left, right, op) =>
      for {
        l <- fromSchemaExpr(left)
        r <- fromSchemaExpr(right)
      } yield {
        val dynOp = op match {
          case SchemaExpr.RelationalOperator.LessThan           => DynamicSchemaExpr.RelationalOperator.LessThan
          case SchemaExpr.RelationalOperator.LessThanOrEqual    => DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
          case SchemaExpr.RelationalOperator.GreaterThan        => DynamicSchemaExpr.RelationalOperator.GreaterThan
          case SchemaExpr.RelationalOperator.GreaterThanOrEqual =>
            DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
          case SchemaExpr.RelationalOperator.Equal    => DynamicSchemaExpr.RelationalOperator.Equal
          case SchemaExpr.RelationalOperator.NotEqual => DynamicSchemaExpr.RelationalOperator.NotEqual
        }
        DynamicSchemaExpr.Relational(l, r, dynOp)
      }

    case SchemaExpr.Arithmetic(left, right, op, _) =>
      for {
        l <- fromSchemaExpr(left)
        r <- fromSchemaExpr(right)
      } yield {
        val dynOp = op match {
          case SchemaExpr.ArithmeticOperator.Add      => DynamicSchemaExpr.ArithmeticOperator.Add
          case SchemaExpr.ArithmeticOperator.Subtract => DynamicSchemaExpr.ArithmeticOperator.Subtract
          case SchemaExpr.ArithmeticOperator.Multiply => DynamicSchemaExpr.ArithmeticOperator.Multiply
        }
        DynamicSchemaExpr.Arithmetic(l, r, dynOp)
      }

    case SchemaExpr.StringConcat(left, right) =>
      for {
        l <- fromSchemaExpr(left)
        r <- fromSchemaExpr(right)
      } yield DynamicSchemaExpr.StringConcat(l, r)

    case _: SchemaExpr.StringRegexMatch[_] =>
      Left("SchemaExpr.StringRegexMatch is not supported by DynamicSchemaExpr")

    case SchemaExpr.StringLength(inner) =>
      fromSchemaExpr(inner).map(DynamicSchemaExpr.StringLength(_))
  }

  /**
   * A literal constant value.
   */
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  }

  /**
   * Extract a value at a path from the input using [[DynamicOptic]].
   */
  final case class Path(optic: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      navigateDynamicValue(input, optic).toRight(
        MigrationError.single(MigrationError.PathNavigationFailed(optic, "Path does not exist in value"))
      )
  }

  /**
   * Use the default value from the schema for the target field. This is a
   * sentinel value that must be resolved at build time.
   */
  case object DefaultValue extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      Left(MigrationError.single(MigrationError.DefaultValueMissing(DynamicOptic.root, "default")))
  }

  /**
   * Resolved default value with the actual DynamicValue.
   */
  final case class ResolvedDefault(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  }

  /**
   * Logical negation.
   */
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      expr.eval(input).flatMap {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Boolean(!b)))
        case other =>
          Left(
            MigrationError.single(
              MigrationError.TypeConversionFailed(
                DynamicOptic.root,
                getDynamicValueTypeName(other),
                "Boolean",
                "Expected a boolean for NOT operation"
              )
            )
          )
      }
  }

  /**
   * Logical operators.
   */
  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      for {
        l      <- left.eval(input)
        r      <- right.eval(input)
        result <- (l, r) match {
                    case (
                          DynamicValue.Primitive(PrimitiveValue.Boolean(lb)),
                          DynamicValue.Primitive(PrimitiveValue.Boolean(rb))
                        ) =>
                      val res = operator match {
                        case LogicalOperator.And => lb && rb
                        case LogicalOperator.Or  => lb || rb
                      }
                      Right(DynamicValue.Primitive(PrimitiveValue.Boolean(res)))
                    case _ =>
                      Left(
                        MigrationError.single(
                          MigrationError.TypeConversionFailed(
                            DynamicOptic.root,
                            "non-boolean",
                            "Boolean",
                            "Expected booleans for logical operation"
                          )
                        )
                      )
                  }
      } yield result
  }

  /**
   * Relational operators.
   */
  sealed trait RelationalOperator
  object RelationalOperator {
    case object LessThan           extends RelationalOperator
    case object LessThanOrEqual    extends RelationalOperator
    case object GreaterThan        extends RelationalOperator
    case object GreaterThanOrEqual extends RelationalOperator
    case object Equal              extends RelationalOperator
    case object NotEqual           extends RelationalOperator
  }

  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      for {
        l <- left.eval(input)
        r <- right.eval(input)
      } yield {
        val cmp    = l.compare(r)
        val result = operator match {
          case RelationalOperator.LessThan           => cmp < 0
          case RelationalOperator.LessThanOrEqual    => cmp <= 0
          case RelationalOperator.GreaterThan        => cmp > 0
          case RelationalOperator.GreaterThanOrEqual => cmp >= 0
          case RelationalOperator.Equal              => cmp == 0
          case RelationalOperator.NotEqual           => cmp != 0
        }
        DynamicValue.Primitive(PrimitiveValue.Boolean(result))
      }
  }

  /**
   * Arithmetic operators.
   */
  sealed trait ArithmeticOperator
  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
  }

  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      for {
        l      <- left.eval(input)
        r      <- right.eval(input)
        result <- (l, r) match {
                    case (DynamicValue.Primitive(lp), DynamicValue.Primitive(rp)) =>
                      evalPrimitiveArithmetic(lp, rp, operator)
                    case _ =>
                      Left(
                        MigrationError.single(
                          MigrationError.TypeConversionFailed(
                            DynamicOptic.root,
                            "non-primitive",
                            "numeric",
                            "Expected primitives for arithmetic"
                          )
                        )
                      )
                  }
      } yield result
  }

  /**
   * String concatenation.
   */
  final case class StringConcat(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      for {
        l      <- left.eval(input)
        r      <- right.eval(input)
        result <- (l, r) match {
                    case (
                          DynamicValue.Primitive(PrimitiveValue.String(ls)),
                          DynamicValue.Primitive(PrimitiveValue.String(rs))
                        ) =>
                      Right(DynamicValue.Primitive(PrimitiveValue.String(ls + rs)))
                    case _ =>
                      Left(
                        MigrationError.single(
                          MigrationError.TypeConversionFailed(
                            DynamicOptic.root,
                            "non-string",
                            "String",
                            "Expected strings for concatenation"
                          )
                        )
                      )
                  }
      } yield result
  }

  /**
   * String length.
   */
  final case class StringLength(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      expr.eval(input).flatMap {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(s.length)))
        case other =>
          Left(
            MigrationError.single(
              MigrationError.TypeConversionFailed(
                DynamicOptic.root,
                getDynamicValueTypeName(other),
                "String",
                "Expected string for length"
              )
            )
          )
      }
  }

  /**
   * Coerce a primitive value to a different primitive type.
   */
  final case class CoercePrimitive(
    expr: DynamicSchemaExpr,
    targetType: String
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      expr.eval(input).flatMap {
        case DynamicValue.Primitive(pv) => coercePrimitive(pv, targetType)
        case other                      =>
          Left(
            MigrationError.single(
              MigrationError.TypeConversionFailed(
                DynamicOptic.root,
                getDynamicValueTypeName(other),
                targetType,
                "Expected primitive"
              )
            )
          )
      }
  }

  // Helper to get type name for error messages
  private def getDynamicValueTypeName(dv: DynamicValue): String = dv match {
    case _: DynamicValue.Primitive => "Primitive"
    case _: DynamicValue.Record    => "Record"
    case _: DynamicValue.Variant   => "Variant"
    case _: DynamicValue.Sequence  => "Sequence"
    case _: DynamicValue.Map       => "Map"
    case DynamicValue.Null         => "Null"
  }

  // Navigate into a DynamicValue using a DynamicOptic path
  private[migration] def navigateDynamicValue(value: DynamicValue, optic: DynamicOptic): Option[DynamicValue] = {
    var current = value
    val nodes   = optic.nodes
    var idx     = 0
    while (idx < nodes.length) {
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          current match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name) match {
                case Some((_, v)) => current = v
                case None         => return None
              }
            case _ => return None
          }
        case DynamicOptic.Node.Case(name) =>
          current match {
            case DynamicValue.Variant(caseName, v) if caseName == name =>
              current = v
            case _ => return None
          }
        case DynamicOptic.Node.AtIndex(index) =>
          current match {
            case DynamicValue.Sequence(elements) if index >= 0 && index < elements.length =>
              current = elements(index)
            case _ => return None
          }
        case DynamicOptic.Node.AtMapKey(key) =>
          current match {
            case DynamicValue.Map(entries) =>
              entries.find(_._1 == key) match {
                case Some((_, v)) => current = v
                case None         => return None
              }
            case _ => return None
          }
        case DynamicOptic.Node.Wrapped =>
          // For wrapped values, we expect a record with a single field
          current match {
            case DynamicValue.Record(fields) if fields.length == 1 =>
              current = fields.head._2
            case _ => return None
          }
        case _ =>
          // Elements, MapKeys, MapValues, AtIndices, AtMapKeys are traversals, not suitable for single-value extraction
          return None
      }
      idx += 1
    }
    Some(current)
  }

  // Primitive arithmetic evaluation
  private def evalPrimitiveArithmetic(
    left: PrimitiveValue,
    right: PrimitiveValue,
    op: ArithmeticOperator
  ): Either[MigrationError, DynamicValue] =
    (left, right) match {
      case (PrimitiveValue.Int(l), PrimitiveValue.Int(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Int(result)))

      case (PrimitiveValue.Long(l), PrimitiveValue.Long(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Long(result)))

      case (PrimitiveValue.Double(l), PrimitiveValue.Double(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Double(result)))

      case (PrimitiveValue.Float(l), PrimitiveValue.Float(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Float(result)))

      case (PrimitiveValue.Short(l), PrimitiveValue.Short(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => (l + r).toShort
          case ArithmeticOperator.Subtract => (l - r).toShort
          case ArithmeticOperator.Multiply => (l * r).toShort
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Short(result)))

      case (PrimitiveValue.Byte(l), PrimitiveValue.Byte(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => (l + r).toByte
          case ArithmeticOperator.Subtract => (l - r).toByte
          case ArithmeticOperator.Multiply => (l * r).toByte
        }
        Right(DynamicValue.Primitive(PrimitiveValue.Byte(result)))

      case (PrimitiveValue.BigInt(l), PrimitiveValue.BigInt(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(result)))

      case (PrimitiveValue.BigDecimal(l), PrimitiveValue.BigDecimal(r)) =>
        val result = op match {
          case ArithmeticOperator.Add      => l + r
          case ArithmeticOperator.Subtract => l - r
          case ArithmeticOperator.Multiply => l * r
        }
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(result)))

      case _ =>
        Left(
          MigrationError.single(
            MigrationError.TypeConversionFailed(
              DynamicOptic.root,
              s"${left.getClass.getSimpleName}, ${right.getClass.getSimpleName}",
              "compatible numeric types",
              "Arithmetic requires matching numeric types"
            )
          )
        )
    }

  // Coerce a primitive value to a target type
  private def coercePrimitive(value: PrimitiveValue, targetType: String): Either[MigrationError, DynamicValue] = {
    def toInt: Either[MigrationError, Int] = value match {
      case PrimitiveValue.Int(v)    => Right(v)
      case PrimitiveValue.Long(v)   => Right(v.toInt)
      case PrimitiveValue.Short(v)  => Right(v.toInt)
      case PrimitiveValue.Byte(v)   => Right(v.toInt)
      case PrimitiveValue.Double(v) => Right(v.toInt)
      case PrimitiveValue.Float(v)  => Right(v.toInt)
      case PrimitiveValue.String(v) => v.toIntOption.toRight(conversionError(value, targetType))
      case _                        => Left(conversionError(value, targetType))
    }

    def toLong: Either[MigrationError, Long] = value match {
      case PrimitiveValue.Long(v)   => Right(v)
      case PrimitiveValue.Int(v)    => Right(v.toLong)
      case PrimitiveValue.Short(v)  => Right(v.toLong)
      case PrimitiveValue.Byte(v)   => Right(v.toLong)
      case PrimitiveValue.Double(v) => Right(v.toLong)
      case PrimitiveValue.Float(v)  => Right(v.toLong)
      case PrimitiveValue.String(v) => v.toLongOption.toRight(conversionError(value, targetType))
      case _                        => Left(conversionError(value, targetType))
    }

    def toDouble: Either[MigrationError, Double] = value match {
      case PrimitiveValue.Double(v) => Right(v)
      case PrimitiveValue.Float(v)  => Right(v.toDouble)
      case PrimitiveValue.Int(v)    => Right(v.toDouble)
      case PrimitiveValue.Long(v)   => Right(v.toDouble)
      case PrimitiveValue.Short(v)  => Right(v.toDouble)
      case PrimitiveValue.Byte(v)   => Right(v.toDouble)
      case PrimitiveValue.String(v) => v.toDoubleOption.toRight(conversionError(value, targetType))
      case _                        => Left(conversionError(value, targetType))
    }

    def toFloat: Either[MigrationError, Float] = value match {
      case PrimitiveValue.Float(v)  => Right(v)
      case PrimitiveValue.Double(v) => Right(v.toFloat)
      case PrimitiveValue.Int(v)    => Right(v.toFloat)
      case PrimitiveValue.Long(v)   => Right(v.toFloat)
      case PrimitiveValue.Short(v)  => Right(v.toFloat)
      case PrimitiveValue.Byte(v)   => Right(v.toFloat)
      case PrimitiveValue.String(v) => v.toFloatOption.toRight(conversionError(value, targetType))
      case _                        => Left(conversionError(value, targetType))
    }

    def toString_ : Either[MigrationError, String] = value match {
      case PrimitiveValue.String(v)     => Right(v)
      case PrimitiveValue.Int(v)        => Right(v.toString)
      case PrimitiveValue.Long(v)       => Right(v.toString)
      case PrimitiveValue.Double(v)     => Right(v.toString)
      case PrimitiveValue.Float(v)      => Right(v.toString)
      case PrimitiveValue.Boolean(v)    => Right(v.toString)
      case PrimitiveValue.Short(v)      => Right(v.toString)
      case PrimitiveValue.Byte(v)       => Right(v.toString)
      case PrimitiveValue.Char(v)       => Right(v.toString)
      case PrimitiveValue.BigInt(v)     => Right(v.toString)
      case PrimitiveValue.BigDecimal(v) => Right(v.toString)
      case _                            => Left(conversionError(value, targetType))
    }

    def toBoolean: Either[MigrationError, Boolean] = value match {
      case PrimitiveValue.Boolean(v) => Right(v)
      case PrimitiveValue.String(v)  => v.toBooleanOption.toRight(conversionError(value, targetType))
      case PrimitiveValue.Int(v)     => Right(v != 0)
      case _                         => Left(conversionError(value, targetType))
    }

    targetType match {
      case "Int"     => toInt.map(v => DynamicValue.Primitive(PrimitiveValue.Int(v)))
      case "Long"    => toLong.map(v => DynamicValue.Primitive(PrimitiveValue.Long(v)))
      case "Double"  => toDouble.map(v => DynamicValue.Primitive(PrimitiveValue.Double(v)))
      case "Float"   => toFloat.map(v => DynamicValue.Primitive(PrimitiveValue.Float(v)))
      case "String"  => toString_.map(v => DynamicValue.Primitive(PrimitiveValue.String(v)))
      case "Boolean" => toBoolean.map(v => DynamicValue.Primitive(PrimitiveValue.Boolean(v)))
      case _         => Left(conversionError(value, targetType))
    }
  }

  private def conversionError(value: PrimitiveValue, targetType: String): MigrationError =
    MigrationError.single(
      MigrationError.TypeConversionFailed(
        DynamicOptic.root,
        value.getClass.getSimpleName,
        targetType,
        "Conversion not supported"
      )
    )
}
