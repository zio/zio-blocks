package zio.schema.migration.examples

import zio._
import zio.schema._
import zio.schema.migration._

/**
 * Complete example showing how to migrate a Person schema through multiple versions.
 *
 * Evolution:
 * V1: firstName, lastName
 * V2: fullName, age (combine names, add age)
 * V3: fullName, birthYear (replace age with birthYear)
 */
object PersonMigrationExample extends ZIOAppDefault {

  // ===== VERSION 1 =====
  case class PersonV1(firstName: String, lastName: String)

  object PersonV1 {
    implicit val schema: Schema[PersonV1] = DeriveSchema.gen[PersonV1]
  }

  // ===== VERSION 2 =====
  case class PersonV2(fullName: String, age: Int)

  object PersonV2 {
    implicit val schema: Schema[PersonV2] = DeriveSchema.gen[PersonV2]
  }

  // ===== VERSION 3 =====
  case class PersonV3(fullName: String, birthYear: Int)

  object PersonV3 {
    implicit val schema: Schema[PersonV3] = DeriveSchema.gen[PersonV3]
  }

  // ===== MIGRATIONS =====

  /**
   * Migrate V1 -> V2:
   * - Rename firstName to fullName
   * - Drop lastName (in real system, would combine names)
   * - Add age with default 0
   */
  val v1ToV2: Migration[PersonV1, PersonV2] =
    MigrationBuilder[PersonV1, PersonV2]
      .renameField("firstName", "fullName")
      .dropField("lastName")
      .addField[Int]("age", 0)
      .build

  /**
   * Migrate V2 -> V3:
   * - Keep fullName as-is
   * - Rename age to birthYear
   *
   * Note: To transform age to birthYear (2024 - age), you would need to add
   * a SubtractFrom transformation to SerializableTransformation, or use
   * a custom expression-based transformation system.
   */
  val v2ToV3: Migration[PersonV2, PersonV3] =
    MigrationBuilder[PersonV2, PersonV3]
      .renameField("age", "birthYear")
      .build

  /**
   * Composed migration V1 -> V3
   */
  val v1ToV3: Migration[PersonV1, PersonV3] = v1ToV2 ++ v2ToV3

  // ===== EXAMPLE USAGE =====

  def run: ZIO[Any, Any, Unit] = {
    for {
      _ <- Console.printLine("=== ZIO Schema Migration Example ===\n")

      // Original V1 data
      v1Data = PersonV1("John", "Doe")
      _ <- Console.printLine(s"Original V1: $v1Data")

      // Migrate V1 -> V2
      v2Result = v1ToV2(v1Data)
      _ <- v2Result match {
        case Right(v2) =>
          Console.printLine(s"Migrated to V2: $v2")
        case Left(error) =>
          Console.printLine(s"Migration failed: ${error.message}")
      }

      // Migrate V2 -> V3 (if V2 migration succeeded)
      _ <- v2Result match {
        case Right(v2) =>
          v2ToV3(v2) match {
            case Right(v3) =>
              Console.printLine(s"Migrated to V3: $v3")
            case Left(error) =>
              Console.printLine(s"Migration failed: ${error.message}")
          }
        case Left(_) => ZIO.unit
      }

      // Direct migration V1 -> V3
      _ <- Console.printLine("\n--- Direct Migration V1 -> V3 ---")
      v3Direct = v1ToV3(v1Data)
      _ <- v3Direct match {
        case Right(v3) =>
          Console.printLine(s"Direct migration result: $v3")
        case Left(error) =>
          Console.printLine(s"Migration failed: ${error.message}")
      }

      // Show optimization
      _ <- Console.printLine("\n--- Migration Optimization ---")
      unoptimized = v1ToV3.dynamicMigration
      optimized = unoptimized.optimize
      _ <- Console.printLine(s"Unoptimized actions: ${unoptimized.actions.length}")
      _ <- Console.printLine(s"Optimized actions: ${optimized.actions.length}")

      // Show reversibility
      _ <- Console.printLine("\n--- Reversibility ---")
      _ <- v1ToV2.reverse match {
        case Right(reversed) =>
          Console.printLine("V1->V2 migration is reversible (partially)")
        case Left(error) =>
          Console.printLine(s"V1->V2 migration is NOT reversible: ${error.message}")
      }

      _ <- v2ToV3.reverse match {
        case Right(reversed) =>
          Console.printLine("V2->V3 migration is reversible")
        case Left(error) =>
          Console.printLine(s"V2->V3 migration is NOT reversible: ${error.message}")
      }

      // Show serialization capability
      _ <- Console.printLine("\n--- Serialization ---")
      dynamicMigration = v1ToV3.toDynamic
      _ <- Console.printLine(s"Extracted dynamic migration with ${dynamicMigration.actions.length} actions")
      _ <- Console.printLine("This can be serialized and stored in a registry")

    } yield ()
  }
}

/**
 * Example showing a migration registry for versioned schemas
 */
object RegistryExample {
  case class Version(major: Int, minor: Int)

  class MigrationRegistry {
    private val migrations =
      scala.collection.mutable.Map[(Version, Version), DynamicMigration]()

    def register(from: Version, to: Version, migration: DynamicMigration): Unit = {
      migrations((from, to)) = migration
    }

    def get(from: Version, to: Version): Option[DynamicMigration] = {
      migrations.get((from, to))
    }

    def findPath(from: Version, to: Version): Option[List[DynamicMigration]] = {
      // Simple BFS to find migration path
      // In production, would use graph algorithms
      ???
    }
  }

  def example: Unit = {
    val registry = new MigrationRegistry()

    // Register migrations
    registry.register(
      Version(1, 0),
      Version(2, 0),
      PersonMigrationExample.v1ToV2.toDynamic
    )

    registry.register(
      Version(2, 0),
      Version(3, 0),
      PersonMigrationExample.v2ToV3.toDynamic
    )

    // Retrieve migration
    val migration = registry.get(Version(1, 0), Version(2, 0))

    // In a real system, you could serialize the entire registry
    // and load it at runtime without any schema definitions
  }
}
