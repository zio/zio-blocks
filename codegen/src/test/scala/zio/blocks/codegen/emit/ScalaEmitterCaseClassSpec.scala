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

object ScalaEmitterCaseClassSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitCaseClass")(
      test("simple case class with fields") {
        val cc = CaseClass(
          "Person",
          fields = List(
            Field("name", TypeRef.String),
            Field("age", TypeRef.Int)
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Person(
               |  name: String,
               |  age: Int,
               |)""".stripMargin
        )
      },
      test("empty fields") {
        val cc     = CaseClass("Marker", fields = Nil)
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(result == "case class Marker()")
      },
      test("with extends") {
        val cc = CaseClass(
          "Dog",
          fields = List(Field("name", TypeRef.String)),
          extendsTypes = List(TypeRef("Animal"))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Dog(
               |  name: String,
               |) extends Animal""".stripMargin
        )
      },
      test("with multiple extends") {
        val cc = CaseClass(
          "Dog",
          fields = List(Field("name", TypeRef.String)),
          extendsTypes = List(TypeRef("Animal"), TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Dog(
               |  name: String,
               |) extends Animal with Serializable""".stripMargin
        )
      },
      test("with derives (Scala 3)") {
        val cc = CaseClass(
          "Person",
          fields = List(Field("name", TypeRef.String)),
          derives = List("Schema", "JsonCodec")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Person(
               |  name: String,
               |) derives Schema, JsonCodec""".stripMargin
        )
      },
      test("derives omitted in Scala 2") {
        val cc = CaseClass(
          "Person",
          fields = List(Field("name", TypeRef.String)),
          derives = List("Schema")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.scala2)
        assertTrue(!result.contains("derives"))
      },
      test("with type params") {
        val cc = CaseClass(
          "Box",
          fields = List(Field("value", TypeRef("A"))),
          typeParams = List(TypeParam("A"))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Box[A](
               |  value: A,
               |)""".stripMargin
        )
      },
      test("with companion object") {
        val cc = CaseClass(
          "Person",
          fields = List(Field("name", TypeRef.String)),
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("default", TypeRef("Person"), "Person(\"unknown\")")
              )
            )
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Person(
               |  name: String,
               |)
               |
               |object Person {
               |  val default: Person = Person("unknown")
               |}""".stripMargin
        )
      },
      test("with annotations") {
        val cc = CaseClass(
          "User",
          fields = List(Field("name", TypeRef.String)),
          annotations = List(Annotation("deprecated", List(("message", "\"use v2\""))))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|@deprecated(message = "use v2")
               |case class User(
               |  name: String,
               |)""".stripMargin
        )
      },
      test("with doc") {
        val cc = CaseClass(
          "User",
          fields = List(Field("name", TypeRef.String)),
          doc = Some("A user entity")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A user entity */
               |case class User(
               |  name: String,
               |)""".stripMargin
        )
      },
      test("no trailing commas when config says so") {
        val cc = CaseClass(
          "Point",
          fields = List(
            Field("x", TypeRef.Int),
            Field("y", TypeRef.Int)
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.scala2)
        assertTrue(
          result ==
            """|case class Point(
               |  x: Int,
               |  y: Int
               |)""".stripMargin
        )
      },
      test("field with annotations") {
        val cc = CaseClass(
          "Req",
          fields = List(
            Field("name", TypeRef.String, annotations = List(Annotation("required")))
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Req(
               |  @required
               |  name: String,
               |)""".stripMargin
        )
      },
      test("with default values") {
        val cc = CaseClass(
          "Config",
          fields = List(
            Field("debug", TypeRef.Boolean, Some("false")),
            Field("retries", TypeRef.Int, Some("3"))
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result ==
            """|case class Config(
               |  debug: Boolean = false,
               |  retries: Int = 3,
               |)""".stripMargin
        )
      },
      test("indented case class") {
        val cc = CaseClass(
          "Inner",
          fields = List(Field("value", TypeRef.Int))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default, indent = 1)
        assertTrue(
          result ==
            """|  case class Inner(
               |    value: Int,
               |  )""".stripMargin
        )
      },
      test("value class with isValueClass flag") {
        val cc = CaseClass(
          "Meter",
          fields = List(Field("value", TypeRef.Double)),
          isValueClass = true
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("case class Meter("),
          result.contains("value: Double"),
          result.contains("extends AnyVal")
        )
      },
      test("value class with explicit AnyVal extends") {
        val cc = CaseClass(
          "Meter",
          fields = List(Field("value", TypeRef.Double)),
          isValueClass = true,
          extendsTypes = List(TypeRef("AnyVal"))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("case class Meter("),
          result.contains("value: Double"),
          result.contains("extends AnyVal")
        )
      },
      test("value class with AnyVal and other extends") {
        val cc = CaseClass(
          "UserId",
          fields = List(Field("value", TypeRef.Long)),
          isValueClass = true,
          extendsTypes = List(TypeRef("AnyVal"), TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("extends AnyVal with Serializable")
        )
      },
      test("case class with multiple type params and variance") {
        val cc = CaseClass(
          "Pair",
          fields = List(
            Field("first", TypeRef("A")),
            Field("second", TypeRef("B"))
          ),
          typeParams = List(
            TypeParam("A", Variance.Covariant),
            TypeParam("B", Variance.Contravariant)
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("case class Pair[+A, -B]("),
          result.contains("first: A"),
          result.contains("second: B")
        )
      },
      test("case class with both extends and derives") {
        val cc = CaseClass(
          "User",
          fields = List(Field("name", TypeRef.String)),
          extendsTypes = List(TypeRef("Entity")),
          derives = List("Schema", "Codec")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("extends Entity"),
          result.contains("derives Schema, Codec")
        )
      },
      test("case class with extends and derives in Scala 2 omits derives") {
        val cc = CaseClass(
          "User",
          fields = List(Field("name", TypeRef.String)),
          extendsTypes = List(TypeRef("Entity")),
          derives = List("Schema")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.scala2)
        assertTrue(
          result.contains("extends Entity"),
          !result.contains("derives")
        )
      },
      test("case class with generic field types") {
        val cc = CaseClass(
          "Container",
          fields = List(
            Field("items", TypeRef.list(TypeRef.String)),
            Field("metadata", TypeRef.map(TypeRef.String, TypeRef.Int)),
            Field("optional", TypeRef.optional(TypeRef.Boolean))
          )
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("items: List[String]"),
          result.contains("metadata: Map[String, Int]"),
          result.contains("optional: Option[Boolean]")
        )
      },
      test("case class with doc and multiple annotations") {
        val cc = CaseClass(
          "Endpoint",
          fields = List(Field("path", TypeRef.String)),
          annotations = List(
            Annotation("deprecated", List(("message", "\"use v2\""))),
            Annotation("since", List(("version", "\"1.0\"")))
          ),
          doc = Some("An HTTP endpoint")
        )
        val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
        assertTrue(
          result.contains("/** An HTTP endpoint */"),
          result.contains("@deprecated"),
          result.contains("@since"),
          result.contains("case class Endpoint")
        )
      }
    )
}
