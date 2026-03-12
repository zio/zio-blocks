/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
