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

trait KeyMapper {
  def toCanonical(key: String): String
  def fromCanonical(key: String, target: KeyFormat): String
}

object KeyMapper {
  val default: KeyMapper = new KeyMapper {
    def toCanonical(key: String): String =
      // UPPER_SNAKE_CASE -> camelCase
      if (key.contains('_')) {
        val parts = key.split('_').map(_.toLowerCase)
        parts.head + parts.tail.map(p => s"${p.head.toUpper}${p.tail}").mkString
      }
      // kebab-case -> camelCase
      else if (key.contains('-')) {
        val parts = key.split('-').map(_.toLowerCase)
        parts.head + parts.tail.map(p => s"${p.head.toUpper}${p.tail}").mkString
      }
      // camelCase identity
      else key

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
