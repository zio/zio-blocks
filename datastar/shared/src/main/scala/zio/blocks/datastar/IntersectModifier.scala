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
 * A modifier of a `data-on-intersect` attribute, rendered as a `__` suffix.
 * [[DataOnIntersect]] builds these values and combines them with
 * [[IntersectModifier.And]].
 */
sealed trait IntersectModifier extends Product with Serializable {

  /** Returns the attribute-name suffix, such as `__once`. */
  def render: String
}

/** The `data-on-intersect` modifiers. */
object IntersectModifier {

  /** Runs the expression only the first time. */
  case object Once extends IntersectModifier {

    /** Returns `__once`. */
    def render: String = "__once"
  }

  /** Runs the expression when half of the element is visible. */
  case object Half extends IntersectModifier {

    /** Returns `__half`. */
    def render: String = "__half"
  }

  /** Runs the expression when all of the element is visible. */
  case object Full extends IntersectModifier {

    /** Returns `__full`. */
    def render: String = "__full"
  }

  /** Runs the expression when the element leaves the viewport. */
  case object Exit extends IntersectModifier {

    /** Returns `__exit`. */
    def render: String = "__exit"
  }

  /**
   * Runs the expression when `pct` percent of the element is visible; Datastar
   * reads the value as a percentage from 0 to 100.
   */
  final case class Threshold(pct: Double) extends IntersectModifier {

    /**
     * Returns `__threshold.<pct>`, with the number written without trailing
     * zeros.
     *
     * @throws java.lang.NumberFormatException
     *   if `pct` is `NaN` or infinite
     */
    def render: String = "__threshold." + BigDecimal(pct).underlying().stripTrailingZeros().toPlainString
  }

  /** Runs the expression `millis` milliseconds late. */
  final case class Delay(millis: Long) extends IntersectModifier {

    /** Returns `__delay.<millis>ms`. */
    def render: String = "__delay." + millis + "ms"
  }

  /** Debounces the trigger by `millis` milliseconds. */
  final case class Debounce(millis: Long) extends IntersectModifier {

    /** Returns `__debounce.<millis>ms`. */
    def render: String = "__debounce." + millis + "ms"
  }

  /** Throttles the trigger to once every `millis` milliseconds. */
  final case class Throttle(millis: Long) extends IntersectModifier {

    /** Returns `__throttle.<millis>ms`. */
    def render: String = "__throttle." + millis + "ms"
  }

  /**
   * Runs the expression inside a view transition where the browser supports it.
   */
  case object ViewTransition extends IntersectModifier {

    /** Returns `__viewTransition`. */
    def render: String = "__viewTransition"
  }

  /** Two modifiers together. */
  final case class And(left: IntersectModifier, right: IntersectModifier) extends IntersectModifier {

    /** Returns the left suffix followed by the right one. */
    def render: String = left.render + right.render
  }

  private[datastar] def normalize(
    existing: Maybe[IntersectModifier],
    next: IntersectModifier
  ): Maybe[IntersectModifier] =
    existing.fold(Maybe.present(next): Maybe[IntersectModifier]) { current =>
      val normalized = flatten(current :: next :: Nil)
        .foldLeft(List.empty[IntersectModifier]) { (acc, modifier) =>
          modifier match {
            case t: Threshold =>
              acc.filter {
                case _: Threshold | Half | Full => false
                case _                          => true
              } :+ t
            case Half =>
              acc.filter {
                case _: Threshold | Half | Full => false
                case _                          => true
              } :+ Half
            case Full =>
              acc.filter {
                case _: Threshold | Half | Full => false
                case _                          => true
              } :+ Full
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

  private def flatten(modifiers: List[IntersectModifier]): List[IntersectModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
