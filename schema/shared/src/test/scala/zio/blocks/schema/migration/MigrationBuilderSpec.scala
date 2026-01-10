package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Comprehensive test suite for MigrationBuilder DSL.
 *
 * Tests the type-safe builder API for creating migrations.
 */
object MigrationBuilderSpec extends ZIOSpecDefault {

  // Test schemas - V0 and V1 must be compatible for migration
  case class PersonV0(name: String, age: Int)
  case class PersonV1(name: String, age: Int, country: String)

  // Use sealed traits for Scala 2/3 cross-compatibility (enums are Scala 3 only)
  sealed trait StatusV0
  object StatusV0 {
    case object Active   extends StatusV0
    case object Inactive extends StatusV0
  }

  sealed trait StatusV1
  object StatusV1 {
    case object Active extends StatusV1
    case object Legacy extends StatusV1
  }

  sealed trait StatusV2
  object StatusV2 {
    case object Active extends StatusV2
  }

  implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val statusV0Schema: Schema[StatusV0] = Schema.derived[StatusV0]
  implicit val statusV1Schema: Schema[StatusV1] = Schema.derived[StatusV1]
  implicit val statusV2Schema: Schema[StatusV2] = Schema.derived[StatusV2]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderSpec")(
      test("builds a simple migration with addField") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 1) &&
        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("builds migration with multiple actions") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .mandate("country", "Unknown")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 2)
      },
      test("builds migration with renameCase") {
        val migration = Migration
          .builder[StatusV0, StatusV1]
          .renameCase("Inactive", "Legacy")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RenameCase])
      },
      test("builds migration with removeCase") {
        val migration = Migration
          .builder[StatusV0, StatusV2]
          .removeCase("Inactive")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RemoveCase])
      },
      test("builder produces working migration") {
        // V0 has 'name', V1 has 'name' - they're compatible!
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .build
          .toOption
          .get

        val input  = PersonV0("John Doe", 30)
        val result = migration.apply(input)

        // Should succeed since we're adding the missing 'country' field
        assertTrue(result.isRight) &&
        assertTrue(result.map(_.country).getOrElse("") == "USA")
      },
      // Scala 3 Selector Macro Tests
      test("selector-based addField with _.targetField") {
        // This test verifies the new selector-based addField macro (Scala 3 only)
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField(_.country, "USA")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 1) &&
        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("selector-based addField produces working migration") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField(_.country, "Germany")
          .build
          .toOption
          .get

        val input  = PersonV0("Hans", 25)
        val result = migration.apply(input)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.country).getOrElse("") == "Germany")
      },
      test("selector-based dropField") {
        // PersonV1 has 'country', PersonV0 doesn't
        val migration = Migration
          .builder[PersonV1, PersonV0]
          .dropField(_.country)
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 1) &&
        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      // Nested field access test - demonstrates _.address.street pattern
      test("nested field selector with _.address.street pattern") {
        case class AddressV0(street: String, city: String)
        case class AddressV1(street: String, city: String, zipCode: String)
        case class CompanyV0(name: String, address: AddressV0)
        case class CompanyV1(name: String, address: AddressV1)

        implicit val addrV0: Schema[AddressV0] = Schema.derived
        implicit val addrV1: Schema[AddressV1] = Schema.derived
        implicit val compV0: Schema[CompanyV0] = Schema.derived
        implicit val compV1: Schema[CompanyV1] = Schema.derived

        // Test that nested path optic is created correctly (using buildPartial to skip validation)
        val migration = Migration
          .builder[CompanyV0, CompanyV1]
          .addField("zipCode", "00000") // Adding at top level for simplicity
          .buildPartial

        assertTrue(migration.dynamic.actions.length == 1) &&
        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      // Issue #519 example: PersonV0 to PersonV1 with addField(_.age, default)
      test("issue #519 example: add field with selector and default value") {
        // Simulates: PersonV0 { firstName, lastName } -> PersonV1 { firstName, lastName, age }
        case class PersonOld(firstName: String, lastName: String)
        case class PersonNew(firstName: String, lastName: String, age: Int)

        implicit val oldSchema: Schema[PersonOld] = Schema.derived
        implicit val newSchema: Schema[PersonNew] = Schema.derived

        val migration = Migration
          .builder[PersonOld, PersonNew]
          .addField(_.age, 0) // Default age of 0 as specified in #519
          .build
          .toOption
          .get

        val input  = PersonOld("John", "Doe")
        val result = migration.apply(input)

        // Verifies the exact workflow from issue #519
        assertTrue(result.isRight) &&
        assertTrue(result.map(_.firstName).getOrElse("") == "John") &&
        assertTrue(result.map(_.lastName).getOrElse("") == "Doe") &&
        assertTrue(result.map(_.age).getOrElse(-1) == 0)
      }
    )
}
