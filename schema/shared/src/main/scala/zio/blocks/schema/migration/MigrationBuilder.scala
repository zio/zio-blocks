package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * A fluent builder for constructing [[Migration]] instances.
 *
 * The builder accumulates [[MigrationAction]]s and provides two build methods:
 *   - `.build` — full macro validation (checks that all source fields are
 *     accounted for)
 *   - `.buildPartial` — creates the migration without full validation
 *
 * Selector functions (e.g., `_.name`, `_.address.street`) are converted to
 * [[DynamicOptic]] paths via macros in the platform-specific code.
 */
final class MigrationBuilder[A, B](
  val actions: Vector[MigrationAction],
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B]
) extends MigrationBuilderSyntax[A, B] {

  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(actions, sourceSchema, targetSchema)

  // ─── Low-level API (optic-based) ────────────────────────────────────

  /** Adds a field at the target path with a default value. */
  def addFieldAt(target: DynamicOptic, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.AddField(target, defaultValue))

  /** Drops a field at the source path. */
  def dropFieldAt(source: DynamicOptic, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.DropField(source, defaultForReverse))

  /** Renames a field within a record at the given parent path. */
  def renameFieldAt(parentPath: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Rename(parentPath, fromName, toName))

  /** Transforms a value at a path using a serializable transform. */
  def transformFieldAt(
    source: DynamicOptic,
    target: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformValue(source, target, transform, reverseTransform))

  /** Makes an optional field mandatory. */
  def mandateFieldAt(
    source: DynamicOptic,
    target: DynamicOptic,
    defaultValue: DynamicValue
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Mandate(source, target, defaultValue))

  /** Makes a mandatory field optional. */
  def optionalizeFieldAt(source: DynamicOptic, target: DynamicOptic): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Optionalize(source, target))

  /** Changes the type of a field. */
  def changeFieldTypeAt(
    source: DynamicOptic,
    target: DynamicOptic,
    converter: DynamicValueTransform,
    reverseConverter: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.ChangeType(source, target, converter, reverseConverter))

  /** Renames a variant case at the given path. */
  def renameCaseAt(path: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.RenameCase(path, fromName, toName))

  /** Transforms a variant case with sub-actions. */
  def transformCaseAt(
    path: DynamicOptic,
    caseName: String,
    targetCaseName: String,
    subActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformCase(path, caseName, targetCaseName, subActions))

  /** Transforms elements of a sequence. */
  def transformElementsAt(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformElements(path, transform, reverseTransform))

  /** Transforms keys of a map. */
  def transformKeysAt(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformKeys(path, transform, reverseTransform))

  /** Transforms values of a map. */
  def transformValuesAt(
    path: DynamicOptic,
    transform: DynamicValueTransform,
    reverseTransform: Option[DynamicValueTransform] = None
  ): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.TransformValues(path, transform, reverseTransform))

  // ─── Build ──────────────────────────────────────────────────────────

  /**
   * Builds the migration without full validation. Creates a [[Migration]] from
   * the accumulated actions.
   */
  def buildPartial: Migration[A, B] =
    new Migration(new DynamicMigration(actions), sourceSchema, targetSchema)
}

object MigrationBuilder {

  /**
   * Creates a typed `DynamicValue` literal. Eagerly converts the value to
   * `DynamicValue` using the provided schema.
   *
   * Example: `literal[Int](0)` or `literal("hello")`
   */
  def literal[T](value: T)(implicit schema: Schema[T]): DynamicValue =
    schema.toDynamicValue(value)
}
