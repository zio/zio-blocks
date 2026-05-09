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

import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaType

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

  sealed trait Authorization extends zio.http.Header {
    def headerName: String    = Authorization.name
    def renderedValue: String = Authorization.render(this)
  }

  object Authorization extends Typed[Authorization] {
    val name: String = "authorization"

    final case class Basic(username: String, password: String) extends Authorization
    final case class Bearer(token: String)                     extends Authorization
    final case class Digest(params: Map[String, String])       extends Authorization
    final case class Unparsed(scheme: String, params: String)  extends Authorization

    def parse(value: String): Either[String, Authorization] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty authorization header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) return Left(s"Invalid authorization header: $trimmed")
      val scheme = trimmed.substring(0, spaceIdx)
      val rest   = trimmed.substring(spaceIdx + 1).trim
      scheme.toLowerCase match {
        case "basic"  => parseBasic(rest)
        case "bearer" => Right(Bearer(rest))
        case "digest" => Right(Digest(parseParams(rest)))
        case _        => Right(Unparsed(scheme, rest))
      }
    }

    def render(h: Authorization): String = h match {
      case Basic(username, password) =>
        val encoded = java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes("UTF-8"))
        s"Basic $encoded"
      case Bearer(token)            => s"Bearer $token"
      case Digest(params)           => "Digest " + renderParams(params)
      case Unparsed(scheme, params) => s"$scheme $params"
    }

    private def parseBasic(encoded: String): Either[String, Authorization] =
      try {
        val decoded  = new String(java.util.Base64.getDecoder.decode(encoded), "UTF-8")
        val colonIdx = decoded.indexOf(':')
        if (colonIdx < 0) Left("Basic authorization missing colon separator")
        else Right(Basic(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1)))
      } catch {
        case _: IllegalArgumentException => Left("Invalid base64 in basic authorization")
      }

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }

    private def renderParams(params: Map[String, String]): String =
      params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")
  }

  sealed trait ProxyAuthorization extends zio.http.Header {
    def headerName: String    = ProxyAuthorization.name
    def renderedValue: String = ProxyAuthorization.render(this)
  }

  object ProxyAuthorization extends Typed[ProxyAuthorization] {
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
        case "digest" => Right(Digest(parseParams(rest)))
        case _        => Right(Unparsed(scheme, rest))
      }
    }

    def render(h: ProxyAuthorization): String = h match {
      case Basic(username, password) =>
        val encoded = java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes("UTF-8"))
        s"Basic $encoded"
      case Bearer(token)            => s"Bearer $token"
      case Digest(params)           => "Digest " + renderParams(params)
      case Unparsed(scheme, params) => s"$scheme $params"
    }

    private def parseBasic(encoded: String): Either[String, ProxyAuthorization] =
      try {
        val decoded  = new String(java.util.Base64.getDecoder.decode(encoded), "UTF-8")
        val colonIdx = decoded.indexOf(':')
        if (colonIdx < 0) Left("Basic proxy-authorization missing colon separator")
        else Right(Basic(decoded.substring(0, colonIdx), decoded.substring(colonIdx + 1)))
      } catch {
        case _: IllegalArgumentException => Left("Invalid base64 in basic proxy-authorization")
      }

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }

    private def renderParams(params: Map[String, String]): String =
      params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")
  }

  final case class WWWAuthenticate(scheme: String, params: Map[String, String]) extends zio.http.Header {
    def headerName: String    = WWWAuthenticate.name
    def renderedValue: String = WWWAuthenticate.render(this)
  }

  object WWWAuthenticate extends Typed[WWWAuthenticate] {
    val name: String = "www-authenticate"

    def parse(value: String): Either[String, WWWAuthenticate] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty www-authenticate header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) Right(WWWAuthenticate(trimmed, Map.empty))
      else {
        val scheme = trimmed.substring(0, spaceIdx)
        val rest   = trimmed.substring(spaceIdx + 1).trim
        Right(WWWAuthenticate(scheme, parseParams(rest)))
      }
    }

    def render(h: WWWAuthenticate): String =
      if (h.params.isEmpty) h.scheme
      else h.scheme + " " + h.params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }
  }

  final case class ProxyAuthenticate(scheme: String, params: Map[String, String]) extends zio.http.Header {
    def headerName: String    = ProxyAuthenticate.name
    def renderedValue: String = ProxyAuthenticate.render(this)
  }

  object ProxyAuthenticate extends Typed[ProxyAuthenticate] {
    val name: String = "proxy-authenticate"

    def parse(value: String): Either[String, ProxyAuthenticate] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty proxy-authenticate header")
      val spaceIdx = trimmed.indexOf(' ')
      if (spaceIdx < 0) Right(ProxyAuthenticate(trimmed, Map.empty))
      else {
        val scheme = trimmed.substring(0, spaceIdx)
        val rest   = trimmed.substring(spaceIdx + 1).trim
        Right(ProxyAuthenticate(scheme, parseParams(rest)))
      }
    }

    def render(h: ProxyAuthenticate): String =
      if (h.params.isEmpty) h.scheme
      else h.scheme + " " + h.params.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")

    private def parseParams(value: String): Map[String, String] = {
      val result = Map.newBuilder[String, String]
      val parts  = value.split(",")
      var i      = 0
      while (i < parts.length) {
        val part  = parts(i).trim
        val eqIdx = part.indexOf('=')
        if (eqIdx > 0) {
          val key = part.substring(0, eqIdx).trim
          val raw = part.substring(eqIdx + 1).trim
          val v   = if (raw.startsWith("\"") && raw.endsWith("\"")) raw.substring(1, raw.length - 1) else raw
          result += (key -> v)
        }
        i += 1
      }
      result.result()
    }
  }

  final case class ContentType(value: zio.http.ContentType) extends zio.http.Header {
    def headerName: String    = ContentType.name
    def renderedValue: String = value.render
  }

  object ContentType extends Typed[ContentType] {
    val name: String = "content-type"

    def parse(value: String): Either[String, ContentType] =
      zio.http.ContentType.parse(value).map(ct => ContentType(ct))

    def render(h: ContentType): String = h.renderedValue
  }

  final case class ContentLength(length: Long) extends zio.http.Header {
    def headerName: String    = ContentLength.name
    def renderedValue: String = length.toString
  }

  object ContentLength extends Typed[ContentLength] {
    val name: String = "content-length"

    def parse(value: String): Either[String, ContentLength] =
      try {
        val l = value.toLong
        if (l < 0) Left(s"Invalid content-length: $l")
        else Right(ContentLength(l))
      } catch {
        case _: NumberFormatException => Left(s"Invalid content-length: $value")
      }

    def render(h: ContentLength): String = h.renderedValue
  }

  sealed trait ContentEncoding extends zio.http.Header {
    def headerName: String    = ContentEncoding.name
    def renderedValue: String = ContentEncoding.render(this)
  }

  object ContentEncoding extends Typed[ContentEncoding] {
    val name: String = "content-encoding"

    case object GZip     extends ContentEncoding
    case object Deflate  extends ContentEncoding
    case object Br       extends ContentEncoding
    case object Compress extends ContentEncoding
    case object Identity extends ContentEncoding

    final case class Multiple(values: Chunk[ContentEncoding]) extends ContentEncoding

    private def findEncoding(value: String): Option[ContentEncoding] =
      value.trim.toLowerCase match {
        case "gzip"     => Some(GZip)
        case "deflate"  => Some(Deflate)
        case "br"       => Some(Br)
        case "compress" => Some(Compress)
        case "identity" => Some(Identity)
        case _          => None
      }

    def parse(value: String): Either[String, ContentEncoding] = {
      val parts  = value.split(',')
      val parsed = Chunk.fromArray(parts.flatMap(s => findEncoding(s)))
      if (parsed.isEmpty) Left(s"Invalid content-encoding: $value")
      else if (parsed.length == 1) Right(parsed(0))
      else Right(Multiple(parsed))
    }

    def render(h: ContentEncoding): String = h match {
      case GZip        => "gzip"
      case Deflate     => "deflate"
      case Br          => "br"
      case Compress    => "compress"
      case Identity    => "identity"
      case Multiple(v) => v.map(render).mkString(", ")
    }
  }

  sealed trait ContentDisposition extends zio.http.Header {
    def headerName: String    = ContentDisposition.name
    def renderedValue: String = ContentDisposition.render(this)
  }

  object ContentDisposition extends Typed[ContentDisposition] {
    val name: String = "content-disposition"

    final case class Attachment(filename: Option[String])             extends ContentDisposition
    final case class Inline(filename: Option[String])                 extends ContentDisposition
    final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

    private val AttachmentRegex         = """attachment;\s*filename="(.*)"""".r
    private val InlineRegex             = """inline;\s*filename="(.*)"""".r
    private val FormDataRegex           = """form-data;\s*name="(.*)";\s*filename="(.*)"""".r
    private val FormDataNoFileNameRegex = """form-data;\s*name="(.*)"""".r

    def parse(value: String): Either[String, ContentDisposition] = {
      val trimmed = value.trim
      if (trimmed.startsWith("attachment")) {
        Right(trimmed match {
          case AttachmentRegex(filename) => Attachment(Some(filename))
          case _                         => Attachment(None)
        })
      } else if (trimmed.startsWith("inline")) {
        Right(trimmed match {
          case InlineRegex(filename) => Inline(Some(filename))
          case _                     => Inline(None)
        })
      } else if (trimmed.startsWith("form-data")) {
        trimmed match {
          case FormDataRegex(name, filename) => Right(FormData(name, Some(filename)))
          case FormDataNoFileNameRegex(name) => Right(FormData(name, None))
          case _                             => Left("Invalid form-data content disposition")
        }
      } else {
        Left(s"Invalid content-disposition: $trimmed")
      }
    }

    def render(h: ContentDisposition): String = h match {
      case Attachment(Some(f))  => s"""attachment; filename=\"$f\""""
      case Attachment(None)     => "attachment"
      case Inline(Some(f))      => s"""inline; filename=\"$f\""""
      case Inline(None)         => "inline"
      case FormData(n, Some(f)) => s"""form-data; name=\"$n\"; filename=\"$f\""""
      case FormData(n, None)    => s"""form-data; name=\"$n\""""
    }
  }

  final case class ContentLanguage(language: String) extends zio.http.Header {
    def headerName: String    = ContentLanguage.name
    def renderedValue: String = language
  }

  object ContentLanguage extends Typed[ContentLanguage] {
    val name: String                                          = "content-language"
    def parse(value: String): Either[String, ContentLanguage] = Right(ContentLanguage(value))
    def render(h: ContentLanguage): String                    = h.renderedValue
  }

  final case class ContentLocation(location: String) extends zio.http.Header {
    def headerName: String    = ContentLocation.name
    def renderedValue: String = location
  }

  object ContentLocation extends Typed[ContentLocation] {
    val name: String                                          = "content-location"
    def parse(value: String): Either[String, ContentLocation] = Right(ContentLocation(value))
    def render(h: ContentLocation): String                    = h.renderedValue
  }

  final case class ContentRange(unit: String, range: Option[(Long, Long)], size: Option[Long]) extends zio.http.Header {
    def headerName: String    = ContentRange.name
    def renderedValue: String = ContentRange.render(this)
  }

  object ContentRange extends Typed[ContentRange] {
    val name: String = "content-range"

    private val StartEndTotalRegex = """(\w+)\s+(\d+)-(\d+)/(\d+)""".r
    private val StartEndRegex      = """(\w+)\s+(\d+)-(\d+)/\*""".r
    private val TotalOnlyRegex     = """(\w+)\s+\*/(\d+)""".r

    def parse(value: String): Either[String, ContentRange] =
      value.trim match {
        case StartEndTotalRegex(unit, start, end, total) =>
          Right(ContentRange(unit, Some((start.toLong, end.toLong)), Some(total.toLong)))
        case StartEndRegex(unit, start, end) =>
          Right(ContentRange(unit, Some((start.toLong, end.toLong)), None))
        case TotalOnlyRegex(unit, total) =>
          Right(ContentRange(unit, None, Some(total.toLong)))
        case _ =>
          Left(s"Invalid content-range: $value")
      }

    def render(h: ContentRange): String = (h.range, h.size) match {
      case (Some((start, end)), Some(total)) => s"${h.unit} $start-$end/$total"
      case (Some((start, end)), None)        => s"${h.unit} $start-$end/*"
      case (None, Some(total))               => s"${h.unit} */$total"
      case (None, None)                      => s"${h.unit} */*"
    }
  }

  final case class ContentSecurityPolicy(directives: String) extends zio.http.Header {
    def headerName: String    = ContentSecurityPolicy.name
    def renderedValue: String = directives
  }

  object ContentSecurityPolicy extends Typed[ContentSecurityPolicy] {
    val name: String                                                = "content-security-policy"
    def parse(value: String): Either[String, ContentSecurityPolicy] = Right(ContentSecurityPolicy(value))
    def render(h: ContentSecurityPolicy): String                    = h.renderedValue
  }

  sealed trait ContentTransferEncoding extends zio.http.Header {
    def headerName: String    = ContentTransferEncoding.name
    def renderedValue: String = ContentTransferEncoding.render(this)
  }

  object ContentTransferEncoding extends Typed[ContentTransferEncoding] {
    val name: String = "content-transfer-encoding"

    case object SevenBit        extends ContentTransferEncoding
    case object EightBit        extends ContentTransferEncoding
    case object Binary          extends ContentTransferEncoding
    case object QuotedPrintable extends ContentTransferEncoding
    case object Base64          extends ContentTransferEncoding

    def parse(value: String): Either[String, ContentTransferEncoding] =
      value.trim.toLowerCase match {
        case "7bit"             => Right(SevenBit)
        case "8bit"             => Right(EightBit)
        case "binary"           => Right(Binary)
        case "quoted-printable" => Right(QuotedPrintable)
        case "base64"           => Right(Base64)
        case other              => Left(s"Invalid content-transfer-encoding: $other")
      }

    def render(h: ContentTransferEncoding): String = h match {
      case SevenBit        => "7bit"
      case EightBit        => "8bit"
      case Binary          => "binary"
      case QuotedPrintable => "quoted-printable"
      case Base64          => "base64"
    }
  }

  final case class ContentMd5(value: String) extends zio.http.Header {
    def headerName: String    = ContentMd5.name
    def renderedValue: String = value
  }

  object ContentMd5 extends Typed[ContentMd5] {
    val name: String                                     = "content-md5"
    def parse(value: String): Either[String, ContentMd5] = Right(ContentMd5(value))
    def render(h: ContentMd5): String                    = h.renderedValue
  }

  final case class ContentBase(uri: String) extends zio.http.Header {
    def headerName: String    = ContentBase.name
    def renderedValue: String = uri
  }

  object ContentBase extends Typed[ContentBase] {
    val name: String                                      = "content-base"
    def parse(value: String): Either[String, ContentBase] = Right(ContentBase(value))
    def render(h: ContentBase): String                    = h.renderedValue
  }

  final case class Host(host: String, port: Option[Int]) extends zio.http.Header {
    def headerName: String = Host.name

    def renderedValue: String = port match {
      case Some(p) => host + ":" + p
      case None    => host
    }
  }

  object Host extends Typed[Host] {
    val name: String = "host"

    def parse(value: String): Either[String, Host] = {
      if (value.isEmpty) return Left("Invalid host: cannot be empty")
      val colonIdx = value.lastIndexOf(':')
      if (colonIdx < 0) Right(Host(value, None))
      else {
        val hostPart = value.substring(0, colonIdx)
        if (hostPart.isEmpty) return Left("Invalid host: cannot be empty")
        val portStr = value.substring(colonIdx + 1)
        try {
          val p = portStr.toInt
          if (p < 0 || p > 65535) Left(s"Invalid port: $p")
          else Right(Host(hostPart, Some(p)))
        } catch {
          case _: NumberFormatException => Left(s"Invalid port: $portStr")
        }
      }
    }

    def render(h: Host): String = h.renderedValue
  }

  final case class Location(uri: String) extends zio.http.Header {
    def headerName: String    = Location.name
    def renderedValue: String = uri
  }

  object Location extends Typed[Location] {
    val name: String                                   = "location"
    def parse(value: String): Either[String, Location] = Right(Location(value.trim))
    def render(h: Location): String                    = h.renderedValue
  }

  sealed trait Origin extends zio.http.Header {
    def headerName: String    = Origin.name
    def renderedValue: String = Origin.render(this)
  }

  object Origin extends Typed[Origin] {
    val name: String = "origin"

    case object Null_ extends Origin

    final case class Value(scheme: String, host: String, port: Option[Int]) extends Origin

    def parse(value: String): Either[String, Origin] = {
      val trimmed = value.trim
      if (trimmed.equalsIgnoreCase("null")) Right(Null_)
      else {
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd < 0) Left(s"Invalid origin: $trimmed")
        else {
          val scheme   = trimmed.substring(0, schemeEnd)
          val rest     = trimmed.substring(schemeEnd + 3)
          val colonIdx = rest.lastIndexOf(':')
          if (colonIdx < 0) Right(Value(scheme, rest, None))
          else {
            val hostPart = rest.substring(0, colonIdx)
            val portStr  = rest.substring(colonIdx + 1)
            try {
              val p = portStr.toInt
              if (p < 0 || p > 65535) Left(s"Invalid origin port: $p")
              else Right(Value(scheme, hostPart, Some(p)))
            } catch {
              case _: NumberFormatException => Left(s"Invalid origin port: $portStr")
            }
          }
        }
      }
    }

    def render(h: Origin): String = h match {
      case Null_                           => "null"
      case Value(scheme, host, Some(port)) => s"$scheme://$host:$port"
      case Value(scheme, host, None)       => s"$scheme://$host"
    }
  }

  final case class Referer(uri: String) extends zio.http.Header {
    def headerName: String    = Referer.name
    def renderedValue: String = uri
  }

  object Referer extends Typed[Referer] {
    val name: String                                  = "referer"
    def parse(value: String): Either[String, Referer] = Right(Referer(value.trim))
    def render(h: Referer): String                    = h.renderedValue
  }

  final case class Via(entries: Chunk[String]) extends zio.http.Header {
    def headerName: String    = Via.name
    def renderedValue: String = entries.mkString(", ")
  }

  object Via extends Typed[Via] {
    val name: String = "via"

    def parse(value: String): Either[String, Via] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty via header")
      else Right(Via(Chunk.fromArray(parts)))
    }

    def render(h: Via): String = h.renderedValue
  }

  final case class Forwarded(params: String) extends zio.http.Header {
    def headerName: String    = Forwarded.name
    def renderedValue: String = params
  }

  object Forwarded extends Typed[Forwarded] {
    val name: String                                    = "forwarded"
    def parse(value: String): Either[String, Forwarded] = Right(Forwarded(value.trim))
    def render(h: Forwarded): String                    = h.renderedValue
  }

  final case class MaxForwards(count: Int) extends zio.http.Header {
    def headerName: String    = MaxForwards.name
    def renderedValue: String = count.toString
  }

  object MaxForwards extends Typed[MaxForwards] {
    val name: String = "max-forwards"

    def parse(value: String): Either[String, MaxForwards] =
      try {
        val n = value.trim.toInt
        if (n < 0) Left(s"Invalid max-forwards: $n")
        else Right(MaxForwards(n))
      } catch {
        case _: NumberFormatException => Left(s"Invalid max-forwards: $value")
      }

    def render(h: MaxForwards): String = h.renderedValue
  }

  final case class From(email: String) extends zio.http.Header {
    def headerName: String    = From.name
    def renderedValue: String = email
  }

  object From extends Typed[From] {
    val name: String                               = "from"
    def parse(value: String): Either[String, From] = Right(From(value.trim))
    def render(h: From): String                    = h.renderedValue
  }

  final case class CookieHeader(value: String) extends zio.http.Header {
    def headerName: String    = CookieHeader.name
    def renderedValue: String = value
  }

  object CookieHeader extends Typed[CookieHeader] {
    val name: String                                       = "cookie"
    def parse(value: String): Either[String, CookieHeader] = Right(CookieHeader(value))
    def render(h: CookieHeader): String                    = h.renderedValue
  }

  final case class SetCookieHeader(value: String) extends zio.http.Header {
    def headerName: String    = SetCookieHeader.name
    def renderedValue: String = value
  }

  object SetCookieHeader extends Typed[SetCookieHeader] {
    val name: String                                          = "set-cookie"
    def parse(value: String): Either[String, SetCookieHeader] = Right(SetCookieHeader(value))
    def render(h: SetCookieHeader): String                    = h.renderedValue
  }

  sealed trait Connection extends zio.http.Header {
    def headerName: String    = Connection.name
    def renderedValue: String = Connection.render(this)
  }

  object Connection extends Typed[Connection] {
    val name: String = "connection"

    case object Close     extends Connection
    case object KeepAlive extends Connection

    final case class Other(value: String) extends Connection

    def parse(value: String): Either[String, Connection] =
      value.trim.toLowerCase match {
        case "close"      => Right(Close)
        case "keep-alive" => Right(KeepAlive)
        case other        => Right(Other(other))
      }

    def render(h: Connection): String = h match {
      case Close     => "close"
      case KeepAlive => "keep-alive"
      case Other(v)  => v
    }
  }

  final case class Upgrade(protocol: String) extends zio.http.Header {
    def headerName: String    = Upgrade.name
    def renderedValue: String = protocol
  }

  object Upgrade extends Typed[Upgrade] {
    val name: String                                  = "upgrade"
    def parse(value: String): Either[String, Upgrade] = Right(Upgrade(value.trim))
    def render(h: Upgrade): String                    = h.renderedValue
  }

  final case class Te(value: String) extends zio.http.Header {
    def headerName: String    = Te.name
    def renderedValue: String = value
  }

  object Te extends Typed[Te] {
    val name: String                             = "te"
    def parse(value: String): Either[String, Te] = Right(Te(value.trim))
    def render(h: Te): String                    = h.renderedValue
  }

  final case class Trailer(headers: Chunk[String]) extends zio.http.Header {
    def headerName: String    = Trailer.name
    def renderedValue: String = headers.mkString(", ")
  }

  object Trailer extends Typed[Trailer] {
    val name: String = "trailer"

    def parse(value: String): Either[String, Trailer] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty trailer header")
      else Right(Trailer(Chunk.fromArray(parts)))
    }

    def render(h: Trailer): String = h.renderedValue
  }

  sealed trait TransferEncoding extends zio.http.Header {
    def headerName: String    = TransferEncoding.name
    def renderedValue: String = TransferEncoding.render(this)
  }

  object TransferEncoding extends Typed[TransferEncoding] {
    val name: String = "transfer-encoding"

    case object Chunked  extends TransferEncoding
    case object Compress extends TransferEncoding
    case object Deflate  extends TransferEncoding
    case object GZip     extends TransferEncoding
    case object Identity extends TransferEncoding

    final case class Multiple(values: Chunk[TransferEncoding]) extends TransferEncoding

    private def findEncoding(value: String): Option[TransferEncoding] =
      value.trim.toLowerCase match {
        case "chunked"  => Some(Chunked)
        case "compress" => Some(Compress)
        case "deflate"  => Some(Deflate)
        case "gzip"     => Some(GZip)
        case "identity" => Some(Identity)
        case _          => None
      }

    def parse(value: String): Either[String, TransferEncoding] = {
      val parts  = value.split(',')
      val parsed = Chunk.fromArray(parts.flatMap(s => findEncoding(s)))
      if (parsed.isEmpty) Left(s"Invalid transfer-encoding: $value")
      else if (parsed.length == 1) Right(parsed(0))
      else Right(Multiple(parsed))
    }

    def render(h: TransferEncoding): String = h match {
      case Chunked     => "chunked"
      case Compress    => "compress"
      case Deflate     => "deflate"
      case GZip        => "gzip"
      case Identity    => "identity"
      case Multiple(v) => v.map(render).mkString(", ")
    }
  }

  sealed trait CacheControl extends zio.http.Header {
    def headerName: String    = CacheControl.name
    def renderedValue: String = CacheControl.render(this)
  }

  object CacheControl extends Typed[CacheControl] {
    val name: String = "cache-control"

    case object NoCache         extends CacheControl
    case object NoStore         extends CacheControl
    case object NoTransform     extends CacheControl
    case object Public          extends CacheControl
    case object Private         extends CacheControl
    case object MustRevalidate  extends CacheControl
    case object ProxyRevalidate extends CacheControl
    case object Immutable       extends CacheControl
    case object OnlyIfCached    extends CacheControl
    case object MustUnderstand  extends CacheControl

    final case class MaxAge(seconds: Long)                     extends CacheControl
    final case class SMaxAge(seconds: Long)                    extends CacheControl
    final case class MaxStale(seconds: Option[Long])           extends CacheControl
    final case class MinFresh(seconds: Long)                   extends CacheControl
    final case class StaleWhileRevalidate(seconds: Long)       extends CacheControl
    final case class StaleIfError(seconds: Long)               extends CacheControl
    final case class Multiple(directives: Chunk[CacheControl]) extends CacheControl

    def parse(value: String): Either[String, CacheControl] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty cache-control header")
      val parts = trimmed.split(",")
      if (parts.length == 1) parseDirective(parts(0).trim)
      else {
        val parsed = new Array[CacheControl](parts.length)
        var i      = 0
        while (i < parts.length) {
          parseDirective(parts(i).trim) match {
            case Right(d) => parsed(i) = d
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        Right(Multiple(Chunk.fromArray(parsed)))
      }
    }

    def render(h: CacheControl): String = h match {
      case NoCache                 => "no-cache"
      case NoStore                 => "no-store"
      case NoTransform             => "no-transform"
      case Public                  => "public"
      case Private                 => "private"
      case MustRevalidate          => "must-revalidate"
      case ProxyRevalidate         => "proxy-revalidate"
      case Immutable               => "immutable"
      case OnlyIfCached            => "only-if-cached"
      case MustUnderstand          => "must-understand"
      case MaxAge(s)               => s"max-age=$s"
      case SMaxAge(s)              => s"s-maxage=$s"
      case MaxStale(Some(s))       => s"max-stale=$s"
      case MaxStale(None)          => "max-stale"
      case MinFresh(s)             => s"min-fresh=$s"
      case StaleWhileRevalidate(s) => s"stale-while-revalidate=$s"
      case StaleIfError(s)         => s"stale-if-error=$s"
      case Multiple(ds)            => ds.map(render).mkString(", ")
    }

    private def parseDirective(s: String): Either[String, CacheControl] = {
      val eqIdx = s.indexOf('=')
      if (eqIdx < 0) {
        s.toLowerCase match {
          case "no-cache"         => Right(NoCache)
          case "no-store"         => Right(NoStore)
          case "no-transform"     => Right(NoTransform)
          case "public"           => Right(Public)
          case "private"          => Right(Private)
          case "must-revalidate"  => Right(MustRevalidate)
          case "proxy-revalidate" => Right(ProxyRevalidate)
          case "immutable"        => Right(Immutable)
          case "only-if-cached"   => Right(OnlyIfCached)
          case "must-understand"  => Right(MustUnderstand)
          case "max-stale"        => Right(MaxStale(None))
          case other              => Left(s"Unknown cache-control directive: $other")
        }
      } else {
        val key = s.substring(0, eqIdx).trim.toLowerCase
        val raw = s.substring(eqIdx + 1).trim
        try {
          val num = raw.toLong
          key match {
            case "max-age"                => Right(MaxAge(num))
            case "s-maxage"               => Right(SMaxAge(num))
            case "max-stale"              => Right(MaxStale(Some(num)))
            case "min-fresh"              => Right(MinFresh(num))
            case "stale-while-revalidate" => Right(StaleWhileRevalidate(num))
            case "stale-if-error"         => Right(StaleIfError(num))
            case other                    => Left(s"Unknown cache-control directive: $other")
          }
        } catch {
          case _: NumberFormatException => Left(s"Invalid number in cache-control directive: $raw")
        }
      }
    }
  }

  final case class ETag(tag: String, weak: Boolean) extends zio.http.Header {
    def headerName: String    = ETag.name
    def renderedValue: String = ETag.render(this)
  }

  object ETag extends Typed[ETag] {
    val name: String = "etag"

    def parse(value: String): Either[String, ETag] = {
      val trimmed = value.trim
      if (trimmed.startsWith("W/\"") && trimmed.endsWith("\""))
        Right(ETag(trimmed.substring(3, trimmed.length - 1), weak = true))
      else if (trimmed.startsWith("\"") && trimmed.endsWith("\""))
        Right(ETag(trimmed.substring(1, trimmed.length - 1), weak = false))
      else
        Left(s"Invalid etag: $trimmed")
    }

    def render(h: ETag): String =
      if (h.weak) s"""W/\"${h.tag}\""""
      else s"""\"${h.tag}\""""
  }

  sealed trait IfMatch extends zio.http.Header {
    def headerName: String    = IfMatch.name
    def renderedValue: String = IfMatch.render(this)
  }

  object IfMatch extends Typed[IfMatch] {
    val name: String = "if-match"

    case object Any                           extends IfMatch
    final case class ETags(tags: Chunk[ETag]) extends IfMatch

    def parse(value: String): Either[String, IfMatch] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts  = trimmed.split(",")
        val parsed = new scala.collection.mutable.ArrayBuffer[ETag](parts.length)
        var i      = 0
        while (i < parts.length) {
          ETag.parse(parts(i).trim) match {
            case Right(e) => parsed += e
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        if (parsed.isEmpty) Left("Empty if-match header")
        else Right(ETags(Chunk.fromArray(parsed.toArray)))
      }
    }

    def render(h: IfMatch): String = h match {
      case Any       => "*"
      case ETags(ts) => ts.map(ETag.render).mkString(", ")
    }
  }

  sealed trait IfNoneMatch extends zio.http.Header {
    def headerName: String    = IfNoneMatch.name
    def renderedValue: String = IfNoneMatch.render(this)
  }

  object IfNoneMatch extends Typed[IfNoneMatch] {
    val name: String = "if-none-match"

    case object Any                           extends IfNoneMatch
    final case class ETags(tags: Chunk[ETag]) extends IfNoneMatch

    def parse(value: String): Either[String, IfNoneMatch] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts  = trimmed.split(",")
        val parsed = new scala.collection.mutable.ArrayBuffer[ETag](parts.length)
        var i      = 0
        while (i < parts.length) {
          ETag.parse(parts(i).trim) match {
            case Right(e) => parsed += e
            case Left(e)  => return Left(e)
          }
          i += 1
        }
        if (parsed.isEmpty) Left("Empty if-none-match header")
        else Right(ETags(Chunk.fromArray(parsed.toArray)))
      }
    }

    def render(h: IfNoneMatch): String = h match {
      case Any       => "*"
      case ETags(ts) => ts.map(ETag.render).mkString(", ")
    }
  }

  final case class IfModifiedSince(date: String) extends zio.http.Header {
    def headerName: String    = IfModifiedSince.name
    def renderedValue: String = date
  }

  object IfModifiedSince extends Typed[IfModifiedSince] {
    val name: String                                          = "if-modified-since"
    def parse(value: String): Either[String, IfModifiedSince] = Right(IfModifiedSince(value.trim))
    def render(h: IfModifiedSince): String                    = h.renderedValue
  }

  final case class IfUnmodifiedSince(date: String) extends zio.http.Header {
    def headerName: String    = IfUnmodifiedSince.name
    def renderedValue: String = date
  }

  object IfUnmodifiedSince extends Typed[IfUnmodifiedSince] {
    val name: String                                            = "if-unmodified-since"
    def parse(value: String): Either[String, IfUnmodifiedSince] = Right(IfUnmodifiedSince(value.trim))
    def render(h: IfUnmodifiedSince): String                    = h.renderedValue
  }

  final case class IfRange(value: String) extends zio.http.Header {
    def headerName: String    = IfRange.name
    def renderedValue: String = value
  }

  object IfRange extends Typed[IfRange] {
    val name: String                                  = "if-range"
    def parse(value: String): Either[String, IfRange] = Right(IfRange(value.trim))
    def render(h: IfRange): String                    = h.renderedValue
  }

  final case class Expires(date: String) extends zio.http.Header {
    def headerName: String    = Expires.name
    def renderedValue: String = date
  }

  object Expires extends Typed[Expires] {
    val name: String                                  = "expires"
    def parse(value: String): Either[String, Expires] = Right(Expires(value.trim))
    def render(h: Expires): String                    = h.renderedValue
  }

  final case class Age(seconds: Long) extends zio.http.Header {
    def headerName: String    = Age.name
    def renderedValue: String = seconds.toString
  }

  object Age extends Typed[Age] {
    val name: String = "age"

    def parse(value: String): Either[String, Age] =
      try {
        val s = value.trim.toLong
        if (s < 0) Left(s"Invalid age: $s")
        else Right(Age(s))
      } catch {
        case _: NumberFormatException => Left(s"Invalid age: $value")
      }

    def render(h: Age): String = h.renderedValue
  }

  final case class LastModified(date: String) extends zio.http.Header {
    def headerName: String    = LastModified.name
    def renderedValue: String = date
  }

  object LastModified extends Typed[LastModified] {
    val name: String                                       = "last-modified"
    def parse(value: String): Either[String, LastModified] = Right(LastModified(value.trim))
    def render(h: LastModified): String                    = h.renderedValue
  }

  final case class Pragma(directives: String) extends zio.http.Header {
    def headerName: String    = Pragma.name
    def renderedValue: String = directives
  }

  object Pragma extends Typed[Pragma] {
    val name: String                                 = "pragma"
    def parse(value: String): Either[String, Pragma] = Right(Pragma(value.trim))
    def render(h: Pragma): String                    = h.renderedValue
  }

  sealed trait Vary extends zio.http.Header {
    def headerName: String    = Vary.name
    def renderedValue: String = Vary.render(this)
  }

  object Vary extends Typed[Vary] {
    val name: String = "vary"

    case object Any                                extends Vary
    final case class Headers(names: Chunk[String]) extends Vary

    def parse(value: String): Either[String, Vary] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(Any)
      else {
        val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
        if (parts.isEmpty) Left("Empty vary header")
        else Right(Headers(Chunk.fromArray(parts)))
      }
    }

    def render(h: Vary): String = h match {
      case Any        => "*"
      case Headers(n) => n.mkString(", ")
    }
  }

  final case class Accept(mediaRanges: Chunk[Accept.MediaRange]) extends zio.http.Header {
    def headerName: String    = Accept.name
    def renderedValue: String = Accept.render(this)
  }

  object Accept extends Typed[Accept] {
    val name: String = "accept"

    final case class MediaRange(mediaType: MediaType, quality: Double = 1.0)

    def parse(value: String): Either[String, Accept] = {
      if (value.trim.isEmpty) return Left("Accept header cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val ranges = Chunk.fromArray(parts.map(parseMediaRange))
      val errors = ranges.collect { case Left(err) => err }
      if (errors.nonEmpty) Left(errors.mkString("; "))
      else Right(Accept(ranges.collect { case Right(mr) => mr }))
    }

    def render(h: Accept): String =
      h.mediaRanges.map { mr =>
        if (mr.quality == 1.0) mr.mediaType.fullType
        else s"${mr.mediaType.fullType};q=${mr.quality}"
      }.mkString(", ")

    private def parseMediaRange(s: String): Either[String, MediaRange] = {
      val semiIdx = s.indexOf(';')
      if (semiIdx < 0) {
        MediaType.parse(s.trim).map(mt => MediaRange(mt))
      } else {
        val mediaStr  = s.substring(0, semiIdx).trim
        val paramsStr = s.substring(semiIdx + 1).trim
        val quality   = parseQuality(paramsStr)
        MediaType.parse(mediaStr).map(mt => MediaRange(mt, quality))
      }
    }

    private def parseQuality(params: String): Double = {
      val qIdx = params.indexOf("q=")
      if (qIdx >= 0) {
        try params.substring(qIdx + 2).trim.toDouble
        catch { case _: NumberFormatException => 1.0 }
      } else 1.0
    }
  }

  sealed trait AcceptEncoding extends zio.http.Header {
    def headerName: String    = AcceptEncoding.name
    def renderedValue: String = AcceptEncoding.render(this)
  }

  object AcceptEncoding extends Typed[AcceptEncoding] {
    val name: String = "accept-encoding"

    final case class GZip(weight: Option[Double] = None)     extends AcceptEncoding
    final case class Deflate(weight: Option[Double] = None)  extends AcceptEncoding
    final case class Br(weight: Option[Double] = None)       extends AcceptEncoding
    final case class Compress(weight: Option[Double] = None) extends AcceptEncoding
    final case class Identity(weight: Option[Double] = None) extends AcceptEncoding
    final case class Any(weight: Option[Double] = None)      extends AcceptEncoding

    final case class Multiple(values: Chunk[AcceptEncoding]) extends AcceptEncoding

    def parse(value: String): Either[String, AcceptEncoding] = {
      val parts = value.split(',').map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left(s"Invalid accept-encoding: $value")
      else if (parts.length == 1) Right(parseSingle(parts(0)))
      else Right(Multiple(Chunk.fromArray(parts.map(parseSingle))))
    }

    def render(h: AcceptEncoding): String = h match {
      case GZip(w)     => renderWithWeight("gzip", w)
      case Deflate(w)  => renderWithWeight("deflate", w)
      case Br(w)       => renderWithWeight("br", w)
      case Compress(w) => renderWithWeight("compress", w)
      case Identity(w) => renderWithWeight("identity", w)
      case Any(w)      => renderWithWeight("*", w)
      case Multiple(v) => v.map(render).mkString(", ")
    }

    private def parseSingle(s: String): AcceptEncoding = {
      val qIdx          = s.indexOf(";q=")
      val (raw, weight) =
        if (qIdx >= 0) {
          val w =
            try Some(s.substring(qIdx + 3).trim.toDouble)
            catch { case _: NumberFormatException => None }
          (s.substring(0, qIdx).trim, w)
        } else (s.trim, None)

      raw.toLowerCase match {
        case "gzip"     => GZip(weight)
        case "deflate"  => Deflate(weight)
        case "br"       => Br(weight)
        case "compress" => Compress(weight)
        case "identity" => Identity(weight)
        case "*"        => Any(weight)
        case _          => GZip(weight)
      }
    }

    private def renderWithWeight(name: String, weight: Option[Double]): String =
      weight match {
        case Some(w) => s"$name;q=$w"
        case None    => name
      }
  }

  final case class AcceptLanguage(languages: Chunk[AcceptLanguage.LanguageRange]) extends zio.http.Header {
    def headerName: String    = AcceptLanguage.name
    def renderedValue: String = AcceptLanguage.render(this)
  }

  object AcceptLanguage extends Typed[AcceptLanguage] {
    val name: String = "accept-language"

    final case class LanguageRange(tag: String, quality: Double = 1.0)

    def parse(value: String): Either[String, AcceptLanguage] = {
      if (value.trim.isEmpty) return Left("Accept-Language cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val ranges = Chunk.fromArray(parts.map(parseLangRange))
      Right(AcceptLanguage(ranges))
    }

    def render(h: AcceptLanguage): String =
      h.languages.map { lr =>
        if (lr.quality == 1.0) lr.tag
        else s"${lr.tag};q=${lr.quality}"
      }.mkString(", ")

    private def parseLangRange(s: String): LanguageRange = {
      val qIdx = s.indexOf(";q=")
      if (qIdx >= 0) {
        val tag    = s.substring(0, qIdx).trim
        val weight =
          try s.substring(qIdx + 3).trim.toDouble
          catch { case _: NumberFormatException => 1.0 }
        LanguageRange(tag, weight)
      } else {
        LanguageRange(s.trim)
      }
    }
  }

  sealed trait AcceptRanges extends zio.http.Header {
    def headerName: String    = AcceptRanges.name
    def renderedValue: String = AcceptRanges.render(this)
  }

  object AcceptRanges extends Typed[AcceptRanges] {
    val name: String = "accept-ranges"

    case object Bytes extends AcceptRanges
    case object None_ extends AcceptRanges

    def parse(value: String): Either[String, AcceptRanges] =
      value.trim.toLowerCase match {
        case "bytes" => Right(Bytes)
        case "none"  => Right(None_)
        case other   => Left(s"Invalid accept-ranges: $other")
      }

    def render(h: AcceptRanges): String = h match {
      case Bytes => "bytes"
      case None_ => "none"
    }
  }

  final case class AcceptPatch(mediaTypes: Chunk[MediaType]) extends zio.http.Header {
    def headerName: String    = AcceptPatch.name
    def renderedValue: String = AcceptPatch.render(this)
  }

  object AcceptPatch extends Typed[AcceptPatch] {
    val name: String = "accept-patch"

    def parse(value: String): Either[String, AcceptPatch] = {
      if (value.trim.isEmpty) return Left("Accept-Patch header cannot be empty")
      val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
      val parsed = parts.flatMap(s => MediaType.parse(s).toOption)
      if (parsed.isEmpty) Left(s"Invalid accept-patch: $value")
      else Right(AcceptPatch(Chunk.fromArray(parsed)))
    }

    def render(h: AcceptPatch): String =
      h.mediaTypes.map(_.fullType).mkString(", ")
  }

  sealed trait AccessControlAllowOrigin extends zio.http.Header {
    def headerName: String    = AccessControlAllowOrigin.name
    def renderedValue: String = AccessControlAllowOrigin.render(this)
  }

  object AccessControlAllowOrigin extends Typed[AccessControlAllowOrigin] {
    val name: String = "access-control-allow-origin"

    case object All                           extends AccessControlAllowOrigin
    final case class Specific(origin: String) extends AccessControlAllowOrigin

    def parse(value: String): Either[String, AccessControlAllowOrigin] = {
      val trimmed = value.trim
      if (trimmed == "*") Right(All)
      else if (trimmed.isEmpty) Left("Empty access-control-allow-origin header")
      else Right(Specific(trimmed))
    }

    def render(h: AccessControlAllowOrigin): String = h match {
      case All         => "*"
      case Specific(o) => o
    }
  }

  final case class AccessControlAllowMethods(methods: Chunk[Method]) extends zio.http.Header {
    def headerName: String    = AccessControlAllowMethods.name
    def renderedValue: String = AccessControlAllowMethods.render(this)
  }

  object AccessControlAllowMethods extends Typed[AccessControlAllowMethods] {
    val name: String = "access-control-allow-methods"

    def parse(value: String): Either[String, AccessControlAllowMethods] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-allow-methods header")
      val parts   = trimmed.split(",")
      val methods = new scala.collection.mutable.ArrayBuffer[Method](parts.length)
      var i       = 0
      while (i < parts.length) {
        val name = parts(i).trim
        Method.fromString(name) match {
          case Some(m) => methods += m
          case None    => return Left(s"Unknown method: $name")
        }
        i += 1
      }
      Right(AccessControlAllowMethods(Chunk.fromArray(methods.toArray)))
    }

    def render(h: AccessControlAllowMethods): String =
      h.methods.map(_.name).mkString(", ")
  }

  final case class AccessControlAllowHeaders(headers: Chunk[String]) extends zio.http.Header {
    def headerName: String    = AccessControlAllowHeaders.name
    def renderedValue: String = AccessControlAllowHeaders.render(this)
  }

  object AccessControlAllowHeaders extends Typed[AccessControlAllowHeaders] {
    val name: String = "access-control-allow-headers"

    def parse(value: String): Either[String, AccessControlAllowHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-allow-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlAllowHeaders(Chunk.fromArray(parts)))
    }

    def render(h: AccessControlAllowHeaders): String =
      h.headers.mkString(", ")
  }

  final case class AccessControlAllowCredentials(allow: Boolean) extends zio.http.Header {
    def headerName: String    = AccessControlAllowCredentials.name
    def renderedValue: String = AccessControlAllowCredentials.render(this)
  }

  object AccessControlAllowCredentials extends Typed[AccessControlAllowCredentials] {
    val name: String = "access-control-allow-credentials"

    def parse(value: String): Either[String, AccessControlAllowCredentials] =
      value.trim.toLowerCase match {
        case "true"  => Right(AccessControlAllowCredentials(true))
        case "false" => Right(AccessControlAllowCredentials(false))
        case other   => Left(s"Invalid access-control-allow-credentials: $other")
      }

    def render(h: AccessControlAllowCredentials): String =
      if (h.allow) "true" else "false"
  }

  final case class AccessControlExposeHeaders(headers: Chunk[String]) extends zio.http.Header {
    def headerName: String    = AccessControlExposeHeaders.name
    def renderedValue: String = AccessControlExposeHeaders.render(this)
  }

  object AccessControlExposeHeaders extends Typed[AccessControlExposeHeaders] {
    val name: String = "access-control-expose-headers"

    def parse(value: String): Either[String, AccessControlExposeHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-expose-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlExposeHeaders(Chunk.fromArray(parts)))
    }

    def render(h: AccessControlExposeHeaders): String =
      h.headers.mkString(", ")
  }

  final case class AccessControlMaxAge(seconds: Long) extends zio.http.Header {
    def headerName: String    = AccessControlMaxAge.name
    def renderedValue: String = AccessControlMaxAge.render(this)
  }

  object AccessControlMaxAge extends Typed[AccessControlMaxAge] {
    val name: String = "access-control-max-age"

    def parse(value: String): Either[String, AccessControlMaxAge] =
      try {
        val s = value.trim.toLong
        if (s < 0) Left(s"Invalid access-control-max-age: $s")
        else Right(AccessControlMaxAge(s))
      } catch {
        case _: NumberFormatException => Left(s"Invalid access-control-max-age: $value")
      }

    def render(h: AccessControlMaxAge): String = h.seconds.toString
  }

  final case class AccessControlRequestHeaders(headers: Chunk[String]) extends zio.http.Header {
    def headerName: String    = AccessControlRequestHeaders.name
    def renderedValue: String = AccessControlRequestHeaders.render(this)
  }

  object AccessControlRequestHeaders extends Typed[AccessControlRequestHeaders] {
    val name: String = "access-control-request-headers"

    def parse(value: String): Either[String, AccessControlRequestHeaders] = {
      val trimmed = value.trim
      if (trimmed.isEmpty) return Left("Empty access-control-request-headers header")
      val parts = trimmed.split(",").map(_.trim).filter(_.nonEmpty)
      Right(AccessControlRequestHeaders(Chunk.fromArray(parts)))
    }

    def render(h: AccessControlRequestHeaders): String =
      h.headers.mkString(", ")
  }

  final case class AccessControlRequestMethod(method: Method) extends zio.http.Header {
    def headerName: String    = AccessControlRequestMethod.name
    def renderedValue: String = AccessControlRequestMethod.render(this)
  }

  object AccessControlRequestMethod extends Typed[AccessControlRequestMethod] {
    val name: String = "access-control-request-method"

    def parse(value: String): Either[String, AccessControlRequestMethod] = {
      val trimmed = value.trim
      Method.fromString(trimmed) match {
        case Some(m) => Right(AccessControlRequestMethod(m))
        case None    => Left(s"Unknown method: $trimmed")
      }
    }

    def render(h: AccessControlRequestMethod): String = h.method.name
  }

  final case class UserAgent(product: String) extends zio.http.Header {
    def headerName: String    = UserAgent.name
    def renderedValue: String = product
  }

  object UserAgent extends Typed[UserAgent] {
    val name: String                                    = "user-agent"
    def parse(value: String): Either[String, UserAgent] = Right(UserAgent(value.trim))
    def render(h: UserAgent): String                    = h.renderedValue
  }

  final case class Server(product: String) extends zio.http.Header {
    def headerName: String    = Server.name
    def renderedValue: String = product
  }

  object Server extends Typed[Server] {
    val name: String                                 = "server"
    def parse(value: String): Either[String, Server] = Right(Server(value.trim))
    def render(h: Server): String                    = h.renderedValue
  }

  final case class Date(value: String) extends zio.http.Header {
    def headerName: String    = Date.name
    def renderedValue: String = value
  }

  object Date extends Typed[Date] {
    val name: String                               = "date"
    def parse(value: String): Either[String, Date] = Right(Date(value.trim))
    def render(h: Date): String                    = h.renderedValue
  }

  final case class Link(value: String) extends zio.http.Header {
    def headerName: String    = Link.name
    def renderedValue: String = value
  }

  object Link extends Typed[Link] {
    val name: String                               = "link"
    def parse(value: String): Either[String, Link] = Right(Link(value.trim))
    def render(h: Link): String                    = h.renderedValue
  }

  final case class RetryAfter(value: String) extends zio.http.Header {
    def headerName: String    = RetryAfter.name
    def renderedValue: String = value
  }

  object RetryAfter extends Typed[RetryAfter] {
    val name: String                                     = "retry-after"
    def parse(value: String): Either[String, RetryAfter] = Right(RetryAfter(value.trim))
    def render(h: RetryAfter): String                    = h.renderedValue
  }

  final case class Allow(methods: Chunk[Method]) extends zio.http.Header {
    def headerName: String    = Allow.name
    def renderedValue: String = methods.map(_.name).mkString(", ")
  }

  object Allow extends Typed[Allow] {
    val name: String = "allow"

    def parse(value: String): Either[String, Allow] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty allow header")
      else {
        val methods = new scala.collection.mutable.ArrayBuffer[Method](parts.length)
        var i       = 0
        while (i < parts.length) {
          Method.fromString(parts(i)) match {
            case Some(m) => methods += m
            case None    => return Left(s"Invalid method: ${parts(i)}")
          }
          i += 1
        }
        Right(Allow(Chunk.fromArray(methods.toArray)))
      }
    }

    def render(h: Allow): String = h.renderedValue
  }

  final case class Expect(value: String) extends zio.http.Header {
    def headerName: String    = Expect.name
    def renderedValue: String = value
  }

  object Expect extends Typed[Expect] {
    val name: String                                 = "expect"
    def parse(value: String): Either[String, Expect] = Right(Expect(value.trim))
    def render(h: Expect): String                    = h.renderedValue
  }

  final case class Range(unit: String, ranges: String) extends zio.http.Header {
    def headerName: String    = Range.name
    def renderedValue: String = unit + "=" + ranges
  }

  object Range extends Typed[Range] {
    val name: String = "range"

    def parse(value: String): Either[String, Range] = {
      val trimmed = value.trim
      val eqIdx   = trimmed.indexOf('=')
      if (eqIdx < 0) Left(s"Invalid range: $trimmed")
      else {
        val unit      = trimmed.substring(0, eqIdx).trim
        val rangeSpec = trimmed.substring(eqIdx + 1).trim
        if (unit.isEmpty || rangeSpec.isEmpty) Left(s"Invalid range: $trimmed")
        else Right(Range(unit, rangeSpec))
      }
    }

    def render(h: Range): String = h.renderedValue
  }

  sealed trait XFrameOptions extends zio.http.Header {
    def headerName: String    = XFrameOptions.name
    def renderedValue: String = XFrameOptions.render(this)
  }

  object XFrameOptions extends Typed[XFrameOptions] {
    val name: String = "x-frame-options"

    case object Deny       extends XFrameOptions
    case object SameOrigin extends XFrameOptions

    def parse(value: String): Either[String, XFrameOptions] =
      value.trim.toLowerCase match {
        case "deny"       => Right(Deny)
        case "sameorigin" => Right(SameOrigin)
        case other        => Left(s"Invalid x-frame-options: $other")
      }

    def render(h: XFrameOptions): String = h match {
      case Deny       => "DENY"
      case SameOrigin => "SAMEORIGIN"
    }
  }

  final case class XRequestedWith(value: String) extends zio.http.Header {
    def headerName: String    = XRequestedWith.name
    def renderedValue: String = value
  }

  object XRequestedWith extends Typed[XRequestedWith] {
    val name: String                                         = "x-requested-with"
    def parse(value: String): Either[String, XRequestedWith] = Right(XRequestedWith(value.trim))
    def render(h: XRequestedWith): String                    = h.renderedValue
  }

  sealed trait DNT extends zio.http.Header {
    def headerName: String    = DNT.name
    def renderedValue: String = DNT.render(this)
  }

  object DNT extends Typed[DNT] {
    val name: String = "dnt"

    case object TrackingAllowed    extends DNT
    case object TrackingNotAllowed extends DNT
    case object Unset              extends DNT

    def parse(value: String): Either[String, DNT] =
      value.trim match {
        case "0"    => Right(TrackingAllowed)
        case "1"    => Right(TrackingNotAllowed)
        case "null" => Right(Unset)
        case other  => Left(s"Invalid dnt: $other")
      }

    def render(h: DNT): String = h match {
      case TrackingAllowed    => "0"
      case TrackingNotAllowed => "1"
      case Unset              => "null"
    }
  }

  final case class UpgradeInsecureRequests(upgrade: Boolean) extends zio.http.Header {
    def headerName: String    = UpgradeInsecureRequests.name
    def renderedValue: String = if (upgrade) "1" else "0"
  }

  object UpgradeInsecureRequests extends Typed[UpgradeInsecureRequests] {
    val name: String = "upgrade-insecure-requests"

    def parse(value: String): Either[String, UpgradeInsecureRequests] =
      value.trim match {
        case "1"   => Right(UpgradeInsecureRequests(true))
        case "0"   => Right(UpgradeInsecureRequests(false))
        case other => Left(s"Invalid upgrade-insecure-requests: $other")
      }

    def render(h: UpgradeInsecureRequests): String = h.renderedValue
  }

  final case class ClearSiteData(directives: Chunk[String]) extends zio.http.Header {
    def headerName: String    = ClearSiteData.name
    def renderedValue: String = directives.map(d => "\"" + d + "\"").mkString(", ")
  }

  object ClearSiteData extends Typed[ClearSiteData] {
    val name: String = "clear-site-data"

    def parse(value: String): Either[String, ClearSiteData] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty clear-site-data header")
      else {
        val directives = parts.map { p =>
          val stripped = p.trim
          if (stripped.startsWith("\"") && stripped.endsWith("\""))
            stripped.substring(1, stripped.length - 1)
          else
            stripped
        }
        Right(ClearSiteData(Chunk.fromArray(directives)))
      }
    }

    def render(h: ClearSiteData): String = h.renderedValue
  }

  final case class SecWebSocketAccept(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketAccept.name
    def renderedValue: String = value
  }

  object SecWebSocketAccept extends Typed[SecWebSocketAccept] {
    val name: String                                             = "sec-websocket-accept"
    def parse(value: String): Either[String, SecWebSocketAccept] = Right(SecWebSocketAccept(value.trim))
    def render(h: SecWebSocketAccept): String                    = h.renderedValue
  }

  final case class SecWebSocketExtensions(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketExtensions.name
    def renderedValue: String = value
  }

  object SecWebSocketExtensions extends Typed[SecWebSocketExtensions] {
    val name: String                                                 = "sec-websocket-extensions"
    def parse(value: String): Either[String, SecWebSocketExtensions] = Right(SecWebSocketExtensions(value.trim))
    def render(h: SecWebSocketExtensions): String                    = h.renderedValue
  }

  final case class SecWebSocketKey(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketKey.name
    def renderedValue: String = value
  }

  object SecWebSocketKey extends Typed[SecWebSocketKey] {
    val name: String                                          = "sec-websocket-key"
    def parse(value: String): Either[String, SecWebSocketKey] = Right(SecWebSocketKey(value.trim))
    def render(h: SecWebSocketKey): String                    = h.renderedValue
  }

  final case class SecWebSocketLocation(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketLocation.name
    def renderedValue: String = value
  }

  object SecWebSocketLocation extends Typed[SecWebSocketLocation] {
    val name: String                                               = "sec-websocket-location"
    def parse(value: String): Either[String, SecWebSocketLocation] = Right(SecWebSocketLocation(value.trim))
    def render(h: SecWebSocketLocation): String                    = h.renderedValue
  }

  final case class SecWebSocketOrigin(value: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketOrigin.name
    def renderedValue: String = value
  }

  object SecWebSocketOrigin extends Typed[SecWebSocketOrigin] {
    val name: String                                             = "sec-websocket-origin"
    def parse(value: String): Either[String, SecWebSocketOrigin] = Right(SecWebSocketOrigin(value.trim))
    def render(h: SecWebSocketOrigin): String                    = h.renderedValue
  }

  final case class SecWebSocketProtocol(protocols: Chunk[String]) extends zio.http.Header {
    def headerName: String    = SecWebSocketProtocol.name
    def renderedValue: String = protocols.mkString(", ")
  }

  object SecWebSocketProtocol extends Typed[SecWebSocketProtocol] {
    val name: String = "sec-websocket-protocol"

    def parse(value: String): Either[String, SecWebSocketProtocol] = {
      val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
      if (parts.isEmpty) Left("Empty sec-websocket-protocol header")
      else Right(SecWebSocketProtocol(Chunk.fromArray(parts)))
    }

    def render(h: SecWebSocketProtocol): String = h.renderedValue
  }

  final case class SecWebSocketVersion(version: String) extends zio.http.Header {
    def headerName: String    = SecWebSocketVersion.name
    def renderedValue: String = version
  }

  object SecWebSocketVersion extends Typed[SecWebSocketVersion] {
    val name: String                                              = "sec-websocket-version"
    def parse(value: String): Either[String, SecWebSocketVersion] = Right(SecWebSocketVersion(value.trim))
    def render(h: SecWebSocketVersion): String                    = h.renderedValue
  }

}
