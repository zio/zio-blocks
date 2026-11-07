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

import zio.blocks.html._
import zio.blocks.maybe.Maybe

/**
 * Picks the event of a `data-on:<event>` handler. `dataOn` from
 * [[DatastarAttributes]] returns one; each method returns a [[DataOn]] for one
 * event, and [[apply]] takes any other event name.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * val count = Signal[Int]("count")
 *
 * button(dataOn.click := js"alert('hi')").render
 * // <button data-on:click="alert(&#x27;hi&#x27;)"></button>
 *
 * input(dataOn("custom-event").debounce(500) := count.ref).render
 * // <input data-on:custom-event__debounce.500ms="\$count"/>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/events/ Event Handlers]]
 */
final class PartialDataOn {

  /** Returns a handler for the `click` event, `data-on:click`. */
  def click: DataOn = new DataOn("click", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `submit` event, `data-on:submit`. */
  def submit: DataOn = new DataOn("submit", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `input` event, `data-on:input`. */
  def input: DataOn = new DataOn("input", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `keydown` event, `data-on:keydown`. */
  def keydown: DataOn = new DataOn("keydown", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `keyup` event, `data-on:keyup`. */
  def keyup: DataOn = new DataOn("keyup", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `keypress` event, `data-on:keypress`. */
  def keypress: DataOn = new DataOn("keypress", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `change` event, `data-on:change`. */
  def change: DataOn = new DataOn("change", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `focus` event, `data-on:focus`. */
  def focus: DataOn = new DataOn("focus", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `blur` event, `data-on:blur`. */
  def blur: DataOn = new DataOn("blur", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `mouseover` event, `data-on:mouseover`. */
  def mouseover: DataOn = new DataOn("mouseover", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `mouseout` event, `data-on:mouseout`. */
  def mouseout: DataOn = new DataOn("mouseout", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `mouseenter` event, `data-on:mouseenter`. */
  def mouseenter: DataOn = new DataOn("mouseenter", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `mouseleave` event, `data-on:mouseleave`. */
  def mouseleave: DataOn = new DataOn("mouseleave", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `scroll` event, `data-on:scroll`. */
  def scroll: DataOn = new DataOn("scroll", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `resize` event, `data-on:resize`. */
  def resize: DataOn = new DataOn("resize", Maybe.absent, CaseModifier.Kebab)

  /** Returns a handler for the `load` event, `data-on:load`. */
  def load: DataOn = new DataOn("load", Maybe.absent, CaseModifier.Kebab)

  /**
   * Returns a handler for the event `name`, written in kebab case (`myEvent`
   * becomes `data-on:my-event`). The name is otherwise written as given,
   * without checks or escaping, so pass only names from your own code.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is empty or contains `__`
   */
  def apply(name: String): DataOn = {
    require(
      name.nonEmpty && !name.contains("__"),
      s"Invalid Datastar custom event name '$name'. Custom event names must be non-empty and must not contain '__'."
    )
    new DataOn(name, Maybe.absent, CaseModifier.Kebab)
  }
}

/**
 * A `data-on:<event>` handler for one event: chain modifiers, then assign the
 * expression to run with `:=`. Each modifier returns a new builder and adds a
 * `__` suffix to the attribute name, in the order of the calls.
 *
 * A second `debounce`, `throttle` or `delay` (in either variant) replaces the
 * first and moves to the end, `window` and `document` replace each other, and a
 * repeated flag such as `once` is kept once, at its first position. The case
 * methods (`camel`, `kebab`, `snake`, `pascal`) tell Datastar how to turn the
 * kebab-case name back into the event's real name; `kebab` is the default and
 * adds no suffix.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * input(dataOn.input.debounce(300).debounce(500) := js"@get('/search')").render
 * // <input data-on:input__debounce.500ms="@get(&#x27;/search&#x27;)"/>
 *
 * div(dataOn("myEvent").camel := js"@get('/handle')").render
 * // <div data-on:my-event__case.camel="@get(&#x27;/handle&#x27;)"></div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/events/ Event Handlers]]
 */
final class DataOn(
  private[datastar] val eventName: String,
  private[datastar] val modifier: Maybe[EventModifier],
  private[datastar] val caseModifier: CaseModifier
) {

  /**
   * Adds `__debounce.<millis>ms`: the handler runs once the event has stopped
   * firing for `millis` milliseconds.
   */
  def debounce(millis: Long): DataOn = withModifier(EventModifier.Debounce(millis, false))

  /**
   * Adds `__debounce.<millis>ms.leading`: a debounce that also runs the handler
   * on the first event.
   */
  def debounceLeading(millis: Long): DataOn = withModifier(EventModifier.Debounce(millis, true))

  /**
   * Adds `__throttle.<millis>ms`: the handler runs at most once every `millis`
   * milliseconds.
   */
  def throttle(millis: Long): DataOn = withModifier(EventModifier.Throttle(millis, false))

  /**
   * Adds `__throttle.<millis>ms.leading`. Datastar's throttle already runs on
   * the leading edge, and its docs list no `leading` option for throttle.
   */
  def throttleLeading(millis: Long): DataOn = withModifier(EventModifier.Throttle(millis, true))

  /** Adds `__delay.<millis>ms`: the handler runs `millis` milliseconds late. */
  def delay(millis: Long): DataOn = withModifier(EventModifier.Delay(millis))

  /** Adds `__once`: the handler runs at most once. */
  def once: DataOn = withModifier(EventModifier.Once)

  /** Adds `__passive`, which registers a passive listener. */
  def passive: DataOn = withModifier(EventModifier.Passive)

  /** Adds `__capture`, which listens in the capture phase. */
  def capture: DataOn = withModifier(EventModifier.Capture)

  /** Adds `__stop`, which calls `stopPropagation()` on the event. */
  def stop: DataOn = withModifier(EventModifier.Stop)

  /** Adds `__prevent`, which calls `preventDefault()` on the event. */
  def prevent: DataOn = withModifier(EventModifier.Prevent)

  /**
   * Adds `__outside`: the handler runs for events outside the element, such as
   * a click away from a dropdown.
   */
  def outside: DataOn = withModifier(EventModifier.Outside)

  /** Adds `__window`, which listens on `window`; it replaces `document`. */
  def window: DataOn = withModifier(EventModifier.Window)

  /** Adds `__document`, which listens on `document`; it replaces `window`. */
  def document: DataOn = withModifier(EventModifier.Document)

  /**
   * Adds `__viewTransition`, which runs the handler inside a view transition
   * where the browser supports it.
   */
  def viewTransition: DataOn = withModifier(EventModifier.ViewTransition)

  /** Adds `__case.camel`: the event's real name is camel case (`myEvent`). */
  def camel: DataOn = new DataOn(eventName, modifier, CaseModifier.Camel)

  /** Selects kebab case (`my-event`), the default, which adds no suffix. */
  def kebab: DataOn = new DataOn(eventName, modifier, CaseModifier.Kebab)

  /** Adds `__case.snake`: the event's real name is snake case (`my_event`). */
  def snake: DataOn = new DataOn(eventName, modifier, CaseModifier.Snake)

  /** Adds `__case.pascal`: the event's real name is Pascal case (`MyEvent`). */
  def pascal: DataOn = new DataOn(eventName, modifier, CaseModifier.Pascal)

  /**
   * Returns the attribute `data-on:<event><modifiers><case>="<expression>"`,
   * with the event name in kebab case, the modifiers in order and the case
   * suffix last.
   */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val caseSuffix  = caseModifier.suffix(CaseModifier.Kebab)
    val attrName    = "data-on:" + toKebabCase(eventName) + modifierStr + caseSuffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: EventModifier): DataOn =
    new DataOn(eventName, EventModifier.normalize(modifier, m), caseModifier)
}
