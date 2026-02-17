package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 3: UPDATE, INSERT, and DELETE Builders
 *
 * Demonstrates the fluent UPDATE builder with .set() and .where(),
 * the INSERT builder with .set(), and the DELETE builder with .where().
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step3UpdateInsertDelete"
 */
object Step3UpdateInsertDelete extends App {

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

  // --- Statement builders ---

  case class Assignment(column: String, value: String)

  // UPDATE
  case class UpdateStmt[S](
    table: Table[S],
    assignments: List[Assignment] = Nil,
    whereExpr: Option[Expr[S, Boolean]] = None
  ) {
    def set[A](optic: Optic[S, A], value: A): UpdateStmt[S] =
      copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value)))
    def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
      copy(whereExpr = Some(Expr.Wrapped(cond)))
  }

  def update[S](table: Table[S]): UpdateStmt[S] = UpdateStmt(table)

  def renderUpdate[S](stmt: UpdateStmt[S]): String = {
    val sets = stmt.assignments.map(a => s"${a.column} = ${a.value}").mkString(", ")
    val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    s"UPDATE ${stmt.table.name} SET $sets$where"
  }

  // INSERT
  case class InsertStmt[S](
    table: Table[S],
    assignments: List[Assignment] = Nil
  ) {
    def set[A](optic: Optic[S, A], value: A): InsertStmt[S] =
      copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value)))
  }

  def insertInto[S](table: Table[S]): InsertStmt[S] = InsertStmt(table)

  def renderInsert[S](stmt: InsertStmt[S]): String = {
    val cols = stmt.assignments.map(_.column).mkString(", ")
    val vals = stmt.assignments.map(_.value).mkString(", ")
    s"INSERT INTO ${stmt.table.name} ($cols) VALUES ($vals)"
  }

  // DELETE
  case class DeleteStmt[S](
    table: Table[S],
    whereExpr: Option[Expr[S, Boolean]] = None
  ) {
    def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(Expr.Wrapped(cond)))
  }

  def deleteFrom[S](table: Table[S]): DeleteStmt[S] = DeleteStmt(table)

  def renderDelete[S](stmt: DeleteStmt[S]): String = {
    val where = stmt.whereExpr.map(c => s" WHERE ${exprToSql(c)}").getOrElse("")
    s"DELETE FROM ${stmt.table.name}$where"
  }

  // --- Output ---

  println("=== Step 3: UPDATE, INSERT, DELETE ===")
  println()

  // UPDATE matching user's desired syntax
  val basicUpdate =
    update(Product.table)
      .set(Product.price, 9.99)
      .where(
        Product.category === "Books" &&
          Product.rating >= 4 &&
          Product.inStock === true &&
          Product.price.between(10.0, 30.0) &&
          Product.name.like("M%")
      )

  println("1. UPDATE with mixed conditions:")
  println(s"   ${renderUpdate(basicUpdate)}")
  println()

  // Multi-set UPDATE
  val multiUpdate =
    update(Product.table)
      .set(Product.price, 19.99)
      .set(Product.inStock, false)
      .where(Product.category === "Clearance")

  println("2. UPDATE with multiple SET clauses:")
  println(s"   ${renderUpdate(multiUpdate)}")
  println()

  // INSERT
  val ins = insertInto(Product.table)
    .set(Product.name, "Wireless Mouse")
    .set(Product.price, 29.99)
    .set(Product.category, "Electronics")
    .set(Product.inStock, true)
    .set(Product.rating, 4)

  println("3. INSERT:")
  println(s"   ${renderInsert(ins)}")
  println()

  // DELETE
  val del = deleteFrom(Product.table)
    .where(
      Product.inStock === false &&
      Product.price.between(0.0, 1.0)
    )

  println("4. DELETE with mixed conditions:")
  println(s"   ${renderDelete(del)}")
}
