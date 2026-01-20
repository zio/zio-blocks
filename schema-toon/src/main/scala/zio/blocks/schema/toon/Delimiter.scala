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
 * Delimiter used to separate values in TOON inline arrays.
 *
 * @param char
 *   the delimiter character
 */
sealed abstract class Delimiter(val char: Char)

object Delimiter {
  case object Comma extends Delimiter(',')
  case object Tab   extends Delimiter('\t')
  case object Pipe  extends Delimiter('|')

  /**
   * Special delimiter that won't match any character in values - used for
   * reading complete values.
   */
  case object None extends Delimiter('\u0000')
}
