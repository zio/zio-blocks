package zio.blocks.schema.xml

import zio.blocks.chunk.{Chunk, ChunkBuilder}

object XmlReader {
  def read(input: String, config: ReaderConfig = ReaderConfig.default): Either[XmlError, Xml] =
    try new Right(new XmlReaderImpl(input, config).parse())
    catch {
      case e: XmlError => new Left(e)
    }

  /**
   * Reads XML from a byte array.
   *
   * Note: This method always decodes bytes as UTF-8. For other encodings,
   * decode the bytes to a String first and use the `read` method.
   */
  def readFromBytes(input: Array[Byte], config: ReaderConfig = ReaderConfig.default): Either[XmlError, Xml] =
    read(new String(input, "UTF-8"), config)

  private class XmlReaderImpl(input: String, config: ReaderConfig) {
    private[this] var pos    = 0
    private[this] var line   = 1
    private[this] var column = 1
    private[this] var depth  = 0

    def parse(): Xml = {
      skipWhitespace()
      if (current == '<' && peek() == '?') {
        advance() // skip '<'
        advance() // skip '?'
        parseProcessingInstructionContent()
        skipWhitespace()
      }
      parseElement()
    }

    private[this] def current: Char =
      if (pos < input.length) input.charAt(pos)
      else '\u0000'

    private[this] def peek(offset: Int = 1): Char = {
      val p = pos + offset
      if (p < input.length) input.charAt(p) else '\u0000'
    }

    private[this] def advance(): Unit =
      if (pos < input.length) {
        if (current == '\n') {
          line += 1
          column = 1
        } else column += 1
        pos += 1
      }

    private[this] def error(msg: String): Nothing = throw XmlError.parseError(msg, line, column)

    private[this] def isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    private[this] def skipWhitespace(): Unit =
      while (isWhitespace(current)) advance()

    private[this] def isNameStartChar(c: Char): Boolean = {
      val a = c | 0x20
      (a >= 'a' && a <= 'z') || c == '_' || c == ':'
    }

    private[this] def isNameChar(c: Char): Boolean =
      isNameStartChar(c) || (c >= '0' && c <= '9') || c == '-' || c == '.'

    private[this] def parseElement(): Xml.Element = {
      if (current != '<') error("Expected '<'")
      advance()
      val name       = parseName()
      val attributes = parseAttributes()
      skipWhitespace()
      var children = Chunk.empty[Xml]
      if (current == '/' && peek() == '>') {
        advance()
        advance()
      } else {
        if (current != '>') error("Expected '>' or '/>'")
        advance()
        depth += 1
        if (depth > config.maxDepth) error(s"Maximum depth ${config.maxDepth} exceeded")
        children = parseChildren(name)
        depth -= 1
      }
      new Xml.Element(name, attributes, children)
    }

    private[this] def parseName(): XmlName = {
      if (!isNameStartChar(current)) error("Invalid element name")
      val sb = new java.lang.StringBuilder
      while (isNameChar(current)) {
        sb.append(current)
        advance()
      }
      val fullName = sb.toString
      val colonIdx = fullName.indexOf(':')
      if (colonIdx > 0) {
        new XmlName(fullName.substring(colonIdx + 1), new Some(fullName.substring(0, colonIdx)), None)
      } else new XmlName(fullName)
    }

    private[this] def parseAttributes(): Chunk[(XmlName, String)] = {
      val attrs = ChunkBuilder.make[(XmlName, String)]()
      while (true) {
        skipWhitespace()
        val ch = current
        if (ch == '>' || ch == '/' || ch == '?') return attrs.result()
        if (attrs.knownSize >= config.maxAttributes) error(s"Maximum attributes ${config.maxAttributes} exceeded")
        val attrName = parseName()
        skipWhitespace()
        if (current != '=') error("Expected '=' after attribute name")
        advance()
        skipWhitespace()
        val quote = current
        if (quote != '"' && quote != '\'') error("Expected quote after '='")
        advance()
        attrs.addOne((attrName, parseAttributeValue(quote)))
      }
      attrs.result()
    }

    private[this] def parseAttributeValue(quote: Char): String = {
      val sb       = new java.lang.StringBuilder
      var ch: Char = 0
      while ({
        ch = current
        ch != quote && ch != '\u0000'
      }) {
        if (ch == '&') sb.append(parseEntityReference())
        else {
          sb.append(ch)
          advance()
        }
      }
      if (current != quote) error("Unclosed attribute value")
      advance()
      sb.toString
    }

    private[this] def parseChildren(parentName: XmlName): Chunk[Xml] = {
      val children = ChunkBuilder.make[Xml]()
      while (true) {
        if (!config.preserveWhitespace) skipWhitespace()
        if (current == '<') {
          if (peek() == '/') {
            parseClosingTag(parentName)
            return children.result()
          } else if (peek() == '!') {
            advance()
            advance()
            if (current == '-' && peek() == '-') children.addOne(parseCommentAfterOpenMarker())
            else if (current == '[' && peek() == 'C') children.addOne(parseCDataAfterOpenBracket())
            else error("Invalid markup")
          } else if (peek() == '?') children.addOne(parseProcessingInstructionAfterOpen())
          else children.addOne(parseElement())
        } else if (current == '\u0000') error(s"Unclosed element '${parentName.localName}'")
        else {
          val text = parseText()
          if (text.nonEmpty) {
            val finalText =
              if (config.preserveWhitespace) text
              else text.trim
            if (finalText.nonEmpty) children.addOne(new Xml.Text(finalText))
          }
        }
      }
      children.result()
    }

    private[this] def parseClosingTag(expectedName: XmlName): Unit = {
      if (current != '<') error("Expected '<'")
      advance()
      if (current != '/') error("Expected '/'")
      advance()
      val closingName = parseName()
      if (closingName.localName != expectedName.localName) {
        error(s"Mismatched closing tag: expected '${expectedName.localName}', got '${closingName.localName}'")
      }
      skipWhitespace()
      if (current != '>') error("Expected '>'")
      advance()
    }

    private[this] def parseText(): String = {
      val sb       = new java.lang.StringBuilder
      var ch: Char = 0
      while ({
        ch = current
        ch != '<' && ch != '\u0000'
      }) {
        if (ch == '&') sb.append(parseEntityReference())
        else {
          sb.append(ch)
          advance()
        }
      }
      if (sb.length > config.maxTextLength) {
        error(s"Text length ${sb.length} exceeds maximum ${config.maxTextLength}")
      }
      sb.toString
    }

    private[this] def parseEntityReference(): String = {
      if (current != '&') error("Expected '&'")
      advance()
      val sb       = new java.lang.StringBuilder
      var ch: Char = 0
      while ({
        ch = current
        ch != ';' && ch != '\u0000'
      }) {
        sb.append(ch)
        advance()
      }
      if (current != ';') error("Unclosed entity reference")
      advance()
      val entity = sb.toString
      entity match {
        case "amp"  => "&"
        case "lt"   => "<"
        case "gt"   => ">"
        case "quot" => "\""
        case "apos" => "'"
        case _      => error(s"Unknown entity reference: &$entity;")
      }
    }

    private[this] def parseCommentAfterOpenMarker(): Xml.Comment = {
      advance()
      advance()
      val sb = new java.lang.StringBuilder
      while (true) {
        val ch = current
        if (ch == '-' && peek() == '-' && peek(2) == '>') {
          advance()
          advance()
          advance()
          return new Xml.Comment(sb.toString)
        } else if (ch == '\u0000') error("Unclosed comment")
        else {
          sb.append(ch)
          advance()
        }
      }
      new Xml.Comment("") // not reachable
    }

    private[this] def parseCDataAfterOpenBracket(): Xml.CData = {
      if (current != '[') error("Expected '['")
      advance()
      if (!matchString("CDATA[")) error("Expected 'CDATA['")
      val sb = new java.lang.StringBuilder
      while (true) {
        val ch = current
        if (ch == ']' && peek() == ']' && peek(2) == '>') {
          advance()
          advance()
          advance()
          return new Xml.CData(sb.toString)
        } else if (ch == '\u0000') { error("Unclosed CDATA section") }
        else {
          sb.append(ch)
          advance()
        }
      }
      new Xml.CData("") // not reachable
    }

    private[this] def parseProcessingInstructionAfterOpen(): Xml.ProcessingInstruction = {
      advance()
      advance()
      parseProcessingInstructionContent()
    }

    private[this] def parseProcessingInstructionContent(): Xml.ProcessingInstruction = {
      val target            = parseName()
      val sb                = new java.lang.StringBuilder
      var seenNonWhitespace = false
      while (true) {
        val ch = current
        if (ch == '?' && peek() == '>') {
          advance()
          advance()
          val data    = sb.toString
          val trimmed =
            if (seenNonWhitespace) data.trim
            else ""
          return new Xml.ProcessingInstruction(target.localName, trimmed)
        } else if (ch == '\u0000') {
          error("Unclosed processing instruction")
        } else {
          if (!isWhitespace(ch)) seenNonWhitespace = true
          sb.append(ch)
          advance()
        }
      }
      new Xml.ProcessingInstruction("", "") // not reachable
    }

    private[this] def matchString(s: String): Boolean = {
      val len = s.length
      var idx = 0
      while (idx < len) {
        if (peek(idx) != s.charAt(idx)) return false
        idx += 1
      }
      idx = 0
      while (idx < len) {
        advance()
        idx += 1
      }
      true
    }
  }
}
