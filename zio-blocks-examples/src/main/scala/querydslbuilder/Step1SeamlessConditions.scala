package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 1: Seamless Condition Composition
 *
 * Demonstrates bridge extension methods that let SchemaExpr and Expr compose
 * with && and || without explicit .toExpr lifting. SchemaExpr values are
 * translated into Expr at the boundary via fromSchemaExpr.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step1SeamlessConditions"
 */
object Step1SeamlessConditions extends App {

  // --- Demonstrate seamless composition ---

  println("=== Step 1: Seamless Condition Composition ===")
  println()

  // Start with an Expr-producing expression so && can accept both Expr and SchemaExpr
  val condition =
    Product.price.between(10.0, 500.0) &&
      (Product.category === "Electronics") &&
      (Product.rating >= 4) &&
      Product.name.like("L%")

  println("Mixed SchemaExpr + Expr condition:")
  println(s"  ${exprToSql(condition)}")
  println()

  // OR combination works too
  val orCondition =
    Product.price.between(0.0, 10.0) ||
      (Product.rating >= 5).toExpr ||
      Product.category.in("Sale", "Clearance")

  println("OR combination:")
  println(s"  ${exprToSql(orCondition)}")
}
