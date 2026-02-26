package zio.blocks.schema.migration

import zio.test.*
import zio.blocks.schema.*

object MigrationBuildValidationSpec extends ZIOSpecDefault {

  private def literal[A: Schema](value: A): SchemaExpr[Any, A] =
    SchemaExpr.literal(value)

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuildValidationSpec")(
    suite("build validation")(
      test("build succeeds when all fields are handled via auto-mapping") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("build succeeds when new field is added") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(0))
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("build succeeds when field is dropped") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(0))
          .build

        val input  = PersonV1("Alice", 30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },
      test("build succeeds when field is renamed") {
        case class PersonV1(firstName: String)
        case class PersonV2(fullName: String)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        val input  = PersonV1("Alice")
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice")))
      },
      test("build succeeds when field is transformed") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Long)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.age, _.age, literal(30L))
          .build

        val input  = PersonV1(30)
        val result = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      },
      test("build fails to compile when target field is missing") {
        typeCheck {
          """
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration
          
          case class V1(name: String)
          case class V2(name: String, age: Int)

          given Schema[V1] = Schema.derived[V1]
          given Schema[V2] = Schema.derived[V2]

          Migration.newBuilder[V1, V2].build
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("build fails to compile when source field is not handled") {
        typeCheck {
          """
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration
          
          case class V1(name: String, age: Int)
          case class V2(name: String)

          given Schema[V1] = Schema.derived[V1]
          given Schema[V2] = Schema.derived[V2]

          Migration.newBuilder[V1, V2].build
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("buildPartial succeeds even when fields are missing") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, birthYear: Int)

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .buildPartial

        assertTrue(migration.actions.isEmpty)
      },
      test("build succeeds with migrateField for nested type migration") {
        case class AddressV1(street: String, city: String)
        case class AddressV2(street: String, city: String, zip: String)
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        given Schema[AddressV1] = Schema.derived[AddressV1]
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV1]  = Schema.derived[PersonV1]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val addressMigration = Migration
          .newBuilder[AddressV1, AddressV2]
          .addField(_.zip, literal("00000"))
          .build

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .migrateField(_.address, addressMigration)
          .build

        val input  = PersonV1("Alice", AddressV1("123 Main St", "Springfield"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", AddressV2("123 Main St", "Springfield", "00000"))))
      },
      test("build fails to compile when nested field is missing without migrateField") {
        typeCheck {
          """
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration

          case class AddressV1(street: String, city: String)
          case class AddressV2(street: String, city: String, zip: String)
          case class PersonV1(name: String, address: AddressV1)
          case class PersonV2(name: String, address: AddressV2)

          given Schema[AddressV1] = Schema.derived[AddressV1]
          given Schema[AddressV2] = Schema.derived[AddressV2]
          given Schema[PersonV1]  = Schema.derived[PersonV1]
          given Schema[PersonV2]  = Schema.derived[PersonV2]

          Migration.newBuilder[PersonV1, PersonV2].build
          """
        }.map { result =>
          assertTrue(result.isLeft)
        }
      },
      test("build succeeds with migrateField when dropping nested field") {
        case class AddressV1(street: String, city: String, zip: String)
        case class AddressV2(street: String, city: String)
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        given Schema[AddressV1] = Schema.derived[AddressV1]
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV1]  = Schema.derived[PersonV1]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val addressMigration = Migration
          .newBuilder[AddressV1, AddressV2]
          .dropField(_.zip, literal("00000"))
          .build

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .migrateField(_.address, addressMigration)
          .build

        val input  = PersonV1("Alice", AddressV1("123 Main St", "Springfield", "12345"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", AddressV2("123 Main St", "Springfield"))))
      },
      test("build succeeds when nested types are identical (auto-mapped)") {
        case class Address(street: String, city: String)
        case class PersonV1(name: String, address: Address)
        case class PersonV2(name: String, address: Address, age: Int)

        given Schema[Address]  = Schema.derived[Address]
        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(0))
          .build

        val input  = PersonV1("Alice", Address("123 Main St", "Springfield"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", Address("123 Main St", "Springfield"), 0)))
      },
      test("build succeeds with deep nested addField") {
        case class Address1(street: String)
        case class Person1(name: String, address: Address1)

        case class Address2(street: String, number: Int)
        case class Person2(name: String, address: Address2)

        given Schema[Address1] = Schema.derived
        given Schema[Person1]  = Schema.derived
        given Schema[Address2] = Schema.derived
        given Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .addField(_.address.number, literal(0))
          .build

        val input  = Person1("Alice", Address1("123 Main"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main", 0))))
      },
      test("build succeeds with deep nested renameField") {
        case class Address1(street: String, city: String)
        case class Person1(name: String, address: Address1)

        case class Address2(streetName: String, city: String)
        case class Person2(name: String, address: Address2)

        given Schema[Address1] = Schema.derived
        given Schema[Person1]  = Schema.derived
        given Schema[Address2] = Schema.derived
        given Schema[Person2]  = Schema.derived

        val migration = Migration
          .newBuilder[Person1, Person2]
          .renameField(_.address.street, _.address.streetName)
          .build

        val input  = Person1("Alice", Address1("123 Main", "NYC"))
        val result = migration(input)

        assertTrue(result == Right(Person2("Alice", Address2("123 Main", "NYC"))))
      }
    )
  )
}
