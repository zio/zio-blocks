package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. CacheControl ---

sealed trait CacheControl extends Header {
  def headerName: String    = CacheControl.name
  def renderedValue: String = CacheControl.render(this)
}

object CacheControl extends Header.Typed[CacheControl] {
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

// --- 2. ETag ---

final case class ETag(tag: String, weak: Boolean) extends Header {
  def headerName: String    = ETag.name
  def renderedValue: String = ETag.render(this)
}

object ETag extends Header.Typed[ETag] {
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
    if (h.weak) s"""W/"${h.tag}""""
    else s""""${h.tag}""""
}

// --- 3. IfMatch ---

sealed trait IfMatch extends Header {
  def headerName: String    = IfMatch.name
  def renderedValue: String = IfMatch.render(this)
}

object IfMatch extends Header.Typed[IfMatch] {
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

// --- 4. IfNoneMatch ---

sealed trait IfNoneMatch extends Header {
  def headerName: String    = IfNoneMatch.name
  def renderedValue: String = IfNoneMatch.render(this)
}

object IfNoneMatch extends Header.Typed[IfNoneMatch] {
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

// --- 5. IfModifiedSince ---

final case class IfModifiedSince(date: String) extends Header {
  def headerName: String    = IfModifiedSince.name
  def renderedValue: String = date
}

object IfModifiedSince extends Header.Typed[IfModifiedSince] {
  val name: String                                          = "if-modified-since"
  def parse(value: String): Either[String, IfModifiedSince] = Right(IfModifiedSince(value.trim))
  def render(h: IfModifiedSince): String                    = h.renderedValue
}

// --- 6. IfUnmodifiedSince ---

final case class IfUnmodifiedSince(date: String) extends Header {
  def headerName: String    = IfUnmodifiedSince.name
  def renderedValue: String = date
}

object IfUnmodifiedSince extends Header.Typed[IfUnmodifiedSince] {
  val name: String                                            = "if-unmodified-since"
  def parse(value: String): Either[String, IfUnmodifiedSince] = Right(IfUnmodifiedSince(value.trim))
  def render(h: IfUnmodifiedSince): String                    = h.renderedValue
}

// --- 7. IfRange ---

final case class IfRange(value: String) extends Header {
  def headerName: String    = IfRange.name
  def renderedValue: String = value
}

object IfRange extends Header.Typed[IfRange] {
  val name: String                                  = "if-range"
  def parse(value: String): Either[String, IfRange] = Right(IfRange(value.trim))
  def render(h: IfRange): String                    = h.renderedValue
}

// --- 8. Expires ---

final case class Expires(date: String) extends Header {
  def headerName: String    = Expires.name
  def renderedValue: String = date
}

object Expires extends Header.Typed[Expires] {
  val name: String                                  = "expires"
  def parse(value: String): Either[String, Expires] = Right(Expires(value.trim))
  def render(h: Expires): String                    = h.renderedValue
}

// --- 9. Age ---

final case class Age(seconds: Long) extends Header {
  def headerName: String    = Age.name
  def renderedValue: String = seconds.toString
}

object Age extends Header.Typed[Age] {
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

// --- 10. LastModified ---

final case class LastModified(date: String) extends Header {
  def headerName: String    = LastModified.name
  def renderedValue: String = date
}

object LastModified extends Header.Typed[LastModified] {
  val name: String                                       = "last-modified"
  def parse(value: String): Either[String, LastModified] = Right(LastModified(value.trim))
  def render(h: LastModified): String                    = h.renderedValue
}

// --- 11. Pragma ---

final case class Pragma(directives: String) extends Header {
  def headerName: String    = Pragma.name
  def renderedValue: String = directives
}

object Pragma extends Header.Typed[Pragma] {
  val name: String                                 = "pragma"
  def parse(value: String): Either[String, Pragma] = Right(Pragma(value.trim))
  def render(h: Pragma): String                    = h.renderedValue
}

// --- 12. Vary ---

sealed trait Vary extends Header {
  def headerName: String    = Vary.name
  def renderedValue: String = Vary.render(this)
}

object Vary extends Header.Typed[Vary] {
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
