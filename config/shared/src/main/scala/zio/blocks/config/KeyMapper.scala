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
 * Maps source-specific configuration keys to the canonical lower-camel form
 * used by config decoders and wrapper sources, and back again.
 *
 * For example, a mapper may treat `databaseUrl`, `database_url`, and
 * `database-url` as the same logical key.
 */
trait KeyMapper {

  /** Normalize a source-facing key into canonical lower-camel form. */
  def toCanonical(key: String): String

  /** Render a canonical lower-camel key into the requested target format. */
  def fromCanonical(key: String, target: KeyFormat): String
}

object KeyMapper {

  /**
   * Default mapper that normalizes snake_case and kebab-case into camelCase,
   * and renders canonical keys back into camelCase, snake_case, kebab-case, or
   * UPPER_SNAKE_CASE.
   */
  val default: KeyMapper = new KeyMapper {
    def toCanonical(key: String): String = {
      val sep = if (key.indexOf('_') >= 0) '_' else if (key.indexOf('-') >= 0) '-' else '\u0000'
      if (sep == '\u0000') return key
      val sb             = new StringBuilder(key.length)
      var capitalizeNext = false
      var i              = 0
      while (i < key.length) {
        val c = key.charAt(i)
        if (c == sep) capitalizeNext = true
        else if (capitalizeNext) { sb.append(c.toUpper); capitalizeNext = false }
        else sb.append(c.toLower)
        i += 1
      }
      sb.toString
    }

    def fromCanonical(key: String, target: KeyFormat): String = target match {
      case KeyFormat.CamelCase =>
        key
      case KeyFormat.SnakeCase =>
        camelToSnakeCase(key)
      case KeyFormat.KebabCase =>
        camelToKebabCase(key)
      case KeyFormat.UpperSnakeCase =>
        camelToSnakeCase(key).toUpperCase
    }

    private def camelToSnakeCase(key: String): String = {
      val sb = new StringBuilder
      for (i <- key.indices) {
        val c = key(i)
        if (c.isUpper && i > 0) {
          sb.append('_')
          sb.append(c.toLower)
        } else {
          sb.append(c)
        }
      }
      sb.toString
    }

    private def camelToKebabCase(key: String): String = {
      val sb = new StringBuilder
      for (i <- key.indices) {
        val c = key(i)
        if (c.isUpper && i > 0) {
          sb.append('-')
          sb.append(c.toLower)
        } else {
          sb.append(c)
        }
      }
      sb.toString
    }
  }
}
