package zio.blocks.schema.migration

import zio._
import zio.test._
import zio.blocks.schema._

/**
 * Tests for the MigrationBuilder DSL, including both string-based and
 * macro-based selector methods.
 *
 * This demonstrates the fluent API for building migrations.
 */
object MigrationBuilderSpec extends SchemaBaseSpec {

  // Test schemas
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, country: String)
  case class PersonV3(fullName: String, country: String)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
  implicit val personV3Schema: Schema[PersonV3] = Schema.derived[PersonV3]

  def spec = suite("MigrationBuilderSpec")(
    suite("String-based Builder API")(
      test("build migration with addFieldWithDefault") {
        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .addFieldWithDefault("country", "USA")
          .renameField("name", "fullName")
          .build

        val person = PersonV1("Alice", 30)
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Alice") &&
        assertTrue(result.toOption.get.age == 30) &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("build migration with dropField") {
        val migration = MigrationBuilder(personV2Schema, personV3Schema)
          .dropField("age")
          .build

        val person = PersonV2("Bob", 25, "Canada")
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Bob") &&
        assertTrue(result.toOption.get.country == "Canada")
      },

      test("build migration with multiple operations") {
        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .renameField("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .build

        val person = PersonV1("Charlie", 35)
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Charlie") &&
        assertTrue(result.toOption.get.age == 35) &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("build migration with renameCase") {
        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .renameCase("OldCase", "NewCase")
          .build

        // Just verify it builds successfully
        assertTrue(migration.dynamicMigration.actions.length == 1)
      }
    ),

    suite("Migration Composition")(
      test("compose migrations with ++") {
        val migration1 = MigrationBuilder(personV1Schema, personV2Schema)
          .renameField("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .build

        val migration2 = MigrationBuilder(personV2Schema, personV3Schema)
          .dropField("age")
          .build

        val composed = migration1 ++ migration2

        val person = PersonV1("Frank", 40)
        val result = composed.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Frank") &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("compose migrations with andThen") {
        val migration1 = MigrationBuilder(personV1Schema, personV2Schema)
          .renameField("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .build

        val migration2 = MigrationBuilder(personV2Schema, personV3Schema)
          .dropField("age")
          .build

        val composed = migration1.andThen(migration2)

        val person = PersonV1("Grace", 45)
        val result = composed.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Grace") &&
        assertTrue(result.toOption.get.country == "USA")
      }
    )
  )
}
