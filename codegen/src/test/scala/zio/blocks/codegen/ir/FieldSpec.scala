package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object FieldSpec extends ZIOSpecDefault {
  def spec =
    suite("Field")(
      suite("construction")(
        test("creates simple field") {
          val field = Field("name", TypeRef.String)
          assert(field.name)(equalTo("name")) &&
          assert(field.typeRef.name)(equalTo("String")) &&
          assert(field.defaultValue)(isNone) &&
          assert(field.annotations)(isEmpty) &&
          assert(field.doc)(isNone)
        },
        test("creates field with default value") {
          val field = Field("age", TypeRef.Int, Some("0"))
          assert(field.name)(equalTo("age")) &&
          assert(field.defaultValue)(isSome(equalTo("0")))
        },
        test("creates field with annotations") {
          val annot = Annotation("deprecated")
          val field = Field("oldField", TypeRef.String, annotations = List(annot))
          assert(field.annotations.length)(equalTo(1)) &&
          assert(field.annotations(0).name)(equalTo("deprecated"))
        },
        test("creates field with doc") {
          val field = Field("email", TypeRef.String, doc = Some("User email address"))
          assert(field.doc)(isSome(equalTo("User email address")))
        },
        test("creates field with all parameters") {
          val annot = Annotation("required")
          val field = Field(
            "id",
            TypeRef.Long,
            defaultValue = Some("1L"),
            annotations = List(annot),
            doc = Some("Unique identifier")
          )
          assert(field.name)(equalTo("id")) &&
          assert(field.typeRef.name)(equalTo("Long")) &&
          assert(field.defaultValue)(isSome(equalTo("1L"))) &&
          assert(field.annotations.length)(equalTo(1)) &&
          assert(field.doc)(isSome(equalTo("Unique identifier")))
        }
      ),
      suite("with different types")(
        test("field with simple type") {
          val field = Field("count", TypeRef.Int)
          assert(field.typeRef.name)(equalTo("Int"))
        },
        test("field with generic type") {
          val field = Field("items", TypeRef.list(TypeRef.String))
          assert(field.typeRef.name)(equalTo("List"))
        },
        test("field with optional type") {
          val field = Field("nickname", TypeRef.optional(TypeRef.String))
          assert(field.typeRef.name)(equalTo("Option"))
        },
        test("field with complex generic type") {
          val mapType = TypeRef.map(TypeRef.String, TypeRef.Int)
          val field   = Field("mapping", mapType)
          assert(field.typeRef.name)(equalTo("Map")) &&
          assert(field.typeRef.typeArgs.length)(equalTo(2))
        }
      ),
      suite("with annotations")(
        test("single annotation without args") {
          val annot = Annotation("required")
          val field = Field("username", TypeRef.String, annotations = List(annot))
          assert(field.annotations.length)(isGreaterThan(0)) &&
          assert(field.annotations(0).name)(equalTo("required")) &&
          assert(field.annotations(0).args)(isEmpty)
        },
        test("annotation with arguments") {
          val annot = Annotation("deprecated", List(("message", "\"use v2\"")))
          val field = Field("legacy", TypeRef.String, annotations = List(annot))
          assert(field.annotations(0).args.length)(equalTo(1)) &&
          assert(field.annotations(0).args(0)._1)(equalTo("message"))
        },
        test("multiple annotations") {
          val annot1 = Annotation("required")
          val annot2 = Annotation("indexed")
          val field  = Field("email", TypeRef.String, annotations = List(annot1, annot2))
          assert(field.annotations.length)(equalTo(2)) &&
          assert(field.annotations(0).name)(equalTo("required")) &&
          assert(field.annotations(1).name)(equalTo("indexed"))
        }
      ),
      suite("with default values")(
        test("string default") {
          val field = Field("status", TypeRef.String, Some("\"active\""))
          assert(field.defaultValue)(isSome(equalTo("\"active\"")))
        },
        test("numeric default") {
          val field = Field("count", TypeRef.Int, Some("10"))
          assert(field.defaultValue)(isSome(equalTo("10")))
        },
        test("boolean default") {
          val field = Field("active", TypeRef.Boolean, Some("true"))
          assert(field.defaultValue)(isSome(equalTo("true")))
        },
        test("no default when None") {
          val field = Field("required", TypeRef.String, None)
          assert(field.defaultValue)(isNone)
        }
      ),
      suite("with documentation")(
        test("field with doc string") {
          val field = Field("name", TypeRef.String, doc = Some("User full name"))
          assert(field.doc)(isSome(equalTo("User full name")))
        },
        test("field without doc") {
          val field = Field("id", TypeRef.Int)
          assert(field.doc)(isNone)
        }
      )
    )
}
