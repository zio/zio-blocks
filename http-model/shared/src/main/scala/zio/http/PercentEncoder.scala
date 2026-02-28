package zio.http

object PercentEncoder {

  sealed trait ComponentType
  object ComponentType {
    case object PathSegment extends ComponentType
    case object QueryKey    extends ComponentType
    case object QueryValue  extends ComponentType
    case object Fragment    extends ComponentType
    case object UserInfo    extends ComponentType
  }

  private def isUnreserved(b: Int): Boolean =
    (b >= 'A' && b <= 'Z') ||
      (b >= 'a' && b <= 'z') ||
      (b >= '0' && b <= '9') ||
      b == '-' || b == '_' || b == '.' || b == '~'

  private def isComponentAllowed(b: Int, componentType: ComponentType): Boolean = {
    import ComponentType._
    componentType match {
      case PathSegment =>
        b == ':' || b == '@' || b == '!' || b == '$' || b == '&' ||
        b == '\'' || b == '(' || b == ')' || b == '*' || b == '+' ||
        b == ',' || b == ';' || b == '='
      case QueryKey =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '\'' || b == '(' || b == ')' || b == '*' ||
        b == '+' || b == ',' || b == ';'
      case QueryValue =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '\'' || b == '(' || b == ')' || b == '*' ||
        b == '+' || b == ',' || b == ';' || b == '='
      case Fragment =>
        b == '/' || b == '?' || b == ':' || b == '@' || b == '!' ||
        b == '$' || b == '&' || b == '\'' || b == '(' || b == ')' ||
        b == '*' || b == '+' || b == ',' || b == ';' || b == '='
      case UserInfo =>
        b == ':'
    }
  }

  private val hexChars: Array[Char] = "0123456789ABCDEF".toCharArray

  def encode(s: String, componentType: ComponentType): String = {
    if (s.isEmpty) return ""

    val bytes = s.getBytes("UTF-8")
    val sb    = new java.lang.StringBuilder(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      val b = bytes(i) & 0xff
      if (isUnreserved(b) || isComponentAllowed(b, componentType)) {
        sb.append(b.toChar)
      } else {
        sb.append('%')
        sb.append(hexChars(b >> 4))
        sb.append(hexChars(b & 0x0f))
      }
      i += 1
    }
    sb.toString
  }

  private def hexValue(c: Char): Int =
    if (c >= '0' && c <= '9') c - '0'
    else if (c >= 'A' && c <= 'F') c - 'A' + 10
    else if (c >= 'a' && c <= 'f') c - 'a' + 10
    else -1

  def decode(s: String): String = {
    if (s.isEmpty) return ""

    val len = s.length
    val sb  = new java.lang.StringBuilder(len)
    val buf = new java.io.ByteArrayOutputStream(16)
    var i   = 0

    while (i < len) {
      val c = s.charAt(i)
      if (c == '%' && i + 2 < len) {
        val hi = hexValue(s.charAt(i + 1))
        val lo = hexValue(s.charAt(i + 2))
        if (hi >= 0 && lo >= 0) {
          buf.write((hi << 4) | lo)
          i += 3
        } else {
          if (buf.size() > 0) {
            sb.append(new String(buf.toByteArray, "UTF-8"))
            buf.reset()
          }
          sb.append(c)
          i += 1
        }
      } else {
        if (buf.size() > 0) {
          sb.append(new String(buf.toByteArray, "UTF-8"))
          buf.reset()
        }
        sb.append(c)
        i += 1
      }
    }

    if (buf.size() > 0) {
      sb.append(new String(buf.toByteArray, "UTF-8"))
    }

    sb.toString
  }
}
