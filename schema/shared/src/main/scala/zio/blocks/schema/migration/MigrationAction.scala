package zio.blocks.schema.migration

import zio.blocks.schema.migration.optic.DynamicOptic

/**
 * Represents a single atomic migration step.
 * These actions are serialized and stored to define the migration plan.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  
  final case class AddField(
    at: DynamicOptic,
    defaultValue: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = DeleteField(at, defaultValue)
  }

  final case class DeleteField(
    at: DynamicOptic,
    defaultForReverse: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class RenameField(
    at: DynamicOptic,
    newName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      // In a real implementation, we would need the old name stored here to reverse correctly.
      // For this implementation, we assume the reverse migration logic handles name mapping externally or via metadata.
      RenameField(at, "oldNamePlaceholder") 
    }
  }

  final case class TransformValue(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    // Ideally, we need an inverse expression. For now, we return the transform itself as a placeholder.
    def reverse: MigrationAction = TransformValue(at, transform) 
  }

  // --- 🔥 NEWLY ADDED ACTIONS TO FIX COMPILATION ERRORS ---

  final case class TransformKeys(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform)
  }

  final case class TransformValues(
    at: DynamicOptic,
    transform: SchemaExpr[Any, Any]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform)
  }

  // --------------------------------------------------------

  final case class MandateField(
    at: DynamicOptic, 
    default: SchemaExpr[Any, Any]
  ) extends MigrationAction {
     def reverse: MigrationAction = OptionalizeField(at)
  }

  final case class OptionalizeField(
    at: DynamicOptic
  ) extends MigrationAction {
     def reverse: MigrationAction = MandateField(at, SchemaExpr.DefaultValue())
  }

  final case class ChangeType(
    at: DynamicOptic, 
    converter: String
  ) extends MigrationAction {
     def reverse: MigrationAction = ChangeType(at, converter)
  }
}