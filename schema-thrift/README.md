# ZIO Blocks Schema Thrift

Apache Thrift binary codec support for ZIO Blocks Schema.

## Overview

This module provides automatic Apache Thrift binary serialization and deserialization for any type with a ZIO Blocks `Schema`. Apache Thrift is a high-performance binary serialization framework originally developed at Facebook, widely used for RPC and data interchange in distributed systems.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift" % "<version>"
```

**Note:** This module is JVM-only as it depends on the Apache Thrift library.

## Usage

### Basic Usage

Define your data models with schemas, then derive Thrift codecs:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift.ThriftFormat

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive the Thrift codec
val codec = Schema[Person].derive(ThriftFormat)

// Encode to Thrift binary
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)

// Decode from Thrift binary
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

### Using ByteBuffer

For scenarios requiring `ByteBuffer` (e.g., NIO channels), you can use the buffer-based API:

```scala
import java.nio.ByteBuffer

// Encode to ByteBuffer
val buffer = ByteBuffer.allocate(1024)
Schema[Person].encode(ThriftFormat)(buffer)(Person("Alice", 30))
buffer.flip()

// Decode from ByteBuffer
val result: Either[SchemaError, Person] = Schema[Person].decode(ThriftFormat)(buffer)
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

val teamCodec = Schema[Team].derive(ThriftFormat)
val team = Team(List(User(1, "Alice"), User(2, "Bob")))

// Encode
val bytes = teamCodec.encode(team)

// Decode
val decoded = teamCodec.decode(bytes)
```

### ADT Encoding

Algebraic data types (sealed traits) are encoded using a single-field struct where the field ID represents the variant index:

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

val shapeCodec = Schema[Shape].derive(ThriftFormat)

// Circle is encoded with field ID 1 (first variant)
val circle: Shape = Circle(5.0)
val bytes = shapeCodec.encode(circle)
```

## Thrift Format Characteristics

The codec uses Thrift's TBinaryProtocol with these characteristics:

- **Field ID-based**: Fields are identified by numeric IDs (1-based), not by name
- **Out-of-order decoding**: Fields can arrive in any order on the wire
- **Schema evolution**: Unknown fields are gracefully skipped, enabling forward compatibility
- **Compact integers**: Uses variable-width encoding for smaller integers
- **Fast**: Binary format enables efficient serialization/deserialization
- **Compatible**: Wire-compatible with Apache Thrift 0.22.0

## Type Mapping

| Scala Type | Thrift Type |
|------------|-------------|
| `Unit` | void (no bytes) |
| `Boolean` | bool |
| `Byte` | byte (i8) |
| `Short`, `Char` | i16 |
| `Int` | i32 |
| `Long` | i64 |
| `Float`, `Double` | double |
| `String` | string |
| `BigInt` | binary |
| `BigDecimal` | struct (mantissa, scale, precision, rounding) |
| `List[A]`, `Vector[A]`, `Set[A]` | list |
| `Map[K, V]` | map |
| `Option[A]` | list (0 or 1 element) |
| `Either[A, B]` | struct with "left" or "right" field |
| Case classes | struct with field IDs |
| Sealed traits | struct with single field (variant index) |

### Date/Time Types

All Java Time types are encoded as strings (ISO-8601 format) except:

| Scala Type | Thrift Type |
|------------|-------------|
| `DayOfWeek` | byte (1-7) |
| `Month` | byte (1-12) |
| `Year` | i32 |
| `ZoneOffset` | i32 (total seconds) |

## Supported Types

All ZIO Blocks Schema primitive types are supported:

- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`
- `BigInt`, `BigDecimal`
- `Unit`, `UUID`, `Currency`
- Java Time: `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `DayOfWeek`, `Month`, `ZoneId`, `ZoneOffset`

Plus all derived types: records, enums, sequences, maps, options, and either.

## Platform Support

This module is **JVM-only** because it depends on the Apache Thrift library (`libthrift`), which is a Java library.

## More Information

- [Apache Thrift](https://thrift.apache.org/) - Official Apache Thrift website
- [Thrift Protocol Specification](https://github.com/apache/thrift/blob/master/doc/specs/thrift-binary-protocol.md) - Binary protocol specification
- [ZIO Blocks Homepage](https://zio.dev) - Main ZIO Blocks Homepage
