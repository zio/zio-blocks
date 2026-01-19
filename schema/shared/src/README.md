# ZIO Blocks Schema Migration

A pure, algebraic migration system for ZIO Blocks Schema 2.

## Overview

This module provides a mechanism to transform data structurally between schema versions without requiring runtime representations of older data types. It treats migrations as **first-class, serializable data**, enabling schema evolution, backward/forward compatibility, and offline data transformations.

Unlike traditional migration tools, this system uses **structural types** for past versions and **macros** for validation, ensuring zero runtime overhead for deprecated models.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-migration" % "<version>"

```

## Usage

### Basic Usage

Define your past data version using structural types and your current version using a case class. Then, define a migration path between them using the type-safe DSL.

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._
import zio.schema.Schema

// 1. Define the Old Version (Structural Type - No runtime class needed)
type UserV1 = { def name: String; def age: Int }
implicit val schemaV1: Schema[UserV1] = Schema.structural[UserV1]

// 2. Define the Current Version
case class UserV2(fullName: String, age: Int, active: Boolean)
object UserV2 {
  implicit val schema: Schema[UserV2] = Schema.derived
}

// 3. Define the Migration Logic
val migration = Migration.newBuilder[UserV1, UserV2]
  .renameField(_.name, _.fullName)           // Rename 'name' to 'fullName'
  .addField(_.active, SchemaExpr.Literal(true)) // Add new field 'active' with default
  .build

// 4. Run the Migration
val oldData = DynamicValue.fromSchemaAndValue(schemaV1, new { val name = "Alice"; val age = 30 })
val result  = migration(oldData)

// Output: Right(UserV2("Alice", 30, true))

```

### The Migration Builder

The `MigrationBuilder` provides a fluent API to describe transformations. All selector paths (e.g., `_.address.city`) are validated at compile-time via macros.

```scala
val complexMigration = Migration.newBuilder[OrderV1, OrderV2]
  .transformField(
    from = _.total,
    to   = _.totalAmount,
    expr = price => price * 1.2  // Apply transformation logic
  )
  .dropField(_.internalId)       // Remove obsolete fields
  .optionalizeField(_.notes, _.notes) // Change T -> Option[T]
  .build

```

## Migration Operations

The following algebraic operations are supported and can be composed:

| Operation | Description |
| --- | --- |
| **addField** | Adds a new field to the target schema with a default value. |
| **dropField** | Removes a field from the source schema. |
| **renameField** | Renames a field (Source Path -> Target Path). |
| **transformField** | Transforms a value using a pure expression. |
| **mandateField** | Converts an `Option[T]` to `T` by providing a default. |
| **optionalizeField** | Converts a `T` to `Option[T]`. |
| **changeType** | Changes field type (e.g., `Int` -> `Long`) via conversion. |

## Pure Data & Serialization

One of the core features of this module is that migrations are **pure data**. They do not contain arbitrary functions or closures, making them fully serializable.

This allows you to store migrations in a database, registry, or file system and apply them dynamically.

```scala
import java.io._

// 1. Serialize the migration plan
val bytes = serialize(migration.dynamicMigration)

// 2. Store or transmit 'bytes' (e.g., save to disk)

// 3. Deserialize and apply later
val loadedMigration = deserialize[DynamicMigration](bytes)
val migratedData = MigrationEngine.run(inputData, loadedMigration)

```

## Enum & Collection Support

The engine supports complex transformations beyond simple records.

### Enum Migrations

You can rename or transform cases within Algebraic Data Types (ADTs).

```scala
.renameCase(from = "User", to = "Member")
.transformCase(_.asUser, actions => actions.addField( ... ))

```

### Collection Transformations

Apply logic to elements inside Lists, Maps, or Sets using nested selectors.

```scala
.transformElements(
  at = _.tags, 
  transform = tag => tag.toUpperCase
)

```

## Supported Types

The system supports all standard ZIO Schema types:

* **Primitives:** `Boolean`, `Int`, `Long`, `String`, `UUID`, etc.
* **Collections:** `List`, `Vector`, `Map`, `Set`, `Option`.
* **Structures:** Recursive Records and Enums (ADTs).

## More Information

* [ZIO Schema Documentation](https://zio.dev/zio-schema)
* [ZIO Blocks Homepage](https://zio.dev)