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

sealed trait Scheme {
  def text: String
  def defaultPort: Option[Int]
  def isSecure: Boolean
  def isWebSocket: Boolean
  override def toString: String = text
}

object Scheme {
  case object HTTP extends Scheme {
    val text: String             = "http"
    val defaultPort: Option[Int] = Some(80)
    val isSecure: Boolean        = false
    val isWebSocket: Boolean     = false
  }

  case object HTTPS extends Scheme {
    val text: String             = "https"
    val defaultPort: Option[Int] = Some(443)
    val isSecure: Boolean        = true
    val isWebSocket: Boolean     = false
  }

  case object WS extends Scheme {
    val text: String             = "ws"
    val defaultPort: Option[Int] = Some(80)
    val isSecure: Boolean        = false
    val isWebSocket: Boolean     = true
  }

  case object WSS extends Scheme {
    val text: String             = "wss"
    val defaultPort: Option[Int] = Some(443)
    val isSecure: Boolean        = true
    val isWebSocket: Boolean     = true
  }

  final case class Custom(text: String) extends Scheme {
    val defaultPort: Option[Int] = None
    val isSecure: Boolean        = false
    val isWebSocket: Boolean     = false
  }

  def fromString(s: String): Scheme = s.toLowerCase match {
    case "http"  => HTTP
    case "https" => HTTPS
    case "ws"    => WS
    case "wss"   => WSS
    case other   => Custom(other)
  }

  def render(scheme: Scheme): String = scheme.text
}
