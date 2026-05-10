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
 * Builder for `data-on-signal-patch` Datastar attributes.
 *
 * Delay, debounce, and throttle modifiers are encoded into the final attribute
 * name. Repeated timing modifiers are normalized to the last effective value.
 * Use `:=` to attach the Datastar expression that should run when a signal
 * patch arrives.
 */
final class DataOnSignalPatch(private[datastar] val modifier: Maybe[OnSignalPatchModifier]) {

  def delay(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Delay(millis))

  def debounce(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Debounce(millis))

  def throttle(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Throttle(millis))

  /** Assigns the Datastar expression rendered for this signal-patch trigger. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-signal-patch" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: OnSignalPatchModifier): DataOnSignalPatch =
    new DataOnSignalPatch(OnSignalPatchModifier.normalize(modifier, m))
}
