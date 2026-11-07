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

package zio.blocks.config

/**
 * Reads JSON configuration: `import zio.blocks.config.json._` adds
 * `ConfigSource.fromJson`.
 */
package object json {

  /** The `ConfigSource.fromJson` constructor. */
  implicit final class ConfigSourceJsonSyntax(private val companion: ConfigSource.type) extends AnyVal {

    /**
     * Parses the JSON text `json` and returns its values as a [[ConfigSource]]
     * keyed by dot-separated paths: object keys join with dots, array elements
     * get indexes (`replicas.0`), and `null` values, empty arrays and empty
     * objects are left out. A number keeps the text of its `BigDecimal` value,
     * so `1e3` becomes `1E+3`. `sourceId` names the source in provenance and
     * errors.
     *
     * Text that is not valid JSON gives a [[ConfigError.ParseError]] with an
     * empty path and no cause, so the error does not say where the text is
     * wrong.
     *
     * {{{
     * import zio.blocks.config._
     * import zio.blocks.config.json._
     *
     * val source = ConfigSource.fromJson(
     *   """{"server": {"host": "0.0.0.0", "port": 8080}, "replicas": ["db-1", "db-2"]}""",
     *   "app.json"
     * )
     * val keys = source.map(_.all("").keySet.toList.sorted)
     * // Right(List(replicas.0, replicas.1, server.host, server.port))
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/config/formats/ File Formats]]
     */
    def fromJson(json: String, sourceId: String = "json:string"): Either[ConfigError, ConfigSource] =
      JsonConfigSource.fromString(json, sourceId)
  }
}
