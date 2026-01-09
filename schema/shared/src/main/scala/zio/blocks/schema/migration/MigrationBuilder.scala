package zio.blocks.schema.migration

import zio.schema.{Schema, DynamicValue}
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep, SelectorMacro}

/**
 * A builder class to construct migrations incrementally.
 * It uses macros to convert user-friendly selectors (e.g., _.age) into internal paths.
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // --- Record Operations ---

  /**
   * Adds a new field to the target schema.
   * Usage: .addField(_.newField, defaultValue)
   */
  inline def addField[T](
    inline selector: B => T, 
    defaultValue: DynamicValue // আপাতত DynamicValue রাখছি, পরে SchemaExpr এ শিফট করব
  ): MigrationBuilder[A, B] = {
    // ম্যাক্রো কল করে পাথ বের করছি
    val path = SelectorMacro.translate(selector)
    
    // নতুন অ্যাকশন লিস্টে যোগ করছি
    val newAction = MigrationAction.AddField(path, defaultValue)
    copy(actions = actions :+ newAction)
  }

  /**
   * Drops a field from the source schema.
   * Usage: .dropField(_.oldField)
   */
  inline def dropField[T](
    inline selector: A => T
  ): MigrationBuilder[A, B] = {
    val path = SelectorMacro.translate(selector)
    
    // TODO: Add defaultForReverse support later (Phase 1.4)
    val newAction = MigrationAction.DeleteField(path)
    copy(actions = actions :+ newAction)
  }

  /**
   * Renames a field.
   * Usage: .renameField(_.oldName, _.newName)
   */
  inline def renameField[T, U](
    inline from: A => T,
    inline to: B => U
  ): MigrationBuilder[A, B] = {
    val fromPath = SelectorMacro.translate(from)
    val toPath   = SelectorMacro.translate(to)

    // রিনেম অ্যাকশনে আমাদের শুধু নতুন নামটা (String) দরকার, পুরো পাথ না।
    // তাই আমরা toPath এর শেষ স্টেপটা এক্সট্রাক্ট করব।
    val newName = toPath.steps.lastOption match {
      case Some(OpticStep.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target selector must point to a valid field name")
    }

    val newAction = MigrationAction.RenameField(fromPath, newName)
    copy(actions = actions :+ newAction)
  }

  // --- Build Methods ---

  /**
   * Compiles the builder into a final Migration object.
   * TODO: Add macro validation to check if all fields are handled (Phase 2.2)
   */
  def build: Migration[A, B] = {
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))
  }

  // হেল্পার মেথড: ইমিউটেবল কপি তৈরি করার জন্য
  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)
}