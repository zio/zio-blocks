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

package zio.blocks.datastar

import zio.blocks.maybe.Maybe

/**
 * Modifier ADT used by `data-on-interval` builders.
 *
 * Interval modifiers encode the polling duration and optional view transition.
 * `Duration(..., leading = true)` represents Datastar's leading-edge interval
 * variant. Composition via [[OnIntervalModifier.And]] preserves the order of
 * encoded suffixes before normalization removes redundant duration entries.
 */
sealed trait OnIntervalModifier extends Product with Serializable {

  /** Renders the Datastar modifier suffix for this interval modifier. */
  def render: String
}

object OnIntervalModifier {

  /** Constructors and normalization rules for interval-trigger modifiers. */

  /**
   * Encodes a duration-based interval, optionally using the leading variant.
   */
  final case class Duration(millis: Long, leading: Boolean) extends OnIntervalModifier {

    def render: String =
      if (leading) "__duration." + millis + "ms.leading"
      else "__duration." + millis + "ms"
  }

  /** Wraps the interval action in a view transition when supported. */
  case object ViewTransition extends OnIntervalModifier {
    def render: String = "__viewTransition"
  }

  /** Concatenates two interval modifier suffixes in order. */
  final case class And(left: OnIntervalModifier, right: OnIntervalModifier) extends OnIntervalModifier {
    def render: String = left.render + right.render
  }

  private[datastar] def normalize(existing: Maybe[OnIntervalModifier], next: OnIntervalModifier): Maybe[OnIntervalModifier] =
    existing.fold(Maybe.present(next): Maybe[OnIntervalModifier]) { current =>
      val normalized = flatten(current :: next :: Nil)
      .foldLeft(List.empty[OnIntervalModifier]) { (acc, modifier) =>
      modifier match {
        case d: Duration =>
          acc.filter {
            case _: Duration => false
            case _           => true
          } :+ d
        case flag =>
          if (acc.exists(_ == flag)) acc else acc :+ flag
      }
    }

      normalized match {
        case Nil          => Maybe.absent
        case head :: Nil  => Maybe.present(head)
        case head :: tail => Maybe.present(tail.foldLeft(head)(And.apply))
      }
    }

  private def flatten(modifiers: List[OnIntervalModifier]): List[OnIntervalModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
