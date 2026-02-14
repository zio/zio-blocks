package zio.blocks.schema.migration.registry

import zio.blocks.schema.migration.{DynamicMigration, MigrationError}
import zio.blocks.schema.DynamicOptic

/**
 * MigrationRegistry (Versioning System) -------------------------------------
 * Satisfies Requirement: "Data versioning" & "Backward/Forward compatibility".
 * * It manages an ordered map of migrations (e.g., v1, v2, v3). It calculates
 * the "Plan" to upgrade or downgrade the database.
 */
final case class MigrationRegistry(
  versions: Map[Int, DynamicMigration]
) {

  /**
   * Register a new migration version. Example: register(1, migrationV1)
   */
  def register(version: Int, migration: DynamicMigration): MigrationRegistry =
    MigrationRegistry(versions + (version -> migration))

  /**
   * Calculates the migration path.
   *   - Upgrade (1 -> 3): Combines m2 ++ m3
   *   - Rollback (3 -> 1): Combines m3.reverse ++ m2.reverse
   */
  def plan(fromVersion: Int, toVersion: Int): Either[MigrationError, DynamicMigration] =
    if (fromVersion == toVersion) {
      Right(DynamicMigration.empty)
    } else if (fromVersion < toVersion) {
      // UPGRADE PATH: Forward Compatibility
      val range = (fromVersion + 1) to toVersion
      val steps = range.map(v => versions.get(v))

      if (steps.exists(_.isEmpty))
        Left(MigrationError.DecodingError(DynamicOptic.root, s"Missing migration version in range $range"))
      else
        Right(steps.flatten.foldLeft(DynamicMigration.empty)(_ ++ _))
    } else {
      // ROLLBACK PATH: Backward Compatibility
      // We take versions from Current down to Target+1, and REVERSE them structurally.
      val range = ((toVersion + 1) to fromVersion).reverse
      val steps = range.map(v => versions.get(v).map(_.reverse))

      if (steps.exists(_.isEmpty))
        Left(MigrationError.DecodingError(DynamicOptic.root, s"Missing migration version for rollback in range $range"))
      else
        Right(steps.flatten.foldLeft(DynamicMigration.empty)(_ ++ _))
    }
}

object MigrationRegistry {
  val empty: MigrationRegistry = MigrationRegistry(Map.empty)
}
