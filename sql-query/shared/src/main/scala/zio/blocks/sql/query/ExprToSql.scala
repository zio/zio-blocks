package zio.blocks.sql.query

import zio.blocks.schema._
import zio.blocks.sql._

object ExprToSql {

  def toSql[S, A](expr: SchemaExpr[S, A], table: Table[S]): Frag = {
    expr match {
      case SchemaExpr.Literal(value, schema) =>
        val dbValue = toDbValue(schema.toDynamicValue(value))
        Frag(IndexedSeq("", ""), IndexedSeq(dbValue))
        
      case SchemaExpr.Optic(optic) =>
        val nodes = optic.toDynamic.nodes
        // Flatten fields to column name
        val parts = nodes.collect {
          case f: DynamicOptic.Node.Field => SqlNameMapper.SnakeCase(f.name)
        }
        val columnName = parts.mkString("_")
        Frag.const(columnName)
        
      case SchemaExpr.Relational(left, right, operator) =>
        val l = toSql(left, table)
        val r = toSql(right, table)
        val op = operator match {
          case _: SchemaExpr.RelationalOperator.Equal.type => "="
          case _: SchemaExpr.RelationalOperator.NotEqual.type => "<>"
          case _: SchemaExpr.RelationalOperator.GreaterThan.type => ">"
          case _: SchemaExpr.RelationalOperator.LessThan.type => "<"
          case _: SchemaExpr.RelationalOperator.GreaterThanOrEqual.type => ">="
          case _: SchemaExpr.RelationalOperator.LessThanOrEqual.type => "<="
        }
        l ++ Frag.const(s" $op ") ++ r
        
      case SchemaExpr.Logical(left, right, operator) =>
        val l = toSql(left, table)
        val r = toSql(right, table)
        val op = operator match {
          case _: SchemaExpr.LogicalOperator.And.type => "AND"
          case _: SchemaExpr.LogicalOperator.Or.type => "OR"
        }
        Frag.const("(") ++ l ++ Frag.const(s" $op ") ++ r ++ Frag.const(")")
        
      case not: SchemaExpr.Not[_] =>
        Frag.const("NOT (") ++ toSql(not.expr, table) ++ Frag.const(")")
        
      case arith: SchemaExpr.Arithmetic[_, _] =>
        val l = toSql(arith.left, table)
        val r = toSql(arith.right, table)
        val op = arith.operator match {
          case _: SchemaExpr.ArithmeticOperator.Add.type => "+"
          case _: SchemaExpr.ArithmeticOperator.Subtract.type => "-"
          case _: SchemaExpr.ArithmeticOperator.Multiply.type => "*"
        }
        Frag.const("(") ++ l ++ Frag.const(s" $op ") ++ r ++ Frag.const(")")
        
      case stringConcat: SchemaExpr.StringConcat[_] =>
        val l = toSql(stringConcat.left, table)
        val r = toSql(stringConcat.right, table)
        l ++ Frag.const(" || ") ++ r
        
      case _ =>
        throw new IllegalArgumentException(s"Unsupported SchemaExpr: $expr")
    }
  }

  def toDbValue(dynamicValue: DynamicValue): DbValue = {
    dynamicValue match {
      case DynamicValue.Primitive(primValue) =>
        primValue match {
          case PrimitiveValue.Int(v) => DbValue.DbInt(v)
          case PrimitiveValue.Long(v) => DbValue.DbLong(v)
          case PrimitiveValue.Double(v) => DbValue.DbDouble(v)
          case PrimitiveValue.Float(v) => DbValue.DbFloat(v)
          case PrimitiveValue.Boolean(v) => DbValue.DbBoolean(v)
          case PrimitiveValue.String(v) => DbValue.DbString(v)
          case PrimitiveValue.Short(v) => DbValue.DbShort(v)
          case PrimitiveValue.Byte(v) => DbValue.DbByte(v)
          case PrimitiveValue.Char(v) => DbValue.DbChar(v)
          case PrimitiveValue.BigDecimal(v) => DbValue.DbBigDecimal(v)
          case PrimitiveValue.LocalDate(v) => DbValue.DbLocalDate(v)
          case PrimitiveValue.LocalDateTime(v) => DbValue.DbLocalDateTime(v)
          case PrimitiveValue.LocalTime(v) => DbValue.DbLocalTime(v)
          case PrimitiveValue.Instant(v) => DbValue.DbInstant(v)
          case PrimitiveValue.Duration(v) => DbValue.DbDuration(v)
          case PrimitiveValue.UUID(v) => DbValue.DbUUID(v)
          case _ => throw new IllegalArgumentException(s"Unsupported primitive value: $primValue")
        }
      case DynamicValue.Null => DbValue.DbNull
      case _ => throw new IllegalArgumentException(s"Unsupported dynamic value: $dynamicValue")
    }
  }

}
