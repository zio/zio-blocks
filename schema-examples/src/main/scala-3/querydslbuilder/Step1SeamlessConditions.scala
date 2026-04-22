/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package querydslbuilder

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
