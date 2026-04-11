package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  def ++(that: DynamicMigration): DynamicMigration = DynamicMigration(actions ++ that.actions)
  def reverse: DynamicMigration = DynamicMigration(actions.reverse.map(_.reverse))
}
object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)
}
