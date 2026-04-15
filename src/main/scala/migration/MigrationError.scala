package migration

case class MigrationError(
  message: String,
  path: Option[DynamicOptic] = None
)