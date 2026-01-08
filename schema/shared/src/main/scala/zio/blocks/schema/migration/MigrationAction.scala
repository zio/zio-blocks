package zio.blocks.schema.migration

import zio.schema.DynamicValue

sealed trait MigrationAction

object MigrationAction {

  final case class AddField(
    path: Vector[String],
    defaultValue: DynamicValue
  ) extends MigrationAction

  final case class RenameField(
    path: Vector[String],
    newName: String
  ) extends MigrationAction

  final case class DeleteField(
    path: Vector[String]
  ) extends MigrationAction

  final case class ChangeType(
    path: Vector[String],
    targetType: String
  ) extends MigrationAction
}

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)
}