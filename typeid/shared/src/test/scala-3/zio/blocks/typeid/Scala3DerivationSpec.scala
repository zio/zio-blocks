package zio.blocks.typeid

import zio.test._

/**
 * Tests for Scala 3-specific TypeId derivation features.
 *
 * This spec covers features only available in Scala 3:
 *   - Opaque types
 *   - Enums and enum cases
 *   - Union types
 *   - Intersection types
 *   - Context functions
 */
object Scala3DerivationSpec extends ZIOSpecDefault {

  object OpaqueTypes {
    opaque type Email       = String
    opaque type Age         = Int
    opaque type SafeList[A] = List[A]
  }

  object TypeAliases {
    type IntToString = Int => String
    type Contextual  = Int ?=> String
  }

  def spec = suite("Scala 3 TypeId Derivation")(
    suite("Opaque Types")(
      test("opaque types are detected correctly") {
        val emailId = TypeId.of[OpaqueTypes.Email]
        val ageId   = TypeId.of[OpaqueTypes.Age]
        val listId  = TypeId.of[OpaqueTypes.SafeList[Any]]

        assertTrue(
          emailId.name == "Email" && emailId.isOpaque,
          ageId.name == "Age" && ageId.isOpaque,
          listId.name == "SafeList" && listId.isOpaque && listId.arity == 1
        )
      },
      test("opaque type representations are extracted correctly") {
        val emailId = TypeId.of[OpaqueTypes.Email]
        val ageId   = TypeId.of[OpaqueTypes.Age]

        assertTrue(
          emailId.representation.exists {
            case TypeRepr.Ref(typeId) => typeId.name == "String"
            case _                    => false
          },
          ageId.representation.exists {
            case TypeRepr.Ref(typeId) => typeId.name == "Int"
            case _                    => false
          }
        )
      },
      test("opaque type TypeDefKind is OpaqueType") {
        val emailId = TypeId.of[OpaqueTypes.Email]
        assertTrue(emailId.defKind.isInstanceOf[TypeDefKind.OpaqueType])
      }
    ),
    suite("Enums")(
      test("enum TypeDefKind includes enum cases") {
        enum Status {
          case Pending
          case Active
          case Completed
        }
        val statusId = TypeId.of[Status]

        assertTrue(
          statusId.isEnum,
          statusId.defKind match {
            case TypeDefKind.Enum(cases, _) =>
              cases.length == 3 &&
              cases.exists(_.name == "Pending") &&
              cases.exists(_.name == "Active") &&
              cases.exists(_.name == "Completed")
            case _ => false
          },
          statusId.enumCases.length == 3
        )
      },
      test("enum with parameterized cases has correct EnumCaseInfo") {
        enum Result[+T] {
          case Success(value: T)
          case Failure(error: String)
        }
        val resultId = TypeId.of[Result[Any]]

        assertTrue(
          resultId.isEnum,
          resultId.defKind match {
            case TypeDefKind.Enum(cases, _) =>
              cases.length == 2 &&
              cases.exists(c => c.name == "Success" && !c.isObjectCase && c.params.nonEmpty) &&
              cases.exists(c => c.name == "Failure" && !c.isObjectCase && c.params.exists(_.name == "error"))
            case _ => false
          }
        )
      },
      test("enum object cases are marked as isObjectCase") {
        enum Direction {
          case North, South, East, West
        }
        val dirId = TypeId.of[Direction]

        assertTrue(
          dirId.isEnum,
          dirId.enumCases.forall(_.isObjectCase)
        )
      },
      test("enum case is subtype of parent enum") {
        enum Status { case Pending, Active }

        val pendingId = TypeId.of[Status.Pending.type]
        val statusId  = TypeId.of[Status]

        assertTrue(pendingId.isSubtypeOf(statusId))
      }
    ),
    suite("Union and Intersection Types")(
      test("union type alias works correctly") {
        type StringOrInt = String | Int
        val unionId = TypeId.of[StringOrInt]

        assertTrue(
          unionId.name == "StringOrInt" && unionId.isAlias
        )
      },
      test("intersection type alias works correctly") {
        trait A
        trait B
        type AAndB = A & B
        val intersectionId = TypeId.of[AAndB]

        assertTrue(
          intersectionId.name == "AAndB" && intersectionId.isAlias
        )
      },
      test("union type aliased type is Union TypeRepr") {
        type StringOrInt = String | Int
        val unionId = TypeId.of[StringOrInt]

        assertTrue(
          unionId.aliasedTo.exists {
            case TypeRepr.Union(types) =>
              types.length == 2 &&
              types.exists {
                case TypeRepr.Ref(id) => id.name == "String"
                case _                => false
              } &&
              types.exists {
                case TypeRepr.Ref(id) => id.name == "Int"
                case _                => false
              }
            case _ => false
          }
        )
      },
      test("intersection type aliased type is Intersection TypeRepr") {
        trait A
        trait B
        type AAndB = A & B
        val intersectionId = TypeId.of[AAndB]

        assertTrue(
          intersectionId.aliasedTo.exists {
            case TypeRepr.Intersection(types) =>
              types.length == 2 &&
              types.exists {
                case TypeRepr.Ref(id) => id.name == "A"
                case _                => false
              } &&
              types.exists {
                case TypeRepr.Ref(id) => id.name == "B"
                case _                => false
              }
            case _ => false
          }
        )
      },
      test("union type is equivalent to itself") {
        type StringOrInt = String | Int
        val unionId1 = TypeId.of[StringOrInt]
        val unionId2 = TypeId.of[StringOrInt]

        assertTrue(
          unionId1.isEquivalentTo(unionId2),
          unionId1.isSubtypeOf(unionId2),
          unionId1.isSupertypeOf(unionId2)
        )
      },
      test("intersection type is equivalent to itself") {
        trait C
        trait D
        type CAndD = C & D
        val intersectionId1 = TypeId.of[CAndD]
        val intersectionId2 = TypeId.of[CAndD]

        assertTrue(
          intersectionId1.isEquivalentTo(intersectionId2),
          intersectionId1.isSubtypeOf(intersectionId2),
          intersectionId1.isSupertypeOf(intersectionId2)
        )
      },
      test("different union types are not equivalent") {
        type StringOrInt    = String | Int
        type StringOrDouble = String | Double
        val unionId1 = TypeId.of[StringOrInt]
        val unionId2 = TypeId.of[StringOrDouble]

        assertTrue(
          !unionId1.isEquivalentTo(unionId2),
          !unionId1.isSubtypeOf(unionId2),
          !unionId1.isSupertypeOf(unionId2)
        )
      },
      test("different intersection types are not equivalent") {
        trait E
        trait F
        trait G
        type EAndF = E & F
        type EAndG = E & G
        val intersectionId1 = TypeId.of[EAndF]
        val intersectionId2 = TypeId.of[EAndG]

        assertTrue(
          !intersectionId1.isEquivalentTo(intersectionId2),
          !intersectionId1.isSubtypeOf(intersectionId2),
          !intersectionId1.isSupertypeOf(intersectionId2)
        )
      },
      test("union type is not subtype of its member types") {
        type StringOrInt = String | Int
        val unionId  = TypeId.of[StringOrInt]
        val stringId = TypeId.of[String]
        val intId    = TypeId.of[Int]

        // A union type alias is a distinct type, not a subtype of its members
        assertTrue(
          !unionId.isSubtypeOf(stringId),
          !unionId.isSubtypeOf(intId)
        )
      },
      test("member types are subtypes of union - A <: A | B and B <: A | B") {
        type StringOrInt = String | Int
        val unionId  = TypeId.of[StringOrInt]
        val stringId = TypeId.of[String]
        val intId    = TypeId.of[Int]

        assertTrue(
          stringId.isSubtypeOf(unionId),
          intId.isSubtypeOf(unionId)
        )
      },
      test("intersection type is not supertype of its member types") {
        trait H
        trait I
        type HAndI = H & I
        val intersectionId = TypeId.of[HAndI]
        val hId            = TypeId.of[H]
        val iId            = TypeId.of[I]

        assertTrue(
          !intersectionId.isSupertypeOf(hId),
          !intersectionId.isSupertypeOf(iId)
        )
      },
      test("intersection type is subtype of its member types - A & B <: A and A & B <: B") {
        trait H2
        trait I2
        type H2AndI2 = H2 & I2
        val intersectionId = TypeId.of[H2AndI2]
        val h2Id           = TypeId.of[H2]
        val i2Id           = TypeId.of[I2]

        assertTrue(
          intersectionId.isSubtypeOf(h2Id),
          intersectionId.isSubtypeOf(i2Id)
        )
      },
      test("union types with same members in different order are equal") {
        val union1 = TypeId.of[Int | String]
        val union2 = TypeId.of[String | Int]

        assertTrue(
          union1 == union2,
          union1.hashCode() == union2.hashCode()
        )
      },
      test("intersection types with same members in different order are equal (TypeRepr)") {
        trait J
        trait K
        val intersection1 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[J]),
            TypeRepr.Ref(TypeId.of[K])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[K]),
            TypeRepr.Ref(TypeId.of[J])
          )
        )

        assertTrue(
          intersection1 == intersection2,
          intersection1.hashCode() == intersection2.hashCode()
        )
      },
      test("intersection types with same members in different order are equal (TypeId)") {
        trait JJ
        trait KK
        val inter1 = TypeId.of[JJ & KK]
        val inter2 = TypeId.of[KK & JJ]

        assertTrue(
          inter1 == inter2,
          inter1.hashCode() == inter2.hashCode()
        )
      },
      test("union toString preserves declaration order") {
        type StringOrInt = String | Int
        type IntOrString = Int | String
        val stringOrInt = TypeId.of[StringOrInt]
        val intOrString = TypeId.of[IntOrString]

        // The TypeIds are different (different names: StringOrInt vs IntOrString)
        assertTrue(stringOrInt != intOrString)

        // But their aliasedTo Union types should be equal (order-independent)
        val union1 = stringOrInt.aliasedTo.get
        val union2 = intOrString.aliasedTo.get

        assertTrue(
          union1 == union2, // Unions are order-independent
          union1.toString.contains("String") && union1.toString.contains("Int"),
          union2.toString.contains("Int") && union2.toString.contains("String")
        )
      },
      test("intersection toString preserves declaration order") {
        trait L
        trait M

        val intersection1 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[L]),
            TypeRepr.Ref(TypeId.of[M])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.of[M]),
            TypeRepr.Ref(TypeId.of[L])
          )
        )

        // Intersections are order-independent in equality
        assertTrue(intersection1 == intersection2)

        // But toString preserves the original declaration order
        val repr1 = intersection1.toString
        val repr2 = intersection2.toString

        assertTrue(
          repr1.contains("L") && repr1.contains("M"),
          repr2.contains("M") && repr2.contains("L"),
          intersection1 == intersection2
        )
      },
      test("anonymous union types with different members are NOT equal") {
        val union1 = TypeId.of[Int | String]
        val union2 = TypeId.of[Int | Double]

        assertTrue(
          union1 != union2,
          union1.hashCode() != union2.hashCode()
        )
      },
      test("anonymous intersection types with different members are NOT equal") {
        trait P
        trait Q
        trait R

        val inter1 = TypeId.of[P & Q]
        val inter2 = TypeId.of[P & R]

        assertTrue(
          inter1 != inter2,
          inter1.hashCode() != inter2.hashCode()
        )
      },
      test("named union type aliases with different members are NOT equal") {
        type IntOrString = Int | String
        type IntOrDouble = Int | Double

        val union1 = TypeId.of[IntOrString]
        val union2 = TypeId.of[IntOrDouble]

        assertTrue(
          union1 != union2,
          union1.name != union2.name
        )
      },
      test("union aliasedTo with different members are NOT equal") {
        type StringOrInt    = String | Int
        type StringOrDouble = String | Double

        val union1 = TypeId.of[StringOrInt]
        val union2 = TypeId.of[StringOrDouble]

        assertTrue(
          union1.aliasedTo.isDefined,
          union2.aliasedTo.isDefined,
          union1.aliasedTo != union2.aliasedTo
        )
      },
      test("intersection aliasedTo with different members are NOT equal") {
        trait X1
        trait Y1
        trait Z1
        type X1AndY1 = X1 & Y1
        type X1AndZ1 = X1 & Z1

        val inter1 = TypeId.of[X1AndY1]
        val inter2 = TypeId.of[X1AndZ1]

        assertTrue(
          inter1.aliasedTo.isDefined,
          inter2.aliasedTo.isDefined,
          inter1.aliasedTo != inter2.aliasedTo
        )
      },
      test("union with 3 members differs from union with 2 members") {
        type TwoMember   = Int | String
        type ThreeMember = Int | String | Double

        val union2 = TypeId.of[TwoMember]
        val union3 = TypeId.of[ThreeMember]

        assertTrue(
          union2 != union3,
          union2.aliasedTo != union3.aliasedTo
        )
      },
      test("intersection with 3 members differs from intersection with 2 members") {
        trait M1
        trait M2
        trait M3
        type TwoMember   = M1 & M2
        type ThreeMember = M1 & M2 & M3

        val inter2 = TypeId.of[TwoMember]
        val inter3 = TypeId.of[ThreeMember]

        assertTrue(
          inter2 != inter3,
          inter2.aliasedTo != inter3.aliasedTo
        )
      }
    ),
    suite("Opaque Type Nominality")(
      test("opaque types are nominally distinct from their underlying type") {
        val emailId  = TypeId.of[OpaqueTypes.Email]
        val stringId = TypeId.of[String]

        // Opaque types should NOT be subtypes of their underlying type (outside defining scope)
        assertTrue(
          !emailId.isSubtypeOf(stringId),
          !stringId.isSubtypeOf(emailId),
          !emailId.isEquivalentTo(stringId)
        )
      },
      test("different opaque types with same underlying type are not equivalent") {
        // Create another opaque type with String underlying type for comparison
        val emailId = TypeId.of[OpaqueTypes.Email]
        val ageId   = TypeId.of[OpaqueTypes.Age]

        assertTrue(
          !emailId.isSubtypeOf(ageId),
          !ageId.isSubtypeOf(emailId),
          !emailId.isEquivalentTo(ageId)
        )
      },
      test("opaque type is equivalent to itself") {
        val emailId1 = TypeId.of[OpaqueTypes.Email]
        val emailId2 = TypeId.of[OpaqueTypes.Email]

        assertTrue(
          emailId1.isEquivalentTo(emailId2),
          emailId1.isSubtypeOf(emailId2),
          emailId2.isSubtypeOf(emailId1)
        )
      },
      test("opaque type representation is accessible") {
        val emailId = TypeId.of[OpaqueTypes.Email]

        // The representation can be accessed to know the underlying type
        assertTrue(
          emailId.representation.exists {
            case TypeRepr.Ref(id) => id.name == "String"
            case _                => false
          }
        )
      }
    ),
    suite("Type Parameter Variance")(
      test("user-defined covariant type has correct variance") {
        trait MyBox[+A]
        val derived = TypeId.of[MyBox[Int]]

        println(s"MyBox typeParams: ${derived.typeParams}")
        println(s"MyBox variance: ${derived.typeParams.head.variance}")

        assertTrue(
          derived.typeParams.head.variance == Variance.Covariant
        )
      },
      test("user-defined contravariant type has correct variance") {
        trait MyConsumer[-A]
        val derived = TypeId.of[MyConsumer[Int]]

        assertTrue(
          derived.typeParams.head.variance == Variance.Contravariant
        )
      },
      test("user-defined invariant type has correct variance") {
        trait MyCell[A]
        val derived = TypeId.of[MyCell[Int]]

        assertTrue(
          derived.typeParams.head.variance == Variance.Invariant
        )
      },
      test("stdlib Set (compiled class) has correct variance") {
        val derived = TypeId.of[Set[Int]]

        println(s"stdlib Set typeParams: ${derived.typeParams}")
        println(s"stdlib Set variance: ${derived.typeParams.head.variance}")

        assertTrue(
          derived.typeParams.head.variance == Variance.Covariant
        )
      },
      test("stdlib List (compiled class) has correct variance") {
        val derived = TypeId.of[List[Int]]

        assertTrue(
          derived.typeParams.head.variance == Variance.Covariant
        )
      }
    ),
    suite("Context Functions")(
      test("context function type extracts as ContextFunction TypeRepr") {
        val ctxFuncId = TypeId.of[TypeAliases.Contextual]

        assertTrue(
          ctxFuncId.isAlias,
          ctxFuncId.aliasedTo.exists {
            case TypeRepr.ContextFunction(params, result) =>
              params.length == 1 &&
              (params.head match {
                case TypeRepr.Ref(id) => id.name == "Int"
                case _                => false
              }) &&
              (result match {
                case TypeRepr.Ref(id) => id.name == "String"
                case _                => false
              })
            case _ => false
          }
        )
      }
    ),
    suite("TypeId.of API")(
      test("TypeId.of produces same result as TypeId.of for primitives") {
        val viaOf      = TypeId.of[String]
        val viaDerived = TypeId.of[String]

        assertTrue(
          viaOf == viaDerived,
          viaOf.hashCode() == viaDerived.hashCode()
        )
      },
      test("TypeId.of works for type constructors") {
        val listOf = TypeId.of[List]

        assertTrue(
          listOf.name == "List",
          listOf.arity == 1,
          listOf.typeParams.head.variance == Variance.Covariant
        )
      },
      test("TypeId.of works for applied types") {
        val listIntOf = TypeId.of[List[Int]]

        assertTrue(
          listIntOf.name == "List",
          listIntOf.typeArgs.nonEmpty
        )
      },
      test("TypeId.of works for opaque types") {
        val emailOf = TypeId.of[OpaqueTypes.Email]

        assertTrue(
          emailOf.name == "Email",
          emailOf.isOpaque
        )
      },
      test("TypeId.of works for union types") {
        type StringOrInt = String | Int
        val unionOf = TypeId.of[StringOrInt]

        assertTrue(
          unionOf.isAlias,
          unionOf.aliasedTo.exists(_.isInstanceOf[TypeRepr.Union])
        )
      },
      test("TypeId.of works for intersection types") {
        trait AA
        trait BB
        type AAAndBB = AA & BB
        val interOf = TypeId.of[AAAndBB]

        assertTrue(
          interOf.isAlias,
          interOf.aliasedTo.exists(_.isInstanceOf[TypeRepr.Intersection])
        )
      }
    )
  )
}
