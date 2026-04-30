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

/**
 * Modifier ADT used by `data-on-intersect` builders.
 *
 * Intersect modifiers encode visibility thresholds, timing behavior, and view
 * transitions into the attribute name. Composition preserves left-to-right
 * ordering, while normalization collapses conflicting threshold/timing choices.
 */
sealed trait IntersectModifier extends Product with Serializable {

  /** Renders the Datastar modifier suffix for this intersect modifier. */
  def render: String
}

object IntersectModifier {

  /** Constructors and normalization rules for intersect-trigger modifiers. */

  /** Fires at most once. */
  case object Once extends IntersectModifier {
    def render: String = "__once"
  }

  /** Fires when the element is at least half visible. */
  case object Half extends IntersectModifier {
    def render: String = "__half"
  }

  /** Fires when the element is fully visible. */
  case object Full extends IntersectModifier {
    def render: String = "__full"
  }

  /** Fires when the element exits the intersection area. */
  case object Exit extends IntersectModifier {
    def render: String = "__exit"
  }

  /** Fires when the intersection ratio crosses the provided threshold. */
  final case class Threshold(pct: Double) extends IntersectModifier {
    def render: String = "__threshold." + pct
  }

  /** Delays intersect handling by the specified interval. */
  final case class Delay(millis: Long) extends IntersectModifier {
    def render: String = "__delay." + millis + "ms"
  }

  /** Debounces intersect handling by the specified interval. */
  final case class Debounce(millis: Long) extends IntersectModifier {
    def render: String = "__debounce." + millis + "ms"
  }

  /** Throttles intersect handling by the specified interval. */
  final case class Throttle(millis: Long) extends IntersectModifier {
    def render: String = "__throttle." + millis + "ms"
  }

  /** Wraps the intersect action in a view transition when supported. */
  case object ViewTransition extends IntersectModifier {
    def render: String = "__viewTransition"
  }

  /** Concatenates two intersect modifier suffixes in order. */
  final case class And(left: IntersectModifier, right: IntersectModifier) extends IntersectModifier {
    def render: String = left.render + right.render
  }

  def normalize(existing: Option[IntersectModifier], next: IntersectModifier): Option[IntersectModifier] = {
    val normalized = flatten(existing.toList :+ next).foldLeft(List.empty[IntersectModifier]) { (acc, modifier) =>
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
      case Nil          => None
      case head :: Nil  => Some(head)
      case head :: tail => Some(tail.foldLeft(head)(And.apply))
    }
  }

  private def flatten(modifiers: List[IntersectModifier]): List[IntersectModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
