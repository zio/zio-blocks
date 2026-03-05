package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk

import java.nio.charset.{Charset, UnsupportedCharsetException}

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
    val sb = new java.lang.StringBuilder
    if (config.includeDeclaration) {
      sb.append("<?xml version=\"1.0\" encoding=\"").append(config.encoding).append("\"?>")
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
    try str.getBytes(Charset.forName(config.encoding))
    catch {
      case _: UnsupportedCharsetException => throw XmlError.encodingError(s"Unsupported encoding: ${config.encoding}")
    }
  }

  private[this] def writeNode(xml: Xml, sb: java.lang.StringBuilder, depth: Int, config: WriterConfig): Unit =
    xml match {
      case t: Xml.Text                   => sb.append(escapeText(t.value))
      case cd: Xml.CData                 => sb.append("<![CDATA[").append(cd.value).append("]]>")
      case c: Xml.Comment                => sb.append("<!--").append(c.value).append("-->")
      case pi: Xml.ProcessingInstruction =>
        sb.append("<?").append(pi.target)
        if (pi.data.nonEmpty) {
          sb.append(' ').append(pi.data)
        }
        sb.append("?>")
      case e: Xml.Element => writeElement(e.name, e.attributes, e.children, sb, depth, config)
    }

  private[this] def writeElement(
    name: XmlName,
    attributes: Chunk[(XmlName, String)],
    children: Chunk[Xml],
    sb: java.lang.StringBuilder,
    depth: Int,
    config: WriterConfig
  ): Unit = {
    sb.append('<')
    writeName(name, sb)
    name.namespace.foreach { ns =>
      sb.append(" xmlns")
      name.prefix.foreach { p =>
        sb.append(':').append(p)
      }
      sb.append("=\"").append(escapeAttribute(ns)).append('"')
    }
    var idx = 0
    while (idx < attributes.length) {
      val attr = attributes(idx)
      sb.append(' ')
      writeName(attr._1, sb)
      sb.append("=\"").append(escapeAttribute(attr._2)).append('"')
      idx += 1
    }
    if (children.isEmpty) sb.append("/>")
    else {
      sb.append('>')
      val onlyText = children.length == 1 && children(0).isInstanceOf[Xml.Text]
      if (!onlyText && config.indentStep > 0) {
        idx = 0
        while (idx < children.length) {
          sb.append('\n')
          writeIndent(sb, depth + 1, config.indentStep)
          writeNode(children(idx), sb, depth + 1, config)
          idx += 1
        }
        sb.append('\n')
        writeIndent(sb, depth, config.indentStep)
      } else {
        idx = 0
        while (idx < children.length) {
          writeNode(children(idx), sb, depth + 1, config)
          idx += 1
        }
      }
      sb.append("</")
      writeName(name, sb)
      sb.append('>')
    }
  }

  private[this] def writeName(name: XmlName, sb: java.lang.StringBuilder): Unit = {
    name.prefix.foreach(p => sb.append(p).append(':'))
    sb.append(name.localName)
  }

  private[this] def writeIndent(sb: java.lang.StringBuilder, depth: Int, indentStep: Int): Unit = {
    val total = depth * indentStep
    var idx   = 0
    while (idx < total) {
      sb.append(' ')
      idx += 1
    }
  }

  private[this] def escapeText(text: String): String =
    if (needsEscaping(text)) {
      val len = text.length
      val sb  = new java.lang.StringBuilder(len + 16)
      var idx = 0
      while (idx < len) {
        text.charAt(idx) match {
          case '&' => sb.append("&amp;")
          case '<' => sb.append("&lt;")
          case '>' => sb.append("&gt;")
          case c   => sb.append(c)
        }
        idx += 1
      }
      sb.toString
    } else text

  private[this] def escapeAttribute(attr: String): String =
    if (needsAttributeEscaping(attr)) {
      val len = attr.length
      val sb  = new StringBuilder(len + 16)
      var idx = 0
      while (idx < len) {
        attr.charAt(idx) match {
          case '&'  => sb.append("&amp;")
          case '<'  => sb.append("&lt;")
          case '"'  => sb.append("&quot;")
          case '\'' => sb.append("&apos;")
          case c    => sb.append(c)
        }
        idx += 1
      }
      sb.toString
    } else attr

  private[this] def needsEscaping(text: String): Boolean = {
    val len = text.length
    var idx = 0
    while (idx < len) {
      text.charAt(idx) match {
        case '&' | '<' | '>' => return true
        case _               => idx += 1
      }
    }
    false
  }

  private[this] def needsAttributeEscaping(attr: String): Boolean = {
    val len = attr.length
    var idx = 0
    while (idx < len) {
      attr.charAt(idx) match {
        case '&' | '<' | '"' | '\'' => return true
        case _                      => idx += 1
      }
    }
    false
  }
}
