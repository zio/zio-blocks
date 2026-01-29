# ZIO Blocks Schema MessagePack

MessagePack codec support for ZIO Blocks Schema.

## Overview

This module provides automatic MessagePack serialization and deserialization for any type with a ZIO Blocks `Schema`. MessagePack is an efficient binary serialization format that is more compact than JSON while remaining schema-less and cross-language compatible.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "<version>"
```

## Usage

### Basic Usage

Define your data models with schemas, then derive MessagePack codecs:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive the MessagePack codec
val codec = Schema[Person].derive(MessagePackFormat)

// Encode to MessagePack
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)

// Decode from MessagePack
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

### Working with Collections

```scala
case class User(id: Int, name: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

case class Team(members: List[User])
object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

val teamCodec = Schema[Team].derive(MessagePackFormat)
val team = Team(List(User(1, "Alice"), User(2, "Bob")))

// Encode
val bytes = teamCodec.encode(team)

// Decode
val decoded = teamCodec.decode(bytes)
```

### ADT Encoding

Algebraic data types are encoded with a variant index followed by the value:

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

val shapeCodec = Schema[Shape].derive(MessagePackFormat)

// Circle is encoded as: 0{radius: 5.0} (index 0 followed by the record)
val circle: Shape = Circle(5.0)
val bytes = shapeCodec.encode(circle)
```

## MessagePack Format

MessagePack is a binary format with these characteristics:

- **Compact**: More efficient than JSON, typically 50-80% of JSON size
- **Fast**: Binary format enables fast serialization/deserialization
- **Schema-less**: Self-describing format, no schema required for decoding
- **Cross-language**: Supported in 50+ programming languages
- **Type-rich**: Supports nil, boolean, integers, floats, strings, binary, arrays, and maps

### Encoding Conventions

| Scala Type | MessagePack Type |
|------------|------------------|
| `Unit` | nil |
| `Boolean` | bool |
| `Byte`, `Short`, `Int`, `Long` | int (variable width) |
| `Float` | float32 |
| `Double` | float64 |
| `String`, `Char` | str |
| `Array[Byte]` | bin |
| `List[A]`, `Vector[A]`, `Set[A]` | array |
| `Map[K, V]` | map |
| `Option[A]` | array (0 or 1 element) |
| `Either[A, B]` | map with "left" or "right" key |
| Case classes | map with field names as keys |
| Sealed traits | int index followed by value |

## Supported Types

All ZIO Blocks Schema primitive types are supported:

- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`
- `BigInt`, `BigDecimal`
- `Unit`, `UUID`, `Currency`
- Java Time: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `DayOfWeek`, `Month`, `ZoneId`, `ZoneOffset`

Plus all derived types: records, enums, sequences, maps, options, and either.

## Cross-Platform Support

This module is implemented in pure Scala with no platform-specific dependencies, supporting:

- JVM
- Scala.js
- Scala Native

## More Information

- [MessagePack Specification](https://github.com/msgpack/msgpack/blob/master/spec.md) - Official MessagePack specification
- [ZIO Blocks Homepage](https://zio.dev) - Main ZIO Blocks Homepage
