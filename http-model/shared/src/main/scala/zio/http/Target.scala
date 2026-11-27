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
 * A request target: the raw text from an HTTP request line, such as
 * `/api/orders/42?page=2` or the absolute form
 * `https://shop.example.com/api/orders/42`, with lazy views of its parts.
 * Routers read [[segmentCount]] and [[segment]] without building a [[Path]] or
 * parsing the query string; [[url]], [[path]], [[queryParams]] and [[fragment]]
 * parse the target into a [[URL]] on first access and cache the result.
 *
 * [[Target.parse]] accepts exactly the strings [[URL.parse]] accepts, so a
 * `Right` target has a total [[url]]. [[Target.fromURL]] renders the URL back
 * to text (see [[URL#encode]]), so it may re-render the URL: a host with an
 * empty path gains `/`, and a URL that renders as the empty string gives a
 * target whose URL views throw `IllegalStateException`.
 *
 * {{{
 * val target = Target.parse("/api/orders/42?page=2#items").toOption.get
 *
 * target.segmentCount                     // 3
 * target.segment(2)                       // 42
 * target.url.queryParams.getFirst("page") // Some(2)
 * target.fragment                         // Some(items)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#target Target]]
 */
final class Target private (val raw: String) {

  /**
   * The URL parsed from [[raw]] with [[URL.parse]], on first access; later
   * calls return the same instance.
   *
   * @throws java.lang.IllegalStateException
   *   if the target was created by [[Target.fromURL]] and its rendered text is
   *   not a URL that [[URL.parse]] accepts, such as the empty string.
   */
  def url: URL = parsedURL

  /** The [[URL#path]] of [[url]], on first access. */
  def path: Path = url.path

  /** The [[URL#queryParams]] of [[url]], on first access. */
  def queryParams: QueryParams = url.queryParams

  /** The [[URL#fragment]] of [[url]], on first access. */
  def fragment: Option[String] = url.fragment

  /**
   * The number of decoded path segments, equal to `url.path.segments.length`.
   * It reads the raw text and does not build a [[Path]].
   */
  def segmentCount: Int = decodedSegments.length

  /**
   * Returns decoded path segment `i`, equal to `url.path.segments(i)`. The
   * decoding matches `Path.fromEncoded`: an encoded slash (`%2F`) stays inside
   * its segment and a `+` stays a plus sign. On first access it decodes every
   * segment, but it does not build a [[Path]] or a `Chunk`.
   *
   * @throws java.lang.IndexOutOfBoundsException
   *   if `i` is negative or not less than [[segmentCount]].
   */
  def segment(i: Int): String = decodedSegments(i)

  /** Returns [[raw]]. */
  override def toString: String = raw

  /** Compares targets by [[raw]]. */
  override def equals(that: Any): Boolean =
    that match {
      case target: Target => target.raw == raw
      case _              => false
    }

  /** A hash consistent with `equals`. */
  override def hashCode: Int = raw.hashCode

  private[this] lazy val parsed: Either[String, URL] = URL.parse(raw)

  private def parsedURL: URL =
    parsed match {
      case Right(url) => url
      case Left(err)  => throw new IllegalStateException(s"Invalid target '$raw': $err")
    }

  private[this] lazy val decodedSegments: Array[String] = Target.segmentsOf(raw)
}

/**
 * Parses and creates [[Target]]s.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#target Target]]
 */
object Target {

  /**
   * Parses a request target. It returns `Left` exactly when [[URL.parse]]
   * returns `Left` for the same string, with the same message: the empty
   * string, a port that is not a number from 0 to 65535, or an empty host
   * before a `:`. Nothing is percent-decoded until a view or a segment is read.
   *
   * {{{
   * Target.parse("/api/orders/42").map(_.segmentCount) // Right(3)
   * Target.parse("http://example.com:99999/").isLeft   // true
   * }}}
   */
  def parse(raw: String): Either[String, Target] = pathOf(raw).map(_ => new Target(raw))

  /**
   * Creates a target from `url` by rendering it with [[URL#encode]]. The target
   * holds the rendered text, not `url`, so [[url]] may give a different URL
   * back: a host with an empty path gains `/`, and a URL that renders as the
   * empty string is not accepted by [[URL.parse]], so the URL views throw.
   */
  def fromURL(url: URL): Target = new Target(url.encode)

  // Copies the parsing steps of URL.parse up to the path text, so parse accepts
  // exactly the same strings and segment access reads the same path. This is
  // checked against URL.parse by TargetSpec; keep the two in sync.
  private def pathOf(s: String): Either[String, String] = {
    if (s.isEmpty) return Left("Empty URL string")

    val len             = s.length
    var pos             = 0
    val colonSlashSlash = s.indexOf("://")
    val hasScheme       = colonSlashSlash > 0
    if (hasScheme) pos = colonSlashSlash + 3

    if (hasScheme && pos < len) {
      val slashIdx = s.indexOf('/', pos)
      val atIdx    = s.indexOf('@', pos)
      if (atIdx >= 0 && (slashIdx < 0 || atIdx < slashIdx)) pos = atIdx + 1

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
          val closeBracket = authority.indexOf(']')
          if (closeBracket >= 0 && closeBracket + 1 < authority.length && authority.charAt(closeBracket + 1) == ':') {
            val portStr = authority.substring(closeBracket + 2)
            if (portStr.nonEmpty)
              try {
                val p = portStr.toInt
                if (p < 0 || p > 65535) return Left(s"Invalid port: $p")
              } catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
          }
        } else {
          val colonIdx = authority.lastIndexOf(':')
          if (colonIdx >= 0) {
            if (colonIdx == 0) return Left("Invalid host: cannot be empty")
            val portStr = authority.substring(colonIdx + 1)
            if (portStr.nonEmpty)
              try {
                val p = portStr.toInt
                if (p < 0 || p > 65535) return Left(s"Invalid port: $p")
              } catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
          }
        }
      }
      pos = authorityEnd
    }

    val remaining = if (pos < len) s.substring(pos) else ""
    if (remaining.isEmpty) return Right("")

    val hashIdx        = remaining.indexOf('#')
    val beforeFragment = if (hashIdx >= 0) remaining.substring(0, hashIdx) else remaining
    val qIdx           = beforeFragment.indexOf('?')
    if (qIdx >= 0) Right(beforeFragment.substring(0, qIdx))
    else Right(beforeFragment)
  }

  // Mirrors the segment handling of Path.fromEncoded, which URL.parse uses for
  // a path: strip at most one leading and one trailing slash, split the rest at
  // '/', then decode each piece with PercentEncoder.decode.
  private def segmentsOf(raw: String): Array[String] =
    pathOf(raw) match {
      case Left(err)      => throw new IllegalStateException(s"Invalid target '$raw': $err")
      case Right(pathStr) =>
        if (pathStr.isEmpty) Array.empty[String]
        else {
          val trimmed = pathStr.stripPrefix("/").stripSuffix("/")
          if (trimmed.isEmpty) Array.empty[String]
          else {
            val parts = trimmed.split('/')
            var i     = 0
            while (i < parts.length) {
              parts(i) = PercentEncoder.decode(parts(i))
              i += 1
            }
            parts
          }
        }
    }
}
