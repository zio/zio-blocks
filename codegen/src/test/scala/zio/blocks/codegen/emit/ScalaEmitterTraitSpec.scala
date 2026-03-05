package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterTraitSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitTrait")(
      test("simple trait with no members") {
        val t      = Trait("Serializable")
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Serializable")
      },
      test("trait with type params") {
        val t = Trait(
          "Container",
          typeParams = List(TypeParam("A", variance = Variance.Covariant))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Container[+A]")
      },
      test("trait with extends") {
        val t = Trait(
          "Named",
          extendsTypes = List(TypeRef("HasId"), TypeRef("HasName"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Named extends HasId with HasName")
      },
      test("trait with members") {
        val t = Trait(
          "HasId",
          members = List(
            ObjectMember.DefMember(Method("id", returnType = TypeRef.Long))
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait HasId {
               |  def id: Long
               |}""".stripMargin
        )
      },
      test("trait with companion") {
        val t = Trait(
          "Showable",
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("empty", TypeRef("Showable"), "null")
              )
            )
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Showable
               |
               |object Showable {
               |  val empty: Showable = null
               |}""".stripMargin
        )
      },
      test("trait with doc") {
        val t      = Trait("Marker", doc = Some("A marker trait"))
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A marker trait */
               |trait Marker""".stripMargin
        )
      },
      test("trait with annotations") {
        val t = Trait(
          "Event",
          annotations = List(Annotation("deprecated"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|@deprecated
               |trait Event""".stripMargin
        )
      },
      test("trait emitted via emitTypeDefinition") {
        val t: TypeDefinition = Trait("Marker")
        val result            = ScalaEmitter.emitTypeDefinition(t, EmitterConfig.default)
        assertTrue(result == "trait Marker")
      },
      test("trait with selfType and no members") {
        val t = Trait(
          "Service",
          selfType = Some(TypeRef("Logging"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Service {
               |  self: Logging =>
               |}""".stripMargin
        )
      },
      test("trait with selfType and members") {
        val t = Trait(
          "Service",
          selfType = Some(TypeRef("Logging")),
          members = List(
            ObjectMember.DefMember(Method("run", returnType = TypeRef.Unit))
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Service {
               |  self: Logging =>
               |  def run: Unit
               |}""".stripMargin
        )
      }
    )
}
