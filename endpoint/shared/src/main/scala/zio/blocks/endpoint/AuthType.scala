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

import zio.blocks.combinators.Eithers
import zio.http.{Header, Status}

/**
 * The authentication scheme of an [[Endpoint]]: which credentials a client
 * sends ([[ClientRequirement]]), the request codec that reads them ([[codec]]),
 * and the status to answer with when authentication fails
 * ([[unauthorizedStatus]]). Like the rest of an endpoint it is a description; a
 * server or client built on it does the checking.
 *
 * The built-in schemes are [[AuthType.None]], which reads nothing,
 * [[AuthType.Basic]], [[AuthType.Bearer]] and [[AuthType.Digest]], which read
 * the `authorization` header, and [[AuthType.Custom]] for any request codec.
 * Combine schemes with `|`, and attach scopes with [[AuthType.Scoped]].
 *
 * [[unauthorizedStatus]] is `404 Not Found` unless changed, so that a failed
 * login does not reveal whether a protected resource exists. Use
 * [[withUnauthorizedStatus]] for `401 Unauthorized` instead.
 *
 * {{{
 * import zio.http.Status
 *
 * val auth = AuthType.Bearer | AuthType.Basic
 *
 * auth.unauthorizedStatus.code                                                        // 404
 * AuthType.Bearer.withUnauthorizedStatus(Status.Unauthorized).unauthorizedStatus.code // 401
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/auth-type/ AuthType]]
 */
sealed trait AuthType { self =>

  /**
   * The credentials a client sends for this scheme, and the value [[codec]]
   * decodes: `Unit` for [[AuthType.None]], `Header.Authorization.Bearer` for
   * [[AuthType.Bearer]], an `Either` for an `|` combination.
   */
  type ClientRequirement

  /**
   * The request codec that reads the credentials, such as
   * [[HttpCodec.bearerAuth]] for [[AuthType.Bearer]].
   */
  def codec: HttpCodec[CodecKind.Request, ClientRequirement]

  /**
   * The status a server should answer with when authentication fails: `404 Not
   * Found` unless changed with [[withUnauthorizedStatus]]. An `|` combination
   * takes its left scheme's status, and [[AuthType.Scoped]] takes the inner
   * scheme's.
   */
  def unauthorizedStatus: Status = Status.NotFound

  /**
   * Wraps this scheme in an [[AuthType.WithStatus]] whose
   * [[unauthorizedStatus]] is `status`, keeping the codec. Use it for `401
   * Unauthorized` when hiding protected resources is not needed. The result's
   * type is a plain `AuthType` refinement, not the scheme's own type.
   */
  def withUnauthorizedStatus(status: Status): AuthType { type ClientRequirement = self.ClientRequirement } =
    AuthType.WithStatus(
      self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }],
      status
    )

  /**
   * Accepts this scheme or `that`: the codec tries this scheme first, and the
   * credentials are an `Either` (`Left` for this scheme, `Right` for `that`).
   * The status for failed authentication is this scheme's. As with
   * `HttpCodec#|`, on Scala 3 give the result's type on a second `val`.
   *
   * {{{
   * import zio.http.Header
   *
   * val either = AuthType.Bearer | AuthType.Basic
   *
   * val typed: AuthType { type ClientRequirement = Either[Header.Authorization.Bearer, Header.Authorization.Basic] } =
   *   either
   * }}}
   */
  def |[ClientReq2, ClientReq](that: AuthType { type ClientRequirement = ClientReq2 })(implicit
    alternator: Eithers.Eithers.WithOut[ClientRequirement, ClientReq2, ClientReq]
  ): AuthType { type ClientRequirement = ClientReq } =
    AuthType.Or(
      self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }],
      that,
      alternator
    )
}

/**
 * The built-in authentication schemes and the wrappers that combine them.
 */
object AuthType {

  /**
   * The type of [[AuthType.None]], as in `Endpoint[..., AuthType.None]`.
   */
  type None = None.type

  /**
   * The type of [[AuthType.Basic]].
   */
  type Basic = Basic.type

  /**
   * The type of [[AuthType.Bearer]].
   */
  type Bearer = Bearer.type

  /**
   * No authentication: the codec reads nothing and the credentials are `Unit`.
   * It is the scheme of a new [[Endpoint]].
   */
  case object None extends AuthType {

    /** No credentials. */
    type ClientRequirement = Unit
    override val codec: HttpCodec[CodecKind.Request, Unit] = HttpCodec.empty
  }

  /**
   * HTTP Basic authentication: the `authorization` header with a user name and
   * password ([[HttpCodec.basicAuth]]).
   */
  case object Basic extends AuthType {

    /** The Basic credentials from the `authorization` header. */
    type ClientRequirement = Header.Authorization.Basic
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Basic] =
      HttpCodec.basicAuth
  }

  /**
   * Bearer token authentication: the `authorization` header with a token
   * ([[HttpCodec.bearerAuth]]).
   */
  case object Bearer extends AuthType {

    /** The Bearer token from the `authorization` header. */
    type ClientRequirement = Header.Authorization.Bearer
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Bearer] =
      HttpCodec.bearerAuth
  }

  /**
   * HTTP Digest authentication: the `authorization` header with Digest
   * parameters ([[HttpCodec.digestAuth]]).
   */
  case object Digest extends AuthType {

    /** The Digest parameters from the `authorization` header. */
    type ClientRequirement = Header.Authorization.Digest
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Digest] =
      HttpCodec.digestAuth
  }

  /**
   * A scheme read by any request codec, such as an API-key header.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * val apiKey = AuthType.Custom(HttpCodec.requestHeader("x-api-key", Schema[String]))
   * }}}
   */
  final case class Custom[ClientReq](override val codec: HttpCodec[CodecKind.Request, ClientReq]) extends AuthType {

    /** The value `codec` decodes. */
    type ClientRequirement = ClientReq
  }

  /**
   * Two schemes where either one is enough, built by `|`. The codec tries
   * `left` first, and `alternator` turns the credentials into an `Either`.
   */
  final case class Or[ClientReq1, ClientReq2, ClientReq](
    left: AuthType { type ClientRequirement = ClientReq1 },
    right: AuthType { type ClientRequirement = ClientReq2 },
    alternator: Eithers.Eithers.WithOut[ClientReq1, ClientReq2, ClientReq]
  ) extends AuthType {

    /** The credentials of either scheme, as `alternator` combines them. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] =
      HttpCodec.Fallback(left.codec, right.codec, Alternator.fromEithers(alternator))

    /**
     * The left scheme's status, whatever the right one says. Wrap the
     * combination with `withUnauthorizedStatus` to set one status for both.
     */
    override def unauthorizedStatus: Status = left.unauthorizedStatus
  }

  /**
   * A scheme with its [[AuthType.unauthorizedStatus]] replaced, built by
   * `withUnauthorizedStatus`. The codec is the inner scheme's.
   */
  final case class WithStatus[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    override val unauthorizedStatus: Status
  ) extends AuthType {

    /** The inner scheme's credentials. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] = authType.codec
  }

  /**
   * The `inner` scheme with a list of required scopes, such as `notes:write`.
   * The codec and status are the inner scheme's, unchanged; nothing in this
   * module checks the scopes, so a server built on the endpoint must.
   */
  final case class Scoped[ClientReq](
    inner: AuthType { type ClientRequirement = ClientReq },
    scopes: List[String]
  ) extends AuthType {

    /** The inner scheme's credentials. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] = inner.codec
    override def unauthorizedStatus: Status                     = inner.unauthorizedStatus
  }
}
