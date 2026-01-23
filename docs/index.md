---
id: index
title: "ZIO Blocks"
---

**Powerful, joyful building blocks for modern cloud-native applications.**

@PROJECT_BADGES@

## Overview

In today's distributed systems, you're constantly serializing, deserializing, validating, and transforming data across network boundaries. ZIO Blocks eliminates the boilerplate and gives you **one schema, infinite possibilities**. It provides powerful building blocks for modern cloud-native applications by making data handling in Scala as productive as dynamic languages, while maintaining all the benefits of static typing. It is derived from our experience developing ZIO Schema, but with a complete redesign to address its shortcomings.

## The Problem

Modern cloud-native development presents unique challenges. Data wrangling involves multiple transformations at both application boundaries and within request/response cycles. On the incoming side, HTTP requests are decoded, JSON is parsed, data is deserialized into type-safe models, validated against business rules, and finally processed by business logic. On the outgoing side, the reverse occurs: business logic produces data that must be serialized, encoded, and transmitted as HTTP responses. This repetitive cycle of data transformation consumes significant effort that could be better spent on actual business logic.

In statically-typed languages, the problem intensifies. You must maintain codec implementations for multiple data formats (JSON, Avro, Protobuf, etc.), each requiring redundant serialization/deserialization. Meanwhile, dynamic languages like JavaScript handle data effortlessly without this overhead. For example, when you receive an HTTP response in JavaScript, you can parse JSON into a dynamic object immediately:

```javascript
// In JavaScript:
const data = await res.json(); // Done!
```

In Scala, you'd typically need to define codecs, handle type conversions separately for each format—a significant productivity gap.

## The Solution

ZIO Blocks brings dynamic-language productivity to statically-typed Scala by deriving everything from a single schema definition:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Get everything for free:
val jsonCodec = Schema[Person].derive(JsonFormat.deriver)      // JSON serialization
val avroCodec = Schema[Person].derive(AvroFormat.deriver)      // Avro serialization
val toonCodec = Schema[Person].derive(ToonFormat.deriver)      // TOON serialization
val bsonCodec = Schema[Person].derive(BsonFormat.deriver)      // BSON serialization
val protobuf  = Schema[Person].derive(ProtobufFormat.deriver)  // Protobuf serialization (not implemented yet)
val thrift    = Schema[Person].derive(ThriftFormat.deriver)    // Thrift serialization (not implemented yet)
// ...
```

## Key Features

Here are the key features that make ZIO Blocks stand out:

1. **Zero Dependencies**: ZIO Blocks has no dependencies on the ZIO ecosystem, making it a universal schema library for Scala that works seamlessly with Akka, Typelevel, Kyo, or any other Scala stack.
2. **High Performance**: ZIO Blocks uses a novel register-based design that stores primitives directly in byte arrays and objects in separate arrays, avoiding intermediate heap allocations and object boxing. This architecture enables zero-allocation serialization and deserialization.
3. **Universal Data Formats**: Provides automatic serialization and deserialization across multiple formats:
   - **JSON** – Fast, type-safe JSON handling
   - **Avro** – Apache Avro binary format
   - **TOON** – Compact, LLM-optimized format
   - **Protobuf** – Protocol Buffers
   - **Thrift** – Apache Thrift
   - **BSON** – MongoDB's binary JSON format
   - **MessagePack** – Efficient binary serialization
4. **Reflective Optics**: Combines traditional optics with embedded structural metadata that captures the actual structure of your data types. This enables type-safe introspection, writing DSLs, and dynamic customization of your data models.
5. **Automatic Derivation**: By implementing a few core methods, you can automatically derive type class instances for all your types, eliminating boilerplate code generation.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

Now you have access to the core ZIO Blocks schema library. You can also add additional modules for specific formats:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-json" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "@VERSION@"
```

## Example

Assume we have the following data models:

```scala mdoc:silent
case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)
```

We can define schemas for them as follows:

```scala
import zio.blocks.schema._

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
}
```

Now with this schema, we derive codecs, validators, optics, and more for free!

For example, here is en example of defining a `Lens` optic:

```scala
object Person extends CompanionOptics[Person] {
 implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int] = $(_.age)
  val streetName: Lens[Person, String] = $(_.address.street)
}
```

Then we can do data access and modification like this:

```scala
val person = Person("John", 25, Address("123 Main St", "Exampleville"))

// Use for access/modification
val updated = Person.age.replace(person, 42)

println(s"Original age: ${person.age}, Updated age: ${updated.age}")
```
