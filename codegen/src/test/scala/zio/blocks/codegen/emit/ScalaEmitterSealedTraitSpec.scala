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

package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterSealedTraitSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitSealedTrait")(
      test("simple sealed trait with no cases") {
        val st     = SealedTrait("Shape")
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result == "sealed trait Shape")
      },
      test("sealed trait with case class cases") {
        val st = SealedTrait(
          "Shape",
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass("Circle", List(Field("radius", TypeRef.Double)))
            ),
            SealedTraitCase.CaseClassCase(
              CaseClass("Rectangle", List(Field("width", TypeRef.Double), Field("height", TypeRef.Double)))
            )
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|sealed trait Shape
               |
               |object Shape {
               |  case class Circle(
               |    radius: Double,
               |  ) extends Shape
               |  case class Rectangle(
               |    width: Double,
               |    height: Double,
               |  ) extends Shape
               |}""".stripMargin
        )
      },
      test("sealed trait with case object cases") {
        val st = SealedTrait(
          "Color",
          cases = List(
            SealedTraitCase.CaseObjectCase("Red"),
            SealedTraitCase.CaseObjectCase("Green"),
            SealedTraitCase.CaseObjectCase("Blue")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|sealed trait Color
               |
               |object Color {
               |  case object Red extends Color
               |  case object Green extends Color
               |  case object Blue extends Color
               |}""".stripMargin
        )
      },
      test("sealed trait with mixed cases") {
        val st = SealedTrait(
          "Shape",
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass("Circle", List(Field("radius", TypeRef.Double)))
            ),
            SealedTraitCase.CaseObjectCase("Unknown")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|sealed trait Shape
               |
               |object Shape {
               |  case class Circle(
               |    radius: Double,
               |  ) extends Shape
               |  case object Unknown extends Shape
               |}""".stripMargin
        )
      },
      test("sealed trait with type params") {
        val st = SealedTrait(
          "Result",
          typeParams = List(TypeParam("A"), TypeParam("E"))
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result == "sealed trait Result[A, E]")
      },
      test("sealed trait with doc") {
        val st     = SealedTrait("Shape", doc = Some("Represents a geometric shape"))
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** Represents a geometric shape */
               |sealed trait Shape""".stripMargin
        )
      },
      test("sealed trait with annotations") {
        val st = SealedTrait(
          "Event",
          annotations = List(Annotation("serializable"))
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|@serializable
               |sealed trait Event""".stripMargin
        )
      },
      test("sealed trait with companion object extra members") {
        val st = SealedTrait(
          "Shape",
          cases = List(
            SealedTraitCase.CaseObjectCase("Unknown")
          ),
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("default", TypeRef("Shape"), "Unknown")
              )
            )
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|sealed trait Shape
               |
               |object Shape {
               |  case object Unknown extends Shape
               |  val default: Shape = Unknown
               |}""".stripMargin
        )
      },
      test("case class case already has extends - not duplicated") {
        val st = SealedTrait(
          "Animal",
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass(
                "Dog",
                List(Field("name", TypeRef.String)),
                extendsTypes = List(TypeRef("Animal"))
              )
            )
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result.contains("extends Animal"))
        assertTrue(!result.contains("extends Animal with Animal"))
      },
      test("sealed trait with extendsTypes") {
        val st = SealedTrait(
          "Shape",
          extendsTypes = List(TypeRef("Serializable"), TypeRef("Product")),
          cases = List(
            SealedTraitCase.CaseObjectCase("Unknown")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result ==
            """|sealed trait Shape extends Serializable with Product
               |
               |object Shape {
               |  case object Unknown extends Shape
               |}""".stripMargin
        )
      },
      test("generic sealed trait cases get type params in extends") {
        val st = SealedTrait(
          "Result",
          typeParams = List(TypeParam("A")),
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass("Ok", List(Field("value", TypeRef("A"))))
            ),
            SealedTraitCase.CaseObjectCase("Err")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result.contains("extends Result[A]"))
        assertTrue(result.contains("case object Err extends Result[A]"))
      },
      test("sealed trait with selfType") {
        val st = SealedTrait(
          "Animal",
          selfType = Some(TypeRef("Living")),
          cases = List(
            SealedTraitCase.CaseObjectCase("Dog")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result.contains("sealed trait Animal { self: Living =>"))
      },
      test("sealed trait with selfType and no cases") {
        val st     = SealedTrait("Marker", selfType = Some(TypeRef("HasId")))
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(result == "sealed trait Marker { self: HasId => }")
      },
      test("sealed trait with derives (field is on IR but not yet emitted)") {
        val st = SealedTrait(
          "Shape",
          derives = List("Schema"),
          cases = List(SealedTraitCase.CaseObjectCase("Circle"))
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result.contains("sealed trait Shape"),
          result.contains("case object Circle extends Shape")
        )
      },
      test("sealed trait with multiple type params and cases") {
        val st = SealedTrait(
          "Validated",
          typeParams = List(TypeParam("E", Variance.Covariant), TypeParam("A", Variance.Covariant)),
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass("Valid", List(Field("value", TypeRef("A"))))
            ),
            SealedTraitCase.CaseClassCase(
              CaseClass("Invalid", List(Field("error", TypeRef("E"))))
            )
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result.contains("sealed trait Validated[+E, +A]"),
          result.contains("extends Validated[E, A]")
        )
      },
      test("sealed trait with case class that has generic fields") {
        val st = SealedTrait(
          "Response",
          cases = List(
            SealedTraitCase.CaseClassCase(
              CaseClass(
                "Success",
                List(
                  Field("data", TypeRef.list(TypeRef.String)),
                  Field("metadata", TypeRef.map(TypeRef.String, TypeRef.Int))
                )
              )
            ),
            SealedTraitCase.CaseObjectCase("NotFound")
          )
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
        assertTrue(
          result.contains("data: List[String]"),
          result.contains("metadata: Map[String, Int]"),
          result.contains("case object NotFound extends Response")
        )
      },
      test("sealed trait with indentation") {
        val st = SealedTrait(
          "Inner",
          cases = List(SealedTraitCase.CaseObjectCase("A"))
        )
        val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default, indent = 1)
        assertTrue(
          result.contains("  sealed trait Inner"),
          result.contains("  object Inner")
        )
      }
    )
}
