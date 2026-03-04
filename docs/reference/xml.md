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

## XmlEncoder and XmlDecoder

For more fine-grained control over XML serialization, use the separate `XmlEncoder` and `XmlDecoder` traits:

### XmlEncoder

`XmlEncoder[A]` provides type-safe XML encoding:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

// Automatic derivation from Schema
case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
  implicit val encoder: XmlEncoder[Person] = XmlEncoder.fromSchema
}

val person = Person("Alice", 30)
val xml: Xml = XmlEncoder[Person].encode(person)
```

**Creating custom encoders:**

```scala mdoc:compile-only
import zio.blocks.schema.xml._

// Create from a function
val customEncoder: XmlEncoder[Int] = XmlEncoder.instance(n =>
  Xml.Element("number", Xml.Text(n.toString))
)

// Map with contravariance - encode a wrapper type
case class UserId(value: Int)

val userIdEncoder: XmlEncoder[UserId] =
  customEncoder.contramap[UserId](_.value)
```

**Using implicit resolution:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Product(id: String, price: Double)
object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

// No explicit encoder needed - derives automatically
def encodeProduct[A](value: A)(implicit encoder: XmlEncoder[A]): Xml =
  encoder.encode(value)

val result = encodeProduct(Product("item-1", 99.99))
```

### XmlDecoder

`XmlDecoder[A]` provides type-safe XML decoding with error handling:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

// Automatic derivation from Schema
case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
  implicit val decoder: XmlDecoder[Person] = XmlDecoder.fromSchema
}

val xml = Xml.Element("Person",
  Xml.Element("name", Xml.Text("Alice")),
  Xml.Element("age", Xml.Text("30"))
)

val result: Either[XmlError, Person] = XmlDecoder[Person].decode(xml)
// Right(Person("Alice", 30))
```

**Creating custom decoders:**

```scala mdoc:compile-only
import zio.blocks.schema.xml._

// Create from a function
val numberDecoder: XmlDecoder[Int] = XmlDecoder.instance { xml =>
  xml match {
    case Xml.Element(_, _, Chunk(Xml.Text(text), _*)) =>
      text.toIntOption.toRight(XmlError("Invalid number"))
    case _ => Left(XmlError("Expected number element"))
  }
}

// Map for covariance - decode to a wrapper type
case class UserId(value: Int)

val userIdDecoder: XmlDecoder[UserId] =
  numberDecoder.map(UserId(_))
```

**Error handling with decoders:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

def decodeWithFallback[A](
  xml: Xml,
  fallback: A
)(implicit decoder: XmlDecoder[A]): A = {
  decoder.decode(xml).getOrElse(fallback)
}

val invalidXml = Xml.Element("Empty")
val defaultPerson = Person("Unknown", 0)
val result = decodeWithFallback(invalidXml, defaultPerson)
// Person("Unknown", 0)
```

**Combining encoders and decoders:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Message(id: String, text: String)
object Message {
  implicit val schema: Schema[Message] = Schema.derived
}

// Round-trip: encode then decode
val message = Message("msg-1", "Hello")
val encoded: Xml = message.toXml

val result: Either[XmlError, Message] =
  implicitly[XmlDecoder[Message]].decode(encoded)
// Right(Message("msg-1", "Hello"))
```

## Extension Syntax

When a `Schema` is in scope, use convenient extension methods:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._
import zio.blocks.schema.xml.syntax._

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

## XmlBinaryCodec

`XmlBinaryCodec[A]` is the low-level codec interface that bridges Schema definitions with XML serialization. While usually derived automatically, you can work with it directly:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Get the underlying binary codec
val codec: XmlBinaryCodec[Person] =
  schema.derive(XmlBinaryCodecDeriver)

// Encode to Xml directly
val person = Person("Alice", 30)
val xml: Xml = codec.encodeValue(person)

// Decode from Xml directly
val decoded: Either[XmlError, Person] = codec.decodeValue(xml)
```

**XmlBinaryCodec supports all Schema types:**
- Primitives (Int, String, Boolean, etc.)
- Java time types (Instant, LocalDate, Duration, etc.)
- Records (case classes with field-level configuration)
- Variants (sealed traits with discriminators)
- Collections (List, Vector, Map, etc.)
- Optional fields (Option[A])
- Custom wrappers and dynamic values

## Error Handling

All decoding operations return `Either[SchemaError, A]` or `Either[XmlError, A]`. The `XmlError` type provides detailed error information:

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

### XmlError Details

`XmlError` provides detailed error information for debugging:

```scala mdoc:compile-only
import zio.blocks.schema.xml._

val error = XmlError("Parse failed")

// Error message
val message: String = error.getMessage

// Get error with position information (line/column)
val withSpan = error.atSpan(line = 5, column = 10)
// Message will include position: "Parse failed (at line 5, column 10)"
```

Error handling best practices:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Config(database: String, port: Int)
object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

def loadConfig(xml: String): Either[String, Config] = {
  val codec = Schema[Config].derive(XmlFormat)
  codec.decode(xml).left.map { error =>
    s"Configuration error: ${error.getMessage}\nCheck XML format and required fields."
  }
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

## Real-World Examples

### RSS Feed Parsing

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

@xmlNamespace(uri = "http://www.rss.org/", prefix = "rss")
case class Item(
  @xmlAttribute() guid: String,
  title: String,
  link: String,
  pubDate: String,
  description: String
)

@xmlNamespace(uri = "http://www.rss.org/", prefix = "rss")
case class Channel(
  title: String,
  link: String,
  description: String,
  items: List[Item]
)

object Channel {
  implicit val itemSchema: Schema[Item] = Schema.derived
  implicit val schema: Schema[Channel] = Schema.derived
}

// Parse RSS feed from string
val feedXml = """<rss:Channel xmlns:rss="http://www.rss.org/">
  <title>Tech Blog</title>
  <link>https://example.com</link>
  <description>Latest tech articles</description>
  <items>
    <Item guid="1">
      <title>Functional Programming in Scala</title>
      <link>https://example.com/fp</link>
      <pubDate>2024-01-01</pubDate>
      <description>Deep dive into FP concepts</description>
    </Item>
  </items>
</rss:Channel>"""

val codec = Schema[Channel].derive(XmlFormat)
val result: Either[SchemaError, Channel] = codec.decode(feedXml)
```

### Atom Feed with Advanced Features

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Entry(
  @xmlAttribute() id: String,
  title: String,
  author: String,
  updated: String,
  @xmlAttribute("href") link: String
)

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Feed(
  @xmlAttribute() id: String,
  title: String,
  updated: String,
  entries: List[Entry]
)

object Feed {
  implicit val entrySchema: Schema[Entry] = Schema.derived
  implicit val schema: Schema[Feed] = Schema.derived
}

// Encode feed to XML with custom formatting
val feed = Feed(
  id = "urn:uuid:60a76c80-d399-11d9-b91C-0003939e0af6",
  title = "Example Feed",
  updated = "2024-01-01T18:30:02Z",
  entries = List(
    Entry(
      id = "urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a",
      title = "Atom-Powered Robots Run Amok",
      author = "John Doe",
      updated = "2024-01-01T18:30:02Z",
      link = "http://example.org/2024/01/entry"
    )
  )
)

val codec = Schema[Feed].derive(XmlFormat)
val xmlOutput = codec.encodeToString(feed, WriterConfig.pretty)
```

### Sitemap XML

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.xml._

case class Url(
  loc: String,
  lastmod: Option[String],
  changefreq: Option[String],
  priority: Option[Double]
)

case class Urlset(
  urls: List[Url]
)

object Urlset {
  implicit val urlSchema: Schema[Url] = Schema.derived
  implicit val schema: Schema[Urlset] = Schema.derived
}

// Build and encode sitemap
val sitemap = Urlset(List(
  Url("https://example.com", Some("2024-01-01"), Some("monthly"), Some(1.0)),
  Url("https://example.com/about", Some("2024-01-01"), Some("monthly"), Some(0.8)),
  Url("https://example.com/contact", None, Some("monthly"), Some(0.5))
))

val codec = Schema[Urlset].derive(XmlFormat)
val sitemapXml = codec.encodeToString(sitemap, WriterConfig(
  indentStep = 2,
  includeDeclaration = true
))
```

## Implementation Details

### Zero Dependencies

The schema-xml module is **completely self-contained** with zero external dependencies:

- **XmlReader**: Hand-written pull-based parser with comprehensive XML support
- **XmlWriter**: Streaming XML serializer with proper entity escaping
- **Cross-platform**: Both implementations work identically on JVM and Scala.js
- **No reflection**: Uses compile-time schema derivation for performance

### Cross-Scala Compatibility

Full support for both Scala versions with platform-specific optimizations:

```scala mdoc:compile-only
// Scala 2.13 syntax
implicit class XmlSyntax[A](value: A)(implicit encoder: XmlEncoder[A]) {
  def toXml: Xml = encoder.encode(value)
}

// Scala 3 syntax
extension [A](value: A)(using encoder: XmlEncoder[A]) {
  def toXml: Xml = encoder.encode(value)
}
```

### Performance Characteristics

- **Encoding**: O(n) time where n is total size of output
- **Decoding**: O(n) time where n is input size
- **Memory**: Streaming for both parsing and writing (constant memory for large documents)
- **Chunk-based**: Uses efficient Chunk data structures throughout

## Comparison with Java XML Libraries

| Feature | schema-xml | JAXB | DOM4j |
|---------|-----------|------|-------|
| **Dependencies** | Zero | Jakarta | Yes |
| **Scala Native** | ✅ | ❌ | ❌ |
| **Scala.js** | ✅ | ❌ | ❌ |
| **Type Safety** | ✅ | Limited | ❌ |
| **Schema-driven** | ✅ | ✅ | ❌ |
| **Functional** | ✅ | ❌ | ❌ |
| **Fluent API** | ✅ | ❌ | ✅ |
| **Memory Efficient** | ✅ | ✅ | ❌ |
