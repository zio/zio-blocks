package zio.blocks.schema.migration

final case class MigrationError(message: String, path: List[String] = Nil)
