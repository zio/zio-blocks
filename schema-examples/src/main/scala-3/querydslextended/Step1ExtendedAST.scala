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
 * Run with: sbt "examples/runMain querydslextended.Step1ExtendedAST"
 */
object Step1ExtendedAST extends App {

  // --- Show the AST structure ---

  println("=== Expr AST Examples ===")
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
  val combined: Expr[Product, Boolean] =
    Product.category.in("Electronics") &&
      Product.price.between(10.0, 500.0) &&
      (Product.rating >= 4) // bridge extension handles SchemaExpr && Expr
  println(s"Combined expression: $combined")
}
