---
id: index
title: "ZIO Blocks"
---

**Modular, zero-dependency building blocks for modern Scala applications.**

@PROJECT_BADGES@

## What Is ZIO Blocks?

ZIO Blocks is a **family of type-safe, modular building blocks** for Scala applications. Each block is a standalone library with zero or minimal dependencies, designed to work with *any* Scala stack—ZIO, Cats Effect, Kyo, Ox, Akka, or plain Scala.

The philosophy is simple: **use what you need, nothing more**. Each block is independently useful, cross-platform (JVM, JS), and designed to compose with other blocks or your existing code.

## The Blocks

| Block | Description | Status |
|-------|-------------|--------|
| **Schema** | Type-safe schemas with automatic codec derivation | ✅ Available |
| **Chunk** | High-performance immutable indexed sequences | ✅ Available |
| **Docs** | GitHub Flavored Markdown parsing and rendering | ✅ Available |
| **TypeId** | Compile-time type identity with rich metadata | ✅ Available |
| **Context** | Type-indexed heterogeneous collections | ✅ Available |
| **Streams** | Pull-based streaming primitives | 🚧 In Development |

## Core Principles

- **Zero Lock-In**: No dependencies on ZIO, Cats Effect, or any effect system. Use with whatever stack you prefer.
- **Modular**: Each block is a separate artifact. Import only what you need.
- **Cross-Platform**: Full support for JVM and Scala.js.
- **Cross-Version**: Full support for Scala 2.13 and Scala 3.x with source compatibility—adopt Scala 3 on your timeline, not ours.
- **High Performance**: Optimized implementations that avoid boxing, minimize allocations, and leverage platform-specific features.
- **Type Safety**: Leverage Scala's type system for correctness without runtime overhead.

---

## Schema

The Schema block brings dynamic-language productivity to statically-typed Scala. Define your data types once, and derive codecs, validators, optics, and more automatically.

### The Problem

In statically-typed languages, you often maintain separate codec implementations for each data format (JSON, Avro, Protobuf, etc.). Meanwhile, dynamic languages handle data effortlessly:

```javascript
// JavaScript: one line and done
const data = await res.json();
```

In Scala, you'd typically need separate codecs for each format—a significant productivity gap.

### The Solution

ZIO Blocks Schema derives everything from a single schema definition:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive codecs for any format:
val jsonCodec    = Schema[Person].derive(JsonFormat)        // JSON
val avroCodec    = Schema[Person].derive(AvroFormat)        // Avro
val toonCodec    = Schema[Person].derive(ToonFormat)        // TOON (LLM-optimized)
val msgpackCodec = Schema[Person].derive(MessagePackFormat) // MessagePack
val thriftCodec  = Schema[Person].derive(ThriftFormat)      // Thrift
```

### Key Features

- **Universal Data Formats**: JSON, Avro, TOON (compact LLM-optimized format), MessagePack, Thrift, and BSON, with Protobuf planned.
- **High Performance**: Register-based design stores primitives directly in byte arrays, enabling zero-allocation serialization.
- **Reflective Optics**: Type-safe lenses, prisms, and traversals with embedded structural metadata.
- **Automatic Derivation**: Derive type class instances for any type with a schema.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Optional format modules:
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "@VERSION@"
```

### Example: Optics

```scala
import zio.blocks.schema._

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int] = $(_.age)
  val streetName: Lens[Person, String] = $(_.address.street)
}

val person = Person("Alice", 30, Address("123 Main St", "Springfield"))
val updated = Person.age.replace(person, 31)
```

---

## Chunk

A high-performance, immutable indexed sequence optimized for the patterns common in streaming, parsing, and data processing. Think of it as `Vector` but faster for the operations that matter most.

### Why Chunk?

Standard library collections make trade-offs that aren't ideal for streaming and binary data processing:

- `Vector` is general-purpose but not optimized for concatenation patterns
- `Array` is mutable and boxes primitives when used generically
- `List` has O(n) random access

Chunk is designed for:

- **Fast concatenation** via balanced trees (Conc-Trees)
- **Zero-boxing** for primitive types with specialized builders
- **Efficient slicing** without copying
- **Seamless interop** with `ByteBuffer`, `Array`, and standard collections

### Key Features

- **Specialized Builders**: Dedicated builders for `Byte`, `Int`, `Long`, `Double`, etc. avoid boxing overhead.
- **Balanced Concatenation**: Based on Conc-Trees for O(log n) concatenation while maintaining O(1) indexed access.
- **Bit Operations**: First-class support for bit-level operations, bit chunks backed by `Byte`, `Int`, or `Long` arrays.
- **NonEmptyChunk**: A statically-guaranteed non-empty variant for APIs that require at least one element.
- **Full Scala Collection Integration**: Implements `IndexedSeq` for seamless interop.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "@VERSION@"
```

### Example

```scala
import zio.blocks.chunk._

// Create chunks
val bytes = Chunk[Byte](1, 2, 3, 4, 5)
val moreBytes = Chunk.fromArray(Array[Byte](6, 7, 8))

// Efficient concatenation (O(log n))
val combined = bytes ++ moreBytes

// Zero-copy slicing
val slice = combined.slice(2, 6)

// Bit operations
val bits = bytes.asBitsByte
val masked = bits & Chunk.fill(bits.length)(true)

// NonEmptyChunk for type-safe non-emptiness
val nonEmpty = NonEmptyChunk(1, 2, 3)
val head: Int = nonEmpty.head  // Always safe, no Option needed
```

---

## Docs

A zero-dependency GitHub Flavored Markdown library for parsing, rendering, and programmatic construction of Markdown documents.

### Why Docs?

Generating documentation, README files, or any Markdown content programmatically is common but error-prone with string concatenation. Docs provides:

- **Type-safe AST**: Build Markdown documents with compile-time guarantees
- **Compile-time validation**: The `md"..."` interpolator validates syntax at compile time
- **Multiple renderers**: Output to Markdown, HTML, or ANSI terminal
- **Round-trip parsing**: Parse Markdown to AST and render back to Markdown

### Key Features

- **GFM Compliant**: Tables, strikethrough, autolinks, task lists, fenced code blocks
- **Zero Dependencies**: Only depends on zio-blocks-chunk
- **Cross-Platform**: Full support for JVM and Scala.js
- **Type-Safe Interpolator**: `md"# Hello $name"` with compile-time validation
- **Multiple Renderers**: Markdown, HTML (full document or fragment), ANSI terminal

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "@VERSION@"
```

### Example

```scala
import zio.blocks.docs._

// Parse Markdown
val doc = Parser.parse("# Hello\n\nThis is **bold** text.")
// Right(Doc(Chunk(Heading(H1, "Hello"), Paragraph(...))))

// Render to HTML
val html = doc.map(_.toHtml)
// Full HTML5 document with <html>, <head>, <body>

// Render to HTML fragment (just the content)
val fragment = doc.map(_.toHtmlFragment)
// "<h1>Hello</h1><p>This is <strong>bold</strong> text.</p>"

// Render to terminal with ANSI colors
val terminal = doc.map(_.toTerminal)

// Use the type-safe interpolator
val name = "World"
val greeting = md"# Hello $name"
// Doc containing: Heading(H1, Chunk(Text("Hello World")))

// Build documents programmatically
import zio.blocks.chunk.Chunk

val manual = Doc(Chunk(
  Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("API Reference"))),
  Block.Paragraph(Chunk(
    Inline.Text("See "),
    Inline.Link(Chunk(Inline.Text("docs")), "/docs", None),
    Inline.Text(" for details.")
  ))
))

// Render back to Markdown
val markdown = Renderer.render(manual)
```

### Supported GFM Features

| Feature | Supported |
|---------|-----------|
| Headings (ATX) | ✅ |
| Paragraphs | ✅ |
| Emphasis/Strong | ✅ |
| Code (inline & fenced) | ✅ |
| Links & Images | ✅ |
| Lists (bullet, ordered, task) | ✅ |
| Blockquotes | ✅ |
| Tables | ✅ |
| Strikethrough | ✅ |
| Autolinks | ✅ |
| Hard/Soft breaks | ✅ |
| HTML (passthrough) | ✅ |

### Limitations

- **No frontmatter**: YAML/TOML headers are not parsed
- **No HTML entity decoding**: `&amp;` stays as-is
- **No footnotes**: GFM footnote extension not supported
- **No emoji shortcodes**: `:smile:` not converted to emoji

---

## TypeId

Compile-time type identity with rich metadata. TypeId captures comprehensive information about Scala types including name, owner, type parameters, variance, parent types, and annotations.

### Key Features

- **Rich Metadata**: Captures type name, owner, kind (class/trait/object/enum), parent types, and annotations
- **Higher-Kinded Support**: Works with proper types and type constructors via `AnyKind`
- **Subtype Checking**: Runtime subtype/supertype relationship checks using compile-time extracted information
- **Cross-Platform**: Works identically on JVM and Scala.js

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "@VERSION@"
```

### Example

```scala
import zio.blocks.typeid._

// Get TypeId for any type
val listId = TypeId.of[List[Int]]
println(listId.name)       // "List"
println(listId.fullName)   // "scala.collection.immutable.List"
println(listId.arity)      // 1 (type constructor)

// Check type relationships
trait Animal
case class Dog(name: String) extends Animal

val dogId = TypeId.of[Dog]
val animalId = TypeId.of[Animal]
dogId.isSubtypeOf(animalId)  // true

// Access structural information
dogId.isCaseClass  // true
dogId.isSealed     // false
```

---

## Context

A type-indexed heterogeneous collection that stores values by their types with compile-time type safety.

### Key Features

- **Type-Safe Lookup**: Retrieve values by type with compile-time guarantees
- **Covariant**: `Context[Specific]` is a subtype of `Context[General]`
- **Subtype Matching**: Lookup by supertype finds matching subtypes
- **Cached Access**: O(1) subsequent lookups after first retrieval

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-context" % "@VERSION@"
```

### Example

```scala
import zio.blocks.context._

case class Config(debug: Boolean)
case class Metrics(count: Int)

// Create a context with multiple values
val ctx: Context[Config & Metrics] = Context(
  Config(debug = true),
  Metrics(count = 42)
)

// Retrieve values by type
val config: Config = ctx.get[Config]
val metrics: Metrics = ctx.get[Metrics]

// Add or update values
val updated = ctx.update[Metrics](m => m.copy(count = m.count + 1))

// Combine contexts
val ctx1 = Context(Config(false))
val ctx2 = Context(Metrics(0))
val merged: Context[Config & Metrics] = ctx1 ++ ctx2
```

---

## Streams (In Development)

A pull-based streaming library for composable, backpressure-aware data processing.

```scala
import zio.blocks.streams._

// Coming soon: efficient pull-based streams
// that compose with any effect system
```

---

## Compatibility

ZIO Blocks works with any Scala stack:

| Stack | Compatible |
|-------|------------|
| ZIO 2.x | ✅ |
| Cats Effect 3.x | ✅ |
| Kyo | ✅ |
| Ox | ✅ |
| Akka | ✅ |
| Plain Scala | ✅ |

Each block has zero dependencies on effect systems. Use the blocks directly, or integrate them with your effect system of choice.

## Scala & Platform Support

ZIO Blocks supports **Scala 2.13** and **Scala 3.x** with full source compatibility. Write your code once and compile it against either version—migrate to Scala 3 when your team is ready, not when your dependencies force you.

| Platform | Schema | Chunk | Docs | TypeId | Context | Streams |
|----------|--------|-------|------|--------|---------|---------|
| JVM | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scala.js | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## Documentation

### Core Schema Concepts

- [Schema](./reference/schema.md) - Core schema definitions and derivation
- [Reflect](./reference/reflect.md) - Structural reflection API
- [Binding](./reference/binding.md) - Runtime constructors and deconstructors
- [Registers](./reference/registers.md) - Register-based primitive storage

### Optics & Navigation

- [Optics](./reference/optics.md) - Lenses, prisms, and traversals
- [Path Interpolator](./path-interpolator.md) - Type-safe path construction
- [DynamicValue](./reference/dynamic-value.md) - Schema-less dynamic values

### Serialization

- [JSON](./reference/json.md) - JSON codec and parsing
- [JSON Schema](./reference/json-schema.md) - JSON Schema generation and validation
- [Formats](./reference/formats.md) - Avro, TOON, MessagePack, BSON, Thrift
- [Extension Syntax](./reference/syntax.md) - `.toJson`, `.fromJson`, and more

### Data Operations

- [Patching](./reference/patch.md) - Serializable data transformations
- [Validation](./reference/validation.md) - Data validation and error handling
- [Schema Evolution](./reference/schema-evolution.md) - Migration and compatibility

### Other Blocks

- [Chunk](./reference/chunk.md) - High-performance immutable sequences
- [TypeId](./reference/typeid.md) - Type identity and metadata
- [Context](./reference/context.md) - Type-indexed heterogeneous collections
- [Docs (Markdown)](./reference/docs.md) - Markdown parsing and rendering
