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

final class MultiAttributeKey(val name: String, val separator: Dom.AttributeSeparator) {

  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(value))

  def :=(value1: String, value2: String, rest: String*): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), separator))

  def :=(values: Chunk[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(values, separator))

  def +=(value: String): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(name, Dom.AttributeValue.StringValue(value), separator)

  def +=(value1: String, value2: String, rest: String*): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(
      name,
      Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), separator),
      separator
    )

  def +=(values: Chunk[String]): Dom.Attribute.AppendValue =
    Dom.Attribute.AppendValue(name, Dom.AttributeValue.MultiValue(values, separator), separator)

  def apply(values: String*): Dom.Attribute =
    if (values.length == 1) Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(values.head))
    else Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(values), separator))

  def apply(values: Iterable[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(values), separator))
}
