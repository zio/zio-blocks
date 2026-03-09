package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.http.Request

final class RequestSchemaOps(private val request: Request) extends AnyVal {

  def query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T] =
    new QueryParamsSchemaOps(request.queryParams).query[T](key)

  def queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]] =
    new QueryParamsSchemaOps(request.queryParams).queryAll[T](key)

  def queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T =
    new QueryParamsSchemaOps(request.queryParams).queryOrElse[T](key, default)

  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    new HeadersSchemaOps(request.headers).header[T](name)

  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] =
    new HeadersSchemaOps(request.headers).headerAll[T](name)

  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    new HeadersSchemaOps(request.headers).headerOrElse[T](name, default)
}
