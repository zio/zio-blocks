package zio.blocks.schema.json

import zio.blocks.schema._

object Modifiers {
  def rename(name: String): Modifier.config = Modifier.config(renameKey, name)

  private[json] val renameKey = "json.rename"
}
