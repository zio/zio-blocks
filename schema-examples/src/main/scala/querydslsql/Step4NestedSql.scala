package querydslsql

import zio.blocks.schema._

/**
 * Query DSL SQL Generation — Part 2, Step 4: Nested Structures
 *
 * Demonstrates handling nested domain types with multi-segment optic paths,
 * producing table-qualified column names for SQL JOIN queries.
 *
 * This step uses its own domain types (Seller, Address) and overrides
 * columnName to produce table-qualified names. SQL literal formatting and the
 * toSql interpreter are defined in the package object (package.scala).
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

  def qualifiedColumnName(optic: zio.blocks.schema.Optic[?, ?]): String =
    qualifiedColumnNameDynamic(optic.toDynamic)

  def qualifiedColumnNameDynamic(optic: DynamicOptic): String = {
    val fields = optic.nodes.collect { case f: DynamicOptic.Node.Field =>
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

  def toSqlQualified[A, B](expr: SchemaExpr[A, B]): String = toSqlQualifiedDynamic(expr.dynamic)

  private def toSqlQualifiedDynamic(expr: DynamicSchemaExpr): String = expr match {
    case DynamicSchemaExpr.Select(path)                => qualifiedColumnNameDynamic(path)
    case DynamicSchemaExpr.Literal(value)              => sqlLiteralDV(value)
    case DynamicSchemaExpr.Relational(left, right, op) =>
      val sqlOp = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => "="
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
        case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      s"(${toSqlQualifiedDynamic(left)} $sqlOp ${toSqlQualifiedDynamic(right)})"
    case DynamicSchemaExpr.Logical(left, right, op) =>
      val sqlOp = op match {
        case DynamicSchemaExpr.LogicalOperator.And => "AND"
        case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
      }
      s"(${toSqlQualifiedDynamic(left)} $sqlOp ${toSqlQualifiedDynamic(right)})"
    case DynamicSchemaExpr.Not(inner)                     => s"NOT (${toSqlQualifiedDynamic(inner)})"
    case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
      val sqlOp = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
        case _                                             => "?"
      }
      s"(${toSqlQualifiedDynamic(left)} $sqlOp ${toSqlQualifiedDynamic(right)})"
    case DynamicSchemaExpr.StringConcat(left, right) =>
      s"CONCAT(${toSqlQualifiedDynamic(left)}, ${toSqlQualifiedDynamic(right)})"
    case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
      s"(${toSqlQualifiedDynamic(string)} LIKE ${toSqlQualifiedDynamic(regex)})"
    case DynamicSchemaExpr.StringLength(string) => s"LENGTH(${toSqlQualifiedDynamic(string)})"
    case _                                      => "?"
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
