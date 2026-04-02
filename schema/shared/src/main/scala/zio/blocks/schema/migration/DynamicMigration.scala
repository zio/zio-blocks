package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap { v =>
        MigrationApplicator.applyAction(action, v)
      }
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}
