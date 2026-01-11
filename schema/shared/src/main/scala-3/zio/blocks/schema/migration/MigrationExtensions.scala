package zio.blocks.schema.migration

/**
 * Extension methods for MigrationBuilder (Scala 3).
 */

// format: off
extension [A, B](inline builder: MigrationBuilder[A, B]) {

  /**
   * Build the final migration with strict compile-time validation.
   * This macro validates that all source fields are handled and all target fields are produced.
   */
  inline def build: Migration[A, B] = ${
    MigrationBuilderValidation.validateBuilderImpl[A, B]('builder)
  }
}
