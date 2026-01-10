package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 2).
 * 
 * Provides string-based field selection for Scala 2 compatibility.
 * Redundant methods (already in shared MigrationBuilder) have been removed.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>
  /**
   * Build the final migration with runtime validation.
   */
  def build: Either[String, Migration[A, B]] =
    self.validate.map(_ => Migration[A, B](DynamicMigration(self.actions))(self.fromSchema, self.toSchema))

  def renameField(at: DynamicOptic, newName: String): MigrationBuilder[A, B] =
  // No platform-specific methods needed for Scala 2 at the moment as core methods are shared.
  // In the future, specialized Scala 2 macros could be added here.
}
