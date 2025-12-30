package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

sealed trait MigrationAction

object MigrationAction {
  final case class AddField(optic: DynamicOptic, default: DynamicValue) extends MigrationAction
  final case class DropField(optic: DynamicOptic, default: DynamicValue) extends MigrationAction
  final case class Rename(optic: DynamicOptic, toName: String) extends MigrationAction
  final case class TransformValue(optic: DynamicOptic, f: DynamicValue => Either[String, DynamicValue]) extends MigrationAction
}
