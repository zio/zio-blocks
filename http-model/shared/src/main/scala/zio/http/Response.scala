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
 * An immutable HTTP response: a [[Status]], [[Headers]], a [[Body]] and a
 * [[Version]]. Only the status is required; the other fields default to no
 * headers, [[Body.empty]] and HTTP/1.1. The companion has ready-made responses
 * (`Response.ok`, `Response.notFound`) and constructors that set the body and
 * its `content-type` header together (`Response.text`, `Response.json`).
 *
 * {{{
 * val response = Response
 *   .text(Status.Created, "order 42 created")
 *   .addHeader("location", "/api/orders/42")
 *
 * response.status.code     // 201
 * response.headers.toList  // List((content-type,text/plain; charset=UTF-8), (location,/api/orders/42))
 * response.body.asString() // order 42 created
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#response Response]]
 */
final case class Response(
  status: Status,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  version: Version = Version.`HTTP/1.1`
) {

  /**
   * Returns the first header the codec parses, as [[Headers#get]] does: entries
   * that fail to parse are skipped.
   */
  def header[A](headerCodec: Header.Codec[A]): Option[A] = headers.get(headerCodec)

  /**
   * Returns the content type from the first `content-type` header that parses,
   * or else the body's content type, so the result is never `None`.
   */
  def contentType: Option[ContentType] =
    header(Header.ContentType).map(_.value).orElse(Some(body.contentType))

  /**
   * Parses every `set-cookie` header with `Cookie.parseResponse`, in order. A
   * header that does not parse, such as one without a `name=` part, is skipped.
   *
   * {{{
   * val response = Response.ok
   *   .addCookie(ResponseCookie("session", "abc123", isHttpOnly = true))
   *   .addHeader("set-cookie", "theme=dark; Max-Age=60")
   *
   * response.cookies.map(c => c.name + "=" + c.value) // Chunk(session=abc123,theme=dark)
   * }}}
   */
  def cookies: zio.blocks.chunk.Chunk[ResponseCookie] = {
    val raw     = headers.getAll(Header.SetCookieHeader)
    val builder = zio.blocks.chunk.Chunk.newBuilder[ResponseCookie]
    var i       = 0
    while (i < raw.length) {
      Cookie.parseResponse(raw(i).value) match {
        case Right(cookie) => builder += cookie
        case Left(_)       => ()
      }
      i += 1
    }
    builder.result()
  }

  /**
   * Appends a header, keeping existing headers with the same name (see
   * `Headers#add`).
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
  def addHeader(name: String, value: String): Response = copy(headers = headers.add(name, value))

  /**
   * Appends a typed header, keeping existing headers with the same name.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the header's name or rendered value is invalid
   */
  def addHeader(header: Header): Response = copy(headers = headers.add(header))

  /** Appends every entry of `other` after the existing headers. */
  def addHeaders(other: Headers): Response = copy(headers = headers ++ other)

  /**
   * Removes every header named `name`, ignoring letter case.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name
   */
  def removeHeader(name: String): Response = copy(headers = headers.remove(name))

  /**
   * Replaces every header named `name` with one header holding `value` (see
   * `Headers#set`).
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name or `value` contains CR or LF
   */
  def setHeader(name: String, value: String): Response = copy(headers = headers.set(name, value))

  /**
   * Replaces every header with `header`'s name by `header`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the header's name or rendered value is invalid
   */
  def setHeader(header: Header): Response = copy(headers = headers.set(header))

  /**
   * Returns a copy with `body` and a `content-type` header set to
   * `body.contentType.render`, replacing any existing `content-type` header.
   */
  def body(body: Body): Response = copy(body = body, headers = headers.set("content-type", body.contentType.render))

  /** Returns a copy with `status` replaced. */
  def status(status: Status): Response = copy(status = status)

  /** Returns a copy with `version` replaced. */
  def version(version: Version): Response = copy(version = version)

  /** Returns a copy whose headers are `f` applied to the current ones. */
  def updateHeaders(f: Headers => Headers): Response = copy(headers = f(headers))

  /**
   * Appends a `set-cookie` header rendered by `Cookie.renderResponse`, keeping
   * earlier `set-cookie` headers, so each call adds one cookie.
   *
   * {{{
   * val cookie = ResponseCookie("session", "abc123", path = Some(Path("/")), isSecure = true, isHttpOnly = true)
   *
   * Response.ok.addCookie(cookie).headers.rawGetAll("set-cookie")
   * // Chunk(session=abc123; Path=/; Secure; HttpOnly)
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if the cookie's name or value is invalid, or it has `SameSite=None`
   *   without `Secure` (see `Cookie.renderResponseEither`)
   */
  def addCookie(cookie: ResponseCookie): Response = addHeader("set-cookie", Cookie.renderResponse(cookie))
}

/**
 * Ready-made responses and constructors for common cases. The `val`s carry only
 * a status, with no headers and [[Body.empty]]; the constructors that take a
 * body also set a matching `content-type` header.
 *
 * {{{
 * Response.notFound.status.code                  // 404
 * Response.json("{\"id\":42}").headers.toList       // List((content-type,application/json))
 * Response.redirect("/login").status.code        // 307
 * Response.seeOther("/api/orders/42").headers.toList // List((location,/api/orders/42))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#response-convenience-constructors Response Convenience Constructors]]
 */
object Response {

  /** A `200 OK` response with no headers and an empty body. */
  val ok: Response = Response(Status.Ok)

  /** A `404 Not Found` response with no headers and an empty body. */
  val notFound: Response = Response(Status.NotFound)

  /** A `400 Bad Request` response with no headers and an empty body. */
  val badRequest: Response = Response(Status.BadRequest)

  /** A `401 Unauthorized` response with no headers and an empty body. */
  val unauthorized: Response = Response(Status.Unauthorized)

  /** A `403 Forbidden` response with no headers and an empty body. */
  val forbidden: Response = Response(Status.Forbidden)

  /**
   * A `500 Internal Server Error` response with no headers and an empty body.
   */
  val internalServerError: Response = Response(Status.InternalServerError)

  /** A `503 Service Unavailable` response with no headers and an empty body. */
  val serviceUnavailable: Response = Response(Status.ServiceUnavailable)

  /**
   * A `200 OK` response with `body` and a `content-type` header set to the
   * body's content type.
   */
  def ok(body: Body): Response =
    Response(Status.Ok, Headers("content-type" -> body.contentType.render), body)

  /**
   * A `200 OK` response whose body is `body` encoded as UTF-8, with
   * `content-type: text/plain; charset=UTF-8`.
   */
  def text(body: String): Response =
    text(Status.Ok, body)

  /**
   * A response with `status` whose body is `body` encoded as UTF-8, with
   * `content-type: text/plain; charset=UTF-8`.
   */
  def text(status: Status, body: String): Response = {
    val responseBody = Body.fromString(body)
    Response(status, Headers("content-type" -> responseBody.contentType.render), responseBody)
  }

  /**
   * A `200 OK` response whose body is the JSON text `body` encoded as UTF-8,
   * with `content-type: application/json` (no charset parameter). The text is
   * not checked to be JSON.
   */
  def json(body: String): Response =
    json(Status.Ok, body)

  /**
   * A response with `status` whose body is the JSON text `body` encoded as
   * UTF-8, with `content-type: application/json`. The text is not checked.
   */
  def json(status: Status, body: String): Response =
    Response(
      status,
      Headers("content-type" -> "application/json"),
      Body.fromArray(body.getBytes("UTF-8"), ContentType.`application/json`)
    )

  /**
   * A redirect to `location` with an empty body: `307 Temporary Redirect`, or
   * `308 Permanent Redirect` when `isPermanent` is `true`. Both keep the
   * request method; use [[seeOther]] to send the client on with a `GET`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `location` contains CR or LF
   */
  def redirect(location: String, isPermanent: Boolean = false): Response =
    Response(
      if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect,
      Headers("location" -> location)
    )

  /**
   * A `303 See Other` response to `location` with an empty body, which tells
   * the client to fetch `location` with `GET`, for example after a form post.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `location` contains CR or LF
   */
  def seeOther(location: String): Response =
    Response(Status.SeeOther, Headers("location" -> location))
}
