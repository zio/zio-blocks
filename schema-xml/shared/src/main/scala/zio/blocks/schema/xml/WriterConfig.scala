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
 * Configuration for XML writing.
 *
 * @param indentStep
 *   Number of spaces per indentation level. 0 means no indentation.
 * @param includeDeclaration
 *   Whether to include the XML declaration (<?xml version="1.0"?>).
 * @param encoding
 *   Character encoding to specify in the XML declaration.
 */
final case class WriterConfig(
  indentStep: Int = 0,
  includeDeclaration: Boolean = false,
  encoding: String = "UTF-8"
)

object WriterConfig {

  /** Default configuration with no indentation and no XML declaration. */
  val default: WriterConfig = WriterConfig()

  /** Configuration with pretty-printing enabled (2-space indentation). */
  val pretty: WriterConfig = WriterConfig(indentStep = 2)

  /** Configuration with XML declaration enabled. */
  val withDeclaration: WriterConfig = WriterConfig(includeDeclaration = true)
}
