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
        val id = TypeId.of[Int]
        assertTrue(
          id.name == "Int",
          id.owner == Owner.scala,
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for String") {
        val id = TypeId.of[String]
        assertTrue(
          id.name == "String",
          id.owner == Owner.javaLang,
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for Boolean") {
        val id = TypeId.of[Boolean]
        assertTrue(
          id.name == "Boolean",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Long") {
        val id = TypeId.of[Long]
        assertTrue(
          id.name == "Long",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Double") {
        val id = TypeId.of[Double]
        assertTrue(
          id.name == "Double",
          id.owner == Owner.scala,
          id.arity == 0
        )
      },
      test("derives TypeId for Byte") {
        val id = TypeId.of[Byte]
        assertTrue(id.name == "Byte")
      },
      test("derives TypeId for Short") {
        val id = TypeId.of[Short]
        assertTrue(id.name == "Short")
      },
      test("derives TypeId for Float") {
        val id = TypeId.of[Float]
        assertTrue(id.name == "Float")
      },
      test("derives TypeId for Char") {
        val id = TypeId.of[Char]
        assertTrue(id.name == "Char")
      },
      test("derives TypeId for Unit") {
        val id = TypeId.of[Unit]
        assertTrue(id.name == "Unit")
      },
      test("derived primitive TypeIds are the same instances as predefined") {
        assertTrue(
          TypeId.of[Int] eq TypeId.int,
          TypeId.of[String] eq TypeId.string,
          TypeId.of[Boolean] eq TypeId.boolean,
          TypeId.of[Long] eq TypeId.long,
          TypeId.of[Double] eq TypeId.double,
          TypeId.of[Float] eq TypeId.float,
          TypeId.of[Short] eq TypeId.short,
          TypeId.of[Byte] eq TypeId.byte,
          TypeId.of[Char] eq TypeId.char,
          TypeId.of[Unit] eq TypeId.unit
        )
      }
    ),
    suite("Standard Library Collection Types")(
      test("derives TypeId for List (type constructor)") {
        val id = TypeId.of[List[_]]
        assertTrue(
          id.name == "List",
          id.arity == 1,
          id.typeParams.head.name == "A"
        )
      },
      test("derives TypeId for Option (type constructor)") {
        val id = TypeId.of[Option[_]]
        assertTrue(
          id.name == "Option",
          id.arity == 1
        )
      },
      test("derives TypeId for Map (type constructor with arity 2)") {
        val id = TypeId.of[Map[_, _]]
        assertTrue(
          id.name == "Map",
          id.arity == 2
        )
      },
      test("derives TypeId for Either (type constructor with arity 2)") {
        val id = TypeId.of[Either[_, _]]
        assertTrue(
          id.name == "Either",
          id.arity == 2
        )
      },
      test("derives TypeId for Vector") {
        val id = TypeId.of[Vector[_]]
        assertTrue(
          id.name == "Vector",
          id.arity == 1
        )
      },
      test("derives TypeId for Set") {
        val id = TypeId.of[Set[_]]
        assertTrue(
          id.name == "Set",
          id.arity == 1
        )
      },
      test("derives TypeId for Seq") {
        val id = TypeId.of[Seq[_]]
        assertTrue(id.name == "Seq")
      }
    ),
    suite("Applied Types")(
      test("derives TypeId for List[Int]") {
        val id = TypeId.of[List[Int]]
        assertTrue(
          id.name == "List",
          id.arity == 1
        )
      },
      test("derives TypeId for Option[String]") {
        val id = TypeId.of[Option[String]]
        assertTrue(
          id.name == "Option",
          id.arity == 1
        )
      },
      test("derives TypeId for Map[String, Int]") {
        val id = TypeId.of[Map[String, Int]]
        assertTrue(
          id.name == "Map",
          id.arity == 2
        )
      },
      test("derives TypeId for Either[String, Int]") {
        val id = TypeId.of[Either[String, Int]]
        assertTrue(
          id.name == "Either",
          id.arity == 2
        )
      }
    ),
    suite("User-Defined Case Classes")(
      test("derives TypeId for simple case class") {
        val id = TypeId.of[SimpleClass]
        assertTrue(
          id.name == "SimpleClass",
          id.arity == 0,
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for generic case class") {
        val id = TypeId.of[GenericClass[_]]
        assertTrue(
          id.name == "GenericClass",
          id.arity == 1
        )
      },
      test("derives TypeId for multi-param generic case class") {
        val id = TypeId.of[MultiParamClass[_, _]]
        assertTrue(
          id.name == "MultiParamClass",
          id.arity == 2
        )
      },
      test("derives TypeId for applied generic case class") {
        val id = TypeId.of[GenericClass[Int]]
        assertTrue(
          id.name == "GenericClass",
          id.arity == 1
        )
      }
    ),
    suite("Sealed Traits and ADTs")(
      test("derives TypeId for sealed trait") {
        val id = TypeId.of[SimpleSealed]
        assertTrue(
          id.name == "SimpleSealed",
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for case class extending sealed trait") {
        val id = TypeId.of[SimpleSealed.CaseA]
        assertTrue(
          id.name == "CaseA"
        )
      },
      test("derives TypeId for case object extending sealed trait") {
        val id = TypeId.of[SimpleSealed.CaseC.type]
        assertTrue(
          id.name == "CaseC"
        )
      }
    ),
    suite("Nested Types")(
      test("derives TypeId for nested case class") {
        val id = TypeId.of[Outer.Nested]
        assertTrue(
          id.name == "Nested",
          id.fullName.contains("Outer")
        )
      },
      test("derives TypeId for deeply nested case class") {
        val id = TypeId.of[Outer.Inner.DeepNested]
        assertTrue(
          id.name == "DeepNested",
          id.fullName.contains("Inner")
        )
      }
    ),
    suite("Traits and Abstract Classes")(
      test("derives TypeId for trait") {
        val id = TypeId.of[SimpleTrait]
        assertTrue(
          id.name == "SimpleTrait",
          !id.isAlias && !id.isOpaque
        )
      },
      test("derives TypeId for abstract class") {
        val id = TypeId.of[AbstractClass]
        assertTrue(
          id.name == "AbstractClass",
          !id.isAlias && !id.isOpaque
        )
      }
    ),
    suite("Java Types")(
      test("derives TypeId for java.util.UUID") {
        val id = TypeId.of[java.util.UUID]
        assertTrue(
          id.name == "UUID",
          id.owner == Owner.javaUtil
        )
      },
      test("derives TypeId for java.time.Instant") {
        val id = TypeId.of[java.time.Instant]
        assertTrue(
          id.name == "Instant",
          id.owner == Owner.javaTime
        )
      },
      test("derives TypeId for java.time.Duration") {
        val id = TypeId.of[java.time.Duration]
        assertTrue(
          id.name == "Duration"
        )
      },
      test("derives TypeId for java.util.ArrayList with type parameter") {
        val id = TypeId.of[java.util.ArrayList[String]]
        assertTrue(
          id.name == "ArrayList",
          id.owner == Owner.javaUtil
        )
      }
    ),
    suite("Scala BigInt/BigDecimal")(
      test("derives TypeId for BigInt") {
        val id = TypeId.of[BigInt]
        assertTrue(
          id.name == "BigInt",
          id.owner == Owner.scala
        )
      },
      test("derives TypeId for BigDecimal") {
        val id = TypeId.of[BigDecimal]
        assertTrue(
          id.name == "BigDecimal",
          id.owner == Owner.scala
        )
      }
    ),
    suite("Tuple Types")(
      test("derives TypeId for Tuple2") {
        val id = TypeId.of[Tuple2[_, _]]
        assertTrue(
          id.name == "Tuple2",
          id.arity == 2
        )
      },
      test("derives TypeId for Tuple3") {
        val id = TypeId.of[Tuple3[_, _, _]]
        assertTrue(
          id.name == "Tuple3",
          id.arity == 3
        )
      },
      test("derives TypeId for applied tuple (Int, String)") {
        val id = TypeId.of[(Int, String)]
        assertTrue(
          id.name == "Tuple2",
          id.arity == 2
        )
      }
    ),
    suite("Function Types")(
      test("derives TypeId for Function1") {
        val id = TypeId.of[Function1[_, _]]
        assertTrue(
          id.name == "Function1",
          id.arity == 2
        )
      },
      test("derives TypeId for Function2") {
        val id = TypeId.of[Function2[_, _, _]]
        assertTrue(
          id.name == "Function2",
          id.arity == 3
        )
      }
    ),
    suite("Owner Path Verification")(
      test("scala primitives have correct owner") {
        val intId  = TypeId.of[Int]
        val longId = TypeId.of[Long]
        assertTrue(
          intId.owner == Owner.scala,
          longId.owner == Owner.scala
        )
      },
      test("java.lang types have correct owner") {
        val stringId = TypeId.of[String]
        assertTrue(
          stringId.owner == Owner.javaLang
        )
      },
      test("collection types have correct owner") {
        val listId = TypeId.of[List[_]]
        assertTrue(
          listId.owner == Owner.scalaCollectionImmutable
        )
      }
    ),
    suite("Type Parameter Verification")(
      test("List has one type parameter named A") {
        val id = TypeId.of[List[_]]
        assertTrue(
          id.typeParams.size == 1,
          id.typeParams.head.index == 0
        )
      },
      test("Map has two type parameters") {
        val id = TypeId.of[Map[_, _]]
        assertTrue(
          id.typeParams.size == 2,
          id.typeParams(0).index == 0,
          id.typeParams(1).index == 1
        )
      },
      test("Either has two type parameters") {
        val id = TypeId.of[Either[_, _]]
        assertTrue(
          id.typeParams.size == 2
        )
      }
    ),
    suite("FullName Verification")(
      test("scala.Int has correct fullName") {
        val id = TypeId.of[Int]
        assertTrue(
          id.fullName == "scala.Int"
        )
      },
      test("java.lang.String has correct fullName") {
        val id = TypeId.of[String]
        assertTrue(
          id.fullName == "java.lang.String"
        )
      },
      test("nested type has correct fullName with owner path") {
        val id = TypeId.of[Outer.Nested]
        assertTrue(
          id.fullName.endsWith("Nested")
        )
      }
    ),
    suite("Pattern Matching Support")(
      test("derived TypeId matches Nominal extractor") {
        val id      = TypeId.of[Int]
        val matches = id match {
          case TypeId.Nominal(name, _, _, _, _) => name == "Int"
          case _                                => false
        }
        assertTrue(matches)
      }
    ),
    suite("Consistency with Predefined TypeIds")(
      test("derive[Int] equals predefined TypeId.int") {
        val derived    = TypeId.of[Int]
        val predefined = TypeId.int
        assertTrue(
          derived.name == predefined.name,
          derived.owner == predefined.owner,
          derived.arity == predefined.arity
        )
      },
      test("derive[String] equals predefined TypeId.string") {
        val derived    = TypeId.of[String]
        val predefined = TypeId.string
        assertTrue(
          derived.name == predefined.name,
          derived.owner == predefined.owner
        )
      },
      test("derive[List[_]] equals predefined TypeId.list") {
        val derived    = TypeId.of[List[_]]
        val predefined = TypeId.list
        assertTrue(
          derived.name == predefined.name,
          derived.arity == predefined.arity
        )
      }
    ),
    suite("Parent Types")(
      test("class extending trait has correct parents") {
        val id = TypeId.of[SimpleSealed.CaseA]
        assertTrue(
          id.parents.exists {
            case TypeRepr.Ref(tid) => tid.name == "SimpleSealed"
            case _                 => false
          }
        )
      },
      test("sealed trait subtypes have parent in parents") {
        val caseAId = TypeId.of[SimpleSealed.CaseA]
        val caseBId = TypeId.of[SimpleSealed.CaseB]
        assertTrue(
          caseAId.parents.nonEmpty,
          caseBId.parents.nonEmpty
        )
      },
      test("parents is accessible on all types") {
        val intId   = TypeId.of[Int]
        val listId  = TypeId.of[List[_]]
        val classId = TypeId.of[SimpleClass]
        assertTrue(
          intId.parents.isInstanceOf[List[TypeRepr]],
          listId.parents.isInstanceOf[List[TypeRepr]],
          classId.parents.isInstanceOf[List[TypeRepr]]
        )
      }
    ),
    suite("isSubtypeOf")(
      test("type is subtype of itself") {
        val id = TypeId.of[SimpleTrait]
        assertTrue(id.isSubtypeOf(id))
      },
      test("case class is subtype of its sealed trait") {
        val caseAId  = TypeId.of[SimpleSealed.CaseA]
        val sealedId = TypeId.of[SimpleSealed]
        assertTrue(
          caseAId.isSubtypeOf(sealedId),
          !sealedId.isSubtypeOf(caseAId)
        )
      },
      test("case object is subtype of its sealed trait") {
        val caseCId  = TypeId.of[SimpleSealed.CaseC.type]
        val sealedId = TypeId.of[SimpleSealed]
        assertTrue(caseCId.isSubtypeOf(sealedId))
      },
      test("unrelated types are not subtypes of each other") {
        val intId    = TypeId.of[Int]
        val stringId = TypeId.of[String]
        assertTrue(
          !intId.isSubtypeOf(stringId),
          !stringId.isSubtypeOf(intId)
        )
      },
      test("sibling subtypes are not subtypes of each other") {
        val caseAId = TypeId.of[SimpleSealed.CaseA]
        val caseBId = TypeId.of[SimpleSealed.CaseB]
        assertTrue(
          !caseAId.isSubtypeOf(caseBId),
          !caseBId.isSubtypeOf(caseAId)
        )
      }
    ),
    suite("isSupertypeOf")(
      test("sealed trait is supertype of its case class") {
        val sealedId = TypeId.of[SimpleSealed]
        val caseAId  = TypeId.of[SimpleSealed.CaseA]
        assertTrue(
          sealedId.isSupertypeOf(caseAId),
          !caseAId.isSupertypeOf(sealedId)
        )
      },
      test("type is supertype of itself") {
        val id = TypeId.of[SimpleClass]
        assertTrue(id.isSupertypeOf(id))
      }
    ),
    suite("isEquivalentTo")(
      test("type is equivalent to itself") {
        val id = TypeId.of[SimpleClass]
        assertTrue(id.isEquivalentTo(id))
      },
      test("unrelated types are not equivalent") {
        val intId    = TypeId.of[Int]
        val stringId = TypeId.of[String]
        assertTrue(!intId.isEquivalentTo(stringId))
      },
      test("subtype and supertype are not equivalent") {
        val sealedId = TypeId.of[SimpleSealed]
        val caseAId  = TypeId.of[SimpleSealed.CaseA]
        assertTrue(!sealedId.isEquivalentTo(caseAId))
      }
    ),
    suite("Type Aliases")(
      test("type aliases are detected correctly") {
        val ageId = TypeId.of[TypeAliases.Age]
        val mapId = TypeId.of[TypeAliases.StringMap[Int]]

        assertTrue(
          ageId.name == "Age" && ageId.isAlias,
          mapId.name == "StringMap" && mapId.isAlias
        )
      },
      test("aliased types are extracted correctly") {
        val ageId = TypeId.of[TypeAliases.Age]

        assertTrue(
          ageId.aliasedTo.exists {
            case TypeRepr.Ref(typeId) => typeId.name == "Int"
            case _                    => false
          }
        )
      },
      test("generic type alias has aliased type") {
        val mapId = TypeId.of[TypeAliases.StringMap[Int]]

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
        val ageId = TypeId.of[TypeAliases.Age]
        val intId = TypeId.of[Int]

        assertTrue(
          ageId.isEquivalentTo(intId),
          ageId.isSubtypeOf(intId),
          intId.isSubtypeOf(ageId)
        )
      }
    ),
    suite("Basic Subtyping")(
      test("reflexivity - type is subtype of itself") {
        val stringId = TypeId.of[String]
        val dogId    = TypeId.of[Dog]

        assertTrue(
          stringId.isSubtypeOf(stringId),
          dogId.isSubtypeOf(dogId)
        )
      },
      test("reflexivity with derived type") {
        val caseAId = TypeId.of[SimpleSealed.CaseA]
        assertTrue(caseAId.isSubtypeOf(caseAId))
      },
      test("Nothing is subtype of everything") {
        val nothingId = TypeId.of[Nothing]
        val intId     = TypeId.of[Int]
        val stringId  = TypeId.of[String]
        val anyId     = TypeId.of[Any]
        val listIntId = TypeId.of[List[Int]]

        assertTrue(
          nothingId.isSubtypeOf(intId),
          nothingId.isSubtypeOf(stringId),
          nothingId.isSubtypeOf(anyId),
          nothingId.isSubtypeOf(listIntId)
        )
      },
      test("everything is subtype of Any") {
        val intId     = TypeId.of[Int]
        val stringId  = TypeId.of[String]
        val listIntId = TypeId.of[List[Int]]
        val anyId     = TypeId.of[Any]

        assertTrue(
          intId.isSubtypeOf(anyId),
          stringId.isSubtypeOf(anyId),
          listIntId.isSubtypeOf(anyId)
        )
      }
    ),
    suite("Nominal Hierarchy Subtyping")(
      test("class hierarchy subtyping") {
        val dogId    = TypeId.of[Dog]
        val mammalId = TypeId.of[Mammal]
        val animalId = TypeId.of[Animal]

        assertTrue(
          dogId.isSubtypeOf(mammalId),
          dogId.isSubtypeOf(animalId),
          mammalId.isSubtypeOf(animalId)
        )
      },
      test("transitive subtyping through multiple levels") {
        val germanShepherdId = TypeId.of[GermanShepherd]
        val dogId            = TypeId.of[Dog]
        val mammalId         = TypeId.of[Mammal]
        val animalId         = TypeId.of[Animal]

        assertTrue(
          germanShepherdId.isSubtypeOf(dogId),
          germanShepherdId.isSubtypeOf(mammalId),
          germanShepherdId.isSubtypeOf(animalId)
        )
      },
      test("supertype is not subtype of subtype") {
        val dogId    = TypeId.of[Dog]
        val animalId = TypeId.of[Animal]

        assertTrue(
          !animalId.isSubtypeOf(dogId)
        )
      },
      test("siblings are not subtypes of each other") {
        val dogId = TypeId.of[Dog]
        val catId = TypeId.of[Cat]

        assertTrue(
          !dogId.isSubtypeOf(catId),
          !catId.isSubtypeOf(dogId)
        )
      },
      test("String is subtype of CharSequence") {
        val stringId       = TypeId.of[String]
        val charSequenceId = TypeId.of[CharSequence]

        assertTrue(
          stringId.isSubtypeOf(charSequenceId),
          !charSequenceId.isSubtypeOf(stringId)
        )
      }
    ),
    suite("isSupertypeOf and isEquivalentTo")(
      test("Animal is supertype of Dog") {
        val animalId = TypeId.of[Animal]
        val dogId    = TypeId.of[Dog]

        assertTrue(
          animalId.isSupertypeOf(dogId),
          !dogId.isSupertypeOf(animalId)
        )
      },
      test("equivalent types are mutual subtypes") {
        val id1 = TypeId.of[Dog]
        val id2 = TypeId.of[Dog]

        assertTrue(
          id1.isEquivalentTo(id2),
          id1.isSubtypeOf(id2),
          id2.isSubtypeOf(id1)
        )
      },
      test("non-equivalent types in hierarchy") {
        val dogId    = TypeId.of[Dog]
        val animalId = TypeId.of[Animal]

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
            TypeRepr.Ref(TypeId.of[Serializable]),
            TypeRepr.Ref(TypeId.of[Comparable[String]])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[Comparable[String]]),
            TypeRepr.Ref(TypeId.of[Serializable])
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
            TypeRepr.Ref(TypeId.of[Serializable]),
            TypeRepr.Ref(TypeId.of[Animal])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[Serializable]),
            TypeRepr.Ref(TypeId.of[Mammal])
          )
        )

        assertTrue(intersection1 != intersection2)
      }
    ),
    suite("Map and Set Keys")(
      test("TypeId can be used as Map keys") {
        val intId    = TypeId.of[Int]
        val stringId = TypeId.of[String]
        val dogId    = TypeId.of[Dog]

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
        val intId    = TypeId.of[Int]
        val stringId = TypeId.of[String]
        val dogId    = TypeId.of[Dog]

        val typeSet: Set[TypeId[_]] = Set(intId, stringId, dogId)

        assertTrue(
          typeSet.contains(intId),
          typeSet.contains(stringId),
          typeSet.contains(dogId),
          typeSet.size == 3
        )
      },
      test("TypeId equality works correctly in Map lookups") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]

        val typeMap: Map[TypeId[_], String] = Map(id1 -> "first")

        assertTrue(
          typeMap.get(id2).contains("first"),
          id1 == id2
        )
      },
      test("TypeId equality works correctly in Set membership") {
        val id1 = TypeId.of[Dog]
        val id2 = TypeId.of[Dog]

        val typeSet: Set[TypeId[_]] = Set(id1)

        assertTrue(
          typeSet.contains(id2),
          id1 == id2
        )
      },
      test("Different TypeIds are distinct in Map") {
        val intId    = TypeId.of[Int]
        val stringId = TypeId.of[String]

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
        val ageId = TypeId.of[TypeAliases.Age]
        val intId = TypeId.of[Int]

        val typeMap: Map[TypeId[_], String] = Map(intId -> "integer")

        assertTrue(
          typeMap.get(ageId).contains("integer"),
          ageId == intId
        )
      }
    ),
    suite("Recursive Types")(
      test("derives TypeId for recursive linked list") {
        val nodeId  = TypeId.of[LinkedList.Node[Int]]
        val emptyId = TypeId.of[LinkedList.Empty.type]
        val listId  = TypeId.of[LinkedList[Int]]

        assertTrue(
          nodeId.name == "Node",
          emptyId.name == "Empty",
          listId.name == "LinkedList"
        )
      },
      test("derives TypeId for recursive tree") {
        val branchId = TypeId.of[Tree.Branch[String]]
        val leafId   = TypeId.of[Tree.Leaf.type]
        val treeId   = TypeId.of[Tree[String]]

        assertTrue(
          branchId.name == "Branch",
          leafId.name == "Leaf",
          treeId.name == "Tree"
        )
      },
      test("recursive type has correct parent in hierarchy") {
        val nodeId = TypeId.of[LinkedList.Node[Int]]

        assertTrue(
          nodeId.parents.exists {
            case TypeRepr.Ref(id)                      => id.name == "LinkedList"
            case TypeRepr.Applied(TypeRepr.Ref(id), _) => id.name == "LinkedList"
            case _                                     => false
          }
        )
      },
      test("derives TypeId for mutually recursive types") {
        val exprId    = TypeId.of[Expr]
        val literalId = TypeId.of[Literal]
        val binOpId   = TypeId.of[BinOp]
        val opId      = TypeId.of[Op]

        assertTrue(
          exprId.name == "Expr",
          literalId.name == "Literal",
          binOpId.name == "BinOp",
          opId.name == "Op"
        )
      },
      test("mutually recursive types have correct relationships") {
        val literalId = TypeId.of[Literal]
        val binOpId   = TypeId.of[BinOp]
        val exprId    = TypeId.of[Expr]

        assertTrue(
          literalId.isSubtypeOf(exprId),
          binOpId.isSubtypeOf(exprId)
        )
      },
      test("recursive type can be used as Map key") {
        val nodeId  = TypeId.of[LinkedList.Node[Int]]
        val emptyId = TypeId.of[LinkedList.Empty.type]

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
        val listInt    = TypeId.of[List[Int]]
        val listString = TypeId.of[List[String]]

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
        val optInt    = TypeId.of[Option[Int]]
        val optString = TypeId.of[Option[String]]

        assertTrue(
          optInt != optString,
          optInt.hashCode() != optString.hashCode(),
          optInt.name == "Option"
        )
      },
      test("Map[String, Int] and Map[String, Double] are NOT equal") {
        val mapInt    = TypeId.of[Map[String, Int]]
        val mapDouble = TypeId.of[Map[String, Double]]

        assertTrue(
          mapInt != mapDouble,
          mapInt.hashCode() != mapDouble.hashCode(),
          mapInt.name == "Map",
          mapInt.arity == 2
        )
      },
      test("Either[String, Int] and Either[Int, String] are NOT equal") {
        val either1 = TypeId.of[Either[String, Int]]
        val either2 = TypeId.of[Either[Int, String]]

        assertTrue(
          either1 != either2,
          either1.hashCode() != either2.hashCode(),
          either1.name == "Either"
        )
      },
      test("GenericClass[Int] and GenericClass[String] are NOT equal") {
        val genInt    = TypeId.of[GenericClass[Int]]
        val genString = TypeId.of[GenericClass[String]]

        assertTrue(
          genInt != genString,
          genInt.hashCode() != genString.hashCode(),
          genInt.name == "GenericClass"
        )
      },
      test("MultiParamClass[Int, String] and MultiParamClass[Double, Boolean] are NOT equal") {
        val multi1 = TypeId.of[MultiParamClass[Int, String]]
        val multi2 = TypeId.of[MultiParamClass[Double, Boolean]]

        assertTrue(
          multi1 != multi2,
          multi1.hashCode() != multi2.hashCode(),
          multi1.name == "MultiParamClass"
        )
      },
      test("Box[Int] and Box[String] are NOT equal") {
        val boxInt    = TypeId.of[Box[Int]]
        val boxString = TypeId.of[Box[String]]

        assertTrue(
          boxInt != boxString,
          boxInt.hashCode() != boxString.hashCode(),
          boxInt.name == "Box"
        )
      },
      test("LinkedList[Int] and LinkedList[String] are NOT equal") {
        val listInt    = TypeId.of[LinkedList[Int]]
        val listString = TypeId.of[LinkedList[String]]

        assertTrue(
          listInt != listString,
          listInt.hashCode() != listString.hashCode(),
          listInt.name == "LinkedList"
        )
      },
      test("Tree[Int] and Tree[String] are NOT equal") {
        val treeInt    = TypeId.of[Tree[Int]]
        val treeString = TypeId.of[Tree[String]]

        assertTrue(
          treeInt != treeString,
          treeInt.hashCode() != treeString.hashCode(),
          treeInt.name == "Tree"
        )
      },
      test("applied type has typeArgs while type constructor has none") {
        val listInt = TypeId.of[List[Int]]

        assertTrue(
          listInt.isApplied,
          listInt.typeArgs.nonEmpty,
          listInt.name == "List"
        )
      },
      test("nested applied types are NOT equal when args differ") {
        val nestedInt    = TypeId.of[List[Option[Int]]]
        val nestedString = TypeId.of[List[Option[String]]]

        assertTrue(
          nestedInt != nestedString,
          nestedInt.name == "List"
        )
      },
      test("same applied type is equal to itself") {
        val listInt1 = TypeId.of[List[Int]]
        val listInt2 = TypeId.of[List[Int]]

        assertTrue(
          listInt1 == listInt2,
          listInt1.hashCode() == listInt2.hashCode()
        )
      },
      test("deeply nested generics are equal when structure matches") {
        val id1 = TypeId.of[List[Map[String, Option[Either[Int, List[String]]]]]]
        val id2 = TypeId.of[List[Map[String, Option[Either[Int, List[String]]]]]]

        assertTrue(
          id1 == id2,
          id1.hashCode() == id2.hashCode()
        )
      },
      test("deeply nested generics are NOT equal when inner args differ") {
        val id1 = TypeId.of[List[Map[String, Option[Either[Int, List[String]]]]]]
        val id2 = TypeId.of[List[Map[String, Option[Either[Int, List[Int]]]]]]

        assertTrue(
          id1 != id2,
          id1.hashCode() != id2.hashCode()
        )
      }
    ),
    suite("Variance-Aware Subtyping for Applied Types")(
      test("List[Dog] is subtype of List[Animal] (covariant)") {
        val listDog    = TypeId.of[List[Dog]]
        val listAnimal = TypeId.of[List[Animal]]

        assertTrue(
          listDog.isSubtypeOf(listAnimal),
          !listAnimal.isSubtypeOf(listDog),
          listDog != listAnimal
        )
      },
      test("Option[Dog] is subtype of Option[Animal] (covariant)") {
        val optDog    = TypeId.of[Option[Dog]]
        val optAnimal = TypeId.of[Option[Animal]]

        assertTrue(
          optDog.isSubtypeOf(optAnimal),
          !optAnimal.isSubtypeOf(optDog)
        )
      },
      test("List[Cat] is NOT subtype of List[Dog] (siblings)") {
        val listCat = TypeId.of[List[Cat]]
        val listDog = TypeId.of[List[Dog]]

        assertTrue(
          !listCat.isSubtypeOf(listDog),
          !listDog.isSubtypeOf(listCat)
        )
      },
      test("contravariant type parameter subtyping - (Animal => Int) <: (Dog => Int)") {
        val fnAnimal = TypeId.of[Animal => Int]
        val fnDog    = TypeId.of[Dog => Int]

        assertTrue(
          fnAnimal.isSubtypeOf(fnDog),
          !fnDog.isSubtypeOf(fnAnimal)
        )
      },
      test("invariant type parameter subtyping - Array is invariant") {
        val arrayDog    = TypeId.of[Array[Dog]]
        val arrayAnimal = TypeId.of[Array[Animal]]

        assertTrue(
          !arrayDog.isSubtypeOf(arrayAnimal),
          !arrayAnimal.isSubtypeOf(arrayDog)
        )
      },
      test("mixed variance - Function1 is contravariant in input, covariant in output") {
        val fnAnimalToDog = TypeId.of[Animal => Dog]
        val fnDogToAnimal = TypeId.of[Dog => Animal]

        assertTrue(
          fnAnimalToDog.isSubtypeOf(fnDogToAnimal),
          !fnDogToAnimal.isSubtypeOf(fnAnimalToDog)
        )
      }
    ),
    suite("Cross-Compilation Equality")(
      test("same type derived multiple times equals itself") {
        val id1 = TypeId.of[String]
        val id2 = TypeId.of[String]
        val id3 = TypeId.of[String]

        assertTrue(
          id1 == id2,
          id2 == id3,
          id1 == id3,
          id1.hashCode() == id2.hashCode(),
          id2.hashCode() == id3.hashCode()
        )
      },
      test("generic types derived with same type arguments are equal") {
        val list1 = TypeId.of[List[Int]]
        val list2 = TypeId.of[List[Int]]

        assertTrue(
          list1 == list2,
          list1.hashCode() == list2.hashCode()
        )
      },
      test("user-defined types are equal across derivations") {
        val dog1 = TypeId.of[Dog]
        val dog2 = TypeId.of[Dog]

        assertTrue(
          dog1 == dog2,
          dog1.hashCode() == dog2.hashCode()
        )
      },
      test("type constructors are equal across derivations") {
        val list1 = TypeId.of[List[_]]
        val list2 = TypeId.of[List[_]]

        assertTrue(
          list1 == list2,
          list1.hashCode() == list2.hashCode()
        )
      },
      test("nested types are equal across derivations") {
        val nested1 = TypeId.of[Outer.Nested]
        val nested2 = TypeId.of[Outer.Nested]

        assertTrue(
          nested1 == nested2,
          nested1.hashCode() == nested2.hashCode()
        )
      },
      test("sealed trait subtypes are equal across derivations") {
        val caseA1 = TypeId.of[SimpleSealed.CaseA]
        val caseA2 = TypeId.of[SimpleSealed.CaseA]

        assertTrue(
          caseA1 == caseA2,
          caseA1.hashCode() == caseA2.hashCode()
        )
      }
    ),
    suite("TypeId.of API")(
      test("TypeId.of produces same result as TypeId.of") {
        val viaOf      = TypeId.of[String]
        val viaDerived = TypeId.of[String]

        assertTrue(
          viaOf == viaDerived,
          viaOf.hashCode() == viaDerived.hashCode()
        )
      },
      test("TypeId.of works for primitives") {
        val intOf = TypeId.of[Int]

        assertTrue(
          intOf.name == "Int",
          intOf == TypeId.int
        )
      },
      test("TypeId.of works for collections") {
        val listOf = TypeId.of[List[String]]

        assertTrue(
          listOf.name == "List",
          listOf.typeArgs.nonEmpty
        )
      },
      test("TypeId.of works for user-defined types") {
        val simpleClassOf = TypeId.of[SimpleClass]

        assertTrue(
          simpleClassOf.name == "SimpleClass"
        )
      },
      test("TypeId.of can be used as map key") {
        val map = Map[TypeId[_], String](
          TypeId.of[String] -> "string",
          TypeId.of[Int]    -> "int"
        )
        val stringVal: String  = map(TypeId.of[String])
        val intVal: String     = map(TypeId.of[Int])
        val doubleVal: Boolean = map.get(TypeId.of[Double]).isEmpty

        assertTrue(
          stringVal == "string",
          intVal == "int",
          doubleVal
        )
      }
    )
  )
}
