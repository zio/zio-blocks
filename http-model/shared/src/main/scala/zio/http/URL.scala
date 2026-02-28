package zio.http

/**
 * Pure, immutable URL representation.
 *
 * Path segments are stored in decoded form and percent-encoded on output via
 * `encode`. `parse` returns `Left` on invalid input (empty string, malformed
 * port, etc.). Use `/` to append a path segment and `??` to add a query
 * parameter. `encode` renders the full URL string with proper percent-encoding.
 */
final case class URL(
  scheme: Option[Scheme],
  host: Option[String],
  port: Option[Int],
  path: Path,
  queryParams: QueryParams,
  fragment: Option[String]
) {

  def isAbsolute: Boolean = scheme.isDefined
  def isRelative: Boolean = !isAbsolute

  def /(segment: String): URL = copy(path = path / segment)

  def ??(key: String, value: String): URL = copy(queryParams = queryParams.add(key, value))

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

  override def toString: String = encode
}

object URL {

  val root: URL = URL(Some(Scheme.HTTP), Some("localhost"), None, Path.root, QueryParams.empty, None)

  def fromPath(path: Path): URL = URL(None, None, None, path, QueryParams.empty, None)

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
                try port = Some(portStr.toInt)
                catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
            }
          } else {
            host = Some(authority)
          }
        } else {
          val colonIdx = authority.lastIndexOf(':')
          if (colonIdx >= 0) {
            host = Some(authority.substring(0, colonIdx))
            val portStr = authority.substring(colonIdx + 1)
            if (portStr.nonEmpty)
              try port = Some(portStr.toInt)
              catch { case _: NumberFormatException => return Left(s"Invalid port: $portStr") }
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
