# Schema Migration System

A pure algebraic migration system for representing structural transformations between schema versions.

## Overview

The Schema Migration System provides a type-safe, serializable way to transform data between different schema versions. Unlike traditional migration approaches that rely on functions or reflection, this system represents migrations as pure data structures.

## Key Features

- **Pure Data**: Migrations contain no functions, closures, or reflection
- **Serializable**: All migrations can be serialized to JSON, MessagePack, etc.
- **Type-Safe**: `Migration[A, B]` provides compile-time type guarantees
- **Bidirectional**: Most migrations can be reversed
- **Composable**: Migrations can be composed with `++` operator
- **Inspectable**: Migration structure can be analyzed and debugged

## Core Components

### DynamicMigration

A schema-less migration that operates on `DynamicValue`:

```scala
val migration = DynamicMigration(
  MigrationAction.renameField(DynamicOptic.root, "oldName", "newName"),
  MigrationAction.addField(DynamicOptic.root, "email", SchemaExpr.Literal("", Schema[String]))
)

val result: Either[MigrationError, DynamicValue] = migration.apply(oldValue)
```

### Migration[A, B]

A typed migration wrapper:

```scala
val migration: Migration[UserV1, UserV2] = Migration
  .builder[UserV1, UserV2]
  .renameField("name", "fullName")
  .addField("email", SchemaExpr.Literal("", Schema[String]))
  .build

val result: Either[MigrationError, UserV2] = migration(userV1)
```

### MigrationAction

Represents a single structural transformation:

| Action | Forward | Reverse |
|--------|---------|---------|
| `AddField` | Add field with default | Drop field |
| `DropField` | Remove field | Add field with default |
| `RenameField` | Rename field | Rename back |
| `RenameCase` | Rename variant case | Rename back |
| `TransformValue` | Apply transform | Best-effort reverse |
| `MandateField` | Make required with default | Optionalize |
| `OptionalizeField` | Make optional | Mandate with default |

## Usage Examples

### Basic Field Migration

```scala
case class UserV1(name: String, age: Int)
case class UserV2(fullName: String, age: Int, email: String)

val migration = Migration.builder[UserV1, UserV2]
  .renameField("name", "fullName")
  .addField("email", SchemaExpr.Literal("", Schema[String]))
  .build

val v1 = UserV1("Alice", 30)
val v2 = migration(v1) // Right(UserV2("Alice", 30, ""))
```

### Nested Field Migration

```scala
val migration = Migration.builder[ConfigV1, ConfigV2]
  .renameField(DynamicOptic.root.field("settings"), "oldSetting", "newSetting")
  .build
```

### Enum Migration

```scala
sealed trait Status
case object Active extends Status
case object Inactive extends Status

sealed trait StatusV2
case object Enabled extends StatusV2
case object Disabled extends StatusV2

val migration = Migration.builder[Status, StatusV2]
  .renameCase("Active", "Enabled")
  .renameCase("Inactive", "Disabled")
  .build
```

### Migration Composition

```scala
val v1ToV2: Migration[V1, V2] = ???
val v2ToV3: Migration[V2, V3] = ???

val v1ToV3: Migration[V1, V3] = v1ToV2 ++ v2ToV3
```

### Bidirectional Migration

```scala
val forward: Migration[V1, V2] = Migration.builder[V1, V2].build
val backward: Migration[V2, V1] = forward.reverse
```

## Laws

Migrations obey the following laws:

1. **Identity**: `Migration.identity[A].apply(a) == Right(a)`
2. **Associativity**: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
3. **Structural Inverse**: `m.reverse.reverse == m` (for structural operations)

## Error Handling

Migration errors include path information for debugging:

```scala
val error: MigrationError = MigrationError.fieldNotFound("missingField", path)

error.message // "Field 'missingField' not found"
error.path    // Some(DynamicOptic(.user.settings))
```

Error types:
- `FieldNotFound`: Field doesn't exist in record
- `CaseNotFound`: Case doesn't exist in variant
- `TypeMismatch`: Value has wrong type
- `TransformFailed`: SchemaExpr evaluation failed
- `ValueNotFound`: Value not found at path
- `InvalidOperation`: Invalid migration operation
- `CompositionFailed`: Migration composition failed

## Serialization

All migrations are serializable because they contain only pure data:

```scala
import zio.blocks.schema.json._

val json = migration.toJson
val restored = JsonCodec[DynamicMigration].fromJson(json)
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Migration[A, B]                         │
│  (Typed wrapper with source and target schemas)              │
├─────────────────────────────────────────────────────────────┤
│                    DynamicMigration                          │
│  (Schema-less core, operates on DynamicValue)                │
├─────────────────────────────────────────────────────────────┤
│  MigrationAction                                             │
│  ├─ AddField         ├─ RenameCase                           │
│  ├─ DropField        ├─ TransformCase                        │
│  ├─ RenameField      ├─ TransformElements                    │
│  ├─ TransformValue   ├─ TransformKeys                        │
│  ├─ MandateField     └─ TransformValues                      │
│  └─ OptionalizeField                                         │
├─────────────────────────────────────────────────────────────┤
│  Supporting Types                                            │
│  ├─ DynamicOptic    (path representation)                    │
│  ├─ SchemaExpr      (value expressions)                      │
│  └─ MigrationError  (error handling with paths)              │
└─────────────────────────────────────────────────────────────┘
```

## Future Enhancements

1. **Macro-based Selectors**: Extract field names from lambda expressions
2. **Schema Validation**: Verify migrations are compatible with schemas
3. **Migration Registry**: Store and retrieve migrations by version
4. **Auto-migration**: Generate migrations from schema diffs
5. **Streaming Support**: Apply migrations to large datasets

## Related

- [DynamicValue](../DynamicValue.scala) - Schema-less value representation
- [DynamicOptic](../DynamicOptic.scala) - Path navigation
- [SchemaExpr](../SchemaExpr.scala) - Value expressions
- [DynamicPatch](../patch/DynamicPatch.scala) - Patch system
