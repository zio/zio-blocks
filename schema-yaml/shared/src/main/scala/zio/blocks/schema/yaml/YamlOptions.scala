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
 * Formatting options for `YamlWriter.write`: indentation, flow style and the
 * `---` document marker. `Yaml#print` uses `YamlOptions.default` and
 * `Yaml#printPretty` uses `YamlOptions.pretty`. `YamlCodec#encodeToString` and
 * `YamlCodec#encode` take no options and always use the default, so to write a
 * value with other options, encode it with `YamlCodec#encodeValue` and pass the
 * tree to `YamlWriter.write`.
 *
 * {{{
 * val endpoint = Yaml.Mapping.fromStringKeys(
 *   "path"    -> Yaml.Scalar("/health"),
 *   "methods" -> Yaml.Sequence(Yaml.Scalar("GET"), Yaml.Scalar("HEAD"))
 * )
 *
 * YamlWriter.write(endpoint, YamlOptions(indentStep = 4, documentMarkers = true))
 * // ---
 * // path: /health
 * // methods:
 * //     - GET
 * //     - HEAD
 *
 * YamlWriter.write(endpoint, YamlOptions.flow) // {path: /health, methods: [GET, HEAD]}
 * }}}
 *
 * @param indentStep
 *   the number of spaces added for each nesting level in block style (default
 *   `2`). The entries of a mapping that is a sequence item line up after its
 *   `- `.
 * @param flowStyle
 *   when `true`, writes every mapping as `{k: v}` and every sequence as
 *   `[a, b]`, on one line (default `false`). A string that contains `,`, `]` or
 *   `}` is written without quotes, so other YAML readers reject the output and
 *   `YamlReader` splits the value (a known bug).
 * @param documentMarkers
 *   when `true`, starts the output with a `---` line (default `false`).
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/yaml/ YAML]]
 */
final case class YamlOptions(
  indentStep: Int = 2,
  flowStyle: Boolean = false,
  documentMarkers: Boolean = false
)

/** Ready-made [[YamlOptions]]. */
object YamlOptions {

  /** Block style, two-space indentation, no `---` line. */
  val default: YamlOptions = YamlOptions()

  /**
   * The default options with `documentMarkers` on, so the output starts with
   * `---`.
   */
  val pretty: YamlOptions = YamlOptions(indentStep = 2, documentMarkers = true)

  /** The default options with `flowStyle` on, so the output is one line. */
  val flow: YamlOptions = YamlOptions(flowStyle = true)
}
