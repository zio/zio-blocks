package querydslsql

import zio.blocks.schema._

/**
 * Query DSL SQL Generation — Part 2: Complete Example
 *
 * A complete, self-contained SQL generator that translates SchemaExpr query
 * expressions into both inline SQL and parameterized queries. Combines all
 * techniques: column name extraction, literal formatting, the full interpreter,
 * SELECT statement builders, and parameterized query generation.
 *
 * Run with: sbt "examples/runMain querydslsql.CompleteSqlGenerator"
 */
object CompleteSqlGenerator extends App {

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

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
  }

  // --- SQL Interpreter ---

  def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
    case SchemaExpr.Optic(optic)                    => columnName(optic)
    case SchemaExpr.Literal(value, _)               => sqlLiteral(value)
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

  // --- Parameterized queries ---

  case class SqlQuery(sql: String, params: List[Any])

  def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = expr match {
    case SchemaExpr.Optic(optic)      => SqlQuery(columnName(optic), Nil)
    case SchemaExpr.Literal(value, _) => SqlQuery("?", List(value))
    case SchemaExpr.Relational(left, right, op) =>
      val l = toParameterized(left); val r = toParameterized(right)
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
      val l = toParameterized(left); val r = toParameterized(right)
      val sqlOp = op match {
        case SchemaExpr.LogicalOperator.And => "AND"
        case SchemaExpr.LogicalOperator.Or  => "OR"
      }
      SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
    case SchemaExpr.Not(inner) =>
      val i = toParameterized(inner)
      SqlQuery(s"NOT (${i.sql})", i.params)
    case SchemaExpr.Arithmetic(left, right, op, _) =>
      val l = toParameterized(left); val r = toParameterized(right)
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

  // --- Complete SELECT builder ---

  def select(table: String, predicate: SchemaExpr[?, Boolean]): String =
    s"SELECT * FROM $table WHERE ${toSql(predicate)}"

  // --- Usage ---

  val query =
    (Product.category === "Electronics") &&
    (Product.inStock === true) &&
    (Product.price < 500.0) &&
    (Product.rating >= 4)

  println("=== Complete SQL Generator Example ===")
  println()

  // Inline SQL for debugging
  println("Inline SQL:")
  println(s"  ${select("products", query)}")
  println()

  // Parameterized SQL for execution
  val pq = toParameterized(query)
  println("Parameterized SQL:")
  println(s"  SQL:    ${pq.sql}")
  println(s"  Params: ${pq.params}")
  println()

  // String operations in SQL
  println("String operations:")
  println(s"  LIKE:   ${toSql(Product.name.matches("L%"))}")
  println(s"  CONCAT: ${toSql(Product.name.concat(" [SALE]"))}")
  println(s"  LENGTH: ${toSql(Product.name.length)}")
  println()

  // Arithmetic in SQL
  println("Arithmetic:")
  println(s"  Discount: ${toSql(Product.price * 0.9)}")
  println(s"  Tax:      ${toSql(Product.price * 1.08)}")
}
