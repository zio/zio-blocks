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
 * An immutable URL, absolute (`https://shop.example.com/api/orders`) or
 * relative (`/api/orders?page=2`), split into scheme, host, port, [[Path]],
 * [[QueryParams]] and fragment. The path, query and fragment hold decoded text;
 * [[encode]] percent-encodes them again on output, so read and write them
 * without escaping by hand. Parse one with [[URL.parse]], and build on it with
 * `/` (a path segment) and `??` (a query parameter).
 *
 * {{{
 * val url = URL.parse("https://shop.example.com:8443/api/orders/42?page=2&sort=price#items").toOption.get
 *
 * url.scheme                         // Some(https)
 * url.host                           // Some(shop.example.com)
 * url.port                           // Some(8443)
 * url.path.segments                  // Chunk(api,orders,42)
 * url.queryParams.getFirst("sort")   // Some(price)
 * url.fragment                       // Some(items)
 * url.relative.encode                // /api/orders/42?page=2&sort=price#items
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#url URL]]
 */
final case class URL(
  scheme: Option[Scheme],
  host: Option[String],
  port: Option[Int],
  path: Path,
  queryParams: QueryParams,
  fragment: Option[String]
) {

  /** Whether the URL has a scheme. */
  def isAbsolute: Boolean = scheme.isDefined

  /** Whether the URL has no scheme. */
  def isRelative: Boolean = !isAbsolute

  /**
   * Appends one decoded path segment (see `Path#/`). A `/` inside `segment`
   * stays part of the segment and is written as `%2F`.
   *
   * {{{
   * val base = URL.parse("https://shop.example.com").toOption.get
   *
   * (base / "api" / "orders" / "42").encode         // https://shop.example.com/api/orders/42
   * (URL.fromPath(Path.root) / "q3 plan/draft").encode // /q3%20plan%2Fdraft
   * }}}
   */
  def /(segment: String): URL = copy(path = path / segment)

  /**
   * Adds a query parameter, keeping existing values of the same key (see
   * [[QueryParams#add]]).
   *
   * {{{
   * val base = URL.parse("https://shop.example.com/api/orders").toOption.get
   *
   * base.??("include", "items").??("include", "customer").encode
   * // https://shop.example.com/api/orders?include=items&include=customer
   * }}}
   */
  def ??(key: String, value: String): URL = copy(queryParams = queryParams.add(key, value))

  /**
   * Renders the URL as a string: `scheme://`, the host and `:port`, the
   * percent-encoded path, `?` and the encoded query when there are parameters,
   * and `#` and the encoded fragment when there is one. The host is written as
   * given, without encoding. A URL with a host and an empty path gets the path
   * `/`.
   *
   * {{{
   * URL.parse("https://shop.example.com").toOption.get.encode       // https://shop.example.com/
   * URL.fromPath(Path("/notes/q3 plan")).??("tag", "a&b").encode     // /notes/q3%20plan?tag=a%26b
   * }}}
   */
  def encode: String = {
    val sb = new StringBuilder

    scheme.foreach { s =>
      sb.append(s.text)
      sb.append("://")
    }

    host.foreach { h =>
      sb.append(h)
      port.foreach { p =>
        sb.append(':')
        sb.append(p)
      }
    }

    val pathStr = path.encode
    if (host.isDefined && pathStr.isEmpty) {
      sb.append('/')
    } else {
      sb.append(pathStr)
    }

    val queryStr = queryParams.encode
    if (queryStr.nonEmpty) {
      sb.append('?')
      sb.append(queryStr)
    }

    fragment.foreach { f =>
      sb.append('#')
      sb.append(PercentEncoder.encode(f, PercentEncoder.ComponentType.Fragment))
    }

    sb.toString
  }

  /** Returns [[encode]]. */
  override def toString: String = encode

  /** Returns a copy with the host set to `h`, written as given. */
  def host(h: String): URL = copy(host = Some(h))

  /** Returns a copy with the port set to `p`, which is not range-checked. */
  def port(p: Int): URL = copy(port = Some(p))

  /** Returns a copy with the scheme set to `s`. */
  def scheme(s: Scheme): URL = copy(scheme = Some(s))

  /** Returns a copy with the path replaced by `p`. */
  def path(p: Path): URL = copy(path = p)

  /** Returns a copy with the fragment set to `f`, decoded text. */
  def fragment(f: String): URL = copy(fragment = Some(f))

  /** Same as `/`: appends one decoded path segment. */
  def addPath(segment: String): URL = this / segment

  /**
   * Appends the segments of `p` (see `Path#++`); the result takes its trailing
   * slash from `p`.
   */
  def addPath(p: Path): URL = copy(path = path ++ p)

  /**
   * Adds every parameter of `qp`, appending values to keys that already exist
   * (see `QueryParams#++`).
   */
  def addQueryParams(qp: QueryParams): URL = copy(queryParams = queryParams ++ qp)

  /**
   * Returns a copy whose query parameters are `f` applied to the current ones.
   */
  def updateQueryParams(f: QueryParams => QueryParams): URL = copy(queryParams = f(queryParams))

  /** Returns a copy whose path starts with `/`. */
  def addLeadingSlash: URL = copy(path = path.addLeadingSlash)

  /** Returns a copy whose path does not start with `/`. */
  def dropLeadingSlash: URL = copy(path = path.dropLeadingSlash)

  /**
   * Returns a copy whose path ends with `/`; a path with no segments is
   * unchanged.
   */
  def addTrailingSlash: URL = copy(path = path.addTrailingSlash)

  /** Returns a copy whose path does not end with `/`. */
  def dropTrailingSlash: URL = copy(path = path.dropTrailingSlash)

  /**
   * Returns `host:port`, or the host alone when there is no port, or `None`
   * when there is no host. A port is not filled in from the scheme.
   */
  def hostPort: Option[String] = host.map(h => port.fold(h)(p => s"$h:$p"))

  /**
   * Returns a copy without scheme, host and port: the path, query and fragment.
   */
  def relative: URL = copy(scheme = None, host = None, port = None)
}

/**
 * Parses and creates [[URL]]s.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#parsing-urls Parsing URLs]]
 */
object URL {

  /** `http://localhost/`. */
  val root: URL = URL(Some(Scheme.HTTP), Some("localhost"), None, Path.root, QueryParams.empty, None)

  /** A relative URL with `path`, no query and no fragment. */
  def fromPath(path: Path): URL = URL(None, None, None, path, QueryParams.empty, None)

  /**
   * Parses an absolute URL (`scheme://host:port/path?query#fragment`) or a
   * relative one (`/path?query#fragment`). The path, query and fragment are
   * percent-decoded. The scheme is lower-cased and the host is kept as written;
   * user info before an `@` is dropped. A URL that starts with `//`, such as
   * `//cdn.example.com/app.js`, has no scheme, so it parses as a path whose
   * first segment is empty, with no host.
   *
   * It returns `Left` only for the empty string, a port that is not a number
   * from 0 to 65535, and an empty host before a `:`. Anything else parses, so a
   * `Right` does not mean the URL is well formed.
   *
   * {{{
   * URL.parse("https://shop.example.com:8443/api/orders/42?page=2").map(_.port) // Right(Some(8443))
   * URL.parse("/search?q=tea%20cups").map(_.queryParams.getFirst("q"))          // Right(Some(tea cups))
   * URL.parse("http://shop.example.com:99999/")                                 // Left(Invalid port: 99999)
   * URL.parse("")                                                               // Left(Empty URL string)
   * }}}
   *
   * Known bugs: a `+` in the query stays a plus sign instead of becoming a
   * space, as HTML forms that use `GET` expect (`?q=tea+cups` gives
   * `tea+cups`). The parser also looks for `://`, `/` and `@` in the whole
   * string, not only before the path:
   * `/login?next=https://shop.example.com/cart` parses with the scheme
   * `/login?next=https` and the host `shop.example.com`,
   * `http://shop.example.com?back=/cart` with the host
   * `shop.example.com?back=`, and `http://shop.example.com?to=a@b.com` with the
   * host `b.com`.
   */
  def parse(s: String): Either[String, URL] = {
    if (s.isEmpty) return Left("Empty URL string")

    val len = s.length
    var pos = 0

    // 1. Detect scheme
    var scheme: Option[Scheme] = None
    val colonSlashSlash        = s.indexOf("://")
    if (colonSlashSlash > 0) {
      scheme = Some(Scheme.fromString(s.substring(0, colonSlashSlash)))
      pos = colonSlashSlash + 3
    }

    // 2. Parse authority (host, port) if scheme present
    var host: Option[String] = None
    var port: Option[Int]    = None
    if (scheme.isDefined && pos < len) {
      // Skip userinfo if present (look for @ before next /)
      val slashIdx = s.indexOf('/', pos)
      val atIdx    = s.indexOf('@', pos)
      if (atIdx >= 0 && (slashIdx < 0 || atIdx < slashIdx)) {
        pos = atIdx + 1
      }

      // Parse host (may be IPv6 bracketed)
      val authorityEnd =
        if (slashIdx >= 0) slashIdx
        else {
          val qIdx = s.indexOf('?', pos)
          val hIdx = s.indexOf('#', pos)
          if (qIdx >= 0 && hIdx >= 0) Math.min(qIdx, hIdx)
          else if (qIdx >= 0) qIdx
          else if (hIdx >= 0) hIdx
          else len
        }

      val authority = s.substring(pos, authorityEnd)
      if (authority.nonEmpty) {
        if (authority.charAt(0) == '[') {
          // IPv6
          val closeBracket = authority.indexOf(']')
          if (closeBracket >= 0) {
            host = Some(authority.substring(0, closeBracket + 1))
            if (closeBracket + 1 < authority.length && authority.charAt(closeBracket + 1) == ':') {
              val portStr = authority.substring(closeBracket + 2)
              if (portStr.nonEmpty)
                try {
                  val p = portStr.toInt
                  if (p < 0 || p > 65535) return Left(s"Invalid port: $p")
                  port = Some(p)
                } catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
            }
          } else {
            host = Some(authority)
          }
        } else {
          val colonIdx = authority.lastIndexOf(':')
          if (colonIdx >= 0) {
            val hostPart = authority.substring(0, colonIdx)
            if (hostPart.isEmpty) return Left("Invalid host: cannot be empty")
            host = Some(hostPart)
            val portStr = authority.substring(colonIdx + 1)
            if (portStr.nonEmpty)
              try {
                val p = portStr.toInt
                if (p < 0 || p > 65535) return Left(s"Invalid port: $p")
                port = Some(p)
              } catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
          } else {
            host = Some(authority)
          }
        }
      }
      pos = authorityEnd
    }

    // 3. Parse path, query, fragment from remaining string
    val remaining = if (pos < len) s.substring(pos) else ""

    var pathStr: String             = ""
    var queryStr: Option[String]    = None
    var fragmentStr: Option[String] = None

    if (remaining.nonEmpty) {
      // Split off fragment first
      val hashIdx        = remaining.indexOf('#')
      val beforeFragment = if (hashIdx >= 0) {
        fragmentStr = Some(PercentEncoder.decode(remaining.substring(hashIdx + 1)))
        remaining.substring(0, hashIdx)
      } else {
        remaining
      }

      // Split off query
      val qIdx = beforeFragment.indexOf('?')
      if (qIdx >= 0) {
        pathStr = beforeFragment.substring(0, qIdx)
        queryStr = Some(beforeFragment.substring(qIdx + 1))
      } else {
        pathStr = beforeFragment
      }
    }

    val parsedPath =
      if (pathStr.isEmpty && scheme.isDefined) Path.root
      else if (pathStr.isEmpty) Path.empty
      else Path.fromEncoded(pathStr)

    val parsedQuery = queryStr match {
      case Some(q) => QueryParams.fromEncoded(q)
      case None    => QueryParams.empty
    }

    Right(URL(scheme, host, port, parsedPath, parsedQuery, fragmentStr))
  }
}
