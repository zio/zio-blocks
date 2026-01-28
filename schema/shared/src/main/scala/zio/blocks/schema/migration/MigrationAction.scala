package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

sealed trait MigrationAction

object MigrationAction {
  final case class AddField(path: DynamicOptic, default: DynamicSchemaExpr)            extends MigrationAction
  final case class DropField(path: DynamicOptic, reverse: Option[DynamicValue] = None) extends MigrationAction
  final case class Rename(path: DynamicOptic, name: String)                            extends MigrationAction
  final case class TransformValue(
    path: DynamicOptic,
    transform: DynamicSchemaExpr,
    reverse: Option[DynamicSchemaExpr] = None
  ) extends MigrationAction
  final case class Mandate(path: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction
  final case class Optionalize(path: DynamicOptic)                         extends MigrationAction
}
