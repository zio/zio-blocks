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

package zio.blocks.schema

import zio.test._

// Opaque types with no Schema of their own: the record's derived schema describes them.
opaque type OpticLiteralUnits = Int

object OpticLiteralUnits {
  def apply(n: Int): OpticLiteralUnits = n
}

opaque type OpticLiteralRef = String

object OpticLiteralRef {
  def apply(s: String): OpticLiteralRef = s
}

case class OpticLiteralOrder(ref: OpticLiteralRef, units: OpticLiteralUnits) derives Schema

object OpticLiteralOrder extends CompanionOptics[OpticLiteralOrder] {
  val ref: Lens[OpticLiteralOrder, OpticLiteralRef]     = $(_.ref)
  val units: Lens[OpticLiteralOrder, OpticLiteralUnits] = $(_.units)
}

object OpticOpaqueLiteralSpec extends SchemaBaseSpec {

  private val order = OpticLiteralOrder(OpticLiteralRef("A-1"), OpticLiteralUnits(2))
  private val one   = OpticLiteralUnits(1)

  def spec: Spec[TestEnvironment, Any] = suite("OpticOpaqueLiteralSpec")(
    test("literal comparisons on opaque-type fields need no Schema for the opaque type") {
      assertTrue(
        (OpticLiteralOrder.ref === OpticLiteralRef("A-1")).eval(order) == Right(Seq(true)),
        (OpticLiteralOrder.units === one).eval(order) == Right(Seq(false)),
        (OpticLiteralOrder.units != one).eval(order) == Right(Seq(true)),
        (OpticLiteralOrder.units > one).eval(order) == Right(Seq(true)),
        (OpticLiteralOrder.units >= one).eval(order) == Right(Seq(true)),
        (OpticLiteralOrder.units < one).eval(order) == Right(Seq(false)),
        (OpticLiteralOrder.units <= one).eval(order) == Right(Seq(false))
      )
    },
    test("the literal is stored as the underlying primitive") {
      val literal = (OpticLiteralOrder.units === one).dynamic match {
        case DynamicSchemaExpr.Relational(_, DynamicSchemaExpr.Literal(value, _), _) => Some(value)
        case _                                                                       => None
      }
      assertTrue(literal.contains(DynamicValue.Primitive(PrimitiveValue.Int(1))))
    }
  )
}
