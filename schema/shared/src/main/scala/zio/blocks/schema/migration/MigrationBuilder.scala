package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A builder for constructing migrations type-safely using a fluent DSL.
 * 
 * MigrationBuilder provides a convenient way to create migrations without
 * manually constructing MigrationAction instances.
 * 
 * ==Example Usage==
 * 
 * {{{
 * val migration = Migration.builder[UserV1, UserV2]
 *   .renameField(_.name, "fullName")
 *   .addField("email", SchemaExpr.Literal("", Schema[String]))
 *   .build
 * }}}
 * 
 * ==Macro Support==
 * 
 * In the future, macro-based selectors will enable:
 * 
 * {{{
 * Migration.builder[UserV1, UserV2]
 *   .rename(_.name -> _.fullName)  // Macro extracts field names
 *   .transform(_.age)(a => a + 1)  // Macro creates SchemaExpr
 *   .build
 * }}}
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction] = Vector.empty
) {

  /**
   * Add an action to rename a field.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.renameField(DynamicOptic.root, from, to))

  /**
   * Add an action to rename a field at a specific path.
   */
  def renameField(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.renameField(at, from, to))

  /**
   * Add an action to add a new field with a default value.
   */
  def addField(fieldName: String, default: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.addField(DynamicOptic.root, fieldName, default))

  /**
   * Add an action to add a new field at a specific path.
   */
  def addField(at: DynamicOptic, fieldName: String, default: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.addField(at, fieldName, default))

  /**
   * Add an action to drop a field.
   */
  def dropField(fieldName: String, defaultForReverse: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.dropField(DynamicOptic.root, fieldName, defaultForReverse))

  /**
   * Add an action to drop a field at a specific path.
   */
  def dropField(at: DynamicOptic, fieldName: String, defaultForReverse: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.dropField(at, fieldName, defaultForReverse))

  /**
   * Add an action to transform a field's value.
   */
  def transformField(fieldName: String, transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformValue(DynamicOptic.root, fieldName, transform))

  /**
   * Add an action to transform a field's value at a specific path.
   */
  def transformField(at: DynamicOptic, fieldName: String, transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformValue(at, fieldName, transform))

  /**
   * Add an action to mandate that a required field must have a value.
   */
  def mandateField(fieldName: String, default: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.mandateField(DynamicOptic.root, fieldName, default))

  /**
   * Add an action to mandate that a required field must have a value at a specific path.
   */
  def mandateField(at: DynamicOptic, fieldName: String, default: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.mandateField(at, fieldName, default))

  /**
   * Add an action to make a required field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.optionalizeField(DynamicOptic.root, fieldName))

  /**
   * Add an action to make a required field optional at a specific path.
   */
  def optionalizeField(at: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.optionalizeField(at, fieldName))

  /**
   * Add an action to change a field's type.
   */
  def changeFieldType(fieldName: String, converter: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.changeFieldType(DynamicOptic.root, fieldName, converter))

  /**
   * Add an action to change a field's type at a specific path.
   */
  def changeFieldType(at: DynamicOptic, fieldName: String, converter: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.changeFieldType(at, fieldName, converter))

  /**
   * Add an action to join multiple fields into one.
   */
  def joinFields(
    targetField: String,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.joinFields(DynamicOptic.root, targetField, sourcePaths, combiner))

  /**
   * Add an action to join multiple fields into one at a specific path.
   */
  def joinFields(
    at: DynamicOptic,
    targetField: String,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.joinFields(at, targetField, sourcePaths, combiner))

  /**
   * Add an action to split a field into multiple fields.
   */
  def splitField(
    sourceField: String,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.splitField(DynamicOptic.root, sourceField, targetPaths, splitter))

  /**
   * Add an action to split a field into multiple fields at a specific path.
   */
  def splitField(
    at: DynamicOptic,
    sourceField: String,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[?]
  ): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.splitField(at, sourceField, targetPaths, splitter))

  // ============================================================================
  // Enum/Variant Actions
  // ============================================================================

  /**
   * Add an action to rename a case in a sum type.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.renameCase(DynamicOptic.root, from, to))

  /**
   * Add an action to rename a case in a sum type at a specific path.
   */
  def renameCase(at: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.renameCase(at, from, to))

  /**
   * Add an action to transform a case with nested actions.
   */
  def transformCase(caseName: String, caseActions: MigrationAction*): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformCase(DynamicOptic.root, caseName, caseActions.toVector))

  /**
   * Add an action to transform a case with nested actions at a specific path.
   */
  def transformCase(at: DynamicOptic, caseName: String, caseActions: MigrationAction*): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformCase(at, caseName, caseActions.toVector))

  // ============================================================================
  // Collection/Map Actions
  // ============================================================================

  /**
   * Add an action to transform all elements in a collection.
   */
  def transformElements(transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformElements(DynamicOptic.root, transform))

  /**
   * Add an action to transform all elements in a collection at a specific path.
   */
  def transformElements(at: DynamicOptic, transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformElements(at, transform))

  /**
   * Add an action to transform all keys in a map.
   */
  def transformKeys(transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformKeys(DynamicOptic.root, transform))

  /**
   * Add an action to transform all keys in a map at a specific path.
   */
  def transformKeys(at: DynamicOptic, transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformKeys(at, transform))

  /**
   * Add an action to transform all values in a map.
   */
  def transformValues(transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformValues(DynamicOptic.root, transform))

  /**
   * Add an action to transform all values in a map at a specific path.
   */
  def transformValues(at: DynamicOptic, transform: SchemaExpr[?]): MigrationBuilder[A, B] =
    copy(actions = actions :+ MigrationAction.transformValues(at, transform))

  // ============================================================================
  // Generic Actions
  // ============================================================================

  /**
   * Add a raw MigrationAction.
   */
  def add(action: MigrationAction): MigrationBuilder[A, B] =
    copy(actions = actions :+ action)

  /**
   * Add multiple raw MigrationActions.
   */
  def addAll(newActions: MigrationAction*): MigrationBuilder[A, B] =
    copy(actions = actions ++ newActions)

  // ============================================================================
  // Build
  // ============================================================================

  /**
   * Build the final Migration.
   */
  def build: Migration[A, B] =
    Migration(
      DynamicMigration(actions),
      sourceSchema,
      targetSchema
    )

  /**
   * Build the DynamicMigration (losing type information).
   */
  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)
}

object MigrationBuilder {

  /**
   * Create a new migration builder.
   */
  def apply[A, B](
    implicit sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a migration builder from an existing migration.
   */
  def from[A, B](migration: Migration[A, B]): MigrationBuilder[A, B] =
    MigrationBuilder(
      migration.sourceSchema,
      migration.targetSchema,
      migration.dynamicMigration.actions
    )
}
