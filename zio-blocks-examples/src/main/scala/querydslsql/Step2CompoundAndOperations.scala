package querydslsql

import zio.blocks.schema._

/**
 * Query DSL SQL Generation — Part 2, Step 2: Compound Queries and Operations
 *
 * Demonstrates translating compound boolean queries (AND, OR, NOT),
 * arithmetic expressions, and string operations to SQL.
 *
 * Run with: sbt "examples/runMain querydslsql.Step2CompoundAndOperations"
 */
object Step2CompoundAndOperations extends App {

  // --- Domain Types ---

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

  // --- SQL Helpers (same as Step 1) ---

  def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
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
    case SchemaExpr.Arithmetic(left, right, op, _)  =>
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

  // --- Compound Queries ---

  val affordableElectronics =
    (Product.category === "Electronics") && (Product.price < 500.0)

  val goodDeal =
    (Product.price < 10.0) || (Product.rating >= 5)

  val outOfStock = !Product.inStock

  val complexQuery =
    ((Product.category === "Electronics") && (Product.price < 500.0)) ||
    ((Product.category === "Office") && (Product.rating >= 4))

  println("=== Compound Queries ===")
  println()
  println(s"affordableElectronics -> ${toSql(affordableElectronics)}")
  println(s"goodDeal              -> ${toSql(goodDeal)}")
  println(s"outOfStock            -> ${toSql(outOfStock)}")
  println(s"complexQuery          -> ${toSql(complexQuery)}")
  println()

  // --- Arithmetic in SQL ---

  val discountedPrice = Product.price * 0.9
  val priceWithTax = Product.price * 1.08

  println("=== Arithmetic in SQL ===")
  println()
  println(s"discountedPrice -> ${toSql(discountedPrice)}")
  println(s"priceWithTax    -> ${toSql(priceWithTax)}")
  println()

  // --- String Operations in SQL ---

  val startsWithL = Product.name.matches("L%")
  val labeledName = Product.name.concat(" [SALE]")
  val nameLength = Product.name.length

  println("=== String Operations in SQL ===")
  println()
  println(s"startsWithL -> ${toSql(startsWithL)}")
  println(s"labeledName -> ${toSql(labeledName)}")
  println(s"nameLength  -> ${toSql(nameLength)}")
}
