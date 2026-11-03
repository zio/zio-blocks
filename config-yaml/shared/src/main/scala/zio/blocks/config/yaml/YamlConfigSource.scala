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

package zio.blocks.config.yaml

import zio.blocks.config.{ConfigError, ConfigSource}
import zio.blocks.schema.yaml.{Yaml, YamlReader}

/**
 * Reads YAML text into a [[zio.blocks.config.ConfigSource]] keyed by
 * dot-separated paths: nested mappings join their keys with dots, and sequence
 * elements get indexes (`replicas.0`). `ConfigSource.fromYaml` from
 * `zio.blocks.config.yaml._` calls it.
 *
 * {{{
 * import zio.blocks.config._
 *
 * val text =
 *   """server:
 *     |  host: 0.0.0.0
 *     |  port: 8080
 *     |replicas:
 *     |  - db-1
 *     |  - db-2
 *     |""".stripMargin
 * val source = ConfigSource.fromYaml(text, "app.yaml")
 * val keys   = source.map(_.all("").keySet.toList.sorted)
 * // Right(List(replicas.0, replicas.1, server.host, server.port))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/formats/ File Formats]]
 */
object YamlConfigSource {

  /**
   * Parses `yaml` and returns its values as a source. A value that is `null`,
   * `~` or empty is left out, as is a mapping key that is not a scalar.
   *
   * The parser is lenient: it stops at the first line it cannot read and keeps
   * what it read so far, without an error, and a document whose top level is a
   * single scalar gives a source with no keys; both are known bugs. A
   * [[zio.blocks.config.ConfigError.ParseError]] with an empty path, and no
   * cause, is returned only when the parser throws.
   *
   * @param yaml
   *   the YAML content as a string
   * @param sourceId
   *   a unique identifier for this source (default: "yaml:string")
   * @return
   *   Either a ConfigError if parsing fails, or a ConfigSource with flattened
   *   YAML data
   */
  def fromString(yaml: String, sourceId: String = "yaml:string"): Either[ConfigError, ConfigSource] =
    try {
      val parsed  = YamlReader.read(yaml)
      val flatMap = flatten(parsed)
      Right(ConfigSource.MapSource(flatMap, sourceId))
    } catch {
      case _: Throwable =>
        Left(ConfigError.ParseError("", sourceId, "valid YAML"))
    }

  /**
   * Flattens a YAML AST into a Map[String, String] with dot-separated keys.
   *
   *   - Mappings are recursively flattened with dot-separated keys
   *   - Sequences use indexed keys (e.g., "items.0", "items.1")
   *   - Scalars under a key are stored as strings; a top-level scalar is
   *     dropped
   *   - Null values are skipped
   */
  private def flatten(yaml: Yaml): Map[String, String] = {
    val builder = scala.collection.mutable.Map[String, String]()
    flattenRec(yaml, "", builder)
    builder.toMap
  }

  private def flattenRec(yaml: Yaml, prefix: String, builder: scala.collection.mutable.Map[String, String]): Unit =
    yaml match {
      case Yaml.Mapping(entries) =>
        entries.foreach { case (keyYaml, valueYaml) =>
          keyYaml match {
            case Yaml.Scalar(key, _) =>
              val newPrefix = if (prefix.isEmpty) key else s"$prefix.$key"
              flattenRec(valueYaml, newPrefix, builder)
            case _ => // Skip non-scalar keys
          }
        }

      case Yaml.Sequence(elements) =>
        elements.zipWithIndex.foreach { case (elem, idx) =>
          val newPrefix = if (prefix.isEmpty) idx.toString else s"$prefix.$idx"
          flattenRec(elem, newPrefix, builder)
        }

      case Yaml.Scalar(value, _) =>
        if (prefix.nonEmpty) {
          builder(prefix) = value
        }

      case Yaml.NullValue =>
      // Skip null values
    }
}
