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

package zio.blocks.template

private[template] object Escape {

  def html(s: String): String = {
    val len = s.length
    if (len == 0) return s

    var needsEscape = false
    var i           = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
        needsEscape = true
        i = len
      }
      i += 1
    }

    if (!needsEscape) return s

    val sb = new java.lang.StringBuilder(len + 16)
    htmlTo(s, sb)
    sb.toString
  }

  def htmlTo(s: String, sb: java.lang.StringBuilder): Unit = {
    val len = s.length
    if (len == 0) return

    var i = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '&') sb.append("&amp;")
      else if (c == '<') sb.append("&lt;")
      else if (c == '>') sb.append("&gt;")
      else if (c == '"') sb.append("&quot;")
      else if (c == '\'') sb.append("&#x27;")
      else sb.append(c)
      i += 1
    }
  }

  def jsString(s: String): String = {
    val len = s.length
    if (len == 0) return s

    var i = 0
    while (i < len) {
      val c = s.charAt(i)
      if (
        c == '"' || c == '\'' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f' || c == '<' || c == '>' || c == '&' || c == '\u2028' || c == '\u2029' || c < 32
      ) {
        val sb = new java.lang.StringBuilder(len + 16)
        jsStringTo(s, sb)
        return sb.toString
      }
      i += 1
    }
    s
  }

  def jsStringTo(s: String, sb: java.lang.StringBuilder): Unit = {
    val len = s.length
    if (len == 0) return

    var i = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '"') sb.append("\\\"")
      else if (c == '\'') sb.append("\\'")
      else if (c == '\\') sb.append("\\\\")
      else if (c == '\n') sb.append("\\n")
      else if (c == '\r') sb.append("\\r")
      else if (c == '\t') sb.append("\\t")
      else if (c == '<') sb.append("\\u003c")
      else if (c == '>') sb.append("\\u003e")
      else if (c == '&') sb.append("\\u0026")
      else if (c == '\u2028') sb.append("\\u2028")
      else if (c == '\u2029') sb.append("\\u2029")
      else if (c < 32) {
        sb.append("\\u")
        val hex = Integer.toHexString(c.toInt)
        var pad = 4 - hex.length
        while (pad > 0) {
          sb.append('0')
          pad -= 1
        }
        sb.append(hex)
      } else sb.append(c)
      i += 1
    }
  }

  private val dangerousUrlSchemes: Array[String] =
    Array("javascript:", "vbscript:", "data:text/html")

  def sanitizeUrl(url: String): String = {
    val trimmed = url.trim.toLowerCase
    var i       = 0
    while (i < dangerousUrlSchemes.length) {
      if (trimmed.startsWith(dangerousUrlSchemes(i))) return "unsafe:" + url
      i += 1
    }
    url
  }

  def cssString(s: String): String = {
    val len = s.length
    if (len == 0) return s

    val sb = new java.lang.StringBuilder(len + 8)
    var i  = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '\\') sb.append("\\\\")
      else if (c == '"') sb.append("\\\"")
      else if (c == '\'') sb.append("\\'")
      else if (c == '<') sb.append("\\3c ")
      else if (c == '>') sb.append("\\3e ")
      else if (c == '&') sb.append("\\26 ")
      else if (c < 32) {
        sb.append('\\')
        sb.append(Integer.toHexString(c.toInt))
        sb.append(' ')
      } else sb.append(c)
      i += 1
    }
    sb.toString
  }
}
