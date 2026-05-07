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

import zio.blocks.docs.Doc
import zio.blocks.combinators.Eithers
import zio.blocks.combinators.Tuples
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.Schema
import zio.http.Status

/**
 * Top-level endpoint descriptor. Combines a route pattern with
 * input/output/error HTTP codecs, authentication, and documentation.
 *
 * Pure data — no `implement*` methods, no `Invocation`, no codec errors.
 */
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
) {
  def in[I2, I3](codec: HttpCodec[CodecKind.Request, I2])(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def in[I2, I3](schema: Schema[I2])(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema))

  def in[I2, I3](schema: Schema[I2], doc: Doc)(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, doc = doc))

  def in[I2, I3](mediaType: MediaType, schema: Schema[I2])(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType)))

  def in[I2, I3](mediaType: MediaType, schema: Schema[I2], doc: Doc)(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType), doc = doc))

  def query[I2, I3](codec: HttpCodec.Query[I2])(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def header[I2, I3](codec: HttpCodec.Header[CodecKind.Request, I2])(using
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  def header[A, I2](name: String, schema: Schema[A])(using
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema))

  def header[A, I2](name: String, schema: Schema[A], doc: Doc)(using
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema, doc = doc))

  def out[O2, O3](codec: HttpCodec[CodecKind.Response, O2])(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = codec | output)

  def out[O2, O3](schema: Schema[O2])(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = (HttpCodec.responseBody(schema) ++ HttpCodec.Ok) | output)

  def out[O2, O3](schema: Schema[O2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = (HttpCodec.responseBody(schema, doc = doc) ++ HttpCodec.Ok) | output)

  def out[O2, O3](status: Status, schema: Schema[O2])(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = (HttpCodec.responseBody(schema) ++ HttpCodec.status(status)) | output)

  def out[O2, O3](mediaType: MediaType, schema: Schema[O2])(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output =
      (HttpCodec.responseBody(schema, mediaTypes = zio.blocks.chunk.Chunk.single(mediaType)) ++ HttpCodec.Ok) | output
    )

  def out[O2, O3](status: Status, schema: Schema[O2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = (HttpCodec.responseBody(schema, doc = doc) ++ HttpCodec.status(status, doc)) | output)

  def out[O2, O3](mediaType: MediaType, schema: Schema[O2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(
      output = (HttpCodec.responseBody(
        schema,
        mediaTypes = zio.blocks.chunk.Chunk.single(mediaType),
        doc = doc
      ) ++ HttpCodec.Ok) | output
    )

  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2])(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(
      output =
        (HttpCodec.responseBody(schema, mediaTypes = zio.blocks.chunk.Chunk.single(mediaType)) ++ HttpCodec.status(
          status
        )) | output
    )

  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(
      output =
        (HttpCodec.responseBody(schema, mediaTypes = zio.blocks.chunk.Chunk.single(mediaType), doc = doc) ++ HttpCodec
          .status(status, doc)) | output
    )

  def outError[E2, E3](codec: HttpCodec[CodecKind.Response, E2])(using
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = codec | error)

  def orOutError[E2, E3](codec: HttpCodec[CodecKind.Response, E2])(using
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = builder.add(error, codec))

  def outError[E2, E3](status: Status, schema: Schema[E2])(using
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = (HttpCodec.responseBody(schema, name = Some("error-response")) ++ HttpCodec.status(status)) | error)

  def orOutError[E2, E3](status: Status, schema: Schema[E2])(using
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    orOutError(HttpCodec.responseBody(schema, name = Some("error-response")) ++ HttpCodec.status(status))

  def outError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error =
      ((HttpCodec.responseBody(schema, name = Some("error-response"), doc = doc) ++ HttpCodec
        .status(status, doc))) | error
    )

  def orOutError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(using
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    orOutError(
      HttpCodec.responseBody(schema, name = Some("error-response"), doc = doc) ++ HttpCodec.status(status, doc)
    )

  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(using
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(
      error = ((HttpCodec.responseBody(
        schema,
        mediaTypes = zio.blocks.chunk.Chunk.single(mediaType),
        name = Some("error-response")
      ) ++ HttpCodec.status(status))) | error
    )

  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(using
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    orOutError(
      HttpCodec.responseBody(
        schema,
        mediaTypes = zio.blocks.chunk.Chunk.single(mediaType),
        name = Some("error-response")
      ) ++ HttpCodec.status(status)
    )

  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(using
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(
      error = ((HttpCodec.responseBody(
        schema,
        mediaTypes = zio.blocks.chunk.Chunk.single(mediaType),
        name = Some("error-response"),
        doc = doc
      ) ++ HttpCodec.status(status, doc))) | error
    )

  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(using
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    orOutError(
      HttpCodec.responseBody(
        schema,
        mediaTypes = zio.blocks.chunk.Chunk.single(mediaType),
        name = Some("error-response"),
        doc = doc
      ) ++ HttpCodec.status(status, doc)
    )

  def outHeader[O2, O3](codec: HttpCodec.Header[CodecKind.Response, O2])(using
    combiner: Tuples.Tuples.WithOut[Output, O2, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = output ++ codec)

  def outHeader[A, O2](name: String, schema: Schema[A])(using
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(name, schema))

  def outHeader[A, O2](name: String, schema: Schema[A], doc: Doc)(using
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

  def query[A, I2](name: String, schema: Schema[A])(using
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
