package object querydslsql {

  import zio.blocks.schema._

  // ---------------------------------------------------------------------------
  // SQL rendering helpers
  // ---------------------------------------------------------------------------

  def columnName(optic: Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def columnName(optic: DynamicOptic): String =
    optic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  def sqlLiteralDV(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.String(s)  => s"'${s.replace("'", "''")}'"
        case PrimitiveValue.Boolean(b) => if (b) "TRUE" else "FALSE"
        case PrimitiveValue.Int(n)     => n.toString
        case PrimitiveValue.Long(n)    => n.toString
        case PrimitiveValue.Double(n)  => n.toString
        case PrimitiveValue.Float(n)   => n.toString
        case PrimitiveValue.Short(n)   => n.toString
        case PrimitiveValue.Byte(n)    => n.toString
        case other                     => other.toString
      }
    case other => other.toString
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
      val sqlOp = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
        case _                                             => "?"
      }
      s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
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
      val l     = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
      val sqlOp = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
        case _                                             => "?"
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
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
