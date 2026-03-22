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

package zio.blocks.codegen

import zio.test._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

object CodegenIntegrationSpec extends ZIOSpecDefault {
  def spec =
    suite("Codegen Integration")(
      fullApiModelSuite,
      scala2VsScala3Suite,
      importDeduplicationSuite,
      complexNestedTypesSuite,
      multipleTypesInOneFileSuite,
      advancedFeaturesSuite,
      crossVersionComparisonSuite
    )

  private val fullApiModelSuite = suite("full API model")(
    test("emits a realistic API model with DTOs, enums, and service objects") {
      val statusEnum = Enum(
        "Status",
        cases = List(
          EnumCase.SimpleCase("Active"),
          EnumCase.SimpleCase("Inactive"),
          EnumCase.SimpleCase("Suspended")
        )
      )

      val addressClass = CaseClass(
        "Address",
        fields = List(
          Field("street", TypeRef.String),
          Field("city", TypeRef.String),
          Field("zip", TypeRef.String)
        )
      )

      val personClass = CaseClass(
        "Person",
        fields = List(
          Field("name", TypeRef.String),
          Field("age", TypeRef.Int),
          Field("email", TypeRef.optional(TypeRef.String)),
          Field("address", TypeRef("Address")),
          Field("status", TypeRef("Status"))
        ),
        derives = List("Schema"),
        companion = Some(
          CompanionObject(
            members = List(
              ObjectMember.ValMember(
                "default",
                TypeRef("Person"),
                "Person(\"unnamed\", 0, None, Address(\"\", \"\", \"\"), Status.Active)"
              )
            )
          )
        )
      )

      val file = ScalaFile(
        PackageDecl("com.example.api.model"),
        imports = List(
          Import.WildcardImport("zio.blocks.schema"),
          Import.SingleImport("zio.blocks.chunk", "Chunk")
        ),
        types = List(statusEnum, addressClass, personClass)
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("package com.example.api.model"),
        result.contains("import zio.blocks.chunk.Chunk"),
        result.contains("import zio.blocks.schema.*"),
        result.contains("enum Status {"),
        result.contains("case Active, Inactive, Suspended"),
        result.contains("case class Address("),
        result.contains("street: String,"),
        result.contains("city: String,"),
        result.contains("zip: String,"),
        result.contains("case class Person("),
        result.contains("name: String,"),
        result.contains("age: Int,"),
        result.contains("email: Option[String],"),
        result.contains("address: Address,"),
        result.contains("status: Status,"),
        result.contains("derives Schema"),
        result.contains("object Person {"),
        result.contains("val default: Person =")
      )
    }
  )

  private val scala2VsScala3Suite = suite("Scala 2 vs Scala 3 syntax switching")(
    test("same IR produces Scala 3 enum, derives, and * wildcard") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        imports = List(
          Import.WildcardImport("zio.blocks.schema"),
          Import.RenameImport("java.util", "ArrayList", "JList")
        ),
        types = List(
          Enum(
            "Color",
            cases = List(
              EnumCase.SimpleCase("Red"),
              EnumCase.SimpleCase("Green"),
              EnumCase.SimpleCase("Blue")
            )
          ),
          CaseClass(
            "Pixel",
            fields = List(
              Field("x", TypeRef.Int),
              Field("y", TypeRef.Int),
              Field("color", TypeRef("Color"))
            ),
            derives = List("Schema")
          )
        )
      )

      val scala3 = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        scala3.contains("enum Color {"),
        scala3.contains("derives Schema"),
        scala3.contains("import zio.blocks.schema.*"),
        scala3.contains("{ArrayList as JList}")
      )
    },
    test("same IR produces Scala 2 sealed trait, no derives, and _ wildcard") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        imports = List(
          Import.WildcardImport("zio.blocks.schema"),
          Import.RenameImport("java.util", "ArrayList", "JList")
        ),
        types = List(
          Enum(
            "Color",
            cases = List(
              EnumCase.SimpleCase("Red"),
              EnumCase.SimpleCase("Green"),
              EnumCase.SimpleCase("Blue")
            )
          ),
          CaseClass(
            "Pixel",
            fields = List(
              Field("x", TypeRef.Int),
              Field("y", TypeRef.Int),
              Field("color", TypeRef("Color"))
            ),
            derives = List("Schema")
          )
        )
      )

      val scala2 = ScalaEmitter.emit(file, EmitterConfig.scala2)

      assertTrue(
        scala2.contains("sealed trait Color"),
        !scala2.contains("derives"),
        !scala2.contains("enum"),
        scala2.contains("import zio.blocks.schema._"),
        scala2.contains("{ArrayList => JList}")
      )
    }
  )

  private val importDeduplicationSuite = suite("import deduplication and sorting")(
    test("deduplicates identical imports and sorts them") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        imports = List(
          Import.SingleImport("zio", "ZIO"),
          Import.WildcardImport("scala.collection"),
          Import.SingleImport("zio", "ZIO"),
          Import.SingleImport("com.example", "Foo"),
          Import.WildcardImport("scala.collection"),
          Import.SingleImport("alpha", "Beta")
        ),
        types = List(CaseClass("X", List(Field("a", TypeRef.Int))))
      )

      val result        = ScalaEmitter.emit(file, EmitterConfig.default)
      val importSection = result.split("\n").filter(_.startsWith("import")).toList

      assertTrue(
        importSection.length == 4,
        importSection == List(
          "import alpha.Beta",
          "import com.example.Foo",
          "import scala.collection.*",
          "import zio.ZIO"
        )
      )
    },
    test("sorting can be disabled") {
      val imports = List(
        Import.SingleImport("zeta", "Z"),
        Import.SingleImport("alpha", "A")
      )
      val unsortedConfig = EmitterConfig(sortImports = false)
      val organized      = ScalaEmitter.organizeImports(imports, unsortedConfig)
      assertTrue(
        organized == List(
          Import.SingleImport("zeta", "Z"),
          Import.SingleImport("alpha", "A")
        )
      )
    }
  )

  private val complexNestedTypesSuite = suite("complex nested types")(
    test("sealed trait with case class cases that have nested generics") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        types = List(
          SealedTrait(
            "Event",
            typeParams = List(TypeParam("A")),
            cases = List(
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Created",
                  fields = List(
                    Field("id", TypeRef.Long),
                    Field("payload", TypeRef("A")),
                    Field("metadata", TypeRef.map(TypeRef.String, TypeRef.list(TypeRef.Int)))
                  )
                )
              ),
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Deleted",
                  fields = List(
                    Field("id", TypeRef.Long),
                    Field("tags", TypeRef.set(TypeRef.String))
                  )
                )
              ),
              SealedTraitCase.CaseObjectCase("Unknown")
            )
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("sealed trait Event[A]"),
        result.contains("case class Created("),
        result.contains("metadata: Map[String, List[Int]],"),
        result.contains("case class Deleted("),
        result.contains("tags: Set[String],"),
        result.contains("case object Unknown extends Event")
      )
    },
    test("object with val, def, type alias, and nested case class") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        types = List(
          ObjectDef(
            "Utils",
            members = List(
              ObjectMember.ValMember("MaxRetries", TypeRef.Int, "3"),
              ObjectMember.DefMember(
                Method(
                  "retry",
                  typeParams = List(TypeParam("A")),
                  params = List(
                    ParamList(
                      List(
                        MethodParam("action", TypeRef.of("Function0", TypeRef("A"))),
                        MethodParam("times", TypeRef.Int, Some("MaxRetries"))
                      )
                    )
                  ),
                  returnType = TypeRef("A"),
                  body = Some("???")
                )
              ),
              ObjectMember.TypeAlias("Result", TypeRef.of("Either", TypeRef.String, TypeRef("A"))),
              ObjectMember.NestedType(
                CaseClass(
                  "Config",
                  fields = List(
                    Field("timeout", TypeRef.Long),
                    Field("debug", TypeRef.Boolean)
                  )
                )
              )
            )
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("object Utils {"),
        result.contains("val MaxRetries: Int = 3"),
        result.contains("def retry[A]"),
        result.contains("times: Int = MaxRetries"),
        result.contains(": A = ???"),
        result.contains("type Result = Either[String, A]"),
        result.contains("case class Config("),
        result.contains("timeout: Long,"),
        result.contains("debug: Boolean,")
      )
    },
    test("newtype wrapper") {
      val file = ScalaFile(
        PackageDecl("com.example"),
        types = List(
          Newtype(
            "UserId",
            wrappedType = TypeRef.Long,
            annotations = List(Annotation("specialized")),
            doc = Some("A unique user identifier")
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("/** A unique user identifier */"),
        result.contains("@specialized"),
        result.contains("object UserId extends Newtype[Long]"),
        result.contains("type UserId = UserId.Type")
      )
    }
  )

  private val multipleTypesInOneFileSuite = suite("multiple types in one file")(
    test("file with 5+ types emitted and separated by blank lines") {
      val file = ScalaFile(
        PackageDecl("com.example.domain"),
        imports = List(
          Import.WildcardImport("zio.blocks.schema")
        ),
        types = List(
          Enum(
            "Priority",
            cases = List(
              EnumCase.SimpleCase("Low"),
              EnumCase.SimpleCase("Medium"),
              EnumCase.SimpleCase("High")
            )
          ),
          CaseClass(
            "Tag",
            fields = List(
              Field("name", TypeRef.String),
              Field("value", TypeRef.String)
            )
          ),
          CaseClass(
            "Item",
            fields = List(
              Field("id", TypeRef.Long),
              Field("title", TypeRef.String),
              Field("priority", TypeRef("Priority")),
              Field("tags", TypeRef.list(TypeRef("Tag")))
            ),
            derives = List("Schema")
          ),
          SealedTrait(
            "Notification",
            cases = List(
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Email",
                  fields = List(Field("to", TypeRef.String), Field("subject", TypeRef.String))
                )
              ),
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Sms",
                  fields = List(Field("phone", TypeRef.String), Field("body", TypeRef.String))
                )
              )
            )
          ),
          Newtype("ItemId", wrappedType = TypeRef.Long),
          ObjectDef(
            "Defaults",
            members = List(
              ObjectMember.ValMember("MaxItems", TypeRef.Int, "100"),
              ObjectMember.ValMember("DefaultPriority", TypeRef("Priority"), "Priority.Medium")
            )
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)
      val lines  = result.split("\n").toList

      assertTrue(
        result.contains("enum Priority {"),
        result.contains("case class Tag("),
        result.contains("case class Item("),
        result.contains("sealed trait Notification"),
        result.contains("object ItemId extends Newtype[Long]"),
        result.contains("object Defaults {"),
        result.contains("val MaxItems: Int = 100"),
        result.contains("val DefaultPriority: Priority = Priority.Medium")
      )

      val typeStartIndices = lines.zipWithIndex.collect {
        case (line, idx)
            if line == line.trim && (line.startsWith("enum ") ||
              line.startsWith("case class ") ||
              line.startsWith("sealed trait ") ||
              line.startsWith("object ") && !line.startsWith("object Notification") &&
              !line.startsWith("object ItemId extends")) =>
          idx
      }

      assertTrue(typeStartIndices.length >= 4)

      val separatedByBlankLines = typeStartIndices.zip(typeStartIndices.tail).forall { case (prevIdx, nextIdx) =>
        lines.slice(prevIdx + 1, nextIdx).exists(_.trim.isEmpty)
      }
      assertTrue(separatedByBlankLines)
    }
  )

  private val advancedFeaturesSuite = suite("advanced features")(
    test("file with value class, enum with type params, and abstract class") {
      val file = ScalaFile(
        PackageDecl("com.example.advanced"),
        imports = List(
          Import.SingleImport("zio.blocks.schema", "Schema")
        ),
        types = List(
          CaseClass(
            "UserId",
            fields = List(Field("value", TypeRef.Long)),
            isValueClass = true,
            extendsTypes = List(TypeRef("AnyVal"))
          ),
          AbstractClass(
            "Entity",
            fields = List(Field("id", TypeRef.Long)),
            typeParams = List(TypeParam("A")),
            isSealed = true
          ),
          Enum(
            "Status",
            typeParams = List(TypeParam("A")),
            cases = List(
              EnumCase.ParameterizedCase("Active", List(Field("value", TypeRef("A")))),
              EnumCase.SimpleCase("Inactive")
            )
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("package com.example.advanced"),
        result.contains("import zio.blocks.schema.Schema"),
        result.contains("case class UserId("),
        result.contains("value: Long"),
        result.contains("extends AnyVal"),
        result.contains("abstract class Entity"),
        result.contains("enum Status"),
        result.contains("case Active(value: A)"),
        result.contains("case Inactive")
      )
    },
    test("model with opaque types and newtypes together") {
      val file = ScalaFile(
        PackageDecl("com.example.types"),
        types = List(
          OpaqueType(
            "Token",
            underlyingType = TypeRef.String,
            upperBound = Some(TypeRef("Serializable"))
          ),
          Newtype("UserId", wrappedType = TypeRef.Long),
          CaseClass(
            "Session",
            fields = List(
              Field("token", TypeRef("Token")),
              Field("userId", TypeRef("UserId"))
            )
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("opaque type Token <: Serializable = String"),
        result.contains("object UserId extends Newtype[Long]"),
        result.contains("case class Session(")
      )
    },
    test("model with opaque types in Scala 2 mode omits 'opaque'") {
      val ot     = OpaqueType("Token", underlyingType = TypeRef.String)
      val result = ScalaEmitter.emitTypeDefinition(ot, EmitterConfig.scala2)
      assertTrue(
        result.contains("type Token = String"),
        !result.contains("opaque")
      )
    },
    test("model with methods using all ParamList modifiers") {
      val file = ScalaFile(
        PackageDecl("com.example.methods"),
        types = List(
          ObjectDef(
            "Api",
            members = List(
              ObjectMember.DefMember(
                Method(
                  "fetch",
                  typeParams = List(TypeParam("A")),
                  params = List(
                    ParamList(List(MethodParam("url", TypeRef.String))),
                    ParamList(
                      List(MethodParam("ctx", TypeRef("Context"))),
                      ParamListModifier.Using
                    )
                  ),
                  returnType = TypeRef("A"),
                  body = Some("???")
                )
              ),
              ObjectMember.DefMember(
                Method(
                  "show",
                  typeParams = List(TypeParam("A")),
                  params = List(
                    ParamList(List(MethodParam("value", TypeRef("A")))),
                    ParamList(
                      List(MethodParam("ev", TypeRef("Show[A]"))),
                      ParamListModifier.Using
                    )
                  ),
                  returnType = TypeRef.String,
                  body = Some("ev.show(value)")
                )
              )
            )
          )
        )
      )

      val scala3 = ScalaEmitter.emit(file, EmitterConfig.default)
      val scala2 = ScalaEmitter.emit(file, EmitterConfig.scala2)

      assertTrue(
        scala3.contains("(using ctx: Context)"),
        scala3.contains("(using ev: Show[A])"),
        scala2.contains("(implicit ctx: Context)"),
        scala2.contains("(implicit ev: Show[A])")
      )
    },
    test("trait with method having by-name and varargs params") {
      val t = Trait(
        "Logger",
        members = List(
          ObjectMember.DefMember(
            Method(
              "log",
              params = List(
                ParamList(
                  List(
                    MethodParam("level", TypeRef.String),
                    MethodParam("msg", TypeRef.String, isByName = true),
                    MethodParam("args", TypeRef.Any, isVarargs = true)
                  )
                )
              ),
              returnType = TypeRef.Unit
            )
          )
        )
      )
      val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
      assertTrue(
        result.contains("def log(level: String, msg: => String, args: Any*): Unit")
      )
    },
    test("complex sealed trait hierarchy with annotations and docs") {
      val file = ScalaFile(
        PackageDecl("com.example.events"),
        types = List(
          SealedTrait(
            "Event",
            typeParams = List(TypeParam("A")),
            extendsTypes = List(TypeRef("Serializable")),
            cases = List(
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Created",
                  fields = List(
                    Field("id", TypeRef.Long, annotations = List(Annotation("required"))),
                    Field("payload", TypeRef("A")),
                    Field("timestamp", TypeRef.Long)
                  ),
                  doc = Some("Entity was created")
                )
              ),
              SealedTraitCase.CaseClassCase(
                CaseClass(
                  "Updated",
                  fields = List(
                    Field("id", TypeRef.Long),
                    Field("changes", TypeRef.map(TypeRef.String, TypeRef("A")))
                  )
                )
              ),
              SealedTraitCase.CaseObjectCase("Deleted")
            ),
            annotations = List(Annotation("serializable")),
            doc = Some("Domain events")
          )
        )
      )

      val result = ScalaEmitter.emit(file, EmitterConfig.default)

      assertTrue(
        result.contains("/** Domain events */"),
        result.contains("@serializable"),
        result.contains("sealed trait Event[A] extends Serializable"),
        result.contains("/** Entity was created */"),
        result.contains("@required"),
        result.contains("id: Long"),
        result.contains("payload: A"),
        result.contains("changes: Map[String, A]"),
        result.contains("case object Deleted extends Event[A]")
      )
    }
  )

  private val crossVersionComparisonSuite = suite("cross-version comparison")(
    test("enum with parameterized cases: Scala 3 vs Scala 2") {
      val en = Enum(
        "Result",
        cases = List(
          EnumCase.ParameterizedCase("Ok", List(Field("value", TypeRef.String))),
          EnumCase.ParameterizedCase("Err", List(Field("message", TypeRef.String))),
          EnumCase.SimpleCase("Pending")
        )
      )

      val scala3 = ScalaEmitter.emitEnum(en, EmitterConfig.default)
      val scala2 = ScalaEmitter.emitEnum(en, EmitterConfig.scala2)

      assertTrue(
        scala3.contains("enum Result {"),
        scala3.contains("case Ok(value: String)"),
        scala3.contains("case Err(message: String)"),
        scala3.contains("case Pending"),
        scala2.contains("sealed trait Result"),
        scala2.contains("case class Ok"),
        scala2.contains("case class Err"),
        scala2.contains("case object Pending extends Result"),
        !scala2.contains("enum")
      )
    },
    test("case class with derives: Scala 3 vs Scala 2") {
      val cc = CaseClass(
        "Config",
        fields = List(
          Field("debug", TypeRef.Boolean, Some("false")),
          Field("maxRetries", TypeRef.Int, Some("3"))
        ),
        derives = List("Schema", "Codec")
      )

      val scala3 = ScalaEmitter.emitCaseClass(cc, EmitterConfig.default)
      val scala2 = ScalaEmitter.emitCaseClass(cc, EmitterConfig.scala2)

      assertTrue(
        scala3.contains("derives Schema, Codec"),
        scala3.contains("debug: Boolean = false,"),
        scala3.contains("maxRetries: Int = 3,"),
        !scala2.contains("derives"),
        scala2.contains("debug: Boolean = false,"),
        scala2.contains("maxRetries: Int = 3")
      )
    },
    test("import syntax: Scala 3 vs Scala 2") {
      val imports = List(
        Import.WildcardImport("scala.collection"),
        Import.RenameImport("java.util", "ArrayList", "JList"),
        Import.GroupImport("zio", List("ZIO", "Task"))
      )

      val scala3 = imports.map(i => ScalaEmitter.emitImport(i, EmitterConfig.default))
      val scala2 = imports.map(i => ScalaEmitter.emitImport(i, EmitterConfig.scala2))

      assertTrue(
        scala3(0) == "import scala.collection.*",
        scala3(1) == "import java.util.{ArrayList as JList}",
        scala2(0) == "import scala.collection._",
        scala2(1) == "import java.util.{ArrayList => JList}",
        scala3(2) == scala2(2)
      )
    },
    test("opaque type: Scala 3 vs Scala 2") {
      val ot = OpaqueType("Token", underlyingType = TypeRef.String)

      val scala3 = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
      val scala2 = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.scala2)

      assertTrue(
        scala3.contains("opaque type Token = String"),
        scala2.contains("type Token = String"),
        !scala2.contains("opaque")
      )
    },
    test("implicit/given: Scala 3 vs Scala 2") {
      val method = Method(
        "encode",
        typeParams = List(TypeParam("A")),
        returnType = TypeRef.String,
        isImplicit = true,
        body = Some("\"\"")
      )

      val scala3 = ScalaEmitter.emitMethod(method, EmitterConfig.default)
      val scala2 = ScalaEmitter.emitMethod(method, EmitterConfig.scala2)

      assertTrue(
        scala3.contains("given def encode"),
        scala2.contains("implicit def encode")
      )
    }
  )
}
