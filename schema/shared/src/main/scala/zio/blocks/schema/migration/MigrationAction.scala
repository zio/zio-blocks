package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaExpr

sealed trait MigrationAction {
  def at: DynamicOptic
}

object MigrationAction {
  // --- Record Actions ---
  final case class AddField(at: DynamicOptic, default: SchemaExpr[?, ?]) extends MigrationAction
  final case class DropField(at: DynamicOptic)                           extends MigrationAction
  final case class RenameField(at: DynamicOptic, newName: String)        extends MigrationAction

  // --- Value Actions ---
  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[?, ?]) extends MigrationAction
  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[?, ?])     extends MigrationAction
  final case class Optionalize(at: DynamicOptic)                                 extends MigrationAction
  final case class Mandate(at: DynamicOptic, default: SchemaExpr[?, ?])          extends MigrationAction

  // --- Enum Actions ---
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction

  // --- Collection Actions ---
  final case class TransformElements(at: DynamicOptic, action: MigrationAction) extends MigrationAction
  final case class TransformValues(at: DynamicOptic, action: MigrationAction)   extends MigrationAction
}
