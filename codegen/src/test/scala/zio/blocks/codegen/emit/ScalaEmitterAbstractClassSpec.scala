package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterAbstractClassSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitAbstractClass")(
      test("simple abstract class with no fields") {
        val ac     = AbstractClass("Base")
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Base")
      },
      test("abstract class with fields") {
        val ac = AbstractClass(
          "Entity",
          fields = List(Field("id", TypeRef.Long))
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Entity(
               |  id: Long,
               |)""".stripMargin
        )
      },
      test("abstract class with type params and extends") {
        val ac = AbstractClass(
          "Container",
          typeParams = List(TypeParam("A")),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Container[A] extends Serializable")
      },
      test("abstract class with abstract member") {
        val ac = AbstractClass(
          "Base",
          members = List(
            ObjectMember.DefMember(Method("name", returnType = TypeRef.String))
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Base {
               |  def name: String
               |}""".stripMargin
        )
      },
      test("abstract class with doc and annotations") {
        val ac = AbstractClass(
          "Base",
          annotations = List(Annotation("serializable")),
          doc = Some("Base entity")
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** Base entity */
               |@serializable
               |abstract class Base""".stripMargin
        )
      },
      test("abstract class with companion") {
        val ac = AbstractClass(
          "Base",
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("version", TypeRef.Int, "1")
              )
            )
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Base
               |
               |object Base {
               |  val version: Int = 1
               |}""".stripMargin
        )
      },
      test("abstract class emitted via emitTypeDefinition") {
        val ac: TypeDefinition = AbstractClass("Base")
        val result             = ScalaEmitter.emitTypeDefinition(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Base")
      }
    )
}
