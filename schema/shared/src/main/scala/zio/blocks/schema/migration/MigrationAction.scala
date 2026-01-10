package zio.blocks.schema.migration

import zio.blocks.schema.migration.optic.DynamicOptic

sealed trait MigrationAction extends Serializable {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  
  // --- Record Actions ---
  final case class AddField(at: DynamicOptic, defaultValue: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = DeleteField(at, defaultValue)
  }

  final case class DeleteField(at: DynamicOptic, defaultForReverse: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class RenameField(at: DynamicOptic, newName: String) extends MigrationAction {
    def reverse: MigrationAction = RenameField(at, "oldNamePlaceholder") 
  }

  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform) 
  }

  final case class MandateField(at: DynamicOptic, default: SchemaExpr[Any, Any]) extends MigrationAction {
     def reverse: MigrationAction = OptionalizeField(at)
  }

  final case class OptionalizeField(at: DynamicOptic) extends MigrationAction {
     def reverse: MigrationAction = MandateField(at, SchemaExpr.DefaultValue())
  }

  // 🔥 FIX: Added Missing ChangeType Action
  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter) // Ideally needs inverse converter
  }

  // --- Collection & Map Actions ---
  final case class TransformElements(at: DynamicOptic, transform: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform)
  }

  final case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform)
  }

  final case class TransformValues(at: DynamicOptic, transform: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform)
  }

  // --- Enum Actions ---
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, innerActions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, innerActions.map(_.reverse))
  }
}