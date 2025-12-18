package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {
  // Record actions
  final case class AddField(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, SchemaExpr.DefaultValue())
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
  final case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
  final case class RenameField(at: DynamicOptic, newName: String) extends MigrationAction {
    def reverse: MigrationAction = ??? // a bit more complex, need to know old name
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = ??? // requires reverse transform
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  // Enum actions
  final case class RenameCase(at: DynamicOptic, oldName: String, newName: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, newName, oldName)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
  final case class TransformCase(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  // Collection actions
  final case class TransformElements(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
      def reverse: MigrationAction = TransformElements(at, migration.reverse)
      def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  final case class TransformKeys(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }

  final case class TransformValues(at: DynamicOptic, migration: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, migration.reverse)
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = ???
  }
}
