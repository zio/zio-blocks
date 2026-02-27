package zio.blocks.http

sealed trait Header {
  def headerName: String
  def renderedValue: String
}

trait HeaderType[H <: Header] {
  def name: String
  def parse(value: String): Either[String, H]
  def render(h: H): String
}

object Header {

  final case class ContentType(value: zio.blocks.http.ContentType) extends Header {
    def headerName: String    = ContentType.name
    def renderedValue: String = value.render
  }

  object ContentType extends HeaderType[ContentType] {
    val name: String = "content-type"

    def parse(value: String): Either[String, ContentType] =
      zio.blocks.http.ContentType.parse(value).map(ct => ContentType(ct))

    def render(h: ContentType): String = h.renderedValue
  }

  final case class Accept(mediaTypes: String) extends Header {
    def headerName: String    = Accept.name
    def renderedValue: String = mediaTypes
  }

  object Accept extends HeaderType[Accept] {
    val name: String                                 = "accept"
    def parse(value: String): Either[String, Accept] = Right(Accept(value))
    def render(h: Accept): String                    = h.renderedValue
  }

  final case class Authorization(credentials: String) extends Header {
    def headerName: String    = Authorization.name
    def renderedValue: String = credentials
  }

  object Authorization extends HeaderType[Authorization] {
    val name: String                                        = "authorization"
    def parse(value: String): Either[String, Authorization] = Right(Authorization(value))
    def render(h: Authorization): String                    = h.renderedValue
  }

  final case class Host(host: String, port: Option[Int]) extends Header {
    def headerName: String = Host.name

    def renderedValue: String = port match {
      case Some(p) => host + ":" + p
      case None    => host
    }
  }

  object Host extends HeaderType[Host] {
    val name: String = "host"

    def parse(value: String): Either[String, Host] = {
      if (value.isEmpty) return Left("Invalid host: cannot be empty")
      val colonIdx = value.lastIndexOf(':')
      if (colonIdx < 0) Right(Host(value, None))
      else {
        val hostPart = value.substring(0, colonIdx)
        val portStr  = value.substring(colonIdx + 1)
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

  final case class UserAgent(product: String) extends Header {
    def headerName: String    = UserAgent.name
    def renderedValue: String = product
  }

  object UserAgent extends HeaderType[UserAgent] {
    val name: String                                    = "user-agent"
    def parse(value: String): Either[String, UserAgent] = Right(UserAgent(value))
    def render(h: UserAgent): String                    = h.renderedValue
  }

  final case class CacheControl(directives: String) extends Header {
    def headerName: String    = CacheControl.name
    def renderedValue: String = directives
  }

  object CacheControl extends HeaderType[CacheControl] {
    val name: String                                       = "cache-control"
    def parse(value: String): Either[String, CacheControl] = Right(CacheControl(value))
    def render(h: CacheControl): String                    = h.renderedValue
  }

  final case class ContentLength(length: Long) extends Header {
    def headerName: String    = ContentLength.name
    def renderedValue: String = length.toString
  }

  object ContentLength extends HeaderType[ContentLength] {
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

  final case class Location(uri: String) extends Header {
    def headerName: String    = Location.name
    def renderedValue: String = uri
  }

  object Location extends HeaderType[Location] {
    val name: String                                   = "location"
    def parse(value: String): Either[String, Location] = Right(Location(value))
    def render(h: Location): String                    = h.renderedValue
  }

  final case class SetCookie(value: String) extends Header {
    def headerName: String    = SetCookie.name
    def renderedValue: String = value
  }

  object SetCookie extends HeaderType[SetCookie] {
    val name: String                                    = "set-cookie"
    def parse(value: String): Either[String, SetCookie] = Right(SetCookie(value))
    def render(h: SetCookie): String                    = h.renderedValue
  }

  final case class Cookie(value: String) extends Header {
    def headerName: String    = Cookie.name
    def renderedValue: String = value
  }

  object Cookie extends HeaderType[Cookie] {
    val name: String                                 = "cookie"
    def parse(value: String): Either[String, Cookie] = Right(Cookie(value))
    def render(h: Cookie): String                    = h.renderedValue
  }

  final case class ContentEncoding(encoding: String) extends Header {
    def headerName: String    = ContentEncoding.name
    def renderedValue: String = encoding
  }

  object ContentEncoding extends HeaderType[ContentEncoding] {
    val name: String                                          = "content-encoding"
    def parse(value: String): Either[String, ContentEncoding] = Right(ContentEncoding(value))
    def render(h: ContentEncoding): String                    = h.renderedValue
  }

  final case class TransferEncoding(encoding: String) extends Header {
    def headerName: String    = TransferEncoding.name
    def renderedValue: String = encoding
  }

  object TransferEncoding extends HeaderType[TransferEncoding] {
    val name: String                                           = "transfer-encoding"
    def parse(value: String): Either[String, TransferEncoding] = Right(TransferEncoding(value))
    def render(h: TransferEncoding): String                    = h.renderedValue
  }

  final case class Connection(value: String) extends Header {
    def headerName: String    = Connection.name
    def renderedValue: String = value
  }

  object Connection extends HeaderType[Connection] {
    val name: String                                     = "connection"
    def parse(value: String): Either[String, Connection] = Right(Connection(value))
    def render(h: Connection): String                    = h.renderedValue
  }

  final case class Origin(origin: String) extends Header {
    def headerName: String    = Origin.name
    def renderedValue: String = origin
  }

  object Origin extends HeaderType[Origin] {
    val name: String                                 = "origin"
    def parse(value: String): Either[String, Origin] = Right(Origin(value))
    def render(h: Origin): String                    = h.renderedValue
  }

  final case class Referer(uri: String) extends Header {
    def headerName: String    = Referer.name
    def renderedValue: String = uri
  }

  object Referer extends HeaderType[Referer] {
    val name: String                                  = "referer"
    def parse(value: String): Either[String, Referer] = Right(Referer(value))
    def render(h: Referer): String                    = h.renderedValue
  }

  final case class AcceptEncoding(value: String) extends Header {
    def headerName: String    = AcceptEncoding.name
    def renderedValue: String = value
  }

  object AcceptEncoding extends HeaderType[AcceptEncoding] {
    val name: String                                         = "accept-encoding"
    def parse(value: String): Either[String, AcceptEncoding] = Right(AcceptEncoding(value))
    def render(h: AcceptEncoding): String                    = h.renderedValue
  }

  final case class Custom(override val headerName: String, rawValue: String) extends Header {
    def renderedValue: String = rawValue
  }

  object Custom extends HeaderType[Custom] {
    val name: String                                 = "x-custom"
    def parse(value: String): Either[String, Custom] = Right(Custom(name, value))
    def render(h: Custom): String                    = h.renderedValue
  }
}
