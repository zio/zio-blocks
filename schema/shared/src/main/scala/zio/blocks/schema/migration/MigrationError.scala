package zio.blocks.schema.migration

import zio.blocks.schema.migration.optic.DynamicOptic

/**
 * Represents any error that occurs during the migration execution.
 * Updated to capture the Path (at) where the error occurred, as per requirements.
 */
final case class MigrationError(at: DynamicOptic, message: String)

object MigrationError {
  // Helper for backward compatibility or general errors (where path is empty)
  def apply(message: String): MigrationError = 
    MigrationError(DynamicOptic.empty, message)
}