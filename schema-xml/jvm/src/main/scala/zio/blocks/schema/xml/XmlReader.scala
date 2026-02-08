package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import scala.collection.mutable.ArrayBuffer

object XmlReader {

  def read(input: String, config: ReaderConfig = ReaderConfig.default): Either[XmlError, Xml] = {
    val reader = new XmlReaderImpl(input, config)
    try {
      Right(reader.parse())
    } catch {
      case e: XmlError => Left(e)
    }
  }

  def readFromBytes(input: Array[Byte], config: ReaderConfig = ReaderConfig.default): Either[XmlError, Xml] = {
    val str = new String(input, "UTF-8")
    read(str, config)
  }

  private class XmlReaderImpl(input: String, config: ReaderConfig) {
    private var pos    = 0
    private var line   = 1
    private var column = 1
    private var depth  = 0

    def parse(): Xml = parseDocument()

    private def current: Char               = if (pos < input.length) input.charAt(pos) else '\u0000'
    private def peek(offset: Int = 1): Char = {
      val p = pos + offset
      if (p < input.length) input.charAt(p) else '\u0000'
    }

    private def advance(): Unit =
      if (pos < input.length) {
        if (current == '\n') {
          line += 1
          column = 1
        } else {
          column += 1
        }
        pos += 1
      }

    private def error(msg: String): Nothing = throw XmlError.parseError(msg, line, column)

    private def isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    private def skipWhitespace(): Unit =
      while (isWhitespace(current)) advance()

    private def isNameStartChar(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':'

    private def isNameChar(c: Char): Boolean =
      isNameStartChar(c) || (c >= '0' && c <= '9') || c == '-' || c == '.'

    private def parseDocument(): Xml = {
      skipWhitespace()
      if (current == '<' && peek() == '?') {
        advance() // skip '<'
        advance() // skip '?'
        parseProcessingInstructionContent()
        skipWhitespace()
      }
      parseElement()
    }

    private def parseElement(): Xml.Element = {
      if (current != '<') error("Expected '<'")
      advance()

      val name       = parseName()
      val attributes = parseAttributes()

      skipWhitespace()

      if (current == '/' && peek() == '>') {
        advance()
        advance()
        return Xml.Element(name, attributes, Chunk.empty)
      }

      if (current != '>') error("Expected '>' or '/>'")
      advance()

      depth += 1
      if (depth > config.maxDepth) error(s"Maximum depth ${config.maxDepth} exceeded")

      val children = parseChildren(name)
      depth -= 1

      Xml.Element(name, attributes, children)
    }

    private def parseName(): XmlName = {
      if (!isNameStartChar(current)) error("Invalid element name")

      val sb = new StringBuilder

      while (isNameChar(current)) {
        sb.append(current)
        advance()
      }

      val fullName = sb.toString
      val colonIdx = fullName.indexOf(':')
      if (colonIdx > 0) {
        val localName = fullName.substring(colonIdx + 1)
        XmlName(localName)
      } else {
        XmlName(fullName)
      }
    }

    private def parseAttributes(): Chunk[(XmlName, String)] = {
      val attrs = ArrayBuffer[(XmlName, String)]()

      while (true) {
        skipWhitespace()
        if (current == '>' || current == '/' || current == '?') return Chunk.from(attrs)

        if (attrs.size >= config.maxAttributes) {
          error(s"Maximum attributes ${config.maxAttributes} exceeded")
        }

        val attrName = parseName()
        skipWhitespace()
        if (current != '=') error("Expected '=' after attribute name")
        advance()
        skipWhitespace()

        val quote = current
        if (quote != '"' && quote != '\'') error("Expected quote after '='")
        advance()

        val attrValue = parseAttributeValue(quote)
        attrs += ((attrName, attrValue))
      }
      Chunk.from(attrs)
    }

    private def parseAttributeValue(quote: Char): String = {
      val sb = new StringBuilder
      while (current != quote && current != '\u0000') {
        if (current == '&') {
          sb.append(parseEntityReference())
        } else {
          sb.append(current)
          advance()
        }
      }
      if (current != quote) error("Unclosed attribute value")
      advance()
      sb.toString
    }

    private def parseChildren(parentName: XmlName): Chunk[Xml] = {
      val children = ArrayBuffer[Xml]()

      while (true) {
        if (!config.preserveWhitespace) skipWhitespace()

        if (current == '<') {
          if (peek() == '/') {
            parseClosingTag(parentName)
            return Chunk.from(children)
          } else if (peek() == '!') {
            advance()
            advance()
            if (current == '-' && peek() == '-') {
              children += parseCommentAfterOpenMarker()
            } else if (current == '[' && peek() == 'C') {
              children += parseCDataAfterOpenBracket()
            } else {
              error("Invalid markup")
            }
          } else if (peek() == '?') {
            children += parseProcessingInstructionAfterOpen()
          } else {
            children += parseElement()
          }
        } else if (current == '\u0000') {
          error(s"Unclosed element '${parentName.localName}'")
        } else {
          val text = parseText()
          if (text.nonEmpty) {
            val finalText = if (config.preserveWhitespace) text else text.trim
            if (finalText.nonEmpty) children += Xml.Text(finalText)
          }
        }
      }
      Chunk.from(children)
    }

    private def parseClosingTag(expectedName: XmlName): Unit = {
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

    private def parseText(): String = {
      val sb = new StringBuilder
      while (current != '<' && current != '\u0000') {
        if (current == '&') {
          sb.append(parseEntityReference())
        } else {
          sb.append(current)
          advance()
        }
      }
      val text = sb.toString
      if (text.length > config.maxTextLength) {
        error(s"Text length ${text.length} exceeds maximum ${config.maxTextLength}")
      }
      text
    }

    private def parseEntityReference(): String = {
      if (current != '&') error("Expected '&'")
      advance()

      val sb = new StringBuilder
      while (current != ';' && current != '\u0000') {
        sb.append(current)
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

    private def parseCommentAfterOpenMarker(): Xml.Comment = {
      advance()
      advance()

      val sb = new StringBuilder
      while (true) {
        if (current == '-' && peek() == '-' && peek(2) == '>') {
          advance()
          advance()
          advance()
          return Xml.Comment(sb.toString)
        } else if (current == '\u0000') {
          error("Unclosed comment")
        } else {
          sb.append(current)
          advance()
        }
      }
      Xml.Comment("")
    }

    private def parseCDataAfterOpenBracket(): Xml.CData = {
      if (current != '[') error("Expected '['")
      advance()
      if (!matchString("CDATA[")) error("Expected 'CDATA['")

      val sb = new StringBuilder
      while (true) {
        if (current == ']' && peek() == ']' && peek(2) == '>') {
          advance()
          advance()
          advance()
          return Xml.CData(sb.toString)
        } else if (current == '\u0000') {
          error("Unclosed CDATA section")
        } else {
          sb.append(current)
          advance()
        }
      }
      Xml.CData("")
    }

    private def parseProcessingInstructionAfterOpen(): Xml.ProcessingInstruction = {
      advance()
      advance()
      parseProcessingInstructionContent()
    }

    private def parseProcessingInstructionContent(): Xml.ProcessingInstruction = {
      val target = parseName()

      val sb                = new StringBuilder
      var seenNonWhitespace = false

      while (true) {
        if (current == '?' && peek() == '>') {
          advance()
          advance()
          val data    = sb.toString
          val trimmed = if (seenNonWhitespace) data.trim else ""
          return Xml.ProcessingInstruction(target.localName, trimmed)
        } else if (current == '\u0000') {
          error("Unclosed processing instruction")
        } else {
          if (!isWhitespace(current)) seenNonWhitespace = true
          sb.append(current)
          advance()
        }
      }
      Xml.ProcessingInstruction("", "")
    }

    private def matchString(s: String): Boolean = {
      var i = 0
      while (i < s.length) {
        if (peek(i) != s.charAt(i)) return false
        i += 1
      }
      var j = 0
      while (j < s.length) {
        advance()
        j += 1
      }
      true
    }
  }
}
