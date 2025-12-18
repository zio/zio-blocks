package zio.blocks.schema.migration

import zio.blocks.schema.Schema

object MigrationCheck {
  val builder = Migration.newBuilder(Schema[Int], Schema[String])
}
