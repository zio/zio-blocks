# ZIO Blocks Schema TOON

TOON (Token-Oriented Object Notation) codec support for ZIO Blocks Schema.

## Overview

This module provides automatic TOON serialization and deserialization for any type with a ZIO Blocks `Schema`. TOON is a line-oriented, indentation-based text format that encodes the JSON data model with explicit structure and minimal quoting. It is 30-60% more compact than JSON, making it particularly efficient for LLM prompts and responses.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "<version>"
```

## Usage

### Basic Usage

Define your data models with schemas, then derive TOON codecs:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive the TOON codec
val codec = Schema[Person].derive(ToonFormat)

// Encode to TOON
val person = Person("Alice", 30)
val toon: Array[Byte] = codec.encode(person)
// Output:
// name: Alice
// age: 30

// Decode from TOON
val decoded: Either[SchemaError, Person] = codec.decode(toon)
```

### Working with Collections

TOON supports multiple array formats:

```scala
case class User(id: Int, name: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

case class Team(members: List[User])
object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

// By default, object arrays use list format
val teamCodec = Schema[Team].derive(ToonFormat)
val team = Team(List(User(1, "Alice"), User(2, "Bob")))
// Output:
// members[2]:
//   - id: 1
//     name: Alice
//   - id: 2
//     name: Bob

// Use tabular format for compact representation of uniform objects
val tabularDeriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
val tabularCodec = Schema[Team].derive(tabularDeriver)
// Output:
// members[2]{id,name}:
//   1,Alice
//   2,Bob

case class Project(tags: List[String])
object Project {
  implicit val schema: Schema[Project] = Schema.derived
}

// Primitive arrays automatically use inline format
val projectCodec = Schema[Project].derive(ToonFormat)
val project = Project(List("scala", "zio", "toon"))
// Output:
// tags[3]: scala,zio,toon
```

### Customizing the Deriver

The `ToonBinaryCodecDeriver` provides several configuration options:

```scala
import zio.blocks.schema.toon._

// Custom deriver with snake_case field names
val customDeriver = ToonBinaryCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)
  .withDelimiter(Delimiter.Tab)
  .withDiscriminatorKind(DiscriminatorKind.Field("type"))

val codec = Schema[Person].derive(customDeriver)
```

#### Available Options

| Option | Description | Default |
|--------|-------------|---------|
| `withFieldNameMapper` | Transform field names (Identity, SnakeCase, KebabCase, etc.) | `Identity` |
| `withCaseNameMapper` | Transform variant/case names | `Identity` |
| `withDiscriminatorKind` | How to encode ADT discriminators (Key, Field, None) | `Key` |
| `withArrayFormat` | Array encoding format (Auto, Tabular, Inline, List) | `Auto` |
| `withDelimiter` | Delimiter for inline arrays (Comma, Tab, Pipe) | `Comma` |
| `withRejectExtraFields` | Error on unknown fields during decoding | `false` |
| `withEnumValuesAsStrings` | Encode enum values as strings | `true` |
| `withTransientNone` | Omit None values from output | `true` |
| `withRequireOptionFields` | Require optional fields during decoding | `false` |
| `withTransientEmptyCollection` | Omit empty collections from output | `true` |
| `withRequireCollectionFields` | Require collection fields during decoding | `false` |
| `withTransientDefaultValue` | Omit fields with default values from output | `true` |
| `withRequireDefaultValueFields` | Require fields with defaults during decoding | `false` |

#### Writer/Reader Options

These options apply to `WriterConfig` and `ReaderConfig` for fine-tuning encoding and decoding:

| Option | Description | Default |
|--------|-------------|---------|
| `withIndent` | Number of spaces per indentation level | `2` |
| `withKeyFolding` | Strategy for folding nested keys (Off, Safe) | `Off` |
| `withFlattenDepth` | Maximum depth for key folding | `Int.MaxValue` |
| `withExpandPaths` | Strategy for expanding dot-separated paths (Off, Safe) | `Off` |
| `withDelimiter` | Delimiter for inline arrays | `Comma` |
| `withStrict` | Enforce strict TOON parsing | `true` |
| `withDiscriminatorField` | Field name for DynamicValue variant discriminator | `None` |

### Reader and Writer Configuration

Fine-tune encoding and decoding behavior:

```scala
// Writer configuration (defaults: indent=2, keyFolding=Off, flattenDepth=MaxValue)
val writerConfig = WriterConfig
  .withIndent(4)                              // 4 spaces per indent level
  .withKeyFolding(KeyFolding.Safe)            // Use dot-separated keys for shallow nesting
  .withFlattenDepth(2)                        // Limit flattening depth
  .withDiscriminatorField(Some("type"))       // Discriminator field for DynamicValue variants

// Reader configuration (defaults: indent=2, strict=true, expandPaths=Off)
val readerConfig = ReaderConfig
  .withExpandPaths(PathExpansion.Safe)        // Parse dot-separated keys as nested records
  .withDelimiter(Delimiter.Tab)               // Expect tab-delimited inline arrays
  .withDiscriminatorField(Some("type"))       // Discriminator field for DynamicValue variants

// Use with codec
val encoded: Array[Byte] = codec.encode(person, writerConfig)
val decoded: Either[SchemaError, Person] = codec.decode(toonBytes, readerConfig)
```

### ADT Encoding

Algebraic data types can be encoded with different discriminator strategies:

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

// Key discriminator (default)
// Circle:
//   radius: 5

// Field discriminator
val fieldDeriver = ToonBinaryCodecDeriver
  .withDiscriminatorKind(DiscriminatorKind.Field("type"))
// type: Circle
// radius: 5
```

## TOON Format

TOON is a line-oriented format with these characteristics:

- **Indentation-based**: Objects use indentation instead of braces
- **Minimal quoting**: Strings are only quoted when necessary
- **Explicit array lengths**: Arrays declare their length upfront
- **Three array formats**:
  - Inline: `tags[3]: a,b,c`
  - Tabular: `users[2]{id,name}:` followed by rows
  - List items: `- item` for mixed content
- **No scientific notation**: Numbers are always in plain decimal form

Example TOON document:

```
name: Alice
age: 30
email: alice@example.com
tags[3]: scala,zio,functional
address:
  street: 123 Main St
  city: Springfield
orders[2]:
  - id: 1
    total: 99.99
  - id: 2
    total: 149.5
```

With `ArrayFormat.Tabular`, object arrays use compact tabular format:

```
orders[2]{id,total}:
  1,99.99
  2,149.5
```

## Supported Types

All ZIO Blocks Schema primitive types are supported:

- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`
- `BigInt`, `BigDecimal`
- `Unit`, `UUID`, `Currency`
- Java Time: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `DayOfWeek`, `Month`, `ZoneId`, `ZoneOffset`

Plus all derived types: records, enums, sequences, maps, options, and either.

## More Information

- [TOON Specification](https://github.com/toon-format/spec) - Official TOON specification
- [ZIO Blocks Homepage](https://zio.dev) - Main ZIO Blocks Homepage
