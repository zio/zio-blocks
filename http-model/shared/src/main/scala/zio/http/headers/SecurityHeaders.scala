package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. XFrameOptions ---

sealed trait XFrameOptions extends Header {
  def headerName: String    = XFrameOptions.name
  def renderedValue: String = XFrameOptions.render(this)
}

object XFrameOptions extends Header.Typed[XFrameOptions] {
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

// --- 2. XRequestedWith ---

final case class XRequestedWith(value: String) extends Header {
  def headerName: String    = XRequestedWith.name
  def renderedValue: String = value
}

object XRequestedWith extends Header.Typed[XRequestedWith] {
  val name: String                                         = "x-requested-with"
  def parse(value: String): Either[String, XRequestedWith] = Right(XRequestedWith(value.trim))
  def render(h: XRequestedWith): String                    = h.renderedValue
}

// --- 3. DNT ---

sealed trait DNT extends Header {
  def headerName: String    = DNT.name
  def renderedValue: String = DNT.render(this)
}

object DNT extends Header.Typed[DNT] {
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

// --- 4. UpgradeInsecureRequests ---

final case class UpgradeInsecureRequests(upgrade: Boolean) extends Header {
  def headerName: String    = UpgradeInsecureRequests.name
  def renderedValue: String = if (upgrade) "1" else "0"
}

object UpgradeInsecureRequests extends Header.Typed[UpgradeInsecureRequests] {
  val name: String = "upgrade-insecure-requests"

  def parse(value: String): Either[String, UpgradeInsecureRequests] =
    value.trim match {
      case "1"   => Right(UpgradeInsecureRequests(true))
      case "0"   => Right(UpgradeInsecureRequests(false))
      case other => Left(s"Invalid upgrade-insecure-requests: $other")
    }

  def render(h: UpgradeInsecureRequests): String = h.renderedValue
}

// --- 5. ClearSiteData ---

final case class ClearSiteData(directives: Chunk[String]) extends Header {
  def headerName: String    = ClearSiteData.name
  def renderedValue: String = directives.map(d => "\"" + d + "\"").mkString(", ")
}

object ClearSiteData extends Header.Typed[ClearSiteData] {
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
