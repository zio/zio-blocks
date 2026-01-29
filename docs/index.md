---
id: index
title: "ZIO Blocks"
---

**Modular, zero-dependency building blocks for modern Scala applications.**

@PROJECT_BADGES@

## What Is ZIO Blocks?

ZIO Blocks is a **family of type-safe, modular building blocks** for Scala applications. Each block is a standalone library with zero or minimal dependencies, designed to work with *any* Scala stack—ZIO, Cats Effect, Kyo, Ox, Akka, or plain Scala.

The philosophy is simple: **use what you need, nothing more**. Each block is independently useful, cross-platform (JVM, JS, Native), and designed to compose with other blocks or your existing code.

## The Blocks

| Block | Description | Status |
|-------|-------------|--------|
| **Schema** | Type-safe schemas with automatic codec derivation | ✅ Available |
| **Chunk** | High-performance immutable indexed sequences | ✅ Available |
| **Streams** | Pull-based streaming primitives | 🚧 In Development |
| **TypeId** | Type-safe unique identifiers | 📋 Planned |

## Core Principles

- **Zero Lock-In**: No dependencies on ZIO, Cats Effect, or any effect system. Use with whatever stack you prefer.
- **Modular**: Each block is a separate artifact. Import only what you need.
- **Cross-Platform**: Full support for JVM, Scala.js, and Scala Native.
- **Cross-Version**: Full support for Scala 2.13 and Scala 3.5+ with source compatibility—adopt Scala 3 on your timeline, not ours.
- **High Performance**: Optimized implementations that avoid boxing, minimize allocations, and leverage platform-specific features.
- **Type Safety**: Leverage Scala's type system for correctness without runtime overhead.

---

## Schema

The Schema block brings dynamic-language productivity to statically-typed Scala. Define your data types once, and derive codecs, validators, optics, and more automatically.

### The Problem

In statically-typed languages, you often maintain separate codec implementations for each data format (JSON, Avro, Protobuf, etc.). Meanwhile, dynamic languages handle data effortlessly:

```javascript
// JavaScript: one line and done
const data = await res.json();
```

In Scala, you'd typically need separate codecs for each format—a significant productivity gap.

### The Solution

ZIO Blocks Schema derives everything from a single schema definition:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive codecs for any format:
val jsonCodec    = Schema[Person].derive(JsonFormat)        // JSON
val avroCodec    = Schema[Person].derive(AvroFormat)        // Avro
val toonCodec    = Schema[Person].derive(ToonFormat)        // TOON (LLM-optimized)
val msgpackCodec = Schema[Person].derive(MessagePackFormat) // MessagePack
```

### Key Features

- **Universal Data Formats**: JSON, Avro, TOON (compact LLM-optimized format), with Protobuf, Thrift, BSON, and MessagePack planned.
- **High Performance**: Register-based design stores primitives directly in byte arrays, enabling zero-allocation serialization.
- **Reflective Optics**: Type-safe lenses, prisms, and traversals with embedded structural metadata.
- **Automatic Derivation**: Derive type class instances for any type with a schema.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Optional format modules:
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "@VERSION@"
```

### Example: Optics

```scala
import zio.blocks.schema._

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int] = $(_.age)
  val streetName: Lens[Person, String] = $(_.address.street)
}

val person = Person("Alice", 30, Address("123 Main St", "Springfield"))
val updated = Person.age.replace(person, 31)
```

---

## Chunk

A high-performance, immutable indexed sequence optimized for the patterns common in streaming, parsing, and data processing. Think of it as `Vector` but faster for the operations that matter most.

### Why Chunk?

Standard library collections make trade-offs that aren't ideal for streaming and binary data processing:

- `Vector` is general-purpose but not optimized for concatenation patterns
- `Array` is mutable and boxes primitives when used generically
- `List` has O(n) random access

Chunk is designed for:

- **Fast concatenation** via balanced trees (Conc-Trees)
- **Zero-boxing** for primitive types with specialized builders
- **Efficient slicing** without copying
- **Seamless interop** with `ByteBuffer`, `Array`, and standard collections

### Key Features

- **Specialized Builders**: Dedicated builders for `Byte`, `Int`, `Long`, `Double`, etc. avoid boxing overhead.
- **Balanced Concatenation**: Based on Conc-Trees for O(log n) concatenation while maintaining O(1) indexed access.
- **Bit Operations**: First-class support for bit-level operations, bit chunks backed by `Byte`, `Int`, or `Long` arrays.
- **NonEmptyChunk**: A statically-guaranteed non-empty variant for APIs that require at least one element.
- **Full Scala Collection Integration**: Implements `IndexedSeq` for seamless interop.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "@VERSION@"
```

### Example

```scala
import zio.blocks.chunk._

// Create chunks
val bytes = Chunk[Byte](1, 2, 3, 4, 5)
val moreBytes = Chunk.fromArray(Array[Byte](6, 7, 8))

// Efficient concatenation (O(log n))
val combined = bytes ++ moreBytes

// Zero-copy slicing
val slice = combined.slice(2, 6)

// Bit operations
val bits = bytes.asBitsByte
val masked = bits & Chunk.fill(bits.length)(true)

// NonEmptyChunk for type-safe non-emptiness
val nonEmpty = NonEmptyChunk(1, 2, 3)
val head: Int = nonEmpty.head  // Always safe, no Option needed
```

---

## Streams (In Development)

A pull-based streaming library for composable, backpressure-aware data processing.

```scala
import zio.blocks.streams._

// Coming soon: efficient pull-based streams
// that compose with any effect system
```

---

## Compatibility

ZIO Blocks works with any Scala stack:

| Stack | Compatible |
|-------|------------|
| ZIO 2.x | ✅ |
| Cats Effect 3.x | ✅ |
| Kyo | ✅ |
| Ox | ✅ |
| Akka | ✅ |
| Plain Scala | ✅ |

Each block has zero dependencies on effect systems. Use the blocks directly, or integrate them with your effect system of choice.

## Scala & Platform Support

ZIO Blocks supports **Scala 2.13** and **Scala 3.5+** with full source compatibility. Write your code once and compile it against either version—migrate to Scala 3 when your team is ready, not when your dependencies force you.

| Platform | Schema | Chunk | Streams |
|----------|--------|-------|---------|
| JVM | ✅ | ✅ | ✅ |
| Scala.js | ✅ | ✅ | ✅ |
| Scala Native | ✅ | ✅ | ✅ |
