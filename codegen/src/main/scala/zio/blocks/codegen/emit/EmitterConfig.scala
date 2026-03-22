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

package zio.blocks.codegen.emit

/**
 * Configuration for the Scala code emitter.
 *
 * @param indentWidth
 *   Number of spaces per indentation level (default: 2)
 * @param sortImports
 *   Whether to sort imports alphabetically (default: true)
 * @param scala3Syntax
 *   Whether to use Scala 3 syntax features (default: true)
 * @param trailingCommas
 *   Whether to use trailing commas in multi-line constructs (default: true)
 */
final case class EmitterConfig(
  indentWidth: Int = 2,
  sortImports: Boolean = true,
  scala3Syntax: Boolean = true,
  trailingCommas: Boolean = true
)

object EmitterConfig {
  val default: EmitterConfig = EmitterConfig()
  val scala2: EmitterConfig  = EmitterConfig(scala3Syntax = false, trailingCommas = false)
}
