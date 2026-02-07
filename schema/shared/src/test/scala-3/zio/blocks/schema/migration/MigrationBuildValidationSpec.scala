package zio.blocks.schema.migration

import zio.test.*
import zio.blocks.schema.*

object MigrationBuildValidationSpec extends ZIOSpecDefault {

  private def literal(dv: DynamicValue): SchemaExpr[Any, DynamicValue] =
    SchemaExpr.literal(dv)

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
          .addField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
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
          .dropField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
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
          .transformField(_.age, _.age, literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))))
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
      test("build succeeds with transformNested for nested type migration") {
        case class AddressV1(street: String, city: String)
        case class AddressV2(street: String, city: String, zip: String)
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        given Schema[AddressV1] = Schema.derived[AddressV1]
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV1]  = Schema.derived[PersonV1]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformNested(_.address, _.address) { builder =>
            builder.addField(_.zip, literal(DynamicValue.Primitive(PrimitiveValue.String("00000"))))
          }
          .build

        val input  = PersonV1("Alice", AddressV1("123 Main St", "Springfield"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", AddressV2("123 Main St", "Springfield", "00000"))))
      },
      test("build fails to compile when nested field is missing without transformNested") {
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
      test("build succeeds with transformNested when dropping nested field") {
        case class AddressV1(street: String, city: String, zip: String)
        case class AddressV2(street: String, city: String)
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        given Schema[AddressV1] = Schema.derived[AddressV1]
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV1]  = Schema.derived[PersonV1]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformNested(_.address, _.address) { builder =>
            builder.dropField(_.zip, literal(DynamicValue.Primitive(PrimitiveValue.String("00000"))))
          }
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
          .addField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build

        val input  = PersonV1("Alice", Address("123 Main St", "Springfield"))
        val result = migration(input)

        assertTrue(result == Right(PersonV2("Alice", Address("123 Main St", "Springfield"), 0)))
      }
    )
  )
}
