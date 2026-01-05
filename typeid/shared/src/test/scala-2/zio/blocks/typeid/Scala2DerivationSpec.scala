package zio.blocks.typeid

import zio.test._

object Scala2DerivationSpec extends ZIOSpecDefault {

  case class CustomType(value: Int)

  object TypeAliases {
    type Age = Int
    type MyCustom = CustomType
    type StringMap[V] = Map[String, V]
  }

  def spec = suite("Scala 2 TypeId Derivation")(
    test("type aliases are detected correctly") {
      val ageId = TypeId.derived[TypeAliases.Age]
      val customId = TypeId.derived[TypeAliases.MyCustom]
      val mapId = TypeId.derived[TypeAliases.StringMap[Int]]

      assertTrue(
        ageId.name == "Age" && ageId.isAlias,
        customId.name == "MyCustom" && customId.isAlias,
        mapId.name == "StringMap" && mapId.isAlias
      )
    },
    test("aliased types are extracted correctly") {
      val ageId = TypeId.derived[TypeAliases.Age]
      val mapId = TypeId.derived[TypeAliases.StringMap[Int]]

      assertTrue(
        ageId.aliasedType.exists {
          case TypeRepr.Ref(typeId) => typeId.name == "Int"
          case _ => false
        },
        mapId.aliasedType.exists {
          case TypeRepr.Applied(TypeRepr.Ref(typeId), _) => typeId.name == "Map"
          case _ => false
        }
      )
    }
  )
}

