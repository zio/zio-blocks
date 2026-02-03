package zio.blocks.schema

import zio.blocks.chunk.ChunkBuilder

/**
 * Pure Scala parser for the path interpolator syntax.
 *
 * This parser converts a path string (e.g., ".users[*].email") into a Vector of
 * DynamicOptic.Node elements. All parsing is done at compile time.
 */
private[schema] object PathParser {
  import DynamicOptic.Node

  sealed trait ParseError {
    def message: String
    def position: Int
  }

  object ParseError {
    case class UnexpectedChar(char: Char, position: Int, expected: String) extends ParseError {
      def message: String = s"Unexpected character '$char' at position $position. Expected $expected"
    }

    case class InvalidEscape(char: Char, position: Int) extends ParseError {
      def message: String = s"Invalid escape sequence '\\$char' at position $position"
    }

    case class UnterminatedString(position: Int) extends ParseError {
      def message: String = s"Unterminated string literal starting at position $position"
    }

    case class UnterminatedChar(position: Int) extends ParseError {
      def message: String = s"Unterminated char literal starting at position $position"
    }

    case class EmptyChar(position: Int) extends ParseError {
      def message: String = s"Empty char literal at position $position"
    }

    case class MultiCharLiteral(position: Int) extends ParseError {
      def message: String = s"Char literal contains multiple characters at position $position"
    }

    case class InvalidIdentifier(position: Int) extends ParseError {
      def message: String = s"Invalid identifier at position $position"
    }

    case class IntegerOverflow(position: Int) extends ParseError {
      def message: String = s"Integer overflow at position $position (value exceeds Int.MaxValue)"
    }

    case class UnexpectedEnd(expected: String) extends ParseError {
      def position: Int   = -1
      def message: String = s"Unexpected end of input. Expected $expected"
    }

    case class InvalidSyntax(msg: String, position: Int) extends ParseError {
      def message: String = s"$msg at position $position"
    }
  }

  /**
   * Parse a path string into a Vector of Nodes. Returns Left with error if
   * parsing fails, Right with nodes on success.
   */
  def parse(input: String): Either[ParseError, Vector[Node]] = parseNodes(new ParseContext(input))

  private class ParseContext(val input: String) {
    var pos: Int = 0

    def current: Char =
      if (pos < input.length) input.charAt(pos)
      else '\u0000'

    def atEnd: Boolean = pos >= input.length

    def advance(): Unit = pos += 1

    def peek(offset: Int = 1): Char = {
      val p = pos + offset
      if (p < input.length) input.charAt(p) else '\u0000'
    }

    def skipWhitespace(): Unit = while (!atEnd && current.isWhitespace) advance()
  }

  private def parseNodes(ctx: ParseContext): Either[ParseError, Vector[Node]] = {
    val nodes = Vector.newBuilder[Node]
    while (!ctx.atEnd) {
      parseNode(ctx) match {
        case Right(node) => nodes.addOne(node)
        case l           => return l.asInstanceOf[Either[ParseError, Vector[Node]]]
      }
    }
    new Right(nodes.result())
  }

  private def parseNode(ctx: ParseContext): Either[ParseError, Node] = ctx.current match {
    case '.' =>
      ctx.advance()
      if (ctx.atEnd || !isIdentifierStart(ctx.current)) new Left(new ParseError.InvalidIdentifier(ctx.pos))
      else parseField(ctx)
    case '['                       => parseIndexOrElements(ctx)
    case '{'                       => parseMapAccess(ctx)
    case '<'                       => parseVariantCase(ctx)
    case c if isIdentifierStart(c) => parseField(ctx)
    case c                         => new Left(new ParseError.UnexpectedChar(c, ctx.pos, "field, index, map, or variant accessor"))
  }

  private def parseField(ctx: ParseContext): Either[ParseError, Node] = {
    val start = ctx.pos
    val sb    = new java.lang.StringBuilder
    while (!ctx.atEnd && isIdentifierPart(ctx.current)) {
      sb.append(ctx.current)
      ctx.advance()
    }
    if (sb.length() == 0) new Left(new ParseError.InvalidIdentifier(start))
    else new Right(new Node.Field(sb.toString))
  }

  private def parseIndexOrElements(ctx: ParseContext): Either[ParseError, Node] = {
    ctx.advance() // Skip '['
    ctx.skipWhitespace()
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("']' or index"))
    ctx.current match {
      case '*' =>
        ctx.advance()
        ctx.skipWhitespace()
        if (ctx.current == ':') {
          ctx.advance()
          ctx.skipWhitespace()
          if (ctx.current == ']') {
            ctx.advance()
            new Right(Node.Elements) // [:*] is Elements
          } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "']'"))
        } else if (ctx.current == ']') {
          ctx.advance()
          new Right(Node.Elements) // [*] is Elements
        } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "']' or ':'"))
      case ':' =>
        ctx.advance()
        ctx.skipWhitespace()
        if (ctx.current == '*') {
          ctx.advance()
          ctx.skipWhitespace()
          if (ctx.current == ']') {
            ctx.advance()
            new Right(Node.Elements) // [:*] is Elements
          } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "']'"))
        } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'*'"))
      case c if c.isDigit =>
        parseInteger(ctx) match {
          case Left(err)    => Left(err)
          case Right(first) =>
            ctx.skipWhitespace()
            ctx.current match {
              case ']' =>
                ctx.advance()
                new Right(new Node.AtIndex(first))
              case ',' =>
                // Multiple indices
                val indices = Vector.newBuilder[Int]
                indices.addOne(first)
                while (ctx.current == ',') {
                  ctx.advance()
                  ctx.skipWhitespace()
                  parseInteger(ctx) match {
                    case Left(err)  => return new Left(err)
                    case Right(idx) => indices.addOne(idx)
                  }
                  ctx.skipWhitespace()
                }
                if (ctx.current == ']') {
                  ctx.advance()
                  new Right(new Node.AtIndices(indices.result()))
                } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "']' or ','"))
              case ':' =>
                // Range
                ctx.advance()
                ctx.skipWhitespace()
                parseInteger(ctx) match {
                  case Right(end) =>
                    ctx.skipWhitespace()
                    if (ctx.current == ']') {
                      ctx.advance()
                      val range =
                        if (first >= end) Seq.empty[Int]
                        else first.until(end)
                      new Right(new Node.AtIndices(range))
                    } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "']'"))
                  case l => l.asInstanceOf[Either[ParseError, Node]]
                }
              case c => Left(new ParseError.UnexpectedChar(c, ctx.pos, "']', ',', or ':'"))
            }
        }
      case c => Left(new ParseError.UnexpectedChar(c, ctx.pos, "digit, '*', or ':'"))
    }
  }

  private def parseMapAccess(ctx: ParseContext): Either[ParseError, Node] = {
    ctx.advance() // Skip '{'
    ctx.skipWhitespace()
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("'}' or map key"))
    ctx.current match {
      case '*' =>
        ctx.advance()
        ctx.skipWhitespace()
        if (ctx.current == ':') {
          ctx.advance()
          ctx.skipWhitespace()
          if (ctx.current == '}') {
            ctx.advance()
            new Right(Node.MapKeys) // {*:} is MapKeys
          } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'}'"))
        } else if (ctx.current == '}') {
          ctx.advance()
          new Right(Node.MapValues) // {*} is MapValues
        } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'}' or ':'"))
      case ':' =>
        ctx.advance()
        ctx.skipWhitespace()
        if (ctx.current == '*') {
          ctx.advance()
          ctx.skipWhitespace()
          if (ctx.current == '}') {
            ctx.advance()
            new Right(Node.MapValues) // {:*} is MapValues
          } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'}'"))
        } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'*'"))
      case _ =>
        // Parse key(s)
        parseMapKey(ctx) match {
          case Right(firstKey) =>
            ctx.skipWhitespace()
            if (ctx.current == ',') {
              // Multiple keys
              val keys = ChunkBuilder.make[DynamicValue]()
              keys.addOne(firstKey)
              while (ctx.current == ',') {
                ctx.advance()
                ctx.skipWhitespace()
                parseMapKey(ctx) match {
                  case Left(err)  => return Left(err)
                  case Right(key) => keys.addOne(key)
                }
                ctx.skipWhitespace()
              }
              if (ctx.current == '}') {
                ctx.advance()
                new Right(new Node.AtMapKeys(keys.result()))
              } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'}' or ','"))
            } else if (ctx.current == '}') {
              ctx.advance()
              new Right(new Node.AtMapKey(firstKey))
            } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'}' or ','"))
          case l => l.asInstanceOf[Either[ParseError, Node]]
        }
    }
  }

  private def parseMapKey(ctx: ParseContext): Either[ParseError, DynamicValue] =
    ctx.current match {
      case '"' =>
        parseString(ctx).map(s => new DynamicValue.Primitive(new PrimitiveValue.String(s)))
      case '\'' =>
        parseChar(ctx).map(c => new DynamicValue.Primitive(new PrimitiveValue.Char(c)))
      case '-' =>
        ctx.advance()
        if (ctx.current.isDigit) {
          val start = ctx.pos
          val sb    = new java.lang.StringBuilder
          while (!ctx.atEnd && ctx.current.isDigit) {
            sb.append(ctx.current)
            ctx.advance()
          }
          val numStr = sb.toString
          try {
            new Right(new DynamicValue.Primitive(new PrimitiveValue.Int({
              if (numStr == "2147483648") Int.MinValue
              else -numStr.toInt
            })))
          } catch {
            case _: NumberFormatException => new Left(new ParseError.IntegerOverflow(start))
          }
        } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "digit after '-'"))
      case c if c.isDigit            => parseInteger(ctx).map(n => new DynamicValue.Primitive(new PrimitiveValue.Int(n)))
      case c if isIdentifierStart(c) =>
        val sb = new java.lang.StringBuilder
        while (!ctx.atEnd && isIdentifierPart(ctx.current)) {
          sb.append(ctx.current)
          ctx.advance()
        }
        val ident = sb.toString
        ident match {
          case "true"  => new Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(true)))
          case "false" => new Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
          case _       => new Left(new ParseError.InvalidSyntax(s"Invalid map key identifier '$ident'", ctx.pos))
        }

      case c => new Left(new ParseError.UnexpectedChar(c, ctx.pos, "string, char, int, or boolean"))
    }

  private def parseVariantCase(ctx: ParseContext): Either[ParseError, Node] = {
    ctx.advance() // Skip '<'
    ctx.skipWhitespace()
    if (ctx.atEnd || !isIdentifierStart(ctx.current)) return new Left(new ParseError.InvalidIdentifier(ctx.pos))
    val sb = new java.lang.StringBuilder
    while (!ctx.atEnd && isIdentifierPart(ctx.current)) {
      sb.append(ctx.current)
      ctx.advance()
    }
    ctx.skipWhitespace()
    if (ctx.current == '>') {
      ctx.advance()
      new Right(new Node.Case(sb.toString))
    } else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'>'"))
  }

  private def parseString(ctx: ParseContext): Either[ParseError, String] = {
    val start = ctx.pos
    ctx.advance() // Skip opening '"'
    val sb = new java.lang.StringBuilder
    while (!ctx.atEnd && ctx.current != '"') {
      if (ctx.current == '\\') {
        ctx.advance()
        if (ctx.atEnd) return Left(new ParseError.UnterminatedString(start))
        ctx.current match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case 'n'  => sb.append('\n')
          case 't'  => sb.append('\t')
          case 'r'  => sb.append('\r')
          case c    => return new Left(new ParseError.InvalidEscape(c, ctx.pos))
        }
        ctx.advance()
      } else {
        sb.append(ctx.current)
        ctx.advance()
      }
    }
    if (ctx.atEnd) new Left(new ParseError.UnterminatedString(start))
    else {
      ctx.advance() // Skip closing '"'
      new Right(sb.toString)
    }
  }

  private def parseChar(ctx: ParseContext): Either[ParseError, Char] = {
    val start = ctx.pos
    ctx.advance() // Skip opening '\''
    if (ctx.atEnd) return new Left(new ParseError.UnterminatedChar(start))
    if (ctx.current == '\'') return new Left(new ParseError.EmptyChar(start))
    val ch = if (ctx.current == '\\') {
      ctx.advance()
      if (ctx.atEnd) return new Left(new ParseError.UnterminatedChar(start))
      val escaped = ctx.current match {
        case '\'' => '\''
        case '\\' => '\\'
        case 'n'  => '\n'
        case 't'  => '\t'
        case 'r'  => '\r'
        case c    => return new Left(new ParseError.InvalidEscape(c, ctx.pos))
      }
      ctx.advance()
      escaped
    } else {
      val c = ctx.current
      ctx.advance()
      c
    }
    if (ctx.atEnd) new Left(new ParseError.UnterminatedChar(start))
    else if (ctx.current != '\'') new Left(new ParseError.MultiCharLiteral(start))
    else {
      ctx.advance() // Skip closing '\''
      new Right(ch)
    }
  }

  private def parseInteger(ctx: ParseContext): Either[ParseError, Int] = {
    val start = ctx.pos
    val sb    = new java.lang.StringBuilder
    while (!ctx.atEnd && ctx.current.isDigit) {
      sb.append(ctx.current)
      ctx.advance()
    }
    if (sb.length() == 0) new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "digit"))
    else {
      try new Right(sb.toString.toInt)
      catch {
        case _: NumberFormatException => Left(new ParseError.IntegerOverflow(start))
      }
    }
  }

  private def isIdentifierStart(c: Char): Boolean = c == '_' || Character.isLetter(c)

  private def isIdentifierPart(c: Char): Boolean = c == '_' || Character.isLetterOrDigit(c)
}
