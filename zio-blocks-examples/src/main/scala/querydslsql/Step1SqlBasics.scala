package querydslsql

import zio.blocks.schema._

/**
 * Query DSL SQL Generation — Part 2, Step 1: SQL Basics
 *
 * Demonstrates extracting column names from optic paths using DynamicOptic,
 * formatting SQL literals, building the core SQL interpreter via pattern
 * matching on SchemaExpr, and generating basic SQL WHERE clauses.
 *
 * Run with: sbt "examples/runMain querydslsql.Step1SqlBasics"
 */
object Step1SqlBasics extends App {

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

  // --- Column Name Extraction ---

  def columnName(optic: Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  println("=== Column Name Extraction ===")
  println()
  println(s"Product.price    -> ${columnName(Product.price)}")
  println(s"Product.name     -> ${columnName(Product.name)}")
  println(s"Product.category -> ${columnName(Product.category)}")
  println()

  // --- SQL Literal Formatting ---

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  // --- Core SQL Interpreter ---

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

  // --- Basic SQL Generation ---

  val isElectronics  = Product.category === "Electronics"
  val expensiveItems = Product.price > 100.0
  val highRated      = Product.rating >= 4

  println("=== Basic SQL Generation ===")
  println()
  println(s"isElectronics  -> ${toSql(isElectronics)}")
  println(s"expensiveItems -> ${toSql(expensiveItems)}")
  println(s"highRated      -> ${toSql(highRated)}")
}
