---
id: json-config
title: "JSON Configuration"
---

The JSON codec module provides four configuration types for controlling encoding and decoding behavior: `WriterConfig`, `ReaderConfig`, `MergeStrategy`, and `NameMapper`. These types allow fine-grained control over how JSON is serialized and deserialized.

## WriterConfig

`WriterConfig` controls the formatting and content of encoded JSON output. Use it when calling `.print()` on `Json` values or when encoding values with specific formatting requirements.

### Configuration Options

| Option | Type | Default | Purpose |
|--------|------|---------|---------|
| `indentionStep` | Int | 0 | Spaces per indentation level (0 = compact JSON) |
| `escapeUnicode` | Boolean | false | Escape non-ASCII characters as `\uXXXX` for ASCII-only output |
| `sortKeys` | Boolean | false | Alphabetically sort object keys |
| `nullHandling` | NullHandling | Keep | How to handle null values (Keep, Skip, Null) |
| `preferredBufSize` | Int | 32768 | Internal buffer size in bytes for streaming |

### Usage Examples

**Compact Output (Default):**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Product(id: Int, name: String, price: Double)
object Product { implicit val schema: Schema[Product] = Schema.derived }

val product = Product(1, "Widget", 9.99)
val json = product.toJson

// Default compact output
val compact = json.print()
// {"id":1,"name":"Widget","price":9.99}
```

**Pretty-Printed Output:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{WriterConfig}

case class Config(server: String, port: Int, debug: Boolean)
object Config { implicit val schema: Schema[Config] = Schema.derived }

val config = Config("localhost", 8080, true)
val json = config.toJson

// Pretty-printed with 2-space indentation
val pretty = json.print(WriterConfig.withIndentionStep(2))
// {
//   "server": "localhost",
//   "port": 8080,
//   "debug": true
// }
```

**Sorted Keys with Custom Indentation:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class User(name: String, email: String, age: Int)
object User { implicit val schema: Schema[User] = Schema.derived }

val user = User("Alice", "alice@example.com", 30)
val json = user.toJson

// 4-space indentation
val sorted = json.print(WriterConfig
  .withIndentionStep(4)
)
// {
//     "name": "Alice",
//     "email": "alice@example.com",
//     "age": 30
// }
```

**ASCII-Only Output:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Message(text: String)
object Message { implicit val schema: Schema[Message] = Schema.derived }

val msg = Message("Hello 世界")
val json = msg.toJson

// Escape non-ASCII characters
val ascii = json.print(WriterConfig.withEscapeUnicode(true))
// {"text":"Hello 世界"}
```

**Null Handling:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{WriterConfig, NullHandling}

case class Data(x: Option[Int], y: Option[String])
object Data { implicit val schema: Schema[Data] = Schema.derived }

val data = Data(Some(1), None)
val json = data.toJson

// Skip null fields (smaller output)
val skipNulls = json.print(WriterConfig.withNullHandling(NullHandling.Skip))
// {"x":1}

// Explicitly include nulls
val includeNulls = json.print(WriterConfig.withNullHandling(NullHandling.Keep))
// {"x":1,"y":null}
```

## ReaderConfig

`ReaderConfig` controls how JSON input is parsed and validated during decoding. Use it when calling `Json.parse()` with custom parsing behavior.

### Configuration Options

| Option | Type | Default | Purpose |
|--------|------|---------|---------|
| `checkForEndOfInput` | Boolean | true | Verify no extra input after valid JSON |
| `maxDepth` | Int | 128 | Maximum nesting depth before error |
| `preferredCharBufSize` | Int | 8192 | Internal character buffer size |
| `numberPrecision` | NumberPrecision | Max | Number parsing: Exact, Max, Min |

### Usage Examples

**Lenient Parsing:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, ReaderConfig}

// Lenient: allow trailing whitespace
val lenientConfig = ReaderConfig.withCheckForEndOfInput(false)

val jsonString = """{"name": "Alice"}   """  // Trailing spaces
val result = Json.parse(jsonString, lenientConfig)
// Right(Json.Object(...))
```

**Deep Nesting Limits:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, ReaderConfig}

// Restrict nesting depth for security
val limitedConfig = ReaderConfig.withMaxDepth(10)

val deepJson = """{"a":{"b":{"c":{"d":{"e":{"f":{"g":{"h":{"i":{"j":{"k":1}}}}}}}}}}}"""
val result = Json.parse(deepJson, limitedConfig)
// Left(SchemaError("nesting depth exceeded"))
```

**Number Precision Control:**

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, ReaderConfig, NumberPrecision}

// Maximum precision (uses BigDecimal internally)
val exact = ReaderConfig.withNumberPrecision(NumberPrecision.Exact)

val json = """{"pi": 3.14159265358979323846}"""
val result = Json.parse(json, exact)
```

## MergeStrategy

`MergeStrategy` determines how field conflicts are resolved when merging two JSON objects. It provides strategies for different use cases, from strict validation to lenient concatenation.

**Available Strategies:**
- `Strict` — Fails if the same field appears in both objects
- `Right` — The right object's values override the left's
- `Left` — The left object's values take precedence
- Additional strategies may be available depending on your use case

For details on merge strategies and their usage, see the Merging section of the [Json](./json.md) documentation.

## NameMapper

`NameMapper` customizes how field names are transformed between Scala types and JSON representation. It enables patterns like camelCase ↔ snake_case conversion.

### Built-in Mappers

**Identity (Default):**
Field names unchanged:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.NameMapper

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
}

// Default: no transformation
val user = User("Alice", "Smith")
val json = user.toJson
// {"firstName": "Alice", "lastName": "Smith"}
```

**Snake Case:**
Convert camelCase to snake_case:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.NameMapper

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
  implicit val nameMapper: NameMapper = NameMapper.SnakeCase
}

val user = User("Alice", "Smith")
val json = user.toJson
// {"first_name": "Alice", "last_name": "Smith"}
```

**Kebab Case:**
Convert camelCase to kebab-case:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.NameMapper

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
  implicit val nameMapper: NameMapper = NameMapper.KebabCase
}

val user = User("Alice", "Smith")
val json = user.toJson
// {"first-name": "Alice", "last-name": "Smith"}
```

**Custom Mappers:**
Additional custom name mappers may be available depending on your needs. The built-in mappers (Identity, SnakeCase, KebabCase) cover most common use cases.

## Combining Configuration

Use multiple configuration options together for fine-grained control:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Config(server: String, port: Int, timeout: Option[Int])
object Config { implicit val schema: Schema[Config] = Schema.derived }

val config = Config("example.com", 443, None)
val json = config.toJson

// Pretty-printed with 4-space indentation
val output = json.print(WriterConfig
  .withIndentionStep(4)
)
// {
//     "server": "example.com",
//     "port": 443
// }
```

## Performance Implications

- **WriterConfig:** Indentation increases output size but has minimal performance impact
- **ReaderConfig:** `maxDepth` checks add negligible overhead; `checkForEndOfInput` adds one comparison
- **MergeStrategy:** Strict is fastest; Recursive requires additional traversals
- **NameMapper:** Applied once per field during schema derivation; zero runtime cost if identity mapping

## Best Practices

1. **Use WriterConfig for human-readable output only** — Compact output is faster for wire transmission
2. **Limit maxDepth** for untrusted input to prevent denial-of-service attacks
3. **Choose MergeStrategy based on use case** — Strict for validation, Lenient for defaults
4. **Standardize on one NameMapper** across your codebase to avoid confusion
5. **Cache WriterConfig/ReaderConfig instances** — They're immutable and can be reused
