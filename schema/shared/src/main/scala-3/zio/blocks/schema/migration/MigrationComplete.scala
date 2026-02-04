package zio.blocks.schema.migration

/**
 * Type class that witnesses a migration is complete.
 *
 * A migration is complete when:
 *   - All source fields are either handled (renamed, dropped, transformed) or
 *     auto-mapped
 *   - All target fields are either provided (added, renamed to) or auto-mapped
 *
 * Auto-mapping occurs when source and target have fields with the same name.
 *
 * @tparam A
 *   The source type
 * @tparam B
 *   The target type
 * @tparam SourceHandled
 *   Tuple of source field names that have been explicitly handled
 * @tparam TargetProvided
 *   Tuple of target field names that have been explicitly provided
 */
sealed trait MigrationComplete[A, B, SourceHandled <: Tuple, TargetProvided <: Tuple]

object MigrationComplete {

  /** The singleton instance - validation is done at macro expansion time. */
  private val instance: MigrationComplete[Any, Any, EmptyTuple, EmptyTuple] =
    new MigrationComplete[Any, Any, EmptyTuple, EmptyTuple] {}

  /**
   * Derive a MigrationComplete instance if the migration is valid.
   *
   * This macro validates at compile time that all fields are accounted for.
   */
  inline given derived[A, B, SH <: Tuple, TP <: Tuple]: MigrationComplete[A, B, SH, TP] =
    ${ MigrationValidationMacros.validateMigration[A, B, SH, TP] }

  /** Unsafe instance for partial migrations (skips validation). */
  def unsafePartial[A, B, SH <: Tuple, TP <: Tuple]: MigrationComplete[A, B, SH, TP] =
    instance.asInstanceOf[MigrationComplete[A, B, SH, TP]]
}
