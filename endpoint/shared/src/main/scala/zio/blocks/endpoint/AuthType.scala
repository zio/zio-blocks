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
 * Authentication scheme descriptor aligned with zio-http's typed auth model.
 *
 * When authentication fails, the default response status is `Status.NotFound`
 * rather than `Status.Unauthorized`. This is an intentional security pattern
 * (information hiding) that avoids revealing whether a protected resource
 * exists. Override [[unauthorizedStatus]] with [[withUnauthorizedStatus]] if
 * you want standard `401 Unauthorized` behavior instead.
 */
sealed trait AuthType { self =>
  type ClientRequirement

  def codec: HttpCodec[CodecKind.Request, ClientRequirement]

  /**
   * The HTTP status code returned when authentication fails.
   *
   * Defaults to `Status.NotFound` to avoid revealing protected resources via a
   * distinct authentication failure response.
   */
  def unauthorizedStatus: Status = Status.NotFound

  /**
   * Returns a copy of this auth type with a different unauthorized status.
   *
   * Use this to opt into standard `401 Unauthorized` behavior when information
   * hiding is not desired.
   */
  def withUnauthorizedStatus(status: Status): AuthType { type ClientRequirement = self.ClientRequirement } =
    AuthType.WithStatus(
      self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }],
      status
    )

  def |[ClientReq2, ClientReq](that: AuthType { type ClientRequirement = ClientReq2 })(implicit
    alternator: Eithers.Eithers.WithOut[ClientRequirement, ClientReq2, ClientReq]
  ): AuthType { type ClientRequirement = ClientReq } =
    AuthType.Or(
      self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }],
      that,
      alternator
    )
}

object AuthType {
  type None   = None.type
  type Basic  = Basic.type
  type Bearer = Bearer.type

  case object None extends AuthType {
    type ClientRequirement = Unit
    override val codec: HttpCodec[CodecKind.Request, Unit] = HttpCodec.empty
  }

  case object Basic extends AuthType {
    type ClientRequirement = Header.Authorization.Basic
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Basic] =
      HttpCodec.basicAuth
  }

  case object Bearer extends AuthType {
    type ClientRequirement = Header.Authorization.Bearer
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Bearer] =
      HttpCodec.bearerAuth
  }

  case object Digest extends AuthType {
    type ClientRequirement = Header.Authorization.Digest
    override val codec: HttpCodec[CodecKind.Request, Header.Authorization.Digest] =
      HttpCodec.digestAuth
  }

  final case class Custom[ClientReq](override val codec: HttpCodec[CodecKind.Request, ClientReq]) extends AuthType {
    type ClientRequirement = ClientReq
  }

  final case class Or[ClientReq1, ClientReq2, ClientReq](
    left: AuthType { type ClientRequirement = ClientReq1 },
    right: AuthType { type ClientRequirement = ClientReq2 },
    alternator: Eithers.Eithers.WithOut[ClientReq1, ClientReq2, ClientReq]
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] =
      HttpCodec.Fallback(left.codec, right.codec, Alternator.fromEithers(alternator))
    override def unauthorizedStatus: Status = left.unauthorizedStatus
  }

  final case class WithStatus[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    override val unauthorizedStatus: Status
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] = authType.codec
  }

  final case class Scoped[ClientReq](
    inner: AuthType { type ClientRequirement = ClientReq },
    scopes: List[String]
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[CodecKind.Request, ClientReq] = inner.codec
    override def unauthorizedStatus: Status                     = inner.unauthorizedStatus
  }
}
