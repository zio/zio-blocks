package zio.blocks.schema.migration.macros

import zio.blocks.schema.migration.{DynamicMigration, Migration}

object MacroMigration {
  def derive[A, B](dynamic: DynamicMigration): Migration[A, B] =
    Migration.manual(dynamic)
}
