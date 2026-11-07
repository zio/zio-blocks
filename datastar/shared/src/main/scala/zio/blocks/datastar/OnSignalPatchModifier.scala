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

package zio.http.datastar

import zio.blocks.maybe.Maybe

/**
 * A modifier of a `data-on-signal-patch` attribute, rendered as a `__` suffix.
 * [[DataOnSignalPatch]] builds these values and keeps the last one of each
 * kind.
 */
sealed trait OnSignalPatchModifier extends Product with Serializable {

  /** Returns the attribute-name suffix, such as `__debounce.200ms`. */
  def render: String
}

/** The `data-on-signal-patch` modifiers. */
object OnSignalPatchModifier {

  /** Runs the expression `millis` milliseconds late. */
  final case class Delay(millis: Long) extends OnSignalPatchModifier {

    /** Returns `__delay.<millis>ms`. */
    def render: String = "__delay." + millis + "ms"
  }

  /** Debounces the trigger by `millis` milliseconds. */
  final case class Debounce(millis: Long) extends OnSignalPatchModifier {

    /** Returns `__debounce.<millis>ms`. */
    def render: String = "__debounce." + millis + "ms"
  }

  /** Throttles the trigger to once every `millis` milliseconds. */
  final case class Throttle(millis: Long) extends OnSignalPatchModifier {

    /** Returns `__throttle.<millis>ms`. */
    def render: String = "__throttle." + millis + "ms"
  }

  /** Two modifiers together. */
  final case class And(left: OnSignalPatchModifier, right: OnSignalPatchModifier) extends OnSignalPatchModifier {

    /** Returns the left suffix followed by the right one. */
    def render: String = left.render + right.render
  }

  private[datastar] def normalize(
    existing: Maybe[OnSignalPatchModifier],
    next: OnSignalPatchModifier
  ): Maybe[OnSignalPatchModifier] =
    existing.fold(Maybe.present(next): Maybe[OnSignalPatchModifier]) { current =>
      val normalized = flatten(current :: next :: Nil)
        .foldLeft(List.empty[OnSignalPatchModifier]) { (acc, modifier) =>
          modifier match {
            case d: Delay =>
              acc.filter {
                case _: Delay => false
                case _        => true
              } :+ d
            case d: Debounce =>
              acc.filter {
                case _: Debounce => false
                case _           => true
              } :+ d
            case t: Throttle =>
              acc.filter {
                case _: Throttle => false
                case _           => true
              } :+ t
            case other =>
              if (acc.exists(_ == other)) acc else acc :+ other
          }
        }

      normalized match {
        case Nil          => Maybe.absent
        case head :: Nil  => Maybe.present(head)
        case head :: tail => Maybe.present(tail.foldLeft(head)(And.apply))
      }
    }

  private def flatten(modifiers: List[OnSignalPatchModifier]): List[OnSignalPatchModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
