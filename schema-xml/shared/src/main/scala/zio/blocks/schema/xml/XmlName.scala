package zio.blocks.schema.xml

/**
 * Represents an XML qualified name with an optional namespace URI and prefix.
 *
 * @param localName
 *   The local part of the name (without prefix)
 * @param prefix
 *   Optional namespace prefix (used for display purposes)
 * @param namespace
 *   Optional namespace URI
 */
final case class XmlName(localName: String, prefix: Option[String] = None, namespace: Option[String] = None) {

  /** Returns true if this name has a namespace. */
  def hasNamespace: Boolean = namespace.isDefined

  /** Returns the namespace URI or empty string if none. */
  def namespaceOrEmpty: String = namespace match {
    case Some(ns) => ns
    case _        => ""
  }

  /** Creates a new XmlName with the given namespace. */
  def withNamespace(ns: String): XmlName = copy(namespace = new Some(ns))

  /** Creates a new XmlName without namespace. */
  def withoutNamespace: XmlName = copy(namespace = None)

  /** Creates a new XmlName with the given prefix. */
  def withPrefix(p: String): XmlName = copy(prefix = new Some(p))

  /** Creates a new XmlName without prefix. */
  def withoutPrefix: XmlName = copy(prefix = None)

  /**
   * Returns the qualified name as "prefix:localName" or just "localName" if no
   * prefix.
   */
  def qualifiedName: String = prefix match {
    case Some(p) => s"$p:$localName"
    case _       => localName
  }

  override def toString: String = namespace match {
    case Some(ns) =>
      prefix match {
        case Some(p) => s"{$ns}$p:$localName"
        case _       => s"{$ns}$localName"
      }
    case _ => qualifiedName
  }
}

object XmlName {

  /** Creates an XmlName with just a local name (no namespace or prefix). */
  def apply(localName: String): XmlName = new XmlName(localName, None, None)

  /** Creates an XmlName with a namespace (backward compatible). */
  def apply(localName: String, namespace: String): XmlName = new XmlName(localName, None, new Some(namespace))

  /**
   * Parses an XmlName from a string in the format "{namespace}localName",
   * "{namespace}prefix:localName", "prefix:localName", or "localName".
   */
  def parse(s: String): XmlName =
    if (s.startsWith("{")) {
      val endBrace = s.indexOf('}')
      if (endBrace > 0) {
        val ns   = s.substring(1, endBrace)
        val rest = s.substring(endBrace + 1)
        if (rest.contains(":")) {
          val colonIdx = rest.indexOf(':')
          new XmlName(rest.substring(colonIdx + 1), new Some(rest.substring(0, colonIdx)), new Some(ns))
        } else new XmlName(rest, None, new Some(ns))
      } else new XmlName(s, None, None)
    } else if (s.contains(":")) {
      val colonIdx = s.indexOf(':')
      new XmlName(s.substring(colonIdx + 1), new Some(s.substring(0, colonIdx)), None)
    } else new XmlName(s, None, None)
}
