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
          typeParams = List(TypeRef("A"), TypeRef("E"))
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
          typeParams = List(TypeRef("A")),
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
      }
    )
}
