package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

final case class DynamicMigration(actions: Vector[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => MigrationInterpreter.run(v, action))
    }
  }

  def ++(that: DynamicMigration): DynamicMigration = DynamicMigration(this.actions ++ that.actions)
  def reverse: DynamicMigration = DynamicMigration(actions.reverse.map(_.reverse))
}

object DynamicMigration {
  implicit val schema: Schema[DynamicMigration] = Schema.derived
}