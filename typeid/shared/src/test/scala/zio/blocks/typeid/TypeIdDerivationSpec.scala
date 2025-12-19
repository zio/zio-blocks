package zio.blocks.typeid

import zio.test._

/**
 * Tests for macro derivation of TypeId. These tests verify compile-time
 * derivation works correctly.
 */
object TypeIdDerivationSpec extends ZIOSpecDefault {

  // Sample types for testing
  case class Person(name: String, age: Int)
  case class GenericBox[A](value: A)
  case class TwoParams[A, B](first: A, second: B)

  object Container {
    case class Nested(x: Int)
  }

  def spec = suite("TypeId Macro Derivation")(
    test("derives TypeId for simple case class") {
      val id = TypeId.derive[Person]
      assertTrue(
        id.name == "Person",
        id.isNominal,
        id.typeParams.isEmpty
      )
    },
    test("derives TypeId for generic case class") {
      val id = TypeId.derive[GenericBox[Int]]
      assertTrue(
        id.name == "GenericBox",
        id.isNominal
      )
    },
    test("derives TypeId for two-param generic") {
      val id = TypeId.derive[TwoParams[String, Int]]
      assertTrue(
        id.name == "TwoParams",
        id.isNominal
      )
    },
    test("derives TypeId for nested class") {
      val id = TypeId.derive[Container.Nested]
      assertTrue(
        id.name == "Nested",
        id.isNominal,
        // The owner should contain Container
        id.owner.asString.contains("Container")
      )
    },
    test("derives TypeId for primitive types") {
      val intId    = TypeId.derive[Int]
      val stringId = TypeId.derive[String]
      val boolId   = TypeId.derive[Boolean]
      assertTrue(
        intId.name == "Int",
        stringId.name == "String",
        boolId.name == "Boolean"
      )
    },
    test("derives TypeId for standard library types") {
      val listId   = TypeId.derive[List[Int]]
      val optionId = TypeId.derive[Option[String]]
      val mapId    = TypeId.derive[Map[String, Int]]
      assertTrue(
        listId.name == "List",
        optionId.name == "Option",
        mapId.name == "Map"
      )
    },
    test("derives TypeId for java types") {
      val uuidId    = TypeId.derive[java.util.UUID]
      val instantId = TypeId.derive[java.time.Instant]
      assertTrue(
        uuidId.name == "UUID",
        instantId.name == "Instant"
      )
    },
    test("derived TypeId has correct owner chain") {
      val id = TypeId.derive[scala.collection.immutable.List[Int]]
      assertTrue(
        id.owner.asString.contains("scala") &&
          id.owner.asString.contains("collection") &&
          id.owner.asString.contains("immutable")
      )
    }
  )
}
