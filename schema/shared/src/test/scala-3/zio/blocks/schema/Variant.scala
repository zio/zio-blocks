package zio.blocks.schema

opaque type Variant = Int | String | Boolean

object Variant {
  def apply(v: Int | String | Boolean): Variant = v
}
