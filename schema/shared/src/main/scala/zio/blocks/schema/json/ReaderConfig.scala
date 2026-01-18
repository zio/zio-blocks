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

package zio.blocks.schema.json

/**
 * Configuration for [[zio.blocks.schema.json.JsonReader]] that contains flags
 * for tuning of preferred sizes for internal buffers that are created on the
 * reader instantiation and reused in runtime for parsing of messages. <br/> All
 * configuration params already initialized by recommended default values, but
 * in some cases they should be altered for performance reasons: <ul>
 * <li>increase preferred size of an internal byte buffer for parsing from
 * [[java.io.InputStream]] or [[java.nio.ByteBuffer]] to reduce allocation rate
 * of grown and then reduced buffers during parsing of large (>32Kb) numbers
 * (including stringified), raw values, or ADT instances with the discriminator
 * field doesn't appear at the beginning of the JSON object</li> <li>increase
 * the preferred size of an internal char buffer to reduce the allocation rate
 * of grown and then reduced buffers when large (>4Kb) string instances</li>
 * </ul>
 * @param maxBufSize
 *   a max size (in bytes) of an internal byte buffer when parsing from
 *   [[java.io.InputStream]] or [[java.nio.ByteBuffer]]
 * @param maxCharBufSize
 *   a max size (in chars) of an internal char buffer for parsing of string
 *   values
 * @param preferredBufSize
 *   a preferred size (in bytes) of an internal byte buffer when parsing from
 *   [[java.io.InputStream]] or [[java.nio.ByteBuffer]]
 * @param preferredCharBufSize
 *   a preferred size (in chars) of an internal char buffer for parsing of
 *   string values
 * @param checkForEndOfInput
 *   a flag to check and raise an error if some non-whitespace bytes are
 *   detected after successful parsing of the value
 */
class ReaderConfig private (
  val preferredBufSize: Int,
  val preferredCharBufSize: Int,
  val maxBufSize: Int,
  val maxCharBufSize: Int,
  val checkForEndOfInput: Boolean
) extends Serializable {
  def withMaxBufSize(maxBufSize: Int): ReaderConfig = {
    if (maxBufSize < preferredBufSize)
      throw new IllegalArgumentException("'maxBufSize' should be not less than 'preferredBufSize'")
    if (maxBufSize > 2147483645)
      throw new IllegalArgumentException("'maxBufSize' should be not greater than 2147483645")
    copy(maxBufSize = maxBufSize)
  }

  def withMaxCharBufSize(maxCharBufSize: Int): ReaderConfig = {
    if (maxCharBufSize < preferredCharBufSize)
      throw new IllegalArgumentException("'maxCharBufSize' should be not less than 'preferredCharBufSize'")
    if (maxCharBufSize > 2147483645)
      throw new IllegalArgumentException("'maxCharBufSize' should be not greater than 2147483645")
    copy(maxCharBufSize = maxCharBufSize)
  }

  def withPreferredBufSize(preferredBufSize: Int): ReaderConfig = {
    if (preferredBufSize < 12) throw new IllegalArgumentException("'preferredBufSize' should be not less than 12")
    if (preferredBufSize > maxBufSize)
      throw new IllegalArgumentException("'preferredBufSize' should be not greater than 'maxBufSize'")
    copy(preferredBufSize = preferredBufSize)
  }

  def withPreferredCharBufSize(preferredCharBufSize: Int): ReaderConfig = {
    if (preferredCharBufSize < 0) throw new IllegalArgumentException("'preferredCharBufSize' should be not less than 0")
    if (preferredCharBufSize > maxCharBufSize)
      throw new IllegalArgumentException("'preferredCharBufSize' should be not greater than 'maxCharBufSize'")
    copy(preferredCharBufSize = preferredCharBufSize)
  }

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

object ReaderConfig
    extends ReaderConfig(
      preferredBufSize = 32768,
      preferredCharBufSize = 4096,
      maxBufSize = 33554432,
      maxCharBufSize = 4194304,
      checkForEndOfInput = true
    )
