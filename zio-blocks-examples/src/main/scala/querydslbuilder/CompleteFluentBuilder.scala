package querydslbuilder.complete

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Complete Example
 *
 * A complete, self-contained example demonstrating the fluent SQL builder DSL:
 * SELECT, UPDATE, INSERT, and DELETE with seamless SchemaExpr/Expr composition,
 * table references, and SQL rendering. Uses the independent Expr ADT with
 * fromSchemaExpr translation — no Wrapped case or dual interpreter.
 *
 * Run with: sbt "examples/runMain querydslbuilder.complete.CompleteFluentBuilder"
 */
object CompleteFluentBuilder extends App {

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

  // --- Independent Expr ADT ---

  sealed trait Expr[S, A]

  object Expr {
    // Core nodes
    final case class Column[S, A](optic: Optic[S, A])       extends Expr[S, A]
    final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

    // Relational, logical, arithmetic, string
    final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp)     extends Expr[S, Boolean]
    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean])              extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])               extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean])                                       extends Expr[S, Boolean]
    final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp)   extends Expr[S, A]
    final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String])       extends Expr[S, String]
    final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
    final case class StringLength[S](string: Expr[S, String])                             extends Expr[S, Int]

    // SQL-specific extensions
    final case class In[S, A](expr: Expr[S, A], values: List[A])      extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class IsNull[S, A](expr: Expr[S, A])                   extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String)  extends Expr[S, Boolean]

    // Factory methods
    def col[S, A](optic: Optic[S, A]): Expr[S, A]                   = Column(optic)
    def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

    // One-way translation from SchemaExpr
    def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
      val result = se match {
        case SchemaExpr.Optic(optic)         => Column(optic)
        case l: SchemaExpr.Literal[_, _]     => Lit(l.value, l.schema)
        case SchemaExpr.Relational(l, r, op) =>
          val relOp = op match {
            case SchemaExpr.RelationalOperator.Equal              => RelOp.Equal
            case SchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
            case SchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
            case SchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
            case SchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
            case SchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
          }
          Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp)
        case SchemaExpr.Logical(l, r, op) =>
          op match {
            case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l), fromSchemaExpr(r))
            case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l), fromSchemaExpr(r))
          }
        case SchemaExpr.Not(inner)              => Not(fromSchemaExpr(inner))
        case SchemaExpr.Arithmetic(l, r, op, _) =>
          val arithOp = op match {
            case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
            case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
            case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
          }
          Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)
        case SchemaExpr.StringConcat(l, r)              => StringConcat(fromSchemaExpr(l), fromSchemaExpr(r))
        case SchemaExpr.StringRegexMatch(regex, string) =>
          StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))
        case SchemaExpr.StringLength(string) => StringLength(fromSchemaExpr(string))
      }
      result.asInstanceOf[Expr[S, A]]
    }
  }

  sealed trait RelOp
  object RelOp {
    case object Equal              extends RelOp
    case object NotEqual           extends RelOp
    case object LessThan           extends RelOp
    case object LessThanOrEqual    extends RelOp
    case object GreaterThan        extends RelOp
    case object GreaterThanOrEqual extends RelOp
  }

  sealed trait ArithOp
  object ArithOp {
    case object Add      extends ArithOp
    case object Subtract extends ArithOp
    case object Multiply extends ArithOp
  }

  // --- Table and statement types ---

  case class Table[S](name: String)

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
    def columns(optics: Optic[S, _]*): SelectStmt[S] =
      copy(columnList = optics.map(columnName).toList)
    def where(cond: Expr[S, Boolean]): SelectStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): SelectStmt[S] =
      copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
    def orderBy(optic: Optic[S, _], order: SortOrder = SortOrder.Asc): SelectStmt[S] =
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
      copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteralUntyped(value)))
    def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
      copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
  }

  case class InsertStmt[S](
    table: Table[S],
    assignments: List[Assignment] = Nil
  ) {
    def set[A](optic: Optic[S, A], value: A): InsertStmt[S] =
      copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteralUntyped(value)))
  }

  case class DeleteStmt[S](
    table: Table[S],
    whereExpr: Option[Expr[S, Boolean]] = None
  ) {
    def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(cond))
    def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
      copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
  }

  // --- Extension methods with bridge ---
  // (Self-contained — the package object defines similar implicits for the
  //  package-level Expr, but this object uses its own local Expr ADT.)

  implicit final class OpticOps[S, A](private val optic: Optic[S, A]) extends AnyVal {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
    def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  implicit final class StringOpticOps[S](private val optic: Optic[S, String]) extends AnyVal {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  implicit final class ExprBoolOps[S](private val self: Expr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]       = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
    def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
  }

  implicit final class SchemaExprBridge[S](private val self: SchemaExpr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
    def toExpr: Expr[S, Boolean]                      = Expr.fromSchemaExpr(self)
  }

  // --- SQL rendering ---

  def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteralUntyped(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

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

  // Single unified interpreter
  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Column(optic)               => columnName(optic)
    case Expr.Lit(value, schema)          => sqlLiteral(value, schema)
    case Expr.Relational(left, right, op) =>
      val sqlOp = op match {
        case RelOp.Equal       => "="; case RelOp.NotEqual           => "<>"
        case RelOp.LessThan    => "<"; case RelOp.LessThanOrEqual    => "<="
        case RelOp.GreaterThan => ">"; case RelOp.GreaterThanOrEqual => ">="
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
    case Expr.And(l, r)                   => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)                    => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                      => s"NOT (${exprToSql(e)})"
    case Expr.Arithmetic(left, right, op) =>
      val sqlOp = op match {
        case ArithOp.Add => "+"; case ArithOp.Subtract => "-"; case ArithOp.Multiply => "*"
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
    case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
    case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
    case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"
    case Expr.In(e, values)              =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteralUntyped(v)).mkString(", ")})"
    case Expr.Between(e, low, high) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteralUntyped(low)} AND ${sqlLiteralUntyped(high)})"
    case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
  }

  // --- Statement helpers ---

  def select[S](table: Table[S]): SelectStmt[S]     = SelectStmt(table)
  def update[S](table: Table[S]): UpdateStmt[S]     = UpdateStmt(table)
  def insertInto[S](table: Table[S]): InsertStmt[S] = InsertStmt(table)
  def deleteFrom[S](table: Table[S]): DeleteStmt[S] = DeleteStmt(table)

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

  // 2. UPDATE with seamless condition mixing
  val u = update(Product.table)
    .set(Product.price, 9.99)
    .where(
      Product.price.between(10.0, 30.0) &&
        Product.name.like("M%") &&
        (Product.category === "Books") &&
        (Product.rating >= 4) &&
        (Product.inStock === true)
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
    .where(Product.price.between(0.0, 1.0) && (Product.inStock === false))

  println("4. DELETE:")
  println(s"   ${renderDelete(d)}")
}
