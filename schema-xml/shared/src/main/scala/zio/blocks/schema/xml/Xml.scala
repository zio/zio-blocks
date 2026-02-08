package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk

/**
 * A sealed trait representing an XML node.
 *
 * The `Xml` type provides a complete representation of XML data with five
 * possible cases: Element, Text, CData, Comment, and ProcessingInstruction.
 */
sealed trait Xml {

  /** Returns the [[XmlType]] of this XML node. */
  def xmlType: XmlType

  /**
   * Returns true if this XML node is of the specified type.
   */
  def is(xmlType: XmlType): Boolean = this.xmlType eq xmlType

  /**
   * Narrows this XML node to the specified type, returning `Some` if the types
   * match or `None` otherwise. The return type is path-dependent on the
   * `xmlType` parameter.
   */
  def as(xmlType: XmlType): Option[xmlType.Type]

  /**
   * Extracts the underlying value from this XML if it matches the specified
   * type. The return type is path-dependent on the `xmlType` parameter.
   */
  def unwrap(xmlType: XmlType): Option[xmlType.Unwrap]

  /**
   * Returns the type index for ordering.
   */
  def typeIndex: Int
}

object Xml {

  // ─────────────────────────────────────────────────────────────────────────
  // ADT Cases
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Represents an XML element with a name, attributes, and child nodes.
   */
  final case class Element(
    name: XmlName,
    attributes: Chunk[(XmlName, String)],
    children: Chunk[Xml]
  ) extends Xml {
    override def xmlType: XmlType = XmlType.Element

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Element) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Element)
        new Some((name, attributes, children).asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 0
  }

  object Element {

    /** Creates an empty element with the given name. */
    val empty: Element = Element(XmlName("root"), Chunk.empty, Chunk.empty)

    /** Creates an element with just a name (no attributes or children). */
    def apply(name: String): Element =
      Element(XmlName(name), Chunk.empty, Chunk.empty)

    /** Creates an element with a name and children. */
    def apply(name: String, children: Xml*): Element =
      Element(XmlName(name), Chunk.empty, Chunk.from(children))

    /** Creates an element with a name and namespace. */
    def apply(name: String, namespace: String): Element =
      Element(XmlName(name, namespace), Chunk.empty, Chunk.empty)
  }

  /**
   * Represents character data (text content) within an XML element.
   */
  final case class Text(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.Text

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Text) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Text) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 1
  }

  /**
   * Represents a CDATA section containing unparsed character data.
   */
  final case class CData(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.CData

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.CData) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.CData) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 2
  }

  /**
   * Represents an XML comment.
   */
  final case class Comment(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.Comment

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Comment) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Comment) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 3
  }

  /**
   * Represents an XML processing instruction.
   */
  final case class ProcessingInstruction(target: String, data: String) extends Xml {
    override def xmlType: XmlType = XmlType.ProcessingInstruction

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.ProcessingInstruction)
        new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.ProcessingInstruction)
        new Some((target, data).asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 4
  }
}
