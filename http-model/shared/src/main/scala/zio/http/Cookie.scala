package zio.http

import zio.blocks.chunk.Chunk

final case class RequestCookie(name: String, value: String)

final case class ResponseCookie(
  name: String,
  value: String,
  domain: Option[String] = scala.None,
  path: Option[Path] = scala.None,
  maxAge: Option[Long] = scala.None,
  isSecure: Boolean = false,
  isHttpOnly: Boolean = false,
  sameSite: Option[SameSite] = scala.None
)

sealed trait SameSite

object SameSite {
  case object Strict extends SameSite
  case object Lax    extends SameSite
  case object None_  extends SameSite
}

object Cookie {

  def parseRequest(s: String): Chunk[RequestCookie] = {
    if (s.isEmpty) return Chunk.empty[RequestCookie]
    val parts   = s.split(';')
    val builder = Chunk.newBuilder[RequestCookie]
    var i       = 0
    while (i < parts.length) {
      val part = parts(i).trim
      if (part.nonEmpty) {
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val name  = part.substring(0, eqIdx).trim
          val value = part.substring(eqIdx + 1).trim
          builder += RequestCookie(name, value)
        }
      }
      i += 1
    }
    builder.result()
  }

  def parseResponse(s: String): Either[String, ResponseCookie] = {
    if (s.isEmpty) return Left("Empty cookie string")

    val firstSemi = s.indexOf(';')
    val nameValue = if (firstSemi < 0) s else s.substring(0, firstSemi)
    val eqIdx     = nameValue.indexOf('=')
    if (eqIdx <= 0) return Left("Missing cookie name")

    val cookieName  = nameValue.substring(0, eqIdx).trim
    val cookieValue = nameValue.substring(eqIdx + 1).trim

    if (cookieName.isEmpty) return Left("Empty cookie name")

    var domain: Option[String]     = scala.None
    var path: Option[Path]         = scala.None
    var maxAge: Option[Long]       = scala.None
    var isSecure: Boolean          = false
    var isHttpOnly: Boolean        = false
    var sameSite: Option[SameSite] = scala.None

    if (firstSemi >= 0) {
      val attrs = s.substring(firstSemi + 1)
      val parts = attrs.split(';')
      var i     = 0
      while (i < parts.length) {
        val part    = parts(i).trim
        val attrEq  = part.indexOf('=')
        val attrKey = if (attrEq < 0) part.toLowerCase else part.substring(0, attrEq).trim.toLowerCase
        val attrVal = if (attrEq < 0) "" else part.substring(attrEq + 1).trim

        if (attrKey == "domain") domain = Some(attrVal)
        else if (attrKey == "path") path = Some(Path(attrVal))
        else if (attrKey == "max-age") {
          try maxAge = Some(attrVal.toLong)
          catch { case _: NumberFormatException => () }
        } else if (attrKey == "secure") isSecure = true
        else if (attrKey == "httponly") isHttpOnly = true
        else if (attrKey == "samesite") {
          attrVal.toLowerCase match {
            case "strict" => sameSite = Some(SameSite.Strict)
            case "lax"    => sameSite = Some(SameSite.Lax)
            case "none"   => sameSite = Some(SameSite.None_)
            case _        => ()
          }
        }

        i += 1
      }
    }

    Right(ResponseCookie(cookieName, cookieValue, domain, path, maxAge, isSecure, isHttpOnly, sameSite))
  }

  def renderRequest(cookies: Chunk[RequestCookie]): String = {
    if (cookies.isEmpty) return ""
    val sb = new StringBuilder
    var i  = 0
    while (i < cookies.length) {
      if (i > 0) sb.append("; ")
      val c = cookies(i)
      sb.append(c.name).append('=').append(c.value)
      i += 1
    }
    sb.toString
  }

  def renderResponse(cookie: ResponseCookie): String = {
    val sb = new StringBuilder
    sb.append(cookie.name).append('=').append(cookie.value)

    cookie.domain.foreach { d =>
      sb.append("; Domain=").append(d)
    }
    cookie.path.foreach { p =>
      sb.append("; Path=").append(p.render)
    }
    cookie.maxAge.foreach { ma =>
      sb.append("; Max-Age=").append(ma)
    }
    if (cookie.isSecure) sb.append("; Secure")
    if (cookie.isHttpOnly) sb.append("; HttpOnly")
    cookie.sameSite.foreach {
      case SameSite.Strict => sb.append("; SameSite=Strict")
      case SameSite.Lax    => sb.append("; SameSite=Lax")
      case SameSite.None_  => sb.append("; SameSite=None")
    }

    sb.toString
  }
}
