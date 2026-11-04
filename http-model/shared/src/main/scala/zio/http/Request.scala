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
 * An immutable HTTP request: a [[Method]], a [[URL]], [[Headers]], a [[Body]]
 * and a [[Version]]. It is plain data that a server adapter fills from the wire
 * or a client turns into bytes; http-model itself sends nothing. Start from a
 * constructor in the companion, such as `Request.get(url)`, and refine it with
 * the methods that return a copy (`addHeader`, `body`, `addCookie`, ...).
 *
 * {{{
 * val url = URL.parse("https://shop.example.com/api/orders?page=2").toOption.get
 * val request = Request
 *   .get(url)
 *   .addHeader("authorization", "Bearer abc123")
 *   .addHeader("cookie", "session=abc123; theme=dark")
 *
 * request.method                       // GET
 * request.path                         // /api/orders
 * request.queryParams.getFirst("page") // Some(2)
 * request.header(Header.Authorization) // Some(Bearer(abc123))
 * request.cookies                      // Chunk(RequestCookie(session,abc123),RequestCookie(theme,dark))
 * }}}
 *
 * `toString` includes every header value, so it prints credentials such as the
 * `authorization` and `cookie` headers in full; this is a known bug.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#request Request]]
 */
final case class Request(
  method: Method,
  url: URL,
  headers: Headers,
  body: Body,
  version: Version
) {

  /**
   * Returns the first header the codec parses, as [[Headers#get]] does: entries
   * that fail to parse are skipped.
   */
  def header[A](headerCodec: Header.Codec[A]): Option[A] = headers.get(headerCodec)

  /**
   * Returns the content type from the first `content-type` header that parses,
   * or else the body's content type, so the result is never `None`. A request
   * built with `Request.get` and no header gives `application/octet-stream`,
   * the content type of [[Body.empty]].
   */
  def contentType: Option[ContentType] =
    header(Header.ContentType).map(_.value).orElse(Some(body.contentType))

  /** The path of `url`. */
  def path: Path = url.path

  /** The query parameters of `url`. */
  def queryParams: QueryParams = url.queryParams

  /**
   * Parses the cookies of the `cookie` header with `Cookie.parseRequest`, or
   * returns an empty `Chunk` when there is none. Only the first `cookie` header
   * is read; cookies in a second `cookie` header, which HTTP/2 clients may
   * send, are missing from the result. This is a known bug.
   */
  def cookies: zio.blocks.chunk.Chunk[RequestCookie] =
    header(Header.CookieHeader).map(header => Cookie.parseRequest(header.value)).getOrElse(zio.blocks.chunk.Chunk.empty)

  /**
   * Appends a header, keeping existing headers with the same name (see
   * `Headers#add`).
   *
   * {{{
   * val url = URL.parse("/api/cart").toOption.get
   *
   * Request.get(url).addHeader("Accept", "application/json").headers.toList
   * // List((accept,application/json))
   * }}}
   *
   * @throws IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
  def addHeader(name: String, value: String): Request = copy(headers = headers.add(name, value))

  /**
   * Appends a typed header, keeping existing headers with the same name.
   *
   * @throws IllegalArgumentException
   *   if the header's name or rendered value is invalid
   */
  def addHeader(header: Header): Request = copy(headers = headers.add(header))

  /** Appends every entry of `other` after the existing headers. */
  def addHeaders(other: Headers): Request = copy(headers = headers ++ other)

  /**
   * Adds `cookie` to the `cookie` header: the cookies of the first `cookie`
   * header plus `cookie` are rendered into one header that replaces every
   * existing `cookie` header. Cookies in a second `cookie` header are dropped;
   * this is a known bug. Neither the name nor the value is checked, so a `;` or
   * `=` in them changes how the header parses.
   *
   * {{{
   * val url = URL.parse("/api/cart").toOption.get
   *
   * Request.get(url).addCookie(RequestCookie("session", "abc123")).addCookie(RequestCookie("currency", "eur")).cookies
   * // Chunk(RequestCookie(session,abc123),RequestCookie(currency,eur))
   * }}}
   */
  def addCookie(cookie: RequestCookie): Request = {
    val updatedCookies = cookies :+ cookie
    setHeader(Header.CookieHeader(Cookie.renderRequest(updatedCookies)))
  }

  /**
   * Removes every header named `name`, ignoring letter case.
   *
   * @throws IllegalArgumentException
   *   if `name` is not a valid header name
   */
  def removeHeader(name: String): Request = copy(headers = headers.remove(name))

  /**
   * Replaces every header named `name` with one header holding `value` (see
   * `Headers#set`).
   *
   * @throws IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
  def setHeader(name: String, value: String): Request = copy(headers = headers.set(name, value))

  /**
   * Replaces every header with `header`'s name by `header`.
   *
   * @throws IllegalArgumentException
   *   if the header's name or rendered value is invalid
   */
  def setHeader(header: Header): Request = copy(headers = headers.set(header))

  /**
   * Returns a copy with `body` and a `content-type` header set to
   * `body.contentType.render`, replacing any existing `content-type` header.
   * The constructors that take a body, such as `Request.post`, do not add this
   * header.
   *
   * {{{
   * val url = URL.parse("/api/orders").toOption.get
   *
   * Request.get(url).body(Body.fromString("{}")).headers.toList
   * // List((content-type,text/plain; charset=UTF-8))
   * }}}
   */
  def body(body: Body): Request = copy(body = body, headers = headers.set("content-type", body.contentType.render))

  /** Returns a copy with `url` replaced. */
  def url(url: URL): Request = copy(url = url)

  /** Returns a copy with `method` replaced. */
  def method(method: Method): Request = copy(method = method)

  /**
   * Returns a copy with the URL path replaced, keeping the URL's scheme, host,
   * port, query and fragment.
   */
  def path(path: Path): Request = updateUrl(_.path(path))

  /** Returns a copy with `version` replaced. */
  def version(version: Version): Request = copy(version = version)

  /**
   * Returns a copy whose headers are `f` applied to the current ones.
   *
   * {{{
   * val url = URL.parse("/api/orders").toOption.get
   *
   * Request.get(url).addHeader("x-debug", "1").updateHeaders(_.remove("x-debug").add("x-request-id", "req-7")).headers.toList
   * // List((x-request-id,req-7))
   * }}}
   */
  def updateHeaders(f: Headers => Headers): Request = copy(headers = f(headers))

  /** Returns a copy whose URL is `f` applied to the current one. */
  def updateUrl(f: URL => URL): Request = copy(url = f(url))
}

/**
 * Constructors for the common methods. Each returns a request with no headers
 * and HTTP/1.1; the ones without a body parameter use [[Body.empty]]. None of
 * them adds a `content-type` header for the body; use `Request#body` for that.
 *
 * {{{
 * val url = URL.parse("https://shop.example.com/api/orders").toOption.get
 *
 * val list   = Request.get(url)
 * val create = Request.post(url, Body.fromString("sku=tea-01&qty=2"))
 *
 * create.method                    // POST
 * create.headers.size               // 0
 * create.contentType.map(_.render) // Some(text/plain; charset=UTF-8)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#creating-requests Creating Requests]]
 */
object Request {

  /** Creates a `GET` request for `url`. */
  def get(url: URL): Request =
    Request(Method.GET, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  /** Creates a `POST` request for `url` with `body`. */
  def post(url: URL, body: Body): Request =
    Request(Method.POST, url, Headers.empty, body, Version.`HTTP/1.1`)

  /** Creates a `DELETE` request for `url` with an empty body. */
  def delete(url: URL): Request =
    Request(Method.DELETE, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  /** Creates a `PUT` request for `url` with `body`. */
  def put(url: URL, body: Body): Request =
    Request(Method.PUT, url, Headers.empty, body, Version.`HTTP/1.1`)

  /** Creates a `PATCH` request for `url` with `body`. */
  def patch(url: URL, body: Body): Request =
    Request(Method.PATCH, url, Headers.empty, body, Version.`HTTP/1.1`)

  /** Creates a `HEAD` request for `url` with an empty body. */
  def head(url: URL): Request =
    Request(Method.HEAD, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  /** Creates a `OPTIONS` request for `url` with an empty body. */
  def options(url: URL): Request =
    Request(Method.OPTIONS, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)
}
