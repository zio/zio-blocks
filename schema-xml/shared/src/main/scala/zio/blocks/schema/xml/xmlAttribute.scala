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
 * Intended to mark a field to be encoded as an XML attribute instead of a child
 * element.
 *
 * `Schema.derived` ignores this annotation (a known bug in 0.0.56): it records
 * only [[zio.blocks.schema.Modifier]] annotations, so a field marked
 * `@xmlAttribute()` is still encoded as a child element. The derived
 * [[XmlCodec]] reads the modifier `Modifier.config("xml.attribute", name)`
 * instead; put that on the field to get an attribute. An empty `name` uses the
 * field name. On decoding, a missing attribute becomes `None` for an `Option`
 * field and fails with `Missing required attribute: <name>` otherwise; both
 * ignore a default value the field declares (a known bug). A record nested in
 * another record, or a sealed-trait case, whose fields are all attributes
 * encodes correctly but does not decode back: a required attribute fails with
 * `Missing required attribute` and an `Option` attribute silently becomes
 * `None` (a known bug).
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Sensor(
 *   @Modifier.config("xml.attribute", "") id: String,
 *   @Modifier.config("xml.attribute", "unit") scale: String,
 *   reading: Double
 * )
 * object Sensor {
 *   implicit val schema: Schema[Sensor] = Schema.derived
 * }
 *
 * Schema[Sensor].derive(XmlFormat).encodeToString(Sensor("t-1", "C", 21.5))
 * // <Sensor id="t-1" unit="C"><reading>21.5</reading></Sensor>
 * }}}
 *
 * @param name
 *   Optional custom name for the attribute. If empty, the field name is used.
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/xml/ XML]]
 */
@field case class xmlAttribute(name: String = "") extends StaticAnnotation

/** The modifier key that marks an attribute field. */
object xmlAttribute {

  /**
   * The `Modifier.config` key, `"xml.attribute"`, that the derived codec reads.
   */
  val configKey = "xml.attribute"

  /**
   * Returns the value of the first `Modifier.config("xml.attribute", value)` in
   * `modifiers`, or `None` if there is none. The value is returned as written,
   * so an empty string means "use the field name".
   */
  def isXmlAttribute(modifiers: Seq[Modifier.Term]): Option[String] =
    modifiers.collectFirst { case Modifier.config(`configKey`, value) => value }
}
