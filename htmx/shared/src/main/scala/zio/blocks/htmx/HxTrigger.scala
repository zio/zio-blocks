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

package zio.blocks.htmx

import scala.concurrent.duration.FiniteDuration

import zio.blocks.chunk.Chunk
import zio.blocks.html.Js

/** Common rendered shape for `hx-trigger` values. */
sealed trait HxTriggerValue extends Product with Serializable {
  def render: String
}

/**
 * Typed representation of a single `hx-trigger` declaration.
 *
 * A trigger starts with an event name and can be refined with an optional
 * JavaScript filter plus normalized modifier groups such as delay, throttle,
 * source selector, queue strategy, threshold, or root selector.
 */
final case class HxTrigger private (
  event: String,
  filter: Option[Js] = None,
  modifiers: Chunk[HxTrigger.Modifier] = Chunk.empty
) extends HxTriggerValue {

  /** Adds or replaces the `delay:` modifier. */
  def delay(duration: FiniteDuration): HxTrigger =
    withModifier(HxTrigger.Modifier.Delay(HtmxSupport.requireNonNegativeDuration(duration, "trigger delay")))

  /** Adds or replaces the `throttle:` modifier. */
  def throttle(duration: FiniteDuration): HxTrigger =
    withModifier(HxTrigger.Modifier.Throttle(HtmxSupport.requireNonNegativeDuration(duration, "trigger throttle")))

  /** Adds the `once` modifier. */
  def once: HxTrigger = withModifier(HxTrigger.Modifier.Once)

  /** Adds the `changed` modifier. */
  def changed: HxTrigger = withModifier(HxTrigger.Modifier.Changed)

  /** Adds or replaces the `from:` modifier. */
  def from(selector: String): HxTrigger = withModifier(HxTrigger.Modifier.From(selector))
  def from(target: HxTarget): HxTrigger = from(target.render)

  /** Adds or replaces the `target:` modifier. */
  def target(selector: String): HxTrigger = withModifier(HxTrigger.Modifier.Target(selector))

  /** Adds the `consume` modifier. */
  def consume: HxTrigger = withModifier(HxTrigger.Modifier.Consume)

  /** Adds or replaces the `queue:` modifier. */
  def queue(strategy: HxTrigger.QueueStrategy): HxTrigger = withModifier(HxTrigger.Modifier.Queue(strategy))

  /** Adds or replaces the `threshold:` modifier. */
  def threshold(value: Double): HxTrigger = withModifier(HxTrigger.Modifier.Threshold(value))

  /** Adds or replaces the `root:` modifier. */
  def root(selector: String): HxTrigger = withModifier(HxTrigger.Modifier.Root(selector))

  /** Replaces the JavaScript filter expression for this trigger. */
  def filter(expression: Js): HxTrigger = copy(filter = Some(expression))

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

/** Rendered collection of comma-separated HTMX triggers. */
final case class HxTriggerSet(triggers: Chunk[HxTrigger]) extends HxTriggerValue {
  def render: String = triggers.map(_.render).mkString(", ")
}

object HxTriggerSet {
  implicit val toHtmxValue: ToHtmxValue[HxTriggerSet] = new ToHtmxValue[HxTriggerSet] {
    def toHtmxValue(value: HxTriggerSet): String = value.render
  }
}

object HxTrigger {

  /** Queue strategies supported by HTMX's `queue:` modifier. */
  sealed trait QueueStrategy extends Product with Serializable {
    def render: String
  }

  object QueueStrategy {
    case object First extends QueueStrategy { def render: String = "first" }
    case object Last  extends QueueStrategy { def render: String = "last"  }
    case object All   extends QueueStrategy { def render: String = "all"   }
    case object None  extends QueueStrategy { def render: String = "none"  }
  }

  /** Modifier fragments that can be appended to a trigger declaration. */
  sealed trait Modifier extends Product with Serializable {
    def render: String
  }

  object Modifier {
    final case class Delay(duration: FiniteDuration) extends Modifier {
      def render: String = "delay:" + HtmxSupport.renderDuration(duration)
    }

    final case class Throttle(duration: FiniteDuration) extends Modifier {
      def render: String = "throttle:" + HtmxSupport.renderDuration(duration)
    }

    final case class From(selector: String) extends Modifier {
      def render: String = "from:" + selector
    }

    final case class Target(selector: String) extends Modifier {
      def render: String = "target:" + selector
    }

    final case class Queue(strategy: QueueStrategy) extends Modifier {
      def render: String = "queue:" + strategy.render
    }

    final case class Threshold(value: Double) extends Modifier {
      def render: String = "threshold:" + value.toString
    }

    final case class Root(selector: String) extends Modifier {
      def render: String = "root:" + selector
    }

    case object Once extends Modifier {
      def render: String = "once"
    }

    case object Changed extends Modifier {
      def render: String = "changed"
    }

    case object Consume extends Modifier {
      def render: String = "consume"
    }
  }

  /** Creates a trigger from an arbitrary HTMX event name. */
  def apply(event: String): HxTrigger = new HxTrigger(event)

  /** Creates a comma-separated set of multiple trigger declarations. */
  def apply(trigger: HxTrigger, triggers: HxTrigger*): HxTriggerSet =
    HxTriggerSet(Chunk.from(trigger +: triggers))

  def click: HxTrigger     = HxTrigger("click")
  def submit: HxTrigger    = HxTrigger("submit")
  def load: HxTrigger      = HxTrigger("load")
  def revealed: HxTrigger  = HxTrigger("revealed")
  def intersect: HxTrigger = HxTrigger("intersect")
  def change: HxTrigger    = HxTrigger("change")
  def input: HxTrigger     = HxTrigger("input")

  /** Creates an `every ...` polling trigger. */
  def every(duration: FiniteDuration): HxTrigger =
    HxTrigger("every " + HtmxSupport.renderDuration(HtmxSupport.requireNonNegativeDuration(duration, "poll interval")))

  /**
   * Normalizes modifiers so later values in the same modifier group replace
   * earlier ones while unrelated modifiers are preserved.
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

  implicit val triggerValue: ToHtmxValue[HxTrigger] = new ToHtmxValue[HxTrigger] {
    def toHtmxValue(value: HxTrigger): String = value.render
  }

}
