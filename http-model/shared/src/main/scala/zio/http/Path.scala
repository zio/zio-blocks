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

import zio.blocks.chunk.Chunk

/**
 * The path of a URL as a list of decoded segments, plus whether it starts and
 * ends with `/`. Segments hold plain text, such as `q3 plan` or `a/b`;
 * [[encode]] percent-encodes each one for the wire and [[render]] joins them as
 * they are. Create a path with `Path("/api/orders")` from decoded text, with
 * [[Path.fromEncoded]] from text taken off the wire, or by appending to
 * [[Path.root]] with `/`.
 *
 * {{{
 * val path = Path.fromEncoded("/api/notes/q3%20plan/")
 *
 * path.segments        // Chunk(api,notes,q3 plan)
 * path.hasLeadingSlash // true
 * path.trailingSlash   // true
 * path.encode          // /api/notes/q3%20plan/
 * path.render          // /api/notes/q3 plan/
 * }}}
 *
 * @param segments
 *   the decoded segments, without the `/` between them
 * @param hasLeadingSlash
 *   whether the path starts with `/`
 * @param trailingSlash
 *   whether the path ends with `/`; [[encode]] and [[render]] write it only
 *   when there is at least one segment
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#path Path]]
 */
final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean) {

  /**
   * Whether the path has no segments and no leading slash, like [[Path.empty]].
   * [[Path.root]] (`/`) is not empty.
   */
  def isEmpty: Boolean = segments.isEmpty && !hasLeadingSlash

  /** Whether the path has a segment or a leading slash. */
  def nonEmpty: Boolean = !isEmpty

  /** The number of segments. */
  def length: Int = segments.length

  /**
   * Appends one decoded segment and drops the trailing slash. The segment is
   * kept whole: a `/` in it is encoded as `%2F` by [[encode]].
   *
   * {{{
   * (Path.root / "notes" / "42").encode    // /notes/42
   * (Path.root / "q3 plan/draft").encode   // /q3%20plan%2Fdraft
   * (Path.root / "q3 plan/draft").segments // Chunk(q3 plan/draft)
   * }}}
   */
  def /(segment: String): Path = Path(segments :+ segment, hasLeadingSlash, trailingSlash = false)

  /**
   * Appends the segments of `other`. The result keeps this path's leading slash
   * and takes the trailing slash of `other`.
   */
  def ++(other: Path): Path = Path(segments ++ other.segments, hasLeadingSlash, other.trailingSlash)

  /**
   * Renders the path for the wire: each segment percent-encoded, joined with
   * `/`, with the leading and trailing slash when set. `%`, space, `/`, `?` and
   * `#` in a segment are encoded; `:@!$&'()*+,;=` are not.
   *
   * {{{
   * Path.root./("café")./("50%").encode // /caf%C3%A9/50%25
   * }}}
   */
  def encode: String = {
    val sb = new StringBuilder
    if (hasLeadingSlash) sb.append('/')
    var i = 0
    while (i < segments.length) {
      if (i > 0) sb.append('/')
      sb.append(PercentEncoder.encode(segments(i), PercentEncoder.ComponentType.PathSegment))
      i += 1
    }
    if (trailingSlash && segments.nonEmpty) sb.append('/')
    sb.toString
  }

  /**
   * Joins the segments with `/` as they are, without encoding, with the leading
   * and trailing slash when set. Use it for display and logs; use [[encode]]
   * for the wire.
   */
  def render: String = {
    val sb = new StringBuilder
    if (hasLeadingSlash) sb.append('/')
    var i = 0
    while (i < segments.length) {
      if (i > 0) sb.append('/')
      sb.append(segments(i))
      i += 1
    }
    if (trailingSlash && segments.nonEmpty) sb.append('/')
    sb.toString
  }

  /** Returns [[render]], the decoded form. */
  override def toString: String = render

  /** Returns a copy that starts with `/`. */
  def addLeadingSlash: Path = copy(hasLeadingSlash = true)

  /** Returns a copy that does not start with `/`. */
  def dropLeadingSlash: Path = copy(hasLeadingSlash = false)

  /**
   * Returns a copy that ends with `/`. A path with no segments is returned
   * unchanged.
   */
  def addTrailingSlash: Path = if (segments.nonEmpty) copy(trailingSlash = true) else this

  /** Returns a copy that does not end with `/`. */
  def dropTrailingSlash: Path = copy(trailingSlash = false)

  /** Whether the path is `/`: no segments and a leading slash. */
  def isRoot: Boolean = segments.isEmpty && hasLeadingSlash

  /**
   * Whether the first segments of this path equal the segments of `other`.
   * Whole segments are compared, and the leading and trailing slashes are
   * ignored.
   *
   * {{{
   * Path("/api/orders/42").startsWith(Path("/api/orders")) // true
   * Path("api/orders").startsWith(Path("/api"))            // true
   * Path("/apix").startsWith(Path("/api"))                 // false
   * }}}
   */
  def startsWith(other: Path): Boolean =
    segments.length >= other.segments.length && segments.take(other.segments.length) == other.segments

  /**
   * Drops the first `n` segments, keeping the leading and trailing slash flags:
   * `Path("/api/orders/42").drop(1)` is `/orders/42`.
   */
  def drop(n: Int): Path = copy(segments = segments.drop(n))

  /** Keeps the first `n` segments, keeping both slash flags. */
  def take(n: Int): Path = copy(segments = segments.take(n))

  /** Drops the last `n` segments, keeping both slash flags. */
  def dropRight(n: Int): Path = copy(segments = segments.dropRight(n))

  /** Reverses the order of the segments, keeping both slash flags. */
  def reverse: Path = copy(segments = segments.reverse)

  /** Drops the last segment, if any, keeping both slash flags. */
  def initial: Path = if (segments.isEmpty) this else copy(segments = segments.dropRight(1))

  /** The last segment, or `None` for a path with no segments. */
  def last: Option[String] = if (segments.isEmpty) None else Some(segments(segments.length - 1))
}

/**
 * Creates [[Path]]s.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#creating-paths Creating Paths]]
 */
object Path {

  /**
   * The path with no segments and no slashes; it renders as the empty string.
   */
  val empty: Path = Path(Chunk.empty, hasLeadingSlash = false, trailingSlash = false)

  /** The path `/`: no segments and a leading slash. */
  val root: Path = Path(Chunk.empty, hasLeadingSlash = true, trailingSlash = false)

  /**
   * Splits decoded text at `/` into segments, recording a leading and a
   * trailing slash. Nothing is decoded: `%20` stays as the three characters,
   * and [[Path#encode]] writes it as `%2520`. Use [[fromEncoded]] for text
   * taken from a URL. Empty segments between two slashes are kept.
   *
   * {{{
   * Path("/api/orders/42").segments // Chunk(api,orders,42)
   * Path("/api/orders/").render     // /api/orders/
   * Path("a//b").segments           // Chunk(a,,b)
   * Path("")                        // the same as Path.empty
   * }}}
   */
  def apply(raw: String): Path = {
    if (raw.isEmpty) return empty
    val leading  = raw.startsWith("/")
    val trailing = raw.endsWith("/") && raw.length > 1
    val trimmed  = raw.stripPrefix("/").stripSuffix("/")
    if (trimmed.isEmpty) {
      if (leading) root.copy(trailingSlash = trailing)
      else empty
    } else {
      val parts = trimmed.split('/')
      Path(Chunk.fromArray(parts), hasLeadingSlash = leading, trailingSlash = trailing)
    }
  }

  /**
   * Splits percent-encoded text at `/` and decodes each segment, as
   * [[URL.parse]] does with a path. The split happens before decoding, so an
   * encoded slash (`%2F`) stays inside its segment, and a `+` stays a plus
   * sign.
   *
   * {{{
   * Path.fromEncoded("/notes/caf%C3%A9/a%2Fb").segments // Chunk(notes,café,a/b)
   * Path.fromEncoded("%2Fapi%2Forders").segments        // Chunk(/api/orders)
   * }}}
   */
  def fromEncoded(raw: String): Path = {
    if (raw.isEmpty) return empty
    val leading  = raw.startsWith("/")
    val trailing = raw.endsWith("/") && raw.length > 1
    val trimmed  = raw.stripPrefix("/").stripSuffix("/")
    if (trimmed.isEmpty) {
      if (leading) root.copy(trailingSlash = trailing)
      else empty
    } else {
      val parts = trimmed.split('/')
      var i     = 0
      while (i < parts.length) {
        parts(i) = PercentEncoder.decode(parts(i))
        i += 1
      }
      Path(Chunk.fromArray(parts), hasLeadingSlash = leading, trailingSlash = trailing)
    }
  }

  /** Returns `path.render`. */
  def render(path: Path): String = path.render
}
