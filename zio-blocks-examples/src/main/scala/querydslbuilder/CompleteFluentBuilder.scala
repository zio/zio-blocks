package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Complete Example
 *
 * A complete, self-contained example demonstrating the fluent SQL builder DSL:
 * SELECT, UPDATE, INSERT, and DELETE with seamless SchemaExpr/Expr composition,
 * table references, and SQL rendering.
 *
 * Run with: sbt "examples/runMain querydslbuilder.CompleteFluentBuilder"
 */
object CompleteFluentBuilder extends App {

  // --- Table reference ---

  case class Table[S](name: String)

  // --- Domain ---

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
    case SchemaExpr.Optic(optic)      => columnName(optic)
    case SchemaExpr.Literal(value, _) => sqlLiteral(value)
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

  // --- Extended Expression ADT ---

  sealed trait Expr[S, A]

  object Expr {
    final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
    final case class Column[S, A](optic: Optic[S, A])      extends Expr[S, A]
    final case class Lit[S, A](value: A)                   extends Expr[S, A]

    final case class In[S, A](expr: Expr[S, A], values: List[A])      extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class IsNull[S, A](expr: Expr[S, A])                   extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String)  extends Expr[S, Boolean]

    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])  extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean])                          extends Expr[S, Boolean]

    def wrap[S, A](expr: SchemaExpr[S, A]): Expr[S, A] = Wrapped(expr)
    def col[S, A](optic: Optic[S, A]): Expr[S, A]      = Column(optic)
    def lit[S, A](value: A): Expr[S, A]                = Lit(value)
  }

  // --- Extension methods with bridge ---

  extension [S, A](optic: Optic[S, A]) {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
    def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  extension [S](optic: Optic[S, String]) {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  extension [S](self: Expr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.Wrapped(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.Wrapped(other))
    def unary_! : Expr[S, Boolean]                         = Expr.Not(self)
  }

  extension [S](self: SchemaExpr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.Wrapped(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.Wrapped(self), other)
  }

  extension [S, A](expr: SchemaExpr[S, A]) {
    def toExpr: Expr[S, A] = Expr.Wrapped(expr)
  }

  // --- Extended SQL interpreter ---

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Wrapped(schemaExpr)     => toSql(schemaExpr)
    case Expr.Column(optic)           => columnName(optic)
    case Expr.Lit(value)              => sqlLiteral(value)
    case Expr.In(e, values)           =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
    case Expr.Between(e, low, high)   =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
    case Expr.IsNull(e)               => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern)        => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.And(l, r)               => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)                => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                  => s"NOT (${exprToSql(e)})"
  }

  // --- Statement builders ---

  sealed trait SortOrder
  object SortOrder {
    case object Asc  extends SortOrder
    case object Desc extends SortOrder
  }

  case class Assignment(column: String, value: String)

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

  case class InsertStmt[S](
    table: Table[S],
    assignments: List[Assignment] = Nil
  ) {
    def set[A](optic: Optic[S, A], value: A): InsertStmt[S] =
      copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value)))
  }

  case class DeleteStmt[S](
    table: Table[S],
    whereExpr: Option[Expr[S, Boolean]] = None
  ) {
    def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(Expr.Wrapped(cond)))
  }

  def select[S](table: Table[S]): SelectStmt[S]       = SelectStmt(table)
  def update[S](table: Table[S]): UpdateStmt[S]       = UpdateStmt(table)
  def insertInto[S](table: Table[S]): InsertStmt[S]   = InsertStmt(table)
  def deleteFrom[S](table: Table[S]): DeleteStmt[S]   = DeleteStmt(table)

  // --- Renderers ---

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

  def renderUpdate[S](stmt: UpdateStmt[S]): String = {
    val sets = stmt.assignments.map(a => s"${a.column} = ${a.value}").mkString(", ")
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

  // --- Usage ---

  println("=== Complete Fluent SQL Builder ===")
  println()

  // 1. SELECT with mixed conditions, ordering, and limit
  val q = select(Product.table)
    .columns(Product.name, Product.price, Product.category)
    .where(
      Product.category.in("Electronics", "Books") &&
      Product.price.between(10.0, 500.0) &&
      (Product.rating >= 4).toExpr
    )
    .orderBy(Product.price, SortOrder.Desc)
    .limit(20)

  println("1. SELECT:")
  println(s"   ${renderSelect(q)}")
  println()

  // 2. UPDATE with seamless condition mixing (user's desired syntax)
  val u = update(Product.table)
    .set(Product.price, 9.99)
    .where(
      Product.category === "Books" &&
        Product.rating >= 4 &&
        Product.inStock === true &&
        Product.price.between(10.0, 30.0) &&
        Product.name.like("M%")
    )

  println("2. UPDATE:")
  println(s"   ${renderUpdate(u)}")
  println()

  // 3. INSERT
  val i = insertInto(Product.table)
    .set(Product.name, "Wireless Mouse")
    .set(Product.price, 29.99)
    .set(Product.category, "Electronics")
    .set(Product.inStock, true)
    .set(Product.rating, 4)

  println("3. INSERT:")
  println(s"   ${renderInsert(i)}")
  println()

  // 4. DELETE
  val d = deleteFrom(Product.table)
    .where(Product.inStock === false && Product.price.between(0.0, 1.0))

  println("4. DELETE:")
  println(s"   ${renderDelete(d)}")
}
