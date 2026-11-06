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
 * An attribute name whose value is a Datastar expression. The `data*` methods
 * of [[DatastarAttributes]], such as `dataText` and `dataClass("active")`,
 * return one; `new DatastarAttrKey(name)` makes one for an attribute that has
 * no method. `:=` takes any value with a [[ToDatastarExpr]] instance, so a raw
 * Scala `String` does not compile.
 *
 * The name is written into the HTML as given, without checks or escaping, so
 * pass only names from your own code.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * span(new DatastarAttrKey("data-custom") := js"1").render // <span data-custom="1"></span>
 * }}}
 */
final class DatastarAttrKey(val name: String) {

  /**
   * Returns the attribute `name="<expression>"`, with the expression text from
   * [[ToDatastarExpr]], HTML-escaped when the element is rendered.
   */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
}

/** Creates [[DatastarAttrKey]] values inside the module. */
object DatastarAttrKey {

  /** Creates a key for `name`, as given. */
  private[datastar] def apply(name: String): DatastarAttrKey = new DatastarAttrKey(name)
}
