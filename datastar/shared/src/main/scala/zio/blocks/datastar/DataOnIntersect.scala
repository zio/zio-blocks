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
 * Builder for `data-on-intersect` Datastar attributes.
 *
 * Visibility, timing, and transition modifiers are encoded into the final
 * attribute name. Repeated modifiers are normalized to a single effective
 * suffix. Use `once`, `half`, `full`, `exit`, `threshold`, `delay`, `debounce`,
 * `throttle`, and `viewTransition` to model Datastar's supported intersection
 * semantics.
 */
final class DataOnIntersect(private[datastar] val modifier: Maybe[IntersectModifier]) {

  def once: DataOnIntersect = withModifier(IntersectModifier.Once)

  def half: DataOnIntersect = withModifier(IntersectModifier.Half)

  def full: DataOnIntersect = withModifier(IntersectModifier.Full)

  def exit: DataOnIntersect = withModifier(IntersectModifier.Exit)

  def threshold(pct: Double): DataOnIntersect = withModifier(IntersectModifier.Threshold(pct))

  def delay(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Delay(millis))

  def debounce(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Debounce(millis))

  def throttle(millis: Long): DataOnIntersect = withModifier(IntersectModifier.Throttle(millis))

  def viewTransition: DataOnIntersect = withModifier(IntersectModifier.ViewTransition)

  /** Assigns the Datastar expression rendered for this intersect trigger. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-intersect" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: IntersectModifier): DataOnIntersect =
    new DataOnIntersect(IntersectModifier.normalize(modifier, m))
}
