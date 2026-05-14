---
id: golem-schema
title: "GolemSchema"
---

`GolemSchema[A]` is the type class for encoding and decoding Scala types to Golem's structured value format. It provides the schema description, encoding to `StructuredValue`, and decoding back to Scala types. You don't implement `GolemSchema` directly; it derives automatically from `zio.blocks.schema.Schema[A]`.

```text
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

Every type used in agent method parameters or return values must have a `GolemSchema`. The SDK derives it automatically from `zio.blocks.schema.Schema[A]`. Once `Schema[A]` exists, `GolemSchema[A]` is derived automatically in the agent registration macro.

## Derivation for Case Classes

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
  <TabItem value="scala3" label="Scala 3" default>

```scala mdoc:passthrough
import zio.blocks.schema.Schema

case class User(name: String, age: Int) derives Schema
case class Order(id: String, items: List[String]) derives Schema
```

  </TabItem>
  <TabItem value="scala2" label="Scala 2">

```scala mdoc:passthrough
import zio.blocks.schema.Schema

case class User(name: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}
```

  </TabItem>
</Tabs>

## Encoding and Decoding

Manually encode/decode (rarely needed; handled by the macro):

```scala mdoc:passthrough
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

## Multimodal Types

For data with text and binary components, use multimodal schemas:

```scala mdoc:passthrough
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

```scala mdoc:passthrough
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

```scala mdoc:passthrough
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

```scala mdoc:passthrough
// Encoding/decoding with schemas:
case class Strict(x: Int) derives zio.blocks.schema.Schema

val schema: GolemSchema[Strict] = implicitly

// Encoding succeeds (Scala values are valid)
val encoded = schema.encode(Strict(42))
// Right(StructuredValue(...))

// Decoding can fail (e.g., wrong type in structured value)
val wrongValue = StructuredValue.String("invalid")
val badDecoded = schema.decode(wrongValue)
// Left("Type mismatch: expected Int, got String")
```

Always handle `Either` results when working with schemas directly.
