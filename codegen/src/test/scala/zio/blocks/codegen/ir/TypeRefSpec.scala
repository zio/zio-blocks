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

package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object TypeRefSpec extends ZIOSpecDefault {
  def spec =
    suite("TypeRef")(
      suite("construction")(
        test("creates simple type ref") {
          val typeRef = TypeRef("String")
          assert(typeRef.name)(equalTo("String")) &&
          assert(typeRef.typeArgs)(isEmpty)
        },
        test("creates generic type ref with args") {
          val typeRef = TypeRef("List", List(TypeRef("Int")))
          assert(typeRef.name)(equalTo("List")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("Int"))
        }
      ),
      suite("factory methods")(
        test("TypeRef.of constructs type with args") {
          val typeRef = TypeRef.of("Either", TypeRef.String, TypeRef.Int)
          assert(typeRef.name)(equalTo("Either")) &&
          assert(typeRef.typeArgs.length)(equalTo(2)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("String")) &&
          assert(typeRef.typeArgs(1).name)(equalTo("Int"))
        },
        test("TypeRef.optional wraps in Option") {
          val typeRef = TypeRef.optional(TypeRef.String)
          assert(typeRef.name)(equalTo("Option")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("String"))
        },
        test("TypeRef.list wraps in List") {
          val typeRef = TypeRef.list(TypeRef.Int)
          assert(typeRef.name)(equalTo("List")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("Int"))
        },
        test("TypeRef.set wraps in Set") {
          val typeRef = TypeRef.set(TypeRef.String)
          assert(typeRef.name)(equalTo("Set")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("String"))
        },
        test("TypeRef.map creates Map type") {
          val typeRef = TypeRef.map(TypeRef.String, TypeRef.Int)
          assert(typeRef.name)(equalTo("Map")) &&
          assert(typeRef.typeArgs.length)(equalTo(2)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("String")) &&
          assert(typeRef.typeArgs(1).name)(equalTo("Int"))
        },
        test("TypeRef.chunk wraps in Chunk") {
          val typeRef = TypeRef.chunk(TypeRef.Long)
          assert(typeRef.name)(equalTo("Chunk")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("Long"))
        }
      ),
      suite("common type references")(
        test("provides Unit type") {
          assert(TypeRef.Unit.name)(equalTo("Unit"))
        },
        test("provides Boolean type") {
          assert(TypeRef.Boolean.name)(equalTo("Boolean"))
        },
        test("provides Byte type") {
          assert(TypeRef.Byte.name)(equalTo("Byte"))
        },
        test("provides Short type") {
          assert(TypeRef.Short.name)(equalTo("Short"))
        },
        test("provides Int type") {
          assert(TypeRef.Int.name)(equalTo("Int"))
        },
        test("provides Long type") {
          assert(TypeRef.Long.name)(equalTo("Long"))
        },
        test("provides Float type") {
          assert(TypeRef.Float.name)(equalTo("Float"))
        },
        test("provides Double type") {
          assert(TypeRef.Double.name)(equalTo("Double"))
        },
        test("provides String type") {
          assert(TypeRef.String.name)(equalTo("String"))
        },
        test("provides BigInt type") {
          assert(TypeRef.BigInt.name)(equalTo("BigInt"))
        },
        test("provides BigDecimal type") {
          assert(TypeRef.BigDecimal.name)(equalTo("BigDecimal"))
        },
        test("provides Any type") {
          assert(TypeRef.Any.name)(equalTo("Any"))
        },
        test("provides Nothing type") {
          assert(TypeRef.Nothing.name)(equalTo("Nothing"))
        }
      ),
      suite("complex type compositions")(
        test("creates Option[Int]") {
          val typeRef = TypeRef.optional(TypeRef.Int)
          assert(typeRef.name)(equalTo("Option")) &&
          assert(typeRef.typeArgs(0).name)(equalTo("Int"))
        },
        test("creates List[String]") {
          val typeRef = TypeRef.list(TypeRef.String)
          assert(typeRef.name)(equalTo("List")) &&
          assert(typeRef.typeArgs(0).name)(equalTo("String"))
        },
        test("creates Map[String, Long]") {
          val typeRef = TypeRef.map(TypeRef.String, TypeRef.Long)
          assert(typeRef.name)(equalTo("Map")) &&
          assert(typeRef.typeArgs.length)(equalTo(2))
        },
        test("creates nested types") {
          val listOfStrings = TypeRef.list(TypeRef.String)
          val mapOfLists    = TypeRef.map(TypeRef.String, listOfStrings)
          assert(mapOfLists.name)(equalTo("Map")) &&
          assert(mapOfLists.typeArgs(1).name)(equalTo("List"))
        }
      ),
      suite("union and intersection")(
        test("TypeRef.union creates | type") {
          val tr = TypeRef.union(TypeRef.String, TypeRef.Int)
          assert(tr.name)(equalTo("|")) &&
          assert(tr.typeArgs.length)(equalTo(2)) &&
          assert(tr.typeArgs(0).name)(equalTo("String")) &&
          assert(tr.typeArgs(1).name)(equalTo("Int"))
        },
        test("TypeRef.intersection creates & type") {
          val tr = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
          assert(tr.name)(equalTo("&")) &&
          assert(tr.typeArgs.length)(equalTo(2))
        },
        test("TypeRef.union with three types") {
          val tr = TypeRef.union(TypeRef.String, TypeRef.Int, TypeRef.Boolean)
          assert(tr.typeArgs.length)(equalTo(3))
        }
      ),
      suite("tuple, function, wildcard")(
        test("TypeRef.tuple creates TupleN") {
          val tr = TypeRef.tuple(TypeRef.Int, TypeRef.String)
          assert(tr.name)(equalTo("Tuple2")) &&
          assert(tr.typeArgs.length)(equalTo(2))
        },
        test("TypeRef.tuple with three types") {
          val tr = TypeRef.tuple(TypeRef.Int, TypeRef.String, TypeRef.Boolean)
          assert(tr.name)(equalTo("Tuple3")) &&
          assert(tr.typeArgs.length)(equalTo(3))
        },
        test("TypeRef.function creates FunctionN") {
          val tr = TypeRef.function(List(TypeRef.Int), TypeRef.String)
          assert(tr.name)(equalTo("Function1")) &&
          assert(tr.typeArgs.length)(equalTo(2)) &&
          assert(tr.typeArgs(0).name)(equalTo("Int")) &&
          assert(tr.typeArgs(1).name)(equalTo("String"))
        },
        test("TypeRef.function with two params") {
          val tr = TypeRef.function(List(TypeRef.Int, TypeRef.String), TypeRef.Boolean)
          assert(tr.name)(equalTo("Function2")) &&
          assert(tr.typeArgs.length)(equalTo(3))
        },
        test("TypeRef.function with zero params") {
          val tr = TypeRef.function(Nil, TypeRef.Unit)
          assert(tr.name)(equalTo("Function0")) &&
          assert(tr.typeArgs.length)(equalTo(1))
        },
        test("TypeRef.Wildcard") {
          assert(TypeRef.Wildcard.name)(equalTo("_")) &&
          assert(TypeRef.Wildcard.typeArgs)(isEmpty)
        }
      )
    )
}
