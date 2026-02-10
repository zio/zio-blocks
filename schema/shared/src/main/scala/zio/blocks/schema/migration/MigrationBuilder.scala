package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Immutable builder for constructing typed migrations.
 *
 * Type parameters:
 *   - A: Source schema type
 *   - B: Target schema type
 *   - Handled: Type-level list of field names from A that have been handled
 *     (dropped, renamed from, etc.)
 *   - Provided: Type-level list of field names for B that have been provided
 *     (added, renamed to, etc.)
 */
final case class MigrationBuilder[A, B, Handled, Provided](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  private[migration] actions: Vector[MigrationAction]
) {

  /**
   * Appends a single migration action to this builder.
   *
   * This is the sole entry point used by macro-generated code and tests to add
   * actions. Named methods like addField/dropField live only on the
   * selector-syntax extensions (MigrationBuilderSyntax) to avoid shadowing
   * issues in Scala 2 implicit conversion resolution.
   */
  private[migration] def withAction(action: MigrationAction): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ action)

  /**
   * Builds the migration without compile-time validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(
      dynamicMigration = DynamicMigration(actions),
      sourceSchema = sourceSchema,
      targetSchema = targetSchema
    )
}

object MigrationBuilder extends MigrationBuilderCompanionVersionSpecific
