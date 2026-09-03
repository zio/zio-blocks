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

/**
 * The `Proxy-Authorization` request header (RFC 9110, section 11.5).
 *
 * This is the companion implementation behind [[Header.ProxyAuthorization]];
 * the nested aliases on [[Header]] are preserved so existing imports keep
 * working.
 */
sealed trait ProxyAuthorization extends Header {
  def headerName: String    = ProxyAuthorization.name
  def renderedValue: String = ProxyAuthorization.render(this)
}

object ProxyAuthorization extends Header.Typed[ProxyAuthorization] {
  val name: String = "proxy-authorization"

  final case class Basic(username: String, password: String) extends ProxyAuthorization
  final case class Bearer(token: String)                     extends ProxyAuthorization
  final case class Digest(params: Map[String, String])       extends ProxyAuthorization
  final case class Unparsed(scheme: String, params: String)  extends ProxyAuthorization

  def parse(value: String): Either[String, ProxyAuthorization] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) return Left("Empty proxy-authorization header")
    val spaceIdx = trimmed.indexOf(' ')
    if (spaceIdx < 0) return Left(s"Invalid proxy-authorization header: $trimmed")
    val scheme = trimmed.substring(0, spaceIdx)
    val rest   = trimmed.substring(spaceIdx + 1).trim
    scheme.toLowerCase match {
      case "basic"  => parseBasic(rest)
      case "bearer" => Right(Bearer(rest))
      case "digest" => Right(Digest(HeaderParams.parseParams(rest)))
      case _        => Right(Unparsed(scheme, rest))
    }
  }

  def render(h: ProxyAuthorization): String = h match {
    case Basic(username, password) =>
      val encoded = java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes("UTF-8"))
      s"Basic $encoded"
    case Bearer(token)            => s"Bearer $token"
    case Digest(params)           => "Digest " + HeaderParams.renderParams(params)
    case Unparsed(scheme, params) => s"$scheme $params"
  }

  private def parseBasic(encoded: String): Either[String, ProxyAuthorization] =
    HeaderParams.parseBasic(encoded, "proxy-authorization").map { case (username, password) =>
      Basic(username, password)
    }
}
