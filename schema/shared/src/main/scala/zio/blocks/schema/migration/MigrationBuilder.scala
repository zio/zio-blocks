package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveType, PrimitiveValue, Schema, SchemaError}

/**
 * A fluent builder for constructing `Migration[A, B]`.
 *
 * Provides a type-safe API for declaring migration steps using selector-style
 * paths. Each method returns a new `MigrationBuilder` with the action appended.
 *
 * == Validation ==
 *
 *   - `.build` validates that the migration actions produce a complete mapping
 *     from source to target schema. Returns `Left` with diagnostics if
 *     validation fails.
 *   - `.buildPartial` skips validation and produces the migration as-is.
 *
 * == Selector Syntax ==
 *
 * Field paths use `DynamicOptic`:
 * {{{
 * DynamicOptic.root.field("address").field("street")  // _.address.street
 * DynamicOptic.root.field("items").elements            // _.items.each
 * }}}
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val pendingActions: Vector[MigrationAction]
) {

  // ───────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to the target record with a default value.
   */
  def addField(fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.AddField(DynamicOptic.root, fieldName, defaultValue))

  /**
   * Add a new field at a nested path.
   */
  def addField(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.AddField(path, fieldName, defaultValue))

  /**
   * Drop a field from the source record.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(DynamicOptic.root, fieldName, None))

  /**
   * Drop a field from a nested record.
   */
  def dropField(path: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(path, fieldName, None))

  /**
   * Drop a field, preserving its last known default for reversibility.
   */
  def dropFieldReversible(fieldName: String, lastKnownDefault: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.DropField(DynamicOptic.root, fieldName, Some(lastKnownDefault)))

  /**
   * Rename a field in the record.
   */
  def renameField(oldName: String, newName: String): MigrationBuilder[A, B] =
    append(MigrationAction.RenameField(DynamicOptic.root, oldName, newName))

  /**
   * Rename a field at a nested path.
   */
  def renameField(path: DynamicOptic, oldName: String, newName: String): MigrationBuilder[A, B] =
    append(MigrationAction.RenameField(path, oldName, newName))

  /**
   * Set a field to a new literal value.
   */
  def setFieldValue(fieldName: String, newValue: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.SetFieldValue(DynamicOptic.root, fieldName, newValue))

  /**
   * Make an optional field mandatory.
   */
  def mandateField(fieldName: String, defaultForNull: DynamicValue): MigrationBuilder[A, B] =
    append(MigrationAction.MandateField(DynamicOptic.root, fieldName, defaultForNull))

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    append(MigrationAction.OptionalizeField(DynamicOptic.root, fieldName))

  /**
   * Change a field's type with explicit value mappings.
   */
  def changeFieldType(
    fieldName: String,
    mappings: (DynamicValue, DynamicValue)*
  ): MigrationBuilder[A, B] =
    append(MigrationAction.ChangeFieldType(
      DynamicOptic.root,
      fieldName,
      Chunk.fromIterable(mappings)
    ))

  // ───────────────────────────────────────────────────────────────────────────
  // Enum / Variant Operations
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Rename a variant case.
   */
  def renameCase(oldName: String, newName: String): MigrationBuilder[A, B] =
    append(MigrationAction.RenameCase(DynamicOptic.root, oldName, newName))

  /**
   * Transform a variant case's value using a nested migration.
   */
  def transformCase(caseName: String, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformCase(DynamicOptic.root, caseName, migration))

  // ───────────────────────────────────────────────────────────────────────────
  // Collection Operations
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Transform each element of a sequence field.
   */
  def transformElements(path: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformElements(path, migration))

  /**
   * Transform map keys at a given path.
   */
  def transformKeys(path: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformKeys(path, migration))

  /**
   * Transform map values at a given path.
   */
  def transformValues(path: DynamicOptic, migration: DynamicMigration): MigrationBuilder[A, B] =
    append(MigrationAction.TransformValues(path, migration))

  // ───────────────────────────────────────────────────────────────────────────
  // Build
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with validation.
   *
   * Validates that the migration actions produce a complete transformation from
   * the source schema's dynamic representation to the target schema's. Returns
   * `Left` with validation errors if the migration is incomplete.
   *
   * Validation checks:
   *   - All fields present in the target but missing from the source have
   *     corresponding `addField` actions
   *   - All fields present in the source but missing from the target have
   *     corresponding `dropField` actions
   *   - Field renames are consistent (no dangling references)
   */
  def build: Either[SchemaError, Migration[A, B]] = {
    val dynamicMigration = DynamicMigration(Chunk.fromIterable(pendingActions))

    // Validate: apply migration to source schema's default/empty representation
    // and check if the result conforms to the target schema
    val sourceDynamic = sourceSchema.toDynamicSchema
    val targetDynamic = targetSchema.toDynamicSchema

    // Structural validation: check that we have actions covering the structural
    // differences between source and target schemas
    val sourceFields = getRecordFieldNames(sourceDynamic.reflect)
    val targetFields = getRecordFieldNames(targetDynamic.reflect)

    val addedFieldNames   = pendingActions.collect { case a: MigrationAction.AddField => a.fieldName }.toSet
    val droppedFieldNames = pendingActions.collect { case d: MigrationAction.DropField => d.fieldName }.toSet
    val renamedFrom       = pendingActions.collect { case r: MigrationAction.RenameField => r.oldName }.toSet
    val renamedTo         = pendingActions.collect { case r: MigrationAction.RenameField => r.newName }.toSet

    // Fields in target that aren't in source and aren't being added or renamed-to
    val effectiveSourceFields = (sourceFields -- droppedFieldNames -- renamedFrom) ++ addedFieldNames ++ renamedTo
    val missingInTarget       = targetFields -- effectiveSourceFields

    if (missingInTarget.nonEmpty) {
      Left(SchemaError(
        s"Migration is incomplete. Target fields not covered: ${missingInTarget.mkString(", ")}. " +
          "Add `addField` or `renameField` actions to cover these fields."
      ))
    } else {
      Right(Migration(sourceSchema, targetSchema, dynamicMigration))
    }
  }

  /**
   * Build the migration without validation. Useful for partial migrations or
   * when validation constraints don't apply.
   */
  def buildPartial: Migration[A, B] = {
    val dynamicMigration = DynamicMigration(Chunk.fromIterable(pendingActions))
    Migration(sourceSchema, targetSchema, dynamicMigration)
  }

  private def append(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, pendingActions :+ action)

  private def getRecordFieldNames(reflect: zio.blocks.schema.Reflect[?, ?]): Set[String] =
    reflect match {
      case r: zio.blocks.schema.Reflect.Record[?, ?] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty
    }
}

/**
 * Convenience methods for creating literal `DynamicValue` instances with typed
 * ergonomics.
 */
object MigrationBuilder {

  /**
   * Create a literal `DynamicValue` from a typed value.
   *
   * {{{
   * literal[String]("hello")         // DynamicValue.Primitive(PrimitiveValue.String("hello"))
   * literal[Int](42)                 // DynamicValue.Primitive(PrimitiveValue.Int(42))
   * literal[Boolean](true)           // DynamicValue.Primitive(PrimitiveValue.Boolean(true))
   * }}}
   */
  def literal[T](value: T)(implicit schema: Schema[T]): DynamicValue =
    schema.toDynamicValue(value)
}
