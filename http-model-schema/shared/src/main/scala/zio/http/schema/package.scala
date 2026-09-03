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

package zio.http

import scala.language.implicitConversions

import zio.http.{Headers, QueryParams, Request, Response}

/**
 * Typed, schema-based access to query parameters and headers. With
 * `import zio.http.schema._` in scope, it adds `query`, `queryAll` and
 * `queryOrElse` to [[zio.http.QueryParams]] and [[zio.http.Request]], and
 * `header`, `headerAll` and `headerOrElse` to [[zio.http.Headers]], `Request`
 * and [[zio.http.Response]]; on `Request` and `Response`, `header[T](name)`
 * does not compile, a known bug, so call it on `request.headers`. Failures are
 * a [[QueryParamError]] or a [[HeaderError]].
 *
 * To read or write a whole case class at once, derive a [[QueryCodec]] with
 * `Schema[A].derive(DefaultQueryFormat)` or a [[HeaderCodec]] with
 * `Schema[A].derive(DefaultHeaderFormat)`. That derivation chooses between the
 * three parallel query/header modelings in this codebase:
 *
 *   - Typed [[zio.http.Header.Codec]]: one hand-written header value with
 *     custom parse/render logic. Reaches for this when a single header needs
 *     bespoke validation that a primitive schema cannot express; decoded via
 *     the `HeadersSchemaOps.header(codec)` overloads.
 *   - Schema-based `zio.blocks.endpoint.HttpCodec` atoms (in the `endpoint/`
 *     module, documented here only): endpoint descriptors that model query,
 *     header, body, and status parts of an API operation for routing and
 *     documentation. Reaches for this when describing a whole endpoint, not
 *     when reading values out of a request.
 *   - Derived [[zio.http.schema.QueryCodec]] / [[zio.http.schema.HeaderCodec]]
 *     (this module): a whole case class mapped onto a collection of query
 *     parameters or headers through `Schema` derivation
 *     (`Schema[A].derive(DefaultQueryFormat)` /
 *     `Schema[A].derive(DefaultHeaderFormat)`). Reaches for this when a
 *     record's fields each become one parameter or header.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema/ Schema-Based Typed Access]]
 * @see
 *   [[https://zioblocks.com/reference/http-model/schema-codecs/ HeaderCodec and QueryCodec]]
 */
package object schema {

  /**
   * Adds `query`, `queryAll` and `queryOrElse` to a [[zio.http.QueryParams]].
   */
  implicit def queryParamsSchemaOps(qp: QueryParams): QueryParamsSchemaOps = new QueryParamsSchemaOps(qp)

  /**
   * Adds `header`, `headerAll` and `headerOrElse` to a [[zio.http.Headers]].
   */
  implicit def headersSchemaOps(headers: Headers): HeadersSchemaOps = new HeadersSchemaOps(headers)

  /**
   * Adds the typed query and header reads of [[RequestSchemaOps]] to a
   * [[zio.http.Request]]. `request.header[T](name)` never reaches it, a known
   * bug: `Request#header` takes a `Header.Codec` and wins.
   */
  implicit def requestSchemaOps(request: Request): RequestSchemaOps = new RequestSchemaOps(request)

  /**
   * Adds the typed header reads of [[ResponseSchemaOps]] to a
   * [[zio.http.Response]]. `response.header[T](name)` never reaches it, a known
   * bug: `Response#header` takes a `Header.Codec` and wins.
   */
  implicit def responseSchemaOps(response: Response): ResponseSchemaOps = new ResponseSchemaOps(response)
}
