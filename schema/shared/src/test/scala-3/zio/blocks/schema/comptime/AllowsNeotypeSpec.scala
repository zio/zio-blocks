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

package zio.blocks.schema.comptime

import neotype._
import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.blocks.schema.comptime.Allows
import Allows._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Allows[A, Wrapped[...]] with the neotype library.
 *
 * neotype is available as a test dependency for Scala 3 on both JVM and JS.
 */
object AllowsNeotypeSpec extends SchemaBaseSpec {

  // ---------------------------------------------------------------------------
  // neotype Newtype / Subtype wrappers
  // ---------------------------------------------------------------------------

  object SKU extends Newtype[String] {
    override inline def validate(s: String): Boolean = s.nonEmpty
    implicit val schema: Schema[SKU.Type]            = Schema.derived
  }
  type SKU = SKU.Type

  object Weight extends Newtype[Double] {
    override inline def validate(d: Double): Boolean = d >= 0.0
    implicit val schema: Schema[Weight.Type]         = Schema.derived
  }
  type Weight = Weight.Type

  case class Product(sku: SKU, weight: Weight)
  object Product { implicit val schema: Schema[Product] = Schema.derived }

  // ---------------------------------------------------------------------------
  // Positive evidence
  // ---------------------------------------------------------------------------

  val skuEv1: Allows[SKU, Wrapped[Primitive]]             = implicitly
  val skuEv2: Allows[SKU, Primitive | Wrapped[Primitive]] = implicitly
  val wEv: Allows[Weight, Wrapped[Primitive]]             = implicitly
  val prodEv: Allows[Product, Record[Wrapped[Primitive]]] = implicitly

  def spec: Spec[TestEnvironment, Any] = suite("AllowsNeotypeSpec")(
    test("SKU (neotype Newtype[String]) satisfies Wrapped[Primitive]") {
      assertTrue(skuEv1.ne(null))
    },
    test("SKU satisfies Primitive | Wrapped[Primitive]") {
      assertTrue(skuEv2.ne(null))
    },
    test("Weight (neotype Newtype[Double]) satisfies Wrapped[Primitive]") {
      assertTrue(wEv.ne(null))
    },
    test("Product (Record[SKU, Weight]) satisfies Record[Wrapped[Primitive]]") {
      assertTrue(prodEv.ne(null))
    },
    test("SKU does NOT satisfy bare Primitive") {
      typeCheck("""
        import neotype._
        import zio.blocks.schema.Schema
        import zio.blocks.schema.comptime.Allows
        import Allows._
        object SKU2 extends Newtype[String] {
          inline def validate(s: String): Boolean = s.nonEmpty
          implicit val schema: Schema[SKU2.Type]  = Schema.derived
        }
        summon[Allows[SKU2.Type, Primitive]]
      """).map(
        assert(_)(
          isLeft(containsString("Wrapped") || containsString("Primitive") || containsString("No given instance"))
        )
      )
    }
  )
}
