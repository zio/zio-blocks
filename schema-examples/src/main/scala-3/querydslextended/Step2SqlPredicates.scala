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
 * Query DSL Part 3 — Step 2: SQL-Specific Predicates
 *
 * Demonstrates generating SQL from the Expr ADT using the single unified
 * interpreter (exprToSql). SchemaExpr values are translated into Expr at the
 * boundary via fromSchemaExpr.
 *
 * Run with: sbt "examples/runMain querydslextended.Step2SqlPredicates"
 */
object Step2SqlPredicates extends App {

  // --- Output ---

  println("=== SQL-Specific Predicates ===")
  println()

  println(s"IN:       ${exprToSql(Product.category.in("Electronics", "Books", "Toys"))}")
  println(s"BETWEEN:  ${exprToSql(Product.price.between(10.0, 100.0))}")
  println(s"IS NULL:  ${exprToSql(Product.name.isNull)}")
  println(s"NOT NULL: ${exprToSql(Product.name.isNotNull)}")
  println(s"LIKE:     ${exprToSql(Product.name.like("Lap%"))}")
  println()

  // Translate a SchemaExpr into Expr, then render with the same interpreter
  val schemaExpr: SchemaExpr[Product, Boolean] = Product.rating >= 4
  val asExpr: Expr[Product, Boolean]           = Expr.fromSchemaExpr(schemaExpr)
  println(s"SchemaExpr translated to SQL: ${exprToSql(asExpr)}")
  println()

  println("=== Composing with SchemaExpr ===")
  println()

  // Bridge extensions auto-translate SchemaExpr at the boundary — no .toExpr needed
  val combined: Expr[Product, Boolean] =
    Product.category.in("Electronics", "Books") &&
      Product.price.between(10.0, 500.0) &&
      (Product.rating >= 4) &&
      Product.name.like("M%")

  println(s"SELECT * FROM products WHERE ${exprToSql(combined)}")
}
