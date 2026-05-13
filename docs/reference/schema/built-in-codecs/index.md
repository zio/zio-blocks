---
id: index
title: "Built-in Formats and Codecs"
sidebar_label: "Built-in Formats and Codecs"
---

ZIO Blocks Schema provides automatic codec derivation for multiple serialization formats. Once you have a `Schema[A]` for your data type, you can derive codecs for any supported format using the unified `Schema.derive(Format)` pattern.

A `Format` is an abstraction that bundles together everything needed to serialize and deserialize data in a specific format (JSON, Avro, Protobuf, etc.).

## Overview

Each format defines the types of input for decoding and output for encoding, as well as the typeclass used as a codec for that format. Each format contains a `Deriver` corresponding to its specific MIME type, which is used to derive codecs from schemas:

```scala
trait Format {
  type DecodeInput
  type EncodeOutput
  type TypeClass[A] <: Codec[DecodeInput, EncodeOutput, A]
  def mimeType: String
  def deriver: Deriver[TypeClass]
}
```

It unifies all metadata related to serialization formats, such as MIME type and codec deriver, in a single place. This allows for a consistent API across different formats when deriving codecs from schemas. Having MIME type information helps with runtime content negotiation and format routing, for example in HTTP servers or message queues.

That is, you can easily call [`Schema[A].derive(format)`](../type-class-derivation.md#using-the-deriver-to-derive-type-class-instances) for any format that implements the `Format` trait, and receive a codec that can encode and decode values of type `A` according to the rules of that format.

Formats are categorized into `BinaryFormat` and `TextFormat`, which specify the types of input and output for encoding and decoding:

```scala
sealed trait Format
abstract class BinaryFormat[...](...) extends Format { ... }
abstract class TextFormat[...](...) extends Format { ... }
```

For example, the `JsonFormat` is a `BinaryFormat` that represents a JSON binary format, where the input for decoding is `ByteBuffer` and the output for encoding is also `ByteBuffer`, the MIME type is `application/json`, and the deriver for generating codecs from schemas is `JsonCodecDeriver`:

```scala
object JsonFormat extends BinaryFormat("application/json", JsonCodecDeriver)
```

## Built-in Codecs

Here's a summary of the codecs currently supported by ZIO Blocks. Each codec provides a `BinaryFormat` or `TextFormat` object that can be passed to `derive`. See the dedicated codec documentation for installation, usage examples, and detailed type mappings:

| Format Object       | Codec Type            | MIME Type             | Module                          | Documentation                   |
|---------------------|-----------------------|-----------------------|---------------------------------|---------------------------------|
| `JsonFormat`        | `JsonCodec[A]`        | `application/json`    | `zio-blocks-schema`             | [JSON](../json.md)              |
| `AvroFormat`        | `AvroCodec[A]`        | `application/avro`    | `zio-blocks-schema-avro`        | [Avro](./avro.md)               |
| `BsonFormat`        | `BsonCodec[A]`        | `application/bson`    | `zio-blocks-schema-bson`        | [BSON](./bson.md)               |
| `CsvFormat`         | `CsvCodec[A]`         | `text/csv`            | `zio-blocks-schema-csv`         | [CSV](./csv.md)                 |
| `MessagePackFormat` | `MessagePackCodec[A]` | `application/msgpack` | `zio-blocks-schema-messagepack` | [MessagePack](./messagepack.md) |
| `ThriftFormat`      | `ThriftCodec[A]`      | `application/thrift`  | `zio-blocks-schema-thrift`      | [Thrift](./thrift.md)           |
| `ToonFormat`        | `ToonCodec[A]`        | `text/toon`           | `zio-blocks-schema-toon`        | [TOON](./toon.md)               |
| `XmlFormat`         | `XmlCodec[A]`         | `application/xml`     | `zio-blocks-schema-xml`         | [XML](./xml.md)                 |
| `YamlFormat`        | `YamlCodec[A]`        | `application/yaml`    | `zio-blocks-schema-yaml`        | [YAML](./yaml.md)               |

## Defining a Custom Format

To add a new serialization format, define a `BinaryFormat` (or `TextFormat`) singleton with a custom `Deriver`:

```scala mdoc:compile-only
import zio.blocks.schema.codec.{BinaryCodec, BinaryFormat}
import zio.blocks.schema.derive.Deriver

// 1. Define your codec base class
abstract class MyCodec[A] extends BinaryCodec[A]

// 2. Implement a Deriver[MyCodec] (see Type-class Derivation docs)
// val myDeriver: Deriver[MyCodec] = ...

// 3. Create the format singleton
// object MyFormat extends BinaryFormat[MyCodec]("application/x-myformat", myDeriver)
```

For details on implementing a `Deriver`, see [Type-class Derivation](../type-class-derivation.md).

## Codec Derivation Pattern

All serialization formats in ZIO Blocks follow the same pattern: given a `Schema[A]`, you derive a codec by calling `derive` with a format object:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive codec for any format (using TOON as an example)
val codec = Schema[Person].derive(ToonFormat)

// Encode to bytes
val bytes: Array[Byte] = codec.encode(Person("Alice", 30))

// Decode from bytes
val result: Either[SchemaError, Person] = codec.decode(bytes)
```

## Supported Types

All formats support the full set of ZIO Blocks Schema primitive types:

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
- Sequences (`List`, `Vector`, `Set`, `Array`, etc.)
- Maps (`Map[K, V]`)
- Options (`Option[A]`)
- Eithers (`Either[A, B]`)
- Wrappers (newtypes)

## Cross-Platform Support

| Format      | JVM | Scala.js |
|-------------|-----|----------|
| JSON        | ✓   | ✓        |
| TOON        | ✓   | ✓        |
| MessagePack | ✓   | ✓        |
| Avro        | ✓   | ✗        |
| Thrift      | ✓   | ✗        |
| BSON        | ✓   | ✗        |

## Error Handling

All formats return `Either[SchemaError, A]` for decoding operations. Errors include path information for debugging, showing exactly where in nested structures a decoding failure occurred. See individual codec documentation for format-specific error details.
