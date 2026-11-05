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
 * Configuration for XML writing, used by [[XmlWriter]], `Xml#print` and the
 * encoding methods of [[XmlCodec]].
 *
 * {{{
 * val config = WriterConfig(indentStep = 4, includeDeclaration = true)
 *
 * Xml.Element("status", Xml.Element("code", Xml.Text("200"))).print(config)
 * // <?xml version="1.0" encoding="UTF-8"?>
 * // <status>
 * //     <code>200</code>
 * // </status>
 * }}}
 *
 * @param indentStep
 *   Number of spaces per indentation level. 0 means no indentation and no line
 *   breaks.
 * @param includeDeclaration
 *   Whether to start the output with the XML declaration
 *   `<?xml version="1.0" encoding="..."?>`, which names `encoding`.
 * @param encoding
 *   Character encoding named in the XML declaration, and the charset used to
 *   turn the output into bytes (`XmlWriter.writeToBytes`, `XmlCodec#encode`).
 *   An unsupported name makes the byte methods throw
 *   `java.nio.charset.UnsupportedCharsetException`. The decoding methods read
 *   bytes as UTF-8 whatever this says, so bytes written with another encoding
 *   do not decode back correctly.
 */
final case class WriterConfig(
  indentStep: Int = 0,
  includeDeclaration: Boolean = false,
  encoding: String = "UTF-8"
)

/** Predefined writer configurations. */
object WriterConfig {

  /** Default configuration with no indentation and no XML declaration. */
  val default: WriterConfig = WriterConfig()

  /** Configuration with pretty-printing enabled (2-space indentation). */
  val pretty: WriterConfig = WriterConfig(indentStep = 2)

  /** Configuration with XML declaration enabled. */
  val withDeclaration: WriterConfig = WriterConfig(includeDeclaration = true)
}
