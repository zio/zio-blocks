# ZIO Blocks Schema BSON

BSON (Binary JSON) codec support for ZIO Blocks Schema.

## Overview

This module provides automatic BSON serialization and deserialization for any type with a ZIO Blocks `Schema`. It leverages `zio-bson` `version 1.0.6` to interface directly with MongoDB and other BSON-native tools. This allows you to use your ZIO Schema data models directly with MongoDB drivers without boilerplate standard code.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "<version>"
```

## Usage

### Basic Usage

Define your data models with schemas, then derive BSON codecs:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.bson._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive the BSON codec
val codec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(Person.schema)

// Encode to BSON Value
val person = Person("Alice", 30)
val bson: BsonValue = codec.encoder.toBsonValue(person)
// Output: {"name": "Alice", "age": 30}

// Decode from BSON Value
val decoded: Person = codec.decoder.fromBsonValueUnsafe(bson, Nil, BsonDecoder.BsonDecoderContext.default)
```

### Configuration

You can customize the codec generation using `BsonSchemaCodec.Config`.

```scala
val config = BsonSchemaCodec.Config
  .withIgnoreExtraFields(false)          // Fail on unknown fields
  .withSumTypeHandling(                  // Customize ADT encoding
    BsonSchemaCodec.SumTypeHandling.DiscriminatorField("_type")
  )

val codec = BsonSchemaCodec.bsonCodec(Person.schema, config)
```

#### Available Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `withIgnoreExtraFields` | If true, extra fields in BSON are ignored. If false, decoding fails. | `true` |
| `withNativeObjectId` | If true, treats `org.bson.types.ObjectId` wrappers as native BSON ObjectIds. | `false` |
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
    You can force specific wrappers to be treated as ObjectIds using `withNativeObjectId(true)` in the config.

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

Use `@Modifier.alias` (often combined with `@rename`) to support multiple names during decoding. This is useful for migrating data without breaking compatibility.

```scala
// Encodes as "NewName"
// Decodes "NewName", "OldName", or "LegacyName"
@Modifier.rename("NewName")
@Modifier.alias("OldName")
@Modifier.alias("LegacyName")
case class MyClass(...)
```

## Supported Types

All ZIO Blocks Schema primitive types are supported and mapped to their BSON equivalents:

-   `Boolean`, `Int` (Int32), `Long` (Int64), `Double`
-   `String`, `Char`
-   `BigInt`, `BigDecimal` (Decimal128)
-   `UUID` (Binary)
-   `Instant`, `LocalDate`, `LocalDateTime` (DateTime)
-   `Option` (Null or missing)
-   `Collections` (BSON Arrays)
-   `Maps` (BSON Documents)
