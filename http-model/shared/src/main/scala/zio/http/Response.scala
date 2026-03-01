package zio.http

/**
 * Immutable HTTP response consisting of status, headers, body, and protocol
 * version.
 */
final case class Response(
  status: Status,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  version: Version = Version.`Http/1.1`
) {
  def header[H <: Header](headerType: Header.Typed[H]): Option[H] = headers.get(headerType)

  def contentType: Option[ContentType] = header(zio.http.headers.ContentType).map(_.value)
}

object Response {

  val ok: Response = Response(Status.Ok)

  val notFound: Response = Response(Status.NotFound)
}
