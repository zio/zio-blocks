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
          typeParams = List(TypeRef("A"))
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
      }
    )
}
