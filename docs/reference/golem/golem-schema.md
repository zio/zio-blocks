---
id: golem-schema
title: "GolemSchema"
---

`GolemSchema[A]` is the type class for encoding and decoding Scala types to Golem's structured value format. It provides the schema description, encoding to `StructuredValue`, and decoding back to Scala types. You don't implement `GolemSchema` directly; it derives automatically from `zio.blocks.schema.Schema[A]`.

```scala
trait GolemSchema[A] {
  def schema: StructuredSchema
  def encode(value: A): Either[String, StructuredValue]
  def decode(value: StructuredValue): Either[String, A]
  def elementSchema: ElementSchema
  def encodeElement(value: A): Either[String, ElementValue]
  def decodeElement(value: ElementValue): Either[String, A]
}
```

## Overview

Every type used in agent method parameters or return values must have a `GolemSchema`. The SDK derives it automatically from `zio.blocks.schema.Schema[A]`.

| Use Case | Derivation | Example |
|----------|-----------|---------|
| **Case classes** | `derives Schema` (Scala 3) | `case class User(name: String, age: Int) derives Schema` |
| **Scala 2** | `Schema.derived` | `implicit val schema: Schema[User] = Schema.derived` |
| **Custom types** | Manual implementation | Rare; see type class instances |
| **Collections** | Automatic | `List[A]`, `Option[A]`, `Map[K, V]` all derive |

Once `Schema[A]` exists, `GolemSchema[A]` is derived automatically in the agent registration macro.

## Derivation for Case Classes

Use `derives Schema` (Scala 3):

```scala
import zio.blocks.schema.Schema

case class User(name: String, age: Int) derives Schema
case class Order(id: String, items: List[String]) derives Schema
```

Or `Schema.derived` (Scala 2):

```scala
import zio.blocks.schema.Schema

case class User(name: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}
```

## Primitive Types

Primitives automatically have schemas:

```scala
import zio.blocks.schema.Schema

// All have implicit schemas
val intSchema: Schema[Int] = implicitly
val stringSchema: Schema[String] = implicitly
val boolSchema: Schema[Boolean] = implicitly
val doubleSchema: Schema[Double] = implicitly
```

## Collections and Optionals

Schemas for collections and optional types derive automatically:

```scala
import zio.blocks.schema.Schema
import zio.blocks.schema.Schema.derived

case class DataContainer(
  names: List[String],           // List[A] has schema if A has schema
  counts: Map[String, Int],      // Map[K, V] has schema if K, V have schemas
  maybeAge: Option[Int],         // Option[A] has schema if A has schema
  tuples: (String, Int)          // Tuples have schemas
) derives Schema
```

## Encoding and Decoding

Manually encode/decode (rarely needed; handled by the macro):

```scala
import golem.data.GolemSchema
import golem.data.StructuredValue

case class Person(name: String, age: Int) derives zio.blocks.schema.Schema

// Get the schema
val schema: GolemSchema[Person] = implicitly[GolemSchema[Person]]

// Encode to structured value
val person = Person("Alice", 30)
val encoded: Either[String, StructuredValue] = schema.encode(person)

// Decode back
val decoded: Either[String, Person] = encoded.flatMap(schema.decode(_))
```

## Structured vs. Element Encoding

Schemas support two encoding modes:

**Structured encoding** (for multi-field types):
```scala
import zio.blocks.schema.Schema

case class Point(x: Int, y: Int) derives Schema
// Encodes as a tuple: (x_value, y_value)
```

**Element encoding** (for single parameters):
```scala
import zio.blocks.schema.Schema

case class Point(x: Int, y: Int) derives Schema
// When used as a method parameter, encoded as a single element
// (not wrapped in a tuple)
```

The macro handles this automatically; you only need to know it exists.

## Custom Schema Implementations

For types where derive doesn't work, provide an implicit manually:

```scala
// For types where derive doesn't work, provide an implicit manually:
// import zio.blocks.schema.Schema
// 
// class CustomDatabase(val connectionString: String)
// implicit val customDbSchema: Schema[CustomDatabase] = // custom implementation
//
// case class DatabaseAgent(db: CustomDatabase) derives Schema
```

This is rare; most types should derive automatically.

## Multimodal Types

For data with text and binary components, use multimodal schemas:

```scala
import zio.blocks.schema.Schema
import golem.data.multimodal.Multimodal

case class Document(
  text: String,
  attachment: Multimodal
) derives Schema
```

Multimodal data handling is covered in the data types section.

## Unstructured Data

For data without a predefined structure (free-form text or binary):

```scala
import zio.blocks.schema.Schema
import golem.data.unstructured.Unstructured

case class AnalysisRequest(
  input: Unstructured.Text
) derives Schema

case class BinaryData(
  data: Unstructured.Binary
) derives Schema
```

Unstructured data preserves exact bytes/characters without schema interpretation.

## Composition and Nesting

Schemas compose naturally for nested types:

```scala
import zio.blocks.schema.Schema

case class Address(street: String, city: String) derives Schema

case class Person(
  name: String,
  address: Address  // Nested type; schema derives from Address's schema
) derives Schema

case class Team(
  members: List[Person],  // Collections of custom types work
  lead: Option[Person]    // Optional custom types work
) derives Schema
```

## Error Handling

Encoding/decoding can fail:

```scala
// Encoding/decoding with schemas:
// case class Strict(x: Int) derives zio.blocks.schema.Schema
// 
// val schema: GolemSchema[Strict] = implicitly
// 
// // Encoding succeeds (Scala values are valid)
// val encoded = schema.encode(Strict(42))
// // Right(StructuredValue(...))
// 
// // Decoding can fail (e.g., wrong type in structured value)
// val badDecoded = schema.decode(wrongValue)
// // Left("Type mismatch: expected Int, got String")
```

Always handle `Either` results when working with schemas directly.

## Relation to Other Types

- **`zio.blocks.schema.Schema`** — The underlying ZIO schema; `GolemSchema` derives from it
- **`StructuredSchema`, `StructuredValue`** — Low-level schema and value representations
- **`AgentDefinition`** — Uses `GolemSchema` to describe agent method signatures
- **`Multimodal`, `Unstructured`** — Specialized schema types for complex data

## Best Practices

- **Use `derives Schema` (Scala 3)** — Simplest, most idiomatic
- **Provide schemas for custom types early** — Fail fast at compile time, not runtime
- **Keep types serializable** — Avoid mutable state, side effects, functions in schema types
- **Test roundtrip encoding** — Verify encode → decode preserves semantics
- **Compose over custom schemas** — Build larger schemas from derived, smaller ones
