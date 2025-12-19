package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue
import zio.json.{JsonDecoder, JsonEncoder}

sealed trait MigrationAction derives JsonEncoder, JsonDecoder {
  def at: DynamicOptic
  def reverse: MigrationAction

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}