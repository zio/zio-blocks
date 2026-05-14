---
id: json-codec
title: "JsonCodec"
---

`JsonCodec[A]` is the abstract base class for encoding and decoding values of type `A` to and from JSON format. It extends `BinaryCodec[A]` and provides two core methods: `decodeValue(in: JsonReader): A` for reading from a binary JSON stream, and `encodeValue(x: A, out: JsonWriter): Unit` for writing to a binary JSON stream.

## Overview

When you have a `Schema[A]` in scope, implicit `JsonCodec[A]` instances are automatically available through the schema derivation system. You don't typically instantiate `JsonCodec` directly—instead, you use extension methods like `.toJson`, `.toJsonString`, and `.as[Type]` that rely on implicit codecs.

**Core responsibilities:**
- Convert Scala values ↔ UTF-8 encoded JSON bytes
- Handle primitive types, case classes, sealed traits, collections, and nested structures
- Support key encoding/decoding for object fields
- Provide optional JSON Schema derivation via `toJsonSchema`
- Integrate with `Schema`-based type derivation

## Creating Codecs

### Via Schema Derivation

The most common pattern is using `Schema.derived` to get a schema, which automatically provides a codec:

```scala mdoc:compile-only
import zio.blocks.schema._

case class User(name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// Codec is implicitly available through the schema
val user = User("Alice", "alice@example.com")
val json = user.toJson  // Json.Object(...)
val decoded: Either[SchemaError, User] = json.as[User]
```

### Predefined Codecs

The `JsonCodec` companion object provides codec instances for all primitive types:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonCodec

// Predefined codecs for primitives
val intCodec = JsonCodec.intCodec
val stringCodec = JsonCodec.stringCodec
val booleanCodec = JsonCodec.booleanCodec
val doubleCodec = JsonCodec.doubleCodec
```

## Core Operations

### Encoding

**Extension methods on values (requires `Schema` in scope):**

```scala mdoc:compile-only
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person { implicit val schema: Schema[Person] = Schema.derived }

val person = Person("Bob", 30)

// Encode to Json ADT
val json = person.toJson  // Json.Object(...)

// Encode to JSON string
val jsonString = person.toJsonString  // {"name":"Bob","age":30}

// Encode to UTF-8 bytes
val bytes = person.toJsonBytes  // Array[Byte]
```

**Encoding with custom WriterConfig:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Person(name: String, age: Int)
object Person { implicit val schema: Schema[Person] = Schema.derived }

val person = Person("Bob", 30)
val json = person.toJson

// Pretty-printed with 2-space indentation
val pretty = json.print(WriterConfig.withIndentionStep(2))

// Escaped Unicode output for ASCII-only transmission
val ascii = json.print(WriterConfig.withEscapeUnicode(true))

// Custom buffer size for large documents
val large = json.print(WriterConfig.withPreferredBufSize(65536))
```

### Decoding

**Extension methods on JsonSelection (from `.as[Type]`):**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.Json

case class Product(name: String, price: Double)
object Product { implicit val schema: Schema[Product] = Schema.derived }

// From JSON value
val json = Json.Object("name" -> Json.String("Widget"), "price" -> Json.Number(9.99))
val product: Either[SchemaError, Product] = json.as[Product]

// From JSON string
val jsonString = """{"name": "Gadget", "price": 19.99}"""
val parsed = Json.parseUnsafe(jsonString)
val decoded: Either[SchemaError, Product] = parsed.as[Product]

// From UTF-8 bytes
val bytes: Array[Byte] = """{"name":"Tool","price":29.99}""".getBytes
val fromBytesJson = Json.parseUnsafe(String(bytes, "UTF-8"))
val fromBytes: Either[SchemaError, Product] = fromBytesJson.as[Product]
```

### Key Encoding/Decoding

For object fields, `JsonCodec` supports encoding/decoding as JSON keys:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonCodec

val intCodec = JsonCodec.intCodec

// Encode value as a key
val key = intCodec.encodeKey(42)  // "42"

// Decode from a key
val value = intCodec.decodeKey("42")  // 42
```

## Integration with Configuration

### WriterConfig

Control encoding behavior with `WriterConfig`:

| Option | Default | Purpose |
|--------|---------|---------|
| `indentionStep` | 0 | Spaces per indentation level (0 = compact) |
| `escapeUnicode` | false | Escape non-ASCII characters as `\uXXXX` |
| `sortKeys` | false | Sort object keys alphabetically |
| `nullHandling` | Keep | How to handle null values (Keep, Skip, Null) |
| `preferredBufSize` | 32768 | Internal buffer size in bytes |

**Example:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Config(server: String, port: Int, debug: Boolean)
object Config { implicit val schema: Schema[Config] = Schema.derived }

val config = Config("localhost", 8080, true)
val json = config.toJson

// Compact ASCII-only output
val compact = json.print(WriterConfig
  .withEscapeUnicode(true)
)

// Pretty-printed with sorted keys
val pretty = json.print(WriterConfig
  .withIndentionStep(2)
  .withSortKeys(true)
)
```

### ReaderConfig

Control decoding behavior with `ReaderConfig` when parsing JSON:

| Option | Default | Purpose |
|--------|---------|---------|
| `checkForEndOfInput` | true | Verify no trailing input after JSON |
| `maxDepth` | 128 | Maximum nesting depth before error |
| `preferredCharBufSize` | 8192 | Internal char buffer size |
| `numberPrecision` | Max | How precisely to parse numbers (Exact, Max, Min) |

**Example:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, ReaderConfig}

// Lenient parsing (allow trailing whitespace)
val lenient = ReaderConfig.withCheckForEndOfInput(false)

// Parse with lenient config
val json = Json.parseUnsafe("""{"x": 1}  """, lenient)
```

## Primitive Type Coverage

`JsonCodec` includes predefined codecs for:

**Numeric:** `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigDecimal`, `BigInt`
**Text:** `String`, `Char`
**Boolean:** `Boolean`, plus implicit coercion from 1/0
**Special:** `Unit`, `UUID`, `Currency`
**Date/Time:** `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration`, `Period`

Each codec respects the JSON RFC-8259 format and handles precision loss (e.g., `Float` from `BigDecimal`).

## JSON Schema Derivation

When you call `.toJsonSchema` on a codec, it returns a `JsonSchema` describing the values it encodes:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonCodec

val intCodec = JsonCodec.intCodec
val schema = intCodec.toJsonSchema  // JsonSchema for integer

// Custom codecs can override toJsonSchema
```

## Error Handling

Decoding failures return `Either[SchemaError, A]`, with detailed path information:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Strict(value: Int)
object Strict { implicit val schema: Schema[Strict] = Schema.derived }

val invalid = """{"value": "not-an-int"}"""
val result = invalid.as[Strict]
// Left(SchemaError(...path: .value, message: "expected Int"))
```

## Thread Safety

`JsonCodec` instances are thread-safe and use thread-local pools for reader/writer instances to minimize allocation overhead.
