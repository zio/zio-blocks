package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk

/**
 * A sealed trait representing the type of an XML node.
 *
 * Each case provides two type members:
 *   - `Type`: The corresponding [[Xml]] subtype (e.g., `Xml.Element`)
 *   - `Unwrap`: The underlying Scala type of the value
 */
sealed trait XmlType extends (Xml => Boolean) {

  /** The corresponding [[Xml]] subtype for this XML type. */
  type Type <: Xml

  /** The underlying Scala type of the value contained in this XML type. */
  type Unwrap

  /** Returns the type index for ordering. */
  def typeIndex: Int

  /** Returns true if the given XML node is of this type. */
  override def apply(xml: Xml): Boolean = xml.xmlType == this
}

object XmlType {
  case object Element extends XmlType {
    override final type Type   = Xml.Element
    override final type Unwrap = (XmlName, Chunk[(XmlName, String)], Chunk[Xml])
    val typeIndex = 0
  }

  case object Text extends XmlType {
    override final type Type   = Xml.Text
    override final type Unwrap = String
    val typeIndex = 1
  }

  case object CData extends XmlType {
    override final type Type   = Xml.CData
    override final type Unwrap = String
    val typeIndex = 2
  }

  case object Comment extends XmlType {
    override final type Type   = Xml.Comment
    override final type Unwrap = String
    val typeIndex = 3
  }

  case object ProcessingInstruction extends XmlType {
    override final type Type   = Xml.ProcessingInstruction
    override final type Unwrap = (String, String)
    val typeIndex = 4
  }
}
