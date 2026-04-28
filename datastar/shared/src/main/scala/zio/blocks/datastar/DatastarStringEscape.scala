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

package zio.blocks.datastar

private[datastar] object DatastarStringEscape {

  def quotedString(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length + 2)
    appendQuotedString(sb, s)
    sb.toString
  }

  def appendQuotedString(sb: java.lang.StringBuilder, s: String): Unit = {
    sb.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'      => sb.append("\\\"")
        case '\\'     => sb.append("\\\\")
        case '\b'     => sb.append("\\b")
        case '\f'     => sb.append("\\f")
        case '\n'     => sb.append("\\n")
        case '\r'     => sb.append("\\r")
        case '\t'     => sb.append("\\t")
        case '<'      => sb.append("\\u003c")
        case '>'      => sb.append("\\u003e")
        case '&'      => sb.append("\\u0026")
        case '\u2028' => sb.append("\\u2028")
        case '\u2029' => sb.append("\\u2029")
        case _ if c < 32 =>
          sb.append("\\u")
          val hex = Integer.toHexString(c.toInt)
          var pad = 4 - hex.length
          while (pad > 0) {
            sb.append('0')
            pad -= 1
          }
          sb.append(hex)
        case _ => sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }
}
