package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterFileSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emit (file)")(
      test("minimal file with just a package") {
        val file   = ScalaFile(PackageDecl("com.example"))
        val result = ScalaEmitter.emit(file)
        assertTrue(result == "package com.example\n")
      },
      test("file with imports") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          imports = List(
            Import.WildcardImport("scala.collection"),
            Import.SingleImport("zio", "ZIO")
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(
          result ==
            """|package com.example
               |
               |import scala.collection.*
               |import zio.ZIO
               |""".stripMargin
        )
      },
      test("file with Scala 2 imports") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          imports = List(
            Import.WildcardImport("scala.collection"),
            Import.RenameImport("java.util", "ArrayList", "JList")
          )
        )
        val result = ScalaEmitter.emit(file, EmitterConfig.scala2)
        assertTrue(
          result ==
            """|package com.example
               |
               |import java.util.{ArrayList => JList}
               |import scala.collection._
               |""".stripMargin
        )
      },
      test("file with type definitions") {
        val file = ScalaFile(
          PackageDecl("com.example.model"),
          imports = List(
            Import.SingleImport("zio.blocks.schema", "Schema")
          ),
          types = List(
            CaseClass(
              "Person",
              fields = List(
                Field("name", TypeRef.String),
                Field("age", TypeRef.Int)
              ),
              derives = List("Schema")
            )
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(
          result ==
            """|package com.example.model
               |
               |import zio.blocks.schema.Schema
               |
               |case class Person(
               |  name: String,
               |  age: Int,
               |) derives Schema
               |""".stripMargin
        )
      },
      test("file with multiple types separated by blank lines") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(
            CaseClass("A", List(Field("x", TypeRef.Int))),
            CaseClass("B", List(Field("y", TypeRef.String)))
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(
          result ==
            """|package com.example
               |
               |case class A(
               |  x: Int,
               |)
               |
               |case class B(
               |  y: String,
               |)
               |""".stripMargin
        )
      },
      test("full file with imports sorted and deduplicated") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          imports = List(
            Import.SingleImport("scala", "List"),
            Import.SingleImport("scala", "Option"),
            Import.SingleImport("scala", "List")
          ),
          types = List(
            CaseClass("Wrapper", List(Field("items", TypeRef.list(TypeRef.String))))
          )
        )
        val result = ScalaEmitter.emit(file)
        // Duplicates removed, sorted
        assertTrue(result.contains("import scala.List\nimport scala.Option\n"))
        assertTrue(!result.contains("import scala.List\nimport scala.List"))
      },
      test("file with newtype") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(
            Newtype("UserId", TypeRef.String)
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(
          result ==
            """|package com.example
               |
               |object UserId extends Newtype[String]
               |type UserId = UserId.Type
               |""".stripMargin
        )
      },
      test("file with sealed trait") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(
            SealedTrait(
              "Shape",
              cases = List(
                SealedTraitCase.CaseObjectCase("Circle"),
                SealedTraitCase.CaseObjectCase("Square")
              )
            )
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(result.contains("sealed trait Shape"))
        assertTrue(result.contains("object Shape {"))
        assertTrue(result.contains("case object Circle extends Shape"))
      },
      test("file with enum (Scala 3)") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(
            Enum(
              "Color",
              cases = List(
                EnumCase.SimpleCase("Red"),
                EnumCase.SimpleCase("Blue")
              )
            )
          )
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(result.contains("enum Color {"))
        assertTrue(result.contains("case Red, Blue"))
      },
      test("file with no imports - no extra blank line") {
        val file = ScalaFile(
          PackageDecl("com.example"),
          types = List(CaseClass("X", Nil))
        )
        val result = ScalaEmitter.emit(file)
        assertTrue(
          result ==
            """|package com.example
               |
               |case class X()
               |""".stripMargin
        )
      }
    )
}
