/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.markdown

/**
 * Configuration for Markdown rendering.
 */
final case class RenderConfig(
  softBreak: String,
  emphasisChar: Char,
  strongChar: Char,
  bulletChar: Char,
  codeBlockChar: Char,
  thematicBreakChar: Char
) {

  def withSoftBreak(value: String): RenderConfig       = copy(softBreak = value)
  def withEmphasisChar(value: Char): RenderConfig      = copy(emphasisChar = value)
  def withStrongChar(value: Char): RenderConfig        = copy(strongChar = value)
  def withBulletChar(value: Char): RenderConfig        = copy(bulletChar = value)
  def withCodeBlockChar(value: Char): RenderConfig     = copy(codeBlockChar = value)
  def withThematicBreakChar(value: Char): RenderConfig = copy(thematicBreakChar = value)
}

object RenderConfig {

  /**
   * Default rendering configuration.
   */
  val default: RenderConfig = RenderConfig(
    softBreak = "\n",
    emphasisChar = '*',
    strongChar = '*',
    bulletChar = '-',
    codeBlockChar = '`',
    thematicBreakChar = '-'
  )
}
