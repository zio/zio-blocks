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

package zio.blocks.schema.json

/**
 * Settings of the JSON writer: indentation, escaping of non-ASCII characters,
 * and the size of its reusable buffer. Start from the defaults in the
 * companion, `WriterConfig`, and change them with the `with` methods, which
 * throw `IllegalArgumentException` for a value out of range. Pass the result to
 * `JsonCodec#encode`, `encodeToString` or `Json#print`. Raise
 * `preferredBufSize` when the output is often over 32 KiB, to avoid growing and
 * shrinking the buffer on each call.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * Json.Object("title" -> Json.String("Zionomicon")).print(WriterConfig.withIndentionStep(2))
 * // {
 * //   "title": "Zionomicon"
 * // }
 * }}}
 *
 * @param indentionStep
 *   the spaces per level of indentation, or 0 (the default) for compact JSON.
 *   Indented output puts an empty array or object on three lines, the middle
 *   one of spaces (a known bug).
 * @param escapeUnicode
 *   whether every non-ASCII character is written as a Unicode escape, for
 *   ASCII-only output. Default `false`.
 * @param preferredBufSize
 *   the starting size (in bytes) of the writer's buffer, used for every output
 *   except a heap `ByteBuffer`. Default 32768.
 */
class WriterConfig private (
  val indentionStep: Int,
  val preferredBufSize: Int,
  val escapeUnicode: Boolean
) extends Serializable {

  /**
   * Returns a copy with this `indentionStep`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is negative
   */
  def withIndentionStep(indentionStep: Int): WriterConfig = {
    if (indentionStep < 0) throw new IllegalArgumentException("'indentionStep' should be not less than 0")
    copy(indentionStep = indentionStep)
  }

  /**
   * Returns a copy that does (`true`) or does not (`false`) escape non-ASCII
   * characters.
   */
  def withEscapeUnicode(escapeUnicode: Boolean): WriterConfig =
    copy(escapeUnicode = escapeUnicode)

  /**
   * Returns a copy with this `preferredBufSize`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is not positive
   */
  def withPreferredBufSize(preferredBufSize: Int): WriterConfig = {
    if (preferredBufSize <= 0) throw new IllegalArgumentException("'preferredBufSize' should be not less than 1")
    copy(preferredBufSize = preferredBufSize)
  }

  private[this] def copy(
    indentionStep: Int = indentionStep,
    preferredBufSize: Int = preferredBufSize,
    escapeUnicode: Boolean = escapeUnicode
  ): WriterConfig =
    new WriterConfig(
      indentionStep = indentionStep,
      preferredBufSize = preferredBufSize,
      escapeUnicode = escapeUnicode
    )
}

/**
 * The default writer settings, and the starting point for the `with` methods.
 */
object WriterConfig
    extends WriterConfig(
      indentionStep = 0,
      preferredBufSize = 32768,
      escapeUnicode = false
    ) {

  /**
   * The defaults with an indentation of two spaces, as `Json#toString` prints.
   */
  val withIndentionStep2: WriterConfig = WriterConfig.withIndentionStep(2)
}
