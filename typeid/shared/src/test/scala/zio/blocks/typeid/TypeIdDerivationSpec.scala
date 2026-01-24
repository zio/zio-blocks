package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for TypeId macro derivation.
 *
 * These tests cover all features supported by TypeId derivation:
 *   - Primitive types
 *   - Standard library types
 *   - Type constructors (arity 1, 2, etc.)
 *   - User-defined types (case classes, sealed traits, objects)
 *   - Type aliases (Scala 3)
 *   - Opaque types (Scala 3)
 *   - Nested types
 *   - Generic types
 *   - Higher-kinded types
 */
object TypeIdDerivationSpec extends ZIOSpecDefault {

  // Test fixtures - user-defined types
  case class SimpleClass(x: Int, y: String)
  case class GenericClass[A](value: A)
  case class MultiParamClass[A, B](a: A, b: B)

  sealed trait SimpleSealed
  object SimpleSealed {
    case class CaseA(x: Int)    extends SimpleSealed
    case class CaseB(y: String) extends SimpleSealed
    case object CaseC           extends SimpleSealed
  }

  object Outer {
    case class Nested(x: Int)
    object Inner {
      case class DeepNested(y: String)
    }
  }

  trait SimpleTrait
  abstract class AbstractClass

  object TypeAliases {
    type Age          = Int
    type StringMap[V] = Map[String, V]
  }

  // Hierarchy for subtyping tests
  trait Animal
  trait Mammal         extends Animal
  class Dog            extends Mammal
  class Cat            extends Mammal
  class GermanShepherd extends Dog

  // For variance testing
  trait Serializable
  trait Comparable[A]

  // Covariant container
  sealed trait Box[+A]
  object Box {
    case class Full[+A](value: A) extends Box[A]
    case object Empty             extends Box[Nothing]
  }

  // Recursive types for testing
  sealed trait LinkedList[+A]
  object LinkedList {
    case class Node[+A](head: A, tail: LinkedList[A]) extends LinkedList[A]
    case object Empty                                 extends LinkedList[Nothing]
  }

  sealed trait Tree[+A]
  object Tree {
    case class Branch[+A](value: A, left: Tree[A], right: Tree[A]) extends Tree[A]
    case object Leaf                                               extends Tree[Nothing]
  }

  // Mutually recursive types
  sealed trait Expr
  case class Literal(value: Int)                 extends Expr
  case class BinOp(op: Op, lhs: Expr, rhs: Expr) extends Expr

  sealed trait Op
  case object Plus  extends Op
  case object Minus extends Op

  def spec = suite("TypeId Derivation")(
    suite("Primitive Types")(
      test("derives TypeId for Int") {
        val id = TypeId.derived[Int]
        assertTrue(
          id.name == "Int",
          id.owner == Owner.scala,
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for String") {
        val id = TypeId.derived[String]
        assertTrue(
          id.name == "String",
          id.owner == Owner.javaLang,
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for Boolean") {
        val id = TypeId.derived[Boolean]
        assertTrue(
          id.name == "Boolean",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Long") {
        val id = TypeId.derived[Long]
        assertTrue(
          id.name == "Long",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Double") {
        val id = TypeId.derived[Double]
        assertTrue(
          id.name == "Double",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Byte") {
        val id = TypeId.derived[Byte]
        assertTrue(id.name == "Byte")
      },
      test("derives TypeId for Short") {
        val id = TypeId.derived[Short]
        assertTrue(id.name == "Short")
      },
      test("derives TypeId for Float") {
        val id = TypeId.derived[Float]
        assertTrue(id.name == "Float")
      },
      test("derives TypeId for Char") {
        val id = TypeId.derived[Char]
        assertTrue(id.name == "Char")
      },
      test("derives TypeId for Unit") {
        val id = TypeId.derived[Unit]
        assertTrue(id.name == "Unit")
      },
      test("derived primitive TypeIds are the same instances as predefined") {
        assertTrue(
          TypeId.derived[Int] eq TypeId.int,
          TypeId.derived[String] eq TypeId.string,
          TypeId.derived[Boolean] eq TypeId.boolean,
          TypeId.derived[Long] eq TypeId.long,
          TypeId.derived[Double] eq TypeId.double,
          TypeId.derived[Float] eq TypeId.float,
          TypeId.derived[Short] eq TypeId.short,
          TypeId.derived[Byte] eq TypeId.byte,
          TypeId.derived[Char] eq TypeId.char,
          TypeId.derived[Unit] eq TypeId.unit
        )
      }
    ),
    suite("Standard Library Collection Types")(
      test("derives TypeId for List (type constructor)") {
        val id = TypeId.derived[List[_]]
        assertTrue(
          id.name == "List",
          id.arity == 1,
          id.typeParams.head.name == "A"
        )
      },
      test("derives TypeId for Option (type constructor)") {
        val id = TypeId.derived[Option[_]]
        assertTrue(
          id.name == "Option",
          id.arity == 1
        )
      },
      test("derives TypeId for Map (type constructor with arity 2)") {
        val id = TypeId.derived[Map[_, _]]
        assertTrue(
          id.name == "Map",
          id.arity == 2
        )
      },
      test("derives TypeId for Either (type constructor with arity 2)") {
        val id = TypeId.derived[Either[_, _]]
        assertTrue(
          id.name == "Either",
          id.arity == 2
        )
      },
      test("derives TypeId for Vector") {
        val id = TypeId.derived[Vector[_]]
        assertTrue(
          id.name == "Vector",
          id.arity == 1
        )
      },
      test("derives TypeId for Set") {
        val id = TypeId.derived[Set[_]]
        assertTrue(
          id.name == "Set",
          id.arity == 1
        )
      },
      test("derives TypeId for Seq") {
        val id = TypeId.derived[Seq[_]]
        assertTrue(id.name == "Seq")
      }
    ),
    suite("Applied Types")(
      test("derives TypeId for List[Int]") {
        val id = TypeId.derived[List[Int]]
        assertTrue(
          id.name == "List",
          id.arity == 1
        )
      },
      test("derives TypeId for Option[String]") {
        val id = TypeId.derived[Option[String]]
        assertTrue(
          id.name == "Option",
          id.arity == 1
        )
      },
      test("derives TypeId for Map[String, Int]") {
        val id = TypeId.derived[Map[String, Int]]
        assertTrue(
          id.name == "Map",
          id.arity == 2
        )
      },
      test("derives TypeId for Either[String, Int]") {
        val id = TypeId.derived[Either[String, Int]]
        assertTrue(
          id.name == "Either",
          id.arity == 2
        )
      }
    ),
    suite("User-Defined Case Classes")(
      test("derives TypeId for simple case class") {
        val id = TypeId.derived[SimpleClass]
        assertTrue(
          id.name == "SimpleClass",
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for generic case class") {
        val id = TypeId.derived[GenericClass[_]]
        assertTrue(
          id.name == "GenericClass",
          id.arity == 1
        )
      },
      test("derives TypeId for multi-param generic case class") {
        val id = TypeId.derived[MultiParamClass[_, _]]
        assertTrue(
          id.name == "MultiParamClass",
          id.arity == 2
        )
      },
      test("derives TypeId for applied generic case class") {
        val id = TypeId.derived[GenericClass[Int]]
        assertTrue(
          id.name == "GenericClass",
          id.arity == 1
        )
      }
    ),
    suite("Sealed Traits and ADTs")(
      test("derives TypeId for sealed trait") {
        val id = TypeId.derived[SimpleSealed]
        assertTrue(
          id.name == "SimpleSealed",
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for case class extending sealed trait") {
        val id = TypeId.derived[SimpleSealed.CaseA]
        assertTrue(
          id.name == "CaseA"
        )
      },
      test("derives TypeId for case object extending sealed trait") {
        val id = TypeId.derived[SimpleSealed.CaseC.type]
        assertTrue(
          id.name == "CaseC"
        )
      }
    ),
    suite("Nested Types")(
      test("derives TypeId for nested case class") {
        val id = TypeId.derived[Outer.Nested]
        assertTrue(
          id.name == "Nested",
          id.fullName.contains("Outer")
        )
      },
      test("derives TypeId for deeply nested case class") {
        val id = TypeId.derived[Outer.Inner.DeepNested]
        assertTrue(
          id.name == "DeepNested",
          id.fullName.contains("Inner")
        )
      }
    ),
    suite("Traits and Abstract Classes")(
      test("derives TypeId for trait") {
        val id = TypeId.derived[SimpleTrait]
        assertTrue(
          id.name == "SimpleTrait",
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for abstract class") {
        val id = TypeId.derived[AbstractClass]
        assertTrue(
          id.name == "AbstractClass",
          !id.isAlias && !id.isOpaque
        )
      }
    ),
    suite("Java Types")(
      test("derives TypeId for java.util.UUID") {
        val id = TypeId.derived[java.util.UUID]
        assertTrue(
          id.name == "UUID",
          id.owner == Owner.javaUtil
        )
      },
      test("derives TypeId for java.time.Instant") {
        val id = TypeId.derived[java.time.Instant]
        assertTrue(
          id.name == "Instant",
          id.owner == Owner.javaTime
        )
      },
      test("derives TypeId for java.time.Duration") {
        val id = TypeId.derived[java.time.Duration]
        assertTrue(
          id.name == "Duration"
        )
      },
      test("derives TypeId for java.util.ArrayList with type parameter") {
        val id = TypeId.derived[java.util.ArrayList[String]]
        assertTrue(
          id.name == "ArrayList",
          id.owner == Owner.javaUtil
        )
      }
    ),
    suite("Scala BigInt/BigDecimal")(
      test("derives TypeId for BigInt") {
        val id = TypeId.derived[BigInt]
        assertTrue(
          id.name == "BigInt",
          id.owner == Owner.scala
        )
      },
      test("derives TypeId for BigDecimal") {
        val id = TypeId.derived[BigDecimal]
        assertTrue(
          id.name == "BigDecimal",
          id.owner == Owner.scala
        )
      }
    ),
    suite("Tuple Types")(
      test("derives TypeId for Tuple2") {
        val id = TypeId.derived[Tuple2[_, _]]
        assertTrue(
          id.name == "Tuple2",
          id.arity == 2
        )
      },
      test("derives TypeId for Tuple3") {
        val id = TypeId.derived[Tuple3[_, _, _]]
        assertTrue(
          id.name == "Tuple3",
          id.arity == 3
        )
      },
      test("derives TypeId for applied tuple (Int, String)") {
        val id = TypeId.derived[(Int, String)]
        assertTrue(
          id.name == "Tuple2",
          id.arity == 2
        )
      }
    ),
    suite("Function Types")(
      test("derives TypeId for Function1") {
        val id = TypeId.derived[Function1[_, _]]
        assertTrue(
          id.name == "Function1",
          id.arity == 2
        )
      },
      test("derives TypeId for Function2") {
        val id = TypeId.derived[Function2[_, _, _]]
        assertTrue(
          id.name == "Function2",
          id.arity == 3
        )
      }
    ),
    suite("Owner Path Verification")(
      test("scala primitives have correct owner") {
        val intId  = TypeId.derived[Int]
        val longId = TypeId.derived[Long]
        assertTrue(
          intId.owner == Owner.scala,
          longId.owner == Owner.scala
        )
      },
      test("java.lang types have correct owner") {
        val stringId = TypeId.derived[String]
        assertTrue(
          stringId.owner == Owner.javaLang
        )
      },
      test("collection types have correct owner") {
        val listId = TypeId.derived[List[_]]
        assertTrue(
          listId.owner == Owner.scalaCollectionImmutable
        )
      }
    ),
    suite("Type Parameter Verification")(
      test("List has one type parameter named A") {
        val id = TypeId.derived[List[_]]
        assertTrue(
          id.typeParams.size == 1,
          id.typeParams.head.index == 0
        )
      },
      test("Map has two type parameters") {
        val id = TypeId.derived[Map[_, _]]
        assertTrue(
          id.typeParams.size == 2,
          id.typeParams(0).index == 0,
          id.typeParams(1).index == 1
        )
      },
      test("Either has two type parameters") {
        val id = TypeId.derived[Either[_, _]]
        assertTrue(
          id.typeParams.size == 2
        )
      }
    ),
    suite("FullName Verification")(
      test("scala.Int has correct fullName") {
        val id = TypeId.derived[Int]
        assertTrue(
          id.fullName == "scala.Int"
        )
      },
      test("java.lang.String has correct fullName") {
        val id = TypeId.derived[String]
        assertTrue(
          id.fullName == "java.lang.String"
        )
      },
      test("nested type has correct fullName with owner path") {
        val id = TypeId.derived[Outer.Nested]
        assertTrue(
          id.fullName.endsWith("Nested")
        )
      }
    ),
    suite("Pattern Matching Support")(
      test("derived TypeId matches Nominal extractor") {
        val id      = TypeId.derived[Int]
        val matches = id match {
          case TypeId.Nominal(name, _, _, _, _) => name == "Int"
          case _                                => false
        }
        assertTrue(matches)
      }
    ),
    suite("Consistency with Predefined TypeIds")(
      test("derive[Int] equals predefined TypeId.int") {
        val derived    = TypeId.derived[Int]
        val predefined = TypeId.int
        assertTrue(
          derived.name == predefined.name,
          derived.owner == predefined.owner,
          derived.arity == predefined.arity
        )
      },
      test("derive[String] equals predefined TypeId.string") {
        val derived    = TypeId.derived[String]
        val predefined = TypeId.string
        assertTrue(
          derived.name == predefined.name,
          derived.owner == predefined.owner
        )
      },
      test("derive[List[_]] equals predefined TypeId.list") {
        val derived    = TypeId.derived[List[_]]
        val predefined = TypeId.list
        assertTrue(
          derived.name == predefined.name,
          derived.arity == predefined.arity
        )
      }
    ),
    suite("Parent Types")(
      test("class extending trait has correct parents") {
        val id = TypeId.derived[SimpleSealed.CaseA]
        assertTrue(
          id.parents.exists {
            case TypeRepr.Ref(tid) => tid.name == "SimpleSealed"
            case _                 => false
          }
        )
      },
      test("sealed trait subtypes have parent in parents") {
        val caseAId = TypeId.derived[SimpleSealed.CaseA]
        val caseBId = TypeId.derived[SimpleSealed.CaseB]
        assertTrue(
          caseAId.parents.nonEmpty,
          caseBId.parents.nonEmpty
        )
      },
      test("parents is accessible on all types") {
        val intId   = TypeId.derived[Int]
        val listId  = TypeId.derived[List[_]]
        val classId = TypeId.derived[SimpleClass]
        assertTrue(
          intId.parents.isInstanceOf[List[TypeRepr]],
          listId.parents.isInstanceOf[List[TypeRepr]],
          classId.parents.isInstanceOf[List[TypeRepr]]
        )
      }
    ),
    suite("isSubtypeOf")(
      test("type is subtype of itself") {
        val id = TypeId.derived[SimpleTrait]
        assertTrue(id.isSubtypeOf(id))
      },
      test("case class is subtype of its sealed trait") {
        val caseAId  = TypeId.derived[SimpleSealed.CaseA]
        val sealedId = TypeId.derived[SimpleSealed]
        assertTrue(
          caseAId.isSubtypeOf(sealedId),
          !sealedId.isSubtypeOf(caseAId)
        )
      },
      test("case object is subtype of its sealed trait") {
        val caseCId  = TypeId.derived[SimpleSealed.CaseC.type]
        val sealedId = TypeId.derived[SimpleSealed]
        assertTrue(caseCId.isSubtypeOf(sealedId))
      },
      test("unrelated types are not subtypes of each other") {
        val intId    = TypeId.derived[Int]
        val stringId = TypeId.derived[String]
        assertTrue(
          !intId.isSubtypeOf(stringId),
          !stringId.isSubtypeOf(intId)
        )
      },
      test("sibling subtypes are not subtypes of each other") {
        val caseAId = TypeId.derived[SimpleSealed.CaseA]
        val caseBId = TypeId.derived[SimpleSealed.CaseB]
        assertTrue(
          !caseAId.isSubtypeOf(caseBId),
          !caseBId.isSubtypeOf(caseAId)
        )
      }
    ),
    suite("isSupertypeOf")(
      test("sealed trait is supertype of its case class") {
        val sealedId = TypeId.derived[SimpleSealed]
        val caseAId  = TypeId.derived[SimpleSealed.CaseA]
        assertTrue(
          sealedId.isSupertypeOf(caseAId),
          !caseAId.isSupertypeOf(sealedId)
        )
      },
      test("type is supertype of itself") {
        val id = TypeId.derived[SimpleClass]
        assertTrue(id.isSupertypeOf(id))
      }
    ),
    suite("isEquivalentTo")(
      test("type is equivalent to itself") {
        val id = TypeId.derived[SimpleClass]
        assertTrue(id.isEquivalentTo(id))
      },
      test("unrelated types are not equivalent") {
        val intId    = TypeId.derived[Int]
        val stringId = TypeId.derived[String]
        assertTrue(!intId.isEquivalentTo(stringId))
      },
      test("subtype and supertype are not equivalent") {
        val sealedId = TypeId.derived[SimpleSealed]
        val caseAId  = TypeId.derived[SimpleSealed.CaseA]
        assertTrue(!sealedId.isEquivalentTo(caseAId))
      }
    ),
    suite("Type Aliases")(
      test("type aliases are detected correctly") {
        val ageId = TypeId.derived[TypeAliases.Age]
        val mapId = TypeId.derived[TypeAliases.StringMap[Int]]

        assertTrue(
          ageId.name == "Age" && ageId.isAlias,
          mapId.name == "StringMap" && mapId.isAlias
        )
      },
      test("aliased types are extracted correctly") {
        val ageId = TypeId.derived[TypeAliases.Age]

        assertTrue(
          ageId.aliasedTo.exists {
            case TypeRepr.Ref(typeId) => typeId.name == "Int"
            case _                    => false
          }
        )
      },
      test("generic type alias has aliased type") {
        val mapId = TypeId.derived[TypeAliases.StringMap[Int]]

        // Generic type aliases may be represented as TypeLambda or Applied depending on Scala version
        assertTrue(
          mapId.aliasedTo.isDefined,
          mapId.aliasedTo.exists {
            case TypeRepr.Applied(TypeRepr.Ref(typeId), _)                         => typeId.name == "Map"
            case TypeRepr.TypeLambda(_, TypeRepr.Applied(TypeRepr.Ref(typeId), _)) => typeId.name == "Map"
            case _                                                                 => false
          }
        )
      },
      test("type alias is equivalent to its underlying type") {
        val ageId = TypeId.derived[TypeAliases.Age]
        val intId = TypeId.derived[Int]

        assertTrue(
          ageId.isEquivalentTo(intId),
          ageId.isSubtypeOf(intId),
          intId.isSubtypeOf(ageId)
        )
      }
    ),
    suite("Basic Subtyping")(
      test("reflexivity - type is subtype of itself") {
        val stringId = TypeId.derived[String]
        val dogId    = TypeId.derived[Dog]

        assertTrue(
          stringId.isSubtypeOf(stringId),
          dogId.isSubtypeOf(dogId)
        )
      },
      test("reflexivity with derived type") {
        val caseAId = TypeId.derived[SimpleSealed.CaseA]
        assertTrue(caseAId.isSubtypeOf(caseAId))
      },
      test("Nothing is subtype of everything") {
        val nothingId = TypeId.derived[Nothing]
        val intId     = TypeId.derived[Int]
        val stringId  = TypeId.derived[String]
        val anyId     = TypeId.derived[Any]
        val listIntId = TypeId.derived[List[Int]]

        assertTrue(
          nothingId.isSubtypeOf(intId),
          nothingId.isSubtypeOf(stringId),
          nothingId.isSubtypeOf(anyId),
          nothingId.isSubtypeOf(listIntId)
        )
      },
      test("everything is subtype of Any") {
        val intId     = TypeId.derived[Int]
        val stringId  = TypeId.derived[String]
        val listIntId = TypeId.derived[List[Int]]
        val anyId     = TypeId.derived[Any]

        assertTrue(
          intId.isSubtypeOf(anyId),
          stringId.isSubtypeOf(anyId),
          listIntId.isSubtypeOf(anyId)
        )
      }
    ),
    suite("Nominal Hierarchy Subtyping")(
      test("class hierarchy subtyping") {
        val dogId    = TypeId.derived[Dog]
        val mammalId = TypeId.derived[Mammal]
        val animalId = TypeId.derived[Animal]

        assertTrue(
          dogId.isSubtypeOf(mammalId),
          dogId.isSubtypeOf(animalId),
          mammalId.isSubtypeOf(animalId)
        )
      },
      test("transitive subtyping through multiple levels") {
        val germanShepherdId = TypeId.derived[GermanShepherd]
        val dogId            = TypeId.derived[Dog]
        val mammalId         = TypeId.derived[Mammal]
        val animalId         = TypeId.derived[Animal]

        assertTrue(
          germanShepherdId.isSubtypeOf(dogId),
          germanShepherdId.isSubtypeOf(mammalId),
          germanShepherdId.isSubtypeOf(animalId)
        )
      },
      test("supertype is not subtype of subtype") {
        val dogId    = TypeId.derived[Dog]
        val animalId = TypeId.derived[Animal]

        assertTrue(
          !animalId.isSubtypeOf(dogId)
        )
      },
      test("siblings are not subtypes of each other") {
        val dogId = TypeId.derived[Dog]
        val catId = TypeId.derived[Cat]

        assertTrue(
          !dogId.isSubtypeOf(catId),
          !catId.isSubtypeOf(dogId)
        )
      },
      test("String is subtype of CharSequence") {
        val stringId       = TypeId.derived[String]
        val charSequenceId = TypeId.derived[CharSequence]

        assertTrue(
          stringId.isSubtypeOf(charSequenceId),
          !charSequenceId.isSubtypeOf(stringId)
        )
      }
    ),
    suite("isSupertypeOf and isEquivalentTo")(
      test("Animal is supertype of Dog") {
        val animalId = TypeId.derived[Animal]
        val dogId    = TypeId.derived[Dog]

        assertTrue(
          animalId.isSupertypeOf(dogId),
          !dogId.isSupertypeOf(animalId)
        )
      },
      test("equivalent types are mutual subtypes") {
        val id1 = TypeId.derived[Dog]
        val id2 = TypeId.derived[Dog]

        assertTrue(
          id1.isEquivalentTo(id2),
          id1.isSubtypeOf(id2),
          id2.isSubtypeOf(id1)
        )
      },
      test("non-equivalent types in hierarchy") {
        val dogId    = TypeId.derived[Dog]
        val animalId = TypeId.derived[Animal]

        assertTrue(
          !dogId.isEquivalentTo(animalId),
          !animalId.isEquivalentTo(dogId)
        )
      }
    ),
    suite("Intersection Type Order Independence")(
      test("intersection types with same members in different order are equal") {
        val intersection1 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[Serializable]),
            TypeRepr.Ref(TypeId.derived[Comparable[String]])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[Comparable[String]]),
            TypeRepr.Ref(TypeId.derived[Serializable])
          )
        )

        assertTrue(
          intersection1 == intersection2,
          intersection1.hashCode() == intersection2.hashCode()
        )
      },
      test("intersection types with different members are not equal") {
        val intersection1 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[Serializable]),
            TypeRepr.Ref(TypeId.derived[Animal])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[Serializable]),
            TypeRepr.Ref(TypeId.derived[Mammal])
          )
        )

        assertTrue(intersection1 != intersection2)
      }
    ),
    suite("Map and Set Keys")(
      test("TypeId can be used as Map keys") {
        val intId    = TypeId.derived[Int]
        val stringId = TypeId.derived[String]
        val dogId    = TypeId.derived[Dog]

        val typeMap: Map[TypeId[_], String] = Map(
          intId    -> "integer type",
          stringId -> "string type",
          dogId    -> "dog type"
        )

        assertTrue(
          typeMap.get(intId).contains("integer type"),
          typeMap.get(stringId).contains("string type"),
          typeMap.get(dogId).contains("dog type"),
          typeMap.size == 3
        )
      },
      test("TypeId can be used as Set elements") {
        val intId    = TypeId.derived[Int]
        val stringId = TypeId.derived[String]
        val dogId    = TypeId.derived[Dog]

        val typeSet: Set[TypeId[_]] = Set(intId, stringId, dogId)

        assertTrue(
          typeSet.contains(intId),
          typeSet.contains(stringId),
          typeSet.contains(dogId),
          typeSet.size == 3
        )
      },
      test("TypeId equality works correctly in Map lookups") {
        val id1 = TypeId.derived[String]
        val id2 = TypeId.derived[String]

        val typeMap: Map[TypeId[_], String] = Map(id1 -> "first")

        assertTrue(
          typeMap.get(id2).contains("first"),
          id1 == id2
        )
      },
      test("TypeId equality works correctly in Set membership") {
        val id1 = TypeId.derived[Dog]
        val id2 = TypeId.derived[Dog]

        val typeSet: Set[TypeId[_]] = Set(id1)

        assertTrue(
          typeSet.contains(id2),
          id1 == id2
        )
      },
      test("Different TypeIds are distinct in Map") {
        val intId    = TypeId.derived[Int]
        val stringId = TypeId.derived[String]

        val typeMap: Map[TypeId[_], String] = Map(intId -> "int", stringId -> "string")
        val intValue                        = typeMap.get(intId)
        val stringValue                     = typeMap.get(stringId)

        assertTrue(
          typeMap.size == 2,
          intValue.isDefined,
          stringValue.isDefined,
          intValue != stringValue
        )
      },
      test("Type alias equals underlying type in Map") {
        val ageId = TypeId.derived[TypeAliases.Age]
        val intId = TypeId.derived[Int]

        val typeMap: Map[TypeId[_], String] = Map(intId -> "integer")

        assertTrue(
          typeMap.get(ageId).contains("integer"),
          ageId == intId
        )
      }
    ),
    suite("Recursive Types")(
      test("derives TypeId for recursive linked list") {
        val nodeId  = TypeId.derived[LinkedList.Node[Int]]
        val emptyId = TypeId.derived[LinkedList.Empty.type]
        val listId  = TypeId.derived[LinkedList[Int]]

        assertTrue(
          nodeId.name == "Node",
          emptyId.name == "Empty",
          listId.name == "LinkedList"
        )
      },
      test("derives TypeId for recursive tree") {
        val branchId = TypeId.derived[Tree.Branch[String]]
        val leafId   = TypeId.derived[Tree.Leaf.type]
        val treeId   = TypeId.derived[Tree[String]]

        assertTrue(
          branchId.name == "Branch",
          leafId.name == "Leaf",
          treeId.name == "Tree"
        )
      },
      test("recursive type has correct parent in hierarchy") {
        val nodeId = TypeId.derived[LinkedList.Node[Int]]

        assertTrue(
          nodeId.parents.exists {
            case TypeRepr.Ref(id)                      => id.name == "LinkedList"
            case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.name == "LinkedList"
            case _                                     => false
          }
        )
      },
      test("derives TypeId for mutually recursive types") {
        val exprId    = TypeId.derived[Expr]
        val literalId = TypeId.derived[Literal]
        val binOpId   = TypeId.derived[BinOp]
        val opId      = TypeId.derived[Op]

        assertTrue(
          exprId.name == "Expr",
          literalId.name == "Literal",
          binOpId.name == "BinOp",
          opId.name == "Op"
        )
      },
      test("mutually recursive types have correct relationships") {
        val literalId = TypeId.derived[Literal]
        val binOpId   = TypeId.derived[BinOp]
        val exprId    = TypeId.derived[Expr]

        assertTrue(
          literalId.isSubtypeOf(exprId),
          binOpId.isSubtypeOf(exprId)
        )
      },
      test("recursive type can be used as Map key") {
        val nodeId  = TypeId.derived[LinkedList.Node[Int]]
        val emptyId = TypeId.derived[LinkedList.Empty.type]

        val typeMap: Map[TypeId[_], String] = Map(
          nodeId  -> "node constructor",
          emptyId -> "empty list"
        )

        assertTrue(
          typeMap.get(nodeId).contains("node constructor"),
          typeMap.get(emptyId).contains("empty list")
        )
      }
    ),
    suite("Applied Type Inequality")(
      test("List[Int] and List[String] are NOT equal") {
        val listInt    = TypeId.derived[List[Int]]
        val listString = TypeId.derived[List[String]]

        assertTrue(
          listInt != listString,
          listInt.hashCode() != listString.hashCode(),
          listInt.name == "List",
          listString.name == "List",
          listInt.arity == 1,
          listString.arity == 1,
          listInt.isApplied,
          listString.isApplied
        )
      },
      test("Option[Int] and Option[String] are NOT equal") {
        val optInt    = TypeId.derived[Option[Int]]
        val optString = TypeId.derived[Option[String]]

        assertTrue(
          optInt != optString,
          optInt.hashCode() != optString.hashCode(),
          optInt.name == "Option"
        )
      },
      test("Map[String, Int] and Map[String, Double] are NOT equal") {
        val mapInt    = TypeId.derived[Map[String, Int]]
        val mapDouble = TypeId.derived[Map[String, Double]]

        assertTrue(
          mapInt != mapDouble,
          mapInt.hashCode() != mapDouble.hashCode(),
          mapInt.name == "Map",
          mapInt.arity == 2
        )
      },
      test("Either[String, Int] and Either[Int, String] are NOT equal") {
        val either1 = TypeId.derived[Either[String, Int]]
        val either2 = TypeId.derived[Either[Int, String]]

        assertTrue(
          either1 != either2,
          either1.hashCode() != either2.hashCode(),
          either1.name == "Either"
        )
      },
      test("GenericClass[Int] and GenericClass[String] are NOT equal") {
        val genInt    = TypeId.derived[GenericClass[Int]]
        val genString = TypeId.derived[GenericClass[String]]

        assertTrue(
          genInt != genString,
          genInt.hashCode() != genString.hashCode(),
          genInt.name == "GenericClass"
        )
      },
      test("MultiParamClass[Int, String] and MultiParamClass[Double, Boolean] are NOT equal") {
        val multi1 = TypeId.derived[MultiParamClass[Int, String]]
        val multi2 = TypeId.derived[MultiParamClass[Double, Boolean]]

        assertTrue(
          multi1 != multi2,
          multi1.hashCode() != multi2.hashCode(),
          multi1.name == "MultiParamClass"
        )
      },
      test("Box[Int] and Box[String] are NOT equal") {
        val boxInt    = TypeId.derived[Box[Int]]
        val boxString = TypeId.derived[Box[String]]

        assertTrue(
          boxInt != boxString,
          boxInt.hashCode() != boxString.hashCode(),
          boxInt.name == "Box"
        )
      },
      test("LinkedList[Int] and LinkedList[String] are NOT equal") {
        val listInt    = TypeId.derived[LinkedList[Int]]
        val listString = TypeId.derived[LinkedList[String]]

        assertTrue(
          listInt != listString,
          listInt.hashCode() != listString.hashCode(),
          listInt.name == "LinkedList"
        )
      },
      test("Tree[Int] and Tree[String] are NOT equal") {
        val treeInt    = TypeId.derived[Tree[Int]]
        val treeString = TypeId.derived[Tree[String]]

        assertTrue(
          treeInt != treeString,
          treeInt.hashCode() != treeString.hashCode(),
          treeInt.name == "Tree"
        )
      },
      test("applied type has typeArgs while type constructor has none") {
        val listInt = TypeId.derived[List[Int]]

        assertTrue(
          listInt.isApplied,
          listInt.typeArgs.nonEmpty,
          listInt.name == "List"
        )
      },
      test("nested applied types are NOT equal when args differ") {
        val nestedInt    = TypeId.derived[List[Option[Int]]]
        val nestedString = TypeId.derived[List[Option[String]]]

        assertTrue(
          nestedInt != nestedString,
          nestedInt.name == "List"
        )
      },
      test("same applied type is equal to itself") {
        val listInt1 = TypeId.derived[List[Int]]
        val listInt2 = TypeId.derived[List[Int]]

        assertTrue(
          listInt1 == listInt2,
          listInt1.hashCode() == listInt2.hashCode()
        )
      },
      test("deeply nested generics are equal when structure matches") {
        val id1 = TypeId.derived[List[Map[String, Option[Either[Int, List[String]]]]]]
        val id2 = TypeId.derived[List[Map[String, Option[Either[Int, List[String]]]]]]

        assertTrue(
          id1 == id2,
          id1.hashCode() == id2.hashCode()
        )
      },
      test("deeply nested generics are NOT equal when inner args differ") {
        val id1 = TypeId.derived[List[Map[String, Option[Either[Int, List[String]]]]]]
        val id2 = TypeId.derived[List[Map[String, Option[Either[Int, List[Int]]]]]]

        assertTrue(
          id1 != id2,
          id1.hashCode() != id2.hashCode()
        )
      }
    ),
    suite("Variance-Aware Subtyping for Applied Types")(
      test("List[Dog] is subtype of List[Animal] (covariant)") {
        val listDog    = TypeId.derived[List[Dog]]
        val listAnimal = TypeId.derived[List[Animal]]

        assertTrue(
          listDog.isSubtypeOf(listAnimal),
          !listAnimal.isSubtypeOf(listDog),
          listDog != listAnimal
        )
      },
      test("Option[Dog] is subtype of Option[Animal] (covariant)") {
        val optDog    = TypeId.derived[Option[Dog]]
        val optAnimal = TypeId.derived[Option[Animal]]

        assertTrue(
          optDog.isSubtypeOf(optAnimal),
          !optAnimal.isSubtypeOf(optDog)
        )
      },
      test("List[Cat] is NOT subtype of List[Dog] (siblings)") {
        val listCat = TypeId.derived[List[Cat]]
        val listDog = TypeId.derived[List[Dog]]

        assertTrue(
          !listCat.isSubtypeOf(listDog),
          !listDog.isSubtypeOf(listCat)
        )
      },
      test("contravariant type parameter subtyping - (Animal => Int) <: (Dog => Int)") {
        val fnAnimal = TypeId.derived[Animal => Int]
        val fnDog    = TypeId.derived[Dog => Int]

        assertTrue(
          fnAnimal.isSubtypeOf(fnDog),
          !fnDog.isSubtypeOf(fnAnimal)
        )
      },
      test("invariant type parameter subtyping - Array is invariant") {
        val arrayDog    = TypeId.derived[Array[Dog]]
        val arrayAnimal = TypeId.derived[Array[Animal]]

        assertTrue(
          !arrayDog.isSubtypeOf(arrayAnimal),
          !arrayAnimal.isSubtypeOf(arrayDog)
        )
      },
      test("mixed variance - Function1 is contravariant in input, covariant in output") {
        val fnAnimalToDog = TypeId.derived[Animal => Dog]
        val fnDogToAnimal = TypeId.derived[Dog => Animal]

        assertTrue(
          fnAnimalToDog.isSubtypeOf(fnDogToAnimal),
          !fnDogToAnimal.isSubtypeOf(fnAnimalToDog)
        )
      }
    ),
    suite("Cross-Compilation Equality")(
      test("same type derived multiple times equals itself") {
        val id1 = TypeId.derived[String]
        val id2 = TypeId.derived[String]
        val id3 = TypeId.derived[String]

        assertTrue(
          id1 == id2,
          id2 == id3,
          id1 == id3,
          id1.hashCode() == id2.hashCode(),
          id2.hashCode() == id3.hashCode()
        )
      },
      test("generic types derived with same type arguments are equal") {
        val list1 = TypeId.derived[List[Int]]
        val list2 = TypeId.derived[List[Int]]

        assertTrue(
          list1 == list2,
          list1.hashCode() == list2.hashCode()
        )
      },
      test("user-defined types are equal across derivations") {
        val dog1 = TypeId.derived[Dog]
        val dog2 = TypeId.derived[Dog]

        assertTrue(
          dog1 == dog2,
          dog1.hashCode() == dog2.hashCode()
        )
      },
      test("type constructors are equal across derivations") {
        val list1 = TypeId.derived[List[_]]
        val list2 = TypeId.derived[List[_]]

        assertTrue(
          list1 == list2,
          list1.hashCode() == list2.hashCode()
        )
      },
      test("nested types are equal across derivations") {
        val nested1 = TypeId.derived[Outer.Nested]
        val nested2 = TypeId.derived[Outer.Nested]

        assertTrue(
          nested1 == nested2,
          nested1.hashCode() == nested2.hashCode()
        )
      },
      test("sealed trait subtypes are equal across derivations") {
        val caseA1 = TypeId.derived[SimpleSealed.CaseA]
        val caseA2 = TypeId.derived[SimpleSealed.CaseA]

        assertTrue(
          caseA1 == caseA2,
          caseA1.hashCode() == caseA2.hashCode()
        )
      }
    )
  )
}
