package zio.blocks.schema.json

import zio.blocks.schema.DynamicValue

object fromJson extends (String => DynamicValue) {
  def apply(rawJson: String): DynamicValue = ???
}
