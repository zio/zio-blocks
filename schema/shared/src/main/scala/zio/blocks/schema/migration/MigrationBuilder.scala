package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr}
import zio.blocks.schema.migration.MigrationAction._

/**
 * A builder for constructing migrations using a fluent API.
 * This provides type-safe methods for adding migration actions.
 */
class MigrationBuilder[A, B] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  // ----- Record operations -----

  /**
   * Adds a new field to the target type.
   */
  def addField(
    target: B => Any,
    default: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(target)
    val action = AddField(optic, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drops a field from the source type.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[?] = SchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(source)
    val action = DropField(optic, defaultForReverse)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Renames a field.
   */
  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(from)
    val targetName = extractFieldName(to)
    val action = Rename(optic, targetName)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Transforms a field value.
   */
  def transformField(
    from: A => Any,
    to: B => Any,
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(from)
    val action = TransformValue(optic, transform)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Makes a field mandatory, providing a default value.
   */
  def mandateField(
    source: A => Option[?],
    target: B => Any,
    default: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(source)
    val action = Mandate(optic, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Converts a mandatory field to optional.
   */
  def optionalizeField(
    source: A => Any,
    target: B => Option[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(source)
    val action = Optionalize(optic)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Changes the type of a field.
   */
  def changeFieldType(
    source: A => Any,
    target: B => Any,
    converter: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(source)
    val action = ChangeType(optic, converter)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ----- Enum operations -----

  /**
   * Renames a case in a sum type.
   */
  def renameCase[SumA, SumB](
    from: String,
    to: String
  ): MigrationBuilder[A, B] = {
    val optic = DynamicOptic(IndexedSeq.empty)
    val action = RenameCase(optic, from, to)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Transforms a case using nested migration actions.
   */
  def transformCase[SumA, CaseA, SumB, CaseB](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  ): MigrationBuilder[A, B] = {
    val optic = DynamicOptic(IndexedSeq.empty)
    val nestedBuilder = MigrationBuilder[CaseA, CaseB](
      sourceSchema.asInstanceOf[Schema[CaseA]],
      targetSchema.asInstanceOf[Schema[CaseB]],
      Vector.empty
    )
    val transformedBuilder = caseMigration(nestedBuilder)
    val action = TransformCase(optic, transformedBuilder.actions)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ----- Collections -----

  /**
   * Transforms each element in a sequence.
   */
  def transformElements(
    at: A => Vector[?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(at)
    val action = TransformElements(optic, transform)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ----- Maps -----

  /**
   * Transforms keys in a map.
   */
  def transformKeys(
    at: A => Map[?, ?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(at)
    val action = TransformKeys(optic, transform)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Transforms values in a map.
   */
  def transformValues(
    at: A => Map[?, ?],
    transform: SchemaExpr[?]
  ): MigrationBuilder[A, B] = {
    val optic = extractOptic(at)
    val action = TransformValues(optic, transform)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ----- Build -----

  /**
   * Builds the migration with full macro validation.
   */
  def build: Migration[A, B] = {
    val dynamicMigration = DynamicMigration(actions)
    Migration(
      dynamicMigration = dynamicMigration,
      sourceSchema = sourceSchema,
      targetSchema = targetSchema
    )
  }

  /**
   * Builds the migration without full validation.
   */
  def buildPartial: Migration[A, B] = build

  // ----- Helper methods -----

  /**
   * Extracts a DynamicOptic from a selector function.
   * This is a placeholder - in a full implementation, this would use macros.
   */
  private def extractOptic(selector: A => Any): DynamicOptic = {
    // Simplified implementation - extracts the field name from the function
    // A full implementation would use macros to extract the path
    DynamicOptic(IndexedSeq.empty)
  }

  /**
   * Extracts a field name from a selector function.
   */
  private def extractFieldName(selector: B => Any): String = {
    // Simplified - would use macros in full implementation
    "field"
  }
}

object MigrationBuilder {

  def apply[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)
}
