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
