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
 * Query DSL Part 4 — Step 2: The SELECT Builder
 *
 * Demonstrates the fluent SELECT builder with .columns(), .where(), .orderBy(),
 * and .limit() methods. The single unified exprToSql interpreter handles all
 * expression types — no dual-interpreter delegation needed.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step2SelectBuilder"
 */
object Step2SelectBuilder extends App {

  // --- Output ---

  println("=== Step 2: SELECT Builder ===")
  println()

  val basicSelect = select(Product.table)
    .columns(Product.name, Product.price)
    .where(Product.inStock === true)

  println("1. Basic SELECT:")
  println(s"   ${renderSelect(basicSelect)}")
  println()

  val advancedSelect = select(Product.table)
    .columns(Product.name, Product.price, Product.category)
    .where(
      Product.price.between(10.0, 500.0) &&
        (Product.category === "Electronics") &&
        (Product.rating >= 4)
    )
    .orderBy(Product.price, SortOrder.Desc)
    .limit(10)

  println("2. Advanced SELECT with ordering and limit:")
  println(s"   ${renderSelect(advancedSelect)}")
  println()

  val selectAll = select(Product.table)
    .where(Product.name.like("Wire%"))

  println("3. SELECT * with LIKE condition:")
  println(s"   ${renderSelect(selectAll)}")
}
