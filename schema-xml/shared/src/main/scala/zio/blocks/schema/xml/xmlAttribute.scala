package zio.blocks.schema.xml

import scala.annotation.StaticAnnotation
import scala.annotation.meta.field
import zio.blocks.schema.Modifier

/**
 * Marks a field to be encoded as an XML attribute instead of a child element.
 *
 * @param name
 *   Optional custom name for the attribute. If empty, the field name is used.
 */
@field case class xmlAttribute(name: String = "") extends StaticAnnotation

object xmlAttribute {
  val configKey = "xml.attribute"

  def isXmlAttribute(modifiers: Seq[Modifier.Term]): Option[String] =
    modifiers.collectFirst { case Modifier.config(`configKey`, value) => value }
}
