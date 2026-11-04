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
 * Compile-time evidence that `A` is a nominal type or an intersection of
 * nominal types (`A with B` on Scala 2, `A & B` on Scala 3), with the erased id
 * of each member in [[typeIdsErased]]. Each member must be nominal in the sense
 * of [[IsNominalType]]; a member that is a type parameter or a structural type
 * stops compilation with a message that starts with "Cannot derive
 * IsNominalIntersection".
 *
 * Use it to key a map by each member of an intersection; `Context#prune` in the
 * `context` module does this. A single type gives one id, and an intersection
 * gives one id per member, in the order written. On Scala 3 the compiler
 * simplifies the intersection first, so a member that is a supertype of another
 * is dropped: for `class Dog extends Animal`, `Dog & Named & Animal` gives the
 * ids of `Dog` and `Named`. Scala 2 keeps all three.
 *
 * {{{
 * // scala-3-only
 * trait Logger
 * trait Metrics
 *
 * IsNominalIntersection[Logger & Metrics].typeIdsErased.map(_.name) // Chunk(Logger,Metrics)
 * IsNominalIntersection[Logger].typeIdsErased.size                  // 1
 * }}}
 *
 * {{{
 * // scala-2-only
 * trait Logger
 * trait Metrics
 *
 * IsNominalIntersection[Logger with Metrics].typeIdsErased.map(_.name) // Chunk(Logger,Metrics)
 * }}}
 *
 * @tparam A
 *   the type witnessed: one nominal type, or an intersection of them
 * @see
 *   [[https://zioblocks.com/reference/typeid/ TypeId]]
 */
trait IsNominalIntersection[A] {

  /**
   * The erased id of each member of the intersection, in the order written, or
   * the id of `A` alone when `A` is not an intersection.
   */
  def typeIdsErased: Chunk[TypeId.Erased]
}

/**
 * Summons and derives [[IsNominalIntersection]] instances. The macro that
 * derives them runs wherever an implicit `IsNominalIntersection[A]` is
 * required.
 */
object IsNominalIntersection extends IsNominalIntersectionVersionSpecific {

  /**
   * Returns the implicit `IsNominalIntersection[A]`.
   */
  def apply[A](implicit ev: IsNominalIntersection[A]): IsNominalIntersection[A] = ev
}
