/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.toon

private[toon] object ToonCodecUtils {

  def createReaderForValue(value: String): ToonReader = {
    val reader = ToonReader(ReaderConfig.withDelimiter(Delimiter.None))
    reader.reset(value)
    reader
  }

  def unescapeQuoted(s: String): String = {
    if (!s.startsWith("\"") || !s.endsWith("\"")) return s
    val inner = s.substring(1, s.length - 1)
    if (inner.indexOf('\\') < 0) return inner
    val sb = new StringBuilder(inner.length)
    var i  = 0
    while (i < inner.length) {
      val c = inner.charAt(i)
      if (c == '\\' && i + 1 < inner.length) {
        inner.charAt(i + 1) match {
          case '"'   => sb.append('"'); i += 2
          case '\\'  => sb.append('\\'); i += 2
          case 'n'   => sb.append('\n'); i += 2
          case 'r'   => sb.append('\r'); i += 2
          case 't'   => sb.append('\t'); i += 2
          case other => sb.append('\\'); sb.append(other); i += 2
        }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString
  }
}
