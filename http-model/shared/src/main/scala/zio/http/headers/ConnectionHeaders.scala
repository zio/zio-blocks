package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. Connection ---

sealed trait Connection extends Header {
  def headerName: String    = Connection.name
  def renderedValue: String = Connection.render(this)
}

object Connection extends Header.Typed[Connection] {
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

// --- 2. Upgrade ---

final case class Upgrade(protocol: String) extends Header {
  def headerName: String    = Upgrade.name
  def renderedValue: String = protocol
}

object Upgrade extends Header.Typed[Upgrade] {
  val name: String                                  = "upgrade"
  def parse(value: String): Either[String, Upgrade] = Right(Upgrade(value.trim))
  def render(h: Upgrade): String                    = h.renderedValue
}

// --- 3. Te ---

final case class Te(value: String) extends Header {
  def headerName: String    = Te.name
  def renderedValue: String = value
}

object Te extends Header.Typed[Te] {
  val name: String                             = "te"
  def parse(value: String): Either[String, Te] = Right(Te(value.trim))
  def render(h: Te): String                    = h.renderedValue
}

// --- 4. Trailer ---

final case class Trailer(headers: Chunk[String]) extends Header {
  def headerName: String    = Trailer.name
  def renderedValue: String = headers.mkString(", ")
}

object Trailer extends Header.Typed[Trailer] {
  val name: String = "trailer"

  def parse(value: String): Either[String, Trailer] = {
    val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
    if (parts.isEmpty) Left("Empty trailer header")
    else Right(Trailer(Chunk.fromArray(parts)))
  }

  def render(h: Trailer): String = h.renderedValue
}

// --- 5. TransferEncoding ---

sealed trait TransferEncoding extends Header {
  def headerName: String    = TransferEncoding.name
  def renderedValue: String = TransferEncoding.render(this)
}

object TransferEncoding extends Header.Typed[TransferEncoding] {
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
