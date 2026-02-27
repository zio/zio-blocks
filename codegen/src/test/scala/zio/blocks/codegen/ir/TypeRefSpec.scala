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
          assert(typeRef.typeArgs)(isEmpty) &&
          assert(typeRef.isOptional)(isFalse) &&
          assert(typeRef.isNullable)(isFalse)
        },
        test("creates generic type ref with args") {
          val typeRef = TypeRef("List", List(TypeRef("Int")))
          assert(typeRef.name)(equalTo("List")) &&
          assert(typeRef.typeArgs.length)(equalTo(1)) &&
          assert(typeRef.typeArgs(0).name)(equalTo("Int"))
        },
        test("handles optional flag") {
          val typeRef = TypeRef("String", isOptional = true)
          assert(typeRef.isOptional)(isTrue)
        },
        test("handles nullable flag") {
          val typeRef = TypeRef("String", isNullable = true)
          assert(typeRef.isNullable)(isTrue)
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
      )
    )
}
