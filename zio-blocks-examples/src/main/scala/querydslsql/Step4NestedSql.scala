package querydslsql

import zio.blocks.schema._

/**
 * Query DSL SQL Generation — Part 2, Step 4: Nested Structures
 *
 * Demonstrates handling nested domain types with multi-segment optic paths,
 * producing table-qualified column names for SQL JOIN queries.
 *
 * This step uses its own domain types (Seller, Address) and overrides
 * columnName to produce table-qualified names. SQL literal formatting and
 * the toSql interpreter are defined in the package object (package.scala).
 *
 * Run with: sbt "examples/runMain querydslsql.Step4NestedSql"
 */
object Step4NestedSql extends App {

  // --- Nested Domain Types ---

  case class Address(city: String, country: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Seller(name: String, address: Address, rating: Double)
  object Seller extends CompanionOptics[Seller] {
    implicit val schema: Schema[Seller] = Schema.derived

    val name: Lens[Seller, String]    = optic(_.name)
    val rating: Lens[Seller, Double]  = optic(_.rating)
    val city: Lens[Seller, String]    = optic(_.address.city)
    val country: Lens[Seller, String] = optic(_.address.country)
  }

  // --- Table-Qualified Column Names ---

  def qualifiedColumnName(optic: zio.blocks.schema.Optic[?, ?]): String = {
    val fields = optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field =>
      f.name
    }
    if (fields.length <= 1) fields.mkString
    else s"${fields.init.mkString("_")}.${fields.last}"
  }

  println("=== Table-Qualified Column Names ===")
  println()
  println(s"Seller.name    -> ${qualifiedColumnName(Seller.name)}")
  println(s"Seller.city    -> ${qualifiedColumnName(Seller.city)}")
  println(s"Seller.country -> ${qualifiedColumnName(Seller.country)}")
  println()

  // --- SQL Generation with Qualified Names ---
  // Override columnName locally to produce table-qualified names

  def toSqlQualified[A, B](expr: SchemaExpr[A, B]): String = expr match {
    case SchemaExpr.Optic(optic)                => qualifiedColumnName(optic)
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
      s"(${toSqlQualified(left)} $sqlOp ${toSqlQualified(right)})"
    case SchemaExpr.Logical(left, right, op) =>
      val sqlOp = op match {
        case SchemaExpr.LogicalOperator.And => "AND"
        case SchemaExpr.LogicalOperator.Or  => "OR"
      }
      s"(${toSqlQualified(left)} $sqlOp ${toSqlQualified(right)})"
    case SchemaExpr.Not(inner)                     => s"NOT (${toSqlQualified(inner)})"
    case SchemaExpr.Arithmetic(left, right, op, _) =>
      val sqlOp = op match {
        case SchemaExpr.ArithmeticOperator.Add      => "+"
        case SchemaExpr.ArithmeticOperator.Subtract => "-"
        case SchemaExpr.ArithmeticOperator.Multiply => "*"
      }
      s"(${toSqlQualified(left)} $sqlOp ${toSqlQualified(right)})"
    case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSqlQualified(left)}, ${toSqlQualified(right)})"
    case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSqlQualified(string)} LIKE ${toSqlQualified(regex)})"
    case SchemaExpr.StringLength(string)            => s"LENGTH(${toSqlQualified(string)})"
  }

  def selectQualified(table: String, predicate: SchemaExpr[?, Boolean]): String =
    s"SELECT * FROM $table WHERE ${toSqlQualified(predicate)}"

  // --- Example: Nested Query to SQL ---

  val berlinSellers =
    (Seller.city === "Berlin") && (Seller.rating >= 4.0)

  println("=== Nested Query to SQL ===")
  println()
  println(s"WHERE clause: ${toSqlQualified(berlinSellers)}")
  println()
  println("Full JOIN query (conceptual):")
  println(selectQualified("sellers", berlinSellers))
  println()
}
