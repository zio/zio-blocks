package zio.http

import zio.blocks.chunk.Chunk

final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean) {

  def isEmpty: Boolean  = segments.isEmpty && !hasLeadingSlash
  def nonEmpty: Boolean = !isEmpty
  def length: Int       = segments.length

  def /(segment: String): Path = Path(segments :+ segment, hasLeadingSlash, trailingSlash = false)
  def ++(other: Path): Path    = Path(segments ++ other.segments, hasLeadingSlash, other.trailingSlash)

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

  override def toString: String = render
}

object Path {
  val empty: Path = Path(Chunk.empty, hasLeadingSlash = false, trailingSlash = false)
  val root: Path  = Path(Chunk.empty, hasLeadingSlash = true, trailingSlash = false)

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

  def render(path: Path): String = path.render
}
