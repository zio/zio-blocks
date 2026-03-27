package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.MigrationMacros.select
import zio.test.*

object MigrationMacroSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("MigrationMacroSpec")(
    test("select produces DynamicOptic for single field") {
      case class Person(name: String)
      val optic = select[Person](_.name)
      assertTrue(optic != null)
    },

    test("select produces DynamicOptic for nested field") {
      case class Address(street: String)
      case class Person(name: String, address: Address)
      val optic = select[Person](_.address.street)
      assertTrue(optic != null)
    },

    test("inField applies nested migration to nested record") {
      case class AddressV0(street: String, zip: String)
      case class AddressV1(streetName: String, postalCode: String)
      case class PersonV0(name: String, address: AddressV0)
      case class PersonV1(name: String, address: AddressV1)

      given Schema[AddressV0] = Schema.derived[AddressV0]
      given Schema[AddressV1] = Schema.derived[AddressV1]
      given Schema[PersonV0]  = Schema.derived[PersonV0]
      given Schema[PersonV1]  = Schema.derived[PersonV1]

      val addrMigration = Migration
        .newBuilder[AddressV0, AddressV1]
        .renameField(select[AddressV0](_.street), select[AddressV1](_.streetName))
        .renameField(select[AddressV0](_.zip), select[AddressV1](_.postalCode))
        .buildPartial

      val personMigration = Migration
        .newBuilder[PersonV0, PersonV1]
        .renameField(select[PersonV0](_.name), select[PersonV1](_.name))
        .inField(select[PersonV0](_.address), select[PersonV1](_.address), addrMigration)
        .buildPartial

      val result = personMigration(PersonV0("John", AddressV0("Main St", "10001")))
      assertTrue(result == Right(PersonV1("John", AddressV1("Main St", "10001"))))
    }
  )
}
