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
 * Intended to specify the XML namespace for a type.
 *
 * `Schema.derived` ignores this annotation (a known bug in 0.0.56): it records
 * only [[zio.blocks.schema.Modifier]] annotations, so a record marked
 * `@xmlNamespace(...)` is encoded without a namespace. The derived [[XmlCodec]]
 * reads the modifiers `Modifier.config("xml.namespace.uri", uri)` and
 * `Modifier.config("xml.namespace.prefix", prefix)` on the record instead. With
 * a prefix, the record element is written as `prefix:Name` with an
 * `xmlns:prefix` attribute; without one, it gets an `xmlns` attribute. Decoding
 * does not check the namespace.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * @Modifier.config("xml.namespace.uri", "http://example.com/inventory")
 * @Modifier.config("xml.namespace.prefix", "inv")
 * case class Stock(sku: String, count: Int)
 * object Stock {
 *   implicit val schema: Schema[Stock] = Schema.derived
 * }
 *
 * Schema[Stock].derive(XmlFormat).encodeToString(Stock("KB-01", 12))
 * // <inv:Stock xmlns:inv="http://example.com/inventory"><sku>KB-01</sku><count>12</count></inv:Stock>
 * }}}
 *
 * @param uri
 *   The namespace URI (e.g., "http://www.w3.org/2005/Atom")
 * @param prefix
 *   Optional namespace prefix (e.g., "atom"). If empty, uses default namespace.
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/xml/ XML]]
 */
case class xmlNamespace(uri: String, prefix: String = "") extends StaticAnnotation

/** The modifier keys that set a record's namespace. */
object xmlNamespace {

  /**
   * The `Modifier.config` key, `"xml.namespace.uri"`, for the namespace URI.
   */
  val configKeyUri = "xml.namespace.uri"

  /** The `Modifier.config` key, `"xml.namespace.prefix"`, for the prefix. */
  val configKeyPrefix = "xml.namespace.prefix"

  /**
   * Returns the namespace URI and prefix set by `Modifier.config` entries in
   * `modifiers`, or `None` if there is no URI. The prefix is `""` when only the
   * URI is set.
   */
  def getNamespace(modifiers: Seq[Modifier.Reflect]): Option[(String, String)] =
    modifiers.collectFirst { case Modifier.config(`configKeyUri`, uri) =>
      val prefix = modifiers.collectFirst { case Modifier.config(`configKeyPrefix`, p) => p } match {
        case Some(p) => p
        case _       => ""
      }
      (uri, prefix)
    }
}
