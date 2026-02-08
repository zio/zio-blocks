package zio.blocks.schema.migration.macros

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.{DynamicMigration, Migration}

object MacroMigration {

  /**
   * Scala 2 Fallback: Always uses the interpreter. Zero-Overhead macros are
   * only available on Scala 3.
   */
  // format: off
  def derive[A, B](dynamic: DynamicMigration)(implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): Migration[A, B] =
    Migration.manual[A, B](dynamic)
  // format: on
}
