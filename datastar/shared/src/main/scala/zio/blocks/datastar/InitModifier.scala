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
 * A modifier of a `data-init` attribute, rendered as a `__` suffix.
 * [[DataInit]] builds these values; a repeated `Delay` keeps the last value and
 * a repeated `ViewTransition` is kept once.
 */
sealed trait InitModifier extends Product with Serializable {

  /** Returns the attribute-name suffix, such as `__delay.500ms`. */
  def render: String
}

/** The `data-init` modifiers. */
object InitModifier {

  /** Runs the expression `millis` milliseconds late. */
  final case class Delay(millis: Long) extends InitModifier {

    /** Returns `__delay.<millis>ms`. */
    def render: String = "__delay." + millis + "ms"
  }

  /**
   * Runs the expression inside a view transition where the browser supports it.
   */
  case object ViewTransition extends InitModifier {

    /** Returns `__viewTransition`. */
    def render: String = "__viewTransition"
  }

  /** Two modifiers together. */
  final case class And(left: InitModifier, right: InitModifier) extends InitModifier {

    /** Returns the left suffix followed by the right one. */
    def render: String = left.render + right.render
  }

  private[datastar] def normalize(existing: Maybe[InitModifier], next: InitModifier): Maybe[InitModifier] =
    existing.fold(Maybe.present(next): Maybe[InitModifier]) { current =>
      val normalized = flatten(current :: next :: Nil).foldLeft(List.empty[InitModifier]) { (acc, modifier) =>
        modifier match {
          case d: Delay =>
            acc.filter {
              case _: Delay => false
              case _        => true
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

  private def flatten(modifiers: List[InitModifier]): List[InitModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
