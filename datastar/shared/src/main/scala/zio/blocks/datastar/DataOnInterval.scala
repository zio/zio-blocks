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
 * A `data-on-interval` attribute, whose expression runs on a timer. Made by
 * `dataOnInterval`. Without [[duration]], Datastar uses its default interval of
 * one second. A second `duration` or `durationLeading` replaces the first and
 * goes last.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * div(dataOnInterval.duration(5000) := js"@get('/status')").render
 * // <div data-on-interval__duration.5000ms="@get(&#x27;/status&#x27;)"></div>
 * }}}
 */
final class DataOnInterval(private[datastar] val modifier: Maybe[OnIntervalModifier]) {

  /** Adds `__duration.<millis>ms`, the interval in milliseconds. */
  def duration(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, false))

  /**
   * Adds `__duration.<millis>ms.leading`: the interval in milliseconds, with
   * the first run at once instead of after one interval.
   */
  def durationLeading(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, true))

  /** Adds `__viewTransition`: each run happens inside a view transition. */
  def viewTransition: DataOnInterval = withModifier(OnIntervalModifier.ViewTransition)

  /** Returns `data-on-interval<modifiers>="<expression>"`. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-interval" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: OnIntervalModifier): DataOnInterval =
    new DataOnInterval(OnIntervalModifier.normalize(modifier, m))
}
