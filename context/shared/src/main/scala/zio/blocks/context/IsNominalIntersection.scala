package zio.blocks.context

import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

trait IsNominalIntersection[A] {
  def typeIdsErased: Chunk[TypeId.Erased]
}

object IsNominalIntersection extends IsNominalIntersectionVersionSpecific {
  def apply[A](implicit ev: IsNominalIntersection[A]): IsNominalIntersection[A] = ev
}
