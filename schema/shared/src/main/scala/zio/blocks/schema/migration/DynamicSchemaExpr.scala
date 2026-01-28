package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}

sealed trait DynamicSchemaExpr

object DynamicSchemaExpr {
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr
  final case class Path(path: DynamicOptic)     extends DynamicSchemaExpr
  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: SchemaExpr.RelationalOperator
  ) extends DynamicSchemaExpr
  final case class Logical(left: DynamicSchemaExpr, right: DynamicSchemaExpr, operator: SchemaExpr.LogicalOperator)
      extends DynamicSchemaExpr
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr
  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: SchemaExpr.ArithmeticOperator
  ) extends DynamicSchemaExpr
  final case class StringConcat(left: DynamicSchemaExpr, right: DynamicSchemaExpr)       extends DynamicSchemaExpr
  final case class StringRegexMatch(regex: DynamicSchemaExpr, string: DynamicSchemaExpr) extends DynamicSchemaExpr
  final case class StringLength(string: DynamicSchemaExpr)                               extends DynamicSchemaExpr

  def eval(expr: DynamicSchemaExpr, input: DynamicValue): Either[MigrationError, DynamicValue] =
    expr match {
      case Literal(value) => Right(value)
      case Path(path)     =>
        val selection = input.get(path)
        selection.values.flatMap(_.headOption).toRight(MigrationError.EvaluationError(path, "Path not found"))
      case Relational(left, right, op) =>
        for {
          l <- eval(left, input)
          r <- eval(right, input)
        } yield {
          val cmp = l.compare(r)
          val res = op match {
            case SchemaExpr.RelationalOperator.Equal              => cmp == 0
            case SchemaExpr.RelationalOperator.NotEqual           => cmp != 0
            case SchemaExpr.RelationalOperator.LessThan           => cmp < 0
            case SchemaExpr.RelationalOperator.GreaterThan        => cmp > 0
            case SchemaExpr.RelationalOperator.LessThanOrEqual    => cmp <= 0
            case SchemaExpr.RelationalOperator.GreaterThanOrEqual => cmp >= 0
          }
          DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(res))
        }
      case Logical(left, right, op) =>
        for {
          l  <- eval(left, input)
          r  <- eval(right, input)
          lb <- asBoolean(l).toRight(MigrationError.ValidationError("Expected Boolean"))
          rb <- asBoolean(r).toRight(MigrationError.ValidationError("Expected Boolean"))
        } yield {
          val res = op match {
            case SchemaExpr.LogicalOperator.And => lb && rb
            case SchemaExpr.LogicalOperator.Or  => lb || rb
          }
          DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(res))
        }
      case Not(subExpr) =>
        eval(subExpr, input).flatMap { v =>
          asBoolean(v)
            .map(b => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(!b)))
            .toRight(MigrationError.ValidationError("Expected Boolean"))
        }

      // ... Implement other cases (Arithmetic, String ops) similarly ...
      case _ => Left(MigrationError.ValidationError(s"Expression evaluation not fully implemented for $expr"))
    }

  private def asBoolean(dv: DynamicValue): Option[Boolean] = dv match {
    case DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(b)) => Some(b)
    case _                                                                   => None
  }

  def fromSchemaExpr[A, B](expr: SchemaExpr[A, B]): DynamicSchemaExpr = expr match {
    case SchemaExpr.Literal(value, schema)          => Literal(schema.toDynamicValue(value.asInstanceOf[schema.A]))
    case SchemaExpr.Optic(optic)                    => Path(optic.toDynamic)
    case SchemaExpr.Relational(left, right, op)     => Relational(fromSchemaExpr(left), fromSchemaExpr(right), op)
    case SchemaExpr.Logical(left, right, op)        => Logical(fromSchemaExpr(left), fromSchemaExpr(right), op)
    case SchemaExpr.Not(expr)                       => Not(fromSchemaExpr(expr))
    case SchemaExpr.Arithmetic(left, right, op, _)  => Arithmetic(fromSchemaExpr(left), fromSchemaExpr(right), op)
    case SchemaExpr.StringConcat(left, right)       => StringConcat(fromSchemaExpr(left), fromSchemaExpr(right))
    case SchemaExpr.StringRegexMatch(regex, string) => StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))
    case SchemaExpr.StringLength(string)            => StringLength(fromSchemaExpr(string))
    case _                                          => throw new IllegalArgumentException(s"Unsupported SchemaExpr: $expr")
  }
}
