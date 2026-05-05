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

import zio.blocks.html.Dom

/**
 * Entry point for building `data-on:*` event handler attributes.
 *
 * Obtained via `dataOn` from [[DatastarAttributes]]. Select an event (`click`,
 * `submit`, `keydown`, etc.) to get a [[DataOn]] builder, then assign a handler
 * with `:=`. Use [[apply]] for custom event names.
 *
 * {{{
 * dataOn.click := js"alert('hi')"
 * dataOn("custom-event").debounce(500) := count.ref
 * }}}
 */
final class PartialDataOn {

  def click: DataOn = new DataOn("click", None, CaseModifier.Kebab)

  def submit: DataOn = new DataOn("submit", None, CaseModifier.Kebab)

  def input: DataOn = new DataOn("input", None, CaseModifier.Kebab)

  def keydown: DataOn = new DataOn("keydown", None, CaseModifier.Kebab)

  def keyup: DataOn = new DataOn("keyup", None, CaseModifier.Kebab)

  def keypress: DataOn = new DataOn("keypress", None, CaseModifier.Kebab)

  def change: DataOn = new DataOn("change", None, CaseModifier.Kebab)

  def focus: DataOn = new DataOn("focus", None, CaseModifier.Kebab)

  def blur: DataOn = new DataOn("blur", None, CaseModifier.Kebab)

  def mouseover: DataOn = new DataOn("mouseover", None, CaseModifier.Kebab)

  def mouseout: DataOn = new DataOn("mouseout", None, CaseModifier.Kebab)

  def mouseenter: DataOn = new DataOn("mouseenter", None, CaseModifier.Kebab)

  def mouseleave: DataOn = new DataOn("mouseleave", None, CaseModifier.Kebab)

  def scroll: DataOn = new DataOn("scroll", None, CaseModifier.Kebab)

  def resize: DataOn = new DataOn("resize", None, CaseModifier.Kebab)

  def load: DataOn = new DataOn("load", None, CaseModifier.Kebab)

  def apply(name: String): DataOn = {
    require(
      name.nonEmpty && !name.contains("__"),
      s"Invalid Datastar custom event name '$name'. Custom event names must be non-empty and must not contain '__'."
    )
    new DataOn(name, None, CaseModifier.Kebab)
  }
}

/**
 * A `data-on:*` attribute builder with optional event modifiers.
 *
 * Chain modifiers like `debounce`, `throttle`, `once`, `prevent`, `stop`,
 * `capture`, `passive`, `outside`, `window`, `document`, and `viewTransition`
 * before assigning a handler with `:=`. Case modifiers (`camel`, `kebab`,
 * `snake`, `pascal`) control attribute name casing. Repeated timing or target
 * modifiers are normalized to the last effective value before the final
 * attribute name is rendered.
 */
final class DataOn(
  private[datastar] val eventName: String,
  private[datastar] val modifier: Option[EventModifier],
  private[datastar] val caseModifier: CaseModifier
) {

  def debounce(millis: Long): DataOn = withModifier(EventModifier.Debounce(millis, false))

  def debounceLeading(millis: Long): DataOn = withModifier(EventModifier.Debounce(millis, true))

  def throttle(millis: Long): DataOn = withModifier(EventModifier.Throttle(millis, false))

  def throttleLeading(millis: Long): DataOn = withModifier(EventModifier.Throttle(millis, true))

  def delay(millis: Long): DataOn = withModifier(EventModifier.Delay(millis))

  def once: DataOn = withModifier(EventModifier.Once)

  def passive: DataOn = withModifier(EventModifier.Passive)

  def capture: DataOn = withModifier(EventModifier.Capture)

  def stop: DataOn = withModifier(EventModifier.Stop)

  def prevent: DataOn = withModifier(EventModifier.Prevent)

  def outside: DataOn = withModifier(EventModifier.Outside)

  def window: DataOn = withModifier(EventModifier.Window)

  def document: DataOn = withModifier(EventModifier.Document)

  def viewTransition: DataOn = withModifier(EventModifier.ViewTransition)

  def camel: DataOn = new DataOn(eventName, modifier, CaseModifier.Camel)

  def kebab: DataOn = new DataOn(eventName, modifier, CaseModifier.Kebab)

  def snake: DataOn = new DataOn(eventName, modifier, CaseModifier.Snake)

  def pascal: DataOn = new DataOn(eventName, modifier, CaseModifier.Pascal)

  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val caseSuffix  = caseModifier.suffix(CaseModifier.Kebab)
    val attrName    = "data-on:" + toKebabCase(eventName) + modifierStr + caseSuffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: EventModifier): DataOn =
    new DataOn(eventName, EventModifier.normalize(modifier, m), caseModifier)
}
