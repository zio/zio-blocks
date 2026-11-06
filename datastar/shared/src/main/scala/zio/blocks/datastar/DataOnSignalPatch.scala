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
 * A `data-on-signal-patch` attribute, whose expression runs when signals are
 * patched. Made by `dataOnSignalPatch`; pair it with `dataOnSignalPatchFilter`
 * to narrow which signals count. Modifiers add suffixes in call order, and a
 * second `delay`, `debounce` or `throttle` replaces the first of its kind and
 * goes last.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * div(dataOnSignalPatch.debounce(200) := js"@get('/recalculate')").render
 * // <div data-on-signal-patch__debounce.200ms="@get(&#x27;/recalculate&#x27;)"></div>
 * }}}
 */
final class DataOnSignalPatch(private[datastar] val modifier: Maybe[OnSignalPatchModifier]) {

  /**
   * Adds `__delay.<millis>ms`: the expression runs `millis` milliseconds late.
   */
  def delay(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Delay(millis))

  /**
   * Adds `__debounce.<millis>ms`: the expression runs once patches have stopped
   * for `millis` milliseconds.
   */
  def debounce(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Debounce(millis))

  /**
   * Adds `__throttle.<millis>ms`: the expression runs at most once every
   * `millis` milliseconds.
   */
  def throttle(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Throttle(millis))

  /** Returns `data-on-signal-patch<modifiers>="<expression>"`. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-signal-patch" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: OnSignalPatchModifier): DataOnSignalPatch =
    new DataOnSignalPatch(OnSignalPatchModifier.normalize(modifier, m))
}
