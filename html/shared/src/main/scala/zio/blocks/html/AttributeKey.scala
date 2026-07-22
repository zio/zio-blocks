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

/** Key for single-valued HTML attributes. Use `:=` to set the value. */
final class AttributeKey(val attrName: String) {

  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value))

  def :=(value: Int): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Long): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Double): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Boolean): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.BooleanValue(value))

  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.JsValue(value))

  def :=(values: Chunk[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, Dom.AttributeSeparator.Space))

  def :=(value1: String, value2: String, rest: String*): Dom.Attribute =
    Dom.Attribute.KeyValue(
      attrName,
      Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), Dom.AttributeSeparator.Space)
    )

  def withSeparator(values: Chunk[String], separator: Dom.AttributeSeparator): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, separator))

}
