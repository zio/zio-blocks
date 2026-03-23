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
      },
      test("Scala 2 - enum with extendsTypes preserved") {
        val en = Enum(
          "Status",
          cases = List(EnumCase.SimpleCase("Active"), EnumCase.SimpleCase("Inactive")),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.scala2)
        assertTrue(result.contains("sealed trait Status extends Serializable"))
      },
      test("parameterized case with annotated fields emits single-line") {
        val en = Enum(
          "Event",
          cases = List(
            EnumCase.ParameterizedCase(
              "Created",
              List(Field("name", TypeRef.String, annotations = List(Annotation("required"))))
            )
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result ==
            """|enum Event {
               |  case Created(@required name: String)
               |}""".stripMargin
        )
      },
      test("enum with type params (type params not yet emitted in enum header)") {
        val en = Enum(
          "Option",
          typeParams = List(TypeParam("A", Variance.Covariant)),
          cases = List(
            EnumCase.ParameterizedCase("Some", List(Field("value", TypeRef("A")))),
            EnumCase.SimpleCase("None")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result.contains("enum Option"),
          result.contains("case Some(value: A)"),
          result.contains("case None")
        )
      },
      test("enum with multiple type params") {
        val en = Enum(
          "Either",
          typeParams = List(
            TypeParam("E", Variance.Covariant),
            TypeParam("A", Variance.Covariant)
          ),
          cases = List(
            EnumCase.ParameterizedCase("Left", List(Field("value", TypeRef("E")))),
            EnumCase.ParameterizedCase("Right", List(Field("value", TypeRef("A"))))
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result.contains("enum Either"),
          result.contains("case Left(value: E)"),
          result.contains("case Right(value: A)")
        )
      },
      test("enum with type params and extends") {
        val en = Enum(
          "Result",
          typeParams = List(TypeParam("E"), TypeParam("A")),
          cases = List(EnumCase.SimpleCase("Ok"), EnumCase.SimpleCase("Err")),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result.contains("enum Result"),
          result.contains("extends Serializable")
        )
      },
      test("enum with derives (Scala 3)") {
        val en = Enum(
          "Color",
          cases = List(
            EnumCase.SimpleCase("Red"),
            EnumCase.SimpleCase("Green")
          ),
          derives = List("Schema", "JsonCodec")
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(result.contains("enum Color"))
      },
      test("enum with companion object") {
        val en = Enum(
          "Priority",
          cases = List(EnumCase.SimpleCase("Low"), EnumCase.SimpleCase("High")),
          companion = Some(
            CompanionObject(
              List(ObjectMember.ValMember("default", TypeRef("Priority"), "Priority.Low"))
            )
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(result.contains("enum Priority"))
      },
      test("enum with type params in Scala 2 mode becomes sealed trait") {
        val en = Enum(
          "Result",
          typeParams = List(TypeParam("A")),
          cases = List(
            EnumCase.ParameterizedCase("Ok", List(Field("value", TypeRef("A")))),
            EnumCase.SimpleCase("Err")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.scala2)
        assertTrue(
          result.contains("sealed trait Result"),
          !result.contains("enum")
        )
      },
      test("parameterized case with extendsTypes is emitted (extendsTypes ignored in Scala 3 enum case)") {
        val en = Enum(
          "Tree",
          cases = List(
            EnumCase.ParameterizedCase(
              "Leaf",
              List(Field("value", TypeRef.Int)),
              extendsTypes = List(TypeRef("Tree"))
            ),
            EnumCase.SimpleCase("Empty")
          )
        )
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result.contains("case Leaf(value: Int)"),
          result.contains("case Empty")
        )
      },
      test("empty enum") {
        val en     = Enum("Empty", cases = Nil)
        val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
        assertTrue(
          result.contains("enum Empty"),
          result.contains("{")
        )
      }
    )
}
