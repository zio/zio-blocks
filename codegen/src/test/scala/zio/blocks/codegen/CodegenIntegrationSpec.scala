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
      multipleTypesInOneFileSuite
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
            typeParams = List(TypeRef("A")),
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
                  typeParams = List(TypeRef("A")),
                  params = List(
                    List(
                      MethodParam("action", TypeRef.of("Function0", TypeRef("A"))),
                      MethodParam("times", TypeRef.Int, Some("MaxRetries"))
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
            if line.trim.startsWith("enum ") ||
              line.trim.startsWith("case class ") ||
              line.trim.startsWith("sealed trait ") ||
              line.trim.startsWith("object ") && !line.trim.startsWith("object Notification") &&
              !line.trim.startsWith("object ItemId extends") =>
          idx
      }

      assertTrue(typeStartIndices.length >= 4)

      val separatedByBlankLines = typeStartIndices.zip(typeStartIndices.tail).forall { case (_, next) =>
        lines.slice(0, next).exists(_.trim.isEmpty)
      }
      assertTrue(separatedByBlankLines)
    }
  )
}
