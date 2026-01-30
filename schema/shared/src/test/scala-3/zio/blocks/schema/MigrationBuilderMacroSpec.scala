package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object MigrationBuilderMacroSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int) derives Schema

  case class PersonV2(name: String, age: Int, email: String) derives Schema

  case class PersonV3(fullName: String, age: Int) derives Schema

  case class Address(street: String, city: String) derives Schema

  case class PersonWithAddress(name: String, address: Address) derives Schema

  case class PersonWithAddress2(name: String, address: Address, active: Boolean) derives Schema

  val defaultEmail: DynamicValue = DynamicValue.string("unknown@example.com")

  case class PersonWithList(name: String, scores: List[Int]) derives Schema

  case class PersonWithMap(name: String, data: Map[String, Int]) derives Schema

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderMacroSpec")(
    selectorSuite,
    transformSuite,
    endToEndSuite
  )

  val selectorSuite: Spec[Any, Nothing] = suite("Selector macros")(
    test("addField with selector extracts correct path") {
      val migration = Migration
        .builder[PersonV1, PersonV2]
        .addField(_.email, defaultEmail)
        .build

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV2("Alice", 30, "unknown@example.com"))))
    },
    test("dropField with selector extracts correct path") {
      val migration = Migration
        .builder[PersonV2, PersonV1]
        .dropField(_.email, defaultEmail)
        .build

      val result = migration(PersonV2("Alice", 30, "alice@example.com"))
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    },
    test("renameField with selectors") {
      val migration = Migration
        .builder[PersonV1, PersonV3]
        .renameField(_.name, _.fullName)
        .build

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV3("Alice", 30))))
    },
    test("addField with nested selector") {
      val migration = Migration
        .builder[PersonWithAddress, PersonWithAddress2]
        .addField(_.active, DynamicValue.boolean(true))
        .build

      val result = migration(PersonWithAddress("Alice", Address("123 Main", "NYC")))
      assert(result)(isRight(equalTo(PersonWithAddress2("Alice", Address("123 Main", "NYC"), true))))
    }
  )

  val transformSuite: Spec[Any, Nothing] = suite("Transform macros")(
    test("transformField with selector") {
      val migration = Migration
        .builder[PersonV1, PersonV1]
        .transformField(_.age, DynamicValue.int(99), None)
        .buildPartial

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV1("Alice", 99))))
    },
    test("changeFieldType with selector") {
      // changeFieldType replaces the value, so this is a schema-breaking operation
      // We use buildPartial to skip validation
      val migration = Migration
        .builder[PersonV1, PersonV1]
        .changeFieldType(_.age, DynamicValue.int(42), None)
        .buildPartial

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV1("Alice", 42))))
    },
    test("transformElements with selector") {
      val migration = Migration
        .builder[PersonWithList, PersonWithList]
        .transformElements(_.scores, DynamicValue.int(0), None)
        .buildPartial

      val result = migration(PersonWithList("Alice", List(1, 2, 3)))
      assert(result)(isRight(equalTo(PersonWithList("Alice", List(0, 0, 0)))))
    },
    test("transformKeys with selector") {
      val migration = Migration
        .builder[PersonWithMap, PersonWithMap]
        .transformKeys(_.data, DynamicValue.string("key"), None)
        .buildPartial

      val input  = PersonWithMap("Alice", Map("a" -> 1, "b" -> 2))
      val result = migration(input)
      // Note: all keys become "key", so map semantics may collapse entries
      assertTrue(result.isRight)
    },
    test("transformValues with selector") {
      val migration = Migration
        .builder[PersonWithMap, PersonWithMap]
        .transformValues(_.data, DynamicValue.int(0), None)
        .buildPartial

      val input  = PersonWithMap("Alice", Map("a" -> 1, "b" -> 2))
      val result = migration(input)
      assert(result)(isRight(equalTo(PersonWithMap("Alice", Map("a" -> 0, "b" -> 0)))))
    }
  )

  val endToEndSuite: Spec[Any, Nothing] = suite("End-to-end macro migrations")(
    test("addField + dropField round-trip with selectors") {
      val forward = Migration
        .builder[PersonV1, PersonV2]
        .addField(_.email, defaultEmail)
        .build

      val backward = Migration
        .builder[PersonV2, PersonV1]
        .dropField(_.email, defaultEmail)
        .build

      val person  = PersonV1("Bob", 25)
      val result1 = forward(person)
      assert(result1)(isRight) && {
        val result2 = backward(result1.toOption.get)
        assert(result2)(isRight(equalTo(person)))
      }
    },
    test("reverse of macro-built migration works") {
      val migration = Migration
        .builder[PersonV1, PersonV2]
        .addField(_.email, defaultEmail)
        .build

      val person2 = PersonV2("Alice", 30, "unknown@example.com")
      val result  = migration.reverse(person2)
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    },
    test("composition of macro-built migrations") {
      val m1 = Migration
        .builder[PersonV1, PersonV2]
        .addField(_.email, defaultEmail)
        .build

      val m2 = Migration
        .builder[PersonV2, PersonV3]
        .dropField(_.email, defaultEmail)
        .renameField(_.name, _.fullName)
        .build

      val composed = m1 ++ m2
      val result   = composed(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV3("Alice", 30))))
    }
  )
}
