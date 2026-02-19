package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Step 1: The Expr AST
 *
 * Demonstrates the Expr[S, A] expression language that translates from
 * SchemaExpr (via Expr.fromSchemaExpr). The Expr ADT is a superset: it includes
 * all SchemaExpr node types plus SQL-specific extensions (IN, BETWEEN, IS NULL,
 * LIKE), with a single unified interpreter.
 *
 * Key design: SchemaExpr values are *translated into* Expr.
 *
 * Run with: sbt "examples/runMain querydslextended.Step1ExtendedAST"
 */
object Step1ExtendedAST extends App {

  // --- Show the AST structure ---

  println("=== Independent Expr AST Examples ===")
  println()

  // SQL-specific nodes — built directly in Expr, no SchemaExpr involved
  val inExpr = Product.category.in("Electronics", "Books", "Toys")
  println(s"IN expression:      $inExpr")

  val betweenExpr = Product.price.between(10.0, 100.0)
  println(s"BETWEEN expression: $betweenExpr")

  val likeExpr = Product.name.like("Lap%")
  println(s"LIKE expression:    $likeExpr")

  val nullExpr = Product.name.isNull
  println(s"IS NULL expression: $nullExpr")
  println()

  // Translation from SchemaExpr
  val schemaExpr: SchemaExpr[Product, Boolean] = Product.rating >= 4
  val translated: Expr[Product, Boolean]       = Expr.fromSchemaExpr(schemaExpr)
  println(s"SchemaExpr:  $schemaExpr")
  println(s"Translated:  $translated")
  println()

  // Seamless composition
  val combined =
    Product.category.in("Electronics") &&
      Product.price.between(10.0, 500.0) &&
      (Product.rating >= 4) // bridge extension handles SchemaExpr && Expr
  println(s"Combined expression: $combined")
}
