package object querydslbuilder {

  import zio.blocks.schema._

  // ---------------------------------------------------------------------------
  // Extension methods
  // ---------------------------------------------------------------------------

  implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) extends AnyVal {
    def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
    def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] =
      Expr.Between(Expr.col(optic), low, high, schema)
    def isNull: Expr[S, Boolean]    = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean] = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) extends AnyVal {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  // Boolean combinators — work with both Expr and SchemaExpr (via bridge)
  implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
    def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
  }

  // Bridge: SchemaExpr && Expr (when SchemaExpr's own && doesn't match)
  implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
    def toExpr: Expr[S, Boolean]                      = Expr.fromSchemaExpr(self)
  }

  // ---------------------------------------------------------------------------
  // SQL rendering helpers
  // ---------------------------------------------------------------------------

  def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def columnName(optic: DynamicOptic): String =
    optic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def tableName[S](schema: Schema[S]): String =
    schema.reflect.modifiers.collectFirst {
      case Modifier.config(key, value) if key == "sql.table_name" => value
    }.getOrElse(pluralize(schema.reflect.typeId.name.toLowerCase))

  def pluralize(word: String): String =
    if (
      word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ||
      word.endsWith("ch") || word.endsWith("sh")
    ) word + "es"
    else if (
      word.endsWith("y") && !word.endsWith("ay") && !word.endsWith("ey") &&
      !word.endsWith("oy") && !word.endsWith("uy")
    ) word.dropRight(1) + "ies"
    else word + "s"

  def sqlLiteral[A](value: A, schema: Schema[A]): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case p: DynamicValue.Primitive =>
        p.value match {
          case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
          case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
          case _                         => value.toString
        }
      case _ => value.toString
    }
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
  // Single unified SQL interpreter
  // ---------------------------------------------------------------------------

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Column(path) => columnName(path)
    case Expr.Lit(value)   => sqlLiteralDV(value)

    case Expr.Relational(left, right, op) =>
      val sqlOp = op match {
        case RelOp.Equal              => "="
        case RelOp.NotEqual           => "<>"
        case RelOp.LessThan           => "<"
        case RelOp.LessThanOrEqual    => "<="
        case RelOp.GreaterThan        => ">"
        case RelOp.GreaterThanOrEqual => ">="
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

    case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)    => s"NOT (${exprToSql(e)})"

    case Expr.Arithmetic(left, right, op) =>
      val sqlOp = op match {
        case ArithOp.Add      => "+"
        case ArithOp.Subtract => "-"
        case ArithOp.Multiply => "*"
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

    case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
    case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
    case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"

    // SQL-specific
    case Expr.In(e, values, schema) =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v, schema)).mkString(", ")})"
    case Expr.Between(e, low, high, schema) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low, schema)} AND ${sqlLiteral(high, schema)})"
    case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
  }

  // ---------------------------------------------------------------------------
  // Statement factory functions
  // ---------------------------------------------------------------------------

  def select[S](table: Table[S]): SelectStmt[S]     = SelectStmt(table)
  def update[S](table: Table[S]): UpdateStmt[S]     = UpdateStmt(table)
  def insertInto[S](table: Table[S]): InsertStmt[S] = InsertStmt(table)
  def deleteFrom[S](table: Table[S]): DeleteStmt[S] = DeleteStmt(table)

  // ---------------------------------------------------------------------------
  // Statement renderers
  // ---------------------------------------------------------------------------

  def renderSelect[S](stmt: SelectStmt[S]): String = {
    val cols    = stmt.columnList.mkString(", ")
    val where   = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    val orderBy =
      if (stmt.orderByList.isEmpty) ""
      else {
        val orders = stmt.orderByList.map { case (col, order) =>
          val dir = order match { case SortOrder.Asc => "ASC"; case SortOrder.Desc => "DESC" }
          s"$col $dir"
        }.mkString(", ")
        s" ORDER BY $orders"
      }
    val limit = stmt.limitCount.map(n => s" LIMIT $n").getOrElse("")
    s"SELECT $cols FROM ${stmt.table.name}$where$orderBy$limit"
  }

  def renderUpdate[S](stmt: UpdateStmt[S]): String = {
    val sets  = stmt.assignments.map(a => s"${a.column} = ${a.value}").mkString(", ")
    val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    s"UPDATE ${stmt.table.name} SET $sets$where"
  }

  def renderInsert[S](stmt: InsertStmt[S]): String = {
    val cols = stmt.assignments.map(_.column).mkString(", ")
    val vals = stmt.assignments.map(_.value).mkString(", ")
    s"INSERT INTO ${stmt.table.name} ($cols) VALUES ($vals)"
  }

  def renderDelete[S](stmt: DeleteStmt[S]): String = {
    val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    s"DELETE FROM ${stmt.table.name}$where"
  }
}
