package zio.blocks.typeid

import zio.test._

object Scala3DerivationSpec extends ZIOSpecDefault {

  object OpaqueTypes {
    opaque type Email       = String
    opaque type Age         = Int
    opaque type SafeList[A] = List[A]
  }

  object TypeAliases {
    type Age          = Int
    type StringMap[V] = Map[String, V]
  }

  def spec = suite("Scala 3 TypeId Derivation")(
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
    test("type aliases work correctly") {
      val ageId = TypeId.derived[TypeAliases.Age]
      val mapId = TypeId.derived[TypeAliases.StringMap[Int]]

      assertTrue(
        ageId.name == "Age" && ageId.isAlias,
        mapId.name == "StringMap" && mapId.isAlias,
        ageId.aliasedType.exists {
          case TypeRepr.Ref(typeId) => typeId.name == "Int"
          case _                    => false
        }
      )
    },
    test("scala 3 specific types work") {
      // Union type
      type StringOrInt = String | Int
      val unionId = TypeId.derived[StringOrInt]

      // Intersection type
      trait A
      trait B
      type AAndB = A & B
      val intersectionId = TypeId.derived[AAndB]

      // Enum
      enum Color { case Red, Green, Blue }
      val enumId = TypeId.derived[Color]

      assertTrue(
        unionId.name == "StringOrInt" && unionId.isAlias,
        intersectionId.name == "AAndB" && intersectionId.isAlias,
        enumId.name == "Color"
      )
    },
    test("structural types with Selectable work correctly") {
      // Structural type using Selectable
      type Person = {
        def name: String
        def age: Int
      } & Selectable

      val structuralId = TypeId.derived[Person]

      // Dynamic type (structural type without members)
      val dynamicId = TypeId.derived[Selectable]

      assertTrue(
        structuralId.name == "Person" && structuralId.isAlias,
        dynamicId.name == "Selectable"
      )
    }
  )
}
