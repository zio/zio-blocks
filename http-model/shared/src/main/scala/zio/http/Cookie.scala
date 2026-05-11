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

import java.util.Locale

import zio.blocks.chunk.Chunk

final case class RequestCookie(name: String, value: String)

/**
 * Cookie emitted in a `Set-Cookie` response header.
 *
 * The `expires` field is rendered verbatim as the `Expires` attribute and is
 * expected to already be in the HTTP-date format used by RFC 6265, such as
 * `Wed, 21 Oct 2026 07:28:00 GMT`. The `priority` field maps to the non-RFC but
 * commonly used `Priority` attribute, and `isPartitioned` maps to the
 * `Partitioned` attribute. Rendering validates the cookie name and unquoted
 * cookie value and rejects `SameSite=None` unless `Secure` is also set.
 */
final case class ResponseCookie(
  name: String,
  value: String,
  expires: Option[String] = scala.None,
  domain: Option[String] = scala.None,
  path: Option[Path] = scala.None,
  maxAge: Option[Long] = scala.None,
  isSecure: Boolean = false,
  isHttpOnly: Boolean = false,
  sameSite: Option[SameSite] = scala.None,
  isPartitioned: Boolean = false,
  priority: Option[CookiePriority] = scala.None
)

sealed trait SameSite

object SameSite {
  case object Strict extends SameSite
  case object Lax    extends SameSite
  case object None_  extends SameSite
}

/**
 * Represents the `Priority` attribute of a `Set-Cookie` header.
 *
 * Supported values are `Low`, `Medium`, and `High`, which render directly as
 * the corresponding `Priority` attribute values.
 */
sealed trait CookiePriority

object CookiePriority {

  /** The `Priority=Low` cookie attribute value. */
  case object Low extends CookiePriority

  /** The `Priority=Medium` cookie attribute value. */
  case object Medium extends CookiePriority

  /** The `Priority=High` cookie attribute value. */
  case object High extends CookiePriority
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
    val cookieValue = unquote(nameValue.substring(eqIdx + 1).trim)

    if (cookieName.isEmpty) return Left("Empty cookie name")
    validateCookieName(cookieName) match {
      case Left(err) => return Left(err)
      case Right(()) => ()
    }

    var expires: Option[String]          = scala.None
    var domain: Option[String]           = scala.None
    var path: Option[Path]               = scala.None
    var maxAge: Option[Long]             = scala.None
    var isSecure: Boolean                = false
    var isHttpOnly: Boolean              = false
    var sameSite: Option[SameSite]       = scala.None
    var isPartitioned: Boolean           = false
    var priority: Option[CookiePriority] = scala.None

    if (firstSemi >= 0) {
      val attrs = s.substring(firstSemi + 1)
      val parts = attrs.split(';')
      var i     = 0
      while (i < parts.length) {
        val part    = parts(i).trim
        val attrEq  = part.indexOf('=')
        val attrKey =
          if (attrEq < 0) part.toLowerCase(Locale.ROOT) else part.substring(0, attrEq).trim.toLowerCase(Locale.ROOT)
        val attrVal = if (attrEq < 0) "" else part.substring(attrEq + 1).trim

        if (attrKey == "expires") expires = Some(attrVal)
        else if (attrKey == "domain") domain = Some(attrVal)
        else if (attrKey == "path") path = Some(Path(attrVal))
        else if (attrKey == "max-age") {
          try maxAge = Some(attrVal.toLong)
          catch { case _: NumberFormatException => () }
        } else if (attrKey == "secure") isSecure = true
        else if (attrKey == "httponly") isHttpOnly = true
        else if (attrKey == "partitioned") isPartitioned = true
        else if (attrKey == "samesite") {
          attrVal.toLowerCase(Locale.ROOT) match {
            case "strict" => sameSite = Some(SameSite.Strict)
            case "lax"    => sameSite = Some(SameSite.Lax)
            case "none"   => sameSite = Some(SameSite.None_)
            case _        => ()
          }
        } else if (attrKey == "priority") {
          attrVal.toLowerCase(Locale.ROOT) match {
            case "low"    => priority = Some(CookiePriority.Low)
            case "medium" => priority = Some(CookiePriority.Medium)
            case "high"   => priority = Some(CookiePriority.High)
            case _        => ()
          }
        }

        i += 1
      }
    }

    Right(
      ResponseCookie(
        cookieName,
        cookieValue,
        expires,
        domain,
        path,
        maxAge,
        isSecure,
        isHttpOnly,
        sameSite,
        isPartitioned,
        priority
      )
    )
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

  def renderResponse(cookie: ResponseCookie): String =
    renderResponseEither(cookie) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(err)
    }

  def renderResponseEither(cookie: ResponseCookie): Either[String, String] = {
    validateCookieName(cookie.name) match {
      case Left(err) => return Left(err)
      case Right(()) => ()
    }
    validateCookieValue(cookie.value) match {
      case Left(err) => return Left(err)
      case Right(()) => ()
    }
    if (cookie.sameSite.contains(SameSite.None_) && !cookie.isSecure) {
      return Left("SameSite=None cookies must also be Secure")
    }

    val sb = new StringBuilder
    sb.append(cookie.name).append('=').append(cookie.value)

    cookie.expires.foreach { e =>
      sb.append("; Expires=").append(e)
    }
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
    if (cookie.isPartitioned) sb.append("; Partitioned")
    cookie.priority.foreach {
      case CookiePriority.Low    => sb.append("; Priority=Low")
      case CookiePriority.Medium => sb.append("; Priority=Medium")
      case CookiePriority.High   => sb.append("; Priority=High")
    }

    Right(sb.toString)
  }

  private def validateCookieName(name: String): Either[String, Unit] = {
    if (name.isEmpty) return Left("Cookie name cannot be empty")
    var i = 0
    while (i < name.length) {
      val c = name.charAt(i)
      if (
        c <= 0x20 || c >= 0x7f || c == '(' || c == ')' || c == '<' || c == '>' || c == '@' || c == ',' ||
        c == ';' || c == ':' || c == '\\' || c == '"' || c == '/' || c == '[' || c == ']' || c == '?' || c == '=' ||
        c == '{' || c == '}'
      ) return Left(s"Invalid cookie name: $name")
      i += 1
    }
    Right(())
  }

  private def isCookieOctet(c: Char): Boolean =
    c == 0x21 || (c >= 0x23 && c <= 0x2b) || (c >= 0x2d && c <= 0x3a) || (c >= 0x3c && c <= 0x5b) ||
      (c >= 0x5d && c <= 0x7e)

  private def validateCookieValue(value: String): Either[String, Unit] = {
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (!isCookieOctet(c)) return Left("Invalid cookie value")
      i += 1
    }
    Right(())
  }

  private def unquote(value: String): String =
    if (value.length >= 2 && value.charAt(0) == '"' && value.charAt(value.length - 1) == '"')
      value.substring(1, value.length - 1)
    else value
}
