package zio.blocks.schema.json

/**
 * Shared runtime utilities for JSON string interpolation. Used by both Scala 2
 * and Scala 3 macro implementations.
 */
object JsonInterpolatorRuntime {

  def jsonWithInterpolation(sc: StringContext, args: Seq[Any]): Json = {
    val parts  = sc.parts.iterator
    val argsIt = args.iterator
    val sb     = new StringBuilder(parts.next())

    while (argsIt.hasNext) {
      val arg = argsIt.next()
      sb.append(encodeValue(arg))
      sb.append(parts.next())
    }

    Json.parseUnsafe(sb.toString())
  }

  private[json] def encodeValue(value: Any): String = value match {
    case s: String      => s""""${escapeString(s)}""""
    case n: Int         => n.toString
    case n: Long        => n.toString
    case n: Double      => n.toString
    case n: Float       => n.toString
    case n: BigDecimal  => n.toString
    case n: BigInt      => n.toString
    case b: Boolean     => b.toString
    case null           => "null"
    case j: Json        => j.encode
    case opt: Option[_] => opt.fold("null")(encodeValue)
    case seq: Seq[_]    => seq.map(encodeValue).mkString("[", ",", "]")
    case arr: Array[_]  => arr.map(encodeValue).mkString("[", ",", "]")
    case other          => s""""${escapeString(other.toString)}""""
  }

  private[json] def escapeString(s: String): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'           => sb.append("\\\"")
        case '\\'          => sb.append("\\\\")
        case '\b'          => sb.append("\\b")
        case '\f'          => sb.append("\\f")
        case '\n'          => sb.append("\\n")
        case '\r'          => sb.append("\\r")
        case '\t'          => sb.append("\\t")
        case ch if ch < 32 => sb.append(f"\\u${ch.toInt}%04x")
        case ch            => sb.append(ch)
      }
      i += 1
    }
    sb.toString()
  }

  /**
   * Validates JSON syntax without parsing. Used by macros for compile-time
   * validation. Returns None if valid, Some(errorMessage) if invalid. This is a
   * pure-Scala implementation that works across JVM, JS, and Native.
   */
  def validateJsonSyntax(s: String): Option[String] =
    new JsonValidator(s).validate()

  private class JsonValidator(s: String) {
    private var pos                    = 0
    private def current: Char          = if (pos < s.length) s.charAt(pos) else '\u0000'
    private def advance(): Unit        = pos += 1
    private def skipWhitespace(): Unit = while (current.isWhitespace) advance()

    def validate(): Option[String] = {
      val result = parseValue()
      if (result.isDefined) result
      else {
        skipWhitespace()
        if (pos < s.length) Some(s"Unexpected character at position $pos")
        else None
      }
    }

    private def parseValue(): Option[String] = {
      skipWhitespace()
      current match {
        case '"'                        => parseString()
        case '{'                        => parseObject()
        case '['                        => parseArray()
        case 't'                        => parseLiteral("true")
        case 'f'                        => parseLiteral("false")
        case 'n'                        => parseLiteral("null")
        case c if c == '-' || c.isDigit => parseNumber()
        case c                          => Some(s"Unexpected character '$c' at position $pos")
      }
    }

    private def parseString(): Option[String] =
      if (current != '"') Some(s"Expected '\"' at position $pos")
      else {
        advance()
        while (current != '"' && current != '\u0000') {
          if (current == '\\') {
            advance()
            if (current == '\u0000') return Some(s"Unterminated escape sequence at position $pos")
          }
          advance()
        }
        if (current != '"') Some(s"Unterminated string at position $pos")
        else { advance(); None }
      }

    private def parseObject(): Option[String] =
      if (current != '{') Some(s"Expected '{{' at position $pos")
      else {
        advance()
        skipWhitespace()
        if (current == '}') { advance(); None }
        else parseObjectFields(first = true)
      }

    private def parseObjectFields(first: Boolean): Option[String] = {
      if (!first) {
        skipWhitespace()
        if (current != ',') return Some(s"Expected ',' at position $pos")
        advance()
      }
      skipWhitespace()
      parseString() match {
        case Some(err) => Some(err)
        case None      =>
          skipWhitespace()
          if (current != ':') Some(s"Expected ':' at position $pos")
          else {
            advance()
            parseValue() match {
              case Some(err) => Some(err)
              case None      =>
                skipWhitespace()
                if (current == '}') { advance(); None }
                else parseObjectFields(first = false)
            }
          }
      }
    }

    private def parseArray(): Option[String] =
      if (current != '[') Some(s"Expected '[' at position $pos")
      else {
        advance()
        skipWhitespace()
        if (current == ']') { advance(); None }
        else parseArrayElements(first = true)
      }

    private def parseArrayElements(first: Boolean): Option[String] = {
      if (!first) {
        skipWhitespace()
        if (current != ',') return Some(s"Expected ',' at position $pos")
        advance()
      }
      parseValue() match {
        case Some(err) => Some(err)
        case None      =>
          skipWhitespace()
          if (current == ']') { advance(); None }
          else parseArrayElements(first = false)
      }
    }

    private def parseLiteral(expected: String): Option[String] = {
      var i = 0
      while (i < expected.length) {
        if (current != expected.charAt(i)) return Some(s"Expected '$expected' at position $pos")
        advance()
        i += 1
      }
      None
    }

    private def parseNumber(): Option[String] = {
      if (current == '-') advance()
      if (!current.isDigit) return Some(s"Expected digit at position $pos")
      while (current.isDigit) advance()
      if (current == '.') {
        advance()
        if (!current.isDigit) return Some(s"Expected digit after decimal point at position $pos")
        while (current.isDigit) advance()
      }
      if (current == 'e' || current == 'E') {
        advance()
        if (current == '+' || current == '-') advance()
        if (!current.isDigit) return Some(s"Expected digit in exponent at position $pos")
        while (current.isDigit) advance()
      }
      None
    }
  }
}
