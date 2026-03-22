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
        test("rename import Scala 3 syntax") {
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
      ),
      suite("TypeRef factory methods")(
        test("TypeRef.tuple emits Tuple2[Int, String]") {
          val tr     = TypeRef.tuple(TypeRef.Int, TypeRef.String)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Tuple2[Int, String]")
        },
        test("TypeRef.tuple with three types emits Tuple3") {
          val tr     = TypeRef.tuple(TypeRef.Int, TypeRef.String, TypeRef.Boolean)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Tuple3[Int, String, Boolean]")
        },
        test("TypeRef.function with single param emits Function1") {
          val tr     = TypeRef.function(List(TypeRef.Int), TypeRef.String)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Function1[Int, String]")
        },
        test("TypeRef.function with two params emits Function2") {
          val tr     = TypeRef.function(List(TypeRef.Int, TypeRef.String), TypeRef.Boolean)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Function2[Int, String, Boolean]")
        },
        test("TypeRef.function with zero params emits Function0") {
          val tr     = TypeRef.function(Nil, TypeRef.Unit)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Function0[Unit]")
        },
        test("TypeRef.Wildcard emits _") {
          val result = ScalaEmitter.emitTypeRef(TypeRef.Wildcard)
          assertTrue(result == "_")
        },
        test("TypeRef.union emits pipe-separated types") {
          val tr     = TypeRef.union(TypeRef.String, TypeRef.Int)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "String | Int")
        },
        test("TypeRef.intersection emits ampersand-separated types") {
          val tr     = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "HasName & HasId")
        },
        test("TypeRef.optional wraps in Option") {
          val tr     = TypeRef.optional(TypeRef.String)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Option[String]")
        },
        test("TypeRef.list wraps in List") {
          val tr     = TypeRef.list(TypeRef.Int)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "List[Int]")
        },
        test("TypeRef.set wraps in Set") {
          val tr     = TypeRef.set(TypeRef.String)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Set[String]")
        },
        test("TypeRef.map wraps in Map") {
          val tr     = TypeRef.map(TypeRef.String, TypeRef.Int)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Map[String, Int]")
        },
        test("TypeRef.chunk wraps in Chunk") {
          val tr     = TypeRef.chunk(TypeRef.Long)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Chunk[Long]")
        },
        test("deeply nested generics") {
          val tr = TypeRef.map(
            TypeRef.String,
            TypeRef.list(TypeRef.optional(TypeRef.Int))
          )
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Map[String, List[Option[Int]]]")
        },
        test("TypeRef with all primitive types") {
          assertTrue(
            ScalaEmitter.emitTypeRef(TypeRef.Unit) == "Unit",
            ScalaEmitter.emitTypeRef(TypeRef.Boolean) == "Boolean",
            ScalaEmitter.emitTypeRef(TypeRef.Byte) == "Byte",
            ScalaEmitter.emitTypeRef(TypeRef.Short) == "Short",
            ScalaEmitter.emitTypeRef(TypeRef.Int) == "Int",
            ScalaEmitter.emitTypeRef(TypeRef.Long) == "Long",
            ScalaEmitter.emitTypeRef(TypeRef.Float) == "Float",
            ScalaEmitter.emitTypeRef(TypeRef.Double) == "Double",
            ScalaEmitter.emitTypeRef(TypeRef.String) == "String",
            ScalaEmitter.emitTypeRef(TypeRef.BigInt) == "BigInt",
            ScalaEmitter.emitTypeRef(TypeRef.BigDecimal) == "BigDecimal",
            ScalaEmitter.emitTypeRef(TypeRef.Any) == "Any",
            ScalaEmitter.emitTypeRef(TypeRef.Nothing) == "Nothing"
          )
        },
        test("TypeRef.of factory method") {
          val tr     = TypeRef.of("Either", TypeRef.String, TypeRef.Int)
          val result = ScalaEmitter.emitTypeRef(tr)
          assertTrue(result == "Either[String, Int]")
        }
      )
    )
}
