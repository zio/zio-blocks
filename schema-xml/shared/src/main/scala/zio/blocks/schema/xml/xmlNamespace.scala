package zio.blocks.schema.xml

import scala.annotation.StaticAnnotation
import zio.blocks.schema.Modifier

/**
 * Specifies the XML namespace for a type.
 *
 * @param uri
 *   The namespace URI (e.g., "http://www.w3.org/2005/Atom")
 * @param prefix
 *   Optional namespace prefix (e.g., "atom"). If empty, uses default namespace.
 */
case class xmlNamespace(uri: String, prefix: String = "") extends StaticAnnotation

object xmlNamespace {
  val configKeyUri    = "xml.namespace.uri"
  val configKeyPrefix = "xml.namespace.prefix"

  def getNamespace(modifiers: Seq[Modifier.Reflect]): Option[(String, String)] =
    modifiers.collectFirst { case Modifier.config(`configKeyUri`, uri) =>
      val prefix = modifiers.collectFirst { case Modifier.config(`configKeyPrefix`, p) =>
        p
      }.getOrElse("")
      (uri, prefix)
    }
}
