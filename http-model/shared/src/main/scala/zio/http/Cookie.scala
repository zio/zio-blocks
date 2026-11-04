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

/**
 * A cookie as a client sends it in the `cookie` request header: a name and a
 * value, without attributes. Neither field is checked; [[Cookie.renderRequest]]
 * writes them as given.
 */
final case class RequestCookie(name: String, value: String)

/**
 * A cookie that a server sets with a `set-cookie` response header: the name,
 * the value and the attributes that tell the browser where to send it and when
 * to drop it. [[Cookie.renderResponse]] writes it and [[Cookie.parseResponse]]
 * reads it; `Response#addCookie` adds one to a response.
 *
 * {{{
 * val session = ResponseCookie(
 *   name = "session",
 *   value = "abc123",
 *   path = Some(Path("/")),
 *   maxAge = Some(3600L),
 *   isSecure = true,
 *   isHttpOnly = true,
 *   sameSite = Some(SameSite.Lax)
 * )
 *
 * Cookie.renderResponse(session)
 * // session=abc123; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Lax
 * }}}
 *
 * Rendering checks the name and the value and rejects `SameSite=None` without
 * `Secure` (see [[Cookie.renderResponseEither]]). The other attributes are
 * written as given and not checked.
 *
 * @param expires
 *   the `Expires` attribute, written as given, so pass an HTTP date such as
 *   `Wed, 21 Oct 2026 07:28:00 GMT`
 * @param domain
 *   the `Domain` attribute, written as given
 * @param path
 *   the `Path` attribute, written with `Path#render`, without percent-encoding
 * @param maxAge
 *   the `Max-Age` attribute in seconds; `0` asks the browser to delete the
 *   cookie
 * @param sameSite
 *   the `SameSite` attribute; `None` writes no attribute and leaves the
 *   browser's default
 * @param isPartitioned
 *   whether to write the `Partitioned` attribute (CHIPS)
 * @param priority
 *   the `Priority` attribute, which Chromium-based browsers read and RFC 6265
 *   does not define
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#responsecookie ResponseCookie]]
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

/**
 * The `SameSite` attribute of a cookie: whether the browser sends it on
 * requests that come from another site.
 */
sealed trait SameSite

/** The three `SameSite` values. */
object SameSite {

  /** `SameSite=Strict`: sent only on requests from the same site. */
  case object Strict extends SameSite

  /**
   * `SameSite=Lax`: also sent when the user follows a link from another site (a
   * top-level `GET` navigation), but not on cross-site form posts.
   */
  case object Lax extends SameSite

  /**
   * `SameSite=None`: sent on cross-site requests too. Browsers require `Secure`
   * with it, and rendering a cookie without `Secure` fails.
   */
  case object None_ extends SameSite
}

/**
 * The `Priority` attribute of a `set-cookie` header. `Low`, `Medium` and `High`
 * render as `Priority=Low`, `Priority=Medium` and `Priority=High`.
 */
sealed trait CookiePriority

/** The three `Priority` values. */
object CookiePriority {

  /** The `Priority=Low` cookie attribute value. */
  case object Low extends CookiePriority

  /** The `Priority=Medium` cookie attribute value. */
  case object Medium extends CookiePriority

  /** The `Priority=High` cookie attribute value. */
  case object High extends CookiePriority
}

/**
 * Reads and writes the values of the `cookie` and `set-cookie` headers.
 * `cookie` holds the [[RequestCookie]]s a client sends, as `name=value` pairs
 * separated by `; `. `set-cookie` holds one [[ResponseCookie]] with its
 * attributes. `Request#cookies`, `Request#addCookie`, `Response#cookies` and
 * `Response#addCookie` use these functions.
 *
 * {{{
 * Cookie.parseRequest("session=abc123; theme=dark")
 * // Chunk(RequestCookie(session,abc123),RequestCookie(theme,dark))
 *
 * Cookie.renderResponse(ResponseCookie("theme", "dark", maxAge = Some(0L)))
 * // theme=dark; Max-Age=0
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#cookie-management Cookie Management]]
 */
object Cookie {

  /**
   * Parses the value of a `cookie` header. It splits at `;`, trims each part
   * and splits it at the first `=` into a name and a value, both trimmed. Parts
   * without `=` or with an empty name are skipped. Names and values are not
   * checked, quotes around a value are kept, and repeated names are all
   * returned in order. The empty string gives an empty `Chunk`.
   *
   * {{{
   * Cookie.parseRequest("session=abc123;theme; =x; lang=en")
   * // Chunk(RequestCookie(session,abc123),RequestCookie(lang,en))
   * }}}
   */
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

  /**
   * Parses the value of a `set-cookie` header. The text before the first `;` is
   * `name=value`: the name is trimmed and must be a valid cookie name, and the
   * value is trimmed and loses surrounding double quotes but is not checked.
   * The attributes after it match by name in any letter case: `Expires`,
   * `Domain` and `Path` are kept as text (`Path` is not percent-decoded),
   * `Max-Age` is read as a number, `SameSite` and `Priority` as one of their
   * values, and `Secure`, `HttpOnly` and `Partitioned` as flags. A `Max-Age`,
   * `SameSite` or `Priority` value it cannot read, and any unknown attribute,
   * is skipped. `SameSite=None` without `Secure` is accepted.
   *
   * It returns `Left` for the empty string, a first part with no name before
   * `=`, and an invalid name.
   *
   * {{{
   * Cookie.parseResponse("session=abc123; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Lax")
   *   .map(c => (c.name, c.value, c.maxAge, c.sameSite))
   * // Right((session,abc123,Some(3600),Some(Lax)))
   *
   * Cookie.parseResponse("novalue") // Left(Missing cookie name)
   * }}}
   */
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

  /**
   * Renders cookies as the value of a `cookie` header: `name=value` pairs
   * joined with `; `. Names and values are written as given, without any check,
   * so a `;` or `=` in them changes how the header parses. No cookies give the
   * empty string.
   *
   * {{{
   * import zio.blocks.chunk.Chunk
   *
   * Cookie.renderRequest(Chunk(RequestCookie("session", "abc123"), RequestCookie("theme", "dark")))
   * // session=abc123; theme=dark
   * }}}
   */
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

  /**
   * Renders a cookie as the value of a `set-cookie` header, as
   * [[renderResponseEither]] does, but throws where that returns `Left`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the name or the value is invalid, or `SameSite=None` is set without
   *   `Secure`
   */
  def renderResponse(cookie: ResponseCookie): String =
    renderResponseEither(cookie) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(err)
    }

  /**
   * Renders a cookie as the value of a `set-cookie` header: `name=value`, then
   * the attributes that are set, in the order `Expires`, `Domain`, `Path`,
   * `Max-Age`, `Secure`, `HttpOnly`, `SameSite`, `Partitioned`, `Priority`.
   *
   * It returns `Left` when the name is empty or contains a space, a control or
   * non-ASCII character, or one of `()<>@,;:\"/[]?={}`; when the value contains
   * a character RFC 6265 does not allow in a cookie value (a space, `"`, `,`,
   * `;`, `\`, a control or non-ASCII character; so a value in double quotes is
   * rejected too); or when `SameSite=None` is set without `Secure`.
   *
   * `expires`, `domain` and `path` are written without any check, so a `;` in
   * them adds attributes: `domain = Some("shop.example.com; HttpOnly")` writes
   * an `HttpOnly` attribute. This is a known bug; do not pass untrusted text in
   * them.
   *
   * {{{
   * Cookie.renderResponseEither(ResponseCookie("session", "abc 123"))
   * // Left(Invalid cookie value)
   * Cookie.renderResponseEither(ResponseCookie("session", "abc123", sameSite = Some(SameSite.None_)))
   * // Left(SameSite=None cookies must also be Secure)
   * }}}
   */
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

  /** Checks a cookie name against the RFC 6265 token rules. */
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

  /** Checks that every character of a cookie value is a cookie-octet. */
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
