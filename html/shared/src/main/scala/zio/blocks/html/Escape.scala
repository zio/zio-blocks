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

package zio.blocks.html

private[html] object Escape {

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
    Array("javascript:", "vbscript:", "data:text/html", "data:image/svg")

  /**
   * Length of the longest dangerous scheme prefix (`data:text/html` and
   * `data:image/svg`). Only bytes inside this prefix window can influence the
   * scheme verdict, so the benign fast path only needs to prove this window
   * needs no normalization.
   */
  private val maxDangerousPrefixLength = 14

  /**
   * Rejects URLs whose (entity-decoded, control-stripped, trimmed, lowercased)
   * scheme is known dangerous by prefixing them with `unsafe:`.
   *
   * Browsers decode HTML character references (e.g. `&#106;avascript:`,
   * including semicolon-less `&#106avascript:`) in attribute values and strip
   * ASCII tab/LF/FF/CR anywhere inside the URL before comparing the scheme, so
   * the scheme is matched against a normalized copy: numeric decimal/hex
   * references with or without a trailing semicolon plus the named references
   * relevant in this position (`colon`, `semi`, `amp`, `lt`, `gt`, `quot`,
   * `Tab`, `NewLine`) are decoded first (single pass, no re-decoding of
   * produced text, matching browsers), then tab/LF/FF/CR are removed, and only
   * then is the result lowercased and prefix-checked. Decoding covers only the
   * references needed to smuggle a scheme prefix; exotic or double-encoded
   * payloads remain the caller's responsibility — prefer an allowlist of
   * `http`/`https`/`mailto`/`tel`/relative URLs for untrusted input.
   *
   * `data:image/svg+xml` is rejected because embedded SVG can carry `<script>`
   * content; other bitmap `data:image` types pass through.
   *
   * Benign URLs (no `&`, no tab/LF/FF/CR, no leading/trailing whitespace, no
   * uppercase or non-ASCII byte in the scheme-relevant prefix, no dangerous
   * prefix) return unchanged without building any intermediate string.
   */
  def sanitizeUrl(url: String): String =
    if (isBenignUrl(url)) url
    else {
      val normalized = normalizedUrlScheme(url)
      if (isDangerousScheme(normalized)) "unsafe:" + url
      else url
    }

  /**
   * Returns true when [[sanitizeUrl]] would reject the URL, without building
   * the `unsafe:`-prefixed verdict string. Used to cache the decision for
   * multi-value URL attributes so repeat renders neither flatten nor
   * resanitize.
   */
  private[html] def isUnsafeUrl(url: String): Boolean =
    !isBenignUrl(url) && isDangerousScheme(normalizedUrlScheme(url))

  /**
   * Zero-allocation conservative proof that `url` needs no normalization: a
   * single scan shows there is no `&` (no entity to decode), no tab/LF/FF/CR
   * (nothing to strip), no leading/trailing whitespace (trim is identity), and
   * no uppercase or non-ASCII byte inside the scheme-relevant prefix window
   * (lowercasing cannot change the verdict window; case or width changes at or
   * beyond the window cannot shift bytes into it). The raw prefix is then
   * compared directly. Any uncertainty falls through to the full path, so this
   * never accepts a URL the full normalization would reject.
   */
  private def isBenignUrl(url: String): Boolean = {
    val len = url.length
    if (len == 0) return true
    if (url.charAt(0) <= ' ' || url.charAt(len - 1) <= ' ') return false
    var i = 0
    while (i < len) {
      val c = url.charAt(i)
      if (c == '&' || c == '\t' || c == '\n' || c == '\f' || c == '\r') return false
      if (i < maxDangerousPrefixLength && ((c >= 'A' && c <= 'Z') || c > 127)) return false
      i += 1
    }
    var k = 0
    while (k < dangerousUrlSchemes.length) {
      if (url.startsWith(dangerousUrlSchemes(k))) return false
      k += 1
    }
    true
  }

  private def normalizedUrlScheme(url: String): String =
    stripUrlControls(decodeUrlEntities(url.trim)).toLowerCase(java.util.Locale.ROOT)

  private def isDangerousScheme(normalized: String): Boolean = {
    var i = 0
    while (i < dangerousUrlSchemes.length) {
      if (normalized.startsWith(dangerousUrlSchemes(i))) return true
      i += 1
    }
    false
  }

  /**
   * Removes the ASCII tab/LF/FF/CR characters browsers strip anywhere inside a
   * URL before scheme comparison. Returns `s` unchanged when clean, so the
   * common slow-path case (mixed case only) allocates nothing here.
   */
  private def stripUrlControls(s: String): String = {
    val len = s.length
    var i   = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '\t' || c == '\n' || c == '\f' || c == '\r') {
        val sb = new java.lang.StringBuilder(len - 1)
        sb.append(s, 0, i)
        i += 1
        while (i < len) {
          val d = s.charAt(i)
          if (d != '\t' && d != '\n' && d != '\f' && d != '\r') sb.append(d)
          i += 1
        }
        return sb.toString
      }
      i += 1
    }
    s
  }

  /**
   * Decodes the character references that can smuggle a URL scheme prefix past
   * a prefix check: numeric decimal/hex references with or without a trailing
   * semicolon (browsers decode `&#106` as well as `&#106;`, consuming digits
   * greedily) plus the named references browsers accept in this position
   * (`colon`, `semi`, `amp`, `lt`, `gt`, `quot`, `Tab`, `NewLine`). Unknown or
   * malformed references are left as-is. Index scans only; no substrings.
   */
  private def decodeUrlEntities(s: String): String = {
    if (s.indexOf('&') < 0) return s
    val sb  = new java.lang.StringBuilder(s.length)
    val len = s.length
    var i   = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c != '&') {
        sb.append(c)
        i += 1
      } else {
        val semi = s.indexOf(';', i + 1)
        if (semi >= 0 && semi - i <= 10) {
          val decoded = decodeEntityBody(s, i + 1, semi)
          if (decoded >= 0) {
            sb.appendCodePoint(decoded)
            i = semi + 1
          } else {
            sb.append(c)
            i += 1
          }
        } else {
          val end = semicolonLessNumericEnd(s, i)
          if (end < 0) {
            sb.append(c)
            i += 1
          } else {
            val decoded = decodeNumericBody(s, i + 2, end)
            if (decoded < 0) {
              sb.append(c)
              i += 1
            } else {
              sb.appendCodePoint(decoded)
              i = end
            }
          }
        }
      }
    }
    sb.toString
  }

  /** Decodes the entity body in `s[start, end)` (text between `&` and `;`). */
  private def decodeEntityBody(s: String, start: Int, end: Int): Int =
    if (end - start >= 2 && s.charAt(start) == '#') decodeNumericBody(s, start + 1, end)
    else {
      val len = end - start
      if (len == 2 && s.startsWith("lt", start)) '<'.toInt
      else if (len == 2 && s.startsWith("gt", start)) '>'.toInt
      else if (len == 3 && s.startsWith("amp", start)) '&'.toInt
      else if (len == 3 && s.startsWith("Tab", start)) '\t'.toInt
      else if (len == 4 && s.startsWith("semi", start)) ';'.toInt
      else if (len == 4 && s.startsWith("quot", start)) '"'.toInt
      else if (len == 5 && s.startsWith("colon", start)) ':'.toInt
      else if (len == 7 && s.startsWith("NewLine", start)) '\n'.toInt
      else -1
    }

  /**
   * Decodes the numeric body in `s[start, end)`: ASCII digits, or `x`/`X`
   * followed by hex digits. Returns the code point, or -1 when empty,
   * malformed, NUL, a surrogate, or beyond U+10FFFF.
   */
  private def decodeNumericBody(s: String, start: Int, end: Int): Int = {
    var i     = start
    var radix = 10
    if (i < end && (s.charAt(i) == 'x' || s.charAt(i) == 'X')) {
      radix = 16
      i += 1
    }
    if (i >= end) return -1
    var value = 0
    while (i < end) {
      val c = s.charAt(i)
      val d =
        if (c >= '0' && c <= '9') c - '0'
        else if (radix == 16 && c >= 'a' && c <= 'f') c - 'a' + 10
        else if (radix == 16 && c >= 'A' && c <= 'F') c - 'A' + 10
        else return -1
      value = value * radix + d
      if (value > 0x10ffff) return -1
      i += 1
    }
    if (value == 0 || (value >= 0xd800 && value <= 0xdfff)) -1
    else value
  }

  /**
   * Exclusive end index of a semicolon-less numeric reference at `i` (where
   * `s(i) == '&'`): `&#[0-9]+` or `&#[xX][0-9a-fA-F]+` with greedy digit
   * consumption, matching browser decoding. Returns -1 when absent.
   */
  private def semicolonLessNumericEnd(s: String, i: Int): Int = {
    val len = s.length
    var j   = i + 1
    if (j >= len || s.charAt(j) != '#') return -1
    j += 1
    if (j < len && (s.charAt(j) == 'x' || s.charAt(j) == 'X')) {
      j += 1
      val digits = j
      while (j < len && isHexDigit(s.charAt(j))) j += 1
      if (j == digits) -1 else j
    } else {
      val digits = j
      while (j < len && s.charAt(j) >= '0' && s.charAt(j) <= '9') j += 1
      if (j == digits) -1 else j
    }
  }

  private def isHexDigit(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

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
