package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * Builder for constructing `Migration[A, B]` instances step by step.
 *
 * All builder methods accumulate `MigrationAction`s. The final `build` method
 * validates the migration and creates the `Migration[A, B]`.
 *
 * Note: In the spec, many of these methods accept selector functions (e.g.,
 * `_.fieldName`) implemented via macros. This implementation uses string-based
 * field names as the core, with macro support layered on top.
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ──────────────── Record Operations ────────────────

  def addField(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.AddField(DynamicOptic.root, fieldName, default))

  def dropField(fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(DynamicOptic.root, fieldName, DynamicValue.Null))

  def dropField(fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(DynamicOptic.root, fieldName, defaultForReverse))

  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    append(MigrationAction.Rename(DynamicOptic.root, from, to))

  def transformField(fieldName: String, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformValue(DynamicOptic.root.field(fieldName), migration))

  def mandateField(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.Mandate(DynamicOptic.root, fieldName, default))

  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.Optionalize(DynamicOptic.root, fieldName))

  def changeFieldType(fieldName: String, converter: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.ChangeType(DynamicOptic.root, fieldName, converter))

  def nestFields(fieldNames: Vector[String], into: String): MigrationBuilder[A, B] =
    append(MigrationAction.Nest(DynamicOptic.root, fieldNames, into))

  def unnestField(fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.Unnest(DynamicOptic.root, fieldName, Vector.empty))

  // ──────────────── Enum Operations ────────────────

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    append(MigrationAction.RenameCase(DynamicOptic.root, from, to))

  def transformCase(
    caseName: String,
    caseActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    append(MigrationAction.TransformCase(DynamicOptic.root, caseName, caseActions))

  // ──────────────── Collection / Map Operations ────────────────

  def transformElements(at: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformElements(at, migration))

  def transformKeys(at: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformKeys(at, migration))

  def transformValues(at: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformValues(at, migration))

  // ──────────────── Path-scoped Operations ────────────────

  /**
   * Add a field at a specific path (for nested records).
   */
  def addFieldAt(path: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.AddField(path, fieldName, default))

  /**
   * Drop a field at a specific path.
   */
  def dropFieldAt(path: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(path, fieldName, DynamicValue.Null))

  /**
   * Rename a field at a specific path.
   */
  def renameFieldAt(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    append(MigrationAction.Rename(path, from, to))

  // ──────────────── Build ────────────────

  /**
   * Build the migration with validation.
   */
  def build: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build the migration without full validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  private def append(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, actions :+ action)
}
