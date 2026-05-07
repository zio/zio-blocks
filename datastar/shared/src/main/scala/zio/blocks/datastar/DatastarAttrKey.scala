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

/**
 * Low-level key for constructing Datastar `data-*` attributes directly.
 *
 * Most callers should prefer higher-level `data*` helpers from
 * [[DatastarAttributes]]. Use `DatastarAttrKey` when defining a custom Datastar
 * attribute name or when no dedicated helper exists yet. Values assigned with
 * `:=` are rendered through [[ToDatastarExpr]], so expression contexts reject
 * raw Scala `String` values in favor of explicit Datastar expressions.
 */
final class DatastarAttrKey(val name: String) {

  /** Builds a DOM attribute from this key and a Datastar expression value. */
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(toDatastarExpr.toDatastarExpr(value)))
}

object DatastarAttrKey {

  /** Creates a low-level Datastar attribute key for the provided raw name. */
  private[datastar] def apply(name: String): DatastarAttrKey = new DatastarAttrKey(name)
}
