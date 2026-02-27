package zio.blocks.http

sealed abstract class Charset(val name: String) extends CharsetPlatformSpecific {
  override def toString: String = name
}

object Charset {
  case object UTF8       extends Charset("UTF-8")
  case object ASCII      extends Charset("US-ASCII")
  case object ISO_8859_1 extends Charset("ISO-8859-1")
  case object UTF16      extends Charset("UTF-16")
  case object UTF16BE    extends Charset("UTF-16BE")
  case object UTF16LE    extends Charset("UTF-16LE")

  val values: Array[Charset] = Array(UTF8, ASCII, ISO_8859_1, UTF16, UTF16BE, UTF16LE)

  def fromString(s: String): Option[Charset] = s.toUpperCase match {
    case "UTF-8" | "UTF8"                    => Some(UTF8)
    case "US-ASCII" | "ASCII"                => Some(ASCII)
    case "ISO-8859-1" | "LATIN1" | "LATIN-1" => Some(ISO_8859_1)
    case "UTF-16"                            => Some(UTF16)
    case "UTF-16BE"                          => Some(UTF16BE)
    case "UTF-16LE"                          => Some(UTF16LE)
    case _                                   => None
  }

  def render(charset: Charset): String = charset.name
}
