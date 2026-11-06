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
 * An HTTP endpoint described as plain data: a [[RoutePattern]] for the method
 * and path, three [[HttpCodec]]s for the request input, the error responses and
 * the success responses, an [[AuthType]], and documentation. Servers, clients
 * and documentation generators read it; the endpoint itself serves and sends
 * nothing.
 *
 * Start with `Endpoint(route)`, which has no input, no outputs and no
 * authentication, then add parts with the builders. Each type parameter tracks
 * what one part decodes to: `PathInput` comes from the route, `Input` from
 * `in`, `query` and `header`, `Err` from `outError` (or `orOutError` on Scala
 * 3), `Output` from `out` and `outHeader`, and `Auth` from `auth`.
 *
 * {{{
 * import zio.blocks.endpoint.RoutePattern._
 * import zio.blocks.schema.Schema
 * import zio.http.{Method, Status}
 *
 * val getNote =
 *   Endpoint(Method.GET / "notes" / PathCodec.int("id"))
 *     .query("format", Schema[String])
 *     .out(Schema[String])
 *     .outError(Status.NotFound, Schema[String])
 *
 * getNote.route.render // GET /notes/{id}
 * }}}
 *
 * The `out` overloads differ in what they take besides the body schema:
 *
 *   - `out(schema)` and `out(schema, doc)` describe a 200 `Ok` response.
 *   - `out(mediaType, schema)` and `out(mediaType, schema, doc)` describe a 200
 *     `Ok` response with one media type.
 *   - `out(status, schema)`, `out(status, schema, doc)`, `out(status,
 *     mediaType, schema)` and `out(status, mediaType, schema, doc)` describe a
 *     response with exactly the status given.
 *   - `out(codec)` adds a response codec as it is.
 *
 * The `outError` and `orOutError` overloads that take a schema always take a
 * status too; the ones that take a codec use it as it is.
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/endpoint/ Endpoint]]
 */
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
) {

  /**
   * Adds a request codec, such as a query parameter, header or body, to the
   * input. The value it decodes joins `Input`: `Unit` drops out, so the first
   * part on a new endpoint gives just its value, and later parts make a tuple.
   */
  def in[I2, I3](codec: HttpCodec[CodecKind.Request, I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  /**
   * Adds a request body described by `schema`, with no media type named. The
   * body value joins `Input`, so the first `in` on a new endpoint gives just
   * the body type.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.Method
   *
   * val createNote: Endpoint[Unit, String, Unit, Unit, AuthType.None] =
   *   Endpoint(Method.POST / "notes").in(Schema[String])
   * }}}
   */
  def in[I2, I3](schema: Schema[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema))

  /**
   * Adds a request body described by `schema`, with documentation for it.
   */
  def in[I2, I3](schema: Schema[I2], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, doc = doc))

  /**
   * Adds a request body described by `schema` in the given media type.
   */
  def in[I2, I3](mediaType: MediaType, schema: Schema[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType)))

  /**
   * Adds a request body described by `schema` in the given media type, with
   * documentation for it.
   */
  def in[I2, I3](mediaType: MediaType, schema: Schema[I2], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.requestBody(schema, zio.blocks.chunk.Chunk.single(mediaType), doc = doc))

  /**
   * Adds a query parameter built with [[HttpCodec.query]], which can carry a
   * default, documentation and examples. The value joins `Input` as with `in`.
   */
  def query[I2, I3](codec: HttpCodec.Query[I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  /**
   * Adds a request header built with [[HttpCodec.requestHeader]]. The value
   * joins `Input` as with `in`.
   */
  def header[I2, I3](codec: HttpCodec.Header[CodecKind.Request, I2])(implicit
    combiner: Tuples.Tuples.WithOut[Input, I2, I3]
  ): Endpoint[PathInput, I3, Err, Output, Auth] =
    copy(input = input ++ codec)

  /**
   * Adds a request header called `name` whose value `schema` describes. The
   * value joins `Input` as with `in`.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.Method
   *
   * val listNotes: Endpoint[Unit, String, Unit, Unit, AuthType.None] =
   *   Endpoint(Method.GET / "notes").header("x-tenant", Schema[String])
   * }}}
   */
  def header[A, I2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema))

  /**
   * Adds a typed request header read and written by a
   * [[zio.http.Header.Codec]], under the codec's header name (for example
   * `content-type` for `Header.ContentType`). The parsed header value joins
   * `Input` as with `in`.
   */
  def header[A, I2](headerCodec: Header.Codec[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(headerCodec))

  /**
   * Adds a request header called `name` whose value `schema` describes, with
   * documentation for it.
   */
  def header[A, I2](name: String, schema: Schema[A], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    header(HttpCodec.requestHeader(name, schema, doc = doc))

  /**
   * Adds a success response codec as it is, on the left of the current `Output`
   * (see `out(schema)` for the resulting type).
   */
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
   * Adds a success response: a body described by `schema` with status 200 `Ok`
   * ([[HttpCodec.Ok]]). Use `out(status, schema)` for any other status; the
   * class description lists every overload.
   *
   * Each `out` puts the new response on the left of the current `Output`, so
   * `out(a).out(b)` gives `Either[Either[B, A], Unit]`. The trailing `Unit` is
   * the empty output of `Endpoint(route)`, which the first `out` keeps as an
   * alternative instead of replacing it: a known bug.
   *
   * On Scala 3, a type written on the expression that calls `out` does not
   * compile ("No given instance of type ... Eithers.WithOut[...]"); store the
   * value first and give the type to a second `val`, as in the example. This is
   * a known bug.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.Method
   *
   * val getNote = Endpoint(Method.GET / "notes" / PathCodec.int("id")).out(Schema[String])
   *
   * val typed: Endpoint[Int, Unit, Unit, Either[String, Unit], AuthType.None] = getNote
   * }}}
   */
  def out[O2, O3](schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, None, schema, Doc.empty)
    copy(output = (body ++ status) | output)
  }

  /**
   * Like `out(schema)`, with documentation for the body and status: a body
   * described by `schema` with status 200 `Ok`.
   */
  def out[O2, O3](schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, None, schema, doc)
    copy(output = (body ++ status) | output)
  }

  /**
   * Adds a success response: a body described by `schema` with exactly the
   * given status.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.{Method, Status}
   *
   * val createNote =
   *   Endpoint(Method.POST / "notes").in(Schema[String]).out(Status.Created, Schema[Int])
   *
   * createNote.route.render // POST /notes
   * }}}
   */
  def out[O2, O3](status: Status, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), None, schema, Doc.empty)
    copy(output = (body ++ statusCodec) | output)
  }

  /**
   * Adds a success response: a body described by `schema` in the given media
   * type, with status 200 `Ok`.
   */
  def out[O2, O3](mediaType: MediaType, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, Some(mediaType), schema, Doc.empty)
    copy(output = (body ++ status) | output)
  }

  /**
   * Adds a success response: a body described by `schema` with exactly the
   * given status, with documentation for both.
   */
  def out[O2, O3](status: Status, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), None, schema, doc)
    copy(output = (body ++ statusCodec) | output)
  }

  /**
   * Adds a success response: a body described by `schema` in the given media
   * type, with status 200 `Ok` and documentation for both.
   */
  def out[O2, O3](mediaType: MediaType, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, status) = bodyOut(None, Some(mediaType), schema, doc)
    copy(output = (body ++ status) | output)
  }

  /**
   * Adds a success response: a body described by `schema` in the given media
   * type, with exactly the given status.
   */
  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2])(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), Some(mediaType), schema, Doc.empty)
    copy(output = (body ++ statusCodec) | output)
  }

  /**
   * Adds a success response: a body described by `schema` in the given media
   * type, with exactly the given status and documentation for both.
   */
  def out[O2, O3](status: Status, mediaType: MediaType, schema: Schema[O2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[O2, Output, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] = {
    val (body, statusCodec) = bodyOut(Some(status), Some(mediaType), schema, doc)
    copy(output = (body ++ statusCodec) | output)
  }

  /**
   * Adds an error response codec as it is, on the left of the current `Err`
   * (see `outError(status, schema)` for the resulting type).
   */
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

  /**
   * Adds an error response codec and makes `Err` a Scala 3 union type. The
   * first call on a new endpoint replaces the empty error, so `Err` becomes
   * `E2`; each later call gives `Err | E2`. A union cannot hold the same type
   * twice, so two calls with the same error type do not compile.
   *
   * Scala 3 only: Scala 2 has no instance of the builder this needs, so a call
   * does not compile there.
   *
   * To decide which codec writes an error value, the union checks the type of
   * the value against the later member first. A value whose type is a subtype
   * of the later member, or matches it only after type erasure (`List[Int]`
   * against `List[String]`), goes to the later codec: a known bug.
   *
   * {{{
   * // scala-3-only
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.{Method, Status}
   *
   * val deleteNote: Endpoint[Int, Unit, String | Int, Unit, AuthType.None] =
   *   Endpoint(Method.DELETE / "notes" / PathCodec.int("id"))
   *     .orOutError(Status.NotFound, Schema[String])
   *     .orOutError(Status.Conflict, Schema[Int])
   * }}}
   */
  def orOutError[E2, E3](codec: HttpCodec[CodecKind.Response, E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] =
    copy(error = builder.add(error, codec))

  /**
   * Adds an error response: a body described by `schema` with the given status.
   * It works on Scala 2 and 3, and puts the new error on the left of the
   * current `Err` with `Either`. As with `out`, the first call keeps the empty
   * error of `Endpoint(route)` as a `Unit` alternative instead of replacing it,
   * so it gives `Either[E2, Unit]`: a known bug. The Scala 3 type-ascription
   * bug of `out(schema)` applies too.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.{Method, Status}
   *
   * val getNote =
   *   Endpoint(Method.GET / "notes" / PathCodec.int("id")).outError(Status.NotFound, Schema[String])
   *
   * val typed: Endpoint[Int, Unit, Either[String, Unit], Unit, AuthType.None] = getNote
   * }}}
   */
  def outError[E2, E3](status: Status, schema: Schema[E2])(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, Doc.empty)
    copy(error = (body ++ statusCodec) | error)
  }

  /**
   * Adds an error response, a body described by `schema` with the given status,
   * to a Scala 3 union `Err` (see `orOutError(codec)`).
   */
  def orOutError[E2, E3](status: Status, schema: Schema[E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, Doc.empty)
    orOutError(body ++ statusCodec)
  }

  /**
   * Adds an error response: a body described by `schema` with the given status,
   * with documentation for both.
   */
  def outError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, doc)
    copy(error = (body ++ statusCodec) | error)
  }

  /**
   * Adds an error response with documentation to a Scala 3 union `Err` (see
   * `orOutError(codec)`).
   */
  def orOutError[E2, E3](status: Status, schema: Schema[E2], doc: Doc)(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, None, schema, doc)
    orOutError(body ++ statusCodec)
  }

  /**
   * Adds an error response: a body described by `schema` in the given media
   * type, with the given status.
   */
  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, Doc.empty)
    copy(error = (body ++ statusCodec) | error)
  }

  /**
   * Adds an error response in the given media type to a Scala 3 union `Err`
   * (see `orOutError(codec)`).
   */
  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2])(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, Doc.empty)
    orOutError(body ++ statusCodec)
  }

  /**
   * Adds an error response: a body described by `schema` in the given media
   * type, with the given status and documentation for both.
   */
  def outError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(implicit
    alternator: Eithers.Eithers.WithOut[E2, Err, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, doc)
    copy(error = (body ++ statusCodec) | error)
  }

  /**
   * Adds an error response in the given media type, with documentation, to a
   * Scala 3 union `Err` (see `orOutError(codec)`).
   */
  def orOutError[E2, E3](status: Status, mediaType: MediaType, schema: Schema[E2], doc: Doc)(implicit
    builder: EndpointUnionErrorBuilder.ErrorBuilder.WithOut[Err, E2, E3]
  ): Endpoint[PathInput, Input, E3, Output, Auth] = {
    val (body, statusCodec) = errorOut(status, Some(mediaType), schema, doc)
    orOutError(body ++ statusCodec)
  }

  /**
   * Adds a response header built with [[HttpCodec.responseHeader]]. Its value
   * joins `Output` as a tuple member, the way request parts join `Input`.
   */
  def outHeader[O2, O3](codec: HttpCodec.Header[CodecKind.Response, O2])(implicit
    combiner: Tuples.Tuples.WithOut[Output, O2, O3]
  ): Endpoint[PathInput, Input, Err, O3, Auth] =
    copy(output = output ++ codec)

  /**
   * Adds a response header called `name` whose value `schema` describes. Its
   * value joins `Output` as a tuple member.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.Method
   *
   * val countNotes: Endpoint[Unit, Unit, Unit, Int, AuthType.None] =
   *   Endpoint(Method.HEAD / "notes").outHeader("x-total-count", Schema[Int])
   * }}}
   */
  def outHeader[A, O2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(name, schema))

  /**
   * Adds a typed response header read and written by a
   * [[zio.http.Header.Codec]], under the codec's header name. The header value
   * joins `Output` as with `outHeader(name, schema)`.
   */
  def outHeader[A, O2](headerCodec: Header.Codec[A])(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(headerCodec))

  /**
   * Adds a response header called `name` whose value `schema` describes, with
   * documentation for it.
   */
  def outHeader[A, O2](name: String, schema: Schema[A], doc: Doc)(implicit
    combiner: Tuples.Tuples.WithOut[Output, A, O2]
  ): Endpoint[PathInput, Input, Err, O2, Auth] =
    outHeader(HttpCodec.responseHeader(name, schema, doc = doc))

  /**
   * Replaces the authentication scheme, and the `Auth` type with it.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.http.Method
   *
   * val deleteNote: Endpoint[Int, Unit, Unit, Unit, AuthType.Bearer] =
   *   Endpoint(Method.DELETE / "notes" / PathCodec.int("id")).auth(AuthType.Bearer)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/endpoint/auth-type/ AuthType]]
   */
  def auth[Auth0 <: AuthType](authType: Auth0): Endpoint[PathInput, Input, Err, Output, Auth0] =
    copy(auth = authType)

  /**
   * Sets the status the endpoint's authentication scheme declares for failed
   * authentication, by wrapping it with [[AuthType.withUnauthorizedStatus]].
   * The `Auth` type becomes a plain `AuthType` refinement, so the scheme's own
   * type (such as `AuthType.Bearer`) is no longer visible in it.
   */
  def unauthorizedStatus(
    status: Status
  ): Endpoint[PathInput, Input, Err, Output, AuthType { type ClientRequirement = auth.ClientRequirement }] =
    copy(auth = auth.withUnauthorizedStatus(status))

  /**
   * Replaces the endpoint's documentation.
   */
  def doc(documentation: Doc): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(doc = documentation)

  /**
   * Adds a query parameter called `name` whose value `schema` describes. The
   * value joins `Input` as with `in`.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.blocks.schema.Schema
   * import zio.http.Method
   *
   * val searchNotes: Endpoint[Unit, (String, Int), Unit, Unit, AuthType.None] =
   *   Endpoint(Method.GET / "notes").query("q", Schema[String]).query("limit", Schema[Int])
   * }}}
   */
  def query[A, I2](name: String, schema: Schema[A])(implicit
    combiner: Tuples.Tuples.WithOut[Input, A, I2]
  ): Endpoint[PathInput, I2, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.query(name, schema))

}

/**
 * Builds endpoints from a route.
 */
object Endpoint {

  /**
   * Starts an endpoint for `route`, with no input, no error or success
   * responses, no authentication and empty documentation.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.http.Method
   *
   * val health: Endpoint[Unit, Unit, Unit, Unit, AuthType.None] = Endpoint(Method.GET / "health")
   * }}}
   */
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
