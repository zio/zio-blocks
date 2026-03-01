package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. SecWebSocketAccept ---

final case class SecWebSocketAccept(value: String) extends Header {
  def headerName: String    = SecWebSocketAccept.name
  def renderedValue: String = value
}

object SecWebSocketAccept extends Header.Typed[SecWebSocketAccept] {
  val name: String                                             = "sec-websocket-accept"
  def parse(value: String): Either[String, SecWebSocketAccept] = Right(SecWebSocketAccept(value.trim))
  def render(h: SecWebSocketAccept): String                    = h.renderedValue
}

// --- 2. SecWebSocketExtensions ---

final case class SecWebSocketExtensions(value: String) extends Header {
  def headerName: String    = SecWebSocketExtensions.name
  def renderedValue: String = value
}

object SecWebSocketExtensions extends Header.Typed[SecWebSocketExtensions] {
  val name: String                                                 = "sec-websocket-extensions"
  def parse(value: String): Either[String, SecWebSocketExtensions] = Right(SecWebSocketExtensions(value.trim))
  def render(h: SecWebSocketExtensions): String                    = h.renderedValue
}

// --- 3. SecWebSocketKey ---

final case class SecWebSocketKey(value: String) extends Header {
  def headerName: String    = SecWebSocketKey.name
  def renderedValue: String = value
}

object SecWebSocketKey extends Header.Typed[SecWebSocketKey] {
  val name: String                                          = "sec-websocket-key"
  def parse(value: String): Either[String, SecWebSocketKey] = Right(SecWebSocketKey(value.trim))
  def render(h: SecWebSocketKey): String                    = h.renderedValue
}

// --- 4. SecWebSocketLocation ---

final case class SecWebSocketLocation(value: String) extends Header {
  def headerName: String    = SecWebSocketLocation.name
  def renderedValue: String = value
}

object SecWebSocketLocation extends Header.Typed[SecWebSocketLocation] {
  val name: String                                               = "sec-websocket-location"
  def parse(value: String): Either[String, SecWebSocketLocation] = Right(SecWebSocketLocation(value.trim))
  def render(h: SecWebSocketLocation): String                    = h.renderedValue
}

// --- 5. SecWebSocketOrigin ---

final case class SecWebSocketOrigin(value: String) extends Header {
  def headerName: String    = SecWebSocketOrigin.name
  def renderedValue: String = value
}

object SecWebSocketOrigin extends Header.Typed[SecWebSocketOrigin] {
  val name: String                                             = "sec-websocket-origin"
  def parse(value: String): Either[String, SecWebSocketOrigin] = Right(SecWebSocketOrigin(value.trim))
  def render(h: SecWebSocketOrigin): String                    = h.renderedValue
}

// --- 6. SecWebSocketProtocol ---

final case class SecWebSocketProtocol(protocols: Chunk[String]) extends Header {
  def headerName: String    = SecWebSocketProtocol.name
  def renderedValue: String = protocols.mkString(", ")
}

object SecWebSocketProtocol extends Header.Typed[SecWebSocketProtocol] {
  val name: String = "sec-websocket-protocol"

  def parse(value: String): Either[String, SecWebSocketProtocol] = {
    val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
    if (parts.isEmpty) Left("Empty sec-websocket-protocol header")
    else Right(SecWebSocketProtocol(Chunk.fromArray(parts)))
  }

  def render(h: SecWebSocketProtocol): String = h.renderedValue
}

// --- 7. SecWebSocketVersion ---

final case class SecWebSocketVersion(version: String) extends Header {
  def headerName: String    = SecWebSocketVersion.name
  def renderedValue: String = version
}

object SecWebSocketVersion extends Header.Typed[SecWebSocketVersion] {
  val name: String                                              = "sec-websocket-version"
  def parse(value: String): Either[String, SecWebSocketVersion] = Right(SecWebSocketVersion(value.trim))
  def render(h: SecWebSocketVersion): String                    = h.renderedValue
}
