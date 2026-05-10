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

import zio.http.html.Dom
import zio.blocks.maybe.Maybe

/**
 * Mixin providing Datastar `data-*` attribute constructors for the HTML DSL.
 *
 * Typical usage is through the package object via `import zio.http.datastar._`.
 * The methods in this trait build the Datastar attribute DSL surface, including
 * signal initialization (`dataSignals`), bindings (`dataBind`), event handlers
 * (`dataOn`), DOM updates (`dataText`, `dataShow`, `dataClass`, `dataStyle`,
 * `dataAttr`), and lifecycle hooks (`dataInit`, `dataOnIntersect`,
 * `dataOnInterval`, `dataOnSignalPatch`).
 *
 * Attribute values are rendered as Datastar expressions, not as generic HTML
 * string values. In expression positions use typed values such as [[Signal]],
 * [[SignalUpdate]], [[DatastarRef]], or explicit `js"..."` expressions.
 */
trait DatastarAttributes {

  def dataSignals(signal: Signal[_]): DataSignalsBuilder =
    new DataSignalsBuilder(signal.name, CaseModifier.Camel)

  def dataSignals(update: SignalUpdate[_], updates: SignalUpdate[_]*): Dom.Attribute =
    Dom.Attribute.KeyValue(
      "data-signals",
      Dom.AttributeValue.StringValue(SignalUpdate.objectExpression(update, updates: _*))
    )

  def dataSignals: DatastarAttrKey = DatastarAttrKey("data-signals")

  def dataBind(signal: Signal[_]): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-bind:" + toKebabCase(signal.name), true)

  def dataText: DatastarAttrKey = DatastarAttrKey("data-text")

  def dataShow: DatastarAttrKey = DatastarAttrKey("data-show")

  def dataClass(className: String): DatastarAttrKey = DatastarAttrKey("data-class:" + className)

  def dataClass: DatastarAttrKey = DatastarAttrKey("data-class")

  def dataStyle(styleName: String): DatastarAttrKey = DatastarAttrKey("data-style:" + styleName)

  def dataStyle: DatastarAttrKey = DatastarAttrKey("data-style")

  def dataAttr(attrName: String): DatastarAttrKey = DatastarAttrKey("data-attr:" + attrName)

  def dataAttr: DatastarAttrKey = DatastarAttrKey("data-attr")

  def dataOn: PartialDataOn = new PartialDataOn

  def dataComputed(signal: Signal[_]): DatastarAttrKey = DatastarAttrKey("data-computed:" + toKebabCase(signal.name))

  def dataEffect: DatastarAttrKey = DatastarAttrKey("data-effect")

  def dataIndicator(signal: Signal[_]): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-indicator:" + toKebabCase(signal.name), true)

  def dataRef(name: String): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-ref:" + toKebabCase(name), true)

  def dataInit: DataInit = new DataInit(Maybe.absent)

  def dataIgnore: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore", true)

  def dataIgnoreSelf: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore__self", true)

  def dataIgnoreMorph: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore-morph", true)

  def dataOnIntersect: DataOnIntersect = new DataOnIntersect(Maybe.absent)

  def dataOnInterval: DataOnInterval = new DataOnInterval(Maybe.absent)

  def dataOnSignalPatch: DataOnSignalPatch = new DataOnSignalPatch(Maybe.absent)

  def dataOnSignalPatchFilter: DatastarAttrKey = DatastarAttrKey("data-on-signal-patch-filter")

  def dataJsonSignals: DatastarAttrKey = DatastarAttrKey("data-json-signals")

  def dataPreserveAttr(attrs: String*): Dom.Attribute =
    Dom.Attribute.KeyValue("data-preserve-attr", Dom.AttributeValue.StringValue(attrs.mkString(" ")))
}
