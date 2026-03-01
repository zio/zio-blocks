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
}

object Request {

  def get(url: URL): Request =
    Request(Method.GET, url, Headers.empty, Body.empty, Version.`Http/1.1`)

  def post(url: URL, body: Body): Request =
    Request(Method.POST, url, Headers.empty, body, Version.`Http/1.1`)
}
