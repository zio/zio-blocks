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
 * Unlike `zio.blocks.html.AttributeKey`, this key carries the expected value
 * type for the attribute itself. This keeps rich HTMX attributes such as
 * `hx-swap`, `hx-trigger`, and `hx-target` from silently accepting unrelated
 * raw strings while still allowing explicit URL/string surfaces where HTMX
 * itself expects them.
 */
final class HtmxAttrKey[-A] private[htmx] (val name: String, encode: A => Dom.AttributeValue) {

  /**
   * Assigns a value to this HTMX attribute using the attribute's declared value
   * type.
   */
  def :=(value: A): Dom.Attribute =
    Dom.Attribute.KeyValue(name, encode(value))
}

object HtmxAttrKey {
  private[htmx] def stringValue[A](name: String)(implicit toHtmxValue: ToHtmxValue[A]): HtmxAttrKey[A] =
    new HtmxAttrKey[A](name, value => Dom.AttributeValue.StringValue(toHtmxValue.toHtmxValue(value)))
}
