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
 * A `data-signals:<name>` attribute that declares one signal, made by
 * `dataSignals(signal)`. The name is written in kebab case, and Datastar turns
 * it into camel case by default; the case methods add a `__case.*` suffix for
 * another case, and `camel`, the default, adds none.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * div(dataSignals(Signal[Int]("pageSize")) := 20).render         // <div data-signals:page-size="20"></div>
 * div(dataSignals(Signal[Int]("pageSize")).snake := 20).render   // <div data-signals:page-size__case.snake="20"></div>
 * }}}
 */
final class DataSignalsBuilder(
  private[datastar] val signalName: String,
  private[datastar] val caseModifier: CaseModifier
) {

  /** Adds `__case.kebab`: the signal's name stays in kebab case. */
  def kebab: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Kebab)

  /** Selects camel case, the default, which adds no suffix. */
  def camel: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Camel)

  /** Adds `__case.snake`: Datastar turns the name into snake case. */
  def snake: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Snake)

  /** Adds `__case.pascal`: Datastar turns the name into Pascal case. */
  def pascal: DataSignalsBuilder = new DataSignalsBuilder(signalName, CaseModifier.Pascal)

  /**
   * Returns `data-signals:<name><case>="<expression>"`, whose expression is the
   * signal's initial value.
   */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val suffix   = caseModifier.suffix(CaseModifier.Camel)
    val attrName = "data-signals:" + toKebabCase(signalName) + suffix
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }
}
