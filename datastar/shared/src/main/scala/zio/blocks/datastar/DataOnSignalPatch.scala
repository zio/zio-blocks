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

final class DataOnSignalPatch(val modifier: Option[OnSignalPatchModifier]) {

  def delay(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Delay(millis))

  def debounce(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Debounce(millis))

  def throttle(millis: Long): DataOnSignalPatch = withModifier(OnSignalPatchModifier.Throttle(millis))

  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-signal-patch" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: OnSignalPatchModifier): DataOnSignalPatch =
    new DataOnSignalPatch(OnSignalPatchModifier.normalize(modifier, m))
}
