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
import zio.blocks.mediatype.MediaType

/**
 * One HTTP header field as a typed value that knows its name and how to write
 * its value. The built-in headers live in the [[Header]] companion, such as
 * `Header.ContentLength` and `Header.CacheControl`: each is a case class or
 * sealed trait that extends `Header`, and its companion object is the
 * [[Header.Codec]] that parses it. Use [[Header.Custom]] for a header that has
 * no typed model.
 *
 * {{{
 * val length = Header.ContentLength(512)
 *
 * length.headerName    // content-length
 * length.renderedValue // 512
 * Headers.empty.add(length).rawGet("Content-Length") // Some(512)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/headers/ Header]]
 */
trait Header {

  /**
   * The header name. Every built-in header returns its lower-case wire name,
   * such as `content-length`; a [[Header.Custom]] returns the name it was
   * given.
   */
  def headerName: String

  /** The header value in wire format, which `Headers#add` stores. */
  def renderedValue: String
}

/**
 * The built-in typed headers and the type classes behind them. Each built-in
 * header's companion object is a [[Header.Typed]] codec, so a name such as
 * `Header.ContentLength` is both the type of the value and the codec that
 * [[Headers#get]] takes.
 *
 * {{{
 * val headers = Headers("content-length" -> "2048", "cache-control" -> "max-age=600")
 *
 * headers.get(Header.ContentLength) // Some(ContentLength(2048))
 * headers.get(Header.CacheControl)  // Some(MaxAge(600))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/headers/ Header]]
 */
object Header {

  /**
   * A type class that reads and writes one HTTP header as a value of type `A`:
   * it names the header and converts between `A` and the header's string value.
   * [[Headers#get]], [[Headers#getAll]], `Request#header` and `Response#header`
   * take a codec. Write your own to read a header that the built-ins do not
   * model; `A` does not have to extend [[Header]].
   *
   * {{{
   * final case class TenantId(value: String)
   *
   * object TenantIdCodec extends Header.Codec[TenantId] {
   *   def name: String = "x-tenant-id"
   *   def parse(value: String): Either[String, TenantId] =
   *     if (value.isEmpty) Left("x-tenant-id must not be empty") else Right(TenantId(value))
   *   def render(value: TenantId): String = value.value
   * }
   *
   * Headers("X-Tenant-Id" -> "shop-7").get(TenantIdCodec) // Some(TenantId(shop-7))
   * Headers("x-tenant-id" -> "").get(TenantIdCodec)       // None
   * TenantIdCodec.parse("")                               // Left(x-tenant-id must not be empty)
   * }}}
   *
   * Keep a codec in a `val` or an `object`. `Headers` caches a parsed value per
   * entry and per codec instance, compared by reference, so a codec built anew
   * for every read never reuses the cache.
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/headers/#a-custom-headercodec A Custom Header.Codec]]
   */
  trait Codec[A] {

    /**
     * The name of the header this codec reads. `Headers` lower-cases it before
     * comparing it with the stored names, so its letter case does not matter.
     */
    def name: String

    /**
     * Parses a raw header value, or returns `Left` with a message that says
     * what was expected. `Headers#get` and `Headers#getAll` skip an entry whose
     * parse fails and drop the message.
     */
    def parse(value: String): Either[String, A]

    /** Renders a value as a raw header value. */
    def render(value: A): String
  }

  /**
   * A [[Header.Codec]] whose value type is itself a [[Header]]. Every built-in
   * header's companion object is one, and [[Headers#getLast]] takes one.
   */
  trait Typed[H <: Header] extends Codec[H]

  /**
   * A header with a name and a value given as plain strings, for a header that
   * has no typed model. It renders `rawValue` unchanged. `Custom` checks
   * neither field; `Headers#add` validates both and stores the name in lower
   * case. It has no codec, so read it back with [[Headers#rawGet]].
   *
   * {{{
   * val tenant = Header.Custom("X-Tenant-Id", "shop-7")
   *
   * Headers.empty.add(tenant).toList // List((x-tenant-id,shop-7))
   * }}}
   */
  final case class Custom(override val headerName: String, rawValue: String) extends Header {

    /** Returns `rawValue`. */
    def renderedValue: String = rawValue
  }

  /**
   * The codec of the `cookie` request header: the [[CookieHeader]] companion,
   * typed as a codec. It keeps the whole value as one string; `Request#cookies`
   * and `Cookie.parseRequest` split it into cookies.
   */
  val Cookie: Typed[CookieHeader] = CookieHeader

  /**
   * The codec of the `set-cookie` response header: the [[SetCookieHeader]]
   * companion, typed as a codec. It keeps the whole value as one string;
   * `Response#cookies` and `Cookie.parseResponse` read the cookie in it.
   */
  val SetCookie: Typed[SetCookieHeader] = SetCookieHeader

  /**
   * The `Authorization` request header: the credentials a client sends to
   * authenticate. Each case is one scheme: `Authorization.Basic`,
   * `Authorization.Bearer`, `Authorization.Digest`, or `Authorization.Unparsed`
   * for any other.
   */
  sealed trait Authorization extends zio.http.Header {

    /** Returns the name of this header, `Authorization.name`. */
    def headerName: String = Authorization.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Authorization.render` returns.
     */
    def renderedValue: String = Authorization.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[Authorization]]: it parses and renders the
   * `authorization` header.
   */
  object Authorization extends Typed[Authorization] {

    /** The header name, `authorization`. */
    val name: String = "authorization"

    /**
     * Credentials for the `Basic` scheme. It renders as `Basic ` followed by
     * the Base64 encoding of `username:password` in UTF-8. Its `toString`
     * includes the password, so keep a value out of logs (a known bug).
     */
    final case class Basic(username: String, password: String) extends Authorization

    /** A bearer token. It renders as `Bearer ` followed by `token`. */
    final case class Bearer(token: String) extends Authorization

    /**
     * The parameters of the `Digest` scheme, such as `username` and `realm`. It
     * renders as `Digest ` followed by the `key="value"` pairs joined with
     * `, `, in the iteration order of `params`. The values are neither escaped
     * on render nor unescaped on parse, and a comma inside a quoted value
     * splits it (a known bug).
     */
    final case class Digest(params: Map[String, String]) extends Authorization

    /**
     * Credentials for any scheme other than `Basic`, `Bearer` and `Digest`: the
     * scheme name and the rest of the value, unchanged. It renders as the
     * scheme, a space and `params`.
     */
    final case class Unparsed(scheme: String, params: String) extends Authorization

    /**
     * Parses a `authorization` value. It trims the value and splits it at the
     * first space into a scheme and the credentials; the scheme is matched as
     * `basic`, `bearer` or `digest` in any letter case, and any other scheme
     * gives `Unparsed`. An empty value gives
     * `Left("Empty authorization header")`, and a value without a space gives
     * `Left("Invalid authorization header: <value>")`. For `Basic`, the
     * credentials must be Base64 text that decodes to `username:password`,
     * split at the first colon; otherwise the result is
     * `Left("Invalid base64 in basic authorization")` or
     * `Left("Basic authorization missing colon separator")`. For `Digest`, the
     * credentials are split on commas into `key=value` pairs, and a pair
     * without `=` is dropped.
     */
    def parse(value: String): Either[String, Authorization] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty authorization header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) return Left(s"Invalid authorization header: $trimmed")
      val scheme = trimmed.substring(0, spaceIdx)
      val rest   = trimmed.substring(spaceIdx + 1).trim
      scheme.toLowerCase match {
        case "basic"  => parseBasic(rest)
        case "bearer" => Right(Bearer(rest))
        case "digest" => Right(Digest(parseParams(rest)))
        case _        => Right(Unparsed(scheme, rest))
      }
    }

    /**
     * Writes `Basic <base64>`, `Bearer <token>`, `Digest key="value", ...` or
     * `<scheme> <params>`.
     */
    def render(h: Authorization): String = h match {
      case Basic(username, password) =>
        val encoded = java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes("UTF-8"))
        s"Basic $encoded"
      case Bearer(token)            => s"Bearer $token"
      case Digest(params)           => "Digest " + renderParams(params)
      case Unparsed(scheme, params) => s"$scheme $params"
    }

    private def parseBasic(encoded: String): Either[String, Authorization] =
      try {
        val decoded  = new String(java.util.Base64.getDecoder.decode(encoded), "UTF-8")
        val colonIdx = decoded.indexOf(':')
        if (colonIdx < 0) Left("Basic authorization missing colon separator")
        else Right(Basic(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1)))
      } catch {
        case _: IllegalArgumentException => Left("Invalid base64 in basic authorization")
      }

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }

    private def renderParams(params: Map[String, String]): String =
      params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")
  }

  /**
   * The `Proxy-Authorization` request header: the credentials a client sends to
   * authenticate with a proxy. It has the same cases as [[Authorization]], one
   * per scheme.
   */
  sealed trait ProxyAuthorization extends zio.http.Header {

    /** Returns the name of this header, `ProxyAuthorization.name`. */
    def headerName: String = ProxyAuthorization.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ProxyAuthorization.render` returns.
     */
    def renderedValue: String = ProxyAuthorization.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ProxyAuthorization]]: it parses and
   * renders the `proxy-authorization` header.
   */
  object ProxyAuthorization extends Typed[ProxyAuthorization] {

    /** The header name, `proxy-authorization`. */
    val name: String = "proxy-authorization"

    /**
     * Credentials for the `Basic` scheme. It renders as `Basic ` followed by
     * the Base64 encoding of `username:password` in UTF-8. Its `toString`
     * includes the password, so keep a value out of logs (a known bug).
     */
    final case class Basic(username: String, password: String) extends ProxyAuthorization

    /** A bearer token. It renders as `Bearer ` followed by `token`. */
    final case class Bearer(token: String) extends ProxyAuthorization

    /**
     * The parameters of the `Digest` scheme, such as `username` and `realm`. It
     * renders as `Digest ` followed by the `key="value"` pairs joined with
     * `, `, in the iteration order of `params`. The values are neither escaped
     * on render nor unescaped on parse, and a comma inside a quoted value
     * splits it (a known bug).
     */
    final case class Digest(params: Map[String, String]) extends ProxyAuthorization

    /**
     * Credentials for any scheme other than `Basic`, `Bearer` and `Digest`: the
     * scheme name and the rest of the value, unchanged. It renders as the
     * scheme, a space and `params`.
     */
    final case class Unparsed(scheme: String, params: String) extends ProxyAuthorization

    /**
     * Parses a `proxy-authorization` value. It trims the value and splits it at
     * the first space into a scheme and the credentials; the scheme is matched
     * as `basic`, `bearer` or `digest` in any letter case, and any other scheme
     * gives `Unparsed`. An empty value gives
     * `Left("Empty proxy-authorization header")`, and a value without a space
     * gives `Left("Invalid proxy-authorization header: <value>")`. For `Basic`,
     * the credentials must be Base64 text that decodes to `username:password`,
     * split at the first colon; otherwise the result is
     * `Left("Invalid base64 in basic proxy-authorization")` or
     * `Left("Basic proxy-authorization missing colon separator")`. For
     * `Digest`, the credentials are split on commas into `key=value` pairs, and
     * a pair without `=` is dropped.
     */
    def parse(value: String): Either[String, ProxyAuthorization] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty proxy-authorization header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) return Left(s"Invalid proxy-authorization header: $trimmed")
      val scheme = trimmed.substring(0, spaceIdx)
      val rest   = trimmed.substring(spaceIdx + 1).trim
      scheme.toLowerCase match {
        case "basic"  => parseBasic(rest)
        case "bearer" => Right(Bearer(rest))
        case "digest" => Right(Digest(parseParams(rest)))
        case _        => Right(Unparsed(scheme, rest))
      }
    }

    /**
     * Writes `Basic <base64>`, `Bearer <token>`, `Digest key="value", ...` or
     * `<scheme> <params>`.
     */
    def render(h: ProxyAuthorization): String = h match {
      case Basic(username, password) =>
        val encoded = java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes("UTF-8"))
        s"Basic $encoded"
      case Bearer(token)            => s"Bearer $token"
      case Digest(params)           => "Digest " + renderParams(params)
      case Unparsed(scheme, params) => s"$scheme $params"
    }

    private def parseBasic(encoded: String): Either[String, ProxyAuthorization] =
      try {
        val decoded  = new String(java.util.Base64.getDecoder.decode(encoded), "UTF-8")
        val colonIdx = decoded.indexOf(':')
        if (colonIdx < 0) Left("Basic proxy-authorization missing colon separator")
        else Right(Basic(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1)))
      } catch {
        case _: IllegalArgumentException => Left("Invalid base64 in basic proxy-authorization")
      }

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }

    private def renderParams(params: Map[String, String]): String =
      params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")
  }

  /**
   * The `WWW-Authenticate` response header: the challenge a server sends to ask
   * for credentials. `scheme` is the authentication scheme, such as `Basic`,
   * and `params` holds its parameters, such as `realm`.
   */
  final case class WWWAuthenticate(scheme: String, params: Map[String, String]) extends zio.http.Header {

    /** Returns the name of this header, `WWWAuthenticate.name`. */
    def headerName: String = WWWAuthenticate.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `WWWAuthenticate.render` returns.
     */
    def renderedValue: String = WWWAuthenticate.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[WWWAuthenticate]]: it parses and renders
   * the `www-authenticate` header.
   */
  object WWWAuthenticate extends Typed[WWWAuthenticate] {

    /** The header name, `www-authenticate`. */
    val name: String = "www-authenticate"

    /**
     * Parses a challenge. It trims the value; an empty value gives
     * `Left("Empty www-authenticate header")`. A value without a space becomes
     * a scheme with no parameters. Otherwise the text before the first space is
     * the scheme, and the rest is split on commas into `key=value` pairs with
     * surrounding double quotes removed; a pair without `=` is dropped, and a
     * comma inside a quoted value splits it (a known bug).
     */
    def parse(value: String): Either[String, WWWAuthenticate] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty www-authenticate header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) Right(WWWAuthenticate(trimmed, Map.empty))
      else {
        val scheme = trimmed.substring(0, spaceIdx)
        val rest   = trimmed.substring(spaceIdx + 1).trim
        Right(WWWAuthenticate(scheme, parseParams(rest)))
      }
    }

    /**
     * Writes the scheme alone when `params` is empty, otherwise the scheme, a
     * space and the `key="value"` pairs joined with `, `. The values are quoted
     * without escaping (a known bug).
     */
    def render(h: WWWAuthenticate): String =
      if (h.params.isEmpty) h.scheme
      else h.scheme + " " + h.params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }
  }

  /**
   * The `Proxy-Authenticate` response header: the challenge a proxy sends to
   * ask for credentials. `scheme` is the authentication scheme, and `params`
   * holds its parameters.
   */
  final case class ProxyAuthenticate(scheme: String, params: Map[String, String]) extends zio.http.Header {

    /** Returns the name of this header, `ProxyAuthenticate.name`. */
    def headerName: String = ProxyAuthenticate.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ProxyAuthenticate.render` returns.
     */
    def renderedValue: String = ProxyAuthenticate.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ProxyAuthenticate]]: it parses and renders
   * the `proxy-authenticate` header.
   */
  object ProxyAuthenticate extends Typed[ProxyAuthenticate] {

    /** The header name, `proxy-authenticate`. */
    val name: String = "proxy-authenticate"

    /**
     * Parses a challenge. It trims the value; an empty value gives
     * `Left("Empty proxy-authenticate header")`. A value without a space
     * becomes a scheme with no parameters. Otherwise the text before the first
     * space is the scheme, and the rest is split on commas into `key=value`
     * pairs with surrounding double quotes removed; a pair without `=` is
     * dropped, and a comma inside a quoted value splits it (a known bug).
     */
    def parse(value: String): Either[String, ProxyAuthenticate] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty proxy-authenticate header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) Right(ProxyAuthenticate(trimmed, Map.empty))
      else {
        val scheme = trimmed.substring(0, spaceIdx)
        val rest   = trimmed.substring(spaceIdx + 1).trim
        Right(ProxyAuthenticate(scheme, parseParams(rest)))
      }
    }

    /**
     * Writes the scheme alone when `params` is empty, otherwise the scheme, a
     * space and the `key="value"` pairs joined with `, `. The values are quoted
     * without escaping (a known bug).
     */
    def render(h: ProxyAuthenticate): String =
      if (h.params.isEmpty) h.scheme
      else h.scheme + " " + h.params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }
  }

  /**
   * The `Content-Type` header: the media type of the body, with its optional
   * charset and boundary, held in `value`.
   */
  final case class ContentType(value: zio.http.ContentType) extends zio.http.Header {

    /** Returns the name of this header, `ContentType.name`. */
    def headerName: String = ContentType.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentType.render` returns.
     */
    def renderedValue: String = value.render
  }

  /**
   * The [[Header.Typed]] codec for [[ContentType]]: it parses and renders the
   * `content-type` header.
   */
  object ContentType extends Typed[ContentType] {

    /** The header name, `content-type`. */
    val name: String = "content-type"

    /**
     * Parses the value with the content type parser and wraps the result. A
     * failure returns that parser's `Left`, for example
     * `Left("Invalid media type: must contain '/' separator")`.
     */
    def parse(value: String): Either[String, ContentType] =
      zio.http.ContentType.parse(value).map(ct => ContentType(ct))

    /** Writes the content type, such as `text/html`. */
    def render(h: ContentType): String = h.renderedValue
  }

  /** The `Content-Length` header: the size of the body in bytes. */
  final case class ContentLength(length: Long) extends zio.http.Header {

    /** Returns the name of this header, `ContentLength.name`. */
    def headerName: String = ContentLength.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentLength.render` returns.
     */
    def renderedValue: String = length.toString
  }

  /**
   * The [[Header.Typed]] codec for [[ContentLength]]: it parses and renders the
   * `content-length` header.
   */
  object ContentLength extends Typed[ContentLength] {

    /** The header name, `content-length`. */
    val name: String = "content-length"

    /**
     * Parses the value as a `Long`. It does not trim, so a value with spaces
     * gives `Left`. A negative number gives
     * `Left("Invalid content-length: <number>")`, and a value that is not an
     * integer gives `Left("Invalid content-length: <value>")`.
     */
    def parse(value: String): Either[String, ContentLength] =
      try {
        val l = value.toLong
        if (l < 0) Left(s"Invalid content-length: $l")
        else Right(ContentLength(l))
      } catch {
        case _: NumberFormatException => Left(s"Invalid content-length: $value")
      }

    /** Writes `length` in decimal. */
    def render(h: ContentLength): String = h.renderedValue
  }

  /**
   * The `Content-Encoding` header: the codings applied to the body. One coding
   * is a case object; several codings, in the order listed, are a `Multiple`.
   */
  sealed trait ContentEncoding extends zio.http.Header {

    /** Returns the name of this header, `ContentEncoding.name`. */
    def headerName: String = ContentEncoding.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentEncoding.render` returns.
     */
    def renderedValue: String = ContentEncoding.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ContentEncoding]]: it parses and renders
   * the `content-encoding` header.
   */
  object ContentEncoding extends Typed[ContentEncoding] {

    /** The header name, `content-encoding`. */
    val name: String = "content-encoding"

    /** The `gzip` coding. */
    case object GZip extends ContentEncoding

    /** The `deflate` coding. */
    case object Deflate extends ContentEncoding

    /** The `br` (Brotli) coding. */
    case object Br extends ContentEncoding

    /** The `compress` coding. */
    case object Compress extends ContentEncoding

    /** The `identity` coding: no encoding. */
    case object Identity extends ContentEncoding

    /** Several codings, in the order given. */
    final case class Multiple(values: Chunk[ContentEncoding]) extends ContentEncoding

    private def findEncoding(value: String): Option[ContentEncoding] =
      value.trim.toLowerCase match {
        case "gzip"     => Some(GZip)
        case "deflate"  => Some(Deflate)
        case "br"       => Some(Br)
        case "compress" => Some(Compress)
        case "identity" => Some(Identity)
        case _          => None
      }

    /**
     * Parses a comma-separated list. It trims each item and matches `gzip`,
     * `deflate`, `br`, `compress` or `identity` in any letter case; an item
     * that matches none of them is dropped. One match gives that coding,
     * several give `Multiple`, and none gives
     * `Left("Invalid content-encoding: <value>")`.
     */
    def parse(value: String): Either[String, ContentEncoding] = {
      val parts  = value.split(',')
      val parsed = Chunk.fromArray(parts.flatMap(s => findEncoding(s)))
      if (parsed.isEmpty) Left(s"Invalid content-encoding: $value")
      else if (parsed.length == 1) Right(parsed(0))
      else Right(Multiple(parsed))
    }

    /**
     * Writes the coding name in lower case; `Multiple` joins its names with
     * `, `.
     */
    def render(h: ContentEncoding): String = h match {
      case GZip        => "gzip"
      case Deflate     => "deflate"
      case Br          => "br"
      case Compress    => "compress"
      case Identity    => "identity"
      case Multiple(v) => v.map(render).mkString(", ")
    }
  }

  /**
   * The `Content-Disposition` header: whether the body shows inline, downloads
   * as an attachment, or is one part of a multipart form, with an optional file
   * name.
   */
  sealed trait ContentDisposition extends zio.http.Header {

    /** Returns the name of this header, `ContentDisposition.name`. */
    def headerName: String = ContentDisposition.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentDisposition.render` returns.
     */
    def renderedValue: String = ContentDisposition.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ContentDisposition]]: it parses and
   * renders the `content-disposition` header.
   */
  object ContentDisposition extends Typed[ContentDisposition] {

    /** The header name, `content-disposition`. */
    val name: String = "content-disposition"

    /** `inline` without a file name. */
    val inline: ContentDisposition = Inline(None)

    /** `attachment` without a file name. */
    val attachment: ContentDisposition = Attachment(None)

    /** `inline` with the given file name. */
    def inline(filename: String): ContentDisposition = Inline(Some(filename))

    /** `attachment` with the given file name. */
    def attachment(filename: String): ContentDisposition = Attachment(Some(filename))

    /** A `form-data` part for the field `name`, without a file name. */
    def formData(name: String): ContentDisposition = FormData(name, None)

    /** A `form-data` part for the field `name`, with the given file name. */
    def formData(name: String, filename: String): ContentDisposition = FormData(name, Some(filename))

    /** A download, with an optional file name. */
    final case class Attachment(filename: Option[String]) extends ContentDisposition

    /** A body shown in place, with an optional file name. */
    final case class Inline(filename: Option[String]) extends ContentDisposition

    /**
     * One part of a multipart form: the form field `name` and an optional file
     * name.
     */
    final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

    private val AttachmentRegex         = """attachment;\s*filename="(.*)"""".r
    private val InlineRegex             = """inline;\s*filename="(.*)"""".r
    private val FormDataRegex           = """form-data;\s*name="(.*)";\s*filename="(.*)"""".r
    private val FormDataNoFileNameRegex = """form-data;\s*name="(.*)"""".r

    /**
     * Parses a value by its start, after trimming: `attachment`, `inline` or
     * `form-data` in lower case. For `attachment` and `inline`,
     * `filename="..."` after a semicolon gives the file name; without it the
     * file name is `None`. A `form-data` value needs `name="..."` and may add
     * `filename="..."`; without a name it gives
     * `Left("Invalid form-data content disposition")`. Any other value gives
     * `Left("Invalid content-disposition: <value>")`.
     */
    def parse(value: String): Either[String, ContentDisposition] = {
      val trimmed = value.trim
      if (trimmed.startsWith("attachment")) {
        Right(trimmed match {
          case AttachmentRegex(filename) => Attachment(Some(filename))
          case _                         => Attachment(None)
        })
      } else if (trimmed.startsWith("inline")) {
        Right(trimmed match {
          case InlineRegex(filename) => Inline(Some(filename))
          case _                     => Inline(None)
        })
      } else if (trimmed.startsWith("form-data")) {
        trimmed match {
          case FormDataRegex(name, filename) => Right(FormData(name, Some(filename)))
          case FormDataNoFileNameRegex(name) => Right(FormData(name, None))
          case _                             => Left("Invalid form-data content disposition")
        }
      } else {
        Left(s"Invalid content-disposition: $trimmed")
      }
    }

    /**
     * Writes `attachment`, `inline` or `form-data; name="..."`, each with
     * `; filename="..."` when there is a file name. The names are quoted
     * without escaping.
     */
    def render(h: ContentDisposition): String = h match {
      case Attachment(Some(f))  => s"""attachment; filename=\"$f\""""
      case Attachment(None)     => "attachment"
      case Inline(Some(f))      => s"""inline; filename=\"$f\""""
      case Inline(None)         => "inline"
      case FormData(n, Some(f)) => s"""form-data; name=\"$n\"; filename=\"$f\""""
      case FormData(n, None)    => s"""form-data; name=\"$n\""""
    }
  }

  /**
   * The `Content-Language` header: the language of the body, held as the text
   * `language`.
   */
  final case class ContentLanguage(language: String) extends zio.http.Header {

    /** Returns the name of this header, `ContentLanguage.name`. */
    def headerName: String = ContentLanguage.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentLanguage.render` returns.
     */
    def renderedValue: String = language
  }

  /**
   * The [[Header.Typed]] codec for [[ContentLanguage]]: it parses and renders
   * the `content-language` header.
   */
  object ContentLanguage extends Typed[ContentLanguage] {

    /** The header name, `content-language`. */
    val name: String = "content-language"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, ContentLanguage] = Right(ContentLanguage(value))

    /** Writes `language` unchanged. */
    def render(h: ContentLanguage): String = h.renderedValue
  }

  /**
   * The `Content-Location` header: an alternate location for the body, held as
   * the text `location`.
   */
  final case class ContentLocation(location: String) extends zio.http.Header {

    /** Returns the name of this header, `ContentLocation.name`. */
    def headerName: String = ContentLocation.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentLocation.render` returns.
     */
    def renderedValue: String = location
  }

  /**
   * The [[Header.Typed]] codec for [[ContentLocation]]: it parses and renders
   * the `content-location` header.
   */
  object ContentLocation extends Typed[ContentLocation] {

    /** The header name, `content-location`. */
    val name: String = "content-location"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, ContentLocation] = Right(ContentLocation(value))

    /** Writes `location` unchanged. */
    def render(h: ContentLocation): String = h.renderedValue
  }

  /**
   * The `Content-Range` header: which part of a resource the body holds. `unit`
   * is usually `bytes`, `range` is the first and last position when known, and
   * `size` is the total length when known.
   */
  final case class ContentRange(unit: String, range: Option[(Long, Long)], size: Option[Long]) extends zio.http.Header {

    /** Returns the name of this header, `ContentRange.name`. */
    def headerName: String = ContentRange.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentRange.render` returns.
     */
    def renderedValue: String = ContentRange.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ContentRange]]: it parses and renders the
   * `content-range` header.
   */
  object ContentRange extends Typed[ContentRange] {

    /** The header name, `content-range`. */
    val name: String = "content-range"

    private val StartEndTotalRegex = """(\w+)\s+(\d+)-(\d+)/(\d+)""".r
    private val StartEndRegex      = """(\w+)\s+(\d+)-(\d+)/\*""".r
    private val TotalOnlyRegex     = """(\w+)\s+\*/(\d+)""".r

    /**
     * Parses `unit first-last/total`, `unit first-last/` followed by an
     * asterisk for an unknown total, or `unit ` followed by an asterisk, a
     * slash and the total for an unknown range, such as `bytes 0-99/1000`. It
     * trims the value. Any other form, including one with both an unknown range
     * and an unknown total, gives `Left("Invalid content-range: <value>")`.
     */
    def parse(value: String): Either[String, ContentRange] =
      value.trim match {
        case StartEndTotalRegex(unit, start, end, total) =>
          Right(ContentRange(unit, Some((start.toLong, end.toLong)), Some(total.toLong)))
        case StartEndRegex(unit, start, end) =>
          Right(ContentRange(unit, Some((start.toLong, end.toLong)), None))
        case TotalOnlyRegex(unit, total) =>
          Right(ContentRange(unit, None, Some(total.toLong)))
        case _ =>
          Left(s"Invalid content-range: $value")
      }

    /**
     * Writes `unit first-last/total`, using an asterisk for a missing total or
     * a missing range. With neither it writes the unit, a space, an asterisk, a
     * slash and an asterisk, a form that `parse` does not accept.
     */
    def render(h: ContentRange): String = (h.range, h.size) match {
      case (Some((start, end)), Some(total)) => s"${h.unit} $start-$end/$total"
      case (Some((start, end)), None)        => s"${h.unit} $start-$end/*"
      case (None, Some(total))               => s"${h.unit} */$total"
      case (None, None)                      => s"${h.unit} */*"
    }
  }

  /**
   * The `Content-Security-Policy` header: the policy that tells the browser
   * which sources it may load. `directives` holds the whole policy text.
   */
  final case class ContentSecurityPolicy(directives: String) extends zio.http.Header {

    /** Returns the name of this header, `ContentSecurityPolicy.name`. */
    def headerName: String = ContentSecurityPolicy.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentSecurityPolicy.render` returns.
     */
    def renderedValue: String = directives
  }

  /**
   * The [[Header.Typed]] codec for [[ContentSecurityPolicy]]: it parses and
   * renders the `content-security-policy` header.
   */
  object ContentSecurityPolicy extends Typed[ContentSecurityPolicy] {

    /** The header name, `content-security-policy`. */
    val name: String = "content-security-policy"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, ContentSecurityPolicy] = Right(ContentSecurityPolicy(value))

    /** Writes `directives` unchanged. */
    def render(h: ContentSecurityPolicy): String = h.renderedValue
  }

  /**
   * The `Content-Transfer-Encoding` header: how the body of a multipart part is
   * encoded for transfer.
   */
  sealed trait ContentTransferEncoding extends zio.http.Header {

    /** Returns the name of this header, `ContentTransferEncoding.name`. */
    def headerName: String = ContentTransferEncoding.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentTransferEncoding.render` returns.
     */
    def renderedValue: String = ContentTransferEncoding.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ContentTransferEncoding]]: it parses and
   * renders the `content-transfer-encoding` header.
   */
  object ContentTransferEncoding extends Typed[ContentTransferEncoding] {

    /** The header name, `content-transfer-encoding`. */
    val name: String = "content-transfer-encoding"

    /** The `7bit` encoding. */
    case object SevenBit extends ContentTransferEncoding

    /** The `8bit` encoding. */
    case object EightBit extends ContentTransferEncoding

    /** The `binary` encoding. */
    case object Binary extends ContentTransferEncoding

    /** The `quoted-printable` encoding. */
    case object QuotedPrintable extends ContentTransferEncoding

    /** The `base64` encoding. */
    case object Base64 extends ContentTransferEncoding

    /**
     * Parses one encoding name: it trims the value, lower-cases it and matches
     * `7bit`, `8bit`, `binary`, `quoted-printable` or `base64`. Any other value
     * gives `Left("Invalid content-transfer-encoding: <value>")`, with the
     * trimmed, lower-cased text.
     */
    def parse(value: String): Either[String, ContentTransferEncoding] =
      value.trim.toLowerCase match {
        case "7bit"             => Right(SevenBit)
        case "8bit"             => Right(EightBit)
        case "binary"           => Right(Binary)
        case "quoted-printable" => Right(QuotedPrintable)
        case "base64"           => Right(Base64)
        case other              => Left(s"Invalid content-transfer-encoding: $other")
      }

    /** Writes the lower-case encoding name. */
    def render(h: ContentTransferEncoding): String = h match {
      case SevenBit        => "7bit"
      case EightBit        => "8bit"
      case Binary          => "binary"
      case QuotedPrintable => "quoted-printable"
      case Base64          => "base64"
    }
  }

  /**
   * The `Content-MD5` header: a digest of the body, held as the text `value`.
   */
  final case class ContentMd5(value: String) extends zio.http.Header {

    /** Returns the name of this header, `ContentMd5.name`. */
    def headerName: String = ContentMd5.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentMd5.render` returns.
     */
    def renderedValue: String = value
  }

  /**
   * The [[Header.Typed]] codec for [[ContentMd5]]: it parses and renders the
   * `content-md5` header.
   */
  object ContentMd5 extends Typed[ContentMd5] {

    /** The header name, `content-md5`. */
    val name: String = "content-md5"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, ContentMd5] = Right(ContentMd5(value))

    /** Writes `value` unchanged. */
    def render(h: ContentMd5): String = h.renderedValue
  }

  /**
   * The `Content-Base` header: the base URI for relative links in the body,
   * held as the text `uri`.
   */
  final case class ContentBase(uri: String) extends zio.http.Header {

    /** Returns the name of this header, `ContentBase.name`. */
    def headerName: String = ContentBase.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ContentBase.render` returns.
     */
    def renderedValue: String = uri
  }

  /**
   * The [[Header.Typed]] codec for [[ContentBase]]: it parses and renders the
   * `content-base` header.
   */
  object ContentBase extends Typed[ContentBase] {

    /** The header name, `content-base`. */
    val name: String = "content-base"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, ContentBase] = Right(ContentBase(value))

    /** Writes `uri` unchanged. */
    def render(h: ContentBase): String = h.renderedValue
  }

  /**
   * The `Host` request header: the host name of the target, with an optional
   * port.
   */
  final case class Host(host: String, port: Option[Int]) extends zio.http.Header {

    /** Returns the name of this header, `Host.name`. */
    def headerName: String = Host.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Host.render` returns.
     */
    def renderedValue: String = port match {
      case Some(p) => host + ":" + p
      case None    => host
    }
  }

  /**
   * The [[Header.Typed]] codec for [[Host]]: it parses and renders the `host`
   * header.
   */
  object Host extends Typed[Host] {

    /** The header name, `host`. */
    val name: String = "host"

    /**
     * Parses `host` or `host:port`, splitting at the last colon. It does not
     * trim. An empty value, or an empty host part, gives
     * `Left("Invalid host: cannot be empty")`; a port that is not an integer
     * from 0 to 65535 gives `Left("Invalid port: <text>")`. An IPv6 literal
     * with a port, such as `[::1]:80`, parses, but one without a port does not,
     * because its last colon is read as the port separator.
     */
    def parse(value: String): Either[String, Host] = {
      if (value.isEmpty) return Left("Invalid host: cannot be empty")
      val colonIdx = value.lastIndexOf(':')
      if (colonIdx < 0) Right(Host(value, None))
      else {
        val hostPart = value.substring(0, colonIdx)
        if (hostPart.isEmpty) return Left("Invalid host: cannot be empty")
        val portStr = value.substring(colonIdx + 1)
        try {
          val p = portStr.toInt
          if (p < 0 || p > 65535) Left(s"Invalid port: $p")
          else Right(Host(hostPart, Some(p)))
        } catch {
          case _: NumberFormatException => Left(s"Invalid port: $portStr")
        }
      }
    }

    /** Writes `host`, or `host:port` when there is a port. */
    def render(h: Host): String = h.renderedValue
  }

  /**
   * The `Location` response header: the URI to go to, for example in a
   * redirect. `uri` holds the text.
   */
  final case class Location(uri: String) extends zio.http.Header {

    /** Returns the name of this header, `Location.name`. */
    def headerName: String = Location.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Location.render` returns.
     */
    def renderedValue: String = uri
  }

  /**
   * The [[Header.Typed]] codec for [[Location]]: it parses and renders the
   * `location` header.
   */
  object Location extends Typed[Location] {

    /** The header name, `location`. */
    val name: String = "location"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Location] = Right(Location(value.trim))

    /** Writes `uri` unchanged. */
    def render(h: Location): String = h.renderedValue
  }

  /**
   * The `Origin` request header: where a request comes from, as a
   * `scheme://host[:port]` origin or the opaque `null` origin.
   */
  sealed trait Origin extends zio.http.Header {

    /** Returns the name of this header, `Origin.name`. */
    def headerName: String = Origin.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Origin.render` returns.
     */
    def renderedValue: String = Origin.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[Origin]]: it parses and renders the
   * `origin` header.
   */
  object Origin extends Typed[Origin] {

    /** The header name, `origin`. */
    val name: String = "origin"

    /** The opaque origin, written `null`. */
    case object Null_ extends Origin

    /** An origin with a scheme, a host and an optional port. */
    final case class Value(scheme: String, host: String, port: Option[Int]) extends Origin

    /**
     * Parses `null` in any letter case, or `scheme://host` with an optional
     * `:port` after the host, splitting at the last colon after `://`. It trims
     * the value. A value without `://` gives `Left("Invalid origin: <value>")`,
     * and a port that is not an integer from 0 to 65535 gives
     * `Left("Invalid origin port: <text>")`.
     */
    def parse(value: String): Either[String, Origin] = {
      val trimmed = value.trim
      if (trimmed.equalsIgnoreCase("null")) Right(Null_)
      else {
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd < 0) Left(s"Invalid origin: $trimmed")
        else {
          val scheme   = trimmed.substring(0, schemeEnd)
          val rest     = trimmed.substring(schemeEnd + 3)
          val colonIdx = rest.lastIndexOf(':')
          if (colonIdx < 0) Right(Value(scheme, rest, None))
          else {
            val hostPart = rest.substring(0, colonIdx)
            val portStr  = rest.substring(colonIdx + 1)
            try {
              val p = portStr.toInt
              if (p < 0 || p > 65535) Left(s"Invalid origin port: $p")
              else Right(Value(scheme, hostPart, Some(p)))
            } catch {
              case _: NumberFormatException => Left(s"Invalid origin port: $portStr")
            }
          }
        }
      }
    }

    /** Writes `null`, `scheme://host` or `scheme://host:port`. */
    def render(h: Origin): String = h match {
      case Null_                           => "null"
      case Value(scheme, host, Some(port)) => s"$scheme://$host:$port"
      case Value(scheme, host, None)       => s"$scheme://$host"
    }
  }

  /**
   * The `Referer` request header: the URI of the page that led to the request.
   * `uri` holds the text.
   */
  final case class Referer(uri: String) extends zio.http.Header {

    /** Returns the name of this header, `Referer.name`. */
    def headerName: String = Referer.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Referer.render` returns.
     */
    def renderedValue: String = uri
  }

  /**
   * The [[Header.Typed]] codec for [[Referer]]: it parses and renders the
   * `referer` header.
   */
  object Referer extends Typed[Referer] {

    /** The header name, `referer`. */
    val name: String = "referer"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Referer] = Right(Referer(value.trim))

    /** Writes `uri` unchanged. */
    def render(h: Referer): String = h.renderedValue
  }

  /**
   * The `Via` header: the proxies and gateways a message passed through, one
   * entry each, such as `1.1 cache.example`.
   */
  final case class Via(entries: Chunk[String]) extends zio.http.Header {

    /** Returns the name of this header, `Via.name`. */
    def headerName: String = Via.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Via.render` returns.
     */
    def renderedValue: String = entries.mkString(", ")
  }

  /**
   * The [[Header.Typed]] codec for [[Via]]: it parses and renders the `via`
   * header.
   */
  object Via extends Typed[Via] {

    /** The header name, `via`. */
    val name: String = "via"

    /** Builds a `Via` from one or more entries. */
    def apply(first: String, rest: String*): Via =
      Via(Chunk.fromIterable(first +: rest))

    /**
     * Splits the value on commas, trims each item and drops empty ones. No
     * items gives `Left("Empty via header")`.
     */
    def parse(value: String): Either[String, Via] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty via header")
      else Right(Via(Chunk.fromArray(parts)))
    }

    /** Writes the entries joined with `, `. */
    def render(h: Via): String = h.renderedValue
  }

  /**
   * The `Forwarded` header: the details a proxy adds about the original
   * request, such as `for=` and `proto=`. `params` holds the whole value as
   * text.
   */
  final case class Forwarded(params: String) extends zio.http.Header {

    /** Returns the name of this header, `Forwarded.name`. */
    def headerName: String = Forwarded.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Forwarded.render` returns.
     */
    def renderedValue: String = params
  }

  /**
   * The [[Header.Typed]] codec for [[Forwarded]]: it parses and renders the
   * `forwarded` header.
   */
  object Forwarded extends Typed[Forwarded] {

    /** The header name, `forwarded`. */
    val name: String = "forwarded"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Forwarded] = Right(Forwarded(value.trim))

    /** Writes `params` unchanged. */
    def render(h: Forwarded): String = h.renderedValue
  }

  /**
   * The `Max-Forwards` request header: how many more times a `TRACE` or
   * `OPTIONS` request may be forwarded.
   */
  final case class MaxForwards(count: Int) extends zio.http.Header {

    /** Returns the name of this header, `MaxForwards.name`. */
    def headerName: String = MaxForwards.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `MaxForwards.render` returns.
     */
    def renderedValue: String = count.toString
  }

  /**
   * The [[Header.Typed]] codec for [[MaxForwards]]: it parses and renders the
   * `max-forwards` header.
   */
  object MaxForwards extends Typed[MaxForwards] {

    /** The header name, `max-forwards`. */
    val name: String = "max-forwards"

    /**
     * Parses the trimmed value as an `Int`. A negative number gives
     * `Left("Invalid max-forwards: <number>")`, and a value that is not an
     * integer gives `Left("Invalid max-forwards: <value>")`.
     */
    def parse(value: String): Either[String, MaxForwards] =
      try {
        val n = value.trim.toInt
        if (n < 0) Left(s"Invalid max-forwards: $n")
        else Right(MaxForwards(n))
      } catch {
        case _: NumberFormatException => Left(s"Invalid max-forwards: $value")
      }

    /** Writes `count` in decimal. */
    def render(h: MaxForwards): String = h.renderedValue
  }

  /**
   * The `From` request header: the email address of the person behind the
   * request. `email` holds the text.
   */
  final case class From(email: String) extends zio.http.Header {

    /** Returns the name of this header, `From.name`. */
    def headerName: String = From.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `From.render` returns.
     */
    def renderedValue: String = email
  }

  /**
   * The [[Header.Typed]] codec for [[From]]: it parses and renders the `from`
   * header.
   */
  object From extends Typed[From] {

    /** The header name, `from`. */
    val name: String = "from"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, From] = Right(From(value.trim))

    /** Writes `email` unchanged. */
    def render(h: From): String = h.renderedValue
  }

  /**
   * The `Cookie` request header: the cookies a client sends back, held as the
   * whole value in `value`. `Request#cookies` splits it into cookies.
   */
  final case class CookieHeader(value: String) extends zio.http.Header {

    /** Returns the name of this header, `CookieHeader.name`. */
    def headerName: String = CookieHeader.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `CookieHeader.render` returns.
     */
    def renderedValue: String = value
  }

  /**
   * The [[Header.Typed]] codec for [[CookieHeader]]: it parses and renders the
   * `cookie` header.
   */
  object CookieHeader extends Typed[CookieHeader] {

    /** The header name, `cookie`. */
    val name: String = "cookie"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, CookieHeader] = Right(CookieHeader(value))

    /** Writes `value` unchanged. */
    def render(h: CookieHeader): String = h.renderedValue
  }

  /**
   * The `Set-Cookie` response header: one cookie a server sets, held as the
   * whole value in `value`. `Response#cookies` reads the cookie in it.
   */
  final case class SetCookieHeader(value: String) extends zio.http.Header {

    /** Returns the name of this header, `SetCookieHeader.name`. */
    def headerName: String = SetCookieHeader.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `SetCookieHeader.render` returns.
     */
    def renderedValue: String = value
  }

  /**
   * The [[Header.Typed]] codec for [[SetCookieHeader]]: it parses and renders
   * the `set-cookie` header.
   */
  object SetCookieHeader extends Typed[SetCookieHeader] {

    /** The header name, `set-cookie`. */
    val name: String = "set-cookie"

    /** Wraps the value unchanged: it is neither trimmed nor checked. */
    def parse(value: String): Either[String, SetCookieHeader] = Right(SetCookieHeader(value))

    /** Writes `value` unchanged. */
    def render(h: SetCookieHeader): String = h.renderedValue
  }

  /**
   * The `Connection` header: whether the connection closes or stays open after
   * this exchange. Any other value is `Other`.
   */
  sealed trait Connection extends zio.http.Header {

    /** Returns the name of this header, `Connection.name`. */
    def headerName: String = Connection.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Connection.render` returns.
     */
    def renderedValue: String = Connection.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[Connection]]: it parses and renders the
   * `connection` header.
   */
  object Connection extends Typed[Connection] {

    /** The header name, `connection`. */
    val name: String = "connection"

    /** The `close` option: the connection closes after this exchange. */
    case object Close extends Connection

    /** The `keep-alive` option: the connection stays open. */
    case object KeepAlive extends Connection

    /** Any other option, held as lower-case text in `value`. */
    final case class Other(value: String) extends Connection

    /**
     * Trims and lower-cases the value, then maps `close` and `keep-alive` to
     * their case objects. Every other value, including an empty one, gives
     * `Other` with the lower-cased text.
     */
    def parse(value: String): Either[String, Connection] =
      value.trim.toLowerCase match {
        case "close"      => Right(Close)
        case "keep-alive" => Right(KeepAlive)
        case other        => Right(Other(other))
      }

    /** Writes `close`, `keep-alive`, or the text of `Other`. */
    def render(h: Connection): String = h match {
      case Close     => "close"
      case KeepAlive => "keep-alive"
      case Other(v)  => v
    }
  }

  /**
   * The `Upgrade` header: the protocols the sender offers to switch to, such as
   * `websocket`. `protocol` holds the text.
   */
  final case class Upgrade(protocol: String) extends zio.http.Header {

    /** Returns the name of this header, `Upgrade.name`. */
    def headerName: String = Upgrade.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Upgrade.render` returns.
     */
    def renderedValue: String = protocol
  }

  /**
   * The [[Header.Typed]] codec for [[Upgrade]]: it parses and renders the
   * `upgrade` header.
   */
  object Upgrade extends Typed[Upgrade] {

    /** The header name, `upgrade`. */
    val name: String = "upgrade"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Upgrade] = Right(Upgrade(value.trim))

    /** Writes `protocol` unchanged. */
    def render(h: Upgrade): String = h.renderedValue
  }

  /**
   * The `TE` request header: the transfer codings the client accepts. `value`
   * holds the text.
   */
  final case class Te(value: String) extends zio.http.Header {

    /** Returns the name of this header, `Te.name`. */
    def headerName: String = Te.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Te.render` returns.
     */
    def renderedValue: String = value
  }

  /**
   * The [[Header.Typed]] codec for [[Te]]: it parses and renders the `te`
   * header.
   */
  object Te extends Typed[Te] {

    /** The header name, `te`. */
    val name: String = "te"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Te] = Right(Te(value.trim))

    /** Writes `value` unchanged. */
    def render(h: Te): String = h.renderedValue
  }

  /**
   * The `Trailer` header: the names of the headers that follow the body as
   * trailers.
   */
  final case class Trailer(headers: Chunk[String]) extends zio.http.Header {

    /** Returns the name of this header, `Trailer.name`. */
    def headerName: String = Trailer.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Trailer.render` returns.
     */
    def renderedValue: String = headers.mkString(", ")
  }

  /**
   * The [[Header.Typed]] codec for [[Trailer]]: it parses and renders the
   * `trailer` header.
   */
  object Trailer extends Typed[Trailer] {

    /** The header name, `trailer`. */
    val name: String = "trailer"

    /** Builds a `Trailer` from one or more header names. */
    def apply(first: String, rest: String*): Trailer =
      Trailer(Chunk.fromIterable(first +: rest))

    /**
     * Splits the value on commas, trims each item and drops empty ones. No
     * items gives `Left("Empty trailer header")`.
     */
    def parse(value: String): Either[String, Trailer] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty trailer header")
      else Right(Trailer(Chunk.fromArray(parts)))
    }

    /** Writes the names joined with `, `. */
    def render(h: Trailer): String = h.renderedValue
  }

  /**
   * The `Transfer-Encoding` header: the codings applied to the message body for
   * transfer. One coding is a case object; several, in the order listed, are a
   * `Multiple`.
   */
  sealed trait TransferEncoding extends zio.http.Header {

    /** Returns the name of this header, `TransferEncoding.name`. */
    def headerName: String = TransferEncoding.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `TransferEncoding.render` returns.
     */
    def renderedValue: String = TransferEncoding.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[TransferEncoding]]: it parses and renders
   * the `transfer-encoding` header.
   */
  object TransferEncoding extends Typed[TransferEncoding] {

    /** The header name, `transfer-encoding`. */
    val name: String = "transfer-encoding"

    /** The `chunked` coding. */
    case object Chunked extends TransferEncoding

    /** The `compress` coding. */
    case object Compress extends TransferEncoding

    /** The `deflate` coding. */
    case object Deflate extends TransferEncoding

    /** The `gzip` coding. */
    case object GZip extends TransferEncoding

    /** The `identity` coding: no encoding. */
    case object Identity extends TransferEncoding

    /** Several codings, in the order given. */
    final case class Multiple(values: Chunk[TransferEncoding]) extends TransferEncoding

    private def findEncoding(value: String): Option[TransferEncoding] =
      value.trim.toLowerCase match {
        case "chunked"  => Some(Chunked)
        case "compress" => Some(Compress)
        case "deflate"  => Some(Deflate)
        case "gzip"     => Some(GZip)
        case "identity" => Some(Identity)
        case _          => None
      }

    /**
     * Parses a comma-separated list. It trims each item and matches `chunked`,
     * `compress`, `deflate`, `gzip` or `identity` in any letter case; an item
     * that matches none of them is dropped. One match gives that coding,
     * several give `Multiple`, and none gives
     * `Left("Invalid transfer-encoding: <value>")`.
     */
    def parse(value: String): Either[String, TransferEncoding] = {
      val parts  = value.split(',')
      val parsed = Chunk.fromArray(parts.flatMap(s => findEncoding(s)))
      if (parsed.isEmpty) Left(s"Invalid transfer-encoding: $value")
      else if (parsed.length == 1) Right(parsed(0))
      else Right(Multiple(parsed))
    }

    /**
     * Writes the coding name in lower case; `Multiple` joins its names with
     * `, `.
     */
    def render(h: TransferEncoding): String = h match {
      case Chunked     => "chunked"
      case Compress    => "compress"
      case Deflate     => "deflate"
      case GZip        => "gzip"
      case Identity    => "identity"
      case Multiple(v) => v.map(render).mkString(", ")
    }
  }

  /**
   * The `Cache-Control` header: the caching directives of a request or
   * response. One directive is a case object or case class; several, in the
   * order listed, are a `Multiple`.
   */
  sealed trait CacheControl extends zio.http.Header {

    /** Returns the name of this header, `CacheControl.name`. */
    def headerName: String = CacheControl.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `CacheControl.render` returns.
     */
    def renderedValue: String = CacheControl.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[CacheControl]]: it parses and renders the
   * `cache-control` header.
   */
  object CacheControl extends Typed[CacheControl] {

    /** The header name, `cache-control`. */
    val name: String = "cache-control"

    /** The `no-cache` directive. */
    case object NoCache extends CacheControl

    /** The `no-store` directive. */
    case object NoStore extends CacheControl

    /** The `no-transform` directive. */
    case object NoTransform extends CacheControl

    /** The `public` directive. */
    case object Public extends CacheControl

    /** The `private` directive. */
    case object Private extends CacheControl

    /** The `must-revalidate` directive. */
    case object MustRevalidate extends CacheControl

    /** The `proxy-revalidate` directive. */
    case object ProxyRevalidate extends CacheControl

    /** The `immutable` directive. */
    case object Immutable extends CacheControl

    /** The `only-if-cached` directive. */
    case object OnlyIfCached extends CacheControl

    /** The `must-understand` directive. */
    case object MustUnderstand extends CacheControl

    /** The `max-age` directive, with a lifetime in seconds. */
    final case class MaxAge(seconds: Long) extends CacheControl

    /**
     * The `s-maxage` directive, with a lifetime in seconds for shared caches.
     */
    final case class SMaxAge(seconds: Long) extends CacheControl

    /**
     * The `max-stale` directive, with an optional number of seconds; it renders
     * as `max-stale` alone when `seconds` is `None`.
     */
    final case class MaxStale(seconds: Option[Long]) extends CacheControl

    /** The `min-fresh` directive, with a number of seconds. */
    final case class MinFresh(seconds: Long) extends CacheControl

    /** The `stale-while-revalidate` directive, with a number of seconds. */
    final case class StaleWhileRevalidate(seconds: Long) extends CacheControl

    /** The `stale-if-error` directive, with a number of seconds. */
    final case class StaleIfError(seconds: Long) extends CacheControl

    /** Several directives, in the order given. */
    final case class Multiple(directives: Chunk[CacheControl]) extends CacheControl

    /**
     * Parses a comma-separated list of directives. It trims the value, and an
     * empty value gives `Left("Empty cache-control header")`. Each item is
     * trimmed and its name matched in lower case; one directive gives its case,
     * several give `Multiple`, and the first failing item gives the `Left`. An
     * unknown name gives `Left("Unknown cache-control directive: <name>")`, and
     * a value after `=` that is not an integer, including a quoted one such as
     * in `no-cache="set-cookie"`, gives
     * `Left("Invalid number in cache-control directive: <value>")`. A negative
     * number is accepted.
     */
    def parse(value: String): Either[String, CacheControl] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty cache-control header")
      val parts = trimmed.split(",")
      if (parts.length == 1) parseDirective(parts(0).trim)
      else {
        val parsed = new Array[CacheControl](parts.length)
        var i      = 0
        while (i < parts.length) {
          parseDirective(parts(i).trim) match {
            case Right(d) => parsed(i) = d
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        Right(Multiple(Chunk.fromArray(parsed)))
      }
    }

    /**
     * Writes each directive as in a header, such as `max-age=600` or
     * `no-store`; `Multiple` joins them with `, `.
     */
    def render(h: CacheControl): String = h match {
      case NoCache                 => "no-cache"
      case NoStore                 => "no-store"
      case NoTransform             => "no-transform"
      case Public                  => "public"
      case Private                 => "private"
      case MustRevalidate          => "must-revalidate"
      case ProxyRevalidate         => "proxy-revalidate"
      case Immutable               => "immutable"
      case OnlyIfCached            => "only-if-cached"
      case MustUnderstand          => "must-understand"
      case MaxAge(s)               => s"max-age=$s"
      case SMaxAge(s)              => s"s-maxage=$s"
      case MaxStale(Some(s))       => s"max-stale=$s"
      case MaxStale(None)          => "max-stale"
      case MinFresh(s)             => s"min-fresh=$s"
      case StaleWhileRevalidate(s) => s"stale-while-revalidate=$s"
      case StaleIfError(s)         => s"stale-if-error=$s"
      case Multiple(ds)            => ds.map(render).mkString(", ")
    }

    private def parseDirective(s: String): Either[String, CacheControl] = {
      val eqIdx = s.indexOf('=')
      if (eqIdx < 0) {
        s.toLowerCase match {
          case "no-cache"         => Right(NoCache)
          case "no-store"         => Right(NoStore)
          case "no-transform"     => Right(NoTransform)
          case "public"           => Right(Public)
          case "private"          => Right(Private)
          case "must-revalidate"  => Right(MustRevalidate)
          case "proxy-revalidate" => Right(ProxyRevalidate)
          case "immutable"        => Right(Immutable)
          case "only-if-cached"   => Right(OnlyIfCached)
          case "must-understand"  => Right(MustUnderstand)
          case "max-stale"        => Right(MaxStale(None))
          case other              => Left(s"Unknown cache-control directive: $other")
        }
      } else {
        val key = s.substring(0, eqIdx).trim.toLowerCase
        val raw = s.substring(eqIdx + 1).trim
        try {
          val num = raw.toLong
          key match {
            case "max-age"                => Right(MaxAge(num))
            case "s-maxage"               => Right(SMaxAge(num))
            case "max-stale"              => Right(MaxStale(Some(num)))
            case "min-fresh"              => Right(MinFresh(num))
            case "stale-while-revalidate" => Right(StaleWhileRevalidate(num))
            case "stale-if-error"         => Right(StaleIfError(num))
            case other                    => Left(s"Unknown cache-control directive: $other")
          }
        } catch {
          case _: NumberFormatException => Left(s"Invalid number in cache-control directive: $raw")
        }
      }
    }
  }

  /**
   * The `ETag` header: an identifier for one version of a resource. `tag` is
   * the tag without its double quotes, and `weak` is `true` for a weak
   * validator, written with a `W/` prefix.
   */
  final case class ETag(tag: String, weak: Boolean) extends zio.http.Header {

    /** Returns the name of this header, `ETag.name`. */
    def headerName: String = ETag.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `ETag.render` returns.
     */
    def renderedValue: String = ETag.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[ETag]]: it parses and renders the `etag`
   * header.
   */
  object ETag extends Typed[ETag] {

    /** The header name, `etag`. */
    val name: String = "etag"

    /**
     * Parses `"tag"` as a strong tag and `W/"tag"` as a weak one, after
     * trimming; the `W/` prefix must be upper case. Any other value gives
     * `Left("Invalid etag: <value>")`. A value that is only one double quote
     * throws a `StringIndexOutOfBoundsException` instead.
     */
    def parse(value: String): Either[String, ETag] = {
      val trimmed = value.trim
      if (trimmed.startsWith("W/\"") && trimmed.endsWith("\""))
        Right(ETag(trimmed.substring(3, trimmed.length - 1), weak = true))
      else if (trimmed.startsWith("\"") && trimmed.endsWith("\""))
        Right(ETag(trimmed.substring(1, trimmed.length - 1), weak = false))
      else
        Left(s"Invalid etag: $trimmed")
    }

    /**
     * Writes the tag in double quotes, with a `W/` prefix when `weak` is
     * `true`. The tag is not escaped.
     */
    def render(h: ETag): String =
      if (h.weak) s"""W/\"${h.tag}\""""
      else s"""\"${h.tag}\""""
  }

  /**
   * The `If-Match` request header: the request applies only when the current
   * entity tag of the resource matches. It is `Any` or a list of tags.
   */
  sealed trait IfMatch extends zio.http.Header {

    /** Returns the name of this header, `IfMatch.name`. */
    def headerName: String = IfMatch.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `IfMatch.render` returns.
     */
    def renderedValue: String = IfMatch.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[IfMatch]]: it parses and renders the
   * `if-match` header.
   */
  object IfMatch extends Typed[IfMatch] {

    /** The header name, `if-match`. */
    val name: String = "if-match"

    /** Matches any version: the `*` value. */
    case object Any extends IfMatch

    /** Matches the listed entity tags. */
    final case class ETags(tags: Chunk[ETag]) extends IfMatch

    /**
     * Parses `*` as `Any`, or a comma-separated list of entity tags, after
     * trimming. Each item is trimmed and parsed as an `ETag`, and the first
     * failing item gives its `Left`, such as `Left("Invalid etag: <item>")`; an
     * empty value gives that error too.
     */
    def parse(value: String): Either[String, IfMatch] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts  = trimmed.split(",")
        val parsed = new scala.collection.mutable.ArrayBuffer[ETag](parts.length)
        var i      = 0
        while (i < parts.length) {
          ETag.parse(parts(i).trim) match {
            case Right(e) => parsed += e
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        if (parsed.isEmpty) Left("Empty if-match header")
        else Right(ETags(Chunk.fromArray(parsed.toArray)))
      }
    }

    /** Writes `*` for `Any`, or the tags joined with `, `. */
    def render(h: IfMatch): String = h match {
      case Any       => "*"
      case ETags(ts) => ts.map(ETag.render).mkString(", ")
    }
  }

  /**
   * The `If-None-Match` request header: the request applies only when no entity
   * tag of the resource matches. It is `Any` or a list of tags.
   */
  sealed trait IfNoneMatch extends zio.http.Header {

    /** Returns the name of this header, `IfNoneMatch.name`. */
    def headerName: String = IfNoneMatch.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `IfNoneMatch.render` returns.
     */
    def renderedValue: String = IfNoneMatch.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[IfNoneMatch]]: it parses and renders the
   * `if-none-match` header.
   */
  object IfNoneMatch extends Typed[IfNoneMatch] {

    /** The header name, `if-none-match`. */
    val name: String = "if-none-match"

    /** Matches any version: the `*` value. */
    case object Any extends IfNoneMatch

    /** Matches the listed entity tags. */
    final case class ETags(tags: Chunk[ETag]) extends IfNoneMatch

    /**
     * Parses `*` as `Any`, or a comma-separated list of entity tags, after
     * trimming. Each item is trimmed and parsed as an `ETag`, and the first
     * failing item gives its `Left`, such as `Left("Invalid etag: <item>")`; an
     * empty value gives that error too.
     */
    def parse(value: String): Either[String, IfNoneMatch] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts  = trimmed.split(",")
        val parsed = new scala.collection.mutable.ArrayBuffer[ETag](parts.length)
        var i      = 0
        while (i < parts.length) {
          ETag.parse(parts(i).trim) match {
            case Right(e) => parsed += e
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        if (parsed.isEmpty) Left("Empty if-none-match header")
        else Right(ETags(Chunk.fromArray(parsed.toArray)))
      }
    }

    /** Writes `*` for `Any`, or the tags joined with `, `. */
    def render(h: IfNoneMatch): String = h match {
      case Any       => "*"
      case ETags(ts) => ts.map(ETag.render).mkString(", ")
    }
  }

  /**
   * The `If-Modified-Since` request header: the request applies only if the
   * resource changed after this date. `date` holds the text.
   */
  final case class IfModifiedSince(date: String) extends zio.http.Header {

    /** Returns the name of this header, `IfModifiedSince.name`. */
    def headerName: String = IfModifiedSince.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `IfModifiedSince.render` returns.
     */
    def renderedValue: String = date
  }

  /**
   * The [[Header.Typed]] codec for [[IfModifiedSince]]: it parses and renders
   * the `if-modified-since` header.
   */
  object IfModifiedSince extends Typed[IfModifiedSince] {

    /** The header name, `if-modified-since`. */
    val name: String = "if-modified-since"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked. It is not parsed as a date.
     */
    def parse(value: String): Either[String, IfModifiedSince] = Right(IfModifiedSince(value.trim))

    /** Writes `date` unchanged. */
    def render(h: IfModifiedSince): String = h.renderedValue
  }

  /**
   * The `If-Unmodified-Since` request header: the request applies only if the
   * resource has not changed since this date. `date` holds the text.
   */
  final case class IfUnmodifiedSince(date: String) extends zio.http.Header {

    /** Returns the name of this header, `IfUnmodifiedSince.name`. */
    def headerName: String = IfUnmodifiedSince.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `IfUnmodifiedSince.render` returns.
     */
    def renderedValue: String = date
  }

  /**
   * The [[Header.Typed]] codec for [[IfUnmodifiedSince]]: it parses and renders
   * the `if-unmodified-since` header.
   */
  object IfUnmodifiedSince extends Typed[IfUnmodifiedSince] {

    /** The header name, `if-unmodified-since`. */
    val name: String = "if-unmodified-since"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked. It is not parsed as a date.
     */
    def parse(value: String): Either[String, IfUnmodifiedSince] = Right(IfUnmodifiedSince(value.trim))

    /** Writes `date` unchanged. */
    def render(h: IfUnmodifiedSince): String = h.renderedValue
  }

  /**
   * The `If-Range` request header: a range request applies only if the resource
   * still matches this entity tag or date. `value` holds the text.
   */
  final case class IfRange(value: String) extends zio.http.Header {

    /** Returns the name of this header, `IfRange.name`. */
    def headerName: String = IfRange.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `IfRange.render` returns.
     */
    def renderedValue: String = value
  }

  /**
   * The [[Header.Typed]] codec for [[IfRange]]: it parses and renders the
   * `if-range` header.
   */
  object IfRange extends Typed[IfRange] {

    /** The header name, `if-range`. */
    val name: String = "if-range"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, IfRange] = Right(IfRange(value.trim))

    /** Writes `value` unchanged. */
    def render(h: IfRange): String = h.renderedValue
  }

  /**
   * The `Expires` response header: the date after which the response is stale.
   * `date` holds the text.
   */
  final case class Expires(date: String) extends zio.http.Header {

    /** Returns the name of this header, `Expires.name`. */
    def headerName: String = Expires.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Expires.render` returns.
     */
    def renderedValue: String = date
  }

  /**
   * The [[Header.Typed]] codec for [[Expires]]: it parses and renders the
   * `expires` header.
   */
  object Expires extends Typed[Expires] {

    /** The header name, `expires`. */
    val name: String = "expires"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked. It is not parsed as a date.
     */
    def parse(value: String): Either[String, Expires] = Right(Expires(value.trim))

    /** Writes `date` unchanged. */
    def render(h: Expires): String = h.renderedValue
  }

  /**
   * The `Age` response header: how many seconds the response has been in a
   * cache.
   */
  final case class Age(seconds: Long) extends zio.http.Header {

    /** Returns the name of this header, `Age.name`. */
    def headerName: String = Age.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Age.render` returns.
     */
    def renderedValue: String = seconds.toString
  }

  /**
   * The [[Header.Typed]] codec for [[Age]]: it parses and renders the `age`
   * header.
   */
  object Age extends Typed[Age] {

    /** The header name, `age`. */
    val name: String = "age"

    /**
     * Parses the trimmed value as a `Long`. A negative number gives
     * `Left("Invalid age: <number>")`, and a value that is not an integer gives
     * `Left("Invalid age: <value>")`.
     */
    def parse(value: String): Either[String, Age] =
      try {
        val s = value.trim.toLong
        if (s < 0) Left(s"Invalid age: $s")
        else Right(Age(s))
      } catch {
        case _: NumberFormatException => Left(s"Invalid age: $value")
      }

    /** Writes `seconds` in decimal. */
    def render(h: Age): String = h.renderedValue
  }

  /**
   * The `Last-Modified` response header: the date the resource last changed.
   * `date` holds the text.
   */
  final case class LastModified(date: String) extends zio.http.Header {

    /** Returns the name of this header, `LastModified.name`. */
    def headerName: String = LastModified.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `LastModified.render` returns.
     */
    def renderedValue: String = date
  }

  /**
   * The [[Header.Typed]] codec for [[LastModified]]: it parses and renders the
   * `last-modified` header.
   */
  object LastModified extends Typed[LastModified] {

    /** The header name, `last-modified`. */
    val name: String = "last-modified"

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked. It is not parsed as a date.
     */
    def parse(value: String): Either[String, LastModified] = Right(LastModified(value.trim))

    /** Writes `date` unchanged. */
    def render(h: LastModified): String = h.renderedValue
  }

  /**
   * The `Pragma` header: legacy cache directives, held as the text
   * `directives`.
   */
  final case class Pragma(directives: String) extends zio.http.Header {

    /** Returns the name of this header, `Pragma.name`. */
    def headerName: String = Pragma.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Pragma.render` returns.
     */
    def renderedValue: String = directives
  }

  /**
   * The [[Header.Typed]] codec for [[Pragma]]: it parses and renders the
   * `pragma` header.
   */
  object Pragma extends Typed[Pragma] {

    /** The header name, `pragma`. */
    val name: String = "pragma"

    /** The header `Pragma: no-cache`. */
    val NoCache: Pragma = Pragma("no-cache")

    /**
     * Wraps the value after trimming surrounding whitespace; the text is not
     * checked.
     */
    def parse(value: String): Either[String, Pragma] = Right(Pragma(value.trim))

    /** Writes `directives` unchanged. */
    def render(h: Pragma): String = h.renderedValue
  }

  /**
   * The `Vary` response header: the request headers that the response depends
   * on, which caches use as part of the key. It is `Any` or a list of header
   * names.
   */
  sealed trait Vary extends zio.http.Header {

    /** Returns the name of this header, `Vary.name`. */
    def headerName: String = Vary.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Vary.render` returns.
     */
    def renderedValue: String = Vary.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[Vary]]: it parses and renders the `vary`
   * header.
   */
  object Vary extends Typed[Vary] {

    /** The header name, `vary`. */
    val name: String = "vary"

    /**
     * The `*` value: the response depends on factors beyond the request
     * headers.
     */
    case object Any extends Vary

    /** A list of request header names. */
    final case class Headers(names: Chunk[String]) extends Vary

    /** Another name for `Any`. */
    val Star: Vary = Any

    /** Builds a `Vary` from one or more header names. */
    def apply(first: String, rest: String*): Vary =
      Headers(Chunk.fromIterable(first +: rest))

    /**
     * Parses `*` as `Any`, or a comma-separated list of header names: it trims
     * the value, splits on commas, trims each item and drops empty ones. No
     * names gives `Left("Empty vary header")`.
     */
    def parse(value: String): Either[String, Vary] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
        if (parts.isEmpty) Left("Empty vary header")
        else Right(Headers(Chunk.fromArray(parts)))
      }
    }

    /** Writes `*` for `Any`, or the names joined with `, `. */
    def render(h: Vary): String = h match {
      case Any        => "*"
      case Headers(n) => n.mkString(", ")
    }
  }

  /**
   * The `Accept` request header: the media types the client accepts, each with
   * a quality.
   */
  final case class Accept(mediaRanges: Chunk[Accept.MediaRange]) extends zio.http.Header {

    /** Returns the name of this header, `Accept.name`. */
    def headerName: String = Accept.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `Accept.render` returns.
     */
    def renderedValue: String = Accept.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[Accept]]: it parses and renders the
   * `accept` header.
   */
  object Accept extends Typed[Accept] {

    /** The header name, `accept`. */
    val name: String = "accept"

    /**
     * One entry of an `Accept` header: a media type, which may contain a
     * wildcard, and its quality, which is `1.0` unless the entry gives `q=`.
     */
    final case class MediaRange(mediaType: MediaType, quality: Double = 1.0)

    /**
     * Parses a comma-separated list of media ranges. A blank value gives
     * `Left("Accept header cannot be empty")`. Each non-empty item is split at
     * its first semicolon: the part before it is parsed as a media type, and
     * any failure gives a `Left` that joins the messages with `; `, such as
     * `Invalid media type: must contain '/' separator`. A `q=` parameter after
     * the semicolon sets the quality, and one that is not a number is ignored,
     * leaving `1.0`; other parameters are ignored. Wildcard ranges such as
     * `text` with any subtype parse.
     */
    def parse(value: String): Either[String, Accept] = {
      if (value.trim.isEmpty) return Left("Accept header cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val ranges = Chunk.fromArray(parts.map(parseMediaRange))
      val errors = ranges.collect { case Left(err) => err }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else Right(Accept(ranges.collect { case Right(mr) => mr }))
    }

    /**
     * Writes each media type, adding `;q=<quality>` unless the quality is
     * `1.0`, joined with `, `.
     */
    def render(h: Accept): String =
      h.mediaRanges.map { mr =>
        if (mr.quality == 1.0) mr.mediaType.fullType
        else s"${mr.mediaType.fullType};q=${mr.quality}"
      }.mkString(", ")

    private def parseMediaRange(s: String): Either[String, MediaRange] = {
      val semiIdx = s.indexOf(';')
      if (semiIdx < 0) {
        MediaType.parse(s.trim).map(mt => MediaRange(mt))
      } else {
        val mediaStr  = s.substring(0, semiIdx).trim
        val paramsStr = s.substring(semiIdx + 1).trim
        val quality   = parseQuality(paramsStr)
        MediaType.parse(mediaStr).map(mt => MediaRange(mt, quality))
      }
    }

    private def parseQuality(params: String): Double = {
      val qIdx = params.indexOf("q=")
      if (qIdx >= 0) {
        try params.substring(qIdx + 2).trim.toDouble
        catch { case _: NumberFormatException => 1.0 }
      } else 1.0
    }
  }

  /**
   * The `Accept-Encoding` request header: the content codings the client
   * accepts, each with an optional weight. One coding is a case class; several,
   * in the order listed, are a `Multiple`.
   */
  sealed trait AcceptEncoding extends zio.http.Header {

    /** Returns the name of this header, `AcceptEncoding.name`. */
    def headerName: String = AcceptEncoding.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AcceptEncoding.render` returns.
     */
    def renderedValue: String = AcceptEncoding.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AcceptEncoding]]: it parses and renders
   * the `accept-encoding` header.
   */
  object AcceptEncoding extends Typed[AcceptEncoding] {

    /** The header name, `accept-encoding`. */
    val name: String = "accept-encoding"

    /** The `gzip` coding, with an optional weight. */
    final case class GZip(weight: Option[Double] = None) extends AcceptEncoding

    /** The `deflate` coding, with an optional weight. */
    final case class Deflate(weight: Option[Double] = None) extends AcceptEncoding

    /** The `br` (Brotli) coding, with an optional weight. */
    final case class Br(weight: Option[Double] = None) extends AcceptEncoding

    /** The `compress` coding, with an optional weight. */
    final case class Compress(weight: Option[Double] = None) extends AcceptEncoding

    /** The `identity` coding, with an optional weight. */
    final case class Identity(weight: Option[Double] = None) extends AcceptEncoding

    /** Any coding, written with an asterisk, with an optional weight. */
    final case class Any(weight: Option[Double] = None) extends AcceptEncoding

    /** Several codings, in the order given. */
    final case class Multiple(values: Chunk[AcceptEncoding]) extends AcceptEncoding

    /** Any coding with no weight: an asterisk alone. */
    val NoPreference: AcceptEncoding = Any(None)

    /**
     * Parses a comma-separated list. It trims each item and drops empty ones;
     * none left gives `Left("Invalid accept-encoding: <value>")`. One item
     * gives its case, several give `Multiple`. A weight written as
     * `;q=<number>` with no space is read, and one that is not a number is
     * dropped. A name other than `gzip`, `deflate`, `br`, `compress`,
     * `identity` and an asterisk, matched in any letter case, becomes `GZip`
     * with the item's weight.
     */
    def parse(value: String): Either[String, AcceptEncoding] = {
      val parts = value.split(',').map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left(s"Invalid accept-encoding: $value")
      else if (parts.length == 1) Right(parseSingle(parts(0)))
      else Right(Multiple(Chunk.fromArray(parts.map(parseSingle))))
    }

    /**
     * Writes each coding name, adding `;q=<weight>` when there is a weight;
     * `Multiple` joins them with `, `.
     */
    def render(h: AcceptEncoding): String = h match {
      case GZip(w)     => renderWithWeight("gzip", w)
      case Deflate(w)  => renderWithWeight("deflate", w)
      case Br(w)       => renderWithWeight("br", w)
      case Compress(w) => renderWithWeight("compress", w)
      case Identity(w) => renderWithWeight("identity", w)
      case Any(w)      => renderWithWeight("*", w)
      case Multiple(v) => v.map(render).mkString(", ")
    }

    private def parseSingle(s: String): AcceptEncoding = {
      val qIdx          = s.indexOf(";q=")
      val (raw, weight) =
        if (qIdx >= 0) {
          val w =
            try Some(s.substring(qIdx + 3).trim.toDouble)
            catch { case _: NumberFormatException => None }
          (s.substring(0, qIdx).trim, w)
        } else (s.trim, None)

      raw.toLowerCase match {
        case "gzip"     => GZip(weight)
        case "deflate"  => Deflate(weight)
        case "br"       => Br(weight)
        case "compress" => Compress(weight)
        case "identity" => Identity(weight)
        case "*"        => Any(weight)
        case _          => GZip(weight)
      }
    }

    private def renderWithWeight(name: String, weight: Option[Double]): String =
      weight match {
        case Some(w) => s"$name;q=$w"
        case None    => name
      }
  }

  /**
   * The `Accept-Language` request header: the languages the client prefers,
   * each with a quality.
   */
  final case class AcceptLanguage(languages: Chunk[AcceptLanguage.LanguageRange]) extends zio.http.Header {

    /** Returns the name of this header, `AcceptLanguage.name`. */
    def headerName: String = AcceptLanguage.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AcceptLanguage.render` returns.
     */
    def renderedValue: String = AcceptLanguage.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AcceptLanguage]]: it parses and renders
   * the `accept-language` header.
   */
  object AcceptLanguage extends Typed[AcceptLanguage] {

    /** The header name, `accept-language`. */
    val name: String = "accept-language"

    /**
     * One entry of an `Accept-Language` header: a language tag, which is not
     * checked, and its quality, which is `1.0` unless the entry gives `;q=`.
     */
    final case class LanguageRange(tag: String, quality: Double = 1.0)

    /** The range that matches any language: an asterisk with quality `1.0`. */
    val Any: LanguageRange = LanguageRange("*")

    /**
     * Parses a comma-separated list of language ranges. A blank value gives
     * `Left("Accept-Language cannot be empty")`. It trims each item and drops
     * empty ones, reads a quality written as `;q=<number>` with no space, and
     * falls back to `1.0` when that is not a number. Tags are not checked.
     */
    def parse(value: String): Either[String, AcceptLanguage] = {
      if (value.trim.isEmpty) return Left("Accept-Language cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val ranges = Chunk.fromArray(parts.map(parseLangRange))
      Right(AcceptLanguage(ranges))
    }

    /**
     * Writes each tag, adding `;q=<quality>` unless the quality is `1.0`,
     * joined with `, `.
     */
    def render(h: AcceptLanguage): String =
      h.languages.map { lr =>
        if (lr.quality == 1.0) lr.tag
        else s"${lr.tag};q=${lr.quality}"
      }.mkString(", ")

    private def parseLangRange(s: String): LanguageRange = {
      val qIdx = s.indexOf(";q=")
      if (qIdx >= 0) {
        val tag    = s.substring(0, qIdx).trim
        val weight =
          try s.substring(qIdx + 3).trim.toDouble
          catch { case _: NumberFormatException => 1.0 }
        LanguageRange(tag, weight)
      } else {
        LanguageRange(s.trim)
      }
    }
  }

  /**
   * The `Accept-Ranges` response header: whether the server supports range
   * requests.
   */
  sealed trait AcceptRanges extends zio.http.Header {

    /** Returns the name of this header, `AcceptRanges.name`. */
    def headerName: String = AcceptRanges.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AcceptRanges.render` returns.
     */
    def renderedValue: String = AcceptRanges.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AcceptRanges]]: it parses and renders the
   * `accept-ranges` header.
   */
  object AcceptRanges extends Typed[AcceptRanges] {

    /** The header name, `accept-ranges`. */
    val name: String = "accept-ranges"

    /** The `bytes` value: the server supports byte ranges. */
    case object Bytes extends AcceptRanges

    /** The `none` value: the server supports no range requests. */
    case object None_ extends AcceptRanges

    /**
     * Trims and lower-cases the value, then maps `bytes` and `none` to their
     * case objects. Any other value gives
     * `Left("Invalid accept-ranges: <value>")` with the trimmed, lower-cased
     * text.
     */
    def parse(value: String): Either[String, AcceptRanges] =
      value.trim.toLowerCase match {
        case "bytes" => Right(Bytes)
        case "none"  => Right(None_)
        case other   => Left(s"Invalid accept-ranges: $other")
      }

    /** Writes `bytes` or `none`. */
    def render(h: AcceptRanges): String = h match {
      case Bytes => "bytes"
      case None_ => "none"
    }
  }

  /**
   * The `Accept-Patch` response header: the media types the server accepts in
   * `PATCH` requests.
   */
  final case class AcceptPatch(mediaTypes: Chunk[MediaType]) extends zio.http.Header {

    /** Returns the name of this header, `AcceptPatch.name`. */
    def headerName: String = AcceptPatch.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AcceptPatch.render` returns.
     */
    def renderedValue: String = AcceptPatch.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AcceptPatch]]: it parses and renders the
   * `accept-patch` header.
   */
  object AcceptPatch extends Typed[AcceptPatch] {

    /** The header name, `accept-patch`. */
    val name: String = "accept-patch"

    /** Builds an `AcceptPatch` from one or more media types. */
    def apply(first: MediaType, rest: MediaType*): AcceptPatch =
      AcceptPatch(Chunk.fromIterable(first +: rest))

    /**
     * Parses a comma-separated list of media types. A blank value gives
     * `Left("Accept-Patch header cannot be empty")`. An item that is not a
     * valid media type is dropped without an error; when none are left the
     * result is `Left("Invalid accept-patch: <value>")`.
     */
    def parse(value: String): Either[String, AcceptPatch] = {
      if (value.trim.isEmpty) return Left("Accept-Patch header cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val parsed = parts.flatMap(s => MediaType.parse(s).toOption)
      if (parsed.isEmpty) Left(s"Invalid accept-patch: $value")
      else Right(AcceptPatch(Chunk.fromArray(parsed)))
    }

    /** Writes the media types joined with `, `. */
    def render(h: AcceptPatch): String =
      h.mediaTypes.map(_.fullType).mkString(", ")
  }

  /**
   * The `Access-Control-Allow-Origin` response header: which origin may read
   * the response in a cross-origin request.
   */
  sealed trait AccessControlAllowOrigin extends zio.http.Header {

    /** Returns the name of this header, `AccessControlAllowOrigin.name`. */
    def headerName: String = AccessControlAllowOrigin.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlAllowOrigin.render` returns.
     */
    def renderedValue: String = AccessControlAllowOrigin.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlAllowOrigin]]: it parses and
   * renders the `access-control-allow-origin` header.
   */
  object AccessControlAllowOrigin extends Typed[AccessControlAllowOrigin] {

    /** The header name, `access-control-allow-origin`. */
    val name: String = "access-control-allow-origin"

    /** Any origin: the `*` value. */
    case object All extends AccessControlAllowOrigin

    /** One origin, held as the text `origin`. */
    final case class Specific(origin: String) extends AccessControlAllowOrigin

    /**
     * Trims the value and maps `*` to `All`. An empty value gives
     * `Left("Empty access-control-allow-origin header")`; anything else becomes
     * `Specific` with the trimmed text, which is not checked.
     */
    def parse(value: String): Either[String, AccessControlAllowOrigin] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(All)
      else if (trimmed.isEmpty) Left("Empty access-control-allow-origin header")
      else Right(Specific(trimmed))
    }

    /** Writes `*` for `All`, or the origin text. */
    def render(h: AccessControlAllowOrigin): String = h match {
      case All         => "*"
      case Specific(o) => o
    }
  }

  /**
   * The `Access-Control-Allow-Methods` response header: the methods allowed in
   * a cross-origin request.
   */
  final case class AccessControlAllowMethods(methods: Chunk[Method]) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlAllowMethods.name`. */
    def headerName: String = AccessControlAllowMethods.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlAllowMethods.render` returns.
     */
    def renderedValue: String = AccessControlAllowMethods.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlAllowMethods]]: it parses and
   * renders the `access-control-allow-methods` header.
   */
  object AccessControlAllowMethods extends Typed[AccessControlAllowMethods] {

    /** The header name, `access-control-allow-methods`. */
    val name: String = "access-control-allow-methods"

    /** Every method of `Method.values`. */
    val All: AccessControlAllowMethods = AccessControlAllowMethods(Chunk.fromIterable(Method.values))

    /** Builds the header from one or more methods. */
    def apply(first: Method, rest: Method*): AccessControlAllowMethods =
      AccessControlAllowMethods(Chunk.fromIterable(first +: rest))

    /**
     * Parses a comma-separated list of method names. It trims the value, and an
     * empty value gives `Left("Empty access-control-allow-methods header")`.
     * Each item is trimmed and read with `Method.fromString`, which accepts
     * upper-case names only; the first unknown item gives
     * `Left("Unknown method: <item>")`.
     */
    def parse(value: String): Either[String, AccessControlAllowMethods] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-allow-methods header")
      val parts   = trimmed.split(",")
      val methods = new scala.collection.mutable.ArrayBuffer[Method](parts.length)
      var i       = 0
      while (i < parts.length) {
        val name = parts(i).trim
        Method.fromString(name) match {
          case Some(m) => methods += m
          case None    => return Left(s"Unknown method: $name")
        }
        i += 1
      }
      Right(AccessControlAllowMethods(Chunk.fromArray(methods.toArray)))
    }

    /** Writes the method names joined with `, `. */
    def render(h: AccessControlAllowMethods): String =
      h.methods.map(_.name).mkString(", ")
  }

  /**
   * The `Access-Control-Allow-Headers` response header: the request headers
   * allowed in a cross-origin request.
   */
  final case class AccessControlAllowHeaders(headers: Chunk[String]) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlAllowHeaders.name`. */
    def headerName: String = AccessControlAllowHeaders.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlAllowHeaders.render` returns.
     */
    def renderedValue: String = AccessControlAllowHeaders.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlAllowHeaders]]: it parses and
   * renders the `access-control-allow-headers` header.
   */
  object AccessControlAllowHeaders extends Typed[AccessControlAllowHeaders] {

    /** The header name, `access-control-allow-headers`. */
    val name: String = "access-control-allow-headers"

    /** Every header name: an asterisk. */
    val All: AccessControlAllowHeaders = AccessControlAllowHeaders(Chunk("*"))

    /** Builds the header from one or more header names. */
    def apply(first: String, rest: String*): AccessControlAllowHeaders =
      AccessControlAllowHeaders(Chunk.fromIterable(first +: rest))

    /**
     * Parses a comma-separated list of header names. It trims the value, and an
     * empty value gives `Left("Empty access-control-allow-headers header")`.
     * Each item is trimmed, and empty items are dropped; the names are not
     * checked.
     */
    def parse(value: String): Either[String, AccessControlAllowHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-allow-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlAllowHeaders(Chunk.fromArray(parts)))
    }

    /** Writes the names joined with `, `. */
    def render(h: AccessControlAllowHeaders): String =
      h.headers.mkString(", ")
  }

  /**
   * The `Access-Control-Allow-Credentials` response header: whether a
   * cross-origin page may send credentials. `allow` is `true` to allow them.
   */
  final case class AccessControlAllowCredentials(allow: Boolean) extends zio.http.Header {

    /**
     * Returns the name of this header, `AccessControlAllowCredentials.name`.
     */
    def headerName: String = AccessControlAllowCredentials.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlAllowCredentials.render` returns.
     */
    def renderedValue: String = AccessControlAllowCredentials.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlAllowCredentials]]: it parses
   * and renders the `access-control-allow-credentials` header.
   */
  object AccessControlAllowCredentials extends Typed[AccessControlAllowCredentials] {

    /** The header name, `access-control-allow-credentials`. */
    val name: String = "access-control-allow-credentials"

    /** Credentials allowed: `true`. */
    val Allow: AccessControlAllowCredentials = AccessControlAllowCredentials(true)

    /** Credentials not allowed: `false`. */
    val Deny: AccessControlAllowCredentials = AccessControlAllowCredentials(false)

    /** Another name for `Deny`. */
    val DoNotAllow: AccessControlAllowCredentials = Deny

    /**
     * Trims and lower-cases the value, then maps `true` to `Allow` and `false`
     * to `Deny`. Any other value gives
     * `Left("Invalid access-control-allow-credentials: <value>")` with the
     * trimmed, lower-cased text.
     */
    def parse(value: String): Either[String, AccessControlAllowCredentials] =
      value.trim.toLowerCase match {
        case "true"  => Right(Allow)
        case "false" => Right(Deny)
        case other   => Left(s"Invalid access-control-allow-credentials: $other")
      }

    /** Writes `true` or `false`. */
    def render(h: AccessControlAllowCredentials): String =
      if (h.allow) "true" else "false"
  }

  /**
   * The `Access-Control-Expose-Headers` response header: the response headers
   * that a cross-origin page may read.
   */
  final case class AccessControlExposeHeaders(headers: Chunk[String]) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlExposeHeaders.name`. */
    def headerName: String = AccessControlExposeHeaders.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlExposeHeaders.render` returns.
     */
    def renderedValue: String = AccessControlExposeHeaders.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlExposeHeaders]]: it parses
   * and renders the `access-control-expose-headers` header.
   */
  object AccessControlExposeHeaders extends Typed[AccessControlExposeHeaders] {

    /** The header name, `access-control-expose-headers`. */
    val name: String = "access-control-expose-headers"

    /** Every header name: an asterisk. */
    val All: AccessControlExposeHeaders = AccessControlExposeHeaders(Chunk("*"))

    /** Builds the header from one or more header names. */
    def apply(first: String, rest: String*): AccessControlExposeHeaders =
      AccessControlExposeHeaders(Chunk.fromIterable(first +: rest))

    /**
     * Parses a comma-separated list of header names. It trims the value, and an
     * empty value gives `Left("Empty access-control-expose-headers header")`.
     * Each item is trimmed, and empty items are dropped; the names are not
     * checked.
     */
    def parse(value: String): Either[String, AccessControlExposeHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-expose-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlExposeHeaders(Chunk.fromArray(parts)))
    }

    /** Writes the names joined with `, `. */
    def render(h: AccessControlExposeHeaders): String =
      h.headers.mkString(", ")
  }

  /**
   * The `Access-Control-Max-Age` response header: how many seconds a browser
   * may cache the result of a preflight request.
   */
  final case class AccessControlMaxAge(seconds: Long) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlMaxAge.name`. */
    def headerName: String = AccessControlMaxAge.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlMaxAge.render` returns.
     */
    def renderedValue: String = AccessControlMaxAge.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlMaxAge]]: it parses and
   * renders the `access-control-max-age` header.
   */
  object AccessControlMaxAge extends Typed[AccessControlMaxAge] {

    /** The header name, `access-control-max-age`. */
    val name: String = "access-control-max-age"

    /**
     * Parses the trimmed value as a `Long`. A negative number gives
     * `Left("Invalid access-control-max-age: <number>")`, and a value that is
     * not an integer gives `Left("Invalid access-control-max-age: <value>")`.
     */
    def parse(value: String): Either[String, AccessControlMaxAge] =
      try {
        val s = value.trim.toLong
        if (s < 0) Left(s"Invalid access-control-max-age: $s")
        else Right(AccessControlMaxAge(s))
      } catch {
        case _: NumberFormatException => Left(s"Invalid access-control-max-age: $value")
      }

    /** Writes `seconds` in decimal. */
    def render(h: AccessControlMaxAge): String = h.seconds.toString
  }

  /**
   * The `Access-Control-Request-Headers` request header: the headers a
   * preflight request says the real request will send.
   */
  final case class AccessControlRequestHeaders(headers: Chunk[String]) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlRequestHeaders.name`. */
    def headerName: String = AccessControlRequestHeaders.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlRequestHeaders.render` returns.
     */
    def renderedValue: String = AccessControlRequestHeaders.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlRequestHeaders]]: it parses
   * and renders the `access-control-request-headers` header.
   */
  object AccessControlRequestHeaders extends Typed[AccessControlRequestHeaders] {

    /** The header name, `access-control-request-headers`. */
    val name: String = "access-control-request-headers"

    /** Builds the header from one or more header names. */
    def apply(first: String, rest: String*): AccessControlRequestHeaders =
      AccessControlRequestHeaders(Chunk.fromIterable(first +: rest))

    /**
     * Parses a comma-separated list of header names. It trims the value, and an
     * empty value gives `Left("Empty access-control-request-headers header")`.
     * Each item is trimmed, and empty items are dropped; the names are not
     * checked.
     */
    def parse(value: String): Either[String, AccessControlRequestHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-request-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlRequestHeaders(Chunk.fromArray(parts)))
    }

    /** Writes the names joined with `, `. */
    def render(h: AccessControlRequestHeaders): String =
      h.headers.mkString(", ")
  }

  /**
   * The `Access-Control-Request-Method` request header: the method that the
   * real request will use, sent in a preflight request.
   */
  final case class AccessControlRequestMethod(method: Method) extends zio.http.Header {

    /** Returns the name of this header, `AccessControlRequestMethod.name`. */
    def headerName: String = AccessControlRequestMethod.name

    /**
     * The value of this header as it is written on the wire, the same text
     * `AccessControlRequestMethod.render` returns.
     */
    def renderedValue: String = AccessControlRequestMethod.render(this)
  }

  /**
   * The [[Header.Typed]] codec for [[AccessControlRequestMethod]]: it parses
   * and renders the `access-control-request-method` header.
   */
  object AccessControlRequestMethod extends Typed[AccessControlRequestMethod] {

    /** The header name, `access-control-request-method`. */
    val name: String = "access-control-request-method"

    /**
     * Trims the value and reads it with `Method.fromString`, which accepts
     * upper-case names only. An unknown name gives
     * `Left("Unknown method: <value>")`.
     */
    def parse(value: String): Either[String, AccessControlRequestMethod] = {
      val trimmed = value.trim
      Method.fromString(trimmed) match {
        case Some(m) => Right(AccessControlRequestMethod(m))
        case None    => Left(s"Unknown method: $trimmed")
      }
    }

    /** Writes the method name. */
    def render(h: AccessControlRequestMethod): String = h.method.name
  }

  final case class UserAgent(product: String) extends zio.http.Header {
    def headerName: String    = UserAgent.name
    def renderedValue: String = product
  }

  object UserAgent extends Typed[UserAgent] {
    val name: String                                    = "user-agent"
    def parse(value: String): Either[String, UserAgent] = Right(UserAgent(value.trim))
    def render(h: UserAgent): String                    = h.renderedValue
  }

  final case class Server(product: String) extends zio.http.Header {
    def headerName: String    = Server.name
    def renderedValue: String = product
  }

  object Server extends Typed[Server] {
    val name: String                                 = "server"
    def parse(value: String): Either[String, Server] = Right(Server(value.trim))
    def render(h: Server): String                    = h.renderedValue
  }

  final case class Date(value: String) extends zio.http.Header {
    def headerName: String    = Date.name
    def renderedValue: String = value
  }

  object Date extends Typed[Date] {
    val name: String                               = "date"
    def parse(value: String): Either[String, Date] = Right(Date(value.trim))
    def render(h: Date): String                    = h.renderedValue
  }

  final case class Link(value: String) extends zio.http.Header {
    def headerName: String    = Link.name
    def renderedValue: String = value
  }

  object Link extends Typed[Link] {
    val name: String                               = "link"
    def parse(value: String): Either[String, Link] = Right(Link(value.trim))
    def render(h: Link): String                    = h.renderedValue
  }

  final case class RetryAfter(value: String) extends zio.http.Header {
    def headerName: String    = RetryAfter.name
    def renderedValue: String = value
  }

  object RetryAfter extends Typed[RetryAfter] {
    val name: String                                     = "retry-after"
    def parse(value: String): Either[String, RetryAfter] = Right(RetryAfter(value.trim))
    def render(h: RetryAfter): String                    = h.renderedValue
  }

  final case class Allow(methods: Chunk[Method]) extends zio.http.Header {
    def headerName: String    = Allow.name
    def renderedValue: String = methods.map(_.name).mkString(", ")
  }

  object Allow extends Typed[Allow] {
    val name: String = "allow"

    val OPTIONS: Allow = Allow(Method.OPTIONS)
    val GET: Allow     = Allow(Method.GET)
    val HEAD: Allow    = Allow(Method.HEAD)
    val POST: Allow    = Allow(Method.POST)
    val PUT: Allow     = Allow(Method.PUT)
    val PATCH: Allow   = Allow(Method.PATCH)
    val DELETE: Allow  = Allow(Method.DELETE)
    val TRACE: Allow   = Allow(Method.TRACE)
    val CONNECT: Allow = Allow(Method.CONNECT)

    def apply(first: Method, rest: Method*): Allow =
      Allow(Chunk.fromIterable(first +: rest))

    def parse(value: String): Either[String, Allow] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty allow header")
      else {
        val methods = new scala.collection.mutable.ArrayBuffer[Method](parts.length)
        var i       = 0
        while (i < parts.length) {
          Method.fromString(parts(i)) match {
            case Some(m) => methods += m
            case None    => return Left(s"Invalid method: ${parts(i)}")
          }
          i += 1
        }
        Right(Allow(Chunk.fromArray(methods.toArray)))
      }
    }

    def render(h: Allow): String = h.renderedValue
  }

  final case class Expect(value: String) extends zio.http.Header {
    def headerName: String    = Expect.name
    def renderedValue: String = value
  }

  object Expect extends Typed[Expect] {
    val name: String                                 = "expect"
    def parse(value: String): Either[String, Expect] = Right(Expect(value.trim))
    def render(h: Expect): String                    = h.renderedValue
  }

  final case class Range(unit: String, ranges: String) extends zio.http.Header {
    def headerName: String    = Range.name
    def renderedValue: String = unit + "=" + ranges
  }

  object Range extends Typed[Range] {
    val name: String = "range"

    def parse(value: String): Either[String, Range] = {
      val trimmed = value.trim
      val eqIdx   = trimmed.indexOf('=')
      if (eqIdx < 0) Left(s"Invalid range: $trimmed")
      else {
        val unit      = trimmed.substring(0, eqIdx).trim
        val rangeSpec = trimmed.substring(eqIdx + 1).trim
        if (unit.isEmpty || rangeSpec.isEmpty) Left(s"Invalid range: $trimmed")
        else Right(Range(unit, rangeSpec))
      }
    }

    def render(h: Range): String = h.renderedValue
  }

  sealed trait XFrameOptions extends zio.http.Header {
    def headerName: String    = XFrameOptions.name
    def renderedValue: String = XFrameOptions.render(this)
  }

  object XFrameOptions extends Typed[XFrameOptions] {
    val name: String = "x-frame-options"

    case object Deny       extends XFrameOptions
    case object SameOrigin extends XFrameOptions

    def parse(value: String): Either[String, XFrameOptions] =
      value.trim.toLowerCase match {
        case "deny"       => Right(Deny)
        case "sameorigin" => Right(SameOrigin)
        case other        => Left(s"Invalid x-frame-options: $other")
      }

    def render(h: XFrameOptions): String = h match {
      case Deny       => "DENY"
      case SameOrigin => "SAMEORIGIN"
    }
  }

  final case class XRequestedWith(value: String) extends zio.http.Header {
    def headerName: String    = XRequestedWith.name
    def renderedValue: String = value
  }

  object XRequestedWith extends Typed[XRequestedWith] {
    val name: String                                         = "x-requested-with"
    def parse(value: String): Either[String, XRequestedWith] = Right(XRequestedWith(value.trim))
    def render(h: XRequestedWith): String                    = h.renderedValue
  }

  sealed trait DNT extends zio.http.Header {
    def headerName: String    = DNT.name
    def renderedValue: String = DNT.render(this)
  }

  object DNT extends Typed[DNT] {
    val name: String = "dnt"

    case object TrackingAllowed    extends DNT
    case object TrackingNotAllowed extends DNT
    case object Unset              extends DNT

    val NotSpecified: DNT = Unset

    def parse(value: String): Either[String, DNT] =
      value.trim match {
        case "0"    => Right(TrackingAllowed)
        case "1"    => Right(TrackingNotAllowed)
        case "null" => Right(Unset)
        case other  => Left(s"Invalid dnt: $other")
      }

    def render(h: DNT): String = h match {
      case TrackingAllowed    => "0"
      case TrackingNotAllowed => "1"
      case Unset              => "null"
    }
  }

  final case class UpgradeInsecureRequests(upgrade: Boolean) extends zio.http.Header {
    def headerName: String    = UpgradeInsecureRequests.name
    def renderedValue: String = if (upgrade) "1" else "0"
  }

  object UpgradeInsecureRequests extends Typed[UpgradeInsecureRequests] {
    val name: String = "upgrade-insecure-requests"

    def parse(value: String): Either[String, UpgradeInsecureRequests] =
      value.trim match {
        case "1"   => Right(UpgradeInsecureRequests(true))
        case "0"   => Right(UpgradeInsecureRequests(false))
        case other => Left(s"Invalid upgrade-insecure-requests: $other")
      }

    def render(h: UpgradeInsecureRequests): String = h.renderedValue
  }

  final case class ClearSiteData(directives: Chunk[String]) extends zio.http.Header {
    def headerName: String    = ClearSiteData.name
    def renderedValue: String = directives.map(d => "\"" + d + "\"").mkString(", ")
  }

  object ClearSiteData extends Typed[ClearSiteData] {
    val name: String = "clear-site-data"

    def parse(value: String): Either[String, ClearSiteData] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty clear-site-data header")
      else {
        val directives = parts.map { p =>
          val stripped = p.trim
          if (stripped.startsWith("\"") && stripped.endsWith("\""))
            stripped.substring(1, stripped.length - 1)
          else
            stripped
        }
        Right(ClearSiteData(Chunk.fromArray(directives)))
      }
    }

    def render(h: ClearSiteData): String = h.renderedValue
  }

  final case class SecWebSocketAccept(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketAccept.name
    def renderedValue: String = value
  }

  object SecWebSocketAccept extends Typed[SecWebSocketAccept] {
    val name: String                                             = "sec-websocket-accept"
    def parse(value: String): Either[String, SecWebSocketAccept] = Right(SecWebSocketAccept(value.trim))
    def render(h: SecWebSocketAccept): String                    = h.renderedValue
  }

  final case class SecWebSocketExtensions(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketExtensions.name
    def renderedValue: String = value
  }

  object SecWebSocketExtensions extends Typed[SecWebSocketExtensions] {
    val name: String                                                 = "sec-websocket-extensions"
    def parse(value: String): Either[String, SecWebSocketExtensions] = Right(SecWebSocketExtensions(value.trim))
    def render(h: SecWebSocketExtensions): String                    = h.renderedValue
  }

  final case class SecWebSocketKey(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketKey.name
    def renderedValue: String = value
  }

  object SecWebSocketKey extends Typed[SecWebSocketKey] {
    val name: String                                          = "sec-websocket-key"
    def parse(value: String): Either[String, SecWebSocketKey] = Right(SecWebSocketKey(value.trim))
    def render(h: SecWebSocketKey): String                    = h.renderedValue
  }

  final case class SecWebSocketLocation(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketLocation.name
    def renderedValue: String = value
  }

  object SecWebSocketLocation extends Typed[SecWebSocketLocation] {
    val name: String                                               = "sec-websocket-location"
    def parse(value: String): Either[String, SecWebSocketLocation] = Right(SecWebSocketLocation(value.trim))
    def render(h: SecWebSocketLocation): String                    = h.renderedValue
  }

  final case class SecWebSocketOrigin(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketOrigin.name
    def renderedValue: String = value
  }

  object SecWebSocketOrigin extends Typed[SecWebSocketOrigin] {
    val name: String                                             = "sec-websocket-origin"
    def parse(value: String): Either[String, SecWebSocketOrigin] = Right(SecWebSocketOrigin(value.trim))
    def render(h: SecWebSocketOrigin): String                    = h.renderedValue
  }

  final case class SecWebSocketProtocol(protocols: Chunk[String]) extends zio.http.Header {
    def headerName: String    = SecWebSocketProtocol.name
    def renderedValue: String = protocols.mkString(", ")
  }

  object SecWebSocketProtocol extends Typed[SecWebSocketProtocol] {
    val name: String = "sec-websocket-protocol"

    def apply(first: String, rest: String*): SecWebSocketProtocol =
      SecWebSocketProtocol(Chunk.fromIterable(first +: rest))

    def parse(value: String): Either[String, SecWebSocketProtocol] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty sec-websocket-protocol header")
      else Right(SecWebSocketProtocol(Chunk.fromArray(parts)))
    }

    def render(h: SecWebSocketProtocol): String = h.renderedValue
  }

  final case class SecWebSocketVersion(version: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketVersion.name
    def renderedValue: String = version
  }

  object SecWebSocketVersion extends Typed[SecWebSocketVersion] {
    val name: String                                              = "sec-websocket-version"
    def parse(value: String): Either[String, SecWebSocketVersion] = Right(SecWebSocketVersion(value.trim))
    def render(h: SecWebSocketVersion): String                    = h.renderedValue
  }

}
