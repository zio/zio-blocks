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

package querydslsql

/**
 * Query DSL SQL Generation — Part 2, Step 3: SELECT Statements and
 * Parameterized Queries
 *
 * Demonstrates building complete SELECT statements from SchemaExpr predicates,
 * and generating parameterized queries with ? placeholders for SQL injection
 * safety.
 *
 * SQL helpers, SELECT builders, and parameterized query support are defined in
 * the package object (package.scala). The Product domain type is defined in
 * Common.scala.
 *
 * Run with: sbt "examples/runMain querydslsql.Step3SelectAndParameterized"
 */
object Step3SelectAndParameterized extends App {

  val query = (Product.category === "Electronics") && (Product.inStock === true) && (Product.price < 500.0)

  println("=== SELECT Statements ===")
  println()
  println(select("products", query))
  println()
  println(selectColumns("products", List("name", "price"), query))
  println()
  println(selectWithLimit("products", query, orderBy = Some("price ASC"), limit = Some(10)))
  println()

  // --- Parameterized Queries ---

  val q          = (Product.category === "Electronics") && (Product.price < 500.0) && (Product.rating >= 4)
  val paramQuery = toParameterized(q)

  println("=== Parameterized Queries ===")
  println()
  println(s"SQL:    ${paramQuery.sql}")
  println(s"Params: ${paramQuery.params}")
}
