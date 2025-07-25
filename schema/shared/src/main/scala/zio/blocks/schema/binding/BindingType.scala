package zio.blocks.schema.binding

final abstract class BindingType

object BindingType {
  type Record <: BindingType
  type Variant <: BindingType
  type Seq[_[_]] <: BindingType
  type Map[_[_, _]] <: BindingType
  type Primitive <: BindingType
  type Wrapper[_, _] <: BindingType
  type Dynamic <: BindingType
}
