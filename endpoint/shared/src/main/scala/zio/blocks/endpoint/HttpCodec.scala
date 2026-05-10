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

/** Phantom type for codec direction. */
sealed trait CodecKind

object CodecKind {

  /**
   * Codec that describes request parts (query, request header, request body).
   */
  sealed trait Request extends CodecKind

  /**
   * Codec that describes response parts (status, response header, response
   * body).
   */
  sealed trait Response extends CodecKind
}

/**
 * Composable typed descriptor for HTTP request/response parts.
 *
 * Most nodes are plain data carrying schemas plus endpoint metadata directly on
 * the atom (`doc`, `examples`, `deprecated`) instead of wrapping in extra
 * annotation layers. Typed header and auth helpers reuse `Schema.transform` so
 * the descriptor can keep exposing `Schema[A]` for parsed header values without
 * introducing dedicated interpreter-only AST nodes.
 */
sealed trait HttpCodec[+K <: CodecKind, A] { self =>
  def ++[B, C](that: HttpCodec[K @uncheckedVariance, B])(using
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): HttpCodec[K, C] =
    HttpCodec.Combine(self, that, combiner)

  def |[B, C](that: HttpCodec[K @uncheckedVariance, B])(using
    alternator: Eithers.Eithers.WithOut[A, B, C]
  ): HttpCodec[K, C] =
    HttpCodec.Fallback(self, that, Alternator.fromEithers(alternator))
}

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

  /** No additional data. */
  case object Empty extends HttpCodec[Nothing, Unit]

  /** Sequential combination of two codecs. */
  final case class Combine[K <: CodecKind, A, B, C](
    left: HttpCodec[K, A],
    right: HttpCodec[K, B],
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ) extends HttpCodec[K, C]

  /** Alternative between two codecs. */
  final case class Fallback[K <: CodecKind, A, B, C](
    left: HttpCodec[K, A],
    right: HttpCodec[K, B],
    alternator: Alternator.WithOut[A, B, C]
  ) extends HttpCodec[K, C]

  // === Atom types (with metadata as direct fields) ===

  /** Query parameter descriptor. */
  final case class Query[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[CodecKind.Request, A]

  /** HTTP header descriptor. */
  final case class Header[K <: CodecKind, A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[K, A]

  /** Request/response body descriptor. */
  final case class Body[K <: CodecKind, A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[K, A]

  /** HTTP status code descriptor. */
  final case class StatusCodec(
    status: Option[Status] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[Status] = Chunk.empty,
    deprecated: Option[Doc] = None
  ) extends HttpCodec[CodecKind.Response, Unit]

  def empty[K <: CodecKind]: HttpCodec[K, Unit] = Empty

  def query[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Query[A] =
    Query(name, schema, default, doc, examples, deprecated)

  def requestHeader[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Header[CodecKind.Request, A] =
    Header[CodecKind.Request, A](name, schema, default, doc, examples, deprecated)

  def requestHeader[A](headerType: HttpHeader.Codec[A]): HttpCodec.Header[CodecKind.Request, A] =
    Header[CodecKind.Request, A](headerType.name, headerSchema(headerType))

  def responseHeader[A](
    name: String,
    schema: Schema[A],
    default: Option[A] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec.Header[CodecKind.Response, A] =
    Header[CodecKind.Response, A](name, schema, default, doc, examples, deprecated)

  def responseHeader[A](headerType: HttpHeader.Codec[A]): HttpCodec.Header[CodecKind.Response, A] =
    Header[CodecKind.Response, A](headerType.name, headerSchema(headerType))

  def requestBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Request, A] =
    Body[CodecKind.Request, A](schema, mediaTypes, name, doc, examples, deprecated)

  def responseBody[A](
    schema: Schema[A],
    mediaTypes: Chunk[MediaType] = Chunk.empty,
    name: Option[String] = None,
    doc: Doc = Doc.empty,
    examples: Chunk[(String, A)] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Response, A] =
    Body[CodecKind.Response, A](schema, mediaTypes, name, doc, examples, deprecated)

  def status(
    status: Status,
    doc: Doc = Doc.empty,
    examples: Chunk[Status] = Chunk.empty,
    deprecated: Option[Doc] = None
  ): HttpCodec[CodecKind.Response, Unit] =
    StatusCodec(Some(status), doc, examples, deprecated)

  val authorization: HttpCodec[CodecKind.Request, HttpHeader.Authorization]   = requestHeader(HttpHeader.Authorization)
  val basicAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Basic] =
    requestHeader("authorization", authorizationSchema("Basic", { case basic: HttpHeader.Authorization.Basic => basic }))
  val bearerAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Bearer] =
    requestHeader(
      "authorization",
      authorizationSchema("Bearer", { case bearer: HttpHeader.Authorization.Bearer => bearer })
    )
  val digestAuth: HttpCodec[CodecKind.Request, HttpHeader.Authorization.Digest] =
    requestHeader(
      "authorization",
      authorizationSchema("Digest", { case digest: HttpHeader.Authorization.Digest => digest })
    )
  val proxyAuthorization: HttpCodec[CodecKind.Request, HttpHeader.ProxyAuthorization] = requestHeader(
    HttpHeader.ProxyAuthorization
  )

  val Continue: HttpCodec[CodecKind.Response, Unit]                = status(Status.Continue)
  val SwitchingProtocols: HttpCodec[CodecKind.Response, Unit]      = status(Status.SwitchingProtocols)
  val Processing: HttpCodec[CodecKind.Response, Unit]              = status(Status.Processing)
  val Ok: HttpCodec[CodecKind.Response, Unit]                      = status(Status.Ok)
  val Created: HttpCodec[CodecKind.Response, Unit]                 = status(Status.Created)
  val Accepted: HttpCodec[CodecKind.Response, Unit]                = status(Status.Accepted)
  val NoContent: HttpCodec[CodecKind.Response, Unit]               = status(Status.NoContent)
  val BadRequest: HttpCodec[CodecKind.Response, Unit]              = status(Status.BadRequest)
  val Unauthorized: HttpCodec[CodecKind.Response, Unit]            = status(Status.Unauthorized)
  val Forbidden: HttpCodec[CodecKind.Response, Unit]               = status(Status.Forbidden)
  val NotFound: HttpCodec[CodecKind.Response, Unit]                = status(Status.NotFound)
  val InternalServerError: HttpCodec[CodecKind.Response, Unit]     = status(Status.InternalServerError)
  def CustomStatus(code: Int): HttpCodec[CodecKind.Response, Unit] = status(Status.fromInt(code))
}
