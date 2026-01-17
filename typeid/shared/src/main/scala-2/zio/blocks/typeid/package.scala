package zio.blocks

package object typeid extends TypeIdVersionSpecific {
  type AnyKind    = Any
  type EmptyTuple = Unit
  type *:[H, T]   = scala.Tuple2[H, T]
}
