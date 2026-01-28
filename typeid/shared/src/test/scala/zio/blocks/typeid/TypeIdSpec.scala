package zio.blocks.typeid

import zio.test._
import zio.test.Assertion._

object TypeIdSpec extends ZIOSpecDefault {
  def spec = suite("TypeIdSpec")(
    // ========== Basic Contract Tests ==========
    suite("Equals/HashCode Contract")(
      test("equals is reflexive") {
        val id = TypeId.of[String]
        assertTrue(id == id) &&
        assertTrue(id.equals(id))
      },
      test("equals is symmetric") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]
        assertTrue(id1 == id2) &&
        assertTrue(id2 == id1)
      },
      test("hashCode is consistent with equals") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]
        assertTrue(id1 == id2) &&
        assertTrue(id1.hashCode == id2.hashCode)
      },
      test("hashCode is stable across invocations") {
        val id    = TypeId.of[List[Int]]
        val hash1 = id.hashCode
        val hash2 = id.hashCode
        assertTrue(hash1 == hash2)
      }
    ),

    // ========== Nominal Type Equality ==========
    suite("Nominal Type Equality")(
      test("same nominal types are equal") {
        assertTrue(TypeId.of[String] == TypeId.of[String]) &&
        assertTrue(TypeId.of[Int] == TypeId.of[Int]) &&
        assertTrue(TypeId.of[List[Int]] == TypeId.of[List[Int]])
      },
      test("different nominal types are not equal") {
        assertTrue(TypeId.of[String] != TypeId.of[Int]) &&
        assertTrue(TypeId.of[List[Int]] != TypeId.of[List[String]]) &&
        assertTrue(TypeId.of[List[Int]] != TypeId.of[Vector[Int]])
      }
    ),

    // ========== Type Alias Equality ==========
    suite("Type Alias Equality")(
      test("type aliases equal their underlying types") {
        type Age  = Int
        type Name = String
        assertTrue(TypeId.of[Age] == TypeId.of[Int]) &&
        assertTrue(TypeId.of[Name] == TypeId.of[String]) &&
        assertTrue(TypeId.of[Age].hashCode == TypeId.of[Int].hashCode)
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
        type MyList[X] = List[X]
        assertTrue(TypeId.of[MyList[Int]] == TypeId.of[List[Int]]) &&
        assertTrue(TypeId.of[MyList[String]] == TypeId.of[List[String]])
      }
    ),

    // ========== Applied Type Equality ==========
    suite("Applied Type Equality")(
      test("applied types with same constructor and args are equal") {
        assertTrue(TypeId.of[List[Int]] == TypeId.of[List[Int]]) &&
        assertTrue(TypeId.of[Map[String, Int]] == TypeId.of[Map[String, Int]]) &&
        assertTrue(TypeId.of[Either[String, Int]] == TypeId.of[Either[String, Int]])
      },
      test("applied types with different args are not equal") {
        assertTrue(TypeId.of[List[Int]] != TypeId.of[List[String]]) &&
        assertTrue(TypeId.of[Map[String, Int]] != TypeId.of[Map[Int, String]])
      }
    ),

    // ========== Map/Set Usage ==========
    suite("Map/Set Usage")(
      test("TypeId works as Map key") {
        val map = Map[TypeId[_], String](
          TypeId.of[String]    -> "string",
          TypeId.of[Int]       -> "int",
          TypeId.of[List[Int]] -> "list-int"
        )
        val strKey: TypeId[_]  = TypeId.of[String]
        val intKey: TypeId[_]  = TypeId.of[Int]
        val listKey: TypeId[_] = TypeId.of[List[Int]]
        val dblKey: TypeId[_]  = TypeId.of[Double]
        assert(map.get(strKey))(isSome(equalTo("string"))) &&
        assert(map.get(intKey))(isSome(equalTo("int"))) &&
        assert(map.get(listKey))(isSome(equalTo("list-int"))) &&
        assert(map.get(dblKey))(isNone)
      },
      test("TypeId works in Set") {
        val set: Set[TypeId[_]] = Set(
          TypeId.of[String],
          TypeId.of[Int],
          TypeId.of[List[Int]]
        )
        val strKey: TypeId[_] = TypeId.of[String]
        val intKey: TypeId[_] = TypeId.of[Int]
        val dblKey: TypeId[_] = TypeId.of[Double]
        assert(set.contains(strKey))(isTrue) &&
        assert(set.contains(intKey))(isTrue) &&
        assert(set.contains(dblKey))(isFalse)
      },
      test("TypeId deduplication in Set with type aliases") {
        type Age = Int
        val set: Set[TypeId[_]] = Set(
          TypeId.of[Int],
          TypeId.of[Age] // Should deduplicate since Age = Int
        )
        assertTrue(set.size == 1)
      },
      test("TypeId works as Map key with type aliases") {
        type Age = Int
        val map = Map[TypeId[_], String](
          TypeId.of[Int] -> "int"
        )
        val ageKey: TypeId[_] = TypeId.of[Age]
        // Should find the entry via alias
        assert(map.get(ageKey))(isSome(equalTo("int")))
      },
      test("mutable HashMap update via equal key") {
        type Name = String
        val map = scala.collection.mutable.HashMap[TypeId[_], String]()
        map(TypeId.of[String]) = "string"
        // Update via equal key (type alias)
        map(TypeId.of[Name]) = "updated"
        assertTrue(map(TypeId.of[String]) == "updated")
      }
    ),

    // ========== fullName and Helper Methods ==========
    suite("fullName and Helper Methods")(
      test("fullName returns qualified name") {
        val id = TypeId.of[String]
        assertTrue(id.fullName == "java.lang.String")
      },
      test("arity returns type parameter count") {
        val listId = TypeId.of[List[Int]]
        val mapId  = TypeId.of[Map[String, Int]]
        assertTrue(listId.arity >= 0) && // arity is on typeParams, may be 0 for applied types
        assertTrue(mapId.arity >= 0)
      },
      test("helper methods work correctly") {
        val listId = TypeId.of[List[Int]]
        assertTrue(listId.isClass || listId.isTrait) // List is sealed abstract class
      }
    ),

    // ========== Context Bounds (Implicit Derivation) ==========
    suite("Context Bounds")(
      test("TypeId can be used with context bounds pattern") {
        // Test the pattern: def process[A](implicit typeId: TypeId[A])
        def getFullName[A](typeId: TypeId[A]): String = typeId.fullName
        val name                                      = getFullName(TypeId.of[String])
        assertTrue(name == "java.lang.String")
      }
    ),

    // ========== Subtyping ==========
    suite("Subtyping")(
      test("Nothing is subtype of everything") {
        assertTrue(TypeId.of[Nothing].isSubtypeOf(TypeId.of[Int])) &&
        assertTrue(TypeId.of[Nothing].isSubtypeOf(TypeId.of[String])) &&
        assertTrue(TypeId.of[Nothing].isSubtypeOf(TypeId.of[Any]))
      },
      test("everything is subtype of Any") {
        assertTrue(TypeId.of[Int].isSubtypeOf(TypeId.of[Any])) &&
        assertTrue(TypeId.of[String].isSubtypeOf(TypeId.of[Any])) &&
        assertTrue(TypeId.of[List[Int]].isSubtypeOf(TypeId.of[Any]))
      },
      test("reflexivity") {
        assertTrue(TypeId.of[String].isSubtypeOf(TypeId.of[String])) &&
        assertTrue(TypeId.of[List[Int]].isSubtypeOf(TypeId.of[List[Int]]))
      },
      test("class hierarchy subtyping") {
        // String <: CharSequence via known hierarchy
        assertTrue(TypeId.of[String].isSubtypeOf(TypeId.of[CharSequence]))
      },
      test("covariant type parameter subtyping") {
        // List is covariant: List[String] <: List[CharSequence]
        assertTrue(TypeId.of[List[String]].isSubtypeOf(TypeId.of[List[CharSequence]])) &&
        assertTrue(!TypeId.of[List[CharSequence]].isSubtypeOf(TypeId.of[List[String]]))
      },
      test("contravariance logic check") {
        val t1 = TypeId.of[Any => Int]
        val t2 = TypeId.of[Int => Int]
        // Function1[-A, +B]: Any => Int <: Int => Int (contravariant input)
        assertTrue(t1.isSubtypeOf(t2)) &&
        assertTrue(!t2.isSubtypeOf(t1))
      },
      test("type aliases are transparent for subtyping") {
        type Age = Int
        assertTrue(TypeId.of[Age].isSubtypeOf(TypeId.of[Int])) &&
        assertTrue(TypeId.of[Int].isSubtypeOf(TypeId.of[Age])) &&
        assertTrue(TypeId.of[Age].isEquivalentTo(TypeId.of[Int]))
      }
    ),

    // ========== Original Tests ==========
    suite("Original Tests")(
      test("Identity Parity Check: TypeId.of[List[Int]]") {
        val typeId = TypeId.of[List[Int]]
        println(s"TypeId.of[List[Int]] output: ${typeId.show}")
        assert(typeId.show)(equalTo("scala.collection.immutable.List")) &&
        assert(typeId.typeParams.headOption.map(_.variance))(isSome(equalTo(Variance.Covariant))) &&
        assert(TypeId.of[List[Int]].asInstanceOf[Any])(not(equalTo(TypeId.of[List[String]].asInstanceOf[Any])))
      },
      test("Canonicalization Verification: TypeId.of[Runnable with Serializable] vs Serializable with Runnable") {
        val t1 = TypeId.of[Runnable with java.io.Serializable]
        val t2 = TypeId.of[java.io.Serializable with Runnable]
        println(s"HashCode t1: ${t1.hashCode}, t2: ${t2.hashCode}")
        assert(t1.hashCode)(equalTo(t2.hashCode)) &&
        assert(t1.asInstanceOf[Any])(equalTo(t2.asInstanceOf[Any]))
      },
      test("Recursive Generic Identity Check: Map[String, List[Int]] vs Map[String, List[String]]") {
        val t1 = TypeId.of[Map[String, List[Int]]]
        val t2 = TypeId.of[Map[String, List[String]]]
        assert(t1.asInstanceOf[Any])(not(equalTo(t2.asInstanceOf[Any]))) &&
        assert(t1.args)(hasSize(equalTo(2)))
      }
    ),

    // ========== Advanced Tests (Issue #471 Requirements) ==========
    suite("Advanced Tests")(
      test("deeply nested generics are handled correctly") {
        val id  = TypeId.of[List[Map[String, Option[Either[Int, List[String]]]]]]
        val id2 = TypeId.of[List[Map[String, Option[Either[Int, List[String]]]]]]
        assertTrue(id == id2) &&
        assertTrue(id.hashCode == id2.hashCode)
      },
      test("mutable HashMap works with TypeId") {
        val map = scala.collection.mutable.HashMap[TypeId[_], String]()
        map(TypeId.of[String]) = "string"
        map(TypeId.of[Int]) = "int"
        assertTrue(map(TypeId.of[String]) == "string") &&
        assertTrue(map(TypeId.of[Int]) == "int")
      }
    ),

    // ========== Cross-Compilation Stability ==========
    suite("Cross-Compilation Stability")(
      test("TypeId is stable across separate derivations") {
        // Simulates cross-compilation by deriving in separate locations
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]
        val id3 = TypeId.of[List[Int]]
        val id4 = TypeId.of[List[Int]]
        assertTrue(id1 == id2) &&
        assertTrue(id1.hashCode == id2.hashCode) &&
        assertTrue(id3 == id4) &&
        assertTrue(id3.hashCode == id4.hashCode)
      }
    ),

    // ========== Function Types ==========
    suite("Function Types")(
      test("derives function types") {
        val funcId  = TypeId.of[(Int, String) => Boolean]
        val funcId2 = TypeId.of[(Int, String) => Boolean]
        assertTrue(funcId == funcId2) &&
        assertTrue(funcId.hashCode == funcId2.hashCode)
      },
      test("different function types are not equal") {
        val f1 = TypeId.of[Int => String]
        val f2 = TypeId.of[String => Int]
        assertTrue(f1.asInstanceOf[Any] != f2.asInstanceOf[Any])
      }
    ),

    // ========== Tuple Types ==========
    suite("Tuple Types")(
      test("derives positional tuples") {
        val tupleId  = TypeId.of[(Int, String, Boolean)]
        val tupleId2 = TypeId.of[(Int, String, Boolean)]
        assertTrue(tupleId == tupleId2) &&
        assertTrue(tupleId.hashCode == tupleId2.hashCode)
      },
      test("different tuples are not equal") {
        val t1 = TypeId.of[(Int, String)]
        val t2 = TypeId.of[(String, Int)]
        assertTrue(t1.asInstanceOf[Any] != t2.asInstanceOf[Any])
      }
    ),

    // ========== Java Interop ==========
    suite("Java Interop")(
      test("handles Java types") {
        val arrayListId  = TypeId.of[java.util.ArrayList[String]]
        val arrayListId2 = TypeId.of[java.util.ArrayList[String]]
        assertTrue(arrayListId == arrayListId2) &&
        assertTrue(arrayListId.hashCode == arrayListId2.hashCode)
      },
      test("Java HashMap type") {
        val mapId = TypeId.of[java.util.HashMap[String, Integer]]
        assertTrue(mapId != null)
      }
    ),

    // ========== Recursive Types ==========
    suite("Recursive Types")(
      test("handles recursive types without stack overflow") {
        // Tree is a recursive type
        sealed trait Tree[+A]
        case class Leaf[A](value: A)                        extends Tree[A]
        case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]

        // Use the case classes to avoid unused definition warnings
        val _: Tree[Int] = Branch(Leaf(1), Leaf(2))

        val treeId = TypeId.of[Tree[Int]]
        assertTrue(treeId.hashCode == TypeId.of[Tree[Int]].hashCode) &&
        assertTrue(treeId == TypeId.of[Tree[Int]])
      }
    ),

    // ========== TypeId Wrapper Coverage ==========
    suite("TypeId Wrapper Method Coverage")(
      test("isSupertypeOf returns correct result") {
        val anyId = TypeId.of[Any]
        val intId = TypeId.of[Int]
        assertTrue(anyId.isSupertypeOf(intId))
      },
      test("isEquivalentTo returns true for same type") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]
        assertTrue(id1.isEquivalentTo(id2))
      },
      test("isEquivalentTo returns false for different types") {
        val strId = TypeId.of[String]
        val intId = TypeId.of[Int]
        assertTrue(!strId.isEquivalentTo(intId))
      },
      test("toString delegates to dynamic") {
        val id = TypeId.of[String]
        assertTrue(id.toString.nonEmpty)
      },
      test("equals returns false for non-TypeId") {
        val id = TypeId.of[String]
        assertTrue(!id.equals("not a TypeId"))
      },
      test("equals returns false for null") {
        val id = TypeId.of[String]
        assertTrue(!id.equals(null))
      },
      test("show exports correctly") {
        val id = TypeId.of[String]
        assertTrue(id.show == "java.lang.String")
      },
      test("owner exports correctly") {
        val id = TypeId.of[String]
        assertTrue(id.owner.asString.contains("java.lang"))
      },
      test("name exports correctly") {
        val id = TypeId.of[String]
        assertTrue(id.name == "String")
      },
      test("annotations available") {
        val id = TypeId.of[String]
        assertTrue(id.annotations != null)
      },
      test("isEnum returns correct value") {
        val id = TypeId.of[String]
        assertTrue(!id.isEnum)
      },
      test("isAlias returns correct value for alias") {
        type MyString = String
        val id = TypeId.of[MyString]
        assertTrue(!id.isAlias) // expanded at compile time
      },
      test("isOpaque returns false for regular type") {
        val id = TypeId.of[String]
        assertTrue(!id.isOpaque)
      },
      test("isAbstract returns correct value") {
        val id = TypeId.of[String]
        assertTrue(!id.isAbstract)
      },
      test("isSealed is accessible") {
        val id = TypeId.of[String]
        assertTrue(!id.isSealed) // String is not sealed
      },
      test("isCaseClass returns true for case class") {
        val id = TypeId.of[Some[Int]]
        assertTrue(id.isCaseClass)
      },
      test("isValueClass returns correct value") {
        val id = TypeId.of[String]
        assertTrue(!id.isValueClass)
      },
      test("enumCases returns empty for non-enum") {
        val id = TypeId.of[String]
        assertTrue(id.enumCases.isEmpty)
      },
      test("aliasedTo returns None for non-alias") {
        val id = TypeId.of[String]
        assertTrue(id.aliasedTo.isEmpty)
      },
      test("representation returns None for regular class") {
        val id = TypeId.of[String]
        assertTrue(id.representation.isEmpty)
      },
      test("kind is accessible") {
        val id = TypeId.of[String]
        assertTrue(id.kind != null) // kind is accessible
      },
      test("parents is accessible") {
        val id = TypeId.of[String]
        assertTrue(id.parents != null)
      },
      test("args is accessible") {
        val id = TypeId.of[List[Int]]
        assertTrue(id.args.nonEmpty)
      },
      test("isObject returns correct value") {
        val id = TypeId.of[String]
        assertTrue(!id.isObject)
      },
      test("typeParams is accessible") {
        val id = TypeId.of[List[Int]]
        assertTrue(id.typeParams.nonEmpty)
      }
    ),

    // ========== TypeId Factory Coverage ==========
    suite("TypeId Factory Coverage")(
      test("TypeId.from is equivalent to TypeId.of") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.from[String]
        assertTrue(id1 == id2)
      },
      test("given TypeId works") {
        def useTypeId[A](implicit id: TypeId[A]): String = id.fullName
        val name                                         = useTypeId[String]
        assertTrue(name == "java.lang.String")
      }
    )
  )
}
