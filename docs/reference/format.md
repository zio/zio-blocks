---
id: formats
title: "Serialization Formats"
sidebar_label: "Format"
---

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

That is, you can easily call [`Schema[A].derive(format)`](./type-class-derivation.md#using-the-deriver-to-derive-type-class-instances) for any format that implements the `Format` trait, and receive a codec that can encode and decode values of type `A` according to the rules of that format.

Formats are categorized into `BinaryFormat` and `TextFormat`, which specify the types of input and output for encoding and decoding:

```scala
sealed trait Format
abstract class BinaryFormat[...](...) extends Format { ... }
abstract class TextFormat[...](...) extends Format { ... }
```

For example, the `JsonFormat` is a `BinaryFormat` that represents a JSON binary format, where the input for decoding is `Array[Byte]` and the output for encoding is also `Array[Byte]`, the MIME type is `application/json`, and the deriver for generating codecs from schemas is `JsonBinaryCodecDeriver`:

```scala
object JsonFormat extends BinaryFormat("application/json", JsonBinaryCodecDeriver)
```

## Built-in Formats

Here's a summary of the formats currently supported by ZIO Blocks. Each format provides a `BinaryFormat` object that can be passed to `derive`:

| Format      | Object              | MIME Type             |
|-------------|---------------------|-----------------------|
| JSON        | `JsonFormat`        | `application/json`    |
| TOON        | `ToonFormat`        | `text/toon`           |
| MessagePack | `MessagePackFormat` | `application/msgpack` |
| Avro        | `AvroFormat`        | `application/avro`    |
| Thrift      | `ThriftFormat`      | `application/thrift`  |
