package zio.http

sealed abstract class Version(val major: Int, val minor: Int) {
  val text: String              = s"HTTP/$major.$minor"
  override def toString: String = text
}

object Version {
  case object `Http/1.0` extends Version(1, 0)
  case object `Http/1.1` extends Version(1, 1)
  case object `Http/2.0` extends Version(2, 0)
  case object `Http/3.0` extends Version(3, 0)

  val values: Array[Version] = Array(`Http/1.0`, `Http/1.1`, `Http/2.0`, `Http/3.0`)

  def fromString(s: String): Option[Version] = s match {
    case "HTTP/1.0"            => Some(`Http/1.0`)
    case "HTTP/1.1"            => Some(`Http/1.1`)
    case "HTTP/2.0" | "HTTP/2" => Some(`Http/2.0`)
    case "HTTP/3.0" | "HTTP/3" => Some(`Http/3.0`)
    case _                     => None
  }

  def render(version: Version): String = version.text
}
