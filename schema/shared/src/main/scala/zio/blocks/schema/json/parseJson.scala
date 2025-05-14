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
  private final def error[A](message: String): Either[Error, A] = Left(new Error(message))

  def apply(input: String): Either[Error, Json] = {
    val p = new Parser(input)
    p.parseValue() match {
      case Right(json) =>
        p.skipWhitespace()
        if (p.hasNext) error(s"Unexpected character at position ${p.pos}: '${p.peek}'")
        else Right(json)
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
      Right(sb.toString())
    }

    private def parseNumber(): Either[Error, JsonNumber] = {
      val start = pos
      if (peek == '-') advance()
      while (hasNext && peek.isDigit) advance()
      if (hasNext && peek == '.') {
        advance()
        while (hasNext && peek.isDigit) advance()
      }
      val str = input.substring(start, pos)
      str.toDoubleOption match {
        case Some(n) => Right(JsonNumber(n))
        case None    => error(s"Invalid number: $str")
      }
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
          return Right(JsonArray(items.toList))
        } else {
          return error(s"Expected ',' or ']' at position $pos")
        }
      }
      error("Unreachable")
    }

    private def parseObject(): Either[Error, JsonObject] = {
      if (peek != '{') return error(s"Expected '{' at position $pos")
      advance()
      skipWhitespace()
      if (peek == '}') {
        advance()
        return Right(JsonObject(Map.empty))
      }
      val fields = scala.collection.mutable.Map.empty[String, Json]
      while (true) {
        parseString() match {
          case Right(key) =>
            skipWhitespace()
            if (peek != ':') return error(s"Expected ':' after key at position $pos")
            advance()
            skipWhitespace()
            parseValue() match {
              case Right(value) => fields += (key -> value)
              case Left(err)    => return Left(err)
            }
          case Left(err) => return Left(err)
        }
        skipWhitespace()
        if (peek == ',') {
          advance()
          skipWhitespace()
        } else if (peek == '}') {
          advance()
          return Right(JsonObject(fields.toMap))
        } else {
          return error(s"Expected ',' or '}' at position $pos")
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
