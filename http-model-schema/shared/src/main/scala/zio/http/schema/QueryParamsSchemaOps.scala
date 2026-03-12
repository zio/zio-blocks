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
