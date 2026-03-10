/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
