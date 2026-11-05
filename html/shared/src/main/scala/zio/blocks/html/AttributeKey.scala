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

package zio.blocks.html

import zio.blocks.chunk.Chunk

/**
 * A key for a single-valued attribute such as `id` or `href`. `:=` builds the
 * attribute; setting the same name again on one element replaces the earlier
 * value when rendered. The DSL defines keys for common attributes, and
 * `attr(name)`, `dataAttr(name)` and `aria(name)` build others.
 *
 * {{{
 * a(href := "/cart", attr("data-count") := 3, aria("label") := "Cart", "Cart").render
 * // <a href="/cart" data-count="3" aria-label="Cart">Cart</a>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#setting-attributes Setting Attributes]]
 */
final class AttributeKey(val attrName: String) {

  /** Sets the attribute to `value`, HTML-escaped when rendered. */
  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value))

  /** Sets the attribute to the decimal form of `value`. */
  def :=(value: Int): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  /** Sets the attribute to the decimal form of `value`. */
  def :=(value: Long): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  /** Sets the attribute to `value.toString`, so `1.0` renders as `1.0`. */
  def :=(value: Double): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  /**
   * Renders the bare attribute name when `value` is `true` and leaves the
   * attribute out when it is `false`.
   */
  def :=(value: Boolean): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.BooleanValue(value))

  /**
   * Sets the attribute to JavaScript code, as for `onclick`; the code is
   * HTML-escaped when rendered.
   */
  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.JsValue(value))

  /**
   * Sets the attribute to `values` joined with spaces. With no values the
   * attribute is left out.
   */
  def :=(values: Chunk[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, Dom.AttributeSeparator.Space))

  /** Sets the attribute to the values joined with spaces. */
  def :=(value1: String, value2: String, rest: String*): Dom.Attribute =
    Dom.Attribute.KeyValue(
      attrName,
      Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), Dom.AttributeSeparator.Space)
    )

  /**
   * Sets the attribute to `values` joined with `separator`. With no values the
   * attribute is left out.
   */
  def withSeparator(values: Chunk[String], separator: Dom.AttributeSeparator): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, separator))

}
