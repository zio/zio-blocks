package zio.blocks.template

object Escape {

  def html(s: String): String = {
    val len = s.length
    if (len == 0) return s

    var needsEscape = false
    var i           = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
        needsEscape = true
        i = len
      }
      i += 1
    }

    if (!needsEscape) return s

    val sb = new java.lang.StringBuilder(len + 16)
    i = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '&') sb.append("&amp;")
      else if (c == '<') sb.append("&lt;")
      else if (c == '>') sb.append("&gt;")
      else if (c == '"') sb.append("&quot;")
      else if (c == '\'') sb.append("&#x27;")
      else sb.append(c)
      i += 1
    }
    sb.toString
  }

  def jsString(s: String): String = {
    val len = s.length
    if (len == 0) return s

    val sb = new java.lang.StringBuilder(len + 16)
    var i  = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '"') sb.append("\\\"")
      else if (c == '\'') sb.append("\\'")
      else if (c == '\\') sb.append("\\\\")
      else if (c == '\n') sb.append("\\n")
      else if (c == '\r') sb.append("\\r")
      else if (c == '\t') sb.append("\\t")
      else if (c == '<') sb.append("\\u003c")
      else if (c == '>') sb.append("\\u003e")
      else if (c == '&') sb.append("\\u0026")
      else if (c < 32) {
        sb.append("\\u")
        val hex = Integer.toHexString(c.toInt)
        var pad = 4 - hex.length
        while (pad > 0) {
          sb.append('0')
          pad -= 1
        }
        sb.append(hex)
      } else sb.append(c)
      i += 1
    }
    sb.toString
  }

  def cssString(s: String): String = {
    val len = s.length
    if (len == 0) return s

    val sb = new java.lang.StringBuilder(len + 8)
    var i  = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c == '\\') sb.append("\\\\")
      else if (c == '"') sb.append("\\\"")
      else if (c == '\'') sb.append("\\'")
      else if (c == '<') sb.append("\\3c ")
      else if (c == '>') sb.append("\\3e ")
      else if (c == '&') sb.append("\\26 ")
      else sb.append(c)
      i += 1
    }
    sb.toString
  }
}
