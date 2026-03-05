package zio.blocks.schema

import zio.blocks.schema.migration._

/**
 * Package object providing extension methods for migration support.
 */
package object migration {

  /**
   * Extension methods for creating migrations from schemas.
   */
  implicit class MigrationSchemaOps[A](private val schema: Schema[A]) extends AnyVal {

    /**
     * Create an identity migration for this schema.
     */
    def identityMigration: Migration[A, A] = Migration.identity(schema)

    /**
     * Create a migration builder from this schema to another.
     */
    def migrateTo[B](targetSchema: Schema[B]): MigrationBuilder[A, B] =
      Migration.builder(schema, targetSchema)
  }

  /**
   * Extension methods for DynamicOptic in the context of migrations.
   */
  implicit class MigrationOpticOps(private val optic: DynamicOptic) extends AnyVal {

    /**
     * Create an AddField action at this optic path.
     */
    def addField(fieldName: String, default: SchemaExpr[?]): MigrationAction =
      MigrationAction.addField(optic, fieldName, default)

    /**
     * Create a DropField action at this optic path.
     */
    def dropField(fieldName: String, defaultForReverse: SchemaExpr[?]): MigrationAction =
      MigrationAction.dropField(optic, fieldName, defaultForReverse)

    /**
     * Create a RenameField action at this optic path.
     */
    def renameField(from: String, to: String): MigrationAction =
      MigrationAction.renameField(optic, from, to)

    /**
     * Create a TransformValue action at this optic path.
     */
    def transformValue(fieldName: String, transform: SchemaExpr[?]): MigrationAction =
      MigrationAction.transformValue(optic, fieldName, transform)

    /**
     * Create a MandateField action at this optic path.
     */
    def mandateField(fieldName: String, default: SchemaExpr[?]): MigrationAction =
      MigrationAction.mandateField(optic, fieldName, default)

    /**
     * Create an OptionalizeField action at this optic path.
     */
    def optionalizeField(fieldName: String): MigrationAction =
      MigrationAction.optionalizeField(optic, fieldName)

    /**
     * Create a RenameCase action at this optic path.
     */
    def renameCase(from: String, to: String): MigrationAction =
      MigrationAction.renameCase(optic, from, to)

    /**
     * Create a TransformCase action at this optic path.
     */
    def transformCase(caseName: String, actions: MigrationAction*): MigrationAction =
      MigrationAction.transformCase(optic, caseName, actions.toVector)

    /**
     * Create a TransformElements action at this optic path.
     */
    def transformElements(transform: SchemaExpr[?]): MigrationAction =
      MigrationAction.transformElements(optic, transform)

    /**
     * Create a TransformKeys action at this optic path.
     */
    def transformKeys(transform: SchemaExpr[?]): MigrationAction =
      MigrationAction.transformKeys(optic, transform)

    /**
     * Create a TransformValues action at this optic path.
     */
    def transformValues(transform: SchemaExpr[?]): MigrationAction =
      MigrationAction.transformValues(optic, transform)
  }
}
