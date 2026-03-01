package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. Host ---

final case class Host(host: String, port: Option[Int]) extends Header {
  def headerName: String = Host.name

  def renderedValue: String = port match {
    case Some(p) => host + ":" + p
    case None    => host
  }
}

object Host extends Header.Typed[Host] {
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

// --- 2. Location ---

final case class Location(uri: String) extends Header {
  def headerName: String    = Location.name
  def renderedValue: String = uri
}

object Location extends Header.Typed[Location] {
  val name: String                                   = "location"
  def parse(value: String): Either[String, Location] = Right(Location(value.trim))
  def render(h: Location): String                    = h.renderedValue
}

// --- 3. Origin ---

sealed trait Origin extends Header {
  def headerName: String    = Origin.name
  def renderedValue: String = Origin.render(this)
}

object Origin extends Header.Typed[Origin] {
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

// --- 4. Referer ---

final case class Referer(uri: String) extends Header {
  def headerName: String    = Referer.name
  def renderedValue: String = uri
}

object Referer extends Header.Typed[Referer] {
  val name: String                                  = "referer"
  def parse(value: String): Either[String, Referer] = Right(Referer(value.trim))
  def render(h: Referer): String                    = h.renderedValue
}

// --- 5. Via ---

final case class Via(entries: Chunk[String]) extends Header {
  def headerName: String    = Via.name
  def renderedValue: String = entries.mkString(", ")
}

object Via extends Header.Typed[Via] {
  val name: String = "via"

  def parse(value: String): Either[String, Via] = {
    val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
    if (parts.isEmpty) Left("Empty via header")
    else Right(Via(Chunk.fromArray(parts)))
  }

  def render(h: Via): String = h.renderedValue
}

// --- 6. Forwarded ---

final case class Forwarded(params: String) extends Header {
  def headerName: String    = Forwarded.name
  def renderedValue: String = params
}

object Forwarded extends Header.Typed[Forwarded] {
  val name: String                                    = "forwarded"
  def parse(value: String): Either[String, Forwarded] = Right(Forwarded(value.trim))
  def render(h: Forwarded): String                    = h.renderedValue
}

// --- 7. MaxForwards ---

final case class MaxForwards(count: Int) extends Header {
  def headerName: String    = MaxForwards.name
  def renderedValue: String = count.toString
}

object MaxForwards extends Header.Typed[MaxForwards] {
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

// --- 8. From ---

final case class From(email: String) extends Header {
  def headerName: String    = From.name
  def renderedValue: String = email
}

object From extends Header.Typed[From] {
  val name: String                               = "from"
  def parse(value: String): Either[String, From] = Right(From(value.trim))
  def render(h: From): String                    = h.renderedValue
}
