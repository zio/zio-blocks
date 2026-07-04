package querydslextended

import zio.blocks.schema._

// Reproducer: DynamicSchemaExpr.Literal loses the Schema, causing enum literals
// to render as their DynamicValue eJSON representation rather than their case name.
//
// The querydslextended SQL interpreter has two literal rendering paths:
//
//   sqlLiteral(value: A, schema: Schema[A])  — used by .in(), .between()
//     Falls back to value.toString for non-primitives.
//     For a case object this produces the case name — accidentally correct-looking,
//     but still wrong SQL (unquoted identifier instead of a string literal).
//
//   sqlLiteralDV(dv: DynamicValue)  — used by Expr.lit() and === (via fromSchemaExpr)
//     Falls back to dv.toString for non-primitives.
//     DynamicValue.Variant.toString returns eJSON: {} @ {tag: "CaseName"}
//     This produces clearly broken SQL.
//
// Root cause: Expr.lit and fromSchemaExpr both discard the typed value immediately,
// keeping only DynamicValue.  Without the Schema, sqlLiteralDV cannot detect
// isEnumeration and cannot recover the case name.
object EnumLiteralSqlReproducer extends App {

  sealed trait Category
  case object Electronics extends Category
  case object Books       extends Category
  implicit val categorySchema: Schema[Category] = Schema.derived[Category]

  case class Product(name: String, price: Double, category: Category)
  object Product extends CompanionOptics[Product] {
    implicit val schema: Schema[Product] = Schema.derived
    val name:     Lens[Product, String]   = optic(_.name)
    val price:    Lens[Product, Double]   = optic(_.price)
    val category: Lens[Product, Category] = optic(_.category)
  }

  def check(label: String, sql: String, expected: String): Unit = {
    val ok = sql == expected
    println(s"  [${if (ok) "OK  " else "FAIL"}] $label")
    if (!ok) {
      println(s"         expected : $expected")
      println(s"         actual   : $sql")
    }
  }

  // ── 1. .in() — sqlLiteral(value, schema) path ────────────────────────────
  // Uses the typed value for the fallback: value.toString = "Electronics".
  // Looks plausible but is wrong SQL: unquoted identifier, not a string literal.
  println("1. .in() on enum (sqlLiteral path — value.toString fallback)")
  val inQuery = Product.category.in(Electronics, Books)
  val inSql   = exprToSql(inQuery)
  println(s"     SQL : $inSql")
  check(
    ".in() produces quoted string literals",
    inSql,
    "category IN ('Electronics', 'Books')"  // correct SQL
  )
  // Actual: category IN (Electronics, Books)  — unquoted, wrong SQL
  println()

  // ── 2. === via the SchemaExpr bridge — sqlLiteralDV(DynamicValue) path ───
  // SchemaExpr.literal lowers the typed value to DynamicValue, discarding the
  // Schema.  fromSchemaExpr → fromDynamic lifts it to Expr.Lit(DynamicValue).
  // sqlLiteralDV falls through to dv.toString — DynamicValue eJSON format.
  println("2. === on enum (fromSchemaExpr / sqlLiteralDV path — dv.toString fallback)")
  val eqSchemaExpr: SchemaExpr[Product, Boolean] = Product.category === Electronics
  val eqExpr: Expr[Product, Boolean]             = Product.price.between(10.0, 500.0) && eqSchemaExpr
  val eqSql                                      = exprToSql(eqExpr)
  println(s"     SQL : $eqSql")
  check(
    "=== produces correct SQL for enum literal",
    eqSql,
    "((price BETWEEN 10.0 AND 500.0) AND (category = 'Electronics'))"  // correct SQL
  )
  // Actual: ((price BETWEEN 10.0 AND 500.0) AND (category = {} @ {tag: "Electronics"}))
  println()

  // ── 3. Expr.lit() directly — same sqlLiteralDV path ──────────────────────
  // Expr.lit converts to DynamicValue immediately, matching the === path.
  println("3. Expr.lit() on enum (sqlLiteralDV path — same as ===)")
  val litSql = exprToSql(Expr.lit[Product, Category](Electronics))
  println(s"     SQL : $litSql")
  check(
    "Expr.lit produces correct SQL for enum literal",
    litSql,
    "'Electronics'"  // correct SQL
  )
  // Actual: {} @ {tag: "Electronics"}
  println()

  // ── Summary ───────────────────────────────────────────────────────────────
  println("The .in() and .between() extension methods keep the typed value,")
  println("so they avoid the eJSON output — but still produce unquoted SQL.")
  println("Expr.lit() and === go through DynamicValue and produce eJSON.")
  println("Both failures share the same root cause: DynamicSchemaExpr.Literal")
  println("carries no Schema, so sqlLiteralDV cannot recover the case name.")
}
