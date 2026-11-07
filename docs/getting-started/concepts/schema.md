---
id: schema
title: "Schema in a Nutshell"
sidebar_label: "Schema"
description: "The core idea of the Schema block: describe a data type once, then read and write it as JSON, YAML and other formats without writing codecs."
keywords:
  - "Getting Started"
  - "Schema"
  - "JSON"
  - "Codecs"
---

`Schema` describes the shape of a Scala data type: its fields, their types and its cases. You derive it once per type, and every format block reads that description to encode and decode your data. This is the central idea of ZIO Blocks, and many other blocks build on it.

## The Problem It Solves

In most Scala projects, each data format needs its own codec for each type: one for JSON, another for Avro, another for the database. The codecs repeat the same information, the shape of the type, and they drift apart when the type changes.

With Schema, the shape lives in one place. A change to a case class updates every format at once, because every format is derived from the same schema.

## Derive a Schema

We derive a schema by putting an implicit `Schema` in the companion object of the type. `Schema.derived` reads the fields at compile time:

```scala mdoc:silent
import zio.blocks.schema._

final case class Customer(name: String, email: String, vip: Boolean)

object Customer {
  implicit val schema: Schema[Customer] = Schema.derived
}
```

The same line works for sealed traits, nested case classes and collections; on Scala 3 you can also write `derives Schema` on the case class.

## Read and Write JSON

JSON support is part of the Schema block itself. With the schema in scope, a value encodes with `toJsonString`, and a string decodes with `fromJson`:

```scala mdoc
val alice = Customer("Alice", "alice@example.com", vip = true)

println(alice.toJsonString)

"""{"name":"Bob","email":"bob@example.com","vip":false}""".fromJson[Customer]
```

Decoding never throws for bad input. It returns an `Either`, and the message of the error names the field that failed:

```scala mdoc
"""{"name":"Carol","email":"carol@example.com","vip":"yes"}""".fromJson[Customer].left.map(_.getMessage)
```

## One Schema, Many Formats

Other formats are separate blocks that read the same schema. For example, after adding `zio-blocks-schema-yaml` to the build, we derive a YAML codec from the schema we already have, with no new code in the `Customer` type:

```scala mdoc:silent
import zio.blocks.schema.yaml.YamlFormat

val yamlCodec = Customer.schema.derive(YamlFormat)
```

The codec encodes `alice` to YAML bytes:

```scala mdoc
println(new String(yamlCodec.encode(alice), "UTF-8"))
```

Avro, BSON, CSV, MessagePack, Thrift, TOON and XML work the same way; [Built-in Formats and Codecs](../../reference/schema/built-in-codecs/index.md) lists them all.

## What Else a Schema Gives You

A schema is a full description of the type, so it powers more than codecs:

- [Validated types](../../guides/validated-newtypes.md) wrap a field in a rule with `Schema#transform`, so a value that breaks the rule fails to decode.
- [Optics](../../reference/schema/optics.md) read and update deeply nested fields without hand-written `copy` chains.
- [Patches](../../reference/schema/patch.md) compute the difference between two values and apply it elsewhere.
- [Schema evolution](../../reference/schema/schema-evolution/index.md) converts data between an old and a new version of a type.
- JSON Schema documents come from `Schema#toJsonSchema`, for APIs and payload validation.

Other blocks use the schema too: the [SQL](../../reference/sql/index.md) block maps case classes to table rows with it, and the [Config](../../reference/config/index.md) block decodes configuration files into case classes with it.

## Learn More

The [Your First Schema](../../guides/your-first-schema.md) tutorial walks through a complete round trip and reading decode errors, and the [Schema reference](../../reference/schema/index.md) documents every part of the block. The next concept in this section is [Scope](./scope.md).
