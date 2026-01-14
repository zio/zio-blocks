# ZIO Schema TOON Format

[![CI](https://github.com/zio/zio-blocks/workflows/CI/badge.svg)](https://github.com/zio/zio-blocks/actions)
[![Apache 2.0 License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](../LICENSE)

High-performance [TOON Format](https://github.com/toon-format/spec) codec for [ZIO Schema 2](https://github.com/zio/zio-schema), optimized for LLM consumption and human readability.

## What is TOON?

TOON (Token-Oriented Object Notation) is a serialization format designed for:
- **LLM-Friendly**: Reduced token usage compared to JSON, optimized for language model contexts
- **Human-Readable**: Clean, indented structure with minimal syntax noise
- **Array-Efficient**: Tabular and inline formats for compact array encoding
- **Type-Safe**: Integration with ZIO Schema's compile-time type derivation

This implementation follows [TOON Format Specification v3.0](https://github.com/toon-format/spec) and is validated with tests from the official test suite.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-toon" % "VERSION"
```

## Quick Start

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

// Define your data model
case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Create codec and encode
val person = Person("Alice", 30, "alice@example.com")
val codec = Person.schema.derive(ToonFormat.deriver)
val toon = codec.encodeToString(person)

println(toon)
// Output:
// name: Alice
// age: 30
// email: alice@example.com

// Decode back
val decoded = codec.decodeFromString(toon)
// Right(Person("Alice", 30, "alice@example.com"))
```

## Features

### Primitive Types
All primitive types encode in their most compact form:
- **Numbers**: Decimal form only (no scientific notation per TOON spec)
- **Strings**: Unquoted unless containing special characters
- **Booleans**: `true` / `false`
- **Unit/Null**: `null`

### Supported Types
- Basic: `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`
- Big numbers: `BigInt`, `BigDecimal`
- Date/Time: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Duration`, `Period`, etc.
- Other: `UUID`, `Currency`
- Collections: `List[A]`, `Vector[A]`, `Set[A]`, `Map[K, V]`

### Array Formats

TOON automatically selects the most efficient array encoding based on your data:

#### 1. Tabular Format (for uniform records)

```scala
case class Point(x: Int, y: Int)
case class Polygon(name: String, vertices: List[Point])

val triangle = Polygon("triangle", List(Point(0,0), Point(1,0), Point(0,1)))
```

**Encoded as:**
```
name: triangle
vertices[3]{x,y}:
  0,0
  1,0
  0,1
```

#### 2. Inline Format (for primitives)

```scala
case class Numbers(values: List[Int])
val data = Numbers(List(1, 2, 3, 4, 5))
```

**Encoded as:**
```
values[5]: 1,2,3,4,5
```

#### 3. List Format (for complex/mixed types)

```scala
case class Task(name: String, priority: Int, tags: List[String])
val tasks = List(Task("Fix bug", 1, List("urgent")), Task("Review", 2, List("pending")))
```

**Encoded as:**
```
tasks[2]:
- name: Fix bug
  priority: 1
  tags[1]: urgent
- name: Review
  priority: 2
  tags[1]: pending
```

### Example: JSON vs TOON

**JSON**:
```json
{"users":[{"id":1,"name":"Alice"},{"id":2,"name":"Bob"},{"id":3,"name":"Carol"}]}
```

**TOON**:
```
users[3]{id,name}:
  1,Alice
  2,Bob
  3,Carol
```

## Configuration

Configure the deriver for customized encoding:

```scala
val customDeriver = ToonBinaryCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)
  .withArrayFormat(ArrayFormat.Tabular)
  .withDelimiter('\t')

val codec = Schema.derived[MyType].derive(customDeriver)
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `fieldNameMapper` | Identity | Transform field names |
| `caseNameMapper` | Identity | Transform variant case names |
| `arrayFormat` | Auto | Array encoding style |
| `delimiter` | `,` | Array value separator |
| `discriminatorKind` | Key | ADT encoding strategy |
| `enableKeyFolding` | false | Dotted key expansion |

## Cross-Platform Support

Full production support for all Scala platforms:

| Platform | Status | Notes |
|----------|--------|-------|
| **JVM** | ✅ Fully Supported | Java 11+ |
| **Scala.js** | ✅ Fully Supported | 1.x |
| **Scala Native** | ✅ Fully Supported | 0.5.x with BigDecimal workaround |

## Testing

Run tests:
```bash
# All platforms
sbt test

# Specific platforms
sbt testJVM
sbt testJS
sbt testNative

# Lint
sbt scalafmtCheck
```

**Test Suites**:
- ToonDerivedSpec - Array-focused codec derivation (36 tests)
- ToonSpecComplianceTests - Official TOON v3.0 compliance (59 tests)
- ToonTabularSpec - Tabular array format optimization
- ToonLoadSpec - Large dataset and deep nesting
- ToonPropertySpec - Property-based testing
- ToonUtf8Spec - Unicode/emoji support
- ToonDiscriminatorSpec - Sealed trait encoding
- ToonRemediationSpec - Edge case coverage
- And more...

## Links

- [TOON Format Specification v3.0](https://github.com/toon-format/spec)
- [ZIO Schema Documentation](https://zio.dev/zio-schema)
- [API Documentation](https://javadoc.io/doc/dev.zio/zio-schema-toon_3/latest)
- [Issue Tracker](https://github.com/zio/zio-blocks/issues)

## Contributing

Contributions are welcome! See [ATTRIBUTION.md](ATTRIBUTION.md) for acknowledgments.

When contributing:
1. ✅ Maintain TOON v3.0 specification compliance
2. ✅ Add tests with exact match assertions (`assertTrue(encoded == expected)`)
3. ✅ Ensure cross-platform compatibility (JVM + JS + Native)
4. ✅ Run `sbt scalafmtAll` before committing

## Attribution

This implementation acknowledges:

1. **Official TOON Specification** ([github.com/toon-format/spec](https://github.com/toon-format/spec))
   - Apache 2.0 License
   - Test fixtures for v3.0 compliance validation

2. **toon4s Library** ([github.com/vim89/toon4s](https://github.com/vim89/toon4s))
   - Apache 2.0 License
   - Test patterns and codec design inspiration

Full attribution details in [ATTRIBUTION.md](ATTRIBUTION.md).

## License

Copyright 2026 ZIO Contributors

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for details.
