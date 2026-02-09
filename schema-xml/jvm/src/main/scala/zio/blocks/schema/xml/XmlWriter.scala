package zio.blocks.schema.xml

import java.nio.charset.{Charset, UnsupportedCharsetException}

import zio.blocks.chunk.Chunk

/**
 * JVM implementation of XmlWriter for serializing Xml AST nodes to XML strings.
 */
object XmlWriter {

  /**
   * Writes an XML node to a string.
   *
   * @param xml
   *   The XML node to write
   * @param config
   *   Writer configuration
   * @return
   *   XML string representation
   */
  def write(xml: Xml, config: WriterConfig = WriterConfig.default): String = {
    val sb = new StringBuilder
    if (config.includeDeclaration) {
      sb.append("<?xml version=\"1.0\" encoding=\"")
      sb.append(config.encoding)
      sb.append("\"?>")
      if (config.indentStep > 0) sb.append('\n')
    }
    writeNode(xml, sb, 0, config)
    sb.toString
  }

  /**
   * Writes an XML node to a byte array.
   *
   * @param xml
   *   The XML node to write
   * @param config
   *   Writer configuration
   * @return
   *   XML byte array in the specified encoding
   */
  def writeToBytes(xml: Xml, config: WriterConfig = WriterConfig.default): Array[Byte] = {
    val str = write(xml, config)
    try {
      val charset = Charset.forName(config.encoding)
      str.getBytes(charset)
    } catch {
      case _: UnsupportedCharsetException =>
        throw XmlError.encodingError(s"Unsupported encoding: ${config.encoding}")
    }
  }

  private def writeNode(xml: Xml, sb: StringBuilder, depth: Int, config: WriterConfig): Unit = xml match {
    case Xml.Text(value) =>
      sb.append(escapeText(value))

    case Xml.CData(value) =>
      sb.append("<![CDATA[")
      sb.append(value)
      sb.append("]]>")

    case Xml.Comment(value) =>
      sb.append("<!--")
      sb.append(value)
      sb.append("-->")

    case Xml.ProcessingInstruction(target, data) =>
      sb.append("<?")
      sb.append(target)
      if (data.nonEmpty) {
        sb.append(' ')
        sb.append(data)
      }
      sb.append("?>")

    case Xml.Element(name, attributes, children) =>
      writeElement(name, attributes, children, sb, depth, config)
  }

  private def writeElement(
    name: XmlName,
    attributes: Chunk[(XmlName, String)],
    children: Chunk[Xml],
    sb: StringBuilder,
    depth: Int,
    config: WriterConfig
  ): Unit = {
    sb.append('<')
    writeName(name, sb)

    name.namespace.foreach { ns =>
      sb.append(" xmlns=\"")
      sb.append(escapeAttribute(ns))
      sb.append('"')
    }

    var i = 0
    while (i < attributes.length) {
      val (attrName, attrValue) = attributes(i)
      sb.append(' ')
      writeName(attrName, sb)
      sb.append("=\"")
      sb.append(escapeAttribute(attrValue))
      sb.append('"')
      i += 1
    }

    if (children.isEmpty) {
      sb.append("/>")
    } else {
      sb.append('>')

      val onlyText = children.length == 1 && children(0).isInstanceOf[Xml.Text]

      if (!onlyText && config.indentStep > 0) {
        var i = 0
        while (i < children.length) {
          sb.append('\n')
          writeIndent(sb, depth + 1, config.indentStep)
          writeNode(children(i), sb, depth + 1, config)
          i += 1
        }
        sb.append('\n')
        writeIndent(sb, depth, config.indentStep)
      } else {
        var i = 0
        while (i < children.length) {
          writeNode(children(i), sb, depth + 1, config)
          i += 1
        }
      }

      sb.append("</")
      writeName(name, sb)
      sb.append('>')
    }
  }

  private def writeName(name: XmlName, sb: StringBuilder): Unit = {
    name.prefix.foreach { p =>
      sb.append(p)
      sb.append(':')
    }
    sb.append(name.localName)
  }

  private def writeIndent(sb: StringBuilder, depth: Int, indentStep: Int): Unit = {
    var i     = 0
    val total = depth * indentStep
    while (i < total) {
      sb.append(' ')
      i += 1
    }
  }

  private def escapeText(text: String): String =
    if (needsEscaping(text)) {
      val sb = new StringBuilder(text.length + 16)
      var i  = 0
      while (i < text.length) {
        text.charAt(i) match {
          case '&' => sb.append("&amp;")
          case '<' => sb.append("&lt;")
          case '>' => sb.append("&gt;")
          case c   => sb.append(c)
        }
        i += 1
      }
      sb.toString
    } else {
      text
    }

  private def escapeAttribute(attr: String): String =
    if (needsAttributeEscaping(attr)) {
      val sb = new StringBuilder(attr.length + 16)
      var i  = 0
      while (i < attr.length) {
        attr.charAt(i) match {
          case '&'  => sb.append("&amp;")
          case '<'  => sb.append("&lt;")
          case '"'  => sb.append("&quot;")
          case '\'' => sb.append("&apos;")
          case c    => sb.append(c)
        }
        i += 1
      }
      sb.toString
    } else {
      attr
    }

  private def needsEscaping(text: String): Boolean = {
    var i = 0
    while (i < text.length) {
      text.charAt(i) match {
        case '&' | '<' | '>' => return true
        case _               => ()
      }
      i += 1
    }
    false
  }

  private def needsAttributeEscaping(attr: String): Boolean = {
    var i = 0
    while (i < attr.length) {
      attr.charAt(i) match {
        case '&' | '<' | '"' | '\'' => return true
        case _                      => ()
      }
      i += 1
    }
    false
  }
}
