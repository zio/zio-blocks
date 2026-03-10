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
      val prefix = modifiers.collectFirst { case Modifier.config(`configKeyPrefix`, p) => p } match {
        case Some(p) => p
        case _       => ""
      }
      (uri, prefix)
    }
}
