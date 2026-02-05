package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  case class PersonV1(firstName: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, nickname: Option[String])
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSpec")(
    buildPartialSuite,
    buildFullSuite,
    metadataSuite,
    dryRunSuite,
    typedMigrationSuite
  )

  private val buildPartialSuite = suite("buildPartial")(
    test("builds a migration with partial validation (skips shape comparison)") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .addFieldAt[Option[String]](DynamicOptic.root.field("nickname"), None)
      val migration = builder.buildPartial
      assertTrue(
        migration.dynamicMigration.actions.size == 2,
        !migration.isLossy
      )
    },
    test("buildPartial catches action-level errors") {
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .dropFieldAt(DynamicOptic.root.field("nonExistent"))
      val caught = try {
        builder.buildPartial
        false
      } catch {
        case _: MigrationValidationException => true
        case _: Throwable                    => false
      }
      assertTrue(caught)
    }
  )

  private val buildFullSuite = suite("build (full validation)")(
    test("build validates transformed shape matches target") {
      // This migration is incomplete — it doesn't add the nickname field
      val builder = MigrationBuilder[PersonV1, PersonV2]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
      val caught = try {
        builder.build()
        false
      } catch {
        case e: MigrationValidationException =>
          e.errors.exists(_.message.contains("missing target field"))
        case _: Throwable => false
      }
      assertTrue(caught)
    }
  )

  private val metadataSuite = suite("Metadata")(
    test("withId sets migration ID") {
      val builder = MigrationBuilder[PersonV1, PersonV1]
        .withId("v1-to-v1")
        .withDescription("identity migration")
        .withCreatedBy("test")
      val migration = builder.buildPartial
      assertTrue(
        migration.metadata.id == Some("v1-to-v1"),
        migration.metadata.description == Some("identity migration"),
        migration.metadata.createdBy == Some("test")
      )
    },
    test("build computes fingerprint") {
      val builder = MigrationBuilder[PersonV1, PersonV1]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .renameFieldAt(DynamicOptic.root.field("fullName"), "firstName")
      val migration = builder.buildPartial
      assertTrue(
        migration.metadata.fingerprint.isDefined,
        migration.metadata.fingerprint.get.nonEmpty
      )
    },
    test("different actions produce different fingerprints") {
      val m1 = MigrationBuilder[PersonV1, PersonV1]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .renameFieldAt(DynamicOptic.root.field("fullName"), "firstName")
        .buildPartial
      val m2 = MigrationBuilder[PersonV1, PersonV1]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "name")
        .renameFieldAt(DynamicOptic.root.field("name"), "firstName")
        .buildPartial
      assertTrue(m1.metadata.fingerprint != m2.metadata.fingerprint)
    }
  )

  private val dryRunSuite = suite("dryRun")(
    test("dryRun applies migration to sample value") {
      val builder = MigrationBuilder[PersonV1, PersonV1]
        .renameFieldAt(DynamicOptic.root.field("firstName"), "fullName")
        .renameFieldAt(DynamicOptic.root.field("fullName"), "firstName")
      val result = builder.dryRun(PersonV1("Alice", 30))
      assertTrue(
        result.isRight,
        result.toOption.get == PersonV1("Alice", 30)
      )
    }
  )

  private val typedMigrationSuite = suite("Typed Migration")(
    test("Migration.identity passes values through unchanged") {
      val migration = Migration.identity[PersonV1]
      val person    = PersonV1("Alice", 30)
      val result    = migration.apply(person)
      assertTrue(
        result.isRight,
        result.toOption.get == person
      )
    },
    test("typed migration composition works") {
      val m1       = Migration.identity[PersonV1]
      val m2       = Migration.identity[PersonV1]
      val composed = m1 ++ m2
      val result   = composed.apply(PersonV1("Alice", 30))
      assertTrue(result.isRight)
    },
    test("composeStrict rejects lossy right-hand migration") {
      val lossless = Migration[PersonV1, PersonV1](
        DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"))),
        PersonV1.schema,
        PersonV1.schema
      )
      val lossy = Migration[PersonV1, PersonV1](
        DynamicMigration(Vector(MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None))),
        PersonV1.schema,
        PersonV1.schema
      )
      val caught = try {
        lossless.composeStrict(lossy)
        false
      } catch {
        case _: IllegalArgumentException => true
        case _: Throwable                => false
      }
      assertTrue(caught)
    },
    test("explain produces readable output") {
      val migration = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")),
          MigrationMetadata(id = Some("test"))
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val explanation = migration.explain
      assertTrue(
        explanation.contains("Migration["),
        explanation.contains("Rename"),
        explanation.contains("test")
      )
    }
  )
}
