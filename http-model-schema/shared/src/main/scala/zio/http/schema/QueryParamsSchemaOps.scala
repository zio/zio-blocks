package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.http.QueryParams

final class QueryParamsSchemaOps(private val qp: QueryParams) extends AnyVal {

  def query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T] =
    qp.getFirst(key) match {
      case None      => Left(QueryParamError.Missing(key))
      case Some(raw) => StringDecoder.decode(raw, schema).left.map(e => QueryParamError.Malformed(key, raw, e))
    }

  def queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]] =
    qp.get(key) match {
      case None         => Left(QueryParamError.Missing(key))
      case Some(values) =>
        val builder = Chunk.newBuilder[T]
        var i       = 0
        while (i < values.length) {
          StringDecoder.decode(values(i), schema) match {
            case Right(v) => builder += v
            case Left(e)  => return Left(QueryParamError.Malformed(key, values(i), e))
          }
          i += 1
        }
        Right(builder.result())
    }

  def queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T =
    query[T](key).getOrElse(default)
}
