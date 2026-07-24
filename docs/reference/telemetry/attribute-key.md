---
id: attribute-key
title: "AttributeKey"
description: "Type-safe key for Attributes storage in the ZIO Blocks Telemetry shared vocabulary — factory methods enforce compile-time type consistency between key and value."
keywords:
  - "AttributeKey"
  - "Type-safe Key"
  - "Attributes"
  - "AttributeType"
  - "Telemetry Vocabulary"
  - "OTel Semantic Conventions"
---

`AttributeKey[A]` is a type-safe key for use with `Attributes`. Each key carries the value type `A` as a phantom type parameter, so the compiler enforces that a key created with `AttributeKey.string` can only store `String` values, a key created with `AttributeKey.long` can only store `Long` values, and so on. This eliminates the runtime casting that would otherwise be needed when mixing typed telemetry values.

```scala
sealed trait AttributeKey[A] {
  def name: String
  def `type`: AttributeType
}
```

## Usage

The following example creates keys for four primitive types, stores them in an `Attributes` value, and retrieves them in a type-safe manner:

```scala
import zio.blocks.telemetry._

val envKey:  AttributeKey[String]  = AttributeKey.string("environment")
val portKey: AttributeKey[Long]    = AttributeKey.long("port")
val tlsKey:  AttributeKey[Boolean] = AttributeKey.boolean("tls.enabled")
val latKey:  AttributeKey[Double]  = AttributeKey.double("latency.ms")

val attrs: Attributes = Attributes.builder
  .put(envKey, "production")
  .put(portKey, 8443L)
  .put(tlsKey, true)
  .put(latKey, 3.7)
  .build

attrs.get(envKey)  // Some("production")
attrs.get(portKey) // Some(8443L)
attrs.get(tlsKey)  // Some(true)
attrs.get(latKey)  // Some(3.7)
```

`Attributes.of(key, value)` is a convenience constructor for single-entry attribute sets, useful when calling `Span#setAttribute` or `Counter#add` with a typed key.

## Factory Methods

| Method | Key type | Value type |
|---|---|---|
| `AttributeKey.string(name: String)` | `AttributeKey[String]` | `String` |
| `AttributeKey.boolean(name: String)` | `AttributeKey[Boolean]` | `Boolean` |
| `AttributeKey.long(name: String)` | `AttributeKey[Long]` | `Long` |
| `AttributeKey.double(name: String)` | `AttributeKey[Double]` | `Double` |
| `AttributeKey.stringSeq(name: String)` | `AttributeKey[Seq[String]]` | `Seq[String]` |
| `AttributeKey.longSeq(name: String)` | `AttributeKey[Seq[Long]]` | `Seq[Long]` |
| `AttributeKey.doubleSeq(name: String)` | `AttributeKey[Seq[Double]]` | `Seq[Double]` |
| `AttributeKey.booleanSeq(name: String)` | `AttributeKey[Seq[Boolean]]` | `Seq[Boolean]` |

:::tip
Define frequently used `AttributeKey` values as top-level `val`s. `AttributeKey.string("environment")` constructs a new object every call; sharing a single instance avoids repeated allocation and allows `Attributes` lookup by reference equality (`==` on the key name) to short-circuit early.
:::
