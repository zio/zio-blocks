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

/**
 * Builds `data-signals:<name>` attributes for a named signal key.
 *
 * The base key uses the kebab-cased `signalName`. Case modifier methods append
 * a `__case.*` suffix when a non-default casing is requested. Values assigned
 * with `:=` must be valid Datastar expressions and become the attribute value
 * rendered for that keyed signal.
 */
final class DataSignalsBuilder(
  private[datastar] val signalName: String,
  private[datastar] val caseModifier: CaseModifier
) {

  def kebab: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Kebab)

  def camel: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Camel)

  def snake: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Snake)

  def pascal: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Pascal)

  /**
   * Renders a `data-signals:<name>` attribute using the configured case mode.
   */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val suffix   = caseModifier.suffix(CaseModifier.Camel)
    val attrName = "data-signals:" + toKebabCase(signalName) + suffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }
}
