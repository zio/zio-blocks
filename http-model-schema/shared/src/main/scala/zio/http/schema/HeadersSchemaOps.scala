package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.http.Headers

final class HeadersSchemaOps(private val headers: Headers) extends AnyVal {

  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    headers.rawGet(name) match {
      case None      => Left(HeaderError.Missing(name))
      case Some(raw) => StringDecoder.decode(raw, schema).left.map(e => HeaderError.Malformed(name, raw, e))
    }

  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] = {
    val values = headers.rawGetAll(name)
    if (values.isEmpty) Left(HeaderError.Missing(name))
    else {
      val builder = Chunk.newBuilder[T]
      var i       = 0
      while (i < values.length) {
        StringDecoder.decode(values(i), schema) match {
          case Right(v) => builder += v
          case Left(e)  => return Left(HeaderError.Malformed(name, values(i), e))
        }
        i += 1
      }
      Right(builder.result())
    }
  }

  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    header[T](name).getOrElse(default)
}
