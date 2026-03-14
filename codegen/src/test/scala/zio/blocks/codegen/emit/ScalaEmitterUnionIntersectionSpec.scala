package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterUnionIntersectionSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter union and intersection types")(
      test("union of two types") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.Int)
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Int")
      },
      test("union of three types") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.Int, TypeRef.Boolean)
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Int | Boolean")
      },
      test("intersection of two types") {
        val typeRef = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "HasName & HasId")
      },
      test("nested union with Option") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.optional(TypeRef.Int))
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Option[Int]")
      },
      test("intersection used as field type") {
        val field  = Field("value", TypeRef.intersection(TypeRef("Readable"), TypeRef("Writable")))
        val result = ScalaEmitter.emitField(field, EmitterConfig.default)
        assertTrue(result == "value: Readable & Writable")
      }
    )
}
