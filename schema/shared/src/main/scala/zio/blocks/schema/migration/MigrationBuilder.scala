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
 *
 * Both Scala 2 and Scala 3 track field names at compile time, enabling
 * compile-time validation of migration completeness. The mechanisms differ:
 *   - Scala 3: Uses Tuple types with match types
 *   - Scala 2: Uses TList (custom HList) with implicit resolution
 */
final case class MigrationBuilder[A, B, Handled, Provided](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  /**
   * Adds a field to a record with a default value.
   */
  private[migration] def addField(
    target: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.AddField(target, default))

  /**
   * Removes a field from a record.
   */
  private[migration] def dropField(
    source: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.DropField(source, defaultForReverse))

  /**
   * Renames a field in a record.
   */
  private[migration] def renameField(
    from: DynamicOptic,
    to: String
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.Rename(from, to))

  /**
   * Applies a transformation expression to a field value.
   */
  private[migration] def transformField(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.TransformValue(at, transform))

  /**
   * Unwraps an Option field, using default for None values.
   */
  private[migration] def mandateField(
    at: DynamicOptic,
    default: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.Mandate(at, default))

  /**
   * Wraps a field value in Option (as Some).
   */
  private[migration] def optionalizeField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.Optionalize(at, defaultForReverse))

  /**
   * Converts a field from one primitive type to another.
   */
  private[migration] def changeFieldType(
    at: DynamicOptic,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.ChangeType(at, converter))

  /**
   * Joins multiple source fields into a single target field using a combiner
   * expression.
   */
  private[migration] def joinFields(
    target: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.Join(target, sourcePaths, combiner))

  /**
   * Splits a single source field into multiple target fields using a splitter
   * expression.
   */
  private[migration] def splitField(
    source: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.Split(source, targetPaths, splitter))

  /**
   * Renames a variant case.
   */
  private[migration] def renameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.RenameCase(at, from, to))

  /**
   * Applies nested migration actions to a specific variant case.
   */
  private[migration] def transformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = this.actions :+ MigrationAction.TransformCase(at, caseName, actions))

  /**
   * Applies a transformation to all elements in a sequence.
   */
  private[migration] def transformElements(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.TransformElements(at, transform))

  /**
   * Applies a transformation to all keys in a map.
   */
  private[migration] def transformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.TransformKeys(at, transform))

  /**
   * Applies a transformation to all values in a map.
   */
  private[migration] def transformValues(
    at: DynamicOptic,
    transform: SchemaExpr[DynamicValue, ?]
  ): MigrationBuilder[A, B, Handled, Provided] =
    copy(actions = actions :+ MigrationAction.TransformValues(at, transform))

  /**
   * Builds the migration without compile-time validation.
   *
   * Note: For compile-time validated migrations, use the `build` method from
   * MigrationBuilderSyntax which provides compile-time guarantees that the
   * migration is complete.
   */
  def buildPartial: Migration[A, B] =
    Migration(
      dynamicMigration = DynamicMigration(actions),
      sourceSchema = sourceSchema,
      targetSchema = targetSchema
    )
}

object MigrationBuilder extends MigrationBuilderCompanionVersionSpecific
