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
import zio.http.Response

final class ResponseSchemaOps(private val response: Response) extends AnyVal {

  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    new HeadersSchemaOps(response.headers).header[T](name)

  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] =
    new HeadersSchemaOps(response.headers).headerAll[T](name)

  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    new HeadersSchemaOps(response.headers).headerOrElse[T](name, default)
}
