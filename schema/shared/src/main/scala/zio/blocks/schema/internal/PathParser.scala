package zio.blocks.schema.internal

/**
 * Parse result with position tracking for error reporting.
 */
sealed trait ParseResult[+A] {
  def map[B](f: A => B): ParseResult[B] = this match {
    case ParseSuccess(value, consumed) => ParseSuccess(f(value), consumed)
    case err: ParseError               => err
  }

  def flatMap[B](f: A => ParseResult[B]): ParseResult[B] = this match {
    case ParseSuccess(value, _) => f(value)
    case err: ParseError        => err
  }
}

case class ParseSuccess[A](value: A, consumed: Int)   extends ParseResult[A]
case class ParseError(message: String, position: Int) extends ParseResult[Nothing]

/**
 * Intermediate representation for path segments. This IR is easier to debug and
 * test than direct DynamicOptic.Node construction.
 */
sealed trait PathSegment

object PathSegment {
  case class Field(name: String)             extends PathSegment
  case class Index(n: Int)                   extends PathSegment
  case class Indices(ns: Seq[Int])           extends PathSegment
  case object Elements                       extends PathSegment // [*] or [:*]
  case class MapKey(key: MapKeyValue)        extends PathSegment
  case class MapKeys(keys: Seq[MapKeyValue]) extends PathSegment
  case object MapValues                      extends PathSegment // {*} or {:*}
  case object MapKeysSelector                extends PathSegment // {*:}
  case class VariantCase(name: String)       extends PathSegment // <CaseName>
}

/**
 * Typed map key values supporting String, Int, Char, and Boolean.
 */
sealed trait MapKeyValue

object MapKeyValue {
  case class StringKey(value: String) extends MapKeyValue
  case class IntKey(value: Int)       extends MapKeyValue
  case class CharKey(value: Char)     extends MapKeyValue
  case class BoolKey(value: Boolean)  extends MapKeyValue
}

/**
 * Recursive descent parser for DynamicOptic path expressions.
 *
 * Syntax supported:
 *   - Field access: `.field` or `field`
 *   - Single index: `[0]`
 *   - Multiple indices: `[0,1,2]`
 *   - Range (exclusive): `[0:5]` → Seq(0,1,2,3,4)
 *   - All elements: `[*]` or `[:*]`
 *   - Map key access: `{"key"}`, `{42}`, `{'c'}`, `{true}`
 *   - Multiple map keys: `{"a","b"}`
 *   - All map values: `{*}` or `{:*}`
 *   - All map keys: `{*:}`
 *   - Variant case: `<CaseName>`
 *
 * Whitespace is allowed inside [], {}, and <> but not around dots or between
 * segments.
 */
class PathParser(input: String) {
  private var pos: Int = 0

  private val MaxRangeSize: Int = 10000

  def parse(): ParseResult[List[PathSegment]] = {
    val segments = scala.collection.mutable.ListBuffer[PathSegment]()

    while (pos < input.length) {
      parseSegment() match {
        case ParseSuccess(seg, _) =>
          segments += seg
        case err: ParseError => return err
      }
    }

    ParseSuccess(segments.toList, input.length)
  }

  private def parseSegment(): ParseResult[PathSegment] =
    peek() match {
      case Some('.') =>
        advance()
        parseField()
      case Some('[') =>
        parseSequence()
      case Some('{') =>
        parseMap()
      case Some('<') =>
        parseVariant()
      case Some(c) if isIdentifierStart(c) =>
        parseField()
      case Some(c) =>
        ParseError(s"Unexpected character: '$c'", pos)
      case None =>
        ParseError("Unexpected end of input", pos)
    }

  private def parseField(): ParseResult[PathSegment] = {
    val startPos = pos
    peek() match {
      case Some(c) if c.isWhitespace =>
        return ParseError("Whitespace not allowed before field name", pos)
      case Some(c) if isIdentifierStart(c) =>
      // Continue
      case Some(c) =>
        return ParseError(s"Expected field name but found '$c'", pos)
      case None =>
        return ParseError("Expected field name but reached end of input", pos)
    }

    val sb = new StringBuilder
    while (peek().exists(isIdentifierPart)) {
      sb.append(current())
      advance()
    }

    if (sb.isEmpty) {
      ParseError("Empty field name", startPos)
    } else {
      ParseSuccess(PathSegment.Field(sb.toString), pos - startPos)
    }
  }

  /**
   * Parse sequence selector: [], [:*], [*], [N], [N,M,...], [N:M]
   */
  private def parseSequence(): ParseResult[PathSegment] = {
    val startPos = pos
    expect('[') match {
      case Some(err) => return err
      case None      =>
    }
    skipWhitespaceInBrackets()

    peek() match {
      case Some('*') =>
        // [*] → Elements
        advance()
        skipWhitespaceInBrackets()
        expect(']') match {
          case Some(err) => return err
          case None      => ParseSuccess(PathSegment.Elements, pos - startPos)
        }

      case Some(':') =>
        // [:*] → Elements
        advance()
        skipWhitespaceInBrackets()
        peek() match {
          case Some('*') =>
            advance()
            skipWhitespaceInBrackets()
            expect(']') match {
              case Some(err) => return err
              case None      => ParseSuccess(PathSegment.Elements, pos - startPos)
            }
          case other =>
            ParseError(s"Expected '*' after ':' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
        }

      case Some(d) if d.isDigit =>
        parseNonNegativeInt() match {
          case ParseSuccess(startNum, _) =>
            skipWhitespaceInBrackets()
            peek() match {
              case Some(']') =>
                // [N] → single index
                advance()
                ParseSuccess(PathSegment.Index(startNum), pos - startPos)

              case Some(',') =>
                // [N,M,...] → multiple indices
                parseRemainingIndices(startNum, startPos)

              case Some(':') =>
                // [N:M] → range (exclusive end)
                advance()
                skipWhitespaceInBrackets()
                parseNonNegativeInt() match {
                  case ParseSuccess(endNum, _) =>
                    skipWhitespaceInBrackets()
                    expect(']') match {
                      case Some(err) => return err
                      case None      =>
                    }
                    if (startNum > endNum) {
                      ParseError(s"Invalid range: start ($startNum) > end ($endNum)", startPos)
                    } else {
                      val rangeSize = endNum - startNum
                      if (rangeSize > MaxRangeSize) {
                        ParseError(s"Range too large: $rangeSize elements (max $MaxRangeSize)", startPos)
                      } else {
                        ParseSuccess(PathSegment.Indices((startNum until endNum).toSeq), pos - startPos)
                      }
                    }
                  case err: ParseError => err
                }

              case other =>
                ParseError(s"Expected ']', ':', or ',' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
            }
          case err: ParseError => err
        }

      case other =>
        ParseError(s"Expected '*', ':', or digit after '[' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
    }
  }

  private def parseRemainingIndices(firstIndex: Int, startPos: Int): ParseResult[PathSegment] = {
    val indices = scala.collection.mutable.ListBuffer[Int](firstIndex)

    while (peek().contains(',')) {
      advance() // consume ','
      skipWhitespaceInBrackets()
      parseNonNegativeInt() match {
        case ParseSuccess(n, _) =>
          indices += n
          skipWhitespaceInBrackets()
        case err: ParseError => return err
      }
    }

    expect(']') match {
      case Some(err) => return err
      case None      => ParseSuccess(PathSegment.Indices(indices.toSeq), pos - startPos)
    }
  }

  /**
   * Parse map selector: {*}, {:*}, {*:}, {"key"}, {42}, {'c'}, {true},
   * {"a","b"}
   */
  private def parseMap(): ParseResult[PathSegment] = {
    val startPos = pos
    expect('{') match {
      case Some(err) => return err
      case None      =>
    }
    skipWhitespaceInBrackets()

    peek() match {
      case Some('*') =>
        advance()
        skipWhitespaceInBrackets()
        peek() match {
          case Some('}') =>
            // {*} → MapValues
            advance()
            ParseSuccess(PathSegment.MapValues, pos - startPos)
          case Some(':') =>
            // {*:} → MapKeys
            advance()
            skipWhitespaceInBrackets()
            expect('}') match {
              case Some(err) => return err
              case None      => ParseSuccess(PathSegment.MapKeysSelector, pos - startPos)
            }
          case other =>
            ParseError(s"Expected '}' or ':' after '*' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
        }

      case Some(':') =>
        // {:*} → MapValues
        advance()
        skipWhitespaceInBrackets()
        peek() match {
          case Some('*') =>
            advance()
            skipWhitespaceInBrackets()
            expect('}') match {
              case Some(err) => return err
              case None      => ParseSuccess(PathSegment.MapValues, pos - startPos)
            }
          case other =>
            ParseError(s"Expected '*' after ':' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
        }

      case Some('"') =>
        parseMapKeys(startPos)

      case Some('\'') =>
        parseCharKey(startPos)

      case Some(d) if d.isDigit || d == '-' =>
        parseIntMapKey(startPos)

      case Some('t') | Some('f') =>
        parseBoolKey(startPos)

      case other =>
        ParseError(
          s"Expected map key or selector after '{{' but found ${other.map(c => s"'$c'").getOrElse("EOF")}",
          pos
        )
    }
  }

  private def parseMapKeys(startPos: Int): ParseResult[PathSegment] = {
    val keys = scala.collection.mutable.ListBuffer[MapKeyValue]()

    parseStringLiteral() match {
      case ParseSuccess(s, _) => keys += MapKeyValue.StringKey(s)
      case err: ParseError    => return err
    }

    skipWhitespaceInBrackets()

    while (peek().contains(',')) {
      advance() // consume ','
      skipWhitespaceInBrackets()
      parseStringLiteral() match {
        case ParseSuccess(s, _) => keys += MapKeyValue.StringKey(s)
        case err: ParseError    => return err
      }
      skipWhitespaceInBrackets()
    }

    expect('}') match {
      case Some(err) => return err
      case None      =>
        if (keys.size == 1) {
          ParseSuccess(PathSegment.MapKey(keys.head), pos - startPos)
        } else {
          ParseSuccess(PathSegment.MapKeys(keys.toSeq), pos - startPos)
        }
    }
  }

  private def parseCharKey(startPos: Int): ParseResult[PathSegment] = {
    expect('\'') match {
      case Some(err) => return err
      case None      =>
    }

    val charValue = peek() match {
      case Some('\\') =>
        advance()
        parseEscapeChar() match {
          case ParseSuccess(c, _) => c
          case err: ParseError    => return err
        }
      case Some(c) if c != '\'' =>
        advance()
        c
      case _ =>
        return ParseError("Empty char literal", pos)
    }

    expect('\'') match {
      case Some(err) => return err
      case None      =>
    }
    skipWhitespaceInBrackets()
    expect('}') match {
      case Some(err) => return err
      case None      => ParseSuccess(PathSegment.MapKey(MapKeyValue.CharKey(charValue)), pos - startPos)
    }
  }

  private def parseIntMapKey(startPos: Int): ParseResult[PathSegment] = {
    val negative = peek().contains('-')
    if (negative) advance()

    parseNonNegativeInt() match {
      case ParseSuccess(n, _) =>
        val value = if (negative) -n else n
        skipWhitespaceInBrackets()

        // Check for multiple int keys
        if (peek().contains(',')) {
          val keys = scala.collection.mutable.ListBuffer[MapKeyValue](MapKeyValue.IntKey(value))
          while (peek().contains(',')) {
            advance()
            skipWhitespaceInBrackets()
            val neg = peek().contains('-')
            if (neg) advance()
            parseNonNegativeInt() match {
              case ParseSuccess(m, _) =>
                keys += MapKeyValue.IntKey(if (neg) -m else m)
                skipWhitespaceInBrackets()
              case err: ParseError => return err
            }
          }
          expect('}') match {
            case Some(err) => return err
            case None      => ParseSuccess(PathSegment.MapKeys(keys.toSeq), pos - startPos)
          }
        } else {
          expect('}') match {
            case Some(err) => return err
            case None      => ParseSuccess(PathSegment.MapKey(MapKeyValue.IntKey(value)), pos - startPos)
          }
        }
      case err: ParseError => err
    }
  }

  private def parseBoolKey(startPos: Int): ParseResult[PathSegment] = {
    val word  = parseWord()
    val value = word match {
      case "true"  => true
      case "false" => false
      case _       => return ParseError(s"Expected 'true' or 'false' but found '$word'", startPos)
    }
    skipWhitespaceInBrackets()
    expect('}') match {
      case Some(err) => return err
      case None      => ParseSuccess(PathSegment.MapKey(MapKeyValue.BoolKey(value)), pos - startPos)
    }
  }

  /**
   * Parse variant case: <CaseName>
   */
  private def parseVariant(): ParseResult[PathSegment] = {
    val startPos = pos
    expect('<') match {
      case Some(err) => return err
      case None      =>
    }
    skipWhitespaceInBrackets()

    peek() match {
      case Some(c) if isIdentifierStart(c) =>
        val sb = new StringBuilder
        while (peek().exists(isIdentifierPart)) {
          sb.append(current())
          advance()
        }
        skipWhitespaceInBrackets()
        expect('>') match {
          case Some(err) => return err
          case None      => ParseSuccess(PathSegment.VariantCase(sb.toString), pos - startPos)
        }
      case other =>
        ParseError(s"Expected case name after '<' but found ${other.map(c => s"'$c'").getOrElse("EOF")}", pos)
    }
  }

  // ============ Helper Methods ============

  private def parseNonNegativeInt(): ParseResult[Int] = {
    val startPos = pos
    val sb       = new StringBuilder

    while (peek().exists(_.isDigit)) {
      sb.append(current())
      advance()
    }

    if (sb.isEmpty) {
      ParseError("Expected integer", startPos)
    } else {
      try {
        val value = sb.toString.toInt
        if (value < 0) {
          ParseError(s"Integer overflow: ${sb.toString} exceeds Int.MaxValue", startPos)
        } else {
          ParseSuccess(value, pos - startPos)
        }
      } catch {
        case _: NumberFormatException =>
          ParseError(s"Integer overflow: ${sb.toString} exceeds Int.MaxValue", startPos)
      }
    }
  }

  private def parseStringLiteral(): ParseResult[String] = {
    val startPos = pos
    expect('"') match {
      case Some(err) => return err
      case None      =>
    }

    val sb   = new StringBuilder
    var done = false

    while (!done && pos < input.length) {
      current() match {
        case '"' =>
          advance()
          done = true
        case '\\' =>
          advance()
          parseEscapeChar() match {
            case ParseSuccess(c, _) => sb.append(c)
            case err: ParseError    => return err
          }
        case c =>
          sb.append(c)
          advance()
      }
    }

    if (!done) {
      ParseError("Unterminated string literal", startPos)
    } else {
      ParseSuccess(sb.toString, pos - startPos)
    }
  }

  private def parseEscapeChar(): ParseResult[Char] =
    peek() match {
      case Some('n')  => advance(); ParseSuccess('\n', 1)
      case Some('t')  => advance(); ParseSuccess('\t', 1)
      case Some('r')  => advance(); ParseSuccess('\r', 1)
      case Some('\\') => advance(); ParseSuccess('\\', 1)
      case Some('"')  => advance(); ParseSuccess('"', 1)
      case Some('\'') => advance(); ParseSuccess('\'', 1)
      case Some(c)    => ParseError(s"Invalid escape sequence: \\$c", pos)
      case None       => ParseError("Unexpected end of input in escape sequence", pos)
    }

  private def parseWord(): String = {
    val sb = new StringBuilder
    while (peek().exists(c => c.isLetter)) {
      sb.append(current())
      advance()
    }
    sb.toString
  }

  private def peek(offset: Int = 0): Option[Char] = {
    val idx = pos + offset
    if (idx >= 0 && idx < input.length) Some(input.charAt(idx))
    else None
  }

  private def current(): Char = input.charAt(pos)

  private def advance(): Unit = pos += 1

  private def expect(c: Char): Option[ParseError] =
    if (peek().contains(c)) {
      advance()
      None
    } else {
      Some(ParseError(s"Expected '$c' but found ${peek().map(x => s"'$x'").getOrElse("EOF")}", pos))
    }

  private def skipWhitespaceInBrackets(): Unit =
    while (peek().exists(_.isWhitespace)) {
      advance()
    }

  private def isIdentifierStart(c: Char): Boolean = Character.isLetter(c) || c == '_'

  private def isIdentifierPart(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_'
}
