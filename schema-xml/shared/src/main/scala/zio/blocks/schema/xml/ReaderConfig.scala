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

package zio.blocks.schema.xml

/**
 * Configuration for XML reader behavior and limits.
 *
 * @param maxDepth
 *   Maximum nesting depth for XML elements (prevents stack overflow attacks)
 * @param maxAttributes
 *   Maximum number of attributes per element
 * @param maxTextLength
 *   Maximum length of text content
 * @param preserveWhitespace
 *   If true, preserve all whitespace in text nodes; if false, trim
 *   leading/trailing whitespace
 */
final case class ReaderConfig(
  maxDepth: Int = 1000,
  maxAttributes: Int = 1000,
  maxTextLength: Int = 10000000,
  preserveWhitespace: Boolean = false
)

object ReaderConfig {
  val default: ReaderConfig = ReaderConfig()
}
