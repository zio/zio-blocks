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
 * A `data-init` attribute, whose expression runs when Datastar initializes the
 * element, for example on page load. Made by `dataInit`. [[delay]] and
 * [[viewTransition]] add suffixes in call order; a second `delay` replaces the
 * first and goes last, and a second `viewTransition` is ignored.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * div(dataInit.delay(500) := js"@get('/bootstrap')").render
 * // <div data-init__delay.500ms="@get(&#x27;/bootstrap&#x27;)"></div>
 * }}}
 */
final class DataInit(private[datastar] val modifier: Maybe[InitModifier]) {

  /**
   * Adds `__delay.<millis>ms`: the expression runs `millis` milliseconds late.
   */
  def delay(millis: Long): DataInit = withModifier(InitModifier.Delay(millis))

  /** Adds `__viewTransition`: the expression runs inside a view transition. */
  def viewTransition: DataInit = withModifier(InitModifier.ViewTransition)

  /** Returns `data-init<modifiers>="<expression>"`. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-init" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: InitModifier): DataInit =
    new DataInit(InitModifier.normalize(modifier, m))
}
