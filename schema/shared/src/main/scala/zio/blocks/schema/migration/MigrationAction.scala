package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}