package zio.schema

import zio.schema.DynamicValue

trait DynamicMigration {
  def migrate(value: DynamicValue): DynamicValue
}
