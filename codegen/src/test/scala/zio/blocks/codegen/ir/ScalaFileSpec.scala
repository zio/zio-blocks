package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object ScalaFileSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaFile")(
      suite("construction")(
        test("creates file with package only") {
          val file = ScalaFile(PackageDecl("com.example"))
          assert(file.packageDecl.path)(equalTo("com.example")) &&
          assert(file.imports)(isEmpty) &&
          assert(file.types)(isEmpty)
        },
        test("creates file with package and imports") {
          val imports = List(
            Import.SingleImport("scala.collection", "List"),
            Import.WildcardImport("zio")
          )
          val file = ScalaFile(PackageDecl("com.example"), imports = imports)
          assert(file.packageDecl.path)(equalTo("com.example")) &&
          assert(file.imports.length)(equalTo(2)) &&
          assert(file.types)(isEmpty)
        },
        test("creates file with package, imports, and types") {
          val imports = List(
            Import.SingleImport("scala.collection", "List")
          )
          val types = List(
            CaseClass("Person", List(Field("name", TypeRef.String)))
          )
          val file = ScalaFile(
            PackageDecl("com.example"),
            imports = imports,
            types = types
          )
          assert(file.packageDecl.path)(equalTo("com.example")) &&
          assert(file.imports.length)(equalTo(1)) &&
          assert(file.types.length)(equalTo(1))
        },
        test("handles multiple type definitions") {
          val types = List(
            CaseClass("Person", List(Field("name", TypeRef.String))),
            CaseClass("Address", List(Field("street", TypeRef.String))),
            SealedTrait("Shape")
          )
          val file = ScalaFile(
            PackageDecl("com.example"),
            types = types
          )
          assert(file.types.length)(equalTo(3))
        },
        test("preserves import order") {
          val imports = List(
            Import.SingleImport("scala.collection", "List"),
            Import.SingleImport("scala.util", "Try"),
            Import.WildcardImport("java.io")
          )
          val file = ScalaFile(PackageDecl("com.example"), imports = imports)
          assert(file.imports)(equalTo(imports))
        },
        test("preserves type definition order") {
          val types = List(
            CaseClass("A", Nil),
            CaseClass("B", Nil),
            CaseClass("C", Nil)
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types)(equalTo(types))
        }
      ),
      suite("import types")(
        test("works with SingleImport") {
          val file = ScalaFile(
            PackageDecl("com.example"),
            imports = List(Import.SingleImport("scala", "List"))
          )
          assert(file.imports.head.path)(equalTo("scala"))
        },
        test("works with WildcardImport") {
          val file = ScalaFile(
            PackageDecl("com.example"),
            imports = List(Import.WildcardImport("scala.io"))
          )
          assert(file.imports.head.path)(equalTo("scala.io"))
        },
        test("works with RenameImport") {
          val file = ScalaFile(
            PackageDecl("com.example"),
            imports = List(Import.RenameImport("scala", "List", "ScalaList"))
          )
          assert(file.imports.head.path)(equalTo("scala"))
        },
        test("works with mixed import types") {
          val imports = List(
            Import.SingleImport("scala", "List"),
            Import.WildcardImport("java.io"),
            Import.RenameImport("scala.util", "Try", "ScalaTry")
          )
          val file = ScalaFile(PackageDecl("com.example"), imports = imports)
          assert(file.imports.length)(equalTo(3))
        }
      ),
      suite("type definitions")(
        test("works with CaseClass") {
          val types = List(
            CaseClass("Person", List(Field("name", TypeRef.String)))
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.head.name)(equalTo("Person"))
        },
        test("works with SealedTrait") {
          val types = List(
            SealedTrait("Result")
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.head.name)(equalTo("Result"))
        },
        test("works with Enum") {
          val types = List(
            Enum("Color", List(EnumCase.SimpleCase("Red")))
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.head.name)(equalTo("Color"))
        },
        test("works with ObjectDef") {
          val types = List(
            ObjectDef("Utils")
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.head.name)(equalTo("Utils"))
        },
        test("works with Newtype") {
          val types = List(
            Newtype("UserId", TypeRef.Long)
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.head.name)(equalTo("UserId"))
        },
        test("works with mixed type definitions") {
          val types = List(
            CaseClass("Person", List(Field("name", TypeRef.String))),
            SealedTrait("Result"),
            Enum("Status", List(EnumCase.SimpleCase("Active"))),
            ObjectDef("Constants"),
            Newtype("Id", TypeRef.String)
          )
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.length)(equalTo(5))
        }
      ),
      suite("edge cases")(
        test("empty file with just package") {
          val file = ScalaFile(PackageDecl(""))
          assert(file.packageDecl.path)(equalTo("")) &&
          assert(file.imports)(isEmpty) &&
          assert(file.types)(isEmpty)
        },
        test("file with deep package path") {
          val file =
            ScalaFile(PackageDecl("com.example.deeply.nested.package"))
          assert(file.packageDecl.path)(
            equalTo("com.example.deeply.nested.package")
          )
        },
        test("file with many imports") {
          val imports = (1 to 100).map { i =>
            Import.SingleImport("pkg", s"Type$i")
          }.toList
          val file = ScalaFile(PackageDecl("com.example"), imports = imports)
          assert(file.imports.length)(equalTo(100))
        },
        test("file with many type definitions") {
          val types = (1 to 50).map { i =>
            CaseClass(s"Type$i", Nil)
          }.toList
          val file = ScalaFile(PackageDecl("com.example"), types = types)
          assert(file.types.length)(equalTo(50))
        }
      ),
      suite("field access")(
        test("packageDecl field is accessible") {
          val pkg  = PackageDecl("com.example")
          val file = ScalaFile(pkg)
          assert(file.packageDecl)(equalTo(pkg))
        },
        test("imports field is accessible and mutable via copy") {
          val file1      = ScalaFile(PackageDecl("com.example"))
          val newImports =
            List(Import.SingleImport("scala", "List"))
          val file2 = file1.copy(imports = newImports)
          assert(file1.imports)(isEmpty) &&
          assert(file2.imports.length)(equalTo(1))
        },
        test("types field is accessible and mutable via copy") {
          val file1    = ScalaFile(PackageDecl("com.example"))
          val newTypes = List(CaseClass("Person", Nil))
          val file2    = file1.copy(types = newTypes)
          assert(file1.types)(isEmpty) &&
          assert(file2.types.length)(equalTo(1))
        }
      )
    )
}
