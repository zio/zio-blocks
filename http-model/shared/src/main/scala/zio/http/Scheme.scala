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
