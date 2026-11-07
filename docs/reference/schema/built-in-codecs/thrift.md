---
id: thrift
title: "Thrift Codec Module"
---

`zio-blocks-schema-thrift` is a **schema-driven Thrift codec module** for serializing and deserializing Scala types to and from Thrift binary format. It provides comprehensive encoding and decoding with support for 30 primitive types, records, variants, sequences, maps, and recursive types. Core types: `ThriftCodec`, `ThriftCodecDeriver`, `ThriftFormat`.

The module integrates with Apache Thrift to provide native binary serialization using TBinaryProtocol with automatic schema generation and optimized performance for distributed systems.

## Motivation

Thrift is a compact binary serialization format that appears widely across distributed systems, microservices, and real-time messaging. Manually writing Thrift encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-thrift` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Blocks Schema. You describe your data shape once, and the module handles:
- Full Thrift type support (all primitives, records, unions, arrays, maps)
- No `.thrift` file needed: the codec derives the Thrift layout from your Scala types (it generates no IDL)
- Optimized encoding with minimal overhead using TBinaryProtocol
- Precise error reporting with location traces showing the path to errors
- Recursive type support, such as trees
- Multiple encoding paths: ByteBuffer and byte arrays
- Multiple decoding paths: ByteBuffer and byte arrays
- JVM support (not available for Scala.js)

Rather than writing custom encoders or relying on code generation from .thrift files, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift" % "@VERSION@"
```

**Note:** This module is JVM-only and is not available for Scala.js.

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for Thrift codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `Schema.derive(ThriftFormat)` to obtain a `ThriftCodec[A]`
3. **Encode or decode** — Call `codec.encode(value)` or `codec.decode(bytes)`
4. **Handle errors** — `decode` returns the `SchemaError`, with the path to the bad value, in a `Left`

The derivation process is automatic for all supported types (all 30 primitives, records, variants, sequences, maps). The module handles encoding and decoding without a `.thrift` file or manual configuration.

## How They Work Together

The Thrift codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(ThriftFormat) creates ThriftCodec[A]
                 ↓
3. ThriftCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific Thrift encoders/decoders
   - For records: field-by-field composition with Thrift struct encoding
   - For variants: a struct with one field, whose id is the case index plus one
   - For sequences: array encoding/decoding
   - For maps: map encoding/decoding
                 ↓
4. ThriftCodec provides multiple encoding paths
   - encode(value) → Array[Byte]
   - encode(value, buffer: ByteBuffer) → Unit
                 ↓
5. ThriftCodec provides multiple decoding paths
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
   - decode(buffer: ByteBuffer) → Either[SchemaError, A]
                 ↓
6. TBinaryProtocol manages I/O with Thrift wire format
   - Efficient binary encoding with type information
   - Always `TBinaryProtocol`; nothing detects or switches the protocol
   - Support for unknown fields (schema evolution)
                 ↓
7. Errors include location traces
   Shows path (.field.at(index).nested) to error location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
Schema[Person].derive(ThriftFormat) → ThriftCodec[Person]
    ↓
Use codec.encode(person) to serialize → Array[Byte]
Use codec.decode(bytes) to deserialize → Either[SchemaError, Person]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`ThriftCodec[A]`** — Main public API; contains encoder and decoder for bidirectional serialization
- **`ThriftCodecDeriver`** — Configuration and derivation system; generates codecs from Schema
- **`ThriftFormat`** — Integration with the ZIO Blocks Schema format system; enables `Schema[A].derive(ThriftFormat)`

## Common Patterns

This section shows practical patterns for working with Thrift codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Simple Record


To derive a Thrift codec for a record type and encode a value:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(ThriftFormat)
val person = Person("Alice", 30, "alice@example.com")
val bytes = codec.encode(person)
```

### Pattern 2: Decode Thrift with Error Handling

When decoding Thrift data, errors include location traces showing where the problem occurred.

To decode bytes and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Employee(id: Int, name: String, salary: Double)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec = Employee.schema.derive(ThriftFormat)
val bytes = Array[Byte](1, 2, 3) // truncated data

val result = codec.decode(bytes)

result match {
  case Right(employee) => println(s"Decoded: $employee")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Pattern 3: Handle Recursive Types

Recursive types (types that reference themselves) are supported: a recursive type refers to itself by name. The codec does not detect cycles in values, and a case class value cannot be cyclic anyway.

To define and encode a recursive data structure:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

sealed trait Node
case class Leaf(value: String) extends Node
case class Branch(left: Node, right: Node) extends Node

object Node {
  implicit val schema: Schema[Node] = Schema.derived
}

val codec = Node.schema.derive(ThriftFormat)
val tree: Node = Branch(Leaf("A"), Branch(Leaf("B"), Leaf("C")))
val bytes = codec.encode(tree)
```

### Pattern 4: Work with Variants (Sealed Traits)

A variant is a Thrift struct with one field. The field id is the case index plus one, so the case name is not written.

To derive a codec for a sealed trait:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(ThriftFormat)
val status: Status = Active("2024-01-01")
val bytes = codec.encode(status)
```

---

## ThriftCodec[A]

Main codec type for encoding and decoding values to and from Thrift binary format. Contains encoder and decoder for bidirectional serialization.

### Overview

`ThriftCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in Thrift binary format. The codec is derived automatically from a `Schema[A]` using `ThriftFormat`.

### Encoding Values to Byte Array

Use the codec to convert values to byte arrays:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Product(name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(ThriftFormat)
val product = Product("Widget", 9.99)
val bytes = codec.encode(product)
```

### Encoding Values to ByteBuffer

Write encoded values directly to a ByteBuffer:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._
import java.nio.ByteBuffer

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = Item.schema.derive(ThriftFormat)
val item = Item("Gadget", 42)
val buffer = ByteBuffer.allocate(1024)
codec.encode(item, buffer)
val bytes = java.util.Arrays.copyOf(buffer.array, buffer.position)
```

### Decoding Values from Byte Array

Use the codec to convert byte arrays back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Record(id: Int, value: String)

object Record {
  implicit val schema: Schema[Record] = Schema.derived
}

val codec = Record.schema.derive(ThriftFormat)
val record = Record(123, "test")
val buffer = java.nio.ByteBuffer.allocate(256)
codec.encode(record, buffer)
buffer.flip()
val bytes = new Array[Byte](buffer.remaining())
buffer.get(bytes)

val result: Either[zio.blocks.schema.SchemaError, Record] = codec.decode(bytes)
```

### Decoding Values from ByteBuffer

Read and decode values from a ByteBuffer:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._
import java.nio.ByteBuffer

case class Data(timestamp: Long, payload: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(ThriftFormat)
// Use encoded data from a previous encoding
val data = Data(System.currentTimeMillis(), "example")
val encBuffer = java.nio.ByteBuffer.allocate(256)
codec.encode(data, encBuffer)
encBuffer.flip()
val result = codec.decode(encBuffer)
```

---

## ThriftCodecDeriver

Configuration and derivation system for creating `ThriftCodec[A]` instances from `Schema[A]`.

### Overview

`ThriftCodecDeriver` implements the schema-driven derivation of Thrift codecs. It automatically handles 30 primitive types and complex types (records, variants, sequences, maps), generating appropriate Thrift encoders and decoders.

### How Derivation Works

To create a codec from a schema:

```scala mdoc:reset
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(ThriftFormat)
```

### Primitive Type Support

All 30 primitives of ZIO Blocks Schema are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

`Float` is written as a Thrift `double`. `UUID`, `Currency` and most of the `java.time` types are Thrift strings. A `DynamicValue` can be encoded but not decoded.

### Record Type Support

Case classes (records) are fully supported. Each field becomes a numbered field of the Thrift struct (ids 1, 2, 3 and so on, in declaration order; the name is not written). The decoder accepts the fields in any order and skips ids it doesn't know:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(ThriftFormat)
```

Known bugs in 0.0.56 that matter when you read data you did not write yourself:

- A record field missing from the input decodes as `null`, `0` or `false` instead of failing, so a missing `Option` field comes back as `null`, not `None`.
- The decoder does not check the Thrift type written before a field, so a field of another type can decode without an error as a wrong value.
- `decode` ignores any bytes after the first value.

### Variant Type Support

A sealed trait is encoded as a struct with one field whose id is the index of the case plus one (in declaration order). The case name is not written:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(ThriftFormat)
```

---

## ThriftFormat

Integration point with the ZIO Blocks Schema format system. Provides `BinaryFormat[ThriftCodec]` to enable `Schema[A].derive(ThriftFormat)` for any supported type.

### Using ThriftFormat

To derive a Thrift codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(ThriftFormat)
```

`ThriftFormat` is a singleton object extending `BinaryFormat[ThriftCodec]` with the MIME type `"application/thrift"` and the `ThriftCodecDeriver` as its derivation strategy.

---

## Error Handling

Thrift decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as `DynamicOptic` paths such as `.field.at(0).nested.value`: a record field is `.name`, a sequence index is `.at(0)`, a map value is `.atKey(...)` and a variant case is `.when[Case]`. Errors from the Thrift library start with `Thrift protocol error:`. The path shows exactly where decoding failed:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.thrift._

case class Contact(emails: Seq[String])

object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

val codec = Contact.schema.derive(ThriftFormat)
// Example invalid Thrift bytes
val invalidBytes: Array[Byte] = Array(0xFF.toByte, 0xFF.toByte)

val result = codec.decode(invalidBytes)

result match {
  case Right(contact) => println(s"Success: $contact")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in streaming scenarios where errors are expected and handled inline.
