package zio.blocks.schema.migration

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
    suite("String-based Builder API (ByName methods)")(
      test("build migration with addFieldWithDefault") {
        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .addFieldWithDefault("country", "USA")
          .renameFieldByName("name", "fullName")
          .buildUnchecked

        val person = PersonV1("Alice", 30)
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Alice") &&
        assertTrue(result.toOption.get.age == 30) &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("build migration with dropFieldByName") {
        val migration = MigrationBuilder(personV2Schema, personV3Schema)
          .dropFieldByName("age")
          .buildUnchecked

        val person = PersonV2("Bob", 25, "Canada")
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Bob") &&
        assertTrue(result.toOption.get.country == "Canada")
      },

      test("build migration with multiple operations") {
        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .renameFieldByName("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .buildUnchecked

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
          .buildUnchecked

        // Just verify it builds successfully
        assertTrue(migration.dynamicMigration.actions.length == 1)
      }
    ),

    suite("Selector-based Builder API")(
      test("selector API compiles and works with addField") {
        // Import needed for Scala 2 (brings implicit class into scope)
        // In Scala 3, extension methods are automatically available (import is unused but harmless)
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        val migration = MigrationBuilder(personV1Schema, personV2Schema)
          .addField((p: PersonV2) => p.country, "USA")
          .renameField((p: PersonV1) => p.name, (p: PersonV2) => p.fullName)
          .buildUnchecked

        val person = PersonV1("Alice", 30)
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Alice") &&
        assertTrue(result.toOption.get.age == 30) &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("selector API compiles and works with dropField") {
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        val migration = MigrationBuilder(personV2Schema, personV3Schema)
          .dropField((p: PersonV2) => p.age)
          .buildUnchecked

        val person = PersonV2("Bob", 25, "Canada")
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Bob") &&
        assertTrue(result.toOption.get.country == "Canada")
      },

      test("selector API compiles with int default") {
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class PersonV4(fullName: String, age: Int, country: String, score: Int)
        object PersonV4 { implicit val schema: Schema[PersonV4] = Schema.derived[PersonV4] }

        val migration = MigrationBuilder(personV2Schema, PersonV4.schema)
          .addField((p: PersonV4) => p.score, 100)
          .buildUnchecked

        val person = PersonV2("David", 40, "UK")
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.score == 100)
      },

      test("selector API compiles with boolean default") {
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class PersonV5(fullName: String, age: Int, country: String, active: Boolean)
        object PersonV5 { implicit val schema: Schema[PersonV5] = Schema.derived[PersonV5] }

        val migration = MigrationBuilder(personV2Schema, PersonV5.schema)
          .addField((p: PersonV5) => p.active, true)
          .buildUnchecked

        val person = PersonV2("Eve", 28, "France")
        val result = migration.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.active == true)
      }
    ),

    suite("Migration Composition")(
      test("compose migrations with ++") {
        val migration1 = MigrationBuilder(personV1Schema, personV2Schema)
          .renameFieldByName("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .buildUnchecked

        val migration2 = MigrationBuilder(personV2Schema, personV3Schema)
          .dropFieldByName("age")
          .buildUnchecked

        val composed = migration1 ++ migration2

        val person = PersonV1("Frank", 40)
        val result = composed.apply(person)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get.fullName == "Frank") &&
        assertTrue(result.toOption.get.country == "USA")
      },

      test("compose migrations with andThen") {
        val migration1 = MigrationBuilder(personV1Schema, personV2Schema)
          .renameFieldByName("name", "fullName")
          .addFieldWithDefault("country", "USA")
          .buildUnchecked

        val migration2 = MigrationBuilder(personV2Schema, personV3Schema)
          .dropFieldByName("age")
          .buildUnchecked

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
