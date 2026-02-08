package zio.blocks.schema.xml

/**
 * Represents an XML qualified name with an optional namespace URI.
 *
 * @param localName
 *   The local part of the name (without prefix)
 * @param namespace
 *   Optional namespace URI (prefix is cosmetic and not stored)
 */
final case class XmlName(localName: String, namespace: Option[String] = None) {

  /** Returns true if this name has a namespace. */
  def hasNamespace: Boolean = namespace.isDefined

  /** Returns the namespace URI or empty string if none. */
  def namespaceOrEmpty: String = namespace.getOrElse("")

  /** Creates a new XmlName with the given namespace. */
  def withNamespace(ns: String): XmlName = copy(namespace = Some(ns))

  /** Creates a new XmlName without namespace. */
  def withoutNamespace: XmlName = copy(namespace = None)

  override def toString: String = namespace match {
    case Some(ns) => s"{$ns}$localName"
    case None     => localName
  }
}

object XmlName {

  /** Creates an XmlName with just a local name (no namespace). */
  def apply(localName: String): XmlName = new XmlName(localName, None)

  /** Creates an XmlName with a namespace. */
  def apply(localName: String, namespace: String): XmlName = new XmlName(localName, Some(namespace))

  /**
   * Parses an XmlName from a string in the format "{namespace}localName" or
   * "localName".
   */
  def parse(s: String): XmlName =
    if (s.startsWith("{")) {
      val endBrace = s.indexOf('}')
      if (endBrace > 0) {
        val ns        = s.substring(1, endBrace)
        val localName = s.substring(endBrace + 1)
        XmlName(localName, Some(ns))
      } else {
        XmlName(s)
      }
    } else {
      XmlName(s)
    }
}
