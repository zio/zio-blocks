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
 * A `Discriminator` tells which case of a sum type a value belongs to: it is
 * half of a [[Binding.Variant]]. It returns the index of the case in the order
 * of the variant node's cases, and the [[Matcher]] at that index of the
 * variant's [[Matchers]] downcasts the value to that case.
 *
 * @tparam A
 *   the sum type
 */
trait Discriminator[-A] {

  /** Returns the 0-based index of the case that `a` belongs to. */
  def discriminate(a: A): Int
}
