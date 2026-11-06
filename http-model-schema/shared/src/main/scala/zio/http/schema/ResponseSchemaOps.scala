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

/**
 * Typed reads of a [[zio.http.Response]]'s headers. Each method forwards to
 * [[HeadersSchemaOps]] on `response.headers`, with the same rules: names match
 * in any letter case, and only the types that `HeadersSchemaOps` lists decode.
 * `import zio.http.schema._` adds the methods to every `Response`.
 *
 * `response.header[T](name)` does not compile, a known bug: `Response` has its
 * own `header` method, which takes a `Header.Codec`, and once a type argument
 * is given the compiler commits to that member instead of trying the implicit
 * conversion. Write `response.headers.header[T](name)` instead. `headerAll` and
 * `headerOrElse` have no such clash.
 *
 * {{{
 * import zio.http.Response
 *
 * val response = Response.ok
 *   .addHeader("X-RateLimit-Remaining", "99")
 *   .addHeader("X-Order-Id", "1042")
 *
 * response.headerOrElse[Int]("x-ratelimit-remaining", 100) // 99
 * response.headerAll[Long]("X-Order-Id")                    // Right(Chunk(1042))
 * response.headers.header[Long]("X-Order-Id")               // Right(1042)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema/ Schema-Based Typed Access]]
 */
final class ResponseSchemaOps(private val response: Response) extends AnyVal {

  /**
   * Decodes the first header named `name` as a `T`, as [[HeadersSchemaOps]]
   * `header` does on this response's headers. Written as
   * `response.header[T](name)`, the call does not compile, a known bug:
   * `Response#header` takes a `Header.Codec` and hides this method. Call
   * `response.headers.header[T](name)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
   */
  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    new HeadersSchemaOps(response.headers).header[T](name)

  /**
   * Decodes every header named `name` as a `T`, as [[HeadersSchemaOps]]
   * `headerAll` does on this response's headers.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
   */
  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] =
    new HeadersSchemaOps(response.headers).headerAll[T](name)

  /**
   * Decodes the first header named `name` as a `T`, or returns `default` when
   * it is absent or malformed, as [[HeadersSchemaOps]] `headerOrElse` does; a
   * malformed value is dropped without a trace, a known bug.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid header name, such as one with a space
   */
  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    new HeadersSchemaOps(response.headers).headerOrElse[T](name, default)
}
