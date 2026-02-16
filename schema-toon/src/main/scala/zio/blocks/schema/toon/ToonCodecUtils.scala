package zio.blocks.schema.toon

private[toon] object ToonCodecUtils {

  def createReaderForValue(value: String): ToonReader = {
    val reader = ToonReader(ReaderConfig.withDelimiter(Delimiter.None))
    reader.reset(value)
    reader
  }

  def unescapeQuoted(s: String): String = {
    if (!s.startsWith("\"") || !s.endsWith("\"")) return s
    val inner = s.substring(1, s.length - 1)
    if (inner.indexOf('\\') < 0) return inner
    val sb = new java.lang.StringBuilder(inner.length)
    var i  = 0
    while (i < inner.length) {
      val c = inner.charAt(i)
      if (c == '\\' && i + 1 < inner.length) {
        inner.charAt(i + 1) match {
          case '"'   => sb.append('"'); i += 2
          case '\\'  => sb.append('\\'); i += 2
          case 'n'   => sb.append('\n'); i += 2
          case 'r'   => sb.append('\r'); i += 2
          case 't'   => sb.append('\t'); i += 2
          case other => sb.append('\\'); sb.append(other); i += 2
        }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString
  }
}
