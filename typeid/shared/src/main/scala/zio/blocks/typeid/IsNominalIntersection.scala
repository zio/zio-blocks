package zio.blocks.typeid

import zio.blocks.chunk.Chunk

/**
 * A compile-time witness that `A` is a nominal intersection type — either a
 * single nominal type or a conjunction (`A & B` / `A with B`) of nominal types.
 *
 * Each member of the intersection must itself satisfy [[IsNominalType]]
 * constraints: no union branches, no structural refinements, no unresolved type
 * parameters. Derivation fails at compile time if any member is non-nominal.
 *
 * The primary use-case is `Context`, which keys its internal map by the erased
 * `TypeId` of each member in the intersection.
 *
 * @tparam A
 *   The intersection type being witnessed. May be a plain nominal type (in
 *   which case [[typeIdsErased]] has length 1) or an intersection (length ≥ 2).
 */
trait IsNominalIntersection[A] {
  def typeIdsErased: Chunk[TypeId.Erased]
}

object IsNominalIntersection extends IsNominalIntersectionVersionSpecific {
  def apply[A](implicit ev: IsNominalIntersection[A]): IsNominalIntersection[A] = ev
}
