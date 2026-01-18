package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}
import zio.blocks.Chunk

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  final case class AddField(at: DynamicOptic, default: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, "TODO") 
  }

  final case class TransformValue(at: DynamicOptic, expr: SchemaExpr[Any, Any]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  final case class TransformCase(at: DynamicOptic, actions: Chunk[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }
}