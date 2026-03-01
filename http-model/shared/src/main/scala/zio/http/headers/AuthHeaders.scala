package zio.http.headers

import zio.http._

// --- 1. Authorization ---

sealed trait Authorization extends Header {
  def headerName: String    = Authorization.name
  def renderedValue: String = Authorization.render(this)
}

object Authorization extends Header.Typed[Authorization] {
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

// --- 2. ProxyAuthorization ---

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

// --- 3. WWWAuthenticate ---

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

// --- 4. ProxyAuthenticate ---

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
