package zio.blocks.schema

trait MigrationCompanionVersionSpecific {
  /**
   * Automatically derive a migration between A and B by comparing their schemas.
   */
  inline def derived[A, B](using schemaA: Schema[A], schemaB: Schema[B]): Migration[A, B] =
    ${ MigrationMacros.derivedImpl('schemaA, 'schemaB) }
}
