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

/**
 * Typed reads of a [[zio.http.Request]]'s query parameters and headers. Each
 * method forwards to [[QueryParamsSchemaOps]] on `request.queryParams` or to
 * [[HeadersSchemaOps]] on `request.headers`, with the same rules: query keys
 * match exactly, header names in any letter case, and only the types that
 * `QueryParamsSchemaOps` lists decode. `import zio.http.schema._` adds the
 * methods to every `Request`.
 *
 * `request.header[T](name)` does not compile, a known bug: `Request` has its
 * own `header` method, which takes a `Header.Codec`, and once a type argument
 * is given the compiler commits to that member instead of trying the implicit
 * conversion. Write `request.headers.header[T](name)` instead. `headerAll` and
 * `headerOrElse` have no such clash.
 *
 * {{{
 * import zio.http.{Request, URL}
 *
 * val request = Request
 *   .get(URL.parse("/orders?status=open&page=2").toOption.get)
 *   .addHeader("X-Api-Version", "2")
 *
 * request.query[Int]("page")                    // Right(2)
 * request.queryOrElse[Int]("size", 50)          // 50
 * request.headerOrElse[Int]("x-api-version", 1) // 2
 * request.headers.header[Int]("X-Api-Version")  // Right(2)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema/ Schema-Based Typed Access]]
 */
final class RequestSchemaOps(private val request: Request) extends AnyVal {

  /**
   * Decodes the first value of the query parameter `key` as a `T`, as
   * [[QueryParamsSchemaOps]] `query` does on this request's query parameters.
   */
  def query[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, T] =
    new QueryParamsSchemaOps(request.queryParams).query[T](key)

  /**
   * Decodes every value of the query parameter `key` as a `T`, as
   * [[QueryParamsSchemaOps]] `queryAll` does on this request's query
   * parameters.
   */
  def queryAll[T](key: String)(implicit schema: Schema[T]): Either[QueryParamError, Chunk[T]] =
    new QueryParamsSchemaOps(request.queryParams).queryAll[T](key)

  /**
   * Decodes the first value of the query parameter `key` as a `T`, or returns
   * `default` when it is absent or malformed, as [[QueryParamsSchemaOps]]
   * `queryOrElse` does; a malformed value is dropped without a trace, a known
   * bug.
   */
  def queryOrElse[T](key: String, default: => T)(implicit schema: Schema[T]): T =
    new QueryParamsSchemaOps(request.queryParams).queryOrElse[T](key, default)

  /**
   * Decodes the first header named `name` as a `T`, as [[HeadersSchemaOps]]
   * `header` does on this request's headers. Written as
   * `request.header[T](name)`, the call does not compile, a known bug:
   * `Request#header` takes a `Header.Codec` and hides this method. Call
   * `request.headers.header[T](name)`.
   */
  def header[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, T] =
    new HeadersSchemaOps(request.headers).header[T](name)

  /**
   * Decodes every header named `name` as a `T`, as [[HeadersSchemaOps]]
   * `headerAll` does on this request's headers.
   */
  def headerAll[T](name: String)(implicit schema: Schema[T]): Either[HeaderError, Chunk[T]] =
    new HeadersSchemaOps(request.headers).headerAll[T](name)

  /**
   * Decodes the first header named `name` as a `T`, or returns `default` when
   * it is absent or malformed, as [[HeadersSchemaOps]] `headerOrElse` does; a
   * malformed value is dropped without a trace, a known bug.
   */
  def headerOrElse[T](name: String, default: => T)(implicit schema: Schema[T]): T =
    new HeadersSchemaOps(request.headers).headerOrElse[T](name, default)
}
