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

package zio.blocks.htmx

import zio.blocks.html.Dom

/**
 * Typed key for a single HTMX attribute.
 *
 * Unlike `zio.blocks.html.AttributeKey`, this key delegates rendering to
 * [[ToHtmxValue]], which lets the HTMX DSL accept rich domain values such as
 * [[HxSwap]], [[HxTrigger]], [[zio.http.Path]], or custom user-defined types.
 */
final class HtmxAttrKey(val name: String) {

  /**
   * Assigns a typed value to this HTMX attribute by rendering it through an
   * implicit [[ToHtmxValue]] instance.
   */
  def :=[A](value: A)(implicit toHtmxValue: ToHtmxValue[A]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(toHtmxValue.toHtmxValue(value)))
}

object HtmxAttrKey {
  private[htmx] def apply(name: String): HtmxAttrKey = new HtmxAttrKey(name)
}
