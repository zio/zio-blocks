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
 * Immutable HTTP request consisting of method, URL, headers, body, and protocol
 * version.
 *
 * Convenience accessors `path` and `queryParams` delegate to the `url` field.
 */
final case class Request(
  method: Method,
  url: URL,
  headers: Headers,
  body: Body,
  version: Version
) {
  def header[H <: Header](headerType: Header.Typed[H]): Option[H] = headers.get(headerType)

  def contentType: Option[ContentType] = header(zio.http.headers.ContentType).map(_.value)

  def path: Path = url.path

  def queryParams: QueryParams = url.queryParams

  def addHeader(name: String, value: String): Request = copy(headers = headers.add(name, value))
  def addHeaders(other: Headers): Request             = copy(headers = headers ++ other)
  def removeHeader(name: String): Request             = copy(headers = headers.remove(name))
  def setHeader(name: String, value: String): Request = copy(headers = headers.set(name, value))

  def body(body: Body): Request          = copy(body = body)
  def url(url: URL): Request             = copy(url = url)
  def method(method: Method): Request    = copy(method = method)
  def version(version: Version): Request = copy(version = version)

  def updateHeaders(f: Headers => Headers): Request = copy(headers = f(headers))
  def updateUrl(f: URL => URL): Request             = copy(url = f(url))
}

object Request {

  def get(url: URL): Request =
    Request(Method.GET, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  def post(url: URL, body: Body): Request =
    Request(Method.POST, url, Headers.empty, body, Version.`HTTP/1.1`)

  def delete(url: URL): Request =
    Request(Method.DELETE, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  def put(url: URL, body: Body): Request =
    Request(Method.PUT, url, Headers.empty, body, Version.`HTTP/1.1`)

  def patch(url: URL, body: Body): Request =
    Request(Method.PATCH, url, Headers.empty, body, Version.`HTTP/1.1`)

  def head(url: URL): Request =
    Request(Method.HEAD, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)

  def options(url: URL): Request =
    Request(Method.OPTIONS, url, Headers.empty, Body.empty, Version.`HTTP/1.1`)
}
