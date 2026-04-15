package zio.schema.migration

import zio.schema.{DynamicValue}

/** Pure, serialisable description of a migration (a list of actions). */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /** Apply the migration to a DynamicValue. */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (acc, act) => acc.flatMap(act.apply)
    }

  /** Sequential composition – concatenate the action vectors. */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /** Reverse – reverse the order and replace each action with its inverse. */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}
