---
id: xml
title: "XML"
---

Zero-dependency XML codec for ZIO Blocks Schema with cross-platform support.

## Overview

The schema-xml module provides automatic XML codec derivation for any type with a `Schema`. It includes a complete XML AST, fluent navigation API, and support for XML-specific features like attributes and namespaces.

Key features:

- **Zero Dependencies**: No external XML libraries required
- **Cross-Platform**: Full support for JVM and Scala.js
- **Schema-Based**: Automatic codec derivation from Schema definitions
- **XML AST**: Complete representation of XML documents
- **Fluent API**: Navigation and transformation with XmlSelection
- **Attributes**: First-class support for XML attributes via annotations
- **Namespaces**: XML namespace support with prefix handling

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-xml" % "0.0.14"
```

## Basic Usage

### Deriving Codecs

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive XML codec using the unified format API
val codec = Schema[Person].derive(XmlFormat)
```

### Encoding to XML

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)
val person = Person("Alice", 30)

// Encode to XML bytes
val bytes: Array[Byte] = codec.encode(person)

// Encode to XML string
val xmlString: String = codec.encodeToString(person)
// <Person><name>Alice</name><age>30</age></Person>

// Encode to pretty-printed XML
val prettyXml = codec.encodeToString(person, WriterConfig.pretty)
// <Person>
//   <name>Alice</name>
//   <age>30</age>
// </Person>
```

### Decoding from XML

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)

// Decode from XML string
val xml = "<Person><name>Alice</name><age>30</age></Person>"
val result: Either[SchemaError, Person] = codec.decode(xml)
// Right(Person("Alice", 30))

// Decode from bytes
val bytes = xml.getBytes("UTF-8")
val fromBytes: Either[SchemaError, Person] = codec.decode(bytes)
```

## XML AST

The `Xml` ADT represents all valid XML node types:

```
Xml
 ├── Xml.Element          (element with name, attributes, children)
 ├── Xml.Text             (character data)
 ├── Xml.CData            (unparsed character data)
 ├── Xml.Comment          (XML comment)
 └── Xml.ProcessingInstruction (processing instruction)
```

### Creating XML Nodes

```scala mdoc:compile-only
import zio.blocks.schema.xml._
import zio.blocks.chunk.Chunk

// Create elements
val simple = Xml.Element("person")
val withChildren = Xml.Element("person", Xml.Text("Alice"))

// Create with XmlName (for namespaces)
val namespaced = Xml.Element(
  XmlName("person", "http://example.com"),
  Chunk.empty,
  Chunk.empty
)

// Create text nodes
val text = Xml.Text("Hello, World!")
val cdata = Xml.CData("<script>...</script>")

// Create comments and processing instructions
val comment = Xml.Comment("This is a comment")
val pi = Xml.ProcessingInstruction("xml-stylesheet", "href=\"style.css\"")
```

### XmlName

`XmlName` represents an element or attribute name with optional namespace:

```scala mdoc:compile-only
import zio.blocks.schema.xml.XmlName

// Local name only
val simple = XmlName("person")
simple.localName     // "person"
simple.namespace     // ""
simple.prefix        // ""

// With namespace URI
val ns = XmlName("person", "http://example.com/ns")

// With prefix (for prefixed elements like atom:feed)
val prefixed = XmlName("feed", Some("atom"), None)
prefixed.localName              // "feed"
prefixed.prefix.contains("atom") // true
```

## XmlBuilder

Construct XML documents programmatically with a fluent API:

```scala mdoc:compile-only
import zio.blocks.schema.xml._

// Build an element with attributes and children
val doc = XmlBuilder.element("person")
  .attr("id", "123")
  .attr("status", "active")
  .child(XmlBuilder.element("name").text("Alice").build)
  .child(XmlBuilder.element("age").text("30").build)
  .build

// Result:
// <person id="123" status="active">
//   <name>Alice</name>
//   <age>30</age>
// </person>

// Create other node types
val textNode = XmlBuilder.text("content")
val cdataNode = XmlBuilder.cdata("<![CDATA[raw content]]>")
val commentNode = XmlBuilder.comment("comment text")
```

## Configuration

### WriterConfig

Controls XML output formatting:

```scala mdoc:compile-only
import zio.blocks.schema.xml.WriterConfig

// Compact output (default)
val compact = WriterConfig.default
// <Person><name>Alice</name></Person>

// Pretty-printed with 2-space indentation
val pretty = WriterConfig.pretty
// <Person>
//   <name>Alice</name>
// </Person>

// With XML declaration
val withDecl = WriterConfig.withDeclaration
// <?xml version="1.0" encoding="UTF-8"?>
// <Person><name>Alice</name></Person>

// Custom configuration
val custom = WriterConfig(
  indentStep = 4,
  includeDeclaration = true,
  encoding = "UTF-8"
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `indentStep` | `0` | Spaces per indentation level (0 = compact) |
| `includeDeclaration` | `false` | Include XML declaration |
| `encoding` | `"UTF-8"` | Character encoding in declaration |

### ReaderConfig

Controls XML parsing behavior and security limits:

```scala mdoc:compile-only
import zio.blocks.schema.xml.ReaderConfig

// Default configuration
val default = ReaderConfig.default

// Custom limits
val custom = ReaderConfig(
  maxDepth = 100,
  maxAttributes = 50,
  maxTextLength = 1000000,
  preserveWhitespace = true
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `maxDepth` | `1000` | Maximum element nesting depth |
| `maxAttributes` | `1000` | Maximum attributes per element |
| `maxTextLength` | `10000000` | Maximum text content length |
| `preserveWhitespace` | `false` | Preserve whitespace in text nodes |

## Attributes

Encode case class fields as XML attributes using the `@xmlAttribute` annotation:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(
  @xmlAttribute() id: String,
  @xmlAttribute("status") active: String,
  name: String,
  age: Int
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)
val person = Person("123", "active", "Alice", 30)
val xml = codec.encodeToString(person)
// <Person id="123" status="active">
//   <name>Alice</name>
//   <age>30</age>
// </Person>
```

The `@xmlAttribute` annotation accepts an optional custom name:

- `@xmlAttribute()` - Uses the field name as the attribute name
- `@xmlAttribute("customName")` - Uses the provided name as the attribute name

## Namespaces

Support for XML namespaces with the `@xmlNamespace` annotation:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Feed(
  title: String,
  updated: String
)

object Feed {
  implicit val schema: Schema[Feed] = Schema.derived
}

val codec = Schema[Feed].derive(XmlFormat)
val feed = Feed("My Blog", "2024-01-01T00:00:00Z")
val xml = codec.encodeToString(feed)
// <atom:Feed xmlns:atom="http://www.w3.org/2005/Atom">
//   <title>My Blog</title>
//   <updated>2024-01-01T00:00:00Z</updated>
// </atom:Feed>
```

Without a prefix (default namespace):

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

@xmlNamespace(uri = "http://www.w3.org/2005/Atom")
case class Feed(title: String)

object Feed {
  implicit val schema: Schema[Feed] = Schema.derived
}

val codec = Schema[Feed].derive(XmlFormat)
val feed = Feed("My Blog")
val xml = codec.encodeToString(feed)
// <Feed xmlns="http://www.w3.org/2005/Atom">
//   <title>My Blog</title>
// </Feed>
```

## XmlSelection

`XmlSelection` provides a fluent API for navigating and querying XML structures:

### Navigation

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val xml = XmlReader.read("""
<library>
  <books>
    <book id="1">
      <title>Functional Programming</title>
      <author>Alice</author>
    </book>
    <book id="2">
      <title>Advanced Scala</title>
      <author>Bob</author>
    </book>
  </books>
</library>
""").toOption.get

// Navigate to child elements
val books = xml.select.get("library").get("books")

// Navigate by index
val firstBook = books.get("book")(0)

// Extract text content
val title: Either[XmlError, String] = firstBook.get("title").text
// Right("Functional Programming")

// Navigate descendants (recursive search)
val allTitles = xml.select.descendant("title")
```

### Filtering

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val selection: XmlSelection = ???

// Filter by type
val elements = selection.elements
val texts = selection.texts
val comments = selection.comments

// Custom filtering
val filtered = selection.filter(xml => xml.is(XmlType.Element))
```

### Terminal Operations

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val selection: XmlSelection = ???

// Get single value (fails if not exactly one)
val one: Either[XmlError, Xml] = selection.one

// Get any value (first of many)
val any: Either[XmlError, Xml] = selection.any

// Get all values as a single XML element
val all: Either[XmlError, Xml] = selection.all

// Convert to chunk
val chunk = selection.toChunk

// Extract text content
val text: Either[XmlError, String] = selection.text
val allText: String = selection.textContent
```

### Combinators

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val selection1: XmlSelection = ???
val selection2: XmlSelection = ???

// Map over selections
val mapped = selection1.map(xml => xml)

// FlatMap for chaining
val nested = selection1.flatMap(xml => XmlSelection.succeed(xml))

// Combine selections
val combined = selection1 ++ selection2

// Alternative on failure
val withFallback = selection1.orElse(selection2)
```

## XmlPatch

`XmlPatch` provides composable XML modification operations:

### Creating Patches

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

val path = p".library.books.book"

// Add content
val addPatch = XmlPatch.add(
  path,
  XmlBuilder.element("book").attr("id", "3").build,
  XmlPatch.Position.AppendChild
)

// Remove element
val removePatch = XmlPatch.remove(path)

// Replace element
val replacePatch = XmlPatch.replace(
  path,
  XmlBuilder.element("book").attr("id", "999").build
)

// Set attribute
val attrPatch = XmlPatch.setAttribute(path, "featured", "true")

// Remove attribute
val removeAttrPatch = XmlPatch.removeAttribute(path, "id")
```

### Position Options

```scala mdoc:compile-only
import zio.blocks.schema.xml.XmlPatch.Position

Position.Before         // Insert before the target element
Position.After          // Insert after the target element
Position.PrependChild   // Insert as first child of target
Position.AppendChild    // Insert as last child of target
```

### Applying Patches

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

val xml: Xml = ???
val patch = XmlPatch.setAttribute(p".person", "active", "true")

// Apply the patch
val result: Either[XmlError, Xml] = patch(xml)
```

### Composing Patches

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

val patch1 = XmlPatch.setAttribute(p".person", "id", "123")
val patch2 = XmlPatch.add(
  p".person",
  XmlBuilder.element("email").text("alice@example.com").build,
  XmlPatch.Position.AppendChild
)

// Compose patches - applies patch1, then patch2
val combined = patch1 ++ patch2
```

## Extension Syntax

When a `Schema` is in scope, use convenient extension methods:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)

// Encode to XML AST
val xml: Xml = person.toXml

// Encode to XML string
val xmlString: String = person.toXmlString
// <Person><name>Alice</name><age>30</age></Person>

// Encode to bytes
val bytes: Array[Byte] = person.toXmlBytes

// Decode from XML string
val parsed: Either[SchemaError, Person] = 
  "<Person><name>Bob</name><age>25</age></Person>".fromXml[Person]

// Decode from bytes
val fromBytes: Either[SchemaError, Person] = bytes.fromXml[Person]
```

## Printing XML

### Basic Printing

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val xml = Xml.Element("person", Xml.Element("name", Xml.Text("Alice")))

// Compact output
val compact: String = xml.print
// <person><name>Alice</name></person>

// Pretty-printed output
val pretty: String = xml.printPretty
// <person>
//   <name>Alice</name>
// </person>

// Custom configuration
val custom: String = xml.print(WriterConfig(indentStep = 4))
```

## Type Testing and Access

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val xml: Xml = Xml.Element("person")

// Type testing
xml.is(XmlType.Element)  // true
xml.is(XmlType.Text)     // false

// Type narrowing (returns Option)
val elem: Option[Xml.Element] = xml.as(XmlType.Element)

// Value extraction (returns Option)
val (name, attrs, children) = xml.unwrap(XmlType.Element).get
```

## Supported Types

All standard ZIO Blocks Schema types are supported:

**Numeric Types**:
- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`
- `BigInt`, `BigDecimal`

**Text Types**:
- `String`

**Special Types**:
- `Unit`, `UUID`, `Currency`

**Java Time Types**:
- `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`
- `Duration`, `Period`
- `Year`, `YearMonth`, `MonthDay`
- `DayOfWeek`, `Month`
- `ZoneId`, `ZoneOffset`

**Composite Types**:
- Records (case classes)
- Variants (sealed traits)
- Sequences (`List`, `Vector`, `Set`, etc.)
- Maps (`Map[K, V]`)
- Options (`Option[A]`)
- Wrappers (newtypes)

## Error Handling

All decoding operations return `Either[SchemaError, A]` or `Either[XmlError, A]`:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)

// Decoding invalid XML
val invalid = "<Person><name>Alice</name></Person>"  // missing age
val result = codec.decode(invalid)

result match {
  case Right(person) => println(s"Decoded: $person")
  case Left(error) => 
    println(s"Error: ${error.getMessage}")
    // Error information includes parse location and context
}
```

## Cross-Platform Support

The XML module works across all platforms:

- **JVM** - Full functionality
- **Scala.js** - Browser and Node.js

All features including parsing, writing, navigation, and patching work identically on both platforms.

## Examples

### Complete Example with Attributes and Namespaces

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Entry(
  @xmlAttribute() id: String,
  title: String,
  updated: String,
  author: Author
)

case class Author(name: String, email: String)

object Entry {
  implicit val authorSchema: Schema[Author] = Schema.derived
  implicit val schema: Schema[Entry] = Schema.derived
}

val codec = Schema[Entry].derive(XmlFormat)

val entry = Entry(
  id = "entry-1",
  title = "First Post",
  updated = "2024-01-01T00:00:00Z",
  author = Author("Alice", "alice@example.com")
)

// Encode with pretty printing
val xml = codec.encodeToString(entry, WriterConfig.pretty)
println(xml)
// <atom:Entry xmlns:atom="http://www.w3.org/2005/Atom" id="entry-1">
//   <title>First Post</title>
//   <updated>2024-01-01T00:00:00Z</updated>
//   <author>
//     <name>Alice</name>
//     <email>alice@example.com</email>
//   </author>
// </atom:Entry>

// Decode back to typed value
val decoded = codec.decode(xml)
// Right(Entry("entry-1", "First Post", "2024-01-01T00:00:00Z", Author("Alice", "alice@example.com")))
```

### Navigation and Transformation

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

val xmlString = """
<library>
  <books>
    <book id="1">
      <title>Functional Programming</title>
      <price>49.99</price>
    </book>
    <book id="2">
      <title>Advanced Scala</title>
      <price>59.99</price>
    </book>
  </books>
</library>
"""

val xml = XmlReader.read(xmlString).toOption.get

// Find all books
val books = xml.select.get("library").get("books").get("book")

// Extract all titles
val titles = books.get("title").toChunk.map { titleElem =>
  titleElem.asInstanceOf[Xml.Element].children.head match {
    case Xml.Text(text) => text
    case _ => ""
  }
}
// Chunk("Functional Programming", "Advanced Scala")

// Apply a patch to add a new book
val patch = XmlPatch.add(
  p".library.books",
  XmlBuilder.element("book")
    .attr("id", "3")
    .child(XmlBuilder.element("title").text("ZIO Essentials").build)
    .child(XmlBuilder.element("price").text("39.99").build)
    .build,
  XmlPatch.Position.AppendChild
)

val updated = patch(xml)
```
