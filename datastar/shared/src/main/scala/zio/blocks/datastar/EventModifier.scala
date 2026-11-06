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
 * A modifier of a `data-on:<event>` handler, rendered as a `__` suffix of the
 * attribute name. The [[DataOn]] methods (`debounce`, `once`, `prevent`, ...)
 * build these values and combine them with [[EventModifier.And]], whose suffix
 * is its left suffix followed by its right one.
 *
 * {{{
 * EventModifier.And(EventModifier.Once, EventModifier.Prevent).render // "__once__prevent"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/events/ Event Handlers]]
 */
sealed trait EventModifier extends Product with Serializable {

  /** Returns the attribute-name suffix, such as `__debounce.500ms`. */
  def render: String
}

/** The `data-on` modifiers. */
object EventModifier {

  /**
   * Runs the handler once the event has stopped firing for `millis`
   * milliseconds; `leading` also runs it on the first event.
   */
  final case class Debounce(millis: Long, leading: Boolean) extends EventModifier {

    /**
     * Returns `__debounce.<millis>ms`, plus `.leading` when `leading` is set.
     */
    def render: String =
      if (leading) "__debounce." + millis + "ms.leading"
      else "__debounce." + millis + "ms"
  }

  /**
   * Runs the handler at most once every `millis` milliseconds; `leading` adds
   * `.leading`, which Datastar's docs do not list for throttle.
   */
  final case class Throttle(millis: Long, leading: Boolean) extends EventModifier {

    /**
     * Returns `__throttle.<millis>ms`, plus `.leading` when `leading` is set.
     */
    def render: String =
      if (leading) "__throttle." + millis + "ms.leading"
      else "__throttle." + millis + "ms"
  }

  /** Runs the handler `millis` milliseconds after the event. */
  final case class Delay(millis: Long) extends EventModifier {

    /** Returns `__delay.<millis>ms`. */
    def render: String = "__delay." + millis + "ms"
  }

  /** Runs the handler at most once. */
  case object Once extends EventModifier {

    /** Returns `__once`. */
    def render: String = "__once"
  }

  /** Registers the listener as passive. */
  case object Passive extends EventModifier {

    /** Returns `__passive`. */
    def render: String = "__passive"
  }

  /** Listens in the capture phase. */
  case object Capture extends EventModifier {

    /** Returns `__capture`. */
    def render: String = "__capture"
  }

  /** Calls `stopPropagation()` on the event. */
  case object Stop extends EventModifier {

    /** Returns `__stop`. */
    def render: String = "__stop"
  }

  /** Calls `preventDefault()` on the event. */
  case object Prevent extends EventModifier {

    /** Returns `__prevent`. */
    def render: String = "__prevent"
  }

  /** Runs the handler for events outside the element. */
  case object Outside extends EventModifier {

    /** Returns `__outside`. */
    def render: String = "__outside"
  }

  /** Listens on `window`. */
  case object Window extends EventModifier {

    /** Returns `__window`. */
    def render: String = "__window"
  }

  /** Listens on `document`. */
  case object Document extends EventModifier {

    /** Returns `__document`. */
    def render: String = "__document"
  }

  /**
   * Runs the handler inside a view transition where the browser supports it.
   */
  case object ViewTransition extends EventModifier {

    /** Returns `__viewTransition`. */
    def render: String = "__viewTransition"
  }

  /** Two modifiers together. */
  final case class And(left: EventModifier, right: EventModifier) extends EventModifier {

    /** Returns the left suffix followed by the right one. */
    def render: String = left.render + right.render
  }

  /**
   * Adds `next` to `existing`. A `Debounce`, `Throttle` or `Delay` replaces any
   * earlier one of its kind and goes last, `Window` and `Document` replace each
   * other, and a repeated flag is kept once, at its first position.
   */
  private[datastar] def normalize(existing: Maybe[EventModifier], next: EventModifier): Maybe[EventModifier] =
    existing.fold(Maybe.present(next): Maybe[EventModifier]) { current =>
      val normalized = flatten(current :: next :: Nil).foldLeft(List.empty[EventModifier]) { (acc, modifier) =>
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
