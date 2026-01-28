package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for typed Migration[A, B] with schemas.
 *
 * Covers:
 *   - Migration construction with schemas
 *   - Typed apply/applyUnsafe
 *   - Schema validation
 *   - Typed composition
 *   - Error handling with types
 */
object TypedMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int)
  case class PersonV3(fullName: String, age: Int, email: String)

  case class PointV1(x: Int, y: Int)
  case class PointV2(x: Int, y: Int, z: Int)

  case class WrapperV1(value: Int)
  case class WrapperV2(value: Long)

  case class RecordWithOptional(name: String, nickname: Option[String])
  case class RecordWithRequired(name: String, nickname: String)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("TypedMigrationSpec")(
    suite("Basic typed migration")(
      test("migrate PersonV1 to PersonV2 (rename)") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val migration = Migration[PersonV1, PersonV2](dynMigration, schemaV1, schemaV2)

        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("migrate PersonV2 to PersonV3 (add field)") {
        implicit val schemaV2: Schema[PersonV2] = Schema.derived
        implicit val schemaV3: Schema[PersonV3] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("unknown@example.com"))
        )
        val migration = Migration[PersonV2, PersonV3](dynMigration, schemaV2, schemaV3)

        val result = migration.apply(PersonV2("Alice", 30))
        assertTrue(result == Right(PersonV3("Alice", 30, "unknown@example.com")))
      },
      test("migrate PointV1 to PointV2 (add coordinate)") {
        implicit val schemaV1: Schema[PointV1] = Schema.derived
        implicit val schemaV2: Schema[PointV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "z", Resolved.Literal.int(0))
        )
        val migration = Migration[PointV1, PointV2](dynMigration, schemaV1, schemaV2)

        val result = migration.apply(PointV1(1, 2))
        assertTrue(result == Right(PointV2(1, 2, 0)))
      }
    ),
    suite("Type conversion migration")(
      test("migrate WrapperV1 to WrapperV2 (Int to Long)") {
        implicit val schemaV1: Schema[WrapperV1] = Schema.derived
        implicit val schemaV2: Schema[WrapperV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            "value",
            Resolved.Convert("Int", "Long", Resolved.Identity),
            Resolved.Convert("Long", "Int", Resolved.Identity)
          )
        )
        val migration = Migration[WrapperV1, WrapperV2](dynMigration, schemaV1, schemaV2)

        val result = migration.apply(WrapperV1(42))
        assertTrue(result == Right(WrapperV2(42L)))
      }
    ),
    suite("Optionality migration")(
      test("mandate optional field") {
        implicit val schemaOpt: Schema[RecordWithOptional] = Schema.derived
        implicit val schemaReq: Schema[RecordWithRequired] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "nickname", Resolved.Literal.string("N/A"))
        )
        val migration = Migration[RecordWithOptional, RecordWithRequired](dynMigration, schemaOpt, schemaReq)

        // With Some value
        val result1 = migration.apply(RecordWithOptional("Alice", Some("Ali")))
        assertTrue(result1 == Right(RecordWithRequired("Alice", "Ali")))

        // With None value - uses default
        val result2 = migration.apply(RecordWithOptional("Bob", None))
        assertTrue(result2 == Right(RecordWithRequired("Bob", "N/A")))
      },
      test("optionalize required field") {
        implicit val schemaReq: Schema[RecordWithRequired] = Schema.derived
        implicit val schemaOpt: Schema[RecordWithOptional] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root, "nickname")
        )
        val migration = Migration[RecordWithRequired, RecordWithOptional](dynMigration, schemaReq, schemaOpt)

        val result = migration.apply(RecordWithRequired("Alice", "Ali"))
        assertTrue(result == Right(RecordWithOptional("Alice", Some("Ali"))))
      }
    ),
    suite("applyUnsafe")(
      test("successful applyUnsafe returns value") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val migration = Migration[PersonV1, PersonV2](dynMigration, schemaV1, schemaV2)

        val result = migration.unsafeApply(PersonV1("Alice", 30))
        assertTrue(result == PersonV2("Alice", 30))
      },
      test("failed applyUnsafe throws exception") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Fail("forced failure"))
        )
        val migration = Migration[PersonV1, PersonV2](dynMigration, schemaV1, schemaV2)

        val threw = try {
          migration.unsafeApply(PersonV1("Alice", 30))
          false
        } catch {
          case _: Throwable => true
        }
        assertTrue(threw)
      }
    ),
    suite("Migration composition")(
      test("compose typed migrations") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV3: Schema[PersonV3] = Schema.derived

        val dyn1to2 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val dyn2to3 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("default@example.com"))
        )

        // Compose the dynamic migrations
        val composedDyn       = dyn1to2 ++ dyn2to3
        val composedMigration = Migration[PersonV1, PersonV3](composedDyn, schemaV1, schemaV3)

        val result = composedMigration.apply(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV3("Alice", 30, "default@example.com")))
      }
    ),
    suite("Multi-field migrations")(
      test("multiple operations in one migration") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV3: Schema[PersonV3] = Schema.derived

        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("none@none.com"))
          )
        )
        val migration = Migration[PersonV1, PersonV3](dynMigration, schemaV1, schemaV3)

        val result = migration.apply(PersonV1("Bob", 25))
        assertTrue(result == Right(PersonV3("Bob", 25, "none@none.com")))
      }
    ),
    suite("Error handling")(
      test("migration returns error on failed action") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Fail("intentional error"))
        )
        val migration = Migration[PersonV1, PersonV1](dynMigration, schemaV1, schemaV1)

        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result.isLeft)
      },
      test("migration returns error on conversion failure") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            "name",
            Resolved.Convert("String", "Int", Resolved.Identity), // Will fail - can't convert "Alice" to Int
            Resolved.Convert("Int", "String", Resolved.Identity)
          )
        )
        val migration = Migration[PersonV1, PersonV1](dynMigration, schemaV1, schemaV1)

        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result.isLeft)
      }
    ),
    suite("Identity migration")(
      test("identity migration returns same value") {
        implicit val schema: Schema[PersonV1] = Schema.derived

        val migration = Migration[PersonV1, PersonV1](DynamicMigration.identity, schema, schema)

        val input  = PersonV1("Alice", 30)
        val result = migration.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Schema access")(
      test("migration exposes source schema") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val migration = Migration[PersonV1, PersonV2](
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "name", "fullName")),
          schemaV1,
          schemaV2
        )

        assertTrue(migration.sourceSchema == schemaV1)
      },
      test("migration exposes target schema") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val migration = Migration[PersonV1, PersonV2](
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "name", "fullName")),
          schemaV1,
          schemaV2
        )

        assertTrue(migration.targetSchema == schemaV2)
      },
      test("migration exposes dynamic migration") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val migration = Migration[PersonV1, PersonV2](dynMigration, schemaV1, schemaV2)

        assertTrue(migration.dynamicMigration == dynMigration)
      }
    ),
    suite("Reverse typed migration")(
      test("reverse migration works correctly") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val forwardMigration = Migration[PersonV1, PersonV2](dynMigration, schemaV1, schemaV2)
        val reverseMigration = Migration[PersonV2, PersonV1](dynMigration.reverse, schemaV2, schemaV1)

        val original  = PersonV1("Alice", 30)
        val migrated  = forwardMigration.apply(original)
        val roundTrip = migrated.flatMap(reverseMigration.apply)

        assertTrue(roundTrip == Right(original))
      }
    )
  )
}
