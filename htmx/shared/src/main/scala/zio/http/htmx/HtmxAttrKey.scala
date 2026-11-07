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

package zio.http.htmx

import _root_.zio.blocks.html.Dom

/**
 * A typed key for one htmx attribute, such as `hxSwap` for `hx-swap`.
 *
 * Unlike [[zio.blocks.html.AttributeKey]], the key carries the type of value
 * the attribute takes, so `hxSwap := "innerHTML"` does not compile while
 * `hxSwap := HxSwap.InnerHTML` does; attributes where htmx expects free text or
 * a URL, such as `hxConfirm` and `hxGet`, take a `String`. The keys are the
 * members of [[HtmxAttributes]]; the constructor is private, so code outside
 * this package cannot define new ones.
 *
 * @param name
 *   the attribute name, such as `hx-swap`
 */
final class HtmxAttrKey[-A] private[htmx] (val name: String, encode: A => Dom.AttributeValue) {

  /**
   * Creates this attribute with the given value, as a
   * [[zio.blocks.html.Dom.Attribute Dom.Attribute]]. The value is turned into a
   * string by the [[ToHtmxValue]] instance the key was built with, and the HTML
   * renderer escapes it like any other attribute value.
   */
  def :=(value: A): Dom.Attribute =
    Dom.Attribute.KeyValue(name, encode(value))
}

/** Holds the package-private factory for [[HtmxAttrKey]]. */
object HtmxAttrKey {

  /** Creates a key whose values render with the given [[ToHtmxValue]]. */
  private[htmx] def stringValue[A](name: String)(implicit toHtmxValue: ToHtmxValue[A]): HtmxAttrKey[A] =
    new HtmxAttrKey[A](name, value => Dom.AttributeValue.StringValue(toHtmxValue.toHtmxValue(value)))
}
