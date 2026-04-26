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

final class DataOnInterval(val modifier: Option[OnIntervalModifier]) {

  def duration(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, false))

  def durationLeading(millis: Long): DataOnInterval = withModifier(OnIntervalModifier.Duration(millis, true))

  def viewTransition: DataOnInterval = withModifier(OnIntervalModifier.ViewTransition)

  def :=[T](value: T)(implicit toJs: ToJs[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-on-interval" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toJs.toJs(value)))
  }

  private def withModifier(m: OnIntervalModifier): DataOnInterval = {
    val combined = modifier match {
      case None       => m
      case Some(prev) => OnIntervalModifier.And(prev, m)
    }
    new DataOnInterval(Some(combined))
  }
}
