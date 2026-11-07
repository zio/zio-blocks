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

package zio.blocks.schema.toon

/**
 * Chooses how a codec derived with [[ToonCodecDeriver]] writes a sequence:
 * inline (`tags[3]: a,b,c`), as a table (`items[2]{sku,qty}:` followed by one
 * row per record) or as a list of `- ` items. Set it with
 * [[ToonCodecDeriver.withArrayFormat]]; the default is [[ArrayFormat.Auto]].
 * Every format writes the length in the header. A record field decodes from any
 * of the three forms whatever the setting, but a top-level sequence decodes
 * only from the form its own setting writes, a known bug. A sequence of
 * sequences, such as `List[List[Int]]`, cannot be decoded with the default
 * strict `ReaderConfig`, another known bug; with strict mode off it decodes
 * only when every inner list is long enough (a short one, such as `[1]: 3`,
 * fails), and never with [[ArrayFormat.List]]. The `DynamicValue` codec ignores
 * this setting and picks the form from the elements.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
sealed trait ArrayFormat

/**
 * The cases of `ArrayFormat`: `ArrayFormat.Auto`, `ArrayFormat.Inline`,
 * `ArrayFormat.List` and `ArrayFormat.Tabular`.
 */
object ArrayFormat {

  /**
   * Picks the form from the element type: inline for primitive elements, a list
   * for records and other elements. The default.
   *
   * An array of records with only primitive fields that is nested inside a list
   * item is written in tabular form. Use [[Tabular]] to write record arrays as
   * tables everywhere.
   */
  case object Auto extends ArrayFormat

  /**
   * Writes a sequence of records as a table: a header with the length and the
   * field names, such as `items[2]{sku,qty}:`, then one indented row of values
   * per record. A sequence of other elements is written as a list.
   *
   * Three known bugs: a record with a field that is not primitive, such as
   * `tags: List[String]`, is still written as a row, which the codec then fails
   * to decode; a top-level table cannot be decoded, even when the same codec
   * wrote it; and the header uses the case name mapper, not
   * [[ToonCodecDeriver.withFieldNameMapper]], for the field names.
   */
  case object Tabular extends ArrayFormat

  /**
   * Writes a sequence of primitive values on one line after the header, such as
   * `codes[3]: 3,7,9`, separated by the deriver's delimiter. A sequence of
   * other elements is written as a list.
   */
  case object Inline extends ArrayFormat

  /**
   * Writes every element as a `- ` item on its own line under the header, such
   * as `codes[2]:` followed by `- 3` and `- 7`.
   *
   * A known bug: an array of records with only primitive fields that is nested
   * inside a list item is still written as a table, as with [[Auto]].
   */
  case object List extends ArrayFormat
}
