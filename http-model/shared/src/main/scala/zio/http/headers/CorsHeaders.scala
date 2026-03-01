package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. AccessControlAllowOrigin ---

sealed trait AccessControlAllowOrigin extends Header {
  def headerName: String    = AccessControlAllowOrigin.name
  def renderedValue: String = AccessControlAllowOrigin.render(this)
}

object AccessControlAllowOrigin extends Header.Typed[AccessControlAllowOrigin] {
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

// --- 2. AccessControlAllowMethods ---

final case class AccessControlAllowMethods(methods: Chunk[Method]) extends Header {
  def headerName: String    = AccessControlAllowMethods.name
  def renderedValue: String = AccessControlAllowMethods.render(this)
}

object AccessControlAllowMethods extends Header.Typed[AccessControlAllowMethods] {
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

// --- 3. AccessControlAllowHeaders ---

final case class AccessControlAllowHeaders(headers: Chunk[String]) extends Header {
  def headerName: String    = AccessControlAllowHeaders.name
  def renderedValue: String = AccessControlAllowHeaders.render(this)
}

object AccessControlAllowHeaders extends Header.Typed[AccessControlAllowHeaders] {
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

// --- 4. AccessControlAllowCredentials ---

final case class AccessControlAllowCredentials(allow: Boolean) extends Header {
  def headerName: String    = AccessControlAllowCredentials.name
  def renderedValue: String = AccessControlAllowCredentials.render(this)
}

object AccessControlAllowCredentials extends Header.Typed[AccessControlAllowCredentials] {
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

// --- 5. AccessControlExposeHeaders ---

final case class AccessControlExposeHeaders(headers: Chunk[String]) extends Header {
  def headerName: String    = AccessControlExposeHeaders.name
  def renderedValue: String = AccessControlExposeHeaders.render(this)
}

object AccessControlExposeHeaders extends Header.Typed[AccessControlExposeHeaders] {
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

// --- 6. AccessControlMaxAge ---

final case class AccessControlMaxAge(seconds: Long) extends Header {
  def headerName: String    = AccessControlMaxAge.name
  def renderedValue: String = AccessControlMaxAge.render(this)
}

object AccessControlMaxAge extends Header.Typed[AccessControlMaxAge] {
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

// --- 7. AccessControlRequestHeaders ---

final case class AccessControlRequestHeaders(headers: Chunk[String]) extends Header {
  def headerName: String    = AccessControlRequestHeaders.name
  def renderedValue: String = AccessControlRequestHeaders.render(this)
}

object AccessControlRequestHeaders extends Header.Typed[AccessControlRequestHeaders] {
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

// --- 8. AccessControlRequestMethod ---

final case class AccessControlRequestMethod(method: Method) extends Header {
  def headerName: String    = AccessControlRequestMethod.name
  def renderedValue: String = AccessControlRequestMethod.render(this)
}

object AccessControlRequestMethod extends Header.Typed[AccessControlRequestMethod] {
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
