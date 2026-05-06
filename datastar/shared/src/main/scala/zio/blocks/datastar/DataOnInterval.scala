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
import zio.blocks.maybe.Maybe

/**
 * Builder for `data-on-interval` Datastar attributes.
 *
 * Duration modifiers describe how often the interval fires, and
 * `durationLeading` encodes Datastar's leading-edge interval variant. Repeated
 * modifiers are normalized to the last effective duration. Use `:=` to assign
 * the expression executed on each interval tick.
 */
final class DataOnInterval(private[datastar] val modifier: Maybe[OnIntervalModifier]) {

  def duration(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, false))

  def durationLeading(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, true))

  def viewTransition: DataOnInterval = withModifier(OnIntervalModifier.ViewTransition)

  /** Assigns the Datastar expression rendered for this interval trigger. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-interval" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: OnIntervalModifier): DataOnInterval =
    new DataOnInterval(OnIntervalModifier.normalize(modifier, m))
}
