package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object ImportSpec extends ZIOSpecDefault {
  def spec =
    suite("Import")(
      suite("SingleImport")(
        test("creates single import with path and name") {
          val imp = Import.SingleImport("com.example", "Foo")
          assert(imp.path)(equalTo("com.example")) &&
          assert(imp.name)(equalTo("Foo"))
        },
        test("preserves path with multiple segments") {
          val imp = Import.SingleImport("scala.collection.immutable", "List")
          assert(imp.path)(equalTo("scala.collection.immutable"))
        },
        test("handles name with uppercase") {
          val imp = Import.SingleImport("com.example", "MyClass")
          assert(imp.name)(equalTo("MyClass"))
        },
        test("handles name with numbers") {
          val imp = Import.SingleImport("com.example", "Type2D")
          assert(imp.name)(equalTo("Type2D"))
        },
        test("is instance of Import trait") {
          val imp: Import = Import.SingleImport("com.example", "Foo")
          assert(imp.path)(equalTo("com.example"))
        },
        test("copy preserves type") {
          val imp1 = Import.SingleImport("com.example", "Foo")
          val imp2 = imp1.copy(name = "Bar")
          assert(imp2.path)(equalTo("com.example")) &&
          assert(imp2.name)(equalTo("Bar"))
        },
        test("equality works correctly") {
          val imp1 = Import.SingleImport("com.example", "Foo")
          val imp2 = Import.SingleImport("com.example", "Foo")
          assert(imp1)(equalTo(imp2))
        },
        test("inequality when path differs") {
          val imp1 = Import.SingleImport("com.example", "Foo")
          val imp2 = Import.SingleImport("com.other", "Foo")
          assert(imp1)(not(equalTo(imp2)))
        },
        test("inequality when name differs") {
          val imp1 = Import.SingleImport("com.example", "Foo")
          val imp2 = Import.SingleImport("com.example", "Bar")
          assert(imp1)(not(equalTo(imp2)))
        }
      ),
      suite("WildcardImport")(
        test("creates wildcard import with path") {
          val imp = Import.WildcardImport("com.example")
          assert(imp.path)(equalTo("com.example"))
        },
        test("preserves path with multiple segments") {
          val imp = Import.WildcardImport("scala.collection.immutable")
          assert(imp.path)(equalTo("scala.collection.immutable"))
        },
        test("is instance of Import trait") {
          val imp: Import = Import.WildcardImport("com.example")
          assert(imp.path)(equalTo("com.example"))
        },
        test("copy preserves type") {
          val imp1 = Import.WildcardImport("com.example")
          val imp2 = imp1.copy(path = "com.other")
          assert(imp2.path)(equalTo("com.other"))
        },
        test("equality works correctly") {
          val imp1 = Import.WildcardImport("com.example")
          val imp2 = Import.WildcardImport("com.example")
          assert(imp1)(equalTo(imp2))
        },
        test("inequality when path differs") {
          val imp1 = Import.WildcardImport("com.example")
          val imp2 = Import.WildcardImport("com.other")
          assert(imp1)(not(equalTo(imp2)))
        }
      ),
      suite("RenameImport")(
        test("creates rename import with path, from, and to") {
          val imp = Import.RenameImport("com.example", "Foo", "Bar")
          assert(imp.path)(equalTo("com.example")) &&
          assert(imp.from)(equalTo("Foo")) &&
          assert(imp.to)(equalTo("Bar"))
        },
        test("preserves path with multiple segments") {
          val imp = Import.RenameImport(
            "scala.collection.immutable",
            "List",
            "ScalaList"
          )
          assert(imp.path)(equalTo("scala.collection.immutable"))
        },
        test("handles different from and to names") {
          val imp = Import.RenameImport("com.example", "OldName", "NewName")
          assert(imp.from)(equalTo("OldName")) &&
          assert(imp.to)(equalTo("NewName"))
        },
        test("is instance of Import trait") {
          val imp: Import = Import.RenameImport("com.example", "Foo", "Bar")
          assert(imp.path)(equalTo("com.example"))
        },
        test("copy preserves type") {
          val imp1 = Import.RenameImport("com.example", "Foo", "Bar")
          val imp2 = imp1.copy(from = "Baz")
          assert(imp2.path)(equalTo("com.example")) &&
          assert(imp2.from)(equalTo("Baz")) &&
          assert(imp2.to)(equalTo("Bar"))
        },
        test("equality works correctly") {
          val imp1 = Import.RenameImport("com.example", "Foo", "Bar")
          val imp2 = Import.RenameImport("com.example", "Foo", "Bar")
          assert(imp1)(equalTo(imp2))
        },
        test("inequality when path differs") {
          val imp1 = Import.RenameImport("com.example", "Foo", "Bar")
          val imp2 = Import.RenameImport("com.other", "Foo", "Bar")
          assert(imp1)(not(equalTo(imp2)))
        },
        test("inequality when from differs") {
          val imp1 = Import.RenameImport("com.example", "Foo", "Bar")
          val imp2 = Import.RenameImport("com.example", "Baz", "Bar")
          assert(imp1)(not(equalTo(imp2)))
        },
        test("inequality when to differs") {
          val imp1 = Import.RenameImport("com.example", "Foo", "Bar")
          val imp2 = Import.RenameImport("com.example", "Foo", "Baz")
          assert(imp1)(not(equalTo(imp2)))
        }
      ),
      suite("path field")(
        test("SingleImport.path returns correct value") {
          val imp = Import.SingleImport("scala.io", "File")
          assert(imp.path)(equalTo("scala.io"))
        },
        test("WildcardImport.path returns correct value") {
          val imp = Import.WildcardImport("java.util")
          assert(imp.path)(equalTo("java.util"))
        },
        test("RenameImport.path returns correct value") {
          val imp = Import.RenameImport("scala.util", "Try", "ScalaTry")
          assert(imp.path)(equalTo("scala.util"))
        }
      ),
      suite("polymorphism")(
        test("all import types work in same list") {
          val imports: List[Import] = List(
            Import.SingleImport("scala", "List"),
            Import.WildcardImport("java.io"),
            Import.RenameImport("scala.util", "Try", "ScalaTry")
          )
          assert(imports.length)(equalTo(3)) &&
          assert(imports(0).path)(equalTo("scala")) &&
          assert(imports(1).path)(equalTo("java.io")) &&
          assert(imports(2).path)(equalTo("scala.util"))
        },
        test("can filter imports by type") {
          val imports: List[Import] = List(
            Import.SingleImport("scala", "List"),
            Import.WildcardImport("java.io"),
            Import.SingleImport("scala", "Set")
          )
          val singles = imports.collect { case s: Import.SingleImport =>
            s
          }
          assert(singles.length)(equalTo(2))
        },
        test("can map over imports") {
          val imports: List[Import] = List(
            Import.SingleImport("scala", "List"),
            Import.WildcardImport("java.io")
          )
          val paths = imports.map(_.path)
          assert(paths)(
            equalTo(List("scala", "java.io"))
          )
        }
      ),
      suite("edge cases")(
        test("handles empty path") {
          val imp = Import.SingleImport("", "Foo")
          assert(imp.path)(equalTo(""))
        },
        test("handles single segment path") {
          val imp = Import.SingleImport("scala", "List")
          assert(imp.path)(equalTo("scala"))
        },
        test("handles very long path") {
          val longPath =
            "com.example.very.deeply.nested.package.structure.with.many.segments"
          val imp = Import.SingleImport(longPath, "Type")
          assert(imp.path)(equalTo(longPath))
        },
        test("handles names with special characters") {
          val imp = Import.RenameImport("com.example", "Type_Old", "Type_New")
          assert(imp.from)(equalTo("Type_Old")) &&
          assert(imp.to)(equalTo("Type_New"))
        }
      )
    )
}
