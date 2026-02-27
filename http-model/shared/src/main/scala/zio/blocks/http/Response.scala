package zio.blocks.http

final case class Response(
  status: Status,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  version: Version = Version.`Http/1.1`
) {
  def header[H <: Header](headerType: HeaderType[H]): Option[H] = headers.get(headerType)

  def contentType: Option[ContentType] = header(Header.ContentType).map(_.value)
}

object Response {

  val ok: Response = Response(Status.Ok)

  val notFound: Response = Response(Status.NotFound)
}
