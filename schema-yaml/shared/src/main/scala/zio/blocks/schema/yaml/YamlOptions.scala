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

package zio.blocks.schema.yaml

/**
 * Configuration for [[YamlWriter]] output formatting.
 *
 * @param indentStep
 *   number of spaces per indentation level (default: 2)
 * @param flowStyle
 *   when true, emit collections in inline flow style
 * @param documentMarkers
 *   when true, prepend `---` document start marker
 */
final case class YamlOptions(
  indentStep: Int = 2,
  flowStyle: Boolean = false,
  documentMarkers: Boolean = false
)

object YamlOptions {
  val default: YamlOptions = YamlOptions()
  val pretty: YamlOptions  = YamlOptions(indentStep = 2, documentMarkers = true)
  val flow: YamlOptions    = YamlOptions(flowStyle = true)
}
