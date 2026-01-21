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
        val emailId = TypeId.derived[OpaqueTypes.Email]
        val ageId   = TypeId.derived[OpaqueTypes.Age]
        val listId  = TypeId.derived[OpaqueTypes.SafeList[Any]]

        assertTrue(
          emailId.name == "Email" && emailId.isOpaque,
          ageId.name == "Age" && ageId.isOpaque,
          listId.name == "SafeList" && listId.isOpaque && listId.arity == 1
        )
      },
      test("opaque type representations are extracted correctly") {
        val emailId = TypeId.derived[OpaqueTypes.Email]
        val ageId   = TypeId.derived[OpaqueTypes.Age]

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
        val emailId = TypeId.derived[OpaqueTypes.Email]
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
        val statusId = TypeId.derived[Status]

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
        val resultId = TypeId.derived[Result[Any]]

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
        val dirId = TypeId.derived[Direction]

        assertTrue(
          dirId.isEnum,
          dirId.enumCases.forall(_.isObjectCase)
        )
      },
      test("enum case is subtype of parent enum") {
        enum Status { case Pending, Active }

        val pendingId = TypeId.derived[Status.Pending.type]
        val statusId  = TypeId.derived[Status]

        assertTrue(pendingId.isSubtypeOf(statusId))
      }
    ),
    suite("Union and Intersection Types")(
      test("union type alias works correctly") {
        type StringOrInt = String | Int
        val unionId = TypeId.derived[StringOrInt]

        assertTrue(
          unionId.name == "StringOrInt" && unionId.isAlias
        )
      },
      test("intersection type alias works correctly") {
        trait A
        trait B
        type AAndB = A & B
        val intersectionId = TypeId.derived[AAndB]

        assertTrue(
          intersectionId.name == "AAndB" && intersectionId.isAlias
        )
      },
      test("union type aliased type is Union TypeRepr") {
        type StringOrInt = String | Int
        val unionId = TypeId.derived[StringOrInt]

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
        val intersectionId = TypeId.derived[AAndB]

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
        val unionId1 = TypeId.derived[StringOrInt]
        val unionId2 = TypeId.derived[StringOrInt]

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
        val intersectionId1 = TypeId.derived[CAndD]
        val intersectionId2 = TypeId.derived[CAndD]

        assertTrue(
          intersectionId1.isEquivalentTo(intersectionId2),
          intersectionId1.isSubtypeOf(intersectionId2),
          intersectionId1.isSupertypeOf(intersectionId2)
        )
      },
      test("different union types are not equivalent") {
        type StringOrInt    = String | Int
        type StringOrDouble = String | Double
        val unionId1 = TypeId.derived[StringOrInt]
        val unionId2 = TypeId.derived[StringOrDouble]

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
        val intersectionId1 = TypeId.derived[EAndF]
        val intersectionId2 = TypeId.derived[EAndG]

        assertTrue(
          !intersectionId1.isEquivalentTo(intersectionId2),
          !intersectionId1.isSubtypeOf(intersectionId2),
          !intersectionId1.isSupertypeOf(intersectionId2)
        )
      },
      test("union type is not subtype of its member types") {
        type StringOrInt = String | Int
        val unionId  = TypeId.derived[StringOrInt]
        val stringId = TypeId.derived[String]
        val intId    = TypeId.derived[Int]

        // A union type alias is a distinct type, not a subtype of its members
        assertTrue(
          !unionId.isSubtypeOf(stringId),
          !unionId.isSubtypeOf(intId)
        )
      },
      test("intersection type is not supertype of its member types") {
        trait H
        trait I
        type HAndI = H & I
        val intersectionId = TypeId.derived[HAndI]
        val hId            = TypeId.derived[H]
        val iId            = TypeId.derived[I]

        // An intersection type alias is a distinct type, not a supertype of its members
        assertTrue(
          !intersectionId.isSupertypeOf(hId),
          !intersectionId.isSupertypeOf(iId)
        )
      },
      test("union types with same members in different order are equal") {
        val union1 = TypeId.derived[Int | String]
        val union2 = TypeId.derived[String | Int]

        assertTrue(
          union1 == union2,
          union1.hashCode() == union2.hashCode()
        )
      },
      test("intersection types with same members in different order are equal") {
        trait J
        trait K
        val intersection1 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[J]),
            TypeRepr.Ref(TypeId.derived[K])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[K]),
            TypeRepr.Ref(TypeId.derived[J])
          )
        )

        assertTrue(
          intersection1 == intersection2,
          intersection1.hashCode() == intersection2.hashCode()
        )
      },
      test("union toString preserves declaration order") {
        type StringOrInt = String | Int
        type IntOrString = Int | String
        val stringOrInt = TypeId.derived[StringOrInt]
        val intOrString = TypeId.derived[IntOrString]

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
            TypeRepr.Ref(TypeId.derived[L]),
            TypeRepr.Ref(TypeId.derived[M])
          )
        )
        val intersection2 = TypeRepr.Intersection(
          List(
            TypeRepr.Ref(TypeId.derived[M]),
            TypeRepr.Ref(TypeId.derived[L])
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
      }
    ),
    suite("Opaque Type Nominality")(
      test("opaque types are nominally distinct from their underlying type") {
        val emailId  = TypeId.derived[OpaqueTypes.Email]
        val stringId = TypeId.derived[String]

        // Opaque types should NOT be subtypes of their underlying type (outside defining scope)
        assertTrue(
          !emailId.isSubtypeOf(stringId),
          !stringId.isSubtypeOf(emailId),
          !emailId.isEquivalentTo(stringId)
        )
      },
      test("different opaque types with same underlying type are not equivalent") {
        // Create another opaque type with String underlying type for comparison
        val emailId = TypeId.derived[OpaqueTypes.Email]
        val ageId   = TypeId.derived[OpaqueTypes.Age]

        assertTrue(
          !emailId.isSubtypeOf(ageId),
          !ageId.isSubtypeOf(emailId),
          !emailId.isEquivalentTo(ageId)
        )
      },
      test("opaque type is equivalent to itself") {
        val emailId1 = TypeId.derived[OpaqueTypes.Email]
        val emailId2 = TypeId.derived[OpaqueTypes.Email]

        assertTrue(
          emailId1.isEquivalentTo(emailId2),
          emailId1.isSubtypeOf(emailId2),
          emailId2.isSubtypeOf(emailId1)
        )
      },
      test("opaque type representation is accessible") {
        val emailId = TypeId.derived[OpaqueTypes.Email]

        // The representation can be accessed to know the underlying type
        assertTrue(
          emailId.representation.exists {
            case TypeRepr.Ref(id) => id.name == "String"
            case _                => false
          }
        )
      }
    ),
    suite("Context Functions")(
      test("context function type extracts as ContextFunction TypeRepr") {
        val ctxFuncId = TypeId.derived[TypeAliases.Contextual]

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
    )
  )
}
