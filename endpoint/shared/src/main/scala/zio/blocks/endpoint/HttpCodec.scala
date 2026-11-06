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

import scala.annotation.unchecked.uncheckedVariance

import zio.blocks.chunk.Chunk
import zio.blocks.combinators.{Eithers, Tuples}
import zio.blocks.docs.Doc
import zio.blocks.mediatype.MediaType
import zio.blocks.schema.{Schema, SchemaError}
import zio.http.{Header => HttpHeader, Status}

/**
 * The direction of an [[HttpCodec]]: [[CodecKind.Request]] for request parts or
 * [[CodecKind.Response]] for response parts. It exists only as a type, so the
 * compiler keeps request and response parts apart.
 */
sealed trait CodecKind

/** The two directions of an [[HttpCodec]]. */
object CodecKind {

  /**
   * Marks codecs for request parts: query parameters, request headers and
   * request bodies.
   */
  sealed trait Request extends CodecKind

  /**
   * Marks codecs for response parts: statuses, response headers and response
   * bodies.
   */
  sealed trait Response extends CodecKind
}

/**
 * A typed description of HTTP request or response parts: query parameters,
 * headers, bodies and statuses. `K` is the direction ([[CodecKind.Request]] or
 * [[CodecKind.Response]]) and `A` the value the parts decode to. A codec is
 * plain data; servers and clients interpret it.
 *
 * Each part is a case class that carries its [[zio.blocks.schema.Schema]] and
 * its documentation, examples and deprecation note. Combine parts with `++`
 * (both, values in a tuple) and `|` (either one, values in an `Either`). The
 * codecs for typed headers and the `Authorization` header use a `String` schema
 * that parses and renders the header value.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * val page: HttpCodec[CodecKind.Request, (Int, String)] =
 *   HttpCodec.query("page", Schema[Int]) ++ HttpCodec.requestHeader("x-tenant", Schema[String])
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/http-codec/ HttpCodec]]
 */
sealed trait HttpCodec[+K <: CodecKind, A] { self =>

  /**
   * Combines this codec with `that`: both sets of parts are present, and their
   * values join with `Tuples`, so `Unit` drops out and other values form a flat
   * tuple.
   */
  def ++[B, C](that: HttpCodec[K @uncheckedVariance, B])(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): HttpCodec[K, C] =
    HttpCodec.Combine(self, that, combiner)

  /**
   * Offers this codec or `that`: a request or response matches one of them, and
   * the value is an `Either`: `Left` for this codec, `Right` for `that`. When
   * `that` is itself an `Either`, the result is nested to the left, so `A | (B
   * | C)` gives `Either[Either[A, B], C]`.
   *
   * On Scala 3, a type written on the expression that calls `|` does not
   * compile ("No given instance of type ... Eithers.WithOut[...]"); store the
   * value first and give the type to a second `val`, as in the example. This is a known
   * bug.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * val body = HttpCodec.responseBody(Schema[Int]) | HttpCodec.responseBody(Schema[String])
   *
   * val typed: HttpCodec[CodecKind.Response, Either[Int, String]] = body
   * }}}
   */
  def |[B, C](that: HttpCodec[K @uncheckedVariance, B])(implicit
    alternator: Eithers.Eithers.WithOut[A, B, C]
  ): HttpCodec[K, C] =
    HttpCodec.Fallback(self, that, Alternator.fromEithers(alternator))
}

/**
 * Constructors and predefined codecs for request and response parts.
 */
object HttpCodec {

  private def validationFailure(message: String): Nothing =
    throw SchemaError.validationFailed(message)

  private def headerSchema[A](headerType: HttpHeader.Codec[A]): Schema[A] =
    Schema[String].transform[A](
      value => headerType.parse(value).fold(validationFailure, identity),
      value => headerType.render(value)
    )

  private def authorizationSchema[A <: HttpHeader.Authorization](
    expectedScheme: String,
    extract: PartialFunction[HttpHeader.Authorization, A]
  ): Schema[A] =
    Schema[String].transform[A](
      value =>
        HttpHeader.Authorization.parse(value) match {
          case Right(auth) if extract.isDefinedAt(auth) => extract(auth)
          case Right(other)                             =>
            validationFailure(
              s"Expected $expectedScheme authorization header but found ${other.getClass.getSimpleName}"
            )
          case Left(error) => validationFailure(error)
        },
      value => HttpHeader.Authorization.render(value)
    )

  // === Combinators ===

  /** A codec with no parts, decoding to `Unit`. */
  case object Empty extends HttpCodec[Nothing, Unit]

  /**
   * Both codecs, built by `++`: the request or response has the parts of `left`
   * and of `right`, and `combiner` joins their values.
   */
  final case class Combine[K <: CodecKind, A, B, C](
    left: HttpCodec[K, A],
    right: HttpCodec[K, B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends HttpCodec[K, C]

  /**
   * One of two codecs, built by `|` and by the endpoint builders: the request
   * or response matches `left` or `right`, and `alternator` holds the value as
   * either side.
   */
  final case class Fallback[K <: CodecKind, A, B, C](
    left: HttpCodec[K, A],
    right: HttpCodec[K, B],
    alternator: Alternator.WithOut[A, B, C]
  ) extends HttpCodec[K, C]

  // === Atom types (with metadata as direct fields) ===

  /**
   * A query parameter called `name` whose value `schema` describes, with an
   * optional default, documentation, named examples and deprecation note. Build
   * it with [[HttpCodec.query]].
   */
  final case class Query[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[CodecKind.Request, A]

  /**
   * A header called `name` whose value `schema` describes, with an optional
   * default, documentation, named examples and deprecation note. Build it with
   * [[HttpCodec.requestHeader]] or [[HttpCodec.responseHeader]].
   */
  final case class Header[K <: CodecKind, A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[K, A]

  /**
   * A request or response body described by `schema`, with the media types it
   * may use (empty when none is named), an optional name, documentation, named
   * examples and deprecation note. Build it with [[HttpCodec.requestBody]] or
   * [[HttpCodec.responseBody]].
   */
  final case class Body[K <: CodecKind, A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[K, A]

  /**
   * A response status, with documentation, example statuses and deprecation
   * note. The predefined codecs and [[HttpCodec.status]] set `status`; `None`
   * leaves it open.
   */
  final case class StatusCodec(
    status: Option[Status] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[Status] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[CodecKind.Response, Unit]

  /**
   * Returns [[HttpCodec.Empty]] as a codec of either direction.
   */
  def empty[K <: CodecKind]: HttpCodec[K, Unit] = Empty

  /**
   * Builds a query-parameter codec. `default` is the value to use when the
   * parameter is missing, and `examples` pairs each example value with a name.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * val limit: HttpCodec.Query[Int] = HttpCodec.query("limit", Schema[Int], default = Some(20))
   * }}}
   */
  def query[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Query[A] =
    Query(name, schema, default, doc, examples, deprecated)

  /**
   * Builds a request-header codec for the header called `name`, whose value
   * `schema` describes.
   */
  def requestHeader[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Header[CodecKind.Request, A] =
    Header[CodecKind.Request, A](name, schema, default, doc, examples, deprecated)

  /**
   * Builds a request-header codec from a [[zio.http.Header.Codec]]: the header
   * keeps the codec's name, and its value is parsed and rendered by the codec.
   * A value the codec cannot parse fails schema decoding with the codec's error
   * message.
   *
   * {{{
   * import zio.http.Header
   *
   * val contentType: HttpCodec.Header[CodecKind.Request, Header.ContentType] =
   *   HttpCodec.requestHeader(Header.ContentType)
   *
   * contentType.name // content-type
   * }}}
   */
  def requestHeader[A](headerType: HttpHeader.Codec[A]): HttpCodec.Header[CodecKind.Request, A] =
    Header[CodecKind.Request, A](headerType.name, headerSchema(headerType))

  /**
   * Builds a response-header codec for the header called `name`, whose value
   * `schema` describes.
   */
  def responseHeader[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Header[CodecKind.Response, A] =
    Header[CodecKind.Response, A](name, schema, default, doc, examples, deprecated)

  /**
   * Builds a response-header codec from a [[zio.http.Header.Codec]]: the header
   * keeps the codec's name, and its value is parsed and rendered by the codec.
   */
  def responseHeader[A](headerType: HttpHeader.Codec[A]): HttpCodec.Header[CodecKind.Response, A] =
    Header[CodecKind.Response, A](headerType.name, headerSchema(headerType))

  /**
   * Builds a request-body codec described by `schema`. An empty `mediaTypes`
   * names no media type.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * val noteBody: HttpCodec[CodecKind.Request, String] = HttpCodec.requestBody(Schema[String])
   * }}}
   */
  def requestBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Request, A] =
    Body[CodecKind.Request, A](schema, mediaTypes, name, doc, examples, deprecated)

  /**
   * Builds a response-body codec described by `schema`. An empty `mediaTypes`
   * names no media type.
   */
  def responseBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Response, A] =
    Body[CodecKind.Response, A](schema, mediaTypes, name, doc, examples, deprecated)

  /**
   * Builds a codec for the response status `status`.
   *
   * {{{
   * import zio.http.Status
   *
   * val accepted: HttpCodec[CodecKind.Response, Unit] = HttpCodec.status(Status.Accepted)
   * }}}
   */
  def status(
    status: Status,
    doc: Doc = Doc.empty,
    examples: Chunk[Status] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Response, Unit] =
    StatusCodec(Some(status), doc, examples, deprecated)

  /**
   * The `authorization` request header with any scheme
   * (`Header.Authorization.Basic`, `Bearer`, `Digest` or another one).
   */
  val authorization: HttpCodec[CodecKind.Request, HttpHeader.Authorization] = requestHeader(HttpHeader.Authorization)

  /**
   * The `authorization` request header with Basic credentials. A header with
   * another scheme fails schema decoding with "Expected Basic authorization
   * header but found X", where X is the class of the parsed header: `Basic`,
   * `Bearer`, `Digest`, or `Unparsed` for any other scheme. The scheme name is
   * read without regard to case.
   */
  val basicAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Basic] =
    requestHeader(
      "authorization",
      authorizationSchema("Basic", { case basic: HttpHeader.Authorization.Basic => basic })
    )

  /**
   * The `authorization` request header with a Bearer token. A header with
   * another scheme fails schema decoding with "Expected Bearer authorization
   * header but found X", where X is the class of the parsed header: `Basic`,
   * `Bearer`, `Digest`, or `Unparsed` for any other scheme.
   */
  val bearerAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Bearer] =
    requestHeader(
      "authorization",
      authorizationSchema("Bearer", { case bearer: HttpHeader.Authorization.Bearer => bearer })
    )

  /**
   * The `authorization` request header with Digest credentials. A header with
   * another scheme fails schema decoding with "Expected Digest authorization
   * header but found X", where X is the class of the parsed header: `Basic`,
   * `Bearer`, `Digest`, or `Unparsed` for any other scheme.
   */
  val digestAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Digest] =
    requestHeader(
      "authorization",
      authorizationSchema("Digest", { case digest: HttpHeader.Authorization.Digest => digest })
    )

  /**
   * The `proxy-authorization` request header.
   */
  val proxyAuthorization: HttpCodec[CodecKind.Request, HttpHeader.ProxyAuthorization] = requestHeader(
    HttpHeader.ProxyAuthorization
  )

  /**
   * Status 100 Continue.
   */
  val Continue: HttpCodec[CodecKind.Response, Unit] = status(Status.Continue)

  /**
   * Status 101 Switching Protocols.
   */
  val SwitchingProtocols: HttpCodec[CodecKind.Response, Unit] = status(Status.SwitchingProtocols)

  /**
   * Status 102 Processing.
   */
  val Processing: HttpCodec[CodecKind.Response, Unit] = status(Status.Processing)

  /**
   * Status 200 OK, the status that `Endpoint#out` uses when none is given.
   */
  val Ok: HttpCodec[CodecKind.Response, Unit] = status(Status.Ok)

  /**
   * Status 201 Created.
   */
  val Created: HttpCodec[CodecKind.Response, Unit] = status(Status.Created)

  /**
   * Status 202 Accepted.
   */
  val Accepted: HttpCodec[CodecKind.Response, Unit] = status(Status.Accepted)

  /**
   * Status 204 No Content.
   */
  val NoContent: HttpCodec[CodecKind.Response, Unit] = status(Status.NoContent)

  /**
   * Status 400 Bad Request.
   */
  val BadRequest: HttpCodec[CodecKind.Response, Unit] = status(Status.BadRequest)

  /**
   * Status 401 Unauthorized.
   */
  val Unauthorized: HttpCodec[CodecKind.Response, Unit] = status(Status.Unauthorized)

  /**
   * Status 403 Forbidden.
   */
  val Forbidden: HttpCodec[CodecKind.Response, Unit] = status(Status.Forbidden)

  /**
   * Status 404 Not Found.
   */
  val NotFound: HttpCodec[CodecKind.Response, Unit] = status(Status.NotFound)

  /**
   * Status 500 Internal Server Error.
   */
  val InternalServerError: HttpCodec[CodecKind.Response, Unit] = status(Status.InternalServerError)

  /**
   * Builds a status codec from a numeric code with `Status.fromInt`. The code
   * is not checked, so `CustomStatus(999)` and `CustomStatus(-1)` build codecs
   * too.
   */
  def CustomStatus(code: Int): HttpCodec[CodecKind.Response, Unit] = status(Status.fromInt(code))
}
