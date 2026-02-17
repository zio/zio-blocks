package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 2: The SELECT Builder
 *
 * Demonstrates the fluent SELECT builder with .columns(), .where(),
 * .orderBy(), and .limit() methods.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step2SelectBuilder"
 */
object Step2SelectBuilder extends App {

  // --- Domain ---

  case class Table[S](name: String)

  case class Product(
    name: String,
    price: Double,
    category: String,
    inStock: Boolean,
    rating: Int
  )

  object Product extends CompanionOptics[Product] {
    implicit val schema: Schema[Product] = Schema.derived

    val table: Table[Product] = Table("products")

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
  }

  // --- SQL helpers ---

  def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

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
    case SchemaExpr.Not(inner)                      => s"NOT (${toSql(inner)})"
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

  // --- Expr ADT + extensions ---

  sealed trait Expr[S, A]
  object Expr {
    final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
    final case class Column[S, A](optic: Optic[S, A])      extends Expr[S, A]
    final case class Lit[S, A](value: A)                   extends Expr[S, A]
    final case class In[S, A](expr: Expr[S, A], values: List[A])      extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String)  extends Expr[S, Boolean]
    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])  extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean])                          extends Expr[S, Boolean]
    def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  }

  extension [S, A](optic: Optic[S, A]) {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
  }

  extension [S](optic: Optic[S, String]) {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  extension [S](self: Expr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.Wrapped(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.Wrapped(other))
  }

  extension [S](self: SchemaExpr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.Wrapped(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.Wrapped(self), other)
  }

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Wrapped(schemaExpr)   => toSql(schemaExpr)
    case Expr.Column(optic)         => columnName(optic)
    case Expr.Lit(value)            => sqlLiteral(value)
    case Expr.In(e, values)         => s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
    case Expr.Between(e, low, high) => s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
    case Expr.Like(e, pattern)      => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.And(l, r)             => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)              => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                => s"NOT (${exprToSql(e)})"
  }

  // --- SELECT builder ---

  sealed trait SortOrder
  object SortOrder {
    case object Asc  extends SortOrder
    case object Desc extends SortOrder
  }

  case class SelectStmt[S](
    table: Table[S],
    columnList: List[String] = List("*"),
    whereExpr: Option[Expr[S, Boolean]] = None,
    orderByList: List[(String, SortOrder)] = Nil,
    limitCount: Option[Int] = None
  ) {
    def columns(optics: Optic[S, ?]*): SelectStmt[S] =
      copy(columnList = optics.map(columnName).toList)
    def where(cond: Expr[S, Boolean]): SelectStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): SelectStmt[S] =
      copy(whereExpr = Some(Expr.Wrapped(cond)))
    def orderBy(optic: Optic[S, ?], order: SortOrder = SortOrder.Asc): SelectStmt[S] =
      copy(orderByList = orderByList :+ (columnName(optic), order))
    def limit(n: Int): SelectStmt[S] =
      copy(limitCount = Some(n))
  }

  def select[S](table: Table[S]): SelectStmt[S] = SelectStmt(table)

  def renderSelect[S](stmt: SelectStmt[S]): String = {
    val cols = stmt.columnList.mkString(", ")
    val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    val orderBy = if (stmt.orderByList.isEmpty) "" else {
      val orders = stmt.orderByList.map { (col, order) =>
        val dir = order match { case SortOrder.Asc => "ASC"; case SortOrder.Desc => "DESC" }
        s"$col $dir"
      }.mkString(", ")
      s" ORDER BY $orders"
    }
    val limit = stmt.limitCount.map(n => s" LIMIT $n").getOrElse("")
    s"SELECT $cols FROM ${stmt.table.name}$where$orderBy$limit"
  }

  // --- Output ---

  println("=== Step 2: SELECT Builder ===")
  println()

  val basicSelect = select(Product.table)
    .columns(Product.name, Product.price)
    .where(Product.inStock === true)

  println("1. Basic SELECT:")
  println(s"   ${renderSelect(basicSelect)}")
  println()

  val advancedSelect = select(Product.table)
    .columns(Product.name, Product.price, Product.category)
    .where(
      Product.category === "Electronics" &&
      Product.rating >= 4 &&
      Product.price.between(10.0, 500.0)
    )
    .orderBy(Product.price, SortOrder.Desc)
    .limit(10)

  println("2. Advanced SELECT with ordering and limit:")
  println(s"   ${renderSelect(advancedSelect)}")
  println()

  val selectAll = select(Product.table)
    .where(Product.name.like("Wire%"))

  println("3. SELECT * with LIKE condition:")
  println(s"   ${renderSelect(selectAll)}")
}
