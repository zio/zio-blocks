package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterTypeRefSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter")(
      suite("emitTypeRef")(
        test("simple type") {
          val result = ScalaEmitter.emitTypeRef(TypeRef("String"))
          assertTrue(result == "String")
        },
        test("generic with one arg") {
          val result = ScalaEmitter.emitTypeRef(TypeRef("List", List(TypeRef("Int"))))
          assertTrue(result == "List[Int]")
        },
        test("generic with two args") {
          val result = ScalaEmitter.emitTypeRef(TypeRef("Map", List(TypeRef("String"), TypeRef("Int"))))
          assertTrue(result == "Map[String, Int]")
        },
        test("nested generics") {
          val inner  = TypeRef("List", List(TypeRef("Int")))
          val outer  = TypeRef("Map", List(TypeRef("String"), inner))
          val result = ScalaEmitter.emitTypeRef(outer)
          assertTrue(result == "Map[String, List[Int]]")
        }
      ),
      suite("emitAnnotation")(
        test("no args") {
          val result = ScalaEmitter.emitAnnotation(Annotation("required"))
          assertTrue(result == "@required")
        },
        test("single arg") {
          val result = ScalaEmitter.emitAnnotation(Annotation("deprecated", List(("message", "\"use v2\""))))
          assertTrue(result == "@deprecated(message = \"use v2\")")
        },
        test("multiple args") {
          val result = ScalaEmitter.emitAnnotation(
            Annotation("customAnnotation", List(("key", "\"value\""), ("num", "42")))
          )
          assertTrue(result == "@customAnnotation(key = \"value\", num = 42)")
        }
      ),
      suite("emitField")(
        test("simple field") {
          val result = ScalaEmitter.emitField(Field("name", TypeRef.String), EmitterConfig.default)
          assertTrue(result == "name: String")
        },
        test("with default value") {
          val result = ScalaEmitter.emitField(
            Field("age", TypeRef.Int, Some("0")),
            EmitterConfig.default
          )
          assertTrue(result == "age: Int = 0")
        },
        test("with annotation") {
          val result = ScalaEmitter.emitField(
            Field("name", TypeRef.String, annotations = List(Annotation("required"))),
            EmitterConfig.default
          )
          assertTrue(result == "@required\nname: String")
        },
        test("with multiple annotations") {
          val result = ScalaEmitter.emitField(
            Field(
              "email",
              TypeRef.String,
              annotations = List(Annotation("required"), Annotation("indexed"))
            ),
            EmitterConfig.default
          )
          assertTrue(result == "@required\n@indexed\nemail: String")
        },
        test("with doc is ignored in field emission") {
          val result = ScalaEmitter.emitField(
            Field("name", TypeRef.String, doc = Some("User name")),
            EmitterConfig.default
          )
          assertTrue(result == "name: String")
        }
      ),
      suite("emitImport")(
        test("single import") {
          val result = ScalaEmitter.emitImport(Import.SingleImport("com.example", "Foo"))
          assertTrue(result == "import com.example.Foo")
        },
        test("wildcard import Scala 3 syntax") {
          val result = ScalaEmitter.emitImport(Import.WildcardImport("com.example"))
          assertTrue(result == "import com.example.*")
        },
        test("wildcard import Scala 2 syntax") {
          val result = ScalaEmitter.emitImport(Import.WildcardImport("com.example"))
          assertTrue(result == "import com.example.*")
        },
        test("rename import Scala 3 syntax") {
          val result = ScalaEmitter.emitImport(Import.RenameImport("com.example", "Foo", "Bar"))
          assertTrue(result == "import com.example.{Foo as Bar}")
        },
        test("rename import Scala 2 syntax") {
          val result = ScalaEmitter.emitImport(Import.RenameImport("com.example", "Foo", "Bar"))
          assertTrue(result == "import com.example.{Foo as Bar}")
        }
      ),
      suite("emitImportWithConfig")(
        test("wildcard import Scala 3 config") {
          val result = ScalaEmitter.emitImport(Import.WildcardImport("com.example"), EmitterConfig.default)
          assertTrue(result == "import com.example.*")
        },
        test("wildcard import Scala 2 config") {
          val result = ScalaEmitter.emitImport(Import.WildcardImport("com.example"), EmitterConfig.scala2)
          assertTrue(result == "import com.example._")
        },
        test("rename import Scala 3 config") {
          val result =
            ScalaEmitter.emitImport(Import.RenameImport("com.example", "Foo", "Bar"), EmitterConfig.default)
          assertTrue(result == "import com.example.{Foo as Bar}")
        },
        test("rename import Scala 2 config") {
          val result =
            ScalaEmitter.emitImport(Import.RenameImport("com.example", "Foo", "Bar"), EmitterConfig.scala2)
          assertTrue(result == "import com.example.{Foo => Bar}")
        },
        test("single import unaffected by config") {
          val result = ScalaEmitter.emitImport(Import.SingleImport("com.example", "Foo"), EmitterConfig.scala2)
          assertTrue(result == "import com.example.Foo")
        }
      ),
      suite("emitPackageDecl")(
        test("simple package path") {
          val result = ScalaEmitter.emitPackageDecl(PackageDecl("com.example"))
          assertTrue(result == "package com.example")
        },
        test("deeply nested package path") {
          val result = ScalaEmitter.emitPackageDecl(PackageDecl("com.example.app.models"))
          assertTrue(result == "package com.example.app.models")
        }
      ),
      suite("organizeImports")(
        test("deduplication") {
          val imports = List(
            Import.SingleImport("com.example", "Foo"),
            Import.SingleImport("com.example", "Foo"),
            Import.SingleImport("com.example", "Bar")
          )
          val result = ScalaEmitter.organizeImports(imports, EmitterConfig.default)
          assertTrue(result.length == 2)
        },
        test("sorting when enabled") {
          val imports = List(
            Import.SingleImport("com.example", "Zebra"),
            Import.SingleImport("com.example", "Alpha"),
            Import.WildcardImport("com.another")
          )
          val result = ScalaEmitter.organizeImports(imports, EmitterConfig.default)
          assertTrue(
            result == List(
              Import.WildcardImport("com.another"),
              Import.SingleImport("com.example", "Alpha"),
              Import.SingleImport("com.example", "Zebra")
            )
          )
        },
        test("no sorting when config.sortImports is false") {
          val imports = List(
            Import.SingleImport("com.example", "Zebra"),
            Import.SingleImport("com.example", "Alpha"),
            Import.WildcardImport("com.another")
          )
          val result = ScalaEmitter.organizeImports(imports, EmitterConfig(sortImports = false))
          assertTrue(
            result == List(
              Import.SingleImport("com.example", "Zebra"),
              Import.SingleImport("com.example", "Alpha"),
              Import.WildcardImport("com.another")
            )
          )
        }
      )
    )
}
