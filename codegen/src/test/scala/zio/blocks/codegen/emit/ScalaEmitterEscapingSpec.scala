package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterEscapingSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter keyword/special char escaping")(
      suite("emitField escapes keywords")(
        test("field named 'type' gets backtick-escaped") {
          val field  = Field("type", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`type`: String")
        },
        test("field named 'class' gets backtick-escaped") {
          val field  = Field("class", TypeRef.Int)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`class`: Int")
        },
        test("field with hyphen gets backtick-escaped") {
          val field  = Field("my-field", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`my-field`: String")
        },
        test("field with space gets backtick-escaped") {
          val field  = Field("my field", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`my field`: String")
        },
        test("normal field name is NOT escaped") {
          val field  = Field("name", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "name: String")
        },
        test("underscore-prefixed name is NOT escaped") {
          val field  = Field("_id", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "_id: String")
        }
      ),
      suite("emitCaseClass escapes keywords")(
        test("case class named 'type' with keyword field") {
          val cc = CaseClass(
            "type",
            fields = List(Field("val", TypeRef.String))
          )
          val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
          assertTrue(
            result.contains("case class `type`("),
            result.contains("`val`: String")
          )
        }
      ),
      suite("emitMethod escapes keywords")(
        test("method named 'match' gets backtick-escaped") {
          val method = Method(
            "match",
            returnType = TypeRef.Boolean
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def `match`: Boolean")
        },
        test("normal method name is NOT escaped") {
          val method = Method(
            "process",
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def process: Unit")
        }
      ),
      suite("emitObjectDef escapes keywords")(
        test("object named 'type' gets backtick-escaped") {
          val obj    = ObjectDef("type")
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result == "object `type`")
        }
      ),
      suite("emitSealedTrait escapes keywords")(
        test("sealed trait named 'class' gets backtick-escaped") {
          val st     = SealedTrait("class")
          val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
          assertTrue(result == "sealed trait `class`")
        }
      ),
      suite("emitEnum escapes keywords")(
        test("enum named 'type' gets backtick-escaped") {
          val en = Enum(
            "type",
            cases = List(EnumCase.SimpleCase("A"))
          )
          val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
          assertTrue(result.contains("enum `type`"))
        }
      ),
      suite("emitNewtype escapes keywords")(
        test("newtype named 'val' gets backtick-escaped") {
          val nt     = Newtype("val", wrappedType = TypeRef.String)
          val result = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          assertTrue(
            result.contains("object `val` extends Newtype[String]"),
            result.contains("type `val` = `val`.Type")
          )
        }
      ),
      suite("emitObjectMember escapes keywords")(
        test("val member named 'type' gets backtick-escaped") {
          val obj = ObjectDef(
            "Consts",
            members = List(
              ObjectMember.ValMember("type", TypeRef.String, "\"foo\"")
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result.contains("val `type`: String"))
        },
        test("type alias named 'class' gets backtick-escaped") {
          val obj = ObjectDef(
            "Types",
            members = List(
              ObjectMember.TypeAlias("class", TypeRef.Long)
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result.contains("type `class` = Long"))
        }
      ),
      suite("emitMethodParam escapes keywords")(
        test("param named 'type' gets backtick-escaped") {
          val method = Method(
            "process",
            params = List(ParamList(List(MethodParam("type", TypeRef.String)))),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result.contains("`type`: String"))
        }
      ),
      suite("emitTypeRef escapes keyword type names")(
        test("type ref with keyword name gets backtick-escaped") {
          val tr     = TypeRef("type")
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "`type`")
        },
        test("standard type names are NOT escaped") {
          assertTrue(ScalaEmitter.emitTypeRef(TypeRef.String) == "String")
          assertTrue(ScalaEmitter.emitTypeRef(TypeRef.Int) == "Int")
          assertTrue(ScalaEmitter.emitTypeRef(TypeRef.Boolean) == "Boolean")
        },
        test("qualified type names with dots are NOT escaped") {
          val tr     = TypeRef("java.lang.String")
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "java.lang.String")
        }
      )
    )
}
