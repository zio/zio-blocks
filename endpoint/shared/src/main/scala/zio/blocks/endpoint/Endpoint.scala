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

package zio.blocks.endpoint

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.combinators.Eithers
import zio.blocks.combinators.Tuples
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.http.{Header, Status}

/**
 * Top-level endpoint descriptor. Combines a route pattern with
 * input/output/error HTTP codecs, authentication, and documentation.
 *
 * Pure data — no `implement*` methods, no `Invocation`, no codec errors.
 *
 * Choosing an `out` overload — pick the row matching what the response needs:
 *
 * {{{
 * | want status?          | want media-type? | call                                 |
 * |-----------------------|------------------|--------------------------------------|
 * | no (defaults to 200)  | no               | out(schema)                          |
 * | no (defaults to 200)  | no (+ docs)      | out(schema, doc)                     |
 * | yes                   | no               | out(status, schema)                  |
 * | no (defaults to 200)  | yes              | out(mediaType, schema)               |
 * | yes (+ docs)          | no (+ docs)      | out(status, schema, doc)             |
 * | no (defaults to 200)  | yes (+ docs)     | out(mediaType, schema, doc)          |
 * | yes                   | yes              | out(status, mediaType, schema)       |
 * | yes (+ docs)          | yes (+ docs)     | out(status, mediaType, schema, doc)  |
 * | (already a codec)     | (already a codec)| out(codec)                           |
 * }}}
 *
 * Only the `out(schema)` / `out(schema, doc)` / `out(mediaType, ...)` rows
 * default the status to 200 `Ok`; every overload that takes an explicit
 * `status` uses exactly that status. The `outError` / `orOutError` overloads
 * mirror these rows but always require an explicit `status` (errors never
 * default to `Ok`).
 */
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
) {
  def in[I2, I3](codec: HttpCodec[CodecKind.Request, I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def in[I2, I3](schema: Schema[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema))

  def in[I2, I3](schema: Schema[I2], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, doc = doc))

  def in[I2, I3](mediaType: MediaType, schema: Schema[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType)))

  def in[I2, I3](mediaType: MediaType, schema: Schema[I2], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType), doc = doc))

  def query[I2, I3](codec: HttpCodec.Query[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def header[I2, I3](codec: HttpCodec.Header[CodecKind.Request, I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def header[A, I2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema))

  /**
   * Adds a typed request header using a [[zio.http.Header.Codec]].
   */
  def header[A, I2](headerCodec: Header.Codec[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(headerCodec))

  def header[A, I2](name: String, schema: Schema[A], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema, doc = doc))

  def out[O2, O3](codec: HttpCodec[CodecKind.Response, O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = codec | output)

  /**
   * Builds the `(body, status)` codec pair shared by every `out(schema, ...)`
   * overload. `status = None` pairs the body with [[HttpCodec.Ok]] (200), while
   * `Some(status)` pairs it with exactly that status. The pair is returned
   * uncombined so each call site keeps its own `++` / `|` implicit resolution,
   * identical to the previous inline expressions.
   */
  private def bodyOut[O2](
    status: Option[Status],
    mediaType: Option[MediaType],
    schema: Schema[O2],
    doc: Doc
  ): (HttpCodec[CodecKind.Response, O2], HttpCodec[CodecKind.Response, Unit]) = {
    val body        = HttpCodec.responseBody(schema, mediaTypes = mediaTypesOf(mediaType), doc = doc)
    val statusCodec = status.fold[HttpCodec[CodecKind.Response, Unit]](HttpCodec.Ok)(HttpCodec.status(_, doc))
    (body, statusCodec)
  }

  private def mediaTypesOf(mediaType: Option[MediaType]): Chunk[MediaType] =
    mediaType.fold(Chunk.empty[MediaType])(Chunk.single(_))

  /**
   * Adds a response body described by `schema`, defaulting the expected status
   * to 200 `Ok` (via [[HttpCodec.Ok]]). This default applies ONLY to the
   * overloads without an explicit `status` — `out(status, schema)` uses exactly
   * the status given and nothing else. See the decision table on [[Endpoint]]
   * for which overload to pick.
   */
  def out[O2, O3](schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, None, schema, Doc.empty)
    copy(output = (body ++ status) | output)
  }

  /**
   * Like `out(schema)`, plus documentation — the expected status still defaults
   * to 200 `Ok` (via [[HttpCodec.Ok]]).
   */
  def out[O2, O3](schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, None, schema, doc)
    copy(output = (body ++ status) | output)
  }

  def out[O2, O3](status: Status, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), None, schema, Doc.empty)
    copy(output = (body ++ statusCodec) | output)
  }

  def out[O2, O3](mediaType: MediaType, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, Some(mediaType), schema, Doc.empty)
    copy(output = (body ++ status) | output)
  }

  def out[O2, O3](status: Status, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), None, schema, doc)
    copy(output = (body ++ statusCodec) | output)
  }

  def out[O2, O3](mediaType: MediaType, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, Some(mediaType), schema, doc)
    copy(output = (body ++ status) | output)
  }

  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), Some(mediaType), schema, Doc.empty)
    copy(output = (body ++ statusCodec) | output)
  }

  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), Some(mediaType), schema, doc)
    copy(output = (body ++ statusCodec) | output)
  }

  def outError[E2, E3](codec: HttpCodec[CodecKind.Response, E2])(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = codec | error)

  /**
   * Builds the `(body, status)` codec pair shared by every
   * `outError(status, ...)` / `orOutError(status, ...)` overload. Unlike
   * `bodyOut`, the status is always explicit — error bodies never default to
   * `Ok`. Returned uncombined so each call site keeps its own `++` / `|`
   * implicit resolution, identical to the previous inline expressions.
   */
  private def errorOut[E2](
    status: Status,
    mediaType: Option[MediaType],
    schema: Schema[E2],
    doc: Doc
  ): (HttpCodec[CodecKind.Response, E2], HttpCodec[CodecKind.Response, Unit]) = {
    val body = HttpCodec.responseBody(
      schema,
      mediaTypes = mediaTypesOf(mediaType),
      name = Some("error-response"),
      doc = doc
    )
    (body, HttpCodec.status(status, doc))
  }

  def orOutError[E2, E3](codec: HttpCodec[CodecKind.Response, E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = builder.add(error, codec))

  def outError[E2, E3](status: Status, schema: Schema[E2])(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, Doc.empty)
    copy(error = (body ++ statusCodec) | error)
  }

  def orOutError[E2, E3](status: Status, schema: Schema[E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, Doc.empty)
    orOutError(body ++ statusCodec)
  }

  def outError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, doc)
    copy(error = (body ++ statusCodec) | error)
  }

  def orOutError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, doc)
    orOutError(body ++ statusCodec)
  }

  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, Doc.empty)
    copy(error = (body ++ statusCodec) | error)
  }

  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, Doc.empty)
    orOutError(body ++ statusCodec)
  }

  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, doc)
    copy(error = (body ++ statusCodec) | error)
  }

  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, doc)
    orOutError(body ++ statusCodec)
  }

  def outHeader[O2, O3](codec: HttpCodec.Header[CodecKind.Response, O2])(implicit
    combiner: Tuples.Tuples.WithOut[Output, O2, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = output ++ codec)

  def outHeader[A, O2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(name, schema))

  /**
   * Adds a typed response header using a [[zio.http.Header.Codec]].
   */
  def outHeader[A, O2](headerCodec: Header.Codec[A])(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(headerCodec))

  def outHeader[A, O2](name: String, schema: Schema[A], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(name, schema, doc = doc))

  def auth[Auth0 <: AuthType](authType: Auth0): Endpoint[PathInput, Input, Err, Output, Auth0] =
    copy(auth = authType)

  def unauthorizedStatus(
    status: Status
  ): Endpoint[PathInput, Input, Err, Output, AuthType { type ClientRequirement = auth.ClientRequirement }] =
    copy(auth = auth.withUnauthorizedStatus(status))

  def doc(documentation: Doc): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(doc = documentation)

  def query[A, I2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.query(name, schema))

}

object Endpoint {

  def apply[PathInput](route: RoutePattern[PathInput]): Endpoint[PathInput, Unit, Unit, Unit, AuthType.None] =
    Endpoint(
      route = route,
      input = HttpCodec.Empty,
      error = HttpCodec.Empty,
      output = HttpCodec.Empty,
      auth = AuthType.None,
      doc = Doc.empty
    )
}
