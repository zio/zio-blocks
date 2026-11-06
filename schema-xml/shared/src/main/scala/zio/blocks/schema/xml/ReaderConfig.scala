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
 * Configuration for XML reader behavior and limits, used by [[XmlReader]] and
 * the decoding methods of [[XmlCodec]]. A limit that is exceeded makes
 * [[XmlReader]] throw an [[XmlCodecError]] and makes `XmlCodec#decode` return a
 * `Left`.
 *
 * {{{
 * val strict = ReaderConfig(maxDepth = 1)
 *
 * scala.util.Try(XmlReader.read("<a><b></b></a>", strict)).failed.map(_.getMessage)
 * // Success(Maximum depth 1 exceeded (line: 1, column: 7))
 * }}}
 *
 * @param maxDepth
 *   Maximum nesting depth for XML elements (prevents stack overflow attacks).
 *   The root element is depth 1. A self-closing element such as `<b/>` does not
 *   count toward the depth.
 * @param maxAttributes
 *   Maximum number of attributes per element
 * @param maxTextLength
 *   Maximum length of one run of text between two pieces of markup, counted
 *   after entity references are replaced. It does not apply to CDATA sections,
 *   comments or attribute values.
 * @param preserveWhitespace
 *   If true, keep text exactly as written; if false, trim leading and trailing
 *   whitespace from each text run and drop runs that are only whitespace.
 *   `XmlCodec#decode` always reads with `true`, whatever this says.
 */
final case class ReaderConfig(
  maxDepth: Int = 1000,
  maxAttributes: Int = 1000,
  maxTextLength: Int = 10000000,
  preserveWhitespace: Boolean = false
)

/** The default reader configuration. */
object ReaderConfig {

  /**
   * Depth and attribute limits of 1000, a text limit of 10,000,000 characters,
   * and whitespace trimming.
   */
  val default: ReaderConfig = ReaderConfig()
}
