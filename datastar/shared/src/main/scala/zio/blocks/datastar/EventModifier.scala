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
 * Modifier ADT used by `dataOn` event bindings.
 *
 * Modifiers alter Datastar's event-handling semantics, such as debounce,
 * throttle, delay, capture phase, and propagation/default handling. The
 * rendered form is encoded directly into the attribute name. Composition via
 * [[EventModifier.And]] preserves left-to-right ordering of suffixes.
 */
sealed trait EventModifier extends Product with Serializable {

  /** Renders the Datastar modifier suffix for this modifier. */
  def render: String
}

object EventModifier {

  /** Constructors and normalization rules for `data-on:*` modifiers. */

  /** Debounces event handling for the specified interval. */
  final case class Debounce(millis: Long, leading: Boolean) extends EventModifier {

    def render: String =
      if (leading) "__debounce." + millis + "ms.leading"
      else "__debounce." + millis + "ms"
  }

  /** Throttles event handling for the specified interval. */
  final case class Throttle(millis: Long, leading: Boolean) extends EventModifier {

    def render: String =
      if (leading) "__throttle." + millis + "ms.leading"
      else "__throttle." + millis + "ms"
  }

  /** Delays event handling by the specified interval. */
  final case class Delay(millis: Long) extends EventModifier {
    def render: String = "__delay." + millis + "ms"
  }

  /** Handles the event at most once. */
  case object Once extends EventModifier {
    def render: String = "__once"
  }

  /** Marks the listener as passive. */
  case object Passive extends EventModifier {
    def render: String = "__passive"
  }

  /** Registers the listener during the capture phase. */
  case object Capture extends EventModifier {
    def render: String = "__capture"
  }

  /** Calls `stopPropagation()` during event handling. */
  case object Stop extends EventModifier {
    def render: String = "__stop"
  }

  /** Calls `preventDefault()` during event handling. */
  case object Prevent extends EventModifier {
    def render: String = "__prevent"
  }

  /** Restricts handling to events originating outside the bound element. */
  case object Outside extends EventModifier {
    def render: String = "__outside"
  }

  /** Attaches the listener to `window`. */
  case object Window extends EventModifier {
    def render: String = "__window"
  }

  /** Attaches the listener to `document`. */
  case object Document extends EventModifier {
    def render: String = "__document"
  }

  /** Wraps handling in a view transition when supported. */
  case object ViewTransition extends EventModifier {
    def render: String = "__viewTransition"
  }

  /** Concatenates two modifier suffixes in left-to-right order. */
  final case class And(left: EventModifier, right: EventModifier) extends EventModifier {
    def render: String = left.render + right.render
  }

  /**
   * Normalizes repeated or conflicting modifiers into the final suffix set used
   * by the DSL.
   */
  private[datastar] def normalize(existing: Maybe[EventModifier], next: EventModifier): Maybe[EventModifier] =
    existing.fold(Maybe.present(next): Maybe[EventModifier]) { current =>
      val normalized = flatten(current :: next :: Nil).foldLeft(List.empty[EventModifier]) {
      (acc, modifier) =>
      modifier match {
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
        case d: Delay =>
          acc.filter {
            case _: Delay => false
            case _        => true
          } :+ d
        case Window =>
          acc.filter {
            case Window | Document => false
            case _                 => true
          } :+ Window
        case Document =>
          acc.filter {
            case Window | Document => false
            case _                 => true
          } :+ Document
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

  private def flatten(modifiers: List[EventModifier]): List[EventModifier] =
    modifiers.flatMap {
      case And(left, right) => flatten(left :: right :: Nil)
      case other            => other :: Nil
    }
}
