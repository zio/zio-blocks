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
 * A key for an attribute that holds several values, such as `class` or `rel`.
 * `:=` sets the value and `+=` adds to it; the values are joined with
 * `separator` when rendered. A later `:=` replaces an earlier `:=`, but values
 * added with `+=` are kept and placed after it, whatever their order.
 *
 * {{{
 * div(className := "card", className += "sale", rel := ("next", "prefetch")).render
 * // <div class="card sale" rel="next prefetch"></div>
 * div(className += "sale", className := "card").render
 * // <div class="card sale"></div>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#multi-valued-attributes-override-vs-accumulate Multi-Valued Attributes]]
 */
final class MultiAttributeKey(val name: String, val separator: Dom.AttributeSeparator) {

  /**
   * Sets the attribute to `value`, replacing earlier `:=` values when rendered.
   */
  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(value))

  /**
   * Sets the attribute to the values, joined with [[separator]] when rendered.
   */
  def :=(value1: String, value2: String, rest: String*): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), separator))

  /**
   * Sets the attribute to `values`, joined with [[separator]] when rendered.
   * With no values and no `+=` values the attribute is left out.
   */
  def :=(values: Chunk[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(values, separator))

  /**
   * Adds `value` to the attribute, after the `:=` value and earlier `+=`
   * values.
   */
  def +=(value: String): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(name, Dom.AttributeValue.StringValue(value), separator)

  /**
   * Adds the values to the attribute, after the `:=` value and earlier `+=`
   * values.
   */
  def +=(value1: String, value2: String, rest: String*): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(
      name,
      Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), separator),
      separator
    )

  /**
   * Adds `values` to the attribute, after the `:=` value and earlier `+=`
   * values.
   */
  def +=(values: Chunk[String]): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(name, Dom.AttributeValue.MultiValue(values, separator), separator)

  /** Sets the attribute to the values, as `:=` does. */
  def apply(value: String, rest: String*): Dom.Attribute =
    if (rest.isEmpty) Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(value))
    else Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(value +: rest), separator))

  /** Sets the attribute to `values`, in iteration order, as `:=` does. */
  def apply(values: Iterable[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(values), separator))
}
