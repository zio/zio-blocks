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
          emailId.opaqueRepresentation.exists {
            case TypeRepr.Ref(typeId) => typeId.name == "String"
            case _                    => false
          },
          ageId.opaqueRepresentation.exists {
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
      }
    ),
    suite("Context Functions")(
      test("context function type extracts as ContextFunction TypeRepr") {
        val ctxFuncId = TypeId.derived[TypeAliases.Contextual]

        assertTrue(
          ctxFuncId.isAlias,
          ctxFuncId.aliasedType.exists {
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
