package zio.blocks.schema

/**
 * Pure Scala parser for path syntax used by p"..." at compile time.
 * 
 * This module provides tokenization and parsing functionality for
 * path expressions without any runtime allocations beyond simple vectors/lists.
 */
object PathParser {

  /**
   * Error type for path parsing failures.
   * 
   * @param message Human-readable error description
   * @param index Position in the input string where the error occurred
   */
  case class PathError(message: String, index: Int)

  /**
   * Internal parsing segments representing different parts of the path syntax.
   * These are used during the tokenization and parsing process.
   */
  sealed trait Segment

  object Segment {
    case class Field(name: String) extends Segment
    case class Index(content: String) extends Segment
    case class MapAccess(content: String) extends Segment
    case class VariantAccess(content: String) extends Segment
  }

  /**
   * Token representing a parsed unit from the input string.
   */
  case class Token(segment: Segment, startIndex: Int, endIndex: Int)

  /**
   * Basic tokenizer that scans the string left-to-right.
   * Recognizes three bracket types: [] {} <>
   * 
   * @param input The path string to tokenize
   * @return Vector of tokens or a PathError if tokenization fails
   */
  private def tokenize(input: String): Either[PathError, Vector[Token]] = {
    val tokens = Vector.newBuilder[Token]
    var pos = 0
    val length = input.length

    while (pos < length) {
      val char = input.charAt(pos)
      
      char match {
        case '.' =>
          // Field access starting with dot - no whitespace allowed around dot
          pos += 1
          if (pos < length && input.charAt(pos) != '.') {
            val fieldStart = pos
            while (pos < length && isFieldChar(input.charAt(pos))) {
              pos += 1
            }
            if (fieldStart < pos) {
              val fieldName = input.substring(fieldStart, pos)
              tokens += Token(Segment.Field(fieldName), fieldStart - 1, pos)
            } else {
              return Left(PathError("Expected field name after '.'", fieldStart - 1))
            }
          } else {
            return Left(PathError("Expected field name after '.'", pos - 1))
          }
          
        case '[' =>
          // Index block access
          val blockStart = pos
          pos += 1
          parseBracketContent(input, pos, ']') match {
            case Right((content, endPos)) =>
              pos = endPos + 1 // Skip closing bracket
              tokens += Token(Segment.Index(content), blockStart, pos)
            case Left(error) =>
              return Left(error)
          }
          
        case '{' =>
          // Map block access
          val blockStart = pos
          pos += 1
          parseBracketContent(input, pos, '}') match {
            case Right((content, endPos)) =>
              pos = endPos + 1 // Skip closing brace
              tokens += Token(Segment.MapAccess(content), blockStart, pos)
            case Left(error) =>
              return Left(error)
          }
          
        case '<' =>
          // Variant block access
          val blockStart = pos
          pos += 1
          parseBracketContent(input, pos, '>') match {
            case Right((content, endPos)) =>
              pos = endPos + 1 // Skip closing angle bracket
              tokens += Token(Segment.VariantAccess(content), blockStart, pos)
            case Left(error) =>
              return Left(error)
          }
          
        case _ if isFieldChar(char) =>
          // Direct field access without leading dot
          val fieldStart = pos
          while (pos < length && isFieldChar(input.charAt(pos))) {
            pos += 1
          }
          val fieldName = input.substring(fieldStart, pos)
          tokens += Token(Segment.Field(fieldName), fieldStart, pos)
          
        case _ =>
          return Left(PathError(s"Unexpected character '$char'", pos))
      }
    }

    Right(tokens.result())
  }

  /**
   * Helper method to check if a character is valid in field names.
   */
  private def isFieldChar(char: Char): Boolean = {
    char.isLetterOrDigit || char == '_' || char == '$'
  }

  /**
   * Helper method to parse content within brackets of any type.
   * Whitespace is allowed inside brackets.
   * 
   * @param input The input string
   * @param startPos Position after opening bracket
   * @param expectedClose The expected closing bracket character
   * @return Either an error or (content, endPos) where endPos is position of closing bracket
   */
  private def parseBracketContent(input: String, startPos: Int, expectedClose: Char): Either[PathError, (String, Int)] = {
    var pos = startPos
    val length = input.length
    val content = new StringBuilder

    while (pos < length && input.charAt(pos) != expectedClose) {
      content += input.charAt(pos)
      pos += 1
    }

    if (pos >= length) {
      Left(PathError(s"Unclosed bracket: expected '$expectedClose'", startPos - 1))
    } else {
      Right((content.toString, pos))
    }
  }

  /**
   * Safely parses a decimal integer string, checking for overflow.
   * Leading zeros are allowed and ignored in the numeric value.
   * 
   * @param str The string to parse as an integer
   * @param at Position index for error reporting
   * @return Either an error or the parsed integer
   */
  private def parseIntSafe(str: String, at: Int): Either[PathError, Int] = {
    if (str.isEmpty) {
      return Left(PathError("Empty integer string", at))
    }

    try {
      val value = str.toInt
      Right(value)
    } catch {
      case _: NumberFormatException =>
        Left(PathError(s"Invalid integer or overflow: '$str'", at))
    }
  }

  /**
   * Parses a single index expression like "[42]".
   * 
   * @param str The trimmed content inside brackets
   * @param at Position index for error reporting
   * @return Either an error or AtIndex node
   */
  private def parseSingleIndex(str: String, at: Int): Either[PathError, DynamicOptic.Node] = {
    parseIntSafe(str, at).map(DynamicOptic.Node.AtIndex(_))
  }

  /**
   * Parses a comma-separated list of indices like "[0,1,2]".
   * Spaces around commas are allowed.
   * 
   * @param str The trimmed content inside brackets
   * @param at Position index for error reporting
   * @return Either an error or AtIndices node
   */
  private def parseMultiIndexList(str: String, at: Int): Either[PathError, DynamicOptic.Node] = {
    val parts = str.split(',').map(_.trim)
    val indices = Vector.newBuilder[Int]
    
    var pos = 0
    while (pos < parts.length) {
      val part = parts(pos)
      parseIntSafe(part, at) match {
        case Right(index) => indices += index
        case Left(error)  => return Left(error)
      }
      pos += 1
    }
    
    Right(DynamicOptic.Node.AtIndices(indices.result()))
  }

  /**
   * Parses a range expression like "[0:5]" which means indices 0 through 4.
   * Empty ranges like "[5:5]" result in an empty list.
   * 
   * @param str The trimmed content inside brackets
   * @param at Position index for error reporting
   * @return Either an error or AtIndices node
   */
  private def parseRange(str: String, at: Int): Either[PathError, DynamicOptic.Node] = {
    val parts = str.split(':')
    if (parts.length != 2) {
      return Left(PathError(s"Invalid range format: '$str'", at))
    }

    val startStr = parts(0).trim
    val endStr = parts(1).trim

    // Handle cases like "[:5]" or "[0:]" - for now, both parts must be present
    if (startStr.isEmpty || endStr.isEmpty) {
      return Left(PathError(s"Range bounds cannot be empty: '$str'", at))
    }

    for {
      start <- parseIntSafe(startStr, at)
      end   <- parseIntSafe(endStr, at)
    } yield {
      if (start >= end) {
        // Empty range or invalid range
        DynamicOptic.Node.AtIndices(Vector.empty)
      } else {
        // Generate range from start to end-1
        val indices = (start until end).toVector
        DynamicOptic.Node.AtIndices(indices)
      }
    }
  }

  /**
   * Decodes escape sequences in string and character literals.
   * Supports standard escapes only: \", \\, \n, \t, \r, \'
   * 
   * @param str The string with escape sequences
   * @param at Position index for error reporting
   * @return Either an error or the decoded string
   */
  private def decodeEscapes(str: String, at: Int): Either[PathError, String] = {
    val result = new StringBuilder
    var i = 0
    
    while (i < str.length) {
      if (str.charAt(i) == '\\' && i + 1 < str.length) {
        val escape = str.charAt(i + 1)
        escape match {
          case '"'  => result += '"'
          case '\\' => result += '\\'
          case 'n'  => result += '\n'
          case 't'  => result += '\t'
          case 'r'  => result += '\r'
          case '\'' => result += '\''
          case _    => return Left(PathError(s"Invalid escape sequence: '\\$escape'", at))
        }
        i += 2
      } else {
        result += str.charAt(i)
        i += 1
      }
    }
    
    Right(result.toString)
  }

  /**
   * Parses a string literal enclosed in double quotes.
   * Handles escape sequences and Unicode characters.
   * 
   * @param str The string literal including quotes
   * @param at Position index for error reporting
   * @return Either an error or PrimitiveValue.String
   */
  private def parseStringLiteral(str: String, at: Int): Either[PathError, PrimitiveValue] = {
    if (str.length < 2 || str.charAt(0) != '"' || str.charAt(str.length - 1) != '"') {
      return Left(PathError(s"Invalid string literal: '$str'", at))
    }

    val content = str.substring(1, str.length - 1)
    decodeEscapes(content, at).map(PrimitiveValue.String(_))
  }

  /**
   * Parses a character literal enclosed in single quotes.
   * Handles escape sequences and ensures exactly one character.
   * 
   * @param str The character literal including quotes
   * @param at Position index for error reporting
   * @return Either an error or PrimitiveValue.Char
   */
  private def parseCharLiteral(str: String, at: Int): Either[PathError, PrimitiveValue] = {
    if (str.length < 2 || str.charAt(0) != '\'' || str.charAt(str.length - 1) != '\'') {
      return Left(PathError(s"Invalid character literal: '$str'", at))
    }

    val content = str.substring(1, str.length - 1)
    decodeEscapes(content, at).flatMap { decoded =>
      if (decoded.length == 1) {
        Right(PrimitiveValue.Char(decoded.charAt(0)))
      } else {
        Left(PathError(s"Character literal must contain exactly one character: '$str'", at))
      }
    }
  }

  /**
   * Parses a boolean literal (true or false).
   * 
   * @param str The boolean literal
   * @param at Position index for error reporting
   * @return Either an error or PrimitiveValue.Boolean
   */
  private def parseBooleanLiteral(str: String, at: Int): Either[PathError, PrimitiveValue] = {
    str match {
      case "true"  => Right(PrimitiveValue.Boolean(true))
      case "false" => Right(PrimitiveValue.Boolean(false))
      case _       => Left(PathError(s"Invalid boolean literal: '$str'", at))
    }
  }

  /**
   * Parses an integer literal with overflow detection.
   * Handles leading zeros and negative numbers.
   * 
   * @param str The integer literal
   * @param at Position index for error reporting
   * @return Either an error or PrimitiveValue.Int
   */
  private def parseIntLiteralSafe(str: String, at: Int): Either[PathError, PrimitiveValue] = {
    parseIntSafe(str, at).map(PrimitiveValue.Int(_))
  }

  /**
   * Parses a single map key and converts it to a PrimitiveValue.
   * Auto-detects the type based on the literal format.
   * 
   * @param str The trimmed key content
   * @param at Position index for error reporting
   * @return Either an error or PrimitiveValue
   */
  private def parseSingleMapKey(str: String, at: Int): Either[PathError, PrimitiveValue] = {
    if (str.isEmpty) {
      return Left(PathError("Empty map key", at))
    }

    // Detect type based on first and last characters
    str.charAt(0) match {
      case '"' => parseStringLiteral(str, at)
      case '\'' => parseCharLiteral(str, at)
      case _ =>
        // Could be boolean or integer
        if (str == "true" || str == "false") {
          parseBooleanLiteral(str, at)
        } else {
          // Try to parse as integer
          parseIntLiteralSafe(str, at)
        }
    }
  }

  /**
   * Parses a comma-separated list of map keys.
   * Spaces around commas are allowed.
   * 
   * @param str The trimmed content inside braces
   * @param at Position index for error reporting
   * @return Either an error or AtMapKeys node
   */
  private def parseMultiKeyList(str: String, at: Int): Either[PathError, DynamicOptic.Node] = {
    val parts = str.split(',').map(_.trim)
    val keys = Vector.newBuilder[DynamicValue]
    
    var pos = 0
    while (pos < parts.length) {
      val part = parts(pos)
      parseSingleMapKey(part, at) match {
        case Right(primitive) => 
          keys += DynamicValue.Primitive(primitive)
        case Left(error) => 
          return Left(error)
      }
      pos += 1
    }
    
    Right(DynamicOptic.Node.AtMapKeys(keys.result()))
  }

  /**
   * Reads a field segment from tokens and converts it to a DynamicOptic.Node.
   * 
   * @param tokens Vector of tokens to process
   * @param pos Current position in the token vector
   * @return Either an error or the field node and next position
   */
  private def readField(tokens: Vector[Token], pos: Int): Either[PathError, (DynamicOptic.Node, Int)] = {
    if (pos >= tokens.length) {
      return Left(PathError("Unexpected end of tokens while reading field", pos))
    }

    tokens(pos) match {
      case Token(Segment.Field(name), startIndex, endIndex) =>
        // Validate field name - empty names are not allowed
        if (name.isEmpty) {
          Left(PathError("Field name cannot be empty", startIndex))
        } else {
          Right((DynamicOptic.Node.Field(name), pos + 1))
        }
      case Token(other, startIndex, _) =>
        Left(PathError(s"Expected field token but got ${other.getClass.getSimpleName}", startIndex))
    }
  }

  /**
   * Reads an index block segment from tokens and converts it to appropriate DynamicOptic.Node.
   * Handles single indices, multiple indices, ranges, and the star selector.
   * 
   * @param tokens Vector of tokens to process
   * @param pos Current position in the token vector
   * @return Either an error or the index node and next position
   */
  private def readIndexBlock(tokens: Vector[Token], pos: Int): Either[PathError, (DynamicOptic.Node, Int)] = {
    if (pos >= tokens.length) {
      return Left(PathError("Unexpected end of tokens while reading index block", pos))
    }

    tokens(pos) match {
      case Token(Segment.Index(content), startIndex, _) =>
        val trimmed = content.trim
        
        // Determine the type of index expression and parse accordingly
        if (trimmed == "*" || trimmed == ":*") {
          Right((DynamicOptic.Node.Elements, pos + 1))
        } else if (trimmed.contains(':')) {
          parseRange(trimmed, startIndex).map(node => (node, pos + 1))
        } else if (trimmed.contains(',')) {
          parseMultiIndexList(trimmed, startIndex).map(node => (node, pos + 1))
        } else {
          parseSingleIndex(trimmed, startIndex).map(node => (node, pos + 1))
        }
      case Token(other, startIndex, _) =>
        Left(PathError(s"Expected index token but got ${other.getClass.getSimpleName}", startIndex))
    }
  }

  /**
   * Reads a map block segment from tokens and converts it to appropriate DynamicOptic.Node.
   * Handles string, integer, boolean, and char keys, plus map selectors.
   * 
   * @param tokens Vector of tokens to process
   * @param pos Current position in the token vector
   * @return Either an error or the map node and next position
   */
  private def readMapBlock(tokens: Vector[Token], pos: Int): Either[PathError, (DynamicOptic.Node, Int)] = {
    if (pos >= tokens.length) {
      return Left(PathError("Unexpected end of tokens while reading map block", pos))
    }

    tokens(pos) match {
      case Token(Segment.MapAccess(content), startIndex, _) =>
        val trimmed = content.trim
        
        // Handle map selectors
        if (trimmed == "*" || trimmed == ":*") {
          Right((DynamicOptic.Node.MapValues, pos + 1))
        } else if (trimmed == "*:") {
          Right((DynamicOptic.Node.MapKeys, pos + 1))
        } else if (trimmed.contains(',')) {
          parseMultiKeyList(trimmed, startIndex).map(node => (node, pos + 1))
        } else {
          // Single key - convert to AtMapKey
          parseSingleMapKey(trimmed, startIndex).flatMap { primitive =>
            Right((DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(primitive)), pos + 1))
          }
        }
      case Token(other, startIndex, _) =>
        Left(PathError(s"Expected map token but got ${other.getClass.getSimpleName}", startIndex))
    }
  }

  /**
   * Reads a variant block segment from tokens and converts it to a DynamicOptic.Node.
   * Handles variant case selectors like <CaseName>.
   * 
   * @param tokens Vector of tokens to process
   * @param pos Current position in the token vector
   * @return Either an error or the variant case node and next position
   */
  private def readVariantBlock(tokens: Vector[Token], pos: Int): Either[PathError, (DynamicOptic.Node, Int)] = {
    if (pos >= tokens.length) {
      return Left(PathError("Unexpected end of tokens while reading variant block", pos))
    }

    tokens(pos) match {
      case Token(Segment.VariantAccess(content), startIndex, _) =>
        val trimmed = content.trim
        
        if (trimmed.isEmpty) {
          Left(PathError("Empty variant case", startIndex))
        } else if (!isValidVariantIdentifier(trimmed)) {
          Left(PathError("Invalid variant case name", startIndex))
        } else {
          Right((DynamicOptic.Node.Case(trimmed), pos + 1))
        }
      case Token(other, startIndex, _) =>
        Left(PathError(s"Expected variant token but got ${other.getClass.getSimpleName}", startIndex))
    }
  }

  /**
   * Validates that a string is a valid variant case identifier.
   * Uses the same rules as field names: letters, digits, underscores, and dollar signs.
   * Unicode characters are allowed since they pass the isLetterOrDigit check.
   * 
   * @param str The identifier to validate
   * @return true if valid, false otherwise
   */
  private def isValidVariantIdentifier(str: String): Boolean = {
    if (str.isEmpty) return false
    
    var i = 0
    while (i < str.length) {
      if (!isFieldChar(str.charAt(i))) {
        return false
      }
      i += 1
    }
    true
  }

  /**
   * Main entry point for parsing path strings.
   * Handles chained segments: foo.bar, foo[0], foo{"key"}, foo<Case>, etc.
   * 
   * @param path The path string to parse
   * @return Either a PathError or a Vector of DynamicOptic.Node representing the parsed path
   */
  def parse(path: String): Either[PathError, Vector[DynamicOptic.Node]] = {
    if (path.isEmpty) {
      return Right(Vector.empty)
    }

    tokenize(path) match {
      case Right(tokens) =>
        val nodes = Vector.newBuilder[DynamicOptic.Node]
        var pos = 0
        
        // Process tokens in order, handling all segment types
        while (pos < tokens.length) {
          tokens(pos) match {
            case Token(Segment.Field(_), _, _) =>
              readField(tokens, pos) match {
                case Right((node, nextPos)) =>
                  nodes += node
                  pos = nextPos
                case Left(error) =>
                  return Left(error)
              }
            case Token(Segment.Index(_), _, _) =>
              readIndexBlock(tokens, pos) match {
                case Right((node, nextPos)) =>
                  nodes += node
                  pos = nextPos
                case Left(error) =>
                  return Left(error)
              }
            case Token(Segment.MapAccess(_), _, _) =>
              readMapBlock(tokens, pos) match {
                case Right((node, nextPos)) =>
                  nodes += node
                  pos = nextPos
                case Left(error) =>
                  return Left(error)
              }
            case Token(Segment.VariantAccess(_), _, _) =>
              readVariantBlock(tokens, pos) match {
                case Right((node, nextPos)) =>
                  nodes += node
                  pos = nextPos
                case Left(error) =>
                  return Left(error)
              }
          }
        }
        
        Right(nodes.result())
      case Left(error) =>
        Left(error)
    }
  }
}
