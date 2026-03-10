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

package zio.blocks.scope

import scala.deriving.Mirror
import scala.compiletime.*

private[scope] trait UnscopedVersionSpecific {

  private val singleton: Unscoped[Any] = new Unscoped[Any] {}

  /**
   * Derives [[Unscoped]] for case classes, sealed traits, and enums. All
   * fields/cases must have `Unscoped` instances.
   *
   * Uses `Mirror.Of[A]` constraint so this given is only a candidate for types
   * that have a Mirror (case classes, sealed traits, enums). This allows
   * implicit search to fall through to lower-priority instances for non-Mirror
   * types.
   *
   * @tparam A
   *   the case class, sealed trait, or enum to derive `Unscoped` for
   * @return
   *   an `Unscoped[A]` instance, or a compile error if any constituent type
   *   lacks an `Unscoped` instance
   */
  inline given derived[A](using m: Mirror.Of[A]): Unscoped[A] =
    inline m match {
      case pm: Mirror.ProductOf[A] => derivedProduct[A](using pm)
      case sm: Mirror.SumOf[A]     => derivedSum[A](using sm)
    }

  private inline def derivedProduct[A](using m: Mirror.ProductOf[A]): Unscoped[A] = {
    requireAllUnscoped[m.MirroredElemTypes]
    singleton.asInstanceOf[Unscoped[A]]
  }

  private inline def derivedSum[A](using m: Mirror.SumOf[A]): Unscoped[A] = {
    requireAllUnscoped[m.MirroredElemTypes]
    singleton.asInstanceOf[Unscoped[A]]
  }

  private inline def requireAllUnscoped[T <: Tuple]: Unit =
    inline erasedValue[T] match {
      case _: EmptyTuple => ()
      case _: (h *: t)   =>
        summonInline[Unscoped[h]]
        requireAllUnscoped[t]
    }
}
