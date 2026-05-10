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
 * Builder for the `data-init` Datastar attribute.
 *
 * Modifiers such as [[delay]] and [[viewTransition]] are encoded into the final
 * attribute name and normalized so repeated modifiers collapse to the last
 * effective value. Use `:=` to assign the Datastar expression that should run
 * during initialization.
 */
final class DataInit(private[datastar] val modifier: Maybe[InitModifier]) {

  def delay(millis: Long): DataInit = withModifier(InitModifier.Delay(millis))

  def viewTransition: DataInit = withModifier(InitModifier.ViewTransition)

  /** Assigns the Datastar expression rendered for this `data-init` builder. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute = {
    val modifierStr = modifier.fold("")(_.render)
    val attrName    = "data-init" + modifierStr
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
  }

  private def withModifier(m: InitModifier): DataInit =
    new DataInit(InitModifier.normalize(modifier, m))
}
