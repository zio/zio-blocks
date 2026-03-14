package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterTypeAliasSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitTypeAlias")(
      test("simple type alias") {
        val ta     = TypeAlias("UserId", typeRef = TypeRef.String)
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type UserId = String")
      },
      test("generic type alias with one type param") {
        val ta = TypeAlias(
          "Result",
          typeParams = List(TypeParam("A")),
          typeRef = TypeRef.of("Either", TypeRef.String, TypeRef("A"))
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type Result[A] = Either[String, A]")
      },
      test("generic type alias with multiple type params") {
        val ta = TypeAlias(
          "Result",
          typeParams = List(TypeParam("E", Variance.Covariant), TypeParam("A", Variance.Covariant)),
          typeRef = TypeRef.of("Either", TypeRef("E"), TypeRef("A"))
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type Result[+E, +A] = Either[E, A]")
      },
      test("type alias with doc") {
        val ta = TypeAlias(
          "UserId",
          typeRef = TypeRef.Long,
          doc = Some("A unique user identifier")
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A unique user identifier */
               |type UserId = Long""".stripMargin
        )
      },
      test("type alias with annotations") {
        val ta = TypeAlias(
          "Id",
          typeRef = TypeRef.Long,
          annotations = List(Annotation("deprecated", List(("message", "\"use UUID\""))))
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(
          result ==
            """|@deprecated(message = "use UUID")
               |type Id = Long""".stripMargin
        )
      },
      test("type alias with doc and annotations") {
        val ta = TypeAlias(
          "Id",
          typeRef = TypeRef.Long,
          annotations = List(Annotation("stable")),
          doc = Some("Stable ID type")
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(
          result.contains("/** Stable ID type */"),
          result.contains("@stable"),
          result.contains("type Id = Long")
        )
      },
      test("type alias with keyword name gets backtick-escaped") {
        val ta     = TypeAlias("type", typeRef = TypeRef.String)
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type `type` = String")
      },
      test("type alias with complex type ref") {
        val ta     = TypeAlias("UserMap", typeRef = TypeRef.map(TypeRef.String, TypeRef.list(TypeRef.Int)))
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type UserMap = Map[String, List[Int]]")
      },
      test("type alias with indentation") {
        val ta     = TypeAlias("Id", typeRef = TypeRef.Long)
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default, indent = 1)
        assertTrue(result == "  type Id = Long")
      },
      test("type alias emitted via emitTypeDefinition") {
        val ta: TypeDefinition = TypeAlias("Id", typeRef = TypeRef.Long)
        val direct             = ScalaEmitter.emitTypeAlias(ta.asInstanceOf[TypeAlias], EmitterConfig.default)
        val via                = ScalaEmitter.emitTypeDefinition(ta, EmitterConfig.default)
        assertTrue(direct == via)
      },
      test("type alias in a ScalaFile") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(
            TypeAlias("UserId", typeRef = TypeRef.Long),
            TypeAlias(
              "Result",
              typeParams = List(TypeParam("A")),
              typeRef = TypeRef.of("Either", TypeRef.String, TypeRef("A"))
            ),
            CaseClass("User", fields = List(Field("id", TypeRef("UserId"))))
          )
        )
        val result = ScalaEmitter.emit(file, EmitterConfig.default)
        assertTrue(
          result.contains("type UserId = Long"),
          result.contains("type Result[A] = Either[String, A]"),
          result.contains("case class User(")
        )
      },
      test("type alias with bounded type param") {
        val ta = TypeAlias(
          "Comparable",
          typeParams = List(TypeParam("A", upperBound = Some(TypeRef("Ordered", List(TypeRef("A")))))),
          typeRef = TypeRef("A")
        )
        val result = ScalaEmitter.emitTypeAlias(ta, EmitterConfig.default)
        assertTrue(result == "type Comparable[A <: Ordered[A]] = A")
      }
    )
}
