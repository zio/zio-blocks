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
