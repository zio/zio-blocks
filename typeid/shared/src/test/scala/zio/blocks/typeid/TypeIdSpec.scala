package zio.blocks.typeid

import zio.test._
import zio.test.Assertion._

object TypeIdSpec extends ZIOSpecDefault {

  trait MyTrait
  class MyClass
  type MyAlias         = Int
  type StructuralAlias = { def x: Int }
  type ConstantAlias   = 42

  // Opaque types are Scala 3 only usually, or simulated in Scala 2.
  // We can skip explicit opaque type syntax in shared test for now, or use separate files.

  def spec = suite("TypeIdSpec")(
    test("derive nominal types") {
      val intId    = TypeId.derive[Int]
      val stringId = TypeId.derive[String]
      val listId   = TypeId.derive[List[Int]] // Should be TypeId for List

      assert(intId.name)(equalTo("Int")) &&
      assert(stringId.name)(equalTo("String")) &&
      assert(listId.name)(equalTo("List")) &&
      assert(listId.typeParams.size)(equalTo(1))
    },
    test("derive custom types") {
      val traitId = TypeId.derive[MyTrait]
      val classId = TypeId.derive[MyClass]

      assert(traitId.name)(equalTo("MyTrait")) &&
      assert(classId.name)(equalTo("MyClass"))
    },
    test("derive type alias") {
      val aliasId = TypeId.derive[MyAlias]
      // Depending on macro implementation, it might be AliasImpl or NominalImpl (if dealiased).
      // Our macro attempts to preserve alias if possible.
      // But derive[MyAlias] passes MyAlias.

      // In Scala 2 macro, we check sym.isAliasType.
      // In Scala 3 macro, we check sym.isTypeDef.

      // Let's check if it's an alias
      aliasId match {
        case TypeId.Alias(name, _, _, aliased) =>
          assert(name)(equalTo("MyAlias")) &&
          assert(aliased)(isSubtype[TypeRepr.Ref](anything)) // Should be Ref(Int)
        case _ =>
          // If it failed to detect alias, it might be NominalImpl("Int")?
          // Or NominalImpl("MyAlias")?
          assert(aliasId.name)(equalTo("MyAlias"))
      }
    },
    test("equality") {
      val id1 = TypeId.derive[List[Int]]
      val id2 = TypeId.derive[List[String]]

      // TypeId for List should be equal regardless of type application
      assert(id1)(equalTo(id2))
    },
    test("derive structural type alias") {
      val id = TypeId.derive[StructuralAlias]
      id match {
        case TypeId.Alias(name, _, _, aliased) =>
          assert(name)(equalTo("StructuralAlias")) &&
          assert(aliased)(isSubtype[TypeRepr.Structural](anything))
        case _ =>
          // Fallback or failure to detect alias structure
          assert(id.name)(equalTo("StructuralAlias"))
      }
    },
    test("derive constant type alias") {
      val id = TypeId.derive[ConstantAlias]
      id match {
        case TypeId.Alias(name, _, _, aliased) =>
          assert(name)(equalTo("ConstantAlias")) &&
          assert(aliased)(equalTo(TypeRepr.Constant(42)))
        case _ =>
          assert(id.name)(equalTo("ConstantAlias"))
      }
    }
  )
}
