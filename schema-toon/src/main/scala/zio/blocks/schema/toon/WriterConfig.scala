/*
 * Copyright 2023 ZIO Blocks Maintainers
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
 * Strategy for folding nested keys in TOON output.
 */
sealed abstract class KeyFolding

object KeyFolding {

  /** No key folding - nested records use indentation. */
  case object Off extends KeyFolding

  /** Safe key folding - shallow nesting may use dot-separated keys. */
  case object Safe extends KeyFolding
}

/**
 * Configuration for [[zio.blocks.schema.toon.ToonWriter]] that contains params
 * for formatting of output TOON and for tuning serialization behavior.
 *
 * All configuration params are already initialized to default values, but in
 * some cases they should be altered:
 *   - change the indentation step (default 2 spaces per level)
 *   - change the delimiter for inline arrays (comma, tab, or pipe)
 *   - enable key folding to produce more compact output for shallow structures
 *   - adjust flatten depth to control when nested records use indentation
 *   - set discriminator field for DynamicValue variant encoding
 *
 * @param indent
 *   the number of spaces per indentation level (default: 2)
 * @param delimiter
 *   the delimiter character for inline arrays (default: Comma)
 * @param keyFolding
 *   strategy for folding nested keys (default: Off)
 * @param flattenDepth
 *   maximum depth at which to flatten nested structures (default: Int.MaxValue)
 * @param discriminatorField
 *   optional field name to use as discriminator for DynamicValue variants
 *   (default: None, which uses key-style encoding)
 */
class WriterConfig private (
  val indent: Int,
  val delimiter: Delimiter,
  val keyFolding: KeyFolding,
  val flattenDepth: Int,
  val discriminatorField: Option[String]
) extends Serializable {

  def withIndent(indent: Int): WriterConfig = {
    if (indent < 0) throw new IllegalArgumentException("'indent' should be not less than 0")
    copy(indent = indent)
  }

  def withDelimiter(delimiter: Delimiter): WriterConfig =
    copy(delimiter = delimiter)

  def withKeyFolding(keyFolding: KeyFolding): WriterConfig =
    copy(keyFolding = keyFolding)

  def withFlattenDepth(flattenDepth: Int): WriterConfig = {
    if (flattenDepth < 0) throw new IllegalArgumentException("'flattenDepth' should be not less than 0")
    copy(flattenDepth = flattenDepth)
  }

  def withDiscriminatorField(discriminatorField: Option[String]): WriterConfig =
    copy(discriminatorField = discriminatorField)

  private[this] def copy(
    indent: Int = indent,
    delimiter: Delimiter = delimiter,
    keyFolding: KeyFolding = keyFolding,
    flattenDepth: Int = flattenDepth,
    discriminatorField: Option[String] = discriminatorField
  ): WriterConfig =
    new WriterConfig(
      indent = indent,
      delimiter = delimiter,
      keyFolding = keyFolding,
      flattenDepth = flattenDepth,
      discriminatorField = discriminatorField
    )
}

object WriterConfig
    extends WriterConfig(
      indent = 2,
      delimiter = Delimiter.Comma,
      keyFolding = KeyFolding.Off,
      flattenDepth = Int.MaxValue,
      discriminatorField = None
    )
