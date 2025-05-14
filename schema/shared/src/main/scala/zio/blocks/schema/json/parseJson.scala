package zio.blocks.schema.json

sealed trait Json
case object JsonNull                                   extends Json
final case class JsonBool(value: Boolean)              extends Json
final case class JsonNumber(value: Double)             extends Json
final case class JsonString(value: String)             extends Json
final case class JsonArray(items: List[Json])          extends Json
final case class JsonObject(fields: Map[String, Json]) extends Json

private[json] final class Error(message: String) extends Exception(message)

object parseJson extends (String => Either[Error, Json]) {
  private final def rawError(message: String): Error            = new Error(message)
  private final def error[A](message: String): Either[Error, A] = Left(new Error(message))
  private final def ok[A](value: A): Either[Error, A]           = Right(value)

  def apply(input: String): Either[Error, Json] = {
    val p = new Parser(input)
    p.parseValue() match {
      case Right(json) =>
        p.skipWhitespace()
        if (p.hasNext) error(s"Unexpected character at position ${p.pos}: '${p.peek}'")
        else ok(json)
      case e => e
    }
  }

  private final class Parser(private val input: String) {
    private val len             = input.length
    var pos: Int                = 0
    def hasNext: Boolean        = pos < len
    def peek: Char              = input.charAt(pos)
    private def advance(): Unit = pos += 1
    def skipWhitespace(): Unit  = while (hasNext && peek.isWhitespace) advance()

    def parseValue(): Either[Error, Json] = {
      skipWhitespace()
      if (!hasNext) return error("Unexpected end of input")
      peek match {
        case '"'                        => parseString().map(JsonString)
        case 't'                        => expect("true").map(_ => JsonBool(true))
        case 'f'                        => expect("false").map(_ => JsonBool(false))
        case 'n'                        => expect("null").map(_ => JsonNull)
        case '['                        => parseArray()
        case '{'                        => parseObject()
        case c if c == '-' || c.isDigit => parseNumber()
        case c                          => error(s"Unexpected character at $pos: '$c'")
      }
    }

    private def parseString(): Either[Error, String] = {
      if (peek != '"') return error(s"Expected '\"' at position $pos")
      advance()
      val sb = new StringBuilder
      while (hasNext && peek != '"') {
        if (peek == '\\') {
          advance()
          if (!hasNext) return error("Unexpected end in string escape")
          peek match {
            case '"'   => sb.append('"')
            case '\\'  => sb.append('\\')
            case '/'   => sb.append('/')
            case 'b'   => sb.append('\b')
            case 'f'   => sb.append('\f')
            case 'n'   => sb.append('\n')
            case 'r'   => sb.append('\r')
            case 't'   => sb.append('\t')
            case other => return error(s"Invalid escape character: $other")
          }
        } else sb.append(peek)
        advance()
      }
      if (!hasNext || peek != '"') return error(s"Expected closing quote at position $pos")
      advance()
      ok(sb.toString())
    }

    private def parseNumber(): Either[Error, JsonNumber] = {
      val start = pos
      if (peek == '-') advance()
      var hasDigits = false
      while (hasNext && peek.isDigit) {
        hasDigits = true
        advance()
      }

      if (!hasDigits) return error(s"Invalid number format at position $start")

      if (hasNext && peek == '.') {
        advance()
        hasDigits = false
        while (hasNext && peek.isDigit) {
          hasDigits = true
          advance()
        }
        if (!hasDigits) return error(s"Expected digits after decimal point at position ${pos - 1}")
      }

      // Handle scientific notation
      if (hasNext && (peek == 'e' || peek == 'E')) {
        advance()
        if (hasNext && (peek == '+' || peek == '-')) advance()
        hasDigits = false
        while (hasNext && peek.isDigit) {
          hasDigits = true
          advance()
        }
        if (!hasDigits) return error(s"Expected digits in exponent at position ${pos - 1}")
      }

      val str = input.substring(start, pos)
      str.toDoubleOption
        .map(JsonNumber(_))
        .toRight(rawError(s"Invalid number: $str"))
    }

    private def parseArray(): Either[Error, JsonArray] = {
      if (peek != '[') return error(s"Expected '[' at position $pos")
      advance()
      skipWhitespace()
      if (peek == ']') {
        advance()
        return Right(JsonArray(Nil))
      }
      val items = scala.collection.mutable.ListBuffer.empty[Json]
      while (true) {
        parseValue() match {
          case Right(value) => items += value
          case Left(err)    => return Left(err)
        }
        skipWhitespace()
        if (peek == ',') {
          advance()
          skipWhitespace()
        } else if (peek == ']') {
          advance()
          return ok(JsonArray(items.toList))
        } else return error(s"Expected ',' or ']' at position $pos")
      }
      error("Unreachable")
    }

    private def parseObject(): Either[Error, JsonObject] = {
      if (peek != '{') return error(s"Expected '{' at position $pos")
      advance()
      skipWhitespace()
      if (peek == '}') {
        advance()
        return ok(JsonObject(Map.empty))
      }
      val fields = scala.collection.mutable.Map.empty[String, Json]
      var first  = true
      while (true) {
        if (!first) {
          if (peek != ',') return error(s"Expected ',' at position $pos")
          advance()
          skipWhitespace()
        } else first = false

        // Parse the key (must be a string)
        if (peek != '"') return error(s"Expected string key at position $pos")
        parseString() match {
          case Right(key) =>
            skipWhitespace()
            if (peek != ':') return error(s"Expected ':' after key at position $pos")
            advance()
            skipWhitespace()
            parseValue() match {
              case Right(value) =>
                // Check for duplicate keys and throw an error if found
                if (fields.contains(key))
                  return error(s"Duplicate key '$key' at position $pos")
                fields += (key -> value)
              case Left(err) => return Left(err)
            }
          case Left(err) => return Left(err)
        }

        skipWhitespace()
        if (peek == '}') {
          advance()
          return ok(JsonObject(fields.toMap))
        }
      }
      error("Unreachable")
    }

    private def expect(str: String): Either[Error, Unit] =
      if (input.startsWith(str, pos)) {
        pos += str.length
        Right(())
      } else error(s"Expected '$str' at position $pos")
  }
}
