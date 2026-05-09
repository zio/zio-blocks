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

trait Header {
  def headerName: String
  def renderedValue: String
}

object Header {

  trait Typed[H <: Header] {
    def name: String
    def parse(value: String): Either[String, H]
    def render(h: H): String
  }

  val Authorization                 = zio.http.headers.Authorization
  val ProxyAuthorization            = zio.http.headers.ProxyAuthorization
  val WWWAuthenticate               = zio.http.headers.WWWAuthenticate
  val ProxyAuthenticate             = zio.http.headers.ProxyAuthenticate
  val CacheControl                  = zio.http.headers.CacheControl
  val ETag                          = zio.http.headers.ETag
  val IfMatch                       = zio.http.headers.IfMatch
  val IfNoneMatch                   = zio.http.headers.IfNoneMatch
  val IfModifiedSince               = zio.http.headers.IfModifiedSince
  val IfUnmodifiedSince             = zio.http.headers.IfUnmodifiedSince
  val IfRange                       = zio.http.headers.IfRange
  val Expires                       = zio.http.headers.Expires
  val Age                           = zio.http.headers.Age
  val LastModified                  = zio.http.headers.LastModified
  val Pragma                        = zio.http.headers.Pragma
  val Vary                          = zio.http.headers.Vary
  val Connection                    = zio.http.headers.Connection
  val Upgrade                       = zio.http.headers.Upgrade
  val Te                            = zio.http.headers.Te
  val Trailer                       = zio.http.headers.Trailer
  val TransferEncoding              = zio.http.headers.TransferEncoding
  val ContentType                   = zio.http.headers.ContentType
  val ContentLength                 = zio.http.headers.ContentLength
  val ContentEncoding               = zio.http.headers.ContentEncoding
  val ContentDisposition            = zio.http.headers.ContentDisposition
  val ContentLanguage               = zio.http.headers.ContentLanguage
  val ContentLocation               = zio.http.headers.ContentLocation
  val ContentRange                  = zio.http.headers.ContentRange
  val ContentSecurityPolicy         = zio.http.headers.ContentSecurityPolicy
  val ContentTransferEncoding       = zio.http.headers.ContentTransferEncoding
  val ContentMd5                    = zio.http.headers.ContentMd5
  val ContentBase                   = zio.http.headers.ContentBase
  val Cookie                        = zio.http.headers.CookieHeader
  val SetCookie                     = zio.http.headers.SetCookieHeader
  val AccessControlAllowOrigin      = zio.http.headers.AccessControlAllowOrigin
  val AccessControlAllowMethods     = zio.http.headers.AccessControlAllowMethods
  val AccessControlAllowHeaders     = zio.http.headers.AccessControlAllowHeaders
  val AccessControlAllowCredentials = zio.http.headers.AccessControlAllowCredentials
  val AccessControlExposeHeaders    = zio.http.headers.AccessControlExposeHeaders
  val AccessControlMaxAge           = zio.http.headers.AccessControlMaxAge
  val AccessControlRequestHeaders   = zio.http.headers.AccessControlRequestHeaders
  val AccessControlRequestMethod    = zio.http.headers.AccessControlRequestMethod
  val UserAgent                     = zio.http.headers.UserAgent
  val Server                        = zio.http.headers.Server
  val Date                          = zio.http.headers.Date
  val Link                          = zio.http.headers.Link
  val RetryAfter                    = zio.http.headers.RetryAfter
  val Allow                         = zio.http.headers.Allow
  val Expect                        = zio.http.headers.Expect
  val Range                         = zio.http.headers.Range
  val Accept                        = zio.http.headers.Accept
  val AcceptEncoding                = zio.http.headers.AcceptEncoding
  val AcceptLanguage                = zio.http.headers.AcceptLanguage
  val AcceptRanges                  = zio.http.headers.AcceptRanges
  val AcceptPatch                   = zio.http.headers.AcceptPatch
  val Host                          = zio.http.headers.Host
  val Location                      = zio.http.headers.Location
  val Origin                        = zio.http.headers.Origin
  val Referer                       = zio.http.headers.Referer
  val Via                           = zio.http.headers.Via
  val Forwarded                     = zio.http.headers.Forwarded
  val MaxForwards                   = zio.http.headers.MaxForwards
  val From                          = zio.http.headers.From
  val XFrameOptions                 = zio.http.headers.XFrameOptions
  val XRequestedWith                = zio.http.headers.XRequestedWith
  val DNT                           = zio.http.headers.DNT
  val UpgradeInsecureRequests       = zio.http.headers.UpgradeInsecureRequests
  val ClearSiteData                 = zio.http.headers.ClearSiteData
  val SecWebSocketAccept            = zio.http.headers.SecWebSocketAccept
  val SecWebSocketExtensions        = zio.http.headers.SecWebSocketExtensions
  val SecWebSocketKey               = zio.http.headers.SecWebSocketKey
  val SecWebSocketLocation          = zio.http.headers.SecWebSocketLocation
  val SecWebSocketOrigin            = zio.http.headers.SecWebSocketOrigin
  val SecWebSocketProtocol          = zio.http.headers.SecWebSocketProtocol
  val SecWebSocketVersion           = zio.http.headers.SecWebSocketVersion

  final case class Custom(override val headerName: String, rawValue: String) extends Header {
    def renderedValue: String = rawValue
  }

}
