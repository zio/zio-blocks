package zio.blocks.schema.migration.macros

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.{DynamicMigration, Migration}

object MacroMigration {
  // format: off
  def derive[A, B](dynamic: DynamicMigration)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): Migration[A, B] =
    Migration.manual[A, B](dynamic)
  // format: on
}
