package zio.blocks.schema.migration

final case class DynamicMigration(actions: Vector[MigrationAction])
