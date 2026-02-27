package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterEnumSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitEnum")(
      test("simple enum - all cases on one line") {
        val en = Enum(
          "Color",
          cases = List(
            EnumCase.SimpleCase("Red"),
            EnumCase.SimpleCase("Green"),
            EnumCase.SimpleCase("Blue")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|enum Color {
               |  case Red, Green, Blue
               |}""".stripMargin
        )
      },
      test("parameterized enum cases") {
        val en = Enum(
          "Shape",
          cases = List(
            EnumCase.ParameterizedCase("Circle", List(Field("radius", TypeRef.Double))),
            EnumCase.ParameterizedCase(
              "Rectangle",
              List(Field("width", TypeRef.Double), Field("height", TypeRef.Double))
            )
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|enum Shape {
               |  case Circle(radius: Double)
               |  case Rectangle(width: Double, height: Double)
               |}""".stripMargin
        )
      },
      test("mixed simple and parameterized cases - each on own line") {
        val en = Enum(
          "Shape",
          cases = List(
            EnumCase.ParameterizedCase("Circle", List(Field("radius", TypeRef.Double))),
            EnumCase.ParameterizedCase(
              "Rectangle",
              List(Field("width", TypeRef.Double), Field("height", TypeRef.Double))
            ),
            EnumCase.SimpleCase("Unknown")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|enum Shape {
               |  case Circle(radius: Double)
               |  case Rectangle(width: Double, height: Double)
               |  case Unknown
               |}""".stripMargin
        )
      },
      test("enum with extends") {
        val en = Enum(
          "Color",
          cases = List(EnumCase.SimpleCase("Red")),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|enum Color extends Serializable {
               |  case Red
               |}""".stripMargin
        )
      },
      test("enum with doc and annotations") {
        val en = Enum(
          "Priority",
          cases = List(EnumCase.SimpleCase("Low"), EnumCase.SimpleCase("High")),
          annotations = List(Annotation("since", List(("version", "\"1.0\"")))),
          doc = Some("Priority levels")
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** Priority levels */
               |@since(version = "1.0")
               |enum Priority {
               |  case Low, High
               |}""".stripMargin
        )
      },
      test("Scala 2 - enum emitted as sealed trait") {
        val en = Enum(
          "Color",
          cases = List(
            EnumCase.SimpleCase("Red"),
            EnumCase.SimpleCase("Green")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.scala2)
        assertTrue(result.contains("sealed trait Color"))
        assertTrue(result.contains("case object Red extends Color"))
        assertTrue(result.contains("case object Green extends Color"))
        assertTrue(!result.contains("enum"))
      },
      test("Scala 2 - parameterized enum as sealed trait with case classes") {
        val en = Enum(
          "Shape",
          cases = List(
            EnumCase.ParameterizedCase("Circle", List(Field("radius", TypeRef.Double))),
            EnumCase.SimpleCase("Unknown")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.scala2)
        assertTrue(result.contains("sealed trait Shape"))
        assertTrue(result.contains("case class Circle"))
        assertTrue(result.contains("radius: Double"))
        assertTrue(result.contains("case object Unknown extends Shape"))
      }
    )
}
