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

package zio.http.htmx

import scala.concurrent.duration.FiniteDuration

import _root_.zio.blocks.chunk.Chunk
import _root_.zio.blocks.html.Js

/**
 * A value that `hxTrigger` accepts: one [[HxTrigger]] or several in an
 * [[HxTriggerSet]].
 */
sealed trait HxTriggerValue extends Product with Serializable {

  /** Renders this value as the literal `hx-trigger` attribute value. */
  def render: String
}

/** Holds the [[ToHtmxValue]] instance for [[HxTriggerValue]]. */
object HxTriggerValue {

  /** Renders any [[HxTriggerValue]] with its `render` method. */
  implicit val toHtmxValue: ToHtmxValue[HxTriggerValue] = new ToHtmxValue[HxTriggerValue] {
    def toHtmxValue(value: HxTriggerValue): String = value.render
  }
}

/**
 * One `hx-trigger` declaration: the event that sends an htmx request, an
 * optional JavaScript filter and modifiers such as `delay:` or `changed`.
 *
 * Start from a predefined event such as [[HxTrigger.click]] or
 * [[HxTrigger.input]], or name one with `HxTrigger("my-event")`, then add
 * modifiers. Each method returns a new trigger. Modifiers render in the order
 * they were added, except that adding a modifier again removes the earlier one
 * and appends the new one at the end:
 * `click.delay(1.second).once.delay(2.seconds)` renders `click once delay:2s`.
 * Combine several triggers with `HxTrigger(first, more*)`, which returns an
 * [[HxTriggerSet]].
 *
 * {{{
 * import scala.concurrent.duration.DurationInt
 * import zio.blocks.html._
 *
 * val search = input(
 *   name := "q",
 *   hxGet := "/search",
 *   hxTrigger := HxTrigger.input.changed.delay(300.millis),
 *   hxTarget := HxTarget.css("#results")
 * ).render
 * // <input name="q" hx-get="/search" hx-trigger="input changed delay:300ms" hx-target="#results"/>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-trigger/ HxTrigger]]
 */
final case class HxTrigger private (
  event: String,
  filter: Option[Js] = None,
  modifiers: Chunk[HxTrigger.Modifier] = Chunk.empty
) extends HxTriggerValue {

  /**
   * Adds or replaces the `delay:` modifier: htmx waits this long after the last
   * event before sending the request. The duration renders as in
   * [[HxSwap#swap]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `duration` is negative
   */
  def delay(duration: FiniteDuration): HxTrigger =
    withModifier(HxTrigger.Modifier.Delay(HtmxSupport.requireNonNegativeDuration(duration, "trigger delay")))

  /**
   * Adds or replaces the `throttle:` modifier: htmx sends at most one request
   * per period and drops the events in between. The duration renders as in
   * [[HxSwap#swap]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `duration` is negative
   */
  def throttle(duration: FiniteDuration): HxTrigger =
    withModifier(HxTrigger.Modifier.Throttle(HtmxSupport.requireNonNegativeDuration(duration, "trigger throttle")))

  /** Adds the `once` modifier: the trigger fires only once. */
  def once: HxTrigger = withModifier(HxTrigger.Modifier.Once)

  /**
   * Adds the `changed` modifier: the trigger fires only when the element's
   * value has changed.
   */
  def changed: HxTrigger = withModifier(HxTrigger.Modifier.Changed)

  /**
   * Adds or replaces the `from:` modifier, which listens for the event on other
   * elements, such as `document` or a CSS selector. The selector is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def from(selector: String): HxTrigger =
    withModifier(HxTrigger.Modifier.From(HtmxSupport.requireNonBlank(selector, "HTMX trigger source selector")))

  /**
   * Adds or replaces the `from:` modifier with a rendered [[HxTarget]], such as
   * `from:closest form`.
   */
  def from(target: HxTarget): HxTrigger = from(target.render)

  /**
   * Adds or replaces the `target:` modifier: the trigger fires only when the
   * event's target matches the CSS selector, which is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def target(selector: String): HxTrigger =
    withModifier(HxTrigger.Modifier.Target(HtmxSupport.requireNonBlank(selector, "HTMX trigger target selector")))

  /**
   * Adds the `consume` modifier: the event does not trigger htmx requests on
   * parent elements.
   */
  def consume: HxTrigger = withModifier(HxTrigger.Modifier.Consume)

  /**
   * Adds or replaces the `queue:` modifier, which says which events to queue
   * while a request from this element is in flight.
   */
  def queue(strategy: HxTrigger.QueueStrategy): HxTrigger = withModifier(HxTrigger.Modifier.Queue(strategy))

  /**
   * Adds or replaces the `threshold:` modifier of an `intersect` trigger: the
   * visible fraction of the element, from 0.0 to 1.0, that fires it. The value
   * is not checked and renders with `Double.toString`, so a small value uses
   * exponent notation (`1.0E-5`).
   */
  def threshold(value: Double): HxTrigger = withModifier(HxTrigger.Modifier.Threshold(value))

  /**
   * Adds or replaces the `root:` modifier of an `intersect` trigger: the CSS
   * selector of the element whose visible area is observed. The selector is
   * trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `selector` is blank
   */
  def root(selector: String): HxTrigger =
    withModifier(HxTrigger.Modifier.Root(HtmxSupport.requireNonBlank(selector, "HTMX trigger root selector")))

  /**
   * Sets or replaces the JavaScript filter, rendered in square brackets after
   * the event name (`keyup[key=='Enter']`); htmx sends the request only when
   * the expression is true.
   *
   * `Js` is a raw JavaScript surface: the expression is written as given, so
   * never build it from user input.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the expression is blank
   */
  def filter(expression: Js): HxTrigger = {
    HtmxSupport.requireNonBlank(expression.value, "HTMX trigger filter")
    copy(filter = Some(expression))
  }

  /**
   * Renders the event name, then the filter in square brackets, then each
   * modifier after a space.
   */
  def render: String = {
    val sb = new java.lang.StringBuilder(event)
    filter.foreach(js => sb.append('[').append(js.value).append(']'))
    modifiers.foreach { modifier =>
      sb.append(' ')
      sb.append(modifier.render)
    }
    sb.toString
  }

  private def withModifier(modifier: HxTrigger.Modifier): HxTrigger =
    copy(modifiers = HxTrigger.normalize(modifiers, modifier))
}

/**
 * Several trigger declarations in one `hx-trigger` value, built by
 * `HxTrigger(first, more*)`;
 * `HxTrigger(HxTrigger.load, HxTrigger.every(30.seconds))` renders
 * `load, every 30s`.
 */
final case class HxTriggerSet(triggers: Chunk[HxTrigger]) extends HxTriggerValue {

  /** Renders the triggers separated by a comma and a space. */
  def render: String = triggers.map(_.render).mkString(", ")
}

/** Holds the [[ToHtmxValue]] instance for [[HxTriggerSet]]. */
object HxTriggerSet {

  /** Renders an [[HxTriggerSet]] with [[HxTriggerSet#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxTriggerSet] = new ToHtmxValue[HxTriggerSet] {
    def toHtmxValue(value: HxTriggerSet): String = value.render
  }
}

/** Predefined triggers, factories and the modifier types of [[HxTrigger]]. */
object HxTrigger {

  /**
   * What the `queue:` modifier does with events that arrive while a request
   * from the element is in flight.
   */
  sealed trait QueueStrategy extends Product with Serializable {

    /** The strategy as written after `queue:`. */
    def render: String
  }

  /** The four [[QueueStrategy]] values. */
  object QueueStrategy {

    /** `first`: queues the first such event. */
    case object First extends QueueStrategy { def render: String = "first" }

    /** `last`: queues the last such event. */
    case object Last extends QueueStrategy { def render: String = "last" }

    /** `all`: queues every such event. */
    case object All extends QueueStrategy { def render: String = "all" }

    /** `none`: queues no events. */
    case object None extends QueueStrategy { def render: String = "none" }
  }

  /**
   * One modifier of a trigger declaration. The [[HxTrigger]] methods build
   * these and validate their arguments; the case class constructors do not.
   */
  sealed trait Modifier extends Product with Serializable {

    /** The modifier as written in `hx-trigger`, such as `delay:300ms`. */
    def render: String
  }

  /** The [[Modifier]] cases. */
  object Modifier {

    /** `delay:<duration>`; see [[HxTrigger#delay]]. */
    final case class Delay(duration: FiniteDuration) extends Modifier {

      /** Renders `delay:` followed by the duration. */
      def render: String = "delay:" + HtmxSupport.renderDuration(duration)
    }

    /** `throttle:<duration>`; see [[HxTrigger#throttle]]. */
    final case class Throttle(duration: FiniteDuration) extends Modifier {

      /** Renders `throttle:` followed by the duration. */
      def render: String = "throttle:" + HtmxSupport.renderDuration(duration)
    }

    /** `from:<selector>`; see `HxTrigger#from`. */
    final case class From(selector: String) extends Modifier {

      /** Renders `from:` followed by the selector. */
      def render: String = "from:" + selector
    }

    /** `target:<selector>`; see [[HxTrigger#target]]. */
    final case class Target(selector: String) extends Modifier {

      /** Renders `target:` followed by the selector. */
      def render: String = "target:" + selector
    }

    /** `queue:<strategy>`; see [[HxTrigger#queue]]. */
    final case class Queue(strategy: QueueStrategy) extends Modifier {

      /** Renders `queue:` followed by the strategy. */
      def render: String = "queue:" + strategy.render
    }

    /** `threshold:<value>`; see [[HxTrigger#threshold]]. */
    final case class Threshold(value: Double) extends Modifier {

      /** Renders `threshold:` followed by `value.toString`. */
      def render: String = "threshold:" + value.toString
    }

    /** `root:<selector>`; see [[HxTrigger#root]]. */
    final case class Root(selector: String) extends Modifier {

      /** Renders `root:` followed by the selector. */
      def render: String = "root:" + selector
    }

    /** `once`; see [[HxTrigger#once]]. */
    case object Once extends Modifier {

      /** Renders `once`. */
      def render: String = "once"
    }

    /** `changed`; see [[HxTrigger#changed]]. */
    case object Changed extends Modifier {

      /** Renders `changed`. */
      def render: String = "changed"
    }

    /** `consume`; see [[HxTrigger#consume]]. */
    case object Consume extends Modifier {

      /** Renders `consume`. */
      def render: String = "consume"
    }
  }

  /**
   * Creates a trigger for any event name, such as a custom event that your
   * JavaScript or a response header fires. The name is trimmed and otherwise
   * written into `hx-trigger` as given, so validate any value that comes from
   * users.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `event` is blank
   */
  def apply(event: String): HxTrigger = new HxTrigger(HtmxSupport.requireNonBlank(event, "HTMX trigger event name"))

  /**
   * Combines one or more triggers into an [[HxTriggerSet]], which renders them
   * separated by `", "`.
   */
  def apply(trigger: HxTrigger, triggers: HxTrigger*): HxTriggerSet =
    HxTriggerSet(Chunk.from(trigger +: triggers))

  /** A trigger for the DOM `click` event. */
  def click: HxTrigger = HxTrigger("click")

  /** A trigger for the DOM `submit` event. */
  def submit: HxTrigger = HxTrigger("submit")

  /** A trigger for htmx's `load` event, which fires when the element loads. */
  def load: HxTrigger = HxTrigger("load")

  /**
   * A trigger for htmx's `revealed` event, which fires when the element scrolls
   * into the viewport.
   */
  def revealed: HxTrigger = HxTrigger("revealed")

  /**
   * A trigger for htmx's `intersect` event, which fires when the element
   * intersects the viewport; see [[HxTrigger#threshold]] and
   * [[HxTrigger#root]].
   */
  def intersect: HxTrigger = HxTrigger("intersect")

  /** A trigger for the DOM `change` event. */
  def change: HxTrigger = HxTrigger("change")

  /** A trigger for the DOM `input` event. */
  def input: HxTrigger = HxTrigger("input")

  /**
   * Creates an `every <interval>` polling trigger, such as `every 2s`. The
   * interval renders as in [[HxSwap#swap]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `duration` is negative
   */
  def every(duration: FiniteDuration): HxTrigger =
    HxTrigger("every " + HtmxSupport.renderDuration(HtmxSupport.requireNonNegativeDuration(duration, "poll interval")))

  /**
   * Returns `existing` without the modifiers of the same kind as `next`, with
   * `next` appended at the end; modifiers of other kinds keep their order.
   */
  private[htmx] def normalize(existing: Chunk[Modifier], next: Modifier): Chunk[Modifier] = {
    def sameGroup(left: Modifier, right: Modifier): Boolean = (left, right) match {
      case (_: Modifier.Delay, _: Modifier.Delay)         => true
      case (_: Modifier.Throttle, _: Modifier.Throttle)   => true
      case (_: Modifier.From, _: Modifier.From)           => true
      case (_: Modifier.Target, _: Modifier.Target)       => true
      case (_: Modifier.Queue, _: Modifier.Queue)         => true
      case (_: Modifier.Threshold, _: Modifier.Threshold) => true
      case (_: Modifier.Root, _: Modifier.Root)           => true
      case (Modifier.Once, Modifier.Once)                 => true
      case (Modifier.Changed, Modifier.Changed)           => true
      case (Modifier.Consume, Modifier.Consume)           => true
      case _                                              => false
    }

    val filtered = existing.filterNot(m => sameGroup(m, next))
    filtered :+ next
  }

  /** Renders an [[HxTrigger]] with [[HxTrigger#render]]. */
  implicit val triggerValue: ToHtmxValue[HxTrigger] = new ToHtmxValue[HxTrigger] {
    def toHtmxValue(value: HxTrigger): String = value.render
  }

}
