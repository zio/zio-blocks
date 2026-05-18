/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package object querydslsql {

  import zio.blocks.schema._

  // ---------------------------------------------------------------------------
  // SQL rendering helpers
  // ---------------------------------------------------------------------------

  def columnName(optic: Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def columnName(optic: DynamicOptic): String =
    optic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  private def quoted(value: String): String = s"'${value.replace("'", "''")}'"

  private def unsupportedSqlLiteral(value: Any): Nothing =
    throw new IllegalArgumentException(s"Unsupported SQL literal: ${String.valueOf(value)}")

  def sqlLiteral(value: Any): String = value match {
    case null                                   => "NULL"
    case s: String                              => quoted(s)
    case c: Char                                => quoted(c.toString)
    case b: Boolean                             => if (b) "TRUE" else "FALSE"
    case n: Number                              => n.toString
    case v: java.time.temporal.TemporalAccessor => quoted(v.toString)
    case v: java.time.Duration                  => quoted(v.toString)
    case v: java.time.Period                    => quoted(v.toString)
    case v: java.util.Currency                  => quoted(v.getCurrencyCode)
    case v: java.util.UUID                      => quoted(v.toString)
    case other                                  => unsupportedSqlLiteral(other)
  }

  def sqlLiteralDV(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.Unit              => "NULL"
        case PrimitiveValue.String(s)         => quoted(s)
        case PrimitiveValue.Char(c)           => quoted(c.toString)
        case PrimitiveValue.Boolean(b)        => if (b) "TRUE" else "FALSE"
        case PrimitiveValue.Int(n)            => n.toString
        case PrimitiveValue.Long(n)           => n.toString
        case PrimitiveValue.Double(n)         => n.toString
        case PrimitiveValue.Float(n)          => n.toString
        case PrimitiveValue.Short(n)          => n.toString
        case PrimitiveValue.Byte(n)           => n.toString
        case PrimitiveValue.BigInt(n)         => n.toString
        case PrimitiveValue.BigDecimal(n)     => n.toString
        case PrimitiveValue.DayOfWeek(v)      => quoted(v.toString)
        case PrimitiveValue.Duration(v)       => quoted(v.toString)
        case PrimitiveValue.Instant(v)        => quoted(v.toString)
        case PrimitiveValue.LocalDate(v)      => quoted(v.toString)
        case PrimitiveValue.LocalDateTime(v)  => quoted(v.toString)
        case PrimitiveValue.LocalTime(v)      => quoted(v.toString)
        case PrimitiveValue.Month(v)          => quoted(v.toString)
        case PrimitiveValue.MonthDay(v)       => quoted(v.toString)
        case PrimitiveValue.OffsetDateTime(v) => quoted(v.toString)
        case PrimitiveValue.OffsetTime(v)     => quoted(v.toString)
        case PrimitiveValue.Period(v)         => quoted(v.toString)
        case PrimitiveValue.Year(v)           => quoted(v.toString)
        case PrimitiveValue.YearMonth(v)      => quoted(v.toString)
        case PrimitiveValue.ZoneId(v)         => quoted(v.toString)
        case PrimitiveValue.ZoneOffset(v)     => quoted(v.toString)
        case PrimitiveValue.ZonedDateTime(v)  => quoted(v.toString)
        case PrimitiveValue.Currency(v)       => quoted(v.getCurrencyCode)
        case PrimitiveValue.UUID(v)           => quoted(v.toString)
      }
    case other => unsupportedSqlLiteral(other)
  }

  def sqlArithmetic(left: String, right: String, op: DynamicSchemaExpr.ArithmeticOperator): String = op match {
    case DynamicSchemaExpr.ArithmeticOperator.Add      => s"($left + $right)"
    case DynamicSchemaExpr.ArithmeticOperator.Subtract => s"($left - $right)"
    case DynamicSchemaExpr.ArithmeticOperator.Multiply => s"($left * $right)"
    case DynamicSchemaExpr.ArithmeticOperator.Divide   => s"($left / $right)"
    case DynamicSchemaExpr.ArithmeticOperator.Modulo   => s"($left % $right)"
    case DynamicSchemaExpr.ArithmeticOperator.Pow      => s"POWER($left, $right)"
  }

  // ---------------------------------------------------------------------------
  // Core SQL interpreter — translates SchemaExpr to inline SQL
  // ---------------------------------------------------------------------------

  def toSql[A, B](expr: SchemaExpr[A, B]): String = toSqlDynamic(expr.dynamic)

  private def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {
    case DynamicSchemaExpr.Select(path)                => columnName(path)
    case DynamicSchemaExpr.Literal(value)              => sqlLiteralDV(value)
    case DynamicSchemaExpr.Relational(left, right, op) =>
      val sqlOp = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
    case DynamicSchemaExpr.Logical(left, right, op) =>
      val sqlOp = op match {
        case DynamicSchemaExpr.LogicalOperator.And => "AND"
        case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
    case DynamicSchemaExpr.Not(inner)                     => s"NOT (${toSqlDynamic(inner)})"
    case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
      sqlArithmetic(toSqlDynamic(left), toSqlDynamic(right), op)
    case DynamicSchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSqlDynamic(left)}, ${toSqlDynamic(right)})"
    case DynamicSchemaExpr.StringRegexMatch(regex, string) => s"(${toSqlDynamic(string)} LIKE ${toSqlDynamic(regex)})"
    case DynamicSchemaExpr.StringLength(string)            => s"LENGTH(${toSqlDynamic(string)})"
    case _                                                 => "?"
  }

  // ---------------------------------------------------------------------------
  // Parameterized query support
  // ---------------------------------------------------------------------------

  case class SqlQuery(sql: String, params: List[Any])

  def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = toParameterizedDynamic(expr.dynamic)

  private def toParameterizedDynamic(expr: DynamicSchemaExpr): SqlQuery = expr match {
    case DynamicSchemaExpr.Select(path)   => SqlQuery(columnName(path), Nil)
    case DynamicSchemaExpr.Literal(value) =>
      val param = value match {
        case DynamicValue.Primitive(pv) =>
          pv match {
            case PrimitiveValue.String(s)     => s
            case PrimitiveValue.Boolean(b)    => b
            case PrimitiveValue.Int(n)        => n
            case PrimitiveValue.Long(n)       => n
            case PrimitiveValue.Double(n)     => n
            case PrimitiveValue.Float(n)      => n
            case PrimitiveValue.Short(n)      => n
            case PrimitiveValue.Byte(n)       => n
            case PrimitiveValue.BigInt(n)     => n
            case PrimitiveValue.BigDecimal(n) => n
            case PrimitiveValue.Char(c)       => c
            case other                        => other.toString
          }
        case other => other.toString
      }
      SqlQuery("?", List(param))
    case DynamicSchemaExpr.Relational(left, right, op) =>
      val l     = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
      val sqlOp = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case DynamicSchemaExpr.Logical(left, right, op) =>
      val l     = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
      val sqlOp = op match {
        case DynamicSchemaExpr.LogicalOperator.And => "AND"
        case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case DynamicSchemaExpr.Not(inner) =>
      val i = toParameterizedDynamic(inner)
      SqlQuery(s"NOT (${i.sql})", i.params)
    case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
      val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
      SqlQuery(sqlArithmetic(l.sql, r.sql, op), l.params ++ r.params)
    case DynamicSchemaExpr.StringConcat(left, right) =>
      val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
      SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)
    case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
      val s = toParameterizedDynamic(string); val r = toParameterizedDynamic(regex)
      SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)
    case DynamicSchemaExpr.StringLength(string) =>
      val s = toParameterizedDynamic(string)
      SqlQuery(s"LENGTH(${s.sql})", s.params)
    case _ => SqlQuery("?", Nil)
  }

  // ---------------------------------------------------------------------------
  // SELECT statement builders
  // ---------------------------------------------------------------------------

  def select(table: String, predicate: SchemaExpr[?, Boolean]): String =
    s"SELECT * FROM $table WHERE ${toSql(predicate)}"

  def selectColumns(table: String, columns: List[String], predicate: SchemaExpr[?, Boolean]): String =
    s"SELECT ${columns.mkString(", ")} FROM $table WHERE ${toSql(predicate)}"

  def selectWithLimit(
    table: String,
    predicate: SchemaExpr[?, Boolean],
    orderBy: Option[String] = None,
    limit: Option[Int] = None
  ): String = {
    val base    = s"SELECT * FROM $table WHERE ${toSql(predicate)}"
    val ordered = orderBy.fold(base)(col => s"$base ORDER BY $col")
    limit.fold(ordered)(n => s"$ordered LIMIT $n")
  }
}
