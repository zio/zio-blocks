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
 * The Datastar `data-*` attributes for the `zio.blocks.html` element DSL. The
 * `zio.http.datastar` package object extends this trait, so
 * `import zio.http.datastar._` brings every method into scope: signal
 * declarations (`dataSignals`, `dataComputed`), bindings (`dataBind`), event
 * handlers (`dataOn`), DOM updates (`dataText`, `dataShow`, `dataClass`,
 * `dataStyle`, `dataAttr`), triggers (`dataInit`, `dataOnIntersect`,
 * `dataOnInterval`, `dataOnSignalPatch`) and morph control (`dataIgnore`,
 * `dataPreserveAttr`).
 *
 * An attribute's value is a Datastar expression: a [[Signal]], a
 * [[SignalUpdate]], a [[DatastarRef]], a `js"..."` expression, or another value
 * with a [[ToDatastarExpr]] instance. A raw `String` does not compile.
 * `zio.blocks.html` also has a `dataAttr`, so with both packages imported write
 * `zio.http.datastar.dataAttr`.
 *
 * {{{
 * import zio.blocks.html._
 *
 * val count = Signal[Int]("count")
 *
 * div(dataSignals(count := 0), button(dataOn.click := js"@post('/increment')", "+"), span(dataText := count)).render
 * // <div data-signals="{&quot;count&quot;: 0}"><button data-on:click="@post(&#x27;/increment&#x27;)">+</button><span data-text="\$count"></span></div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/attributes/ Datastar Attributes]]
 */
trait DatastarAttributes {

  /**
   * Starts a `data-signals:<name>` attribute that declares one signal, whose
   * initial value is the expression assigned with `:=`. The signal's name is
   * written in kebab case (`userName` becomes `user-name`), and Datastar turns
   * it back into camel case by default; see [[DataSignalsBuilder]].
   *
   * {{{
   * import zio.blocks.html._
   *
   * div(dataSignals(Signal[Int]("count")) := 0).render // <div data-signals:count="0"></div>
   * }}}
   */
  def dataSignals(signal: Signal[_]): DataSignalsBuilder =
    new DataSignalsBuilder(signal.name, CaseModifier.Camel)

  /**
   * Returns a `data-signals` attribute that declares the given signals with
   * their values, as one object in argument order.
   *
   * {{{
   * import zio.blocks.html._
   *
   * val count = Signal[Int]("count")
   * val name  = Signal[String]("name")
   *
   * div(dataSignals(count := 0, name := "Ada")).render
   * // <div data-signals="{&quot;count&quot;: 0, &quot;name&quot;: &quot;Ada&quot;}"></div>
   * }}}
   */
  def dataSignals(update: SignalUpdate[_], updates: SignalUpdate[_]*): Dom.Attribute =
    Dom.Attribute.KeyValue(
      "data-signals",
      Dom.AttributeValue.StringValue(SignalUpdate.objectExpression(update, updates: _*))
    )

  /**
   * Returns the `data-signals` key, for assigning an object expression such as
   * `js"{count: 0}"`.
   */
  def dataSignals: DatastarAttrKey = DatastarAttrKey("data-signals")

  /**
   * Returns `data-bind:<name>`, which binds a form element's value to the
   * signal both ways. The name is written in kebab case.
   *
   * {{{
   * import zio.blocks.html._
   *
   * input(dataBind(Signal[String]("userName"))).render // <input data-bind:user-name/>
   * }}}
   */
  def dataBind(signal: Signal[_]): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-bind:" + toKebabCase(signal.name), true)

  /**
   * Returns the `data-text` key, which sets the element's text to the value of
   * the assigned expression.
   *
   * {{{
   * import zio.blocks.html._
   *
   * val count = Signal[Int]("count")
   *
   * span(dataText := count).render // <span data-text="\$count"></span>
   * }}}
   */
  def dataText: DatastarAttrKey = DatastarAttrKey("data-text")

  /**
   * Returns the `data-show` key, which shows the element while the assigned
   * expression is true and hides it otherwise.
   */
  def dataShow: DatastarAttrKey = DatastarAttrKey("data-show")

  /**
   * Returns the `data-class:<className>` key, which adds the class while the
   * assigned expression is true. The class name is written as given, without
   * escaping, so pass only names from your own code.
   *
   * {{{
   * import zio.blocks.html._
   *
   * li(dataClass("done") := Signal[Boolean]("finished")).render // <li data-class:done="\$finished"></li>
   * }}}
   */
  def dataClass(className: String): DatastarAttrKey = DatastarAttrKey("data-class:" + className)

  /**
   * Returns the `data-class` key, for an object that maps class names to
   * conditions.
   */
  def dataClass: DatastarAttrKey = DatastarAttrKey("data-class")

  /**
   * Returns the `data-style:<styleName>` key, which sets one CSS property to
   * the assigned expression. The property name is written as given, without
   * escaping, so pass only names from your own code.
   */
  def dataStyle(styleName: String): DatastarAttrKey = DatastarAttrKey("data-style:" + styleName)

  /**
   * Returns the `data-style` key, for an object that maps CSS properties to
   * values.
   */
  def dataStyle: DatastarAttrKey = DatastarAttrKey("data-style")

  /**
   * Returns the `data-attr:<attrName>` key, which sets one HTML attribute to
   * the assigned expression. The attribute name is written as given, without
   * escaping, so pass only names from your own code. `zio.blocks.html` has a
   * method of the same name, so with both packages imported write
   * `zio.http.datastar.dataAttr`.
   */
  def dataAttr(attrName: String): DatastarAttrKey = DatastarAttrKey("data-attr:" + attrName)

  /**
   * Returns the `data-attr` key, for an object that maps HTML attribute names
   * to values.
   */
  def dataAttr: DatastarAttrKey = DatastarAttrKey("data-attr")

  /**
   * Starts a `data-on:<event>` handler; pick the event on the returned
   * [[PartialDataOn]], add modifiers, and assign the expression with `:=`.
   *
   * {{{
   * import zio.blocks.html._
   *
   * button(dataOn.click.once := js"@post('/subscribe')").render
   * // <button data-on:click__once="@post(&#x27;/subscribe&#x27;)"></button>
   * }}}
   */
  def dataOn: PartialDataOn = new PartialDataOn

  /**
   * Returns the `data-computed:<name>` key, which declares a read-only signal
   * whose value is the assigned expression. The name is written in kebab case.
   */
  def dataComputed(signal: Signal[_]): DatastarAttrKey = DatastarAttrKey("data-computed:" + toKebabCase(signal.name))

  /**
   * Returns the `data-effect` key, whose expression Datastar runs when the
   * element loads and again whenever a signal it reads changes.
   */
  def dataEffect: DatastarAttrKey = DatastarAttrKey("data-effect")

  /**
   * Returns `data-indicator:<name>`, which makes the signal `true` while a
   * request started from the element is in flight. The name is written in kebab
   * case.
   */
  def dataIndicator(signal: Signal[_]): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-indicator:" + toKebabCase(signal.name), true)

  /**
   * Returns `data-ref:<name>`, which stores the element in a signal of that
   * name. The name is written in kebab case and otherwise as given, without
   * checks or escaping, so pass only names from your own code.
   */
  def dataRef(name: String): Dom.Attribute =
    Dom.Attribute.BooleanAttribute("data-ref:" + toKebabCase(name), true)

  /**
   * Starts a `data-init` attribute, whose expression runs when Datastar first
   * processes the element; see [[DataInit]].
   */
  def dataInit: DataInit = new DataInit(Maybe.absent)

  /**
   * Returns `data-ignore`, which makes Datastar skip the element and its
   * children.
   */
  def dataIgnore: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore", true)

  /**
   * Returns `data-ignore__self`, which makes Datastar skip the element but
   * still process its children.
   */
  def dataIgnoreSelf: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore__self", true)

  /**
   * Returns `data-ignore-morph`, which keeps an element patch from morphing the
   * element and its children.
   */
  def dataIgnoreMorph: Dom.Attribute = Dom.Attribute.BooleanAttribute("data-ignore-morph", true)

  /**
   * Starts a `data-on-intersect` attribute, whose expression runs when the
   * element enters the viewport; see [[DataOnIntersect]].
   */
  def dataOnIntersect: DataOnIntersect = new DataOnIntersect(Maybe.absent)

  /**
   * Starts a `data-on-interval` attribute, whose expression runs on a timer;
   * see [[DataOnInterval]].
   */
  def dataOnInterval: DataOnInterval = new DataOnInterval(Maybe.absent)

  /**
   * Starts a `data-on-signal-patch` attribute, whose expression runs when
   * signals are patched; see [[DataOnSignalPatch]].
   */
  def dataOnSignalPatch: DataOnSignalPatch = new DataOnSignalPatch(Maybe.absent)

  /**
   * Returns the `data-on-signal-patch-filter` key, for an object with `include`
   * or `exclude` patterns that narrow which signal patches trigger
   * `data-on-signal-patch`.
   */
  def dataOnSignalPatchFilter: DatastarAttrKey = DatastarAttrKey("data-on-signal-patch-filter")

  /**
   * Returns the `data-json-signals` key, which shows the page's signals as JSON
   * in the element; assign an object with `include` or `exclude` patterns to
   * narrow them.
   */
  def dataJsonSignals: DatastarAttrKey = DatastarAttrKey("data-json-signals")

  /**
   * Returns `data-preserve-attr` with the given attribute names separated by
   * spaces, so an element patch keeps those attributes' current values. With no
   * names the value is empty.
   *
   * {{{
   * import zio.blocks.html._
   *
   * details(dataPreserveAttr("open", "class")).render // <details data-preserve-attr="open class"></details>
   * }}}
   */
  def dataPreserveAttr(attrs: String*): Dom.Attribute =
    Dom.Attribute.KeyValue("data-preserve-attr", Dom.AttributeValue.StringValue(attrs.mkString(" ")))
}
