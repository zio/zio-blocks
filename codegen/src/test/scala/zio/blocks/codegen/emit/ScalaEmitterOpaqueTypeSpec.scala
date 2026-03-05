package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterOpaqueTypeSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitOpaqueType")(
      test("simple opaque type in Scala 3") {
        val ot     = OpaqueType("UserId", TypeRef.String)
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(result == "opaque type UserId = String")
      },
      test("opaque type with upper bound in Scala 3") {
        val ot = OpaqueType(
          "Age",
          underlyingType = TypeRef.Int,
          upperBound = Some(TypeRef.Int)
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(result == "opaque type Age <: Int = Int")
      },
      test("opaque type falls back to type alias in Scala 2") {
        val ot     = OpaqueType("UserId", TypeRef.String)
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.scala2)
        assertTrue(result == "type UserId = String")
      },
      test("opaque type with companion in Scala 3") {
        val ot = OpaqueType(
          "UserId",
          underlyingType = TypeRef.String,
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.DefMember(
                  Method(
                    "apply",
                    params = List(ParamList(List(MethodParam("value", TypeRef.String)))),
                    returnType = TypeRef("UserId")
                  )
                )
              )
            )
          )
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(
          result ==
            """|opaque type UserId = String
               |
               |object UserId {
               |  def apply(value: String): UserId
               |}""".stripMargin
        )
      },
      test("opaque type with doc") {
        val ot     = OpaqueType("UserId", TypeRef.String, doc = Some("A user identifier"))
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A user identifier */
               |opaque type UserId = String""".stripMargin
        )
      },
      test("opaque type emitted via emitTypeDefinition") {
        val ot: TypeDefinition = OpaqueType("UserId", TypeRef.String)
        val result             = ScalaEmitter.emitTypeDefinition(ot, EmitterConfig.default)
        assertTrue(result == "opaque type UserId = String")
      }
    )
}
