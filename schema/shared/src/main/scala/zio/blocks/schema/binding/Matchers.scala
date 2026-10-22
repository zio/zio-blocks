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

package zio.blocks.schema.binding

/**
 * The [[Matcher]]s of a [[Binding.Variant]], one per case, in the order of the
 * variant node's cases.
 *
 * @tparam A
 *   the sum type
 */
final case class Matchers[+A](matchers: IndexedSeq[Matcher[? <: A]]) {

  /**
   * Returns the matcher of the case at `index`, which must be a valid case
   * index.
   */
  def apply(index: Int): Matcher[? <: A] = matchers(index)
}

/** Creates [[Matchers]]. */
object Matchers {

  /**
   * Creates the matchers of a variant from one matcher per case, in case order.
   */
  def apply[A](matchers: Matcher[? <: A]*): Matchers[A] = new Matchers(matchers.toIndexedSeq)
}
