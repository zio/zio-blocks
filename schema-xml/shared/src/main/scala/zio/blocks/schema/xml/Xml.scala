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

import zio.blocks.chunk.Chunk

/**
 * An immutable XML node: an element, text, a CDATA section, a comment or a
 * processing instruction.
 *
 * Use `Xml` to build, inspect or rewrite XML without a schema: parse a string
 * with [[XmlReader]], navigate it with [[XmlSelection]] (`select`), change it
 * with [[XmlPatch]], and render it with `print`. A derived [[XmlCodec]] maps
 * your own types to and from this tree. There is no node for a document type
 * declaration or for an entity reference.
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 *
 * val order = Xml.Element(
 *   XmlName("order"),
 *   Chunk((XmlName("id"), "A-17")),
 *   Chunk(Xml.Element("item", Xml.Text("keyboard")), Xml.Comment(" ships Monday "))
 * )
 *
 * order.print // <order id="A-17"><item>keyboard</item><!-- ships Monday --></order>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/xml/ XML]]
 */
sealed trait Xml {

  /** Returns the [[XmlType]] of this XML node. */
  def xmlType: XmlType

  /**
   * Returns true if this XML node is of the specified type.
   *
   * {{{
   * val node: Xml = Xml.Text("21.5")
   *
   * node.is(XmlType.Text)    // true
   * node.is(XmlType.Element) // false
   * }}}
   */
  def is(xmlType: XmlType): Boolean = this.xmlType eq xmlType

  /**
   * Narrows this XML node to the specified type, returning `Some` if the types
   * match or `None` otherwise. The return type is path-dependent on the
   * `xmlType` parameter.
   *
   * {{{
   * val node: Xml = Xml.Text("21.5")
   *
   * node.as(XmlType.Text)    // Some(Text(21.5))
   * node.as(XmlType.Element) // None
   * }}}
   */
  def as(xmlType: XmlType): Option[xmlType.Type]

  /**
   * Extracts the underlying value from this XML if it matches the specified
   * type. The return type is path-dependent on the `xmlType` parameter: a
   * `String` for text, CDATA and comments, a `(target, data)` pair for a
   * processing instruction, and a `(name, attributes, children)` triple for an
   * element.
   *
   * {{{
   * val pi: Xml = Xml.ProcessingInstruction("xml-stylesheet", "href=\"feed.css\"")
   *
   * pi.unwrap(XmlType.ProcessingInstruction) // Some((xml-stylesheet,href="feed.css"))
   * pi.unwrap(XmlType.Text)                  // None
   * }}}
   */
  def unwrap(xmlType: XmlType): Option[xmlType.Unwrap]

  /**
   * Returns the index of this node's case: 0 for an element, 1 for text, 2 for
   * CDATA, 3 for a comment and 4 for a processing instruction. It equals
   * `xmlType.typeIndex`.
   */
  def typeIndex: Int

  /**
   * Renders this XML to a compact string (no indentation, no XML declaration).
   * The writer escapes `&`, `<` and `>` in text, and writes an element without
   * children as a self-closing tag.
   *
   * {{{
   * val reading = Xml.Element("reading", Xml.Text("3 < 4 & 5"))
   *
   * reading.print // <reading>3 &lt; 4 &amp; 5</reading>
   * }}}
   */
  def print: String = XmlWriter.write(this, WriterConfig.default)

  /**
   * Renders this XML to a pretty-printed string (2-space indentation). An
   * element whose only child is text stays on one line. In mixed content (text
   * next to other nodes), the indentation adds whitespace around the text, so
   * the text changes.
   *
   * {{{
   * val sensor = Xml.Element(
   *   "sensor",
   *   Xml.Element("id", Xml.Text("t-1")),
   *   Xml.Element("celsius", Xml.Text("21.5"))
   * )
   *
   * sensor.printPretty
   * // <sensor>
   * //   <id>t-1</id>
   * //   <celsius>21.5</celsius>
   * // </sensor>
   * }}}
   */
  def printPretty: String = XmlWriter.write(this, WriterConfig.pretty)

  /**
   * Renders this XML to a string with custom configuration.
   *
   * {{{
   * Xml.Element("ping").print(WriterConfig.withDeclaration)
   * // <?xml version="1.0" encoding="UTF-8"?><ping/>
   * }}}
   */
  def print(config: WriterConfig): String = XmlWriter.write(this, config)

  /**
   * Creates an XmlSelection from this XML node for fluent navigation. The
   * selection starts at this node, so `get` looks at its children.
   *
   * {{{
   * val profile = XmlReader.read("<user><name>Ada</name><role>admin</role></user>")
   *
   * profile.select.get("role").text // Right(admin)
   * }}}
   */
  def select: XmlSelection = XmlSelection.succeed(this)
}

/** The cases of [[Xml]] and constructors for elements. */
object Xml {

  // ─────────────────────────────────────────────────────────────────────────
  // ADT Cases
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Represents an XML element with a name, attributes, and child nodes.
   * Attributes and children keep their order. The writer adds an `xmlns`
   * declaration when `name` has a namespace.
   */
  final case class Element(
    name: XmlName,
    attributes: Chunk[(XmlName, String)],
    children: Chunk[Xml]
  ) extends Xml {
    override def xmlType: XmlType = XmlType.Element

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Element) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Element) new Some((name, attributes, children).asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 0
  }

  /** Constructors for [[Xml.Element]]. */
  object Element {

    /** An element named `root` with no attributes and no children. */
    val empty: Element = new Element(new XmlName("root"), Chunk.empty, Chunk.empty)

    /** Creates an element with just a name (no attributes or children). */
    def apply(name: String): Element = new Element(new XmlName(name), Chunk.empty, Chunk.empty)

    /** Creates an element with a name and children. */
    def apply(name: String, children: Xml*): Element =
      new Element(new XmlName(name), Chunk.empty, Chunk.from(children))

    /**
     * Creates an element with a name and a namespace URI. The second `String`
     * is the namespace, not text content: use `Element(name, Xml.Text(...))`
     * for text.
     */
    def apply(name: String, namespace: String): Element =
      new Element(XmlName(name, namespace), Chunk.empty, Chunk.empty)
  }

  /**
   * Represents character data (text content) within an XML element. `value`
   * holds the unescaped text; the writer escapes `&`, `<` and `>`.
   */
  final case class Text(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.Text

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Text) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Text) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 1
  }

  /**
   * Represents a CDATA section containing unparsed character data. The writer
   * copies `value` between `<![CDATA[` and `]]>` without escaping; a `value`
   * that contains `]]>` produces malformed XML (a known bug).
   */
  final case class CData(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.CData

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.CData) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.CData) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 2
  }

  /**
   * Represents an XML comment. The writer copies `value` between `<!--` and
   * `-->` without checking it for `--`.
   */
  final case class Comment(value: String) extends Xml {
    override def xmlType: XmlType = XmlType.Comment

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.Comment) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.Comment) new Some(value.asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 3
  }

  /**
   * Represents an XML processing instruction, written as `<?target data?>`
   * (without the space when `data` is empty).
   */
  final case class ProcessingInstruction(target: String, data: String) extends Xml {
    override def xmlType: XmlType = XmlType.ProcessingInstruction

    override def as(xmlType: XmlType): Option[xmlType.Type] =
      if (xmlType eq XmlType.ProcessingInstruction) new Some(this.asInstanceOf[xmlType.Type])
      else None

    override def unwrap(xmlType: XmlType): Option[xmlType.Unwrap] =
      if (xmlType eq XmlType.ProcessingInstruction) new Some((target, data).asInstanceOf[xmlType.Unwrap])
      else None

    override def typeIndex: Int = 4
  }
}
