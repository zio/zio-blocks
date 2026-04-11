package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

final case class MigrationError(message: String, path: List[String] = Nil)
