# ZIO Blocks Schema BSON

BSON (Binary JSON) codec support for ZIO Blocks Schema.

> **Note:** This module is JVM-only. It does not support Scala.js.

## Overview

This module provides automatic BSON serialization and deserialization for any type with a ZIO Blocks `Schema`. It uses MongoDB's native BSON library (`org.mongodb:bson`) to interface directly with MongoDB and other BSON-native tools. This allows you to use your ZIO Schema data models directly with MongoDB drivers without boilerplate code.

For a task-oriented walkthrough that stores case classes in MongoDB, see [How to Store Case Classes in MongoDB](../docs/guides/store-case-classes-in-mongodb.md).

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "<version>"
```

This module depends on `org.mongodb:bson:5.13.0`, which is included transitively.

## Usage

### Basic Usage

Define your data models with schemas, then derive BSON codecs:

```scala
import org.bson.BsonValue
import zio.blocks.schema._
import zio.blocks.schema.bson._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive through the shared type-class derivation framework
val codec: BsonCodec[Person] = Person.schema.derive(BsonCodecDeriver)

// The compatibility facade delegates to the same deriver:
val compatibleCodec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(Person.schema)

// Encode to BSON Value
val person = Person("Alice", 30)
val bson: BsonValue = codec.encoder.toBsonValue(person)
// Output: {"name": "Alice", "age": 30}

// Decode from BSON Value
val decoded: Person = codec.decoder.fromBsonValueUnsafe(bson, Nil, BsonDecoder.BsonDecoderContext.default)
```

### Configuration

You can customize the deriver directly:

```scala
val deriver = BsonCodecDeriver
  .withIgnoreExtraFields(false)          // Fail on unknown fields
  .withSumTypeHandling(                  // Customize ADT encoding
    BsonSchemaCodec.SumTypeHandling.DiscriminatorField("_type")
  )

val codec = Person.schema.derive(deriver)
```

`BsonSchemaCodec.Config` remains supported by the compatibility facade. Because BSON implements `Deriver[BsonCodec]`, `Schema.deriving(BsonCodecDeriver)` also supports instance and modifier overrides for exact optics, types, and parent/term names.

#### Available Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `withIgnoreExtraFields` | If true, extra fields in BSON are ignored. If false, decoding fails. | `true` |
| `withNativeObjectId` | Has no effect in 0.0.56: an `ObjectId` is stored as a native BSON ObjectId whenever `ObjectIdSupport.objectIdSchema` is in scope, whatever the value. | `false` |
| `withSumTypeHandling` | Strategy for encoding Sealed Traits / Enums. | `WrapperWithClassNameField` |
| `withClassNameMapping` | Function to transform class names (e.g. for remapping legacy data). | `identity` |

### Sum Type Handling

Polymorphism (Sealed Traits) can be handled in three ways:

1.  **WrapperWithClassNameField** (Default):
    Wraps the data in an object keyed by the case class name.
    ```json
    { "Circle": { "radius": 10 } }
    ```

2.  **DiscriminatorField**:
    Adds a type tag field to the data itself.
    ```scala
    .withSumTypeHandling(SumTypeHandling.DiscriminatorField("type"))
    ```
    ```json
    { "type": "Circle", "radius": 10 }
    ```

3.  **NoDiscriminator**:
    Tries to decode as every possible case until one succeeds. Useful for untagged unions.
    ```json
    { "radius": 10 }
    ```

### MongoDB ObjectId Support

MongoDB `ObjectId` is supported out of the box.

1.  **Automatic Detection**:
    If you use `ObjectIdSupport.objectIdSchema`, the codec automatically detects the type name `org.bson.types.ObjectId` and uses the native MongoDB ObjectId BSON type (12-byte binary) instead of a string.

    ```scala
    import org.bson.types.ObjectId
    import zio.blocks.schema.bson.ObjectIdSupport._

    case class User(_id: ObjectId, name: String)
    object User {
      implicit val schema: Schema[User] = Schema.derived
    }
    // Result BSON: { "_id": ObjectId("..."), "name": "..." }
    ```

2.  **Manual Config**:
    `withNativeObjectId(true)` has no effect in 0.0.56, which is a known bug: an `ObjectId` is stored as a native ObjectId with or without the flag. Import `ObjectIdSupport.objectIdSchema` instead.

## Modifiers

You can use standard ZIO Blocks Schema modifiers to control the BSON serialization.

### Renaming Fields and Cases

Use `@Modifier.rename` to change the field key or class name in the BSON document.

```scala
case class User(
  @Modifier.rename("user_name") name: String,
  @Modifier.rename("user_age") age: Int
)
// Result BSON: { "user_name": "Alice", "user_age": 30 }
```

### Transient Fields

Use `@Modifier.transient` to exclude a field from the BSON document.
- **Encoding:** The field is completely skipped.
- **Decoding:** The field is set to its default value (required) or ignored if optional.

```scala
case class Session(
  id: String,
  @Modifier.transient cache: Map[String, Any] = Map.empty
)
// Encoding Session("123", Map(...)) -> { "id": "123" }
// Decoding { "id": "123" } -> Session("123", Map.empty)
```

### Aliases (Migration Support)

Use `@Modifier.alias` (often combined with `@rename`) on a case of a sealed trait to support multiple names during decoding. This is useful for migrating data without breaking compatibility.

```scala
sealed trait Shape

// Encodes as {"Round": {"r": 2}}
// Decodes {"Round": ...} or {"Circle": ...}
@Modifier.rename("Round")
@Modifier.alias("Circle")
case class Ring(r: Int) extends Shape
```

On a plain record, a class-level `@Modifier.rename` or `@Modifier.alias` has no effect, because the codec writes the fields of a record and not its class name. Put a rename on a field with `@Modifier.rename`, as shown above, and use `@Modifier.alias` only on sealed-trait cases.

## Supported Types

All ZIO Blocks Schema primitive types are supported and mapped to their BSON equivalents:

-   `Boolean`, `Int` (Int32), `Long` (Int64), `Double`
-   `String`, `Char`
-   `BigInt`, `BigDecimal` (Decimal128)
-   `UUID` (Binary)
-   `Instant`, `LocalDate`, `LocalDateTime` (DateTime)
-   `Option`, stored as a sub-document: `Some("x")` is `{"Some": {"value": "x"}}` and `None` is `{"None": {}}`. A known bug in 0.0.56: a field that holds BSON `null`, a plain value or no value does not decode as an `Option`.
-   `Collections` (BSON Arrays)
-   `Maps` (BSON Documents)
-   `zio.blocks.schema.json.Json` (semantic BSON documents, arrays, and scalar values)

Semantic `Json` conversion preserves finite BSON double values except for signed zero, normalizes BSON signed zero to JSON zero, and rejects duplicate object field names that `BsonDocument` cannot represent.
