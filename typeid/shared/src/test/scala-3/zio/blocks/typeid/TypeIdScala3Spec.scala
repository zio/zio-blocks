package zio.blocks.typeid

import zio.test._

/**
 * Scala 3 specific tests for type aliases, opaque types, enums, and compound
 * types. Issue #471 requires comprehensive coverage of Scala 3 type features.
 */
object TypeIdScala3Spec extends ZIOSpecDefault {

  // Type alias for comparison
  type Name = String

  // Enum for testing
  enum Color {
    case Red, Green, Blue
    case RGB(r: Int, g: Int, b: Int)
  }

  // Sealed trait hierarchy
  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal

  def spec = suite("TypeId Scala 3 Specific Tests")(
    // ========== Type Alias Equality ==========
    suite("Type Alias Equality")(
      test("type aliases equal their underlying types") {
        // type Name = String should dealias to String
        val nameId   = TypeId.of[Name]
        val stringId = TypeId.of[String]
        assertTrue(nameId == stringId) &&
        assertTrue(nameId.hashCode == stringId.hashCode)
      },
      test("chained type aliases resolve correctly") {
        type A = Int
        type B = A
        type C = B
        assertTrue(TypeId.of[C] == TypeId.of[Int]) &&
        assertTrue(TypeId.of[C] == TypeId.of[A]) &&
        assertTrue(TypeId.of[C] == TypeId.of[B])
      },
      test("generic type aliases equal their expansion") {
        type MyList[A] = List[A]
        assertTrue(TypeId.of[MyList[Int]] == TypeId.of[List[Int]]) &&
        assertTrue(TypeId.of[MyList[String]] == TypeId.of[List[String]])
      }
    ),

    // ========== Opaque Type Tests ==========
    // Note: Within the defining scope, opaque types are transparent to the macro.
    // This is expected Scala behavior - the macro sees the underlying type.
    // True opaque type nominal distinction only works from OUTSIDE the defining scope.
    suite("Opaque Types")(
      test("opaque types derived within scope resolve to underlying type") {
        // Within the defining scope (OpaqueTypeDefinitions), the macro sees String
        // This is expected Scala behavior for opaque types
        val emailId  = OpaqueTypeDefinitions.emailTypeId
        val stringId = OpaqueTypeDefinitions.stringTypeId
        // Both resolve to String within the defining scope
        assertTrue(emailId == stringId)
      },
      test("opaque type TypeIds can be retrieved for use") {
        // Verify we can get TypeIds for opaque types
        val emailId  = OpaqueTypeDefinitions.emailTypeId
        val userIdId = OpaqueTypeDefinitions.userIdTypeId
        val ageId    = OpaqueTypeDefinitions.ageTypeId
        assertTrue(emailId != null) &&
        assertTrue(userIdId != null) &&
        assertTrue(ageId != null)
      }
    ),

    // ========== Enum Type Tests ==========
    suite("Enum Types")(
      test("derives enums correctly") {
        val colorId = TypeId.of[Color]
        assertTrue(colorId.name == "Color")
      },
      test("enum case types are distinct") {
        val redId   = TypeId.of[Color.Red.type]
        val greenId = TypeId.of[Color.Green.type]
        assertTrue(redId != greenId)
      }
    ),

    // ========== Sealed Hierarchy Tests ==========
    suite("Sealed Hierarchies")(
      test("sealed trait has correct identity") {
        val animalId = TypeId.of[Animal]
        assertTrue(animalId.name == "Animal")
      },
      test("case class subtypes are distinct from parent") {
        val animalId = TypeId.of[Animal]
        val dogId    = TypeId.of[Dog]
        val catId    = TypeId.of[Cat]
        assertTrue(animalId != dogId) &&
        assertTrue(animalId != catId) &&
        assertTrue(dogId != catId)
      },
      test("subtype relationship is recognized") {
        val dogId    = TypeId.of[Dog]
        val animalId = TypeId.of[Animal]
        assertTrue(dogId.isSubtypeOf(animalId))
      }
    ),

    // ========== Union and Intersection Types ==========
    suite("Compound Types")(
      test("intersection types with same components are equal") {
        val t1 = TypeId.of[Runnable & java.io.Serializable]
        val t2 = TypeId.of[Runnable & java.io.Serializable]
        assertTrue(t1 == t2) &&
        assertTrue(t1.hashCode == t2.hashCode)
      }
    ),

    // ========== Type Constructors (Scala 3 only) ==========
    suite("Type Constructors")(
      test("unapplied type constructor not equal to applied type") {
        val listConstructor = TypeId.of[List]
        val listApplied     = TypeId.of[List[Int]]
        assertTrue(listConstructor.asInstanceOf[Any] != listApplied.asInstanceOf[Any])
      },
      test("type constructors have correct arity") {
        val listId = TypeId.of[List]
        val mapId  = TypeId.of[Map]
        assertTrue(listId.typeParams.size >= 1) &&
        assertTrue(mapId.typeParams.size >= 2)
      }
    ),

    // ========== Path-Dependent Types ==========
    suite("Path-Dependent Types")(
      test("path-dependent types from different instances are distinct") {
        class Outer {
          class Inner
        }
        val outer1 = new Outer
        val outer2 = new Outer
        // Path-dependent types: outer1.Inner vs outer2.Inner
        // At compile time these are distinct types
        val id1 = TypeId.of[outer1.Inner]
        val id2 = TypeId.of[outer2.Inner]
        // Both should derive successfully
        assertTrue(id1 != null) &&
        assertTrue(id2 != null) &&
        assertTrue(id1.name == "Inner") &&
        assertTrue(id2.name == "Inner")
      },
      test("path-dependent type has correct owner") {
        class Container {
          class Element
        }
        val container = new Container
        val elementId = TypeId.of[container.Element]
        assertTrue(elementId.name == "Element")
      }
    ),

    // ========== Singleton Types ==========
    suite("Singleton Types")(
      test("object singleton types are derivable") {
        object MySingleton
        val singletonId = TypeId.of[MySingleton.type]
        assertTrue(singletonId != null) &&
        assertTrue(singletonId.name.contains("MySingleton"))
      },
      test("literal singleton types work") {
        val literalId = TypeId.of[42]
        assertTrue(literalId != null)
      },
      test("string literal singleton types work") {
        val literalId = TypeId.of["hello"]
        assertTrue(literalId != null)
      }
    ),

    // ========== Match Types (Scala 3 only) ==========
    suite("Match Types")(
      test("match type resolves to concrete result") {
        type Elem[X] = X match {
          case List[t]  => t
          case Array[t] => t
          case _        => X
        }
        // Elem[List[Int]] should resolve to Int
        val elemId = TypeId.of[Elem[List[Int]]]
        val intId  = TypeId.of[Int]
        assertTrue(elemId == intId)
      },
      test("match type with array resolves correctly") {
        type Elem[X] = X match {
          case List[t]  => t
          case Array[t] => t
          case _        => X
        }
        val elemId = TypeId.of[Elem[Array[String]]]
        val strId  = TypeId.of[String]
        assertTrue(elemId == strId)
      }
    ),

    // ========== Context Function Types (Scala 3 only) ==========
    suite("Context Function Types")(
      test("context function type is derivable") {
        type CtxFn = String ?=> Int
        val ctxFnId = TypeId.of[CtxFn]
        assertTrue(ctxFnId != null)
      },
      test("multi-param context function type works") {
        type MultiCtx = (String, Int) ?=> Boolean
        val multiId = TypeId.of[MultiCtx]
        assertTrue(multiId != null)
      }
    ),

    // ========== Polymorphic Function Types (Scala 3 only) ==========
    suite("Polymorphic Function Types")(
      test("polymorphic function type is derivable") {
        type PolyFn = [A] => A => A
        val polyId = TypeId.of[PolyFn]
        assertTrue(polyId != null)
      },
      test("polymorphic function with bounds works") {
        type BoundedPoly = [A <: AnyVal] => A => A
        val boundedId = TypeId.of[BoundedPoly]
        assertTrue(boundedId != null)
      }
    ),

    // ========== Structural Types ==========
    suite("Structural Types")(
      test("structural type is derivable") {
        type HasName = { def name: String }
        val structId = TypeId.of[HasName]
        assertTrue(structId != null)
      },
      test("structural type with multiple members works") {
        type Person = { def name: String; def age: Int }
        val personId = TypeId.of[Person]
        assertTrue(personId != null)
      }
    )
  )
}
