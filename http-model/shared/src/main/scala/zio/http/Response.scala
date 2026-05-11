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
 * Immutable HTTP response consisting of status, headers, body, and protocol
 * version.
 */
final case class Response(
  status: Status,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  version: Version = Version.`HTTP/1.1`
) {

  /**
   * Decodes the first response header matching the supplied codec.
   */
  def header[A](headerCodec: Header.Codec[A]): Option[A] = headers.get(headerCodec)

  /**
   * Returns this response's content type.
   *
   * The typed `Content-Type` header is preferred when present and parseable. If
   * the header is absent or cannot be parsed as a typed `Content-Type` header,
   * this method falls back to the body's content type.
   */
  def contentType: Option[ContentType] =
    header(Header.ContentType).map(_.value).orElse(Some(body.contentType))

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

  def addHeader(name: String, value: String): Response = copy(headers = headers.add(name, value))
  def addHeader(header: Header): Response              = copy(headers = headers.add(header))
  def addHeaders(other: Headers): Response             = copy(headers = headers ++ other)
  def removeHeader(name: String): Response             = copy(headers = headers.remove(name))
  def setHeader(name: String, value: String): Response = copy(headers = headers.set(name, value))
  def setHeader(header: Header): Response              = copy(headers = headers.set(header))

  /**
   * Returns a copy with the supplied body and a synchronized `Content-Type`
   * header.
   *
   * This overwrites any existing `Content-Type` header with
   * `body.contentType.render` so the headers remain aligned with the body.
   */
  def body(body: Body): Response          = copy(body = body, headers = headers.set("content-type", body.contentType.render))
  def status(status: Status): Response    = copy(status = status)
  def version(version: Version): Response = copy(version = version)

  def updateHeaders(f: Headers => Headers): Response = copy(headers = f(headers))

  def addCookie(cookie: ResponseCookie): Response = addHeader("set-cookie", Cookie.renderResponse(cookie))
}

object Response {

  val ok: Response = Response(Status.Ok)

  val notFound: Response = Response(Status.NotFound)

  val badRequest: Response          = Response(Status.BadRequest)
  val unauthorized: Response        = Response(Status.Unauthorized)
  val forbidden: Response           = Response(Status.Forbidden)
  val internalServerError: Response = Response(Status.InternalServerError)
  val serviceUnavailable: Response  = Response(Status.ServiceUnavailable)

  def ok(body: Body): Response =
    Response(Status.Ok, Headers("content-type" -> body.contentType.render), body)

  def text(body: String): Response =
    text(Status.Ok, body)

  def text(status: Status, body: String): Response = {
    val responseBody = Body.fromString(body)
    Response(status, Headers("content-type" -> responseBody.contentType.render), responseBody)
  }

  def json(body: String): Response =
    json(Status.Ok, body)

  def json(status: Status, body: String): Response =
    Response(
      status,
      Headers("content-type" -> "application/json"),
      Body.fromArray(body.getBytes("UTF-8"), ContentType.`application/json`)
    )

  def redirect(location: String, isPermanent: Boolean = false): Response =
    Response(
      if (isPermanent) Status.PermanentRedirect else Status.TemporaryRedirect,
      Headers("location" -> location)
    )

  def seeOther(location: String): Response =
    Response(Status.SeeOther, Headers("location" -> location))
}
