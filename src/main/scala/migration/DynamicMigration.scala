package migration

case class DynamicMigration(
  actions: Vector[MigrationAction]
) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft(Right(value): Either[MigrationError, DynamicValue]) {
      case (acc, action) => acc.flatMap(action.apply)
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))
}