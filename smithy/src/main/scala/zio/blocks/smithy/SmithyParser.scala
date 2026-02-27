package zio.blocks.smithy

object SmithyParser {

  def parse(input: String): Either[SmithyError, SmithyModel] = {
    val parser = new SmithyParserState(input)
    parser.parseModel()
  }

  private val simpleShapeKeywords: Set[String] = Set(
    "blob",
    "boolean",
    "string",
    "byte",
    "short",
    "integer",
    "long",
    "float",
    "double",
    "bigInteger",
    "bigDecimal",
    "timestamp",
    "document"
  )

  private class SmithyParserState(input: String) {
    private var pos: Int    = 0
    private var line: Int   = 1
    private var column: Int = 1

    def parseModel(): Either[SmithyError, SmithyModel] =
      try {
        skipWsAndComments()

        val version = parseVersion() match {
          case Right(v)  => v
          case Left(err) => return Left(err)
        }

        skipWsAndComments()

        var metadata      = Map.empty[String, NodeValue]
        var namespace     = ""
        var useStatements = List.empty[ShapeId]

        // Parse metadata that appears before namespace
        while (lookingAt("metadata")) {
          parseMetadataEntry() match {
            case Right((k, v)) => metadata = metadata + (k -> v)
            case Left(err)     => return Left(err)
          }
          skipWsAndComments()
        }

        // Parse namespace (required)
        if (!lookingAt("namespace")) {
          return Left(error("Expected 'namespace' declaration"))
        }
        parseNamespace() match {
          case Right(ns) => namespace = ns
          case Left(err) => return Left(err)
        }

        skipWsAndComments()

        // Parse remaining metadata and use statements
        while (lookingAt("metadata") || lookingAt("use")) {
          if (lookingAt("metadata")) {
            parseMetadataEntry() match {
              case Right((k, v)) => metadata = metadata + (k -> v)
              case Left(err)     => return Left(err)
            }
          } else {
            parseUseStatement() match {
              case Right(id) => useStatements = useStatements :+ id
              case Left(err) => return Left(err)
            }
          }
          skipWsAndComments()
        }

        // Parse shapes
        var shapes = List.empty[ShapeDefinition]
        while (!atEnd) {
          skipWsAndComments()
          if (!atEnd) {
            parseShapeStatement() match {
              case Right(sd) => shapes = shapes :+ sd
              case Left(err) => return Left(err)
            }
            skipWsAndComments()
          }
        }

        Right(SmithyModel(version, namespace, useStatements, metadata, shapes))
      } catch {
        case e: SmithyParseException =>
          Left(SmithyError.ParseError(e.getMessage, e.line, e.column, None))
      }

    // -----------------------------------------------------------------------
    // Control section parsers
    // -----------------------------------------------------------------------

    private def parseVersion(): Either[SmithyError, String] = {
      if (!lookingAt("$version")) {
        return Left(error("Expected '$version' declaration"))
      }
      expectString("$version")
      skipWsInline()
      expectChar(':')
      skipWsInline()
      readQuotedString() match {
        case Right(v)  => Right(v)
        case Left(err) => Left(err)
      }
    }

    private def parseNamespace(): Either[SmithyError, String] = {
      expectString("namespace")
      skipWsInline()
      val ns = readNamespaceId()
      if (ns.isEmpty) Left(error("Expected namespace identifier"))
      else Right(ns)
    }

    private def parseUseStatement(): Either[SmithyError, ShapeId] = {
      expectString("use")
      skipWsInline()
      val fullId = readAbsoluteShapeId()
      if (fullId.isEmpty) return Left(error("Expected shape ID after 'use'"))
      val hashIdx = fullId.indexOf('#')
      if (hashIdx < 0) return Left(error("Use statement must contain '#', got: " + fullId))
      val ns   = fullId.substring(0, hashIdx)
      val name = fullId.substring(hashIdx + 1)
      if (ns.isEmpty || name.isEmpty) return Left(error("Invalid shape ID in use statement: " + fullId))
      Right(ShapeId(ns, name))
    }

    private def parseMetadataEntry(): Either[SmithyError, (String, NodeValue)] = {
      expectString("metadata")
      skipWsInline()
      val key = readIdentifier()
      if (key.isEmpty) return Left(error("Expected metadata key"))
      skipWsInline()
      expectChar('=')
      skipWsInline()
      parseNodeValue() match {
        case Right(v)  => Right((key, v))
        case Left(err) => Left(err)
      }
    }

    // -----------------------------------------------------------------------
    // Shape parsing
    // -----------------------------------------------------------------------

    private def parseShapeStatement(): Either[SmithyError, ShapeDefinition] = {
      // Collect trait applications (including doc comments)
      val traits = collectTraits() match {
        case Right(ts) => ts
        case Left(err) => return Left(err)
      }

      skipWsAndComments()

      // Now expect a shape keyword
      val keyword = readIdentifier()
      if (keyword.isEmpty) return Left(error("Expected shape keyword"))

      if (!simpleShapeKeywords.contains(keyword)) {
        return Left(
          error("Unexpected keyword: " + keyword + ". Only simple shapes are supported in this parser version.")
        )
      }

      skipWsInline()
      val shapeName = readIdentifier()
      if (shapeName.isEmpty) return Left(error("Expected shape name after '" + keyword + "'"))

      val shape = createSimpleShape(keyword, shapeName, traits)
      Right(ShapeDefinition(shapeName, shape))
    }

    private def collectTraits(): Either[SmithyError, List[TraitApplication]] = {
      var traits = List.empty[TraitApplication]

      skipWsAndComments()
      while (!atEnd) {
        if (
          peekChar() == '/' && pos + 2 < input.length && input.charAt(pos + 1) == '/' && input.charAt(pos + 2) == '/'
        ) {
          // Doc comment
          val docLines = collectDocCommentLines()
          val docText  = docLines.mkString("\n")
          traits = traits :+ TraitApplication.documentation(docText)
          skipWsAndComments()
        } else if (peekChar() == '@') {
          parseTraitApplication() match {
            case Right(ta) =>
              traits = traits :+ ta
              skipWsAndComments()
            case Left(err) => return Left(err)
          }
        } else {
          return Right(traits)
        }
      }

      Right(traits)
    }

    private def collectDocCommentLines(): List[String] = {
      var lines = List.empty[String]
      while (
        !atEnd && peekChar() == '/' && pos + 2 < input.length && input
          .charAt(pos + 1) == '/' && input.charAt(pos + 2) == '/'
      ) {
        advance() // /
        advance() // /
        advance() // /
        // Skip single leading space if present
        if (!atEnd && peekChar() == ' ') advance()
        val sb = new StringBuilder
        while (!atEnd && peekChar() != '\n') {
          sb.append(peekChar())
          advance()
        }
        lines = lines :+ sb.toString
        // Skip newline
        if (!atEnd && peekChar() == '\n') advance()
        skipWsAndComments()
      }
      lines
    }

    private def parseTraitApplication(): Either[SmithyError, TraitApplication] = {
      expectChar('@')
      val traitId = readTraitId()
      if (traitId.isEmpty) return Left(error("Expected trait name after '@'"))

      val shapeId = parseShapeIdFromString(traitId)

      // Check for trait value
      if (!atEnd && peekChar() == '(') {
        advance() // skip (
        skipWsAndComments()
        // Check if it's a structured value (key: value pairs) or a single value
        if (!atEnd && peekChar() == ')') {
          advance() // empty ()
          Right(TraitApplication(shapeId, Some(NodeValue.ObjectValue(Nil))))
        } else {
          // Look ahead to determine if this is an object body (key: value) or a single value
          val savedPos    = pos
          val savedLine   = line
          val savedColumn = column
          val isObject    = isObjectBody()
          pos = savedPos
          line = savedLine
          column = savedColumn

          if (isObject) {
            // Parse as object fields
            parseObjectFields() match {
              case Right(fields) =>
                skipWsAndComments()
                expectChar(')')
                Right(TraitApplication(shapeId, Some(NodeValue.ObjectValue(fields))))
              case Left(err) => Left(err)
            }
          } else {
            // Parse as single value
            parseNodeValue() match {
              case Right(v) =>
                skipWsAndComments()
                expectChar(')')
                Right(TraitApplication(shapeId, Some(v)))
              case Left(err) => Left(err)
            }
          }
        }
      } else {
        Right(TraitApplication(shapeId, None))
      }
    }

    private def isObjectBody(): Boolean = {
      // Try to determine if the content looks like key: value pairs
      // Read an identifier, then check for ':'
      skipWsAndComments()
      if (atEnd) return false
      val c = peekChar()
      if (!isIdentStart(c)) return false
      // Read the identifier
      while (!atEnd && isIdentPart(peekChar())) advance()
      skipWsInline()
      !atEnd && peekChar() == ':'
    }

    private def parseShapeIdFromString(s: String): ShapeId = {
      val hashIdx = s.indexOf('#')
      if (hashIdx >= 0) {
        ShapeId(s.substring(0, hashIdx), s.substring(hashIdx + 1))
      } else {
        // Simple name — assume smithy.api namespace
        ShapeId("smithy.api", s)
      }
    }

    // -----------------------------------------------------------------------
    // Node value parsing
    // -----------------------------------------------------------------------

    private def parseNodeValue(): Either[SmithyError, NodeValue] = {
      skipWsAndComments()
      if (atEnd) return Left(error("Unexpected end of input, expected a value"))

      peekChar() match {
        case '"'                         => readQuotedString().map(NodeValue.StringValue(_))
        case '['                         => parseArrayValue()
        case '{'                         => parseObjectValue()
        case c if c == '-' || isDigit(c) => readNumber().map(NodeValue.NumberValue(_))
        case c if isIdentStart(c)        =>
          val ident = readIdentifier()
          ident match {
            case "true"  => Right(NodeValue.BooleanValue(true))
            case "false" => Right(NodeValue.BooleanValue(false))
            case "null"  => Right(NodeValue.NullValue)
            case other   => Left(error("Unexpected identifier in value position: " + other))
          }
        case c => Left(error("Unexpected character '" + c + "' in value position"))
      }
    }

    private def parseArrayValue(): Either[SmithyError, NodeValue] = {
      expectChar('[')
      skipWsAndComments()
      if (!atEnd && peekChar() == ']') {
        advance()
        return Right(NodeValue.ArrayValue(Nil))
      }

      var values = List.empty[NodeValue]
      var first  = true
      while (!atEnd && peekChar() != ']') {
        if (!first) {
          skipWsAndComments()
          // Optional comma
          if (!atEnd && peekChar() == ',') {
            advance()
            skipWsAndComments()
          }
        }
        first = false
        if (!atEnd && peekChar() == ']') {
          // trailing comma case
        } else {
          parseNodeValue() match {
            case Right(v)  => values = values :+ v
            case Left(err) => return Left(err)
          }
        }
        skipWsAndComments()
      }
      if (atEnd) return Left(error("Unterminated array"))
      expectChar(']')
      Right(NodeValue.ArrayValue(values))
    }

    private def parseObjectValue(): Either[SmithyError, NodeValue] = {
      expectChar('{')
      skipWsAndComments()
      if (!atEnd && peekChar() == '}') {
        advance()
        return Right(NodeValue.ObjectValue(Nil))
      }

      parseObjectFields() match {
        case Right(fields) =>
          skipWsAndComments()
          expectChar('}')
          Right(NodeValue.ObjectValue(fields))
        case Left(err) => Left(err)
      }
    }

    private def parseObjectFields(): Either[SmithyError, List[(String, NodeValue)]] = {
      var fields = List.empty[(String, NodeValue)]
      var first  = true
      while (!atEnd && peekChar() != '}' && peekChar() != ')') {
        if (!first) {
          skipWsAndComments()
          // Optional comma
          if (!atEnd && peekChar() == ',') {
            advance()
            skipWsAndComments()
          }
        }
        first = false
        skipWsAndComments()
        if (!atEnd && peekChar() != '}' && peekChar() != ')') {
          // Read key
          val key = if (peekChar() == '"') {
            readQuotedString() match {
              case Right(k)  => k
              case Left(err) => return Left(err)
            }
          } else {
            readIdentifier()
          }
          if (key.isEmpty) return Left(error("Expected object key"))
          skipWsInline()
          expectChar(':')
          skipWsInline()
          parseNodeValue() match {
            case Right(v)  => fields = fields :+ (key -> v)
            case Left(err) => return Left(err)
          }
        }
        skipWsAndComments()
      }
      Right(fields)
    }

    // -----------------------------------------------------------------------
    // Simple shape factory
    // -----------------------------------------------------------------------

    private def createSimpleShape(keyword: String, name: String, traits: List[TraitApplication]): Shape =
      keyword match {
        case "blob"       => BlobShape(name, traits)
        case "boolean"    => BooleanShape(name, traits)
        case "string"     => StringShape(name, traits)
        case "byte"       => ByteShape(name, traits)
        case "short"      => ShortShape(name, traits)
        case "integer"    => IntegerShape(name, traits)
        case "long"       => LongShape(name, traits)
        case "float"      => FloatShape(name, traits)
        case "double"     => DoubleShape(name, traits)
        case "bigInteger" => BigIntegerShape(name, traits)
        case "bigDecimal" => BigDecimalShape(name, traits)
        case "timestamp"  => TimestampShape(name, traits)
        case "document"   => DocumentShape(name, traits)
        case _            => StringShape(name, traits) // fallback, should not happen
      }

    // -----------------------------------------------------------------------
    // Lexer helpers
    // -----------------------------------------------------------------------

    private def atEnd: Boolean = pos >= input.length

    private def peekChar(): Char =
      if (atEnd) '\u0000'
      else input.charAt(pos)

    private def advance(): Char = {
      val c = input.charAt(pos)
      pos += 1
      if (c == '\n') {
        line += 1
        column = 1
      } else {
        column += 1
      }
      c
    }

    private def expectChar(expected: Char): Unit = {
      if (atEnd) throw new SmithyParseException("Expected '" + expected + "' but reached end of input", line, column)
      val c = peekChar()
      if (c != expected)
        throw new SmithyParseException("Expected '" + expected + "' but found '" + c + "'", line, column)
      advance()
    }

    private def expectString(expected: String): Unit = {
      var i = 0
      while (i < expected.length) {
        if (atEnd || peekChar() != expected.charAt(i)) {
          throw new SmithyParseException("Expected '" + expected + "'", line, column)
        }
        advance()
        i += 1
      }
    }

    private def lookingAt(s: String): Boolean = {
      if (pos + s.length > input.length) return false
      var i = 0
      while (i < s.length) {
        if (input.charAt(pos + i) != s.charAt(i)) return false
        i += 1
      }
      // Make sure this is not a prefix of a longer identifier
      if (pos + s.length < input.length) {
        val nextChar = input.charAt(pos + s.length)
        !isIdentPart(nextChar)
      } else {
        true
      }
    }

    private def skipWsInline(): Unit =
      while (!atEnd && (peekChar() == ' ' || peekChar() == '\t')) advance()

    private def skipWsAndComments(): Unit =
      while (!atEnd) {
        val c = peekChar()
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
          advance()
        } else if (
          c == '/' && pos + 1 < input.length && input
            .charAt(pos + 1) == '/' && (pos + 2 >= input.length || input.charAt(pos + 2) != '/')
        ) {
          // Line comment (// but not ///)
          while (!atEnd && peekChar() != '\n') advance()
        } else {
          return
        }
      }

    private def readIdentifier(): String = {
      if (atEnd || !isIdentStart(peekChar())) return ""
      val sb = new StringBuilder
      while (!atEnd && isIdentPart(peekChar())) {
        sb.append(advance())
      }
      sb.toString
    }

    private def readNamespaceId(): String = {
      // namespace identifier: identifier(.identifier)*
      val sb    = new StringBuilder
      val first = readIdentifier()
      if (first.isEmpty) return ""
      sb.append(first)
      while (!atEnd && peekChar() == '.') {
        sb.append(advance()) // dot
        val next = readIdentifier()
        if (next.isEmpty) return sb.toString
        sb.append(next)
      }
      sb.toString
    }

    private def readAbsoluteShapeId(): String = {
      // namespace#name format
      val sb = new StringBuilder
      val ns = readNamespaceId()
      if (ns.isEmpty) return ""
      sb.append(ns)
      if (!atEnd && peekChar() == '#') {
        sb.append(advance()) // #
        val name = readIdentifier()
        sb.append(name)
      }
      sb.toString
    }

    private def readTraitId(): String = {
      // A trait ID can be simple (e.g., "required") or absolute (e.g., "smithy.api#required")
      val sb    = new StringBuilder
      val first = readIdentifier()
      if (first.isEmpty) return ""
      sb.append(first)
      // Check for dots (namespace) or hash (absolute)
      while (!atEnd && (peekChar() == '.' || peekChar() == '#')) {
        sb.append(advance())
        val next = readIdentifier()
        sb.append(next)
      }
      sb.toString
    }

    private def readQuotedString(): Either[SmithyError, String] = {
      if (atEnd || peekChar() != '"') return Left(error("Expected '\"'"))
      advance() // opening "
      val sb = new StringBuilder
      while (!atEnd && peekChar() != '"') {
        val c = peekChar()
        if (c == '\\') {
          advance() // backslash
          if (atEnd) return Left(error("Unterminated string escape"))
          val escaped = advance()
          escaped match {
            case 'n'   => sb.append('\n')
            case 't'   => sb.append('\t')
            case 'r'   => sb.append('\r')
            case '\\'  => sb.append('\\')
            case '"'   => sb.append('"')
            case '/'   => sb.append('/')
            case 'b'   => sb.append('\b')
            case 'f'   => sb.append('\f')
            case other => sb.append('\\'); sb.append(other)
          }
        } else {
          sb.append(advance())
        }
      }
      if (atEnd) return Left(error("Unterminated string"))
      advance() // closing "
      Right(sb.toString)
    }

    private def readNumber(): Either[SmithyError, BigDecimal] = {
      val sb = new StringBuilder
      if (!atEnd && peekChar() == '-') sb.append(advance())
      if (atEnd || !isDigit(peekChar())) return Left(error("Expected digit"))
      while (!atEnd && isDigit(peekChar())) sb.append(advance())
      if (!atEnd && peekChar() == '.') {
        sb.append(advance())
        if (atEnd || !isDigit(peekChar())) return Left(error("Expected digit after decimal point"))
        while (!atEnd && isDigit(peekChar())) sb.append(advance())
      }
      // Exponent
      if (!atEnd && (peekChar() == 'e' || peekChar() == 'E')) {
        sb.append(advance())
        if (!atEnd && (peekChar() == '+' || peekChar() == '-')) sb.append(advance())
        if (atEnd || !isDigit(peekChar())) return Left(error("Expected digit in exponent"))
        while (!atEnd && isDigit(peekChar())) sb.append(advance())
      }
      try Right(BigDecimal(sb.toString))
      catch { case _: NumberFormatException => Left(error("Invalid number: " + sb.toString)) }
    }

    // -----------------------------------------------------------------------
    // Character classification
    // -----------------------------------------------------------------------

    private def isIdentStart(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
    private def isIdentPart(c: Char): Boolean  = isIdentStart(c) || isDigit(c)
    private def isDigit(c: Char): Boolean      = c >= '0' && c <= '9'

    // -----------------------------------------------------------------------
    // Error helpers
    // -----------------------------------------------------------------------

    private def error(message: String): SmithyError =
      SmithyError.ParseError(message, line, column, None)
  }

  private class SmithyParseException(message: String, val line: Int, val column: Int) extends RuntimeException(message)
}
