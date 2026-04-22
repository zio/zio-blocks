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

package querydslexpressions

import zio.blocks.schema._

/**
 * Query DSL with Reified Optics — Part 1, Step 3: Arithmetic and String
 * Operations
 *
 * Demonstrates arithmetic expressions (+, -, *) on numeric fields, string
 * operations (matches, concat, length), and dynamic evaluation.
 *
 * Run with: sbt "examples/runMain
 * querydslexpressions.Step3ArithmeticAndStrings"
 */
object Step3ArithmeticAndStrings extends App {

  // --- Domain Types ---

  case class Product(
    name: String,
    price: Double,
    category: String,
    inStock: Boolean,
    rating: Int
  )

  object Product extends CompanionOptics[Product] {
    implicit val schema: Schema[Product] = Schema.derived

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
  }

  val laptop = Product("Laptop", 999.99, "Electronics", true, 5)
  val pen    = Product("Pen", 2.50, "Office", true, 3)

  // --- Arithmetic Expressions ---

  val discountedPrice: SchemaExpr[Product, Double] =
    Product.price * 0.9

  val priceWithTax: SchemaExpr[Product, Double] =
    Product.price * 1.08

  println("=== Arithmetic Expressions ===")
  println()
  println(s"discountedPrice(laptop) = ${discountedPrice.eval(laptop)}")
  println(s"priceWithTax(pen)       = ${priceWithTax.eval(pen)}")
  println()

  // --- String Operations ---

  val startsWithL: SchemaExpr[Product, Boolean] =
    Product.name.matches("L.*")

  val labeledName: SchemaExpr[Product, String] =
    Product.name.concat(" [SALE]")

  val nameLength: SchemaExpr[Product, Int] =
    Product.name.length

  println("=== String Operations ===")
  println()
  println(s"startsWithL(laptop) = ${startsWithL.eval(laptop)}")
  println(s"startsWithL(pen)    = ${startsWithL.eval(pen)}")
  println()
  println(s"labeledName(laptop) = ${labeledName.eval(laptop)}")
  println()
  println(s"nameLength(laptop)  = ${nameLength.eval(laptop)}")
  println(s"nameLength(pen)     = ${nameLength.eval(pen)}")
  println()

  // --- Dynamic Evaluation ---

  val priceExpr: SchemaExpr[Product, Double] = Product.price * 0.9

  println("=== Dynamic Evaluation ===")
  println()
  println(s"priceExpr.evalDynamic(laptop) = ${priceExpr.evalDynamic(laptop)}")
}
