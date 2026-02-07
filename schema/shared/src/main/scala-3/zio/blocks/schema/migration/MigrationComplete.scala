package zio.blocks.schema.migration

/**
 * Type class witnessing that a migration is complete.
 *
 * A migration is complete when:
 *   - All source fields that don't exist in the target are explicitly handled
 *     (via dropField or renameField)
 *   - All target fields that don't exist in the source are explicitly provided
 *     (via addField or renameField)
 *
 * Fields with the same name in both source and target are auto-mapped and don't
 * need explicit handling.
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 * @tparam SourceHandled
 *   Tuple of source field name literal types that have been explicitly handled
 * @tparam TargetProvided
 *   Tuple of target field name literal types that have been explicitly provided
 */
sealed trait MigrationComplete[A, B, SourceHandled <: Tuple, TargetProvided <: Tuple]

object MigrationComplete {

  private val instance: MigrationComplete[Any, Any, EmptyTuple, EmptyTuple] =
    new MigrationComplete[Any, Any, EmptyTuple, EmptyTuple] {}

  /**
   * Derive a [[MigrationComplete]] instance if the migration is valid.
   *
   * This macro validates at compile time that all fields are accounted for,
   * producing a compile error with helpful hints if any fields are missing.
   */
  inline given derived[A, B, SH <: Tuple, TP <: Tuple]: MigrationComplete[A, B, SH, TP] =
    ${ MigrationValidationMacros.validateMigration[A, B, SH, TP] }

  /** Unsafe instance that skips validation. Used by `buildPartial`. */
  def unsafePartial[A, B, SH <: Tuple, TP <: Tuple]: MigrationComplete[A, B, SH, TP] =
    instance.asInstanceOf[MigrationComplete[A, B, SH, TP]]
}
