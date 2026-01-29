# schema-xml Design Guide

Zero-dependency XML support for ZIO Blocks, providing schema-driven serialization, patching, selection, and validation.

## Overview

```
Schema[A] ──derive──► XmlBinaryCodec[A] ──uses──► XmlNode (AST)
                 ▲
                 │
           XmlFormat (BinaryFormat)
                 │
                 └─► XmlBinaryCodecDeriver
```

---

## 1. XML AST (`XmlNode`, `XmlDocument`)

**Purpose:** Strongly-typed, immutable representation of XML documents.

**Design:**

```scala
// Newtypes with validation
final case class NCName private (value: String) extends AnyVal
final case class NamespaceUri private (value: String) extends AnyVal
final case class QName(namespace: Option[NamespaceUri], localName: NCName, prefix: Option[NCName])
final case class XmlAttribute(name: QName, value: String)

// Element content (no Document nesting possible)
sealed trait XmlNode
object XmlNode {
  final case class Element(name: QName, attributes: Chunk[XmlAttribute], children: Chunk[XmlNode]) extends XmlNode
  final case class Text(value: String) extends XmlNode
  final case class CData(value: String) extends XmlNode
  final case class Comment(value: String) extends XmlNode
  final case class ProcessingInstruction(target: NCName, data: String) extends XmlNode
}

// Document-level content (outside root element)
sealed trait XmlMisc
object XmlMisc {
  final case class Comment(value: String) extends XmlMisc
  final case class ProcessingInstruction(target: NCName, data: String) extends XmlMisc
}

sealed abstract class XmlVersion(val value: String)
object XmlVersion {
  case object `1.0` extends XmlVersion("1.0")
  case object `1.1` extends XmlVersion("1.1")
}

final case class XmlDeclaration(
  version: XmlVersion,
  encoding: Option[Encoding],
  standalone: Option[Boolean]
)

final case class XmlDocument(
  declaration: Option[XmlDeclaration],
  prologBefore: Chunk[XmlMisc],
  root: XmlNode.Element,
  prologAfter: Chunk[XmlMisc]
)
```

**References:**
- [XML 1.0 Specification (W3C)](https://www.w3.org/TR/xml/) — Canonical definition of XML syntax
- [XML 1.1 Specification (W3C)](https://www.w3.org/TR/xml11/) — Extended character support
- [Namespaces in XML 1.0](https://www.w3.org/TR/xml-names/) — QName, namespace URI, prefix rules
- [NCName production](https://www.w3.org/TR/xml-names/#NT-NCName) — Valid non-colonized names

---

## 2. Path Navigation (`DynamicOptic` Mapping)

**Purpose:** Reuse existing `DynamicOptic` and `p"..."` interpolator for XML navigation.

**Mapping:**

| Syntax | `DynamicOptic.Node` | XML Semantics |
|--------|---------------------|---------------|
| `.foo` | `Field("foo")` | Child element (record field) |
| `<Foo>` | `Case("Foo")` | Child elements named `<Foo>` (direct) |
| `<<Foo>>` | `DeepCase("Foo")` (new) | Descendant elements named `<Foo>` |
| `[0]` | `AtIndex(0)` | Nth child element |
| `[*]` | `Elements` | All child elements |
| `<*>` | `AllCases` (new?) | All child elements (any name) |
| `{"attr"}` | `AtMapKey("attr")` | Attribute value |
| `{*}` | `MapValues` | All attribute values |
| `{*:}` | `MapKeys` | All attribute names |
| `@attr` | (syntax sugar) | Alias for `{"attr"}` |

**Design considerations:**
- `<<Foo>>` requires new `Node.DeepCase` — semantically "all descendants matching case"
- Consider whether `DeepCase` is useful for `DynamicValue` too (recursive enum search)
- Attribute access via `{...}` repurposes map syntax naturally

**References:**
- [XPath 1.0 (W3C)](https://www.w3.org/TR/xpath/) — Path expression semantics
- [XPath 3.1 (W3C)](https://www.w3.org/TR/xpath-31/) — Modern XPath with maps/arrays
- Existing `DynamicOptic.scala`, `PathParser.scala` in schema module

---

## 3. Parser and Printer

**Purpose:** Zero-dependency streaming XML parser and printer.

**Design:**

```scala
object XmlParser {
  def parse(input: Array[Byte]): Either[XmlError, XmlDocument]
  def parseElement(input: Array[Byte]): Either[XmlError, XmlNode.Element]
  
  // Streaming for large documents
  def parseStream(input: Iterator[Byte]): Iterator[Either[XmlError, XmlEvent]]
}

sealed trait XmlEvent
object XmlEvent {
  case class StartElement(name: QName, attributes: Chunk[XmlAttribute]) extends XmlEvent
  case class EndElement(name: QName) extends XmlEvent
  case class Characters(text: String) extends XmlEvent
  case class CData(text: String) extends XmlEvent
  // ...
}

object XmlPrinter {
  def print(doc: XmlDocument, config: XmlWriterConfig): Array[Byte]
  def print(node: XmlNode, config: XmlWriterConfig): Array[Byte]
}

case class XmlWriterConfig(
  indent: Option[Int],           // None = compact, Some(2) = 2-space indent
  includeDeclaration: Boolean,
  encoding: Encoding,
  normalizeWhitespace: Boolean
)

case class XmlReaderConfig(
  maxDepth: Int,
  maxAttributes: Int,
  maxTextLength: Int,
  preserveWhitespace: Boolean,
  expandEntityReferences: Boolean
)
```

**References:**
- [XML 1.0 §2.1-2.11](https://www.w3.org/TR/xml/#sec-documents) — Document structure
- [XML 1.0 §4.4](https://www.w3.org/TR/xml/#entproc) — Entity handling
- [Canonical XML](https://www.w3.org/TR/xml-c14n/) — Normalization for signing/comparison
- Existing `JsonParser` in schema module for patterns

---

## 4. XML Schema (`XmlSchema`)

**Purpose:** Represent XSD (XML Schema Definition) structures; convert to/from ZIO Schema.

**Design:**

```scala
sealed trait XmlSchema
object XmlSchema {
  case class Element(name: QName, schemaType: XmlSchemaType, minOccurs: Int, maxOccurs: MaxOccurs)
  case class Attribute(name: QName, schemaType: SimpleType, use: AttributeUse, default: Option[String])
  case class ComplexType(content: ContentModel, attributes: Chunk[Attribute])
  case class SimpleType(restriction: SimpleTypeRestriction)
  case class Group(compositor: Compositor, particles: Chunk[XmlSchema])
  // ...
}

sealed trait Compositor
object Compositor {
  case object Sequence extends Compositor  // ordered
  case object Choice extends Compositor    // one-of
  case object All extends Compositor       // unordered
}

sealed trait MaxOccurs
object MaxOccurs {
  case class Bounded(n: Int) extends MaxOccurs
  case object Unbounded extends MaxOccurs
}

sealed trait AttributeUse
object AttributeUse {
  case object Required extends AttributeUse
  case object Optional extends AttributeUse
  case object Prohibited extends AttributeUse
}

// Conversion
object XmlSchema {
  def fromSchema[A](schema: Schema[A]): XmlSchema
  def toSchema(xmlSchema: XmlSchema): Schema[DynamicValue]
}
```

**References:**
- [XML Schema 1.1 Part 1: Structures](https://www.w3.org/TR/xmlschema11-1/) — Complex types, elements, compositors
- [XML Schema 1.1 Part 2: Datatypes](https://www.w3.org/TR/xmlschema11-2/) — Simple types, facets
- [XML Schema Primer](https://www.w3.org/TR/xmlschema-0/) — Introductory guide
- Existing `JsonSchema.scala` for patterns

---

## 5. XML Patch (`XmlPatch`)

**Purpose:** Describe and apply modifications to XML documents.

**Design:**

```scala
sealed trait XmlPatch
object XmlPatch {
  case class Add(selector: DynamicOptic, position: Position, content: Chunk[XmlNode]) extends XmlPatch
  case class Remove(selector: DynamicOptic) extends XmlPatch
  case class Replace(selector: DynamicOptic, content: XmlNode) extends XmlPatch
  case class Rename(selector: DynamicOptic, newName: QName) extends XmlPatch
  case class SetAttribute(selector: DynamicOptic, attribute: XmlAttribute) extends XmlPatch
  case class RemoveAttribute(selector: DynamicOptic, attributeName: QName) extends XmlPatch
  case class Composite(patches: Chunk[XmlPatch]) extends XmlPatch
}

sealed trait Position
object Position {
  case object Before extends Position
  case object After extends Position
  case object Prepend extends Position  // first child
  case object Append extends Position   // last child
}

// Application
extension (doc: XmlDocument) {
  def applyPatch(patch: XmlPatch): Either[XmlError, XmlDocument]
}
```

**References:**
- [RFC 5261: XML Patch Operations](https://www.rfc-editor.org/rfc/rfc5261) — Standard patch format using XPath
- [RFC 7351: XML Patch Media Type](https://www.rfc-editor.org/rfc/rfc7351) — Application/xml-patch+xml
- Existing `JsonPatch` in schema module

---

## 6. XML Selection (`XmlSelection`)

**Purpose:** Navigate and select nodes using `DynamicOptic`, returning zero or more results.

**Design:**

```scala
final case class XmlSelection(result: Either[XmlError, Vector[XmlNode]]) extends AnyVal {
  def select(optic: DynamicOptic): XmlSelection
  def selectElement(name: String): XmlSelection
  def selectAttribute(name: String): XmlSelection
  def selectText: XmlSelection
  def selectIndex(i: Int): XmlSelection
  def selectAll: XmlSelection
  
  def modify(f: XmlNode => XmlNode): Either[XmlError, XmlNode]
  def set(value: XmlNode): Either[XmlError, XmlNode]
  def remove: Either[XmlError, XmlNode]
  
  // Combinators
  def filter(p: XmlNode => Boolean): XmlSelection
  def map[B](f: XmlNode => B): Either[XmlError, Vector[B]]
  def flatMap(f: XmlNode => XmlSelection): XmlSelection
}

object XmlSelection {
  def apply(node: XmlNode): XmlSelection
  def apply(doc: XmlDocument): XmlSelection
}
```

**References:**
- [XPath 1.0 Data Model](https://www.w3.org/TR/xpath/#data-model) — Node types and relationships
- [DOM Level 3 Core](https://www.w3.org/TR/DOM-Level-3-Core/) — Node traversal concepts
- Existing `JsonSelection.scala` in schema module

---

## 7. XML Interpolator

**Purpose:** Compile-time safe XML construction with value splicing.

**Design:**

```scala
// Usage
val name = "Sherlock"
val age = 42
val doc = xml"<Person><Name>$name</Name><Age>$age</Age></Person>"

// With collections
val emails = Chunk("a@b.com", "c@d.com")
val doc2 = xml"<Person><Emails>$emails</Emails></Person>"
// Expands to: <Person><Emails><item>a@b.com</item><item>c@d.com</item></Emails></Person>

// With schema-derived types
case class Address(street: String, city: String)
object Address { implicit val schema: Schema[Address] = DeriveSchema.gen }
val addr = Address("Baker St", "London")
val doc3 = xml"<Person><Address>$addr</Address></Person>"
```

**Splice rules (valid positions):**
- Text content: `<Name>$value</Name>` — requires `XmlEncoder[A]`
- Attribute value: `<Person name=$value>` — requires `XmlEncoder[A]` + `Keyable[A]`
- Element content: `<Parent>$element</Parent>` — requires `XmlEncoder[A]`
- Repeated elements: collection types expand to siblings

**Implementation:**

```scala
trait XmlEncoder[A] {
  def encode(a: A): XmlNode
}

object XmlEncoder {
  implicit val string: XmlEncoder[String] = s => XmlNode.Text(s)
  implicit val int: XmlEncoder[Int] = i => XmlNode.Text(i.toString)
  implicit def chunk[A: XmlEncoder]: XmlEncoder[Chunk[A]] = ...
  implicit def fromSchema[A: Schema]: XmlEncoder[A] = ...
}

// Macro
implicit class XmlInterpolator(sc: StringContext) {
  def xml(args: XmlEncoder[_]*): XmlNode = macro XmlMacros.xmlImpl
}
```

**References:**
- [Scala String Interpolation](https://docs.scala-lang.org/overviews/core/string-interpolation.html)
- [Scala 3 Macros](https://docs.scala-lang.org/scala3/guides/macros/macros.html)
- Existing `JsonInterpolator`, `PathMacros.scala` in schema module

---

## 8. XML Binary Codec (`XmlBinaryCodec`)

**Purpose:** Schema-driven serialization/deserialization between `A` and XML bytes.

**Design:**

```scala
abstract class XmlBinaryCodec[A] extends BinaryCodec[A] {
  def encode(value: A, config: XmlWriterConfig): Array[Byte]
  def decode(bytes: Array[Byte], config: XmlReaderConfig): Either[XmlError, A]
  
  def toXmlSchema: XmlSchema
  def toXmlNode(value: A): XmlNode.Element
  def fromXmlNode(node: XmlNode.Element): Either[XmlError, A]
}

object XmlBinaryCodec {
  // Primitives
  implicit val string: XmlBinaryCodec[String]
  implicit val int: XmlBinaryCodec[Int]
  implicit val boolean: XmlBinaryCodec[Boolean]
  // ...
}
```

**References:**
- Existing `JsonBinaryCodec.scala` in schema module

---

## 9. XML Format (`XmlFormat`)

**Purpose:** Entry point tying MIME type to deriver.

**Design:**

```scala
object XmlFormat extends BinaryFormat("application/xml", XmlBinaryCodecDeriver)

object XmlBinaryCodecDeriver extends Deriver[XmlBinaryCodec] {
  // Derive codec from Schema
  def derive[A](schema: Schema[A]): XmlBinaryCodec[A]
}
```

**Default encoding rules:**

| Scala Type | XML Mapping |
|------------|-------------|
| Primitive field | Child element with text content |
| `Option[A]` | Omit element if `None` |
| `Seq[A]` / `Chunk[A]` | Repeated sibling elements |
| Record (case class) | Wrapper element with children |
| Enum/Sealed trait | Wrapper element, case name as child tag |
| `Map[String, A]` | `<entry key="...">value</entry>` |

**Configuration via `Modifier.config`:**

| Config Key | Values | Effect |
|------------|--------|--------|
| `xml.attribute` | `"true"` | Serialize field as attribute |
| `xml.text` | `"true"` | Serialize as text content (unwrapped) |
| `xml.wrapped` | `"items"` | Wrap collection in container element |
| `xml.name` | `"Foo"` | Override element/attribute name |
| `xml.namespace` | `"http://..."` | Set namespace URI |
| `xml.prefix` | `"ns"` | Set namespace prefix |

**References:**
- [JAXB Mapping](https://jakarta.ee/specifications/xml-binding/4.0/) — Java XML binding conventions
- Existing `JsonBinaryCodecDeriver.scala`, `JsonFormat` in schema module

---

## 10. Supporting Types

### XmlError

```scala
final case class XmlError(message: String, path: DynamicOptic, position: Option[SourcePosition])
final case class SourcePosition(line: Int, column: Int, offset: Int)
```

### XmlNodeType

```scala
sealed trait XmlNodeType extends (XmlNode => Boolean)
object XmlNodeType {
  case object Element extends XmlNodeType
  case object Text extends XmlNodeType
  case object CData extends XmlNodeType
  case object Comment extends XmlNodeType
  case object ProcessingInstruction extends XmlNodeType
}
```

### XmlMergeStrategy

```scala
sealed trait XmlMergeStrategy extends ((DynamicOptic, XmlNode, XmlNode) => XmlNode)
object XmlMergeStrategy {
  case object TakeLeft extends XmlMergeStrategy
  case object TakeRight extends XmlMergeStrategy
  case object DeepMerge extends XmlMergeStrategy  // merge children recursively
  case object Concat extends XmlMergeStrategy     // concatenate children
}
```

---

## 11. Project Structure

```
schema-xml/
├── shared/src/main/scala/zio/blocks/schema/xml/
│   ├── Xml.scala                    # XmlNode, XmlDocument, QName, etc.
│   ├── XmlParser.scala              # Zero-dep parser
│   ├── XmlPrinter.scala             # Serialization
│   ├── XmlSchema.scala              # XSD representation
│   ├── XmlPatch.scala               # Patch operations
│   ├── XmlSelection.scala           # Navigation/selection
│   ├── XmlBinaryCodec.scala         # Base codec
│   ├── XmlBinaryCodecDeriver.scala  # Schema-driven derivation
│   ├── XmlEncoder.scala             # For interpolator
│   ├── XmlDecoder.scala             # For interpolator
│   ├── XmlError.scala               # Error types
│   ├── XmlReaderConfig.scala
│   ├── XmlWriterConfig.scala
│   └── XmlMergeStrategy.scala
├── shared/src/main/scala-2/zio/blocks/schema/xml/
│   ├── XmlInterpolator.scala        # xml"..." macro
│   └── XmlMacros.scala
├── shared/src/main/scala-3/zio/blocks/schema/xml/
│   ├── XmlInterpolator.scala
│   └── XmlMacros.scala
└── shared/src/test/scala/zio/blocks/schema/xml/
    ├── XmlSpec.scala
    ├── XmlParserSpec.scala
    ├── XmlSchemaSpec.scala
    ├── XmlPatchSpec.scala
    ├── XmlSelectionSpec.scala
    ├── XmlBinaryCodecSpec.scala
    └── XmlInterpolatorSpec.scala
```

---

## 12. Implementation Order

1. **XmlNode AST** — Foundation for everything
2. **XmlParser / XmlPrinter** — Enables testing
3. **XmlSelection** — Navigation primitives
4. **XmlEncoder / XmlDecoder** — Low-level codec traits
5. **XmlBinaryCodec + XmlBinaryCodecDeriver** — Schema integration
6. **XmlFormat** — BinaryFormat registration
7. **XmlPatch** — Modification operations
8. **XmlSchema** — XSD generation/consumption
9. **XmlInterpolator** — Developer ergonomics

---

## 13. Reference Summary

| Component | Primary References |
|-----------|-------------------|
| XML AST | [XML 1.0](https://www.w3.org/TR/xml/), [Namespaces](https://www.w3.org/TR/xml-names/) |
| Parser | [XML 1.0 Grammar](https://www.w3.org/TR/xml/#sec-documents) |
| Path/Selection | [XPath 1.0](https://www.w3.org/TR/xpath/), [XPath 3.1](https://www.w3.org/TR/xpath-31/) |
| XmlSchema | [XSD 1.1 Structures](https://www.w3.org/TR/xmlschema11-1/), [XSD 1.1 Datatypes](https://www.w3.org/TR/xmlschema11-2/) |
| XmlPatch | [RFC 5261](https://www.rfc-editor.org/rfc/rfc5261), [RFC 7351](https://www.rfc-editor.org/rfc/rfc7351) |
| Canonicalization | [Canonical XML](https://www.w3.org/TR/xml-c14n/) |
| Internal patterns | `schema/shared/src/main/scala/zio/blocks/schema/json/` |
