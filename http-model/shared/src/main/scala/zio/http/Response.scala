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
  def header[H <: Header](headerType: Header.Typed[H]): Option[H] = headers.get(headerType)

  def contentType: Option[ContentType] = header(zio.http.headers.ContentType).map(_.value)

  def addHeader(name: String, value: String): Response = copy(headers = headers.add(name, value))
  def addHeaders(other: Headers): Response             = copy(headers = headers ++ other)
  def removeHeader(name: String): Response             = copy(headers = headers.remove(name))
  def setHeader(name: String, value: String): Response = copy(headers = headers.set(name, value))

  def body(body: Body): Response          = copy(body = body)
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

  def text(body: String): Response =
    Response(Status.Ok, Headers.empty, Body.fromString(body))

  def json(body: String): Response =
    Response(
      Status.Ok,
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
