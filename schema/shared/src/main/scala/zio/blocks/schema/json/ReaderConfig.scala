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
 * Settings of the JSON reader: two input limits, the sizes of its reusable
 * buffers, and whether text may follow the value. Start from the defaults in
 * the companion, `ReaderConfig`, and change them with the `with` methods, which
 * throw `IllegalArgumentException` for a value out of range. Pass the result to
 * `JsonCodec#decode` or `Json.parse`. Raise `preferredBufSize` when the input
 * holds numbers or raw values over 32 KiB, or objects whose discriminator field
 * comes late, and `preferredCharBufSize` for strings over 4 KiB, to avoid
 * growing and shrinking the buffers on each call.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * Json.parse("[1] [2]").isLeft                                                  // true
 * Json.parse("[1] [2]", ReaderConfig.withCheckForEndOfInput(false)).map(_.print) // Right([1])
 * }}}
 *
 * @param maxBufSize
 *   for input from an `InputStream` or a direct (not array-backed)
 *   `ByteBuffer`, the largest size (in bytes) of the byte buffer, which must
 *   hold, for example, a whole object while the reader looks for a
 *   discriminator field near its end; beyond it decoding fails with
 *   `too long part of input exceeded 'maxBufSize'`. Default 33554432 (32 MiB).
 *   A limit below 32 KiB is not applied reliably (a known bug).
 * @param maxCharBufSize
 *   the longest string value or key (in chars); beyond it decoding fails with
 *   `too long string exceeded 'maxCharBufSize'`. Default 4194304.
 * @param preferredBufSize
 *   the size (in bytes) the byte buffer has at least when reading from an
 *   `InputStream` or a direct (not array-backed) `ByteBuffer` starts; an
 *   array-backed `ByteBuffer` is read in place. Default 32768.
 * @param preferredCharBufSize
 *   the size (in chars) the char buffer has at least when reading starts.
 *   Default 4096.
 * @param checkForEndOfInput
 *   whether anything but whitespace after the value is an error (`expected end
 *   of input`). Default `true`; with `false` the reader stops after the first
 *   value.
 */
class ReaderConfig private (
  val preferredBufSize: Int,
  val preferredCharBufSize: Int,
  val maxBufSize: Int,
  val maxCharBufSize: Int,
  val checkForEndOfInput: Boolean
) extends Serializable {

  /**
   * Returns a copy with this `maxBufSize`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is below `preferredBufSize` or above 2147483645
   */
  def withMaxBufSize(maxBufSize: Int): ReaderConfig = {
    if (maxBufSize < preferredBufSize)
      throw new IllegalArgumentException("'maxBufSize' should be not less than 'preferredBufSize'")
    if (maxBufSize > 2147483645)
      throw new IllegalArgumentException("'maxBufSize' should be not greater than 2147483645")
    copy(maxBufSize = maxBufSize)
  }

  /**
   * Returns a copy with this `maxCharBufSize`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is below `preferredCharBufSize` or above 2147483645
   */
  def withMaxCharBufSize(maxCharBufSize: Int): ReaderConfig = {
    if (maxCharBufSize < preferredCharBufSize)
      throw new IllegalArgumentException("'maxCharBufSize' should be not less than 'preferredCharBufSize'")
    if (maxCharBufSize > 2147483645)
      throw new IllegalArgumentException("'maxCharBufSize' should be not greater than 2147483645")
    copy(maxCharBufSize = maxCharBufSize)
  }

  /**
   * Returns a copy with this `preferredBufSize`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is below 12 or above `maxBufSize`
   */
  def withPreferredBufSize(preferredBufSize: Int): ReaderConfig = {
    if (preferredBufSize < 12) throw new IllegalArgumentException("'preferredBufSize' should be not less than 12")
    if (preferredBufSize > maxBufSize)
      throw new IllegalArgumentException("'preferredBufSize' should be not greater than 'maxBufSize'")
    copy(preferredBufSize = preferredBufSize)
  }

  /**
   * Returns a copy with this `preferredCharBufSize`.
   *
   * @throws java.lang.IllegalArgumentException
   *   when it is negative or above `maxCharBufSize`
   */
  def withPreferredCharBufSize(preferredCharBufSize: Int): ReaderConfig = {
    if (preferredCharBufSize < 0) throw new IllegalArgumentException("'preferredCharBufSize' should be not less than 0")
    if (preferredCharBufSize > maxCharBufSize)
      throw new IllegalArgumentException("'preferredCharBufSize' should be not greater than 'maxCharBufSize'")
    copy(preferredCharBufSize = preferredCharBufSize)
  }

  /**
   * Returns a copy that does (`true`) or does not (`false`) reject text after
   * the value.
   */
  def withCheckForEndOfInput(checkForEndOfInput: Boolean): ReaderConfig =
    copy(checkForEndOfInput = checkForEndOfInput)

  private[this] def copy(
    preferredBufSize: Int = preferredBufSize,
    preferredCharBufSize: Int = preferredCharBufSize,
    maxBufSize: Int = maxBufSize,
    maxCharBufSize: Int = maxCharBufSize,
    checkForEndOfInput: Boolean = checkForEndOfInput
  ): ReaderConfig =
    new ReaderConfig(
      maxBufSize = maxBufSize,
      maxCharBufSize = maxCharBufSize,
      preferredBufSize = preferredBufSize,
      preferredCharBufSize = preferredCharBufSize,
      checkForEndOfInput = checkForEndOfInput
    )
}

/**
 * The default reader settings, and the starting point for the `with` methods.
 */
object ReaderConfig
    extends ReaderConfig(
      preferredBufSize = 32768,
      preferredCharBufSize = 4096,
      maxBufSize = 33554432,
      maxCharBufSize = 4194304,
      checkForEndOfInput = true
    )
