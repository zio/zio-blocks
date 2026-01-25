package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for nested product types to structural conversion. */
object NestedProductSpec extends ZIOSpecDefault {

  case class Address(street: String, city: String, zip: Int)
  case class Person(name: String, age: Int, address: Address)

  def spec: Spec[Any, Nothing] = suite("NestedProductSpec")(
    test("nested case class produces type name with all fields") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("name"), typeName.contains("age"), typeName.contains("address"))
    },
    test("structural schema preserves field names") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
      }
      assertTrue(fieldNames == Set("name", "age", "address"))
    },
    test("nested case class round-trip preserves data") {
      val person    = Person("Alice", 30, Address("123 Main St", "Springfield", 12345))
      val schema    = Schema.derived[Person]
      val dynamic   = schema.toDynamicValue(person)
      val roundTrip = schema.fromDynamicValue(dynamic)
      assertTrue(roundTrip == Right(person))
    }
  )
}
