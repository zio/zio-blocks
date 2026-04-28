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

final class DataSignalsBuilder(val signalName: String, val caseModifier: CaseModifier) {

  def kebab: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Kebab)

  def camel: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Camel)

  def snake: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Snake)

  def pascal: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Pascal)

  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val suffix   = caseModifier.suffix(CaseModifier.Camel)
    val attrName = "data-signals:" + toKebabCase(signalName) + suffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }
}
