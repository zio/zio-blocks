package zio.blocks.schema

case class MigrationResult(
  success: Boolean,
  message: String,
  data: Option[Any]
)