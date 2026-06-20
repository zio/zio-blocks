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

private[json] object JsonConfigSource {

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
        val excerpt = if (json.length > 100) json.take(100) + "..." else json
        Left(ConfigError.InvalidValue("", excerpt, "valid JSON", sourceId, Some(schemaError)))
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
  private def flatten(json: Json, prefix: String): Map[String, String] = {
    val builder = scala.collection.mutable.Map.empty[String, String]
    flattenRec(json, prefix, builder)
    builder.toMap
  }

  private def flattenRec(
    json: Json,
    prefix: String,
    builder: scala.collection.mutable.Map[String, String]
  ): Unit =
    json match {
      case obj: Json.Object =>
        obj.fields.foreach { case (key, value) =>
          val newKey = if (prefix.isEmpty) key else s"$prefix.$key"
          flattenRec(value, newKey, builder)
        }

      case arr: Json.Array =>
        var idx = 0
        arr.elements.foreach { value =>
          val newKey = if (prefix.isEmpty) idx.toString else s"$prefix.$idx"
          flattenRec(value, newKey, builder)
          idx += 1
        }

      case str: Json.String =>
        builder(prefix) = str.value

      case num: Json.Number =>
        builder(prefix) = num.value.toString

      case bool: Json.Boolean =>
        builder(prefix) = bool.value.toString

      case Json.Null => ()
    }
}
