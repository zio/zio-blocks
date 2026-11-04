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
 * The scheme of a URL, such as `http` or `wss`.
 *
 * The four built-in schemes are [[Scheme.HTTP]], [[Scheme.HTTPS]],
 * [[Scheme.WS]] and [[Scheme.WSS]]; any other scheme is a [[Scheme.Custom]].
 * `toString` returns `text`.
 */
sealed trait Scheme {

  /** The scheme as it appears in a URL, for example `https`. */
  def text: String

  /**
   * The port used when a URL names none: `Some(80)` for `http` and `ws`,
   * `Some(443)` for `https` and `wss`, `None` for a [[Scheme.Custom]].
   */
  def defaultPort: Option[Int]

  /** Whether the scheme runs over TLS (`https` and `wss`). */
  def isSecure: Boolean

  /** Whether the scheme is a WebSocket scheme (`ws` and `wss`). */
  def isWebSocket: Boolean
  override def toString: String = text
}

/** The [[Scheme]] values and their text conversions. */
object Scheme {

  /** The `http` scheme: default port 80, not secure, not WebSocket. */
  case object HTTP extends Scheme {

    /** The scheme text, `http`. */
    val text: String = "http"

    /** The default port of the scheme. */
    val defaultPort: Option[Int] = Some(80)

    /** Whether the scheme is secure. */
    val isSecure: Boolean = false

    /** Whether the scheme is a WebSocket scheme. */
    val isWebSocket: Boolean = false
  }

  /** The `https` scheme: default port 443, secure, not WebSocket. */
  case object HTTPS extends Scheme {

    /** The scheme text, `https`. */
    val text: String = "https"

    /** The default port of the scheme. */
    val defaultPort: Option[Int] = Some(443)

    /** Whether the scheme is secure. */
    val isSecure: Boolean = true

    /** Whether the scheme is a WebSocket scheme. */
    val isWebSocket: Boolean = false
  }

  /** The `ws` scheme: default port 80, not secure, WebSocket. */
  case object WS extends Scheme {

    /** The scheme text, `ws`. */
    val text: String = "ws"

    /** The default port of the scheme. */
    val defaultPort: Option[Int] = Some(80)

    /** Whether the scheme is secure. */
    val isSecure: Boolean = false

    /** Whether the scheme is a WebSocket scheme. */
    val isWebSocket: Boolean = true
  }

  /** The `wss` scheme: default port 443, secure, WebSocket. */
  case object WSS extends Scheme {

    /** The scheme text, `wss`. */
    val text: String = "wss"

    /** The default port of the scheme. */
    val defaultPort: Option[Int] = Some(443)

    /** Whether the scheme is secure. */
    val isSecure: Boolean = true

    /** Whether the scheme is a WebSocket scheme. */
    val isWebSocket: Boolean = true
  }

  /**
   * A scheme other than the four built-in ones, for example `ftp`. It has no
   * default port, is not secure and is not a WebSocket scheme. The `text` is
   * kept as given.
   */
  final case class Custom(text: String) extends Scheme {

    /** Always `None`. */
    val defaultPort: Option[Int] = None

    /** Always `false`. */
    val isSecure: Boolean = false

    /** Always `false`. */
    val isWebSocket: Boolean = false
  }

  /**
   * Converts `s` to lower case and returns the matching built-in scheme
   * (`http`, `https`, `ws` or `wss`), or a [[Custom]] holding the lower-cased
   * text for any other input, including the empty string. Whitespace is kept:
   * `" http"` gives `Custom(" http")`.
   *
   * The lower-casing uses the JVM's default locale, so under a Turkish or
   * Azerbaijani locale `FILE` gives `Custom(fıle)`; this is a known bug. The
   * four built-in names contain no `i`, so they are not affected.
   */
  def fromString(s: String): Scheme = s.toLowerCase match {
    case "http"  => HTTP
    case "https" => HTTPS
    case "ws"    => WS
    case "wss"   => WSS
    case other   => Custom(other)
  }

  /** Returns the `text` of `scheme`. */
  def render(scheme: Scheme): String = scheme.text
}
