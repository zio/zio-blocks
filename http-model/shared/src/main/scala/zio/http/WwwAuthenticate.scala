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
 * The `WWW-Authenticate` and `Proxy-Authenticate` challenge headers (RFC 9110,
 * sections 11.2-11.3).
 *
 * These are the companion implementations behind [[Header.WWWAuthenticate]] and
 * [[Header.ProxyAuthenticate]]; the nested aliases on [[Header]] are preserved
 * so existing imports keep working.
 */
final case class WWWAuthenticate(scheme: String, params: Map[String, String]) extends Header {
  def headerName: String    = WWWAuthenticate.name
  def renderedValue: String = WWWAuthenticate.render(this)
}

object WWWAuthenticate extends Header.Typed[WWWAuthenticate] {
  val name: String = "www-authenticate"

  def parse(value: String): Either[String, WWWAuthenticate] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) return Left("Empty www-authenticate header")
    val spaceIdx = trimmed.indexOf(' ')
    if (spaceIdx < 0) Right(WWWAuthenticate(trimmed, Map.empty))
    else {
      val scheme = trimmed.substring(0, spaceIdx)
      val rest   = trimmed.substring(spaceIdx + 1).trim
      Right(WWWAuthenticate(scheme, HeaderParams.parseParams(rest)))
    }
  }

  def render(h: WWWAuthenticate): String =
    if (h.params.isEmpty) h.scheme
    else h.scheme + " " + HeaderParams.renderParams(h.params)
}

final case class ProxyAuthenticate(scheme: String, params: Map[String, String]) extends Header {
  def headerName: String    = ProxyAuthenticate.name
  def renderedValue: String = ProxyAuthenticate.render(this)
}

object ProxyAuthenticate extends Header.Typed[ProxyAuthenticate] {
  val name: String = "proxy-authenticate"

  def parse(value: String): Either[String, ProxyAuthenticate] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) return Left("Empty proxy-authenticate header")
    val spaceIdx = trimmed.indexOf(' ')
    if (spaceIdx < 0) Right(ProxyAuthenticate(trimmed, Map.empty))
    else {
      val scheme = trimmed.substring(0, spaceIdx)
      val rest   = trimmed.substring(spaceIdx + 1).trim
      Right(ProxyAuthenticate(scheme, HeaderParams.parseParams(rest)))
    }
  }

  def render(h: ProxyAuthenticate): String =
    if (h.params.isEmpty) h.scheme
    else h.scheme + " " + HeaderParams.renderParams(h.params)
}
