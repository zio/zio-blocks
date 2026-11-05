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
 * The character that separates the values of an inline array, the values of a
 * table row and the field names of a table header. A derived codec takes it
 * from [[ToonCodecDeriver.withDelimiter]], the `DynamicValue` codec from
 * [[WriterConfig.withDelimiter]]. A delimiter other than the comma is written
 * into the array header, as in `codes[2|]: 3|7`, and the reader takes each
 * array's delimiter from its header.
 *
 * @param char
 *   the delimiter character
 */
sealed abstract class Delimiter(val char: Char)

/**
 * The delimiters of TOON arrays: `Delimiter.Comma`, `Delimiter.Tab`,
 * `Delimiter.Pipe` and `Delimiter.None`.
 */
object Delimiter {

  /** Separates values with `,`, the default. */
  case object Comma extends Delimiter(',')

  /** Separates values with a tab character. */
  case object Tab extends Delimiter('\t')

  /** Separates values with `|`. */
  case object Pipe extends Delimiter('|')

  /**
   * The NUL character (code point 0), which the reader uses internally to read
   * a value whole, without splitting it. Use [[Comma]], [[Tab]] or [[Pipe]] for
   * writing.
   */
  case object None extends Delimiter('\u0000')
}
