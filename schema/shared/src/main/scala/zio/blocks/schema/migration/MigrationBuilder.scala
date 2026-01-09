package zio.blocks.schema.migration

import zio.schema.Schema
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep, SelectorMacro}

/**
 * A builder class to construct migrations incrementally.
 * UPDATED: Strictly follows the ZIO Schema 2 documentation signature using SchemaExpr.
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // --- Record Operations ---

  /**
   * Adds a new field to the target schema.
   * Requirement Match: Uses SchemaExpr instead of raw DynamicValue.
   */
  inline def addField[T](
    inline selector: B => T, 
    default: SchemaExpr // Fixed: Was DynamicValue, now SchemaExpr
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacro.translate(selector)
    val newAction = MigrationAction.AddField(path, default) // Action-ও আপডেট করতে হবে
    copy(actions = actions :+ newAction)
  }

  /**
   * Drops a field from the source schema.
   */
  inline def dropField[T](
    inline selector: A => T,
    defaultForReverse: SchemaExpr = SchemaExpr.DefaultValue // Fixed: Added default param as per doc
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacro.translate(selector)
    val newAction = MigrationAction.DeleteField(path, defaultForReverse)
    copy(actions = actions :+ newAction)
  }

  /**
   * Renames a field.
   */
  inline def renameField[T, U](
    inline from: A => T,
    inline to: B => U
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacro.translate(from)
    val toPath   = SelectorMacro.translate(to)

    // Extract just the field name for the 'to' parameter
    val newName = toPath.steps.lastOption match {
      case Some(OpticStep.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target selector must point to a valid field name")
    }

    val newAction = MigrationAction.RenameField(fromPath, newName)
    copy(actions = actions :+ newAction)
  }

  // --- Build Method ---
  def build: Migration[A, B] = {
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))
  }

  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)
}