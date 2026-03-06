package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}

/**
 * Fluent builder for constructing [[Migration]]s step by step.
 *
 * Each method adds a [[MigrationAction]] to the action list and returns
 * a new builder. Call `.build` to produce the final [[Migration[A, B]]].
 *
 * The selector-accepting overloads (taking `A => Any` / `B => Any`) are
 * intended to be backed by macros that extract a [[DynamicOptic]] at
 * compile time. The `DynamicOptic`-based overloads are the underlying
 * implementation and can be used directly.
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  private def copy(newActions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, newActions)

  // ── Record operations ──────────────────────────────────────────────

  /**
   * Add a field to the target record with the given default value.
   *
   * @param at      path to the containing record (use `DynamicOptic.root` for top-level)
   * @param name    field name to add
   * @param default value to use for the new field
   */
  def addField(at: DynamicOptic, name: String, default: DynamicValue): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.AddField(at, name, default))

  /** Convenience: add a top-level field with a typed default. */
  def addField[V](name: String, default: V)(implicit vs: Schema[V]): MigrationBuilder[A, B] =
    addField(DynamicOptic.root, name, vs.toDynamicValue(default))

  /**
   * Drop a field from the source record.
   *
   * @param at               path to the containing record
   * @param name             field name to drop
   * @param defaultForReverse value to use when reversing (re-adding) the field
   */
  def dropField(at: DynamicOptic, name: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.DropField(at, name, defaultForReverse))

  /** Drop a top-level field. Uses `DynamicValue.Null` as the reverse default. */
  def dropField(name: String): MigrationBuilder[A, B] =
    dropField(DynamicOptic.root, name, DynamicValue.Null)

  /** Drop a top-level field with a typed reverse default. */
  def dropField[V](name: String, defaultForReverse: V)(implicit vs: Schema[V]): MigrationBuilder[A, B] =
    dropField(DynamicOptic.root, name, vs.toDynamicValue(defaultForReverse))

  /**
   * Rename a field.
   */
  def renameField(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.RenameField(at, from, to))

  /** Rename a top-level field. */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    renameField(DynamicOptic.root, from, to)

  /**
   * Make an optional field required, providing a default for `None`.
   */
  def mandateField(at: DynamicOptic, name: String, default: DynamicValue): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Mandate(at, name, default))

  /** Make a top-level optional field required with a typed default. */
  def mandateField[V](name: String, default: V)(implicit vs: Schema[V]): MigrationBuilder[A, B] =
    mandateField(DynamicOptic.root, name, vs.toDynamicValue(default))

  /**
   * Make a required field optional.
   */
  def optionalizeField(at: DynamicOptic, name: String): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.Optionalize(at, name))

  /** Make a top-level field optional. */
  def optionalizeField(name: String): MigrationBuilder[A, B] =
    optionalizeField(DynamicOptic.root, name)

  /**
   * Change a field's primitive type.
   *
   * @param name          field name
   * @param targetType    name of the target primitive type (e.g. "Long", "String")
   * @param sourceType    name of the source type (for reverse conversion)
   */
  def changeFieldType(
    at: DynamicOptic,
    name: String,
    targetType: String,
    sourceType: String
  ): MigrationBuilder[A, B] =
    copy(
      actions :+ MigrationAction.ChangeFieldType(
        at,
        name,
        DynamicValue.Primitive(new PrimitiveValue.String(targetType)),
        DynamicValue.Primitive(new PrimitiveValue.String(sourceType))
      )
    )

  /** Change a top-level field's primitive type. */
  def changeFieldType(name: String, targetType: String, sourceType: String): MigrationBuilder[A, B] =
    changeFieldType(DynamicOptic.root, name, targetType, sourceType)

  // ── Enum operations ────────────────────────────────────────────────

  /** Rename a variant case. */
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions :+ MigrationAction.RenameCase(at, from, to))

  /** Rename a top-level variant case. */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCase(DynamicOptic.root, from, to)

  /** Transform a specific case's payload using a nested builder. */
  def transformCase(
    at: DynamicOptic,
    caseName: String,
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions :+ MigrationAction.TransformCase(at, caseName, nested.actions))
  }

  /** Transform a top-level variant case's payload. */
  def transformCase(
    caseName: String,
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] =
    transformCase(DynamicOptic.root, caseName, f)

  // ── Collection operations ──────────────────────────────────────────

  /** Transform each element of a sequence at the given path. */
  def transformElements(
    at: DynamicOptic,
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions :+ MigrationAction.TransformElements(at, nested.actions))
  }

  /** Transform map keys at the given path. */
  def transformKeys(
    at: DynamicOptic,
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions :+ MigrationAction.TransformKeys(at, nested.actions))
  }

  /** Transform map values at the given path. */
  def transformValues(
    at: DynamicOptic,
    f: MigrationBuilder[A, B] => MigrationBuilder[A, B]
  ): MigrationBuilder[A, B] = {
    val nested = f(new MigrationBuilder(sourceSchema, targetSchema, Vector.empty))
    copy(actions :+ MigrationAction.TransformValues(at, nested.actions))
  }

  // ── Build ──────────────────────────────────────────────────────────

  /**
   * Build the migration. In a full implementation, `.build` would perform
   * macro-level validation that all source fields are accounted for in the
   * target schema. For now, this is equivalent to `buildPartial`.
   */
  def build: Migration[A, B] =
    Migration(new DynamicMigration(actions), sourceSchema, targetSchema)

  /** Build without full validation — useful during incremental development. */
  def buildPartial: Migration[A, B] = build
}
