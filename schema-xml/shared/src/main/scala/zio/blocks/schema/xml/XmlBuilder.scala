package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk

/**
 * A fluent builder for constructing XML nodes programmatically.
 *
 * Provides factory methods for creating XML nodes and an ElementBuilder class
 * for building elements with a fluent API.
 *
 * Example:
 * {{{
 * val elem = XmlBuilder.element("root")
 *   .attr("id", "1")
 *   .attr("class", "main")
 *   .child(XmlBuilder.element("child").text("content").build)
 *   .build
 * }}}
 */
object XmlBuilder {

  /**
   * Creates an ElementBuilder for constructing an element with the given local
   * name.
   *
   * @param name
   *   The local name of the element
   * @return
   *   An ElementBuilder for fluent construction
   */
  def element(name: String): ElementBuilder =
    ElementBuilder(XmlName(name), Chunk.empty, Chunk.empty)

  /**
   * Creates an ElementBuilder for constructing an element with the given
   * XmlName.
   *
   * @param name
   *   The XmlName (possibly with namespace) of the element
   * @return
   *   An ElementBuilder for fluent construction
   */
  def element(name: XmlName): ElementBuilder =
    ElementBuilder(name, Chunk.empty, Chunk.empty)

  /**
   * Creates a Text node with the given content.
   *
   * @param value
   *   The text content
   * @return
   *   A Text node
   */
  def text(value: String): Xml.Text =
    Xml.Text(value)

  /**
   * Creates a CData node with the given content.
   *
   * @param value
   *   The CDATA content
   * @return
   *   A CData node
   */
  def cdata(value: String): Xml.CData =
    Xml.CData(value)

  /**
   * Creates a Comment node with the given content.
   *
   * @param value
   *   The comment content
   * @return
   *   A Comment node
   */
  def comment(value: String): Xml.Comment =
    Xml.Comment(value)

  /**
   * Creates a ProcessingInstruction node with the given target and data.
   *
   * @param target
   *   The processing instruction target
   * @param data
   *   The processing instruction data
   * @return
   *   A ProcessingInstruction node
   */
  def processingInstruction(target: String, data: String): Xml.ProcessingInstruction =
    Xml.ProcessingInstruction(target, data)

  /**
   * A fluent builder for constructing XML elements.
   *
   * Accumulates attributes and children, allowing them to be added in any order
   * via method chaining.
   *
   * @param name
   *   The element name
   * @param attributes
   *   Accumulated attributes
   * @param children
   *   Accumulated children
   */
  final case class ElementBuilder(
    name: XmlName,
    attributes: Chunk[(XmlName, String)],
    children: Chunk[Xml]
  ) {

    /**
     * Adds an attribute with a string name.
     *
     * @param name
     *   The attribute name
     * @param value
     *   The attribute value
     * @return
     *   A new ElementBuilder with the attribute added
     */
    def attr(name: String, value: String): ElementBuilder =
      attr(XmlName(name), value)

    /**
     * Adds an attribute with an XmlName (possibly namespaced).
     *
     * @param name
     *   The attribute name (possibly with namespace)
     * @param value
     *   The attribute value
     * @return
     *   A new ElementBuilder with the attribute added
     */
    def attr(name: XmlName, value: String): ElementBuilder =
      copy(attributes = attributes :+ (name, value))

    /**
     * Adds a single child node.
     *
     * @param xml
     *   The child node to add
     * @return
     *   A new ElementBuilder with the child added
     */
    def child(xml: Xml): ElementBuilder =
      copy(children = children :+ xml)

    /**
     * Adds multiple child nodes.
     *
     * @param xmls
     *   The child nodes to add
     * @return
     *   A new ElementBuilder with all children added
     */
    def children(xmls: Xml*): ElementBuilder =
      copy(children = children ++ Chunk.from(xmls))

    /**
     * Adds a text node as a child.
     *
     * @param value
     *   The text content
     * @return
     *   A new ElementBuilder with the text node added
     */
    def text(value: String): ElementBuilder =
      child(Xml.Text(value))

    /**
     * Builds and returns the final Element.
     *
     * @return
     *   The constructed Xml.Element
     */
    def build: Xml.Element =
      Xml.Element(name, attributes, children)
  }
}
