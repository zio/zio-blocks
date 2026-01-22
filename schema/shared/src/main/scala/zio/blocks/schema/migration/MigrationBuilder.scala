package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, DynamicOptic, DynamicValue}

/**
 * A builder for constructing migrations in a type-safe, fluent manner.
 *
 * All selector-accepting methods will be implemented via macros to extract
 * field names and paths at compile time.
 *
 * For now, this is a basic implementation that will be enhanced with macros.
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ===== Record Operations =====

  /**
   * Add a field with a default value.
   *
   * Example: .addFieldWithDefault("country", "USA")
   */
  def addFieldWithDefault(fieldName: String, defaultValue: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      fieldName,
      SchemaExpr.literalString(defaultValue)
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Add a field with a default integer value.
   */
  def addFieldWithDefault(fieldName: String, defaultValue: Int): MigrationBuilder[A, B] = {
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      fieldName,
      SchemaExpr.literalInt(defaultValue)
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Add a field with a DynamicValue default.
   */
  def addField(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] = {
    val action = MigrationAction.AddField(
      DynamicOptic.root,
      fieldName,
      SchemaExpr.literal(default)
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field from the source schema.
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(
      DynamicOptic.root,
      fieldName,
      None
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field with a default value for reverse migration.
   */
  def dropFieldWithDefault(fieldName: String, defaultValue: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.DropField(
      DynamicOptic.root,
      fieldName,
      Some(SchemaExpr.literalString(defaultValue))
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Rename a field.
   *
   * Note: In the full implementation, this will use macros to extract field
   * names from selector functions like: .renameField(_.name, _.fullName)
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.Rename(
      DynamicOptic.root,
      from,
      to
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Make an optional field mandatory.
   */
  def mandateFieldWithDefault(fieldName: String, defaultValue: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.Mandate(
      DynamicOptic.root,
      fieldName,
      SchemaExpr.literalString(defaultValue)
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.Optionalize(
      DynamicOptic.root,
      fieldName
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ===== Enum Operations =====

  /**
   * Rename an enum case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] = {
    val action = MigrationAction.RenameCase(
      DynamicOptic.root,
      from,
      to
    )
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ===== Build Methods =====

  /**
   * Internal method called by the macro after validation. Do not call directly -
   * use build() instead.
   */
  def buildUnchecked: Migration[A, B] =
    Migration(
      DynamicMigration(actions),
      sourceSchema,
      targetSchema
    )
}

object MigrationBuilder {

  /**
   * Create a new migration builder.
   *
   * Example:
   * {{{
   * val migration = MigrationBuilder(PersonV1.schema, PersonV2.schema)
   *   .addFieldWithDefault("country", "USA")
   *   .renameField("name", "fullName")
   *   .build
   * }}}
   */
  def apply[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
