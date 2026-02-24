---
id: migration
title: "Schema Migration"
sidebar_label: "Schema Migration"
---

ZIO Schema provides a pure, algebraic migration system that represents structural transformations between schema versions as first-class, serializable data.

## Overview

A migration describes how to transform data from one schema version to another, enabling:

- Schema evolution
- Backward / forward compatibility
- Data versioning
- Offline migrations (JSON, SQL, data lakes, registries, etc.)

## Core Types

### DynamicMigration

A pure, fully serializable representation of a migration:

```scala
final case class DynamicMigration(
  actions: Vector[MigrationAction]
)
```

### Migration[A, B]

A typed migration from type A to type B:

```scala
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
)
```

### MigrationAction

The fundamental unit of migration, representing a single transformation:

```scala
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}
```

## Supported Actions

### Record Actions

- **AddField** - Adds a new field with a default value
- **DropField** - Removes a field
- **Rename** - Renames a field
- **TransformValue** - Transforms a field value
- **Mandate** - Makes an optional field required
- **Optionalize** - Makes a required field optional
- **ChangeType** - Changes a field's type
- **Join** - Joins multiple fields into one
- **Split** - Splits a field into multiple

### Enum Actions

- **RenameCase** - Renames a case in a sum type
- **TransformCase** - Transforms a case using nested actions

### Collection Actions

- **TransformElements** - Transforms each element in a sequence
- **TransformKeys** - Transforms keys in a map
- **TransformValues** - Transforms values in a map

## Usage

### Basic Migration

```scala
import zio.blocks.schema.migration._
import zio.blocks.schema._

// Define your versions
type PersonV1 = { def name: String; def age: Int }
type PersonV2 = { def fullName: String; def age: Int; def country: String }

// Create schemas
implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]
implicit val v2Schema: Schema[PersonV2] = Schema.structural[PersonV2]

// Build migration
val migration = Migration
  .newBuilder(v1Schema, v2Schema)
  .addField(_.country, SchemaExpr.DefaultValue[PersonV2])
  .build

// Apply migration
val v1: PersonV1 = new { def name = "John"; def age = 30 }
val result = migration(v1)
```

### Composing Migrations

```scala
val m1 = Migration.newBuilder(v1Schema, v2Schema).build
val m2 = Migration.newBuilder(v2Schema, v3Schema).build

// Compose sequentially
val combined = m1 ++ m2
```

### Reversing Migrations

```scala
val reversed = migration.reverse
```

## Laws

### Identity

```scala
Migration.identity[A].apply(a) == Right(a)
```

### Associativity

```scala
(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
```

### Structural Reverse

```scala
m.reverse.reverse == m
```

## Error Handling

All runtime errors return `MigrationError` with path information:

```scala
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String
}
```

Errors include:
- `MissingField` - Required field is missing
- `TransformationFailed` - Transformation could not be applied
- `TypeMismatch` - Type conversion failed
- `ValidationError` - Validation failed
- `Unsupported` - Operation not supported

## Serialization

Since `DynamicMigration` is pure data, it can be serialized:

- To JSON for storage in registries
- To binary formats for efficient storage
- To database tables for versioning

## Best Practices

1. **Use structural types** for old versions to avoid runtime baggage
2. **Prefer additive changes** over destructive changes
3. **Provide default values** for new required fields
4. **Test migrations** thoroughly, especially reversals
5. **Version your migrations** in a registry
