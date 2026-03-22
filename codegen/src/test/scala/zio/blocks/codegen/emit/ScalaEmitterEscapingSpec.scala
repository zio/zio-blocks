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
        },
        test("all Scala keywords are backtick-escaped as field names") {
          val keywords = List(
            "abstract",
            "case",
            "catch",
            "class",
            "def",
            "do",
            "else",
            "enum",
            "export",
            "extends",
            "false",
            "final",
            "finally",
            "for",
            "forSome",
            "given",
            "if",
            "implicit",
            "import",
            "lazy",
            "match",
            "new",
            "null",
            "object",
            "override",
            "package",
            "private",
            "protected",
            "return",
            "sealed",
            "super",
            "then",
            "this",
            "throw",
            "trait",
            "true",
            "try",
            "type",
            "val",
            "var",
            "while",
            "with",
            "yield"
          )
          val results = keywords.map { kw =>
            val field = Field(kw, TypeRef.String)
            ScalaEmitter.emitField(field, EmitterConfig.default)
          }
          assertTrue(results.forall(r => r.startsWith("`") && r.contains("`: String")))
        },
        test("Scala 3 soft keywords 'enum', 'given', 'export', 'then' are backtick-escaped") {
          val softKw  = List("enum", "given", "export", "then")
          val results = softKw.map(kw => ScalaEmitter.emitField(Field(kw, TypeRef.Int), EmitterConfig.default))
          assertTrue(results.forall(_.startsWith("`")))
        },
        test("identifier starting with digit gets backtick-escaped") {
          val field  = Field("123abc", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`123abc`: String")
        },
        test("operator-like identifier gets backtick-escaped") {
          val field  = Field("+", TypeRef.Int)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`+`: Int")
        },
        test("HTTP header-style names are backtick-escaped") {
          val fields  = List("content-type", "x-forwarded-for", "accept-encoding")
          val results =
            fields.map(name => ScalaEmitter.emitField(Field(name, TypeRef.String), EmitterConfig.default))
          assertTrue(results.forall(r => r.startsWith("`") && r.endsWith("`: String")))
        },
        test("field named 'extends' gets backtick-escaped") {
          val field  = Field("extends", TypeRef.Boolean)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`extends`: Boolean")
        },
        test("field with dot in name is NOT backtick-escaped") {
          val field  = Field("a.b", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`a.b`: String")
        },
        test("numeric-only field name gets backtick-escaped") {
          val field  = Field("42", TypeRef.Int)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "`42`: Int")
        },
        test("empty string field name gets backtick-escaped") {
          val field  = Field("", TypeRef.String)
          val result = ScalaEmitter.emitField(field, EmitterConfig.default)
          assertTrue(result == "``: String")
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
        },
        test("case class where every field name is a keyword") {
          val cc = CaseClass(
            "Problematic",
            fields = List(
              Field("type", TypeRef.String),
              Field("class", TypeRef.Int),
              Field("val", TypeRef.Boolean),
              Field("def", TypeRef.Double),
              Field("match", TypeRef.Long),
              Field("for", TypeRef.String)
            )
          )
          val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
          assertTrue(
            result.contains("`type`: String"),
            result.contains("`class`: Int"),
            result.contains("`val`: Boolean"),
            result.contains("`def`: Double"),
            result.contains("`match`: Long"),
            result.contains("`for`: String")
          )
        },
        test("case class with hyphenated field names") {
          val cc = CaseClass(
            "HttpRequest",
            fields = List(
              Field("content-type", TypeRef.String),
              Field("x-request-id", TypeRef.String)
            )
          )
          val result = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
          assertTrue(
            result.contains("`content-type`: String"),
            result.contains("`x-request-id`: String")
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
        },
        test("method named 'for' with keyword param gets both escaped") {
          val method = Method(
            "for",
            params = List(ParamList(List(MethodParam("while", TypeRef.Int)))),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(result == "def `for`(`while`: Int): Unit")
        }
      ),
      suite("emitObjectDef escapes keywords")(
        test("object named 'type' gets backtick-escaped") {
          val obj    = ObjectDef("type")
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result == "object `type`")
        },
        test("object named 'import' gets backtick-escaped") {
          val obj    = ObjectDef("import")
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result == "object `import`")
        }
      ),
      suite("emitSealedTrait escapes keywords")(
        test("sealed trait named 'class' gets backtick-escaped") {
          val st     = SealedTrait("class")
          val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
          assertTrue(result == "sealed trait `class`")
        },
        test("sealed trait named 'sealed' gets backtick-escaped") {
          val st     = SealedTrait("sealed")
          val result = ScalaEmitter.emitSealedTrait(st, EmitterConfig.default)
          assertTrue(result == "sealed trait `sealed`")
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
        },
        test("enum named 'class' gets backtick-escaped") {
          val en = Enum(
            "class",
            cases = List(EnumCase.SimpleCase("X"))
          )
          val result = ScalaEmitter.emitEnum(en, EmitterConfig.default)
          assertTrue(result.contains("enum `class`"))
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
        },
        test("newtype named 'import' gets backtick-escaped") {
          val nt     = Newtype("import", wrappedType = TypeRef.Int)
          val result = ScalaEmitter.emitNewtype(nt, EmitterConfig.default)
          assertTrue(
            result.contains("object `import` extends Newtype[Int]"),
            result.contains("type `import` = `import`.Type")
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
        },
        test("lazy val member named 'lazy' gets backtick-escaped") {
          val obj = ObjectDef(
            "Container",
            members = List(
              ObjectMember.ValMember("lazy", TypeRef.String, "\"val\"", isLazy = true)
            )
          )
          val result = ScalaEmitter.emitObjectDef(obj, EmitterConfig.default)
          assertTrue(result.contains("lazy val `lazy`: String"))
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
        },
        test("multiple keyword params all get backtick-escaped") {
          val method = Method(
            "process",
            params = List(
              ParamList(
                List(
                  MethodParam("class", TypeRef.String),
                  MethodParam("val", TypeRef.Int),
                  MethodParam("for", TypeRef.Boolean)
                )
              )
            ),
            returnType = TypeRef.Unit
          )
          val result = ScalaEmitter.emitMethod(method, EmitterConfig.default)
          assertTrue(
            result.contains("`class`: String"),
            result.contains("`val`: Int"),
            result.contains("`for`: Boolean")
          )
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
        },
        test("type name 'match' is backtick-escaped") {
          val tr     = TypeRef("match")
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "`match`")
        },
        test("generic type with keyword name is backtick-escaped") {
          val tr     = TypeRef("type", List(TypeRef.String))
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "`type`[String]")
        }
      ),
      suite("emitTypeParam escapes keywords")(
        test("type param named 'type' gets backtick-escaped") {
          val result = ScalaEmitter.emitTypeParam(TypeParam("type"))
          assertTrue(result == "`type`")
        },
        test("covariant type param named 'val' gets backtick-escaped") {
          val result = ScalaEmitter.emitTypeParam(TypeParam("val", Variance.Covariant))
          assertTrue(result == "+`val`")
        },
        test("type param starting with digit gets backtick-escaped") {
          val result = ScalaEmitter.emitTypeParam(TypeParam("1A"))
          assertTrue(result == "`1A`")
        }
      ),
      suite("emitTrait escapes keywords")(
        test("trait named 'type' gets backtick-escaped") {
          val t      = Trait("type")
          val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
          assertTrue(result == "trait `type`")
        }
      ),
      suite("emitAbstractClass escapes keywords")(
        test("abstract class named 'class' gets backtick-escaped") {
          val ac     = AbstractClass("class")
          val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
          assertTrue(result == "abstract class `class`")
        }
      ),
      suite("emitOpaqueType escapes keywords")(
        test("opaque type named 'type' gets backtick-escaped") {
          val ot     = OpaqueType("type", underlyingType = TypeRef.String)
          val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
          assertTrue(result.contains("opaque type `type` = String"))
        }
      ),
      suite("emitCompanionObject escapes keywords")(
        test("companion object for keyword-named type gets backtick-escaped") {
          val result = ScalaEmitter.emitCompanionObject(
            "type",
            CompanionObject(List(ObjectMember.ValMember("x", TypeRef.Int, "1"))),
            EmitterConfig.default
          )
          assertTrue(result.contains("object `type`"))
        }
      )
    )
}
