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

import zio.blocks.html.{Dom, ToJs}

final class PartialDataOn {

  def click: DataOn = new DataOn("click", None, CaseModifier.Camel)

  def submit: DataOn = new DataOn("submit", None, CaseModifier.Camel)

  def input: DataOn = new DataOn("input", None, CaseModifier.Camel)

  def keydown: DataOn = new DataOn("keydown", None, CaseModifier.Camel)

  def keyup: DataOn = new DataOn("keyup", None, CaseModifier.Camel)

  def keypress: DataOn = new DataOn("keypress", None, CaseModifier.Camel)

  def change: DataOn = new DataOn("change", None, CaseModifier.Camel)

  def focus: DataOn = new DataOn("focus", None, CaseModifier.Camel)

  def blur: DataOn = new DataOn("blur", None, CaseModifier.Camel)

  def mouseover: DataOn = new DataOn("mouseover", None, CaseModifier.Camel)

  def mouseout: DataOn = new DataOn("mouseout", None, CaseModifier.Camel)

  def mouseenter: DataOn = new DataOn("mouseenter", None, CaseModifier.Camel)

  def mouseleave: DataOn = new DataOn("mouseleave", None, CaseModifier.Camel)

  def scroll: DataOn = new DataOn("scroll", None, CaseModifier.Camel)

  def resize: DataOn = new DataOn("resize", None, CaseModifier.Camel)

  def load: DataOn = new DataOn("load", None, CaseModifier.Camel)

  def apply(name: String): DataOn = new DataOn(name, None, CaseModifier.Camel)
}

final class DataOn(val eventName: String, val modifier: Option[EventModifier], val caseModifier: CaseModifier) {

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

  def :=[T](value: T)(implicit toJs: ToJs[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val caseSuffix  = caseModifier.suffix(CaseModifier.Camel)
    val attrName    = "data-on:" + eventName + modifierStr + caseSuffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toJs.toJs(value)))
  }

  private def withModifier(m: EventModifier): DataOn = {
    val combined = modifier match {
      case None       => m
      case Some(prev) => EventModifier.And(prev, m)
    }
    new DataOn(eventName, Some(combined), caseModifier)
  }
}
