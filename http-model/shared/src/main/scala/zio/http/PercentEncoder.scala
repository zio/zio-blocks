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

package zio.http

/**
 * RFC 3986 percent-encoding utilities.
 *
 * Provides component-specific encoding via [[ComponentType]] variants
 * (`PathSegment`, `QueryKey`, `QueryValue`, `Fragment`, `UserInfo`), each
 * allowing a different set of unescaped characters as defined by the RFC.
 * Non-ASCII characters are first encoded to UTF-8 bytes, then each byte is
 * percent-encoded.
 *
 * `decode` converts percent-encoded triplets back to characters; invalid or
 * incomplete percent sequences are passed through unchanged.
 *
 * Note: this encoder does '''not''' use `application/x-www-form-urlencoded`
 * rules (spaces are encoded as `%20`, never as `+`).
 */
object PercentEncoder {

  /**
   * The URL component an encoded string goes into. It decides which punctuation
   * characters stay unescaped.
   */
  sealed trait ComponentType

  /** The [[ComponentType]] variants. */
  object ComponentType {

    /** A path segment: keeps `:@!$&'()*+,;=` unescaped. */
    case object PathSegment extends ComponentType

    /** A query parameter name: keeps `/?:@!$'()*+,;` unescaped. */
    case object QueryKey extends ComponentType

    /** A query parameter value: keeps `/?:@!$'()*+,;=` unescaped. */
    case object QueryValue extends ComponentType

    /** A fragment: keeps `/?:@!$&'()*+,;=` unescaped. */
    case object Fragment extends ComponentType

    /** The user-info part of a URL: keeps only `:` unescaped. */
    case object UserInfo extends ComponentType
  }

  private def isUnreserved(b: Int): Boolean =
    (b >= 'A' && b <= 'Z') ||
      (b >= 'a' && b <= 'z') ||
      (b >= '0' && b <= '9') ||
      b == '-' || b == '_' || b == '.' || b == '~'

  private def isComponentAllowed(b: Int, componentType: ComponentType): Boolean = {
    import ComponentType._
    componentType match {
      case PathSegment =>
        b == ':' || b == '@' || b == '!' || b == '$' || b == '&' ||
        b == '\'' || b == '(' || b == ')' || b == '*' || b == '+' ||
        b == ',' || b == ';' || b == '='
      case QueryKey =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '\'' || b == '(' || b == ')' || b == '*' ||
        b == '+' || b == ',' || b == ';'
      case QueryValue =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '\'' || b == '(' || b == ')' || b == '*' ||
        b == '+' || b == ',' || b == ';' || b == '='
      case Fragment =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '&' || b == '\'' || b == '(' || b == ')' ||
        b == '*' || b == '+' || b == ',' || b == ';' || b == '='
      case UserInfo =>
        b == ':'
    }
  }

  private val hexChars: Array[Char] = "0123456789ABCDEF".toCharArray

  /**
   * Percent-encodes `s` for the given component. The unreserved characters
   * `A-Z a-z 0-9 - _ . ~` and the characters the component allows are kept;
   * every other byte of the UTF-8 form of `s` becomes `%XX` with upper-case hex
   * digits. The empty string gives the empty string.
   */
  def encode(s: String, componentType: ComponentType): String = {
    if (s.isEmpty) return ""

    val bytes = s.getBytes("UTF-8")
    val sb    = new java.lang.StringBuilder(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      val b = bytes(i) & 0xff
      if (isUnreserved(b) || isComponentAllowed(b, componentType)) {
        sb.append(b.toChar)
      } else {
        sb.append('%')
        sb.append(hexChars(b >> 4))
        sb.append(hexChars(b & 0x0f))
      }
      i += 1
    }
    sb.toString
  }

  private def hexValue(c: Char): Int =
    if (c >= '0' && c <= '9') c - '0'
    else if (c >= 'A' && c <= 'F') c - 'A' + 10
    else if (c >= 'a' && c <= 'f') c - 'a' + 10
    else -1

  /**
   * Decodes `%XX` sequences (upper- or lower-case hex digits) in `s`, reading
   * the decoded bytes as UTF-8. A `%` not followed by two hex digits is kept as
   * it is, and `+` is not turned into a space. Bytes that are not valid UTF-8
   * decode to the replacement character. The empty string gives the empty
   * string.
   */
  def decode(s: String): String = {
    if (s.isEmpty) return ""

    val len = s.length
    val sb  = new java.lang.StringBuilder(len)
    val buf = new java.io.ByteArrayOutputStream(16)
    var i   = 0

    while (i < len) {
      val c = s.charAt(i)
      if (c == '%' && i + 2 < len) {
        val hi = hexValue(s.charAt(i + 1))
        val lo = hexValue(s.charAt(i + 2))
        if (hi >= 0 && lo >= 0) {
          buf.write((hi << 4) | lo)
          i += 3
        } else {
          if (buf.size() > 0) {
            sb.append(new String(buf.toByteArray, "UTF-8"))
            buf.reset()
          }
          sb.append(c)
          i += 1
        }
      } else {
        if (buf.size() > 0) {
          sb.append(new String(buf.toByteArray, "UTF-8"))
          buf.reset()
        }
        sb.append(c)
        i += 1
      }
    }

    if (buf.size() > 0) {
      sb.append(new String(buf.toByteArray, "UTF-8"))
    }

    sb.toString
  }
}
