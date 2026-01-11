package zio.blocks.schema

import scala.language.experimental.macros

trait MigrationCompanionVersionSpecific {

  /**
   * Automatically derive a migration between A and B by comparing their
   * schemas.
   */
  def derived[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): Migration[A, B] =
    macro MigrationMacros.derivedImpl[A, B]
}
