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
 * A `data-on-intersect` attribute, whose expression runs when the element
 * enters the viewport. Made by `dataOnIntersect`. Modifiers add suffixes in
 * call order. `half`, `full` and `threshold` replace one another, a second
 * `delay`, `debounce` or `throttle` replaces the first of its kind, and the
 * replacing modifier goes last; a repeated flag such as `once` is kept once.
 *
 * {{{
 * import zio.blocks.html._
 *
 * div(dataOnIntersect.once.full := js"@get('/page/2')").render
 * // <div data-on-intersect__once__full="@get(&#x27;/page/2&#x27;)"></div>
 * }}}
 */
final class DataOnIntersect(private[datastar] val modifier: Maybe[IntersectModifier]) {

  /** Adds `__once`: the expression runs only the first time. */
  def once: DataOnIntersect = withModifier(IntersectModifier.Once)

  /** Adds `__half`: the expression runs when half of the element is visible. */
  def half: DataOnIntersect = withModifier(IntersectModifier.Half)

  /** Adds `__full`: the expression runs when all of the element is visible. */
  def full: DataOnIntersect = withModifier(IntersectModifier.Full)

  /**
   * Adds `__exit`: the expression runs when the element leaves the viewport.
   */
  def exit: DataOnIntersect = withModifier(IntersectModifier.Exit)

  /**
   * Adds `__threshold.<pct>`: the expression runs when `pct` percent of the
   * element is visible. Datastar reads the value as a percentage from 0 to 100,
   * so pass a whole number such as `25`; it is written without trailing zeros
   * (`25.0` gives `__threshold.25`). `:=` throws `NumberFormatException` for
   * `NaN` or an infinite value.
   */
  def threshold(pct: Double): DataOnIntersect = withModifier(IntersectModifier.Threshold(pct))

  /**
   * Adds `__delay.<millis>ms`: the expression runs `millis` milliseconds late.
   */
  def delay(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Delay(millis))

  /** Adds `__debounce.<millis>ms` to the trigger. */
  def debounce(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Debounce(millis))

  /** Adds `__throttle.<millis>ms` to the trigger. */
  def throttle(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Throttle(millis))

  /** Adds `__viewTransition`: the expression runs inside a view transition. */
  def viewTransition: DataOnIntersect = withModifier(IntersectModifier.ViewTransition)

  /** Returns `data-on-intersect<modifiers>="<expression>"`. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-intersect" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: IntersectModifier): DataOnIntersect =
    new DataOnIntersect(IntersectModifier.normalize(modifier, m))
}
