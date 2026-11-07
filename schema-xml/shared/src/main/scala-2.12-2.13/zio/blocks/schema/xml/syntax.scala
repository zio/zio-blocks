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

import zio.blocks.schema.{Schema, SchemaError}

/**
 * Extension methods that encode any value with a [[zio.blocks.schema.Schema]]
 * to XML and decode XML strings and bytes, through the codec that
 * `Schema#derive(XmlFormat)` gives. Import `zio.blocks.schema.xml.syntax._` to
 * use them.
 *
 * Each call looks up the codec with `Schema#getInstance(XmlFormat)`. On Scala
 * 2.13 the methods come from implicit classes, on Scala 3 from extension
 * methods; they behave the same.
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.xml.syntax._
 *
 * case class Profile(user: String, active: Boolean)
 * object Profile {
 *   implicit val schema: Schema[Profile] = Schema.derived
 * }
 *
 * Profile("ada", true).toXmlString // <Profile><user>ada</user><active>true</active></Profile>
 * "<Profile><user>bob</user><active>false</active></Profile>".fromXml[Profile] // Right(Profile(bob,false))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/xml/ XML]]
 */
object syntax {

  /** Adds `toXml`, `toXmlString` and `toXmlBytes` to any value of type `A`. */
  implicit final class XmlValueOps[A](private val self: A) extends AnyVal {

    /**
     * Encodes this value as an [[Xml]] tree.
     *
     * {{{
     * import zio.blocks.schema.xml.syntax._
     *
     * List(3, 5).toXml.print // <items><item>3</item><item>5</item></items>
     * }}}
     */
    def toXml(implicit schema: Schema[A]): Xml = schema.getInstance(XmlFormat).encodeValue(self)

    /**
     * Encodes this value as a compact XML string without an XML declaration.
     *
     * {{{
     * import zio.blocks.schema.xml.syntax._
     *
     * 21.5.toXmlString // <value>21.5</value>
     * }}}
     */
    def toXmlString(implicit schema: Schema[A]): String = XmlWriter.write(toXml, WriterConfig.default)

    /**
     * Encodes this value as an XML string formatted by `config`.
     *
     * {{{
     * import zio.blocks.schema.xml.WriterConfig
     * import zio.blocks.schema.xml.syntax._
     *
     * Map("region" -> "eu").toXmlString(WriterConfig.pretty)
     * // <map>
     * //   <entry>
     * //     <key>region</key>
     * //     <value>eu</value>
     * //   </entry>
     * // </map>
     * }}}
     */
    def toXmlString(config: WriterConfig)(implicit schema: Schema[A]): String = XmlWriter.write(toXml, config)

    /**
     * Encodes this value as compact UTF-8 bytes without an XML declaration.
     *
     * {{{
     * import zio.blocks.schema.xml.syntax._
     *
     * true.toXmlBytes.length // 19, the bytes of <value>true</value>
     * }}}
     */
    def toXmlBytes(implicit schema: Schema[A]): Array[Byte] = schema.getInstance(XmlFormat).encode(self)
  }

  /** Adds `fromXml` to XML strings. */
  implicit final class XmlStringOps(private val self: String) extends AnyVal {

    /**
     * Decodes this XML string as an `A`. Returns a `Left` for malformed XML and
     * for XML that does not match `A`.
     *
     * {{{
     * import zio.blocks.schema.xml.syntax._
     *
     * "<value>8080</value>".fromXml[Int] // Right(8080)
     * }}}
     */
    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }

  /** Adds `fromXml` to XML bytes. */
  implicit final class XmlByteArrayOps(private val self: Array[Byte]) extends AnyVal {

    /**
     * Decodes these bytes, read as UTF-8, as an `A`. Returns a `Left` for
     * malformed XML and for XML that does not match `A`.
     *
     * {{{
     * import zio.blocks.schema.xml.syntax._
     *
     * "<value>eu</value>".getBytes("UTF-8").fromXml[String] // Right(eu)
     * }}}
     */
    def fromXml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(XmlFormat).decode(self)
  }
}
