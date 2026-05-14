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

package zio.blocks.config.json

import zio.blocks.config.{ConfigError, ConfigSource}
import zio.blocks.schema.json.Json

object JsonConfigSource {

  /**
   * Creates a ConfigSource from a JSON string.
   *
   * Parses the JSON string and flattens it into a map with dot-separated keys.
   * Objects are recursively flattened with keys joined by dots. Arrays are
   * indexed with numeric keys (e.g., "array.0", "array.1").
   *
   * @param json
   *   The JSON string to parse
   * @param sourceId
   *   A unique identifier for this source (default: "json:string")
   * @return
   *   Either a ConfigError if parsing fails, or a ConfigSource
   */
  def fromString(json: String, sourceId: String = "json:string"): Either[ConfigError, ConfigSource] =
    Json.parse(json) match {
      case Right(jsonValue) =>
        val flatMap = flatten(jsonValue, "")
        Right(ConfigSource.fromMap(flatMap, sourceId))
      case Left(schemaError) =>
        Left(ConfigError.InvalidValue("", json, "valid JSON", sourceId, Some(schemaError)))
    }

  /**
   * Flattens a JSON value into a map with dot-separated keys.
   *
   * @param json
   *   The JSON value to flatten
   * @param prefix
   *   The current key prefix (empty string for root)
   * @return
   *   A map of flattened key-value pairs
   */
  private def flatten(json: Json, prefix: String): Map[String, String] =
    json match {
      case obj: Json.Object =>
        obj.fields.foldLeft(Map.empty[String, String]) { case (acc, (key, value)) =>
          val newKey = if (prefix.isEmpty) key else s"$prefix.$key"
          acc ++ flatten(value, newKey)
        }

      case arr: Json.Array =>
        arr.elements.zipWithIndex.foldLeft(Map.empty[String, String]) { case (acc, (value, index)) =>
          val newKey = if (prefix.isEmpty) index.toString else s"$prefix.$index"
          acc ++ flatten(value, newKey)
        }

      case str: Json.String =>
        Map(prefix -> str.value)

      case num: Json.Number =>
        Map(prefix -> num.value.toString)

      case bool: Json.Boolean =>
        Map(prefix -> bool.value.toString)

      case Json.Null =>
        Map.empty
    }
}
