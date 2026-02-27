package zio.blocks.http

final case class Request(
  method: Method,
  url: URL,
  headers: Headers,
  body: Body,
  version: Version
) {
  def header[H <: Header](headerType: HeaderType[H]): Option[H] = headers.get(headerType)

  def contentType: Option[ContentType] = header(Header.ContentType).map(_.value)

  def path: Path = url.path

  def queryParams: QueryParams = url.queryParams
}

object Request {

  def get(url: URL): Request =
    Request(Method.GET, url, Headers.empty, Body.empty, Version.`Http/1.1`)

  def post(url: URL, body: Body): Request =
    Request(Method.POST, url, Headers.empty, body, Version.`Http/1.1`)
}
