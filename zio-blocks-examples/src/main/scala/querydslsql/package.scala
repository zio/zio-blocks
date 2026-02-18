package object querydslsql {

  import zio.blocks.schema._

  // ---------------------------------------------------------------------------
  // SQL rendering helpers
  // ---------------------------------------------------------------------------

  def columnName(optic: Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  // ---------------------------------------------------------------------------
  // Core SQL interpreter — translates SchemaExpr to inline SQL
  // ---------------------------------------------------------------------------

  def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
    case SchemaExpr.Optic(optic)                => columnName(optic)
    case SchemaExpr.Literal(value, _)           => sqlLiteral(value)
    case SchemaExpr.Relational(left, right, op) =>
      val sqlOp = op match {
        case SchemaExpr.RelationalOperator.Equal              => "="
        case SchemaExpr.RelationalOperator.NotEqual           => "<>"
        case SchemaExpr.RelationalOperator.LessThan           => "<"
        case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case SchemaExpr.RelationalOperator.GreaterThan        => ">"
        case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.Logical(left, right, op) =>
      val sqlOp = op match {
        case SchemaExpr.LogicalOperator.And => "AND"
        case SchemaExpr.LogicalOperator.Or  => "OR"
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.Not(inner)                     => s"NOT (${toSql(inner)})"
    case SchemaExpr.Arithmetic(left, right, op, _) =>
      val sqlOp = op match {
        case SchemaExpr.ArithmeticOperator.Add      => "+"
        case SchemaExpr.ArithmeticOperator.Subtract => "-"
        case SchemaExpr.ArithmeticOperator.Multiply => "*"
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSql(left)}, ${toSql(right)})"
    case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSql(string)} LIKE ${toSql(regex)})"
    case SchemaExpr.StringLength(string)            => s"LENGTH(${toSql(string)})"
  }

  // ---------------------------------------------------------------------------
  // Parameterized query support
  // ---------------------------------------------------------------------------

  case class SqlQuery(sql: String, params: List[Any])

  def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = expr match {
    case SchemaExpr.Optic(optic)                => SqlQuery(columnName(optic), Nil)
    case SchemaExpr.Literal(value, _)           => SqlQuery("?", List(value))
    case SchemaExpr.Relational(left, right, op) =>
      val l     = toParameterized(left); val r = toParameterized(right)
      val sqlOp = op match {
        case SchemaExpr.RelationalOperator.Equal              => "="
        case SchemaExpr.RelationalOperator.NotEqual           => "<>"
        case SchemaExpr.RelationalOperator.LessThan           => "<"
        case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case SchemaExpr.RelationalOperator.GreaterThan        => ">"
        case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case SchemaExpr.Logical(left, right, op) =>
      val l     = toParameterized(left); val r = toParameterized(right)
      val sqlOp = op match {
        case SchemaExpr.LogicalOperator.And => "AND"
        case SchemaExpr.LogicalOperator.Or  => "OR"
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case SchemaExpr.Not(inner) =>
      val i = toParameterized(inner)
      SqlQuery(s"NOT (${i.sql})", i.params)
    case SchemaExpr.Arithmetic(left, right, op, _) =>
      val l     = toParameterized(left); val r = toParameterized(right)
      val sqlOp = op match {
        case SchemaExpr.ArithmeticOperator.Add      => "+"
        case SchemaExpr.ArithmeticOperator.Subtract => "-"
        case SchemaExpr.ArithmeticOperator.Multiply => "*"
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case SchemaExpr.StringConcat(left, right) =>
      val l = toParameterized(left); val r = toParameterized(right)
      SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)
    case SchemaExpr.StringRegexMatch(regex, string) =>
      val s = toParameterized(string); val r = toParameterized(regex)
      SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)
    case SchemaExpr.StringLength(string) =>
      val s = toParameterized(string)
      SqlQuery(s"LENGTH(${s.sql})", s.params)
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
