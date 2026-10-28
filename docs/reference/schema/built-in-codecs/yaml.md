---
id: yaml
title: "YAML Codec Module"
---

`zio-blocks-schema-yaml` is a **schema-driven YAML codec module** for serializing and deserializing Scala types to and from YAML format. It provides encoding and decoding for the ZIO Schema primitive types, records, variants, sequences and maps, with optional JSON interoperability, configuration options for pretty-printing, and automatic schema generation.

The module integrates with a pure-Scala YAML parser and writer to provide human-readable serialization. Its reader is lenient: it does not report most YAML syntax errors, so read the [known limits](#known-limits) before you load files written by people.

See also the [How to Read and Write YAML Documents](../../../guides/read-and-write-yaml.md) guide, which reads YAML into case classes, writes CI-style YAML, converts it to JSON, and lists the YAML forms the reader does not support.

## Motivation

YAML is a human-readable data format that appears widely across configuration files, Kubernetes manifests, CI/CD pipelines, and data serialization. Manually writing YAML encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and sum types.

The YAML codec module provides:

- Full YAML type support (mappings, sequences, scalars, null)
- Automatic schema generation from Scala types
- Pretty-printed and compact formatting options
- JSON interoperability for seamless conversion
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- Multiple encoding paths: byte arrays and strings
- Multiple decoding paths: byte arrays and strings
- Cross-platform compatibility (JVM and Scala.js)

Rather than hand-writing YAML parsing logic or relying on external libraries with limited Scala support, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-yaml" % "@VERSION@"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-yaml" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x. On Scala 3.9 and newer, use the `zio-blocks-next-schema-yaml` module instead, as [How to Add ZIO Blocks to Your Build](../../../guides/add-zio-blocks-to-your-build.md) explains.

## Introduction

The module provides a complete pipeline for YAML codec derivation and usage:

1. **Define your type**: Any Scala type with a `Schema` instance
2. **Derive a codec**: Use `Schema[A].derive(YamlFormat)` to obtain a `YamlCodec[A]`
3. **Encode or decode**: Call `codec.encodeToString(value)` or `codec.decode(yamlString)`
4. **Handle errors**: Catch `SchemaError` with location traces showing where the error occurred
5. **Configure output**: Use `YamlOptions` with `YamlWriter` for pretty-printing or compact formatting

The derivation process is automatic for all supported types (primitives, records, variants, sequences, maps). The module generates YAML from the schema and handles encoding and decoding without manual configuration.

## How They Work Together

The YAML codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(YamlFormat) creates YamlCodec[A]
                 ↓
3. YamlCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific YAML scalar encoders/decoders
   - For records: field-by-field composition with YAML mapping
   - For variants: a one-key mapping named after the case
   - For sequences: YAML sequence encoding/decoding
   - For maps: YAML mapping encoding/decoding
                 ↓
4. YamlCodec provides multiple encoding paths
   - encodeToString(value) → String
   - encode(value) → Array[Byte]
                 ↓
5. YamlCodec provides multiple decoding paths
   - decode(yaml: String) → Either[SchemaError, A]
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
                 ↓
6. YamlOptions controls output formatting, applied by YamlWriter
   - Pretty-printing with document markers
   - Compact output without markers
   - Custom indentation and style
                 ↓
7. Yaml AST provides structured representation
   - Mapping (key-value pairs)
   - Sequence (ordered lists)
   - Scalar (string values with optional tags)
   - NullValue (YAML null)
                 ↓
8. Errors include location traces
   Shows path (.field[index].nested) to error location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Config)
    ↓
Schema.derived (automatic via macro)
    ↓
Schema[Config].derive(YamlFormat) → YamlCodec[Config]
    ↓
Use codec.encodeToString(config) to serialize → String
Use codec.decode(yamlString) to deserialize → Either[SchemaError, Config]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`Yaml`**: Sealed trait AST representing YAML data structures
- **`YamlCodec[A]`**: Main public API; contains encoder and decoder for bidirectional serialization
- **`YamlCodecDeriver`**: Configuration and derivation system; generates codecs from Schema
- **`YamlOptions`**: Configuration for output formatting (pretty, compact, indentation)
- **`YamlReader`, `YamlWriter`**: Low-level YAML parsing and serialization

## Common Patterns

This section shows practical patterns for working with YAML codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Configuration Record

To derive a YAML codec for a record and encode a value to a string:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class AppConfig(name: String, port: Int, debug: Boolean)

object AppConfig {
  implicit val schema: Schema[AppConfig] = Schema.derived
}

val codec = AppConfig.schema.derive(YamlFormat)
```

`YamlCodec#encodeToString` writes the record as a block mapping, with one line per field:

```scala mdoc
println(codec.encodeToString(AppConfig("MyApp", 8080, debug = true)))
```

### Pattern 2: Decode YAML with Error Handling

When decoding YAML data, errors include location traces showing where the problem occurred.

To decode YAML and handle errors with location information:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class DatabaseConfig(host: String, port: Int, username: String)

object DatabaseConfig {
  implicit val schema: Schema[DatabaseConfig] = Schema.derived
}

val codec = DatabaseConfig.schema.derive(YamlFormat)
val yaml = """
host: localhost
port: invalid
username: admin
"""
```

`YamlCodec#decode` returns `Left` with the path of the field that failed:

```scala mdoc
codec.decode(yaml) match {
  case Right(config) => println(s"Loaded: $config")
  case Left(error)   => println(s"Error: ${error.getMessage}")
}
```

### Pattern 3: Pretty-Print with Configuration

Use `YamlOptions` to control output formatting with indentation and document markers. `YamlCodec#encodeToString` takes no options, so encode the value to the `Yaml` tree with `YamlCodec#encodeValue`, then write the tree with `YamlWriter.write`.

To encode with pretty-printing:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Server(name: String, endpoints: List[String], timeout: Int)

object Server {
  implicit val schema: Schema[Server] = Schema.derived
}

val codec  = Server.schema.derive(YamlFormat)
val server = Server("api-server", List("GET /health", "POST /data"), 30)
```

`YamlOptions.pretty` turns on the leading `---` line:

```scala mdoc
println(YamlWriter.write(codec.encodeValue(server), YamlOptions.pretty))
```

### Pattern 4: Handle Recursive Types

Recursive types (types that reference themselves) are fully supported with automatic cycle detection.

To define and encode a recursive data structure:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

sealed trait TreeNode
case class Leaf(value: String) extends TreeNode
case class Branch(label: String, children: List[TreeNode]) extends TreeNode

object TreeNode {
  implicit val schema: Schema[TreeNode] = Schema.derived
}

val codec = TreeNode.schema.derive(YamlFormat)
val tree: TreeNode = Branch("root", List(Leaf("a"), Branch("b", List(Leaf("c")))))
```

The encoded tree nests each `Branch` under its `children` key:

```scala mdoc
println(codec.encodeToString(tree))
```

### Pattern 5: YAML-JSON Interoperability

Convert between YAML and JSON representations seamlessly using `Yaml#toJson`.

To convert the YAML text to a `Json` value and print it:

```scala mdoc
println(YamlReader.read("name: Alice\nage: 30\n").toJson.print)
```

---

## Yaml

Sealed trait representing a YAML node. Provides a complete AST for YAML data with four possible cases: `Mapping`, `Sequence`, `Scalar`, and `NullValue`.

### Overview

`Yaml` is a sealed trait that represents any valid YAML value. It can be constructed directly or derived from codecs.

### YAML AST Structure

To build a mapping and a sequence directly from the AST:

```scala mdoc:silent:reset
import zio.blocks.schema.yaml._

val mapping = Yaml.Mapping.fromStringKeys(
  "name" -> Yaml.Scalar("Alice"),
  "age"  -> Yaml.Scalar("30")
)

val sequence = Yaml.Sequence(
  Yaml.Scalar("item1"),
  Yaml.Scalar("item2")
)
```

`Yaml#print` writes the node on one document without a leading `---` line:

```scala mdoc
println(mapping.print)
println(sequence.print)
```

### Printing YAML

`Yaml#print` writes the compact form, and `Yaml#printPretty` writes the same node with a leading `---` line. A `Yaml.Scalar` that holds `"true"` has no boolean tag, so the writer quotes it:

```scala mdoc:silent
val flags = Yaml.Mapping.fromStringKeys(
  "name"   -> Yaml.Scalar("Bob"),
  "active" -> Yaml.Scalar("true")
)
```

```scala mdoc
println(flags.print)
println(flags.printPretty)
```

A known bug in 0.0.56: `YamlReader` keeps the text of each scalar but not whether it was quoted, so a tree read from text quotes every number and boolean when you print it again. This changes the types of the fields in a read-modify-write:

```scala mdoc
println(YamlReader.read("replicas: 3\n").print)
```

### Converting to JSON

`Yaml#toJson` transforms a `Yaml` node into a `Json` value for interoperability:

```scala mdoc
println(Yaml.Scalar("42").toJson.print)
```

---

## YamlCodec[A]

Main codec type for encoding and decoding values to and from YAML. Contains encoder and decoder for bidirectional serialization.

### Overview

`YamlCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in YAML format. The codec is derived automatically from a `Schema[A]`.

### Encoding Values to String

To convert a value to a YAML string:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Person(name: String, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec  = Person.schema.derive(YamlFormat)
val person = Person("Alice", "alice@example.com")
```

`YamlCodec#encodeToString` returns the YAML text of the record:

```scala mdoc
println(codec.encodeToString(person))
```

### Encoding Values to Byte Array

To convert a value to YAML bytes, encode it with `YamlCodec#encode`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Data(timestamp: Long, value: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(YamlFormat)
val bytes = codec.encode(Data(1700000000000L, "sample"))
```

The bytes are the UTF-8 text of the same document:

```scala mdoc
println(new String(bytes, "UTF-8"))
```

### Decoding Values from String

To convert a YAML string back to a value:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Config(host: String, port: Int)

object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

val codec = Config.schema.derive(YamlFormat)
val yaml = """
host: localhost
port: 8080
"""
```

`YamlCodec#decode` returns `Right` with the decoded value:

```scala mdoc
println(codec.decode(yaml))
```

### Decoding Values from Byte Array

To decode YAML bytes, pass the bytes to `YamlCodec#decode`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Settings(debug: Boolean, timeout: Int)

object Settings {
  implicit val schema: Schema[Settings] = Schema.derived
}

val codec    = Settings.schema.derive(YamlFormat)
val settings = Settings(debug = true, timeout = 30)
val bytes    = codec.encodeToString(settings).getBytes("UTF-8")
```

Decoding the bytes gives back the same value:

```scala mdoc
println(codec.decode(bytes))
```

---

## YamlCodecDeriver

Configuration and derivation system for creating `YamlCodec[A]` instances from `Schema[A]`.

### Overview

`YamlCodecDeriver` implements the schema-driven derivation of YAML codecs. It handles the ZIO Schema primitive types and complex types (records, variants, sequences, maps), generating the matching YAML scalars, mappings and sequences.

### How Derivation Works

To create a codec from a schema:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class User(id: Int, name: String, active: Boolean)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec = User.schema.derive(YamlFormat)
```

### Primitive Type Support

The deriver supports the ZIO Schema primitive types in these groups:

- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

A known bug in 0.0.56: `BigDecimal` and `BigInt` fields are written as quoted strings, although the JSON codec writes them as numbers. A YAML reader that follows the spec then sees a string. The `amount` field below is quoted:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Price(amount: BigDecimal, count: Int)

object Price {
  implicit val schema: Schema[Price] = Schema.derived
}

val codec = Price.schema.derive(YamlFormat)
```

```scala mdoc
println(codec.encodeToString(Price(BigDecimal("49.99"), 2)))
```

### Record Type Support

Case classes (records) are fully supported. Each field becomes a named key in the YAML mapping, and the key is the field name converted to kebab-case, so `zipCode` is written as `zip-code`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Address(street: String, city: String, zipCode: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(YamlFormat)
```

```scala mdoc
println(codec.encodeToString(Address("Unter den Linden 1", "Berlin", "10117")))
```

A known bug in 0.0.56: the derived codec always uses kebab-case keys and ignores field modifiers, so `@Modifier.rename` has no effect and a `@Modifier.transient` field is written and read. A YAML file with camelCase keys cannot be decoded into a case class with this codec. The JSON codec honours the same modifiers.

### Variant Type Support

Sealed traits and sum types are encoded as a mapping with one key, named after the case, and the case's fields nested under that key. There is no discriminator field:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

sealed trait Status
case class Running(pid: Int) extends Status
case class Stopped(exitCode: Int) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(YamlFormat)
```

```scala mdoc
println(codec.encodeToString(Stopped(1)))
```

A known bug in 0.0.56: a case with no fields, such as an enum value, is written as `Ebook: {}`, and a plain `Ebook` scalar in a YAML file fails to decode with `Expected mapping with single key for variant`. The guide [How to Read and Write YAML Documents](../../../guides/read-and-write-yaml.md) shows a `Schema[String].transform` workaround for enumerations.

---

## YamlOptions

Configuration for YAML output formatting. Controls indentation, document markers, and presentation style. Options apply only to `YamlWriter.write` and to `Yaml#print` and `Yaml#printPretty`; `YamlCodec#encodeToString` always uses the default style.

### Overview

`YamlOptions` has three settings:

| Setting           | Default | Effect                                                                     |
| ----------------- | ------- | -------------------------------------------------------------------------- |
| `indentStep`      | `2`     | Spaces per nesting level                                                   |
| `documentMarkers` | `false` | Start the output with a `---` line                                         |
| `flowStyle`       | `false` | Write every mapping as `{k: v}` and every sequence as `[a, b]` on one line |

`YamlOptions.default`, `YamlOptions.pretty` (document markers on) and `YamlOptions.flow` (flow style on) are ready-made values.

### Pretty-Printing Configuration

To write with four-space indentation and a leading `---` line, build the options and pass them to `YamlWriter.write`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Endpoint(path: String, method: String)

object Endpoint {
  implicit val schema: Schema[Endpoint] = Schema.derived
}

val codec    = Endpoint.schema.derive(YamlFormat)
val endpoint = codec.encodeValue(Endpoint("/health", "GET"))
```

```scala mdoc
println(YamlWriter.write(endpoint, YamlOptions(indentStep = 4, documentMarkers = true)))
```

### Flow Style

With `YamlOptions.flow`, every mapping and sequence is written on one line:

```scala mdoc
println(YamlWriter.write(endpoint, YamlOptions.flow))
```

A known bug in 0.0.56: flow style writes a string that contains a comma, `]` or `}` without quotes. Inside a flow collection those characters end the item, so the output is invalid YAML for other readers, and `YamlReader` reads it back as different data without an error. A title such as `Scala, the Language` comes back as `Scala`. Use flow style only for values that contain none of these characters.

---

## Error Handling

YAML decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as paths like `.field[0].nested.value` showing exactly where decoding failed:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Team(name: String, members: List[String])

object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

val codec = Team.schema.derive(YamlFormat)
val invalidYaml = """
name: Engineering
members: invalid
"""
```

The `members` field holds a scalar where a sequence is expected, so decoding fails at that field:

```scala mdoc
codec.decode(invalidYaml) match {
  case Right(team) => println(s"Loaded: $team")
  case Left(error) => println(s"Error: ${error.getMessage}")
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in scenarios where errors are expected and handled inline.

---

## YamlReader and YamlWriter

`YamlReader` parses YAML text into a `Yaml` tree, and `YamlWriter` writes a tree back to text. The codec uses both, and you can call them directly when you work with the tree instead of a case class.

### Reading and Writing the Tree

`YamlReader.read` returns the `Yaml` tree of a document, and `YamlWriter.write` prints it with the options of your choice:

```scala mdoc
println(YamlWriter.write(YamlReader.read("name: api\nport: 8080\n"), YamlOptions.default))
```

The reader does not keep the quotes of a scalar, and the writer quotes a number it reads, so the `8080` comes back quoted. This is the known bug described in [Printing YAML](#printing-yaml).

### Known Limits

The reader does not report most syntax errors. It stops at the first line it cannot read, returns the lines before it, and drops the rest without an error. A key without a space after its colon, such as `pageSize:20`, makes the reader drop that line and every line after it, so `ConfigSource.fromYaml` in `config-yaml` loses every later setting. The reader also accepts an unterminated `"`, `'`, `[` or `{` and drops the last character of the value.

The writer leaves some strings unquoted that other readers treat as YAML syntax. A string that starts with `#`, such as an OpenAPI `$ref` (`#/components/schemas/Book`), is read by other YAML readers as a comment, so the value is lost. A known bug in 0.0.56 also affects the `yaml"..."` interpolator: it does not compile on Scala 2.13 in user code, while it compiles on Scala 3.

The guide [How to Read and Write YAML Documents](../../../guides/read-and-write-yaml.md) has a table of the YAML forms that the reader and writer do not support, with what each one does:

[What schema-yaml Does Not Support](../../../guides/read-and-write-yaml.md#what-schema-yaml-does-not-support)

See also: [How to Write an OpenAPI Document and Export It as JSON or YAML](../../../guides/write-and-export-openapi.md) renders an OpenAPI document as YAML with `Yaml.fromJson`.
