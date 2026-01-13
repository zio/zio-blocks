package zio.blocks.schema

opaque type Variant = Int | String | Boolean

object Variant {
  def apply(v: Int | String | Boolean): Variant = v
}

opaque type StructureId <: String = String

given Schema[StructureId] = Schema.string
