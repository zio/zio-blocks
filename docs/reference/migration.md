---
id: migration
title: "Schema Migration"
sidebar_label: "Migration"
---

Schema migration is a pure, algebraic system for transforming data between schema versions. Unlike traditional migration approaches that rely on functions or closures, ZIO Blocks migrations are represented as **first-class, serializable data** that can be stored, inspected, and applied dynamically.

## Overview

When schemas evolve over time, you need to:

- Transform data from old versions to new versions
- Maintain backward/forward compatibility
- Apply migrations offline (JSON, SQL, data lakes)
- Inspect and validate migration logic

ZIO Blocks provides a two-layer migration architecture:

| Layer | Type | Description |
|-------|------|-------------|
| **Typed API** | `Migration[A, B]` | User-facing, compile-time validated |
| **Untyped Core** | `DynamicMigration` | Pure data, fully serializable |

## Key Features

- **Pure Data**: No functions, closures, or reflection - migrations are ADTs
- **Serializable**: Store migrations in registries, databases, or files
- **Introspectable**: Inspect, transform, and optimize migration logic
- **Bidirectional**: Every migration has a structural reverse
- **Compile-Time Safe**: Builder validates all fields are handled
- **Path-Based**: All actions specify location via `DynamicOptic`

## Quick Start

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Define your schema versions
case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, age: Int, country: String)

object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}
object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

// Build a migration with compile-time field tracking
val migration = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
  .renameField(select(_.name), select(_.fullName))
  .keepField(select(_.age))
  .addField(select(_.country), "US")
  .build

// Apply the migration
val v1 = PersonV1("Alice", 30)
val result = migration.apply(v1)
// Right(PersonV2("Alice", 30, "US"))
```

## Migration Builder

The `MigrationBuilder` provides a fluent API for constructing migrations with compile-time validation.

### Creating a Builder

Three methods are available for creating builders:

```scala
// Full compile-time field tracking (recommended)
val builder = MigrationBuilder.withFieldTracking[A, B]

// Explicit field names (for non-case-class types)
val builder = MigrationBuilder.withFields[A, B]("field1", "field2")("field1", "field2", "field3")

// No field tracking (for quick prototyping)
val builder = MigrationBuilder.create[A, B]
```

### Field Selector

The `select` macro extracts field names at compile time:

```scala
val nameSelector = select[Person](_.name)
// Type: FieldSelector[Person, String, "name"]

val ageSelector = select[Person](_.age)
// Type: FieldSelector[Person, Int, "age"]
```

### Record Operations

| Method | Description |
|--------|-------------|
| `addField(target, default)` | Add a new field with a default value |
| `dropField(source)` | Remove a field from the source |
| `dropFieldWithDefault(source, default)` | Remove a field, providing a default for reverse migration |
| `renameField(from, to)` | Rename a field |
| `keepField(field)` | Mark a field as unchanged |
| `changeFieldType(from, to, fromType, toType)` | Convert field type (primitive to primitive) |
| `mandateField(source, target, default)` | Convert `Option[T]` to `T` with default |
| `optionalizeField(source, target)` | Convert `T` to `Option[T]` |

### Enum Operations

| Method | Description |
|--------|-------------|
| `renameCase(from, to)` | Rename an enum case |

### Build Methods

| Method | Description |
|--------|-------------|
| `build` | Build with full validation (only compiles when all fields handled) |
| `buildPartial` | Build without validation (for partial migrations) |

## Migration Actions

All migrations are composed of pure data actions:

### Record Actions

```scala
// Add a new field
MigrationAction.AddField(at, fieldName, default)

// Remove a field
MigrationAction.DropField(at, fieldName, reverseDefault)

// Rename a field
MigrationAction.Rename(at, from, to)

// Transform a field's value
MigrationAction.TransformValue(at, fieldName, transform, reverseTransform)

// Convert Option[T] to T
MigrationAction.Mandate(at, fieldName, default)

// Convert T to Option[T]
MigrationAction.Optionalize(at, fieldName)

// Change field type (primitive conversions)
MigrationAction.ChangeType(at, fieldName, converter, reverseConverter)
```

### Enum Actions

```scala
// Rename a case
MigrationAction.RenameCase(at, from, to)

// Transform a case's content
MigrationAction.TransformCase(at, caseName, actions)
```

### Collection Actions

```scala
// Transform all elements in a sequence
MigrationAction.TransformElements(at, transform, reverseTransform)

// Transform all keys in a map
MigrationAction.TransformKeys(at, transform, reverseTransform)

// Transform all values in a map
MigrationAction.TransformValues(at, transform, reverseTransform)
```

## Resolved Expressions

Value transformations use `Resolved` expressions - pure data representations of computations:

```scala
// Literal value
Resolved.Literal(dynamicValue)
Resolved.Literal.int(42)
Resolved.Literal.string("hello")

// Identity (pass through)
Resolved.Identity

// Field access
Resolved.FieldAccess("fieldName", inner)

// Type conversion
Resolved.Convert("Int", "String", inner)

// String operations
Resolved.Concat(parts, separator)
Resolved.SplitString(separator, inner)

// Option handling
Resolved.WrapSome(inner)
Resolved.UnwrapOption(inner, default)
Resolved.GetOrElse(primary, fallback)

// Fallback
Resolved.Coalesce(alternatives)
Resolved.Fail(message)
```

## DynamicMigration

The untyped core that's fully serializable:

```scala
// Create from actions
val migration = DynamicMigration(Vector(
  MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(0)),
  MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
))

// Apply to dynamic value
val result = migration.apply(dynamicValue)

// Compose migrations
val combined = migration1 ++ migration2

// Reverse a migration
val reversed = migration.reverse

// Optimize redundant actions
val optimized = migration.optimize
```

## Typed Migration

The user-facing API with schema information:

```scala
// Create typed migration
val migration = Migration[PersonV1, PersonV2](
  dynamicMigration,
  schemaV1,
  schemaV2
)

// Apply to typed value
val result: Either[MigrationError, PersonV2] = migration.apply(v1)

// Compose typed migrations
val v1ToV3 = migration1 ++ migration2

// Reverse
val v2ToV1 = migration.reverse
```

## Primitive Conversions

Built-in support for converting between primitive types:

| From | To | Notes |
|------|-----|-------|
| Numeric types | Other numeric types | Widening always succeeds, narrowing validates range |
| String | Numeric types | Parses string representation |
| Numeric types | String | Converts to string |
| Boolean | Int | true → 1, false → 0 |
| Int | Boolean | 0 → false, non-zero → true |
| Char | Int | Character code |
| Int | Char | Validates range |
| String | UUID | Parses UUID format |
| UUID | String | Standard UUID string |
| String | Temporal types | ISO-8601 parsing |
| Temporal types | String | ISO-8601 formatting |

## Error Handling

All errors include path information for diagnostics:

```scala
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String
}

// Specific error types
MigrationError.PathNotFound(path)
MigrationError.TypeMismatch(path, expected, actual)
MigrationError.ConversionFailed(path, from, to, value)
MigrationError.ExpressionFailed(path, reason)
MigrationError.ExpectedRecord(path, actualType)
MigrationError.ExpectedVariant(path, actualType)
MigrationError.ExpectedSequence(path, actualType)
```

## Laws

Migrations satisfy important algebraic laws:

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

### Best-Effort Semantic Inverse

```scala
// When sufficient information exists:
m.apply(a) == Right(b) ⇒ m.reverse.apply(b) == Right(a)
```

## Complete Example

Here's a comprehensive example showing schema evolution:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Version 1: Original schema
case class UserV1(
  name: String,
  email: String,
  age: Int
)
object UserV1 {
  implicit val schema: Schema[UserV1] = Schema.derived
}

// Version 2: Evolved schema
case class UserV2(
  fullName: String,      // renamed from 'name'
  email: String,         // unchanged
  age: Long,             // changed type Int -> Long
  country: String,       // new field
  nickname: Option[String] // new optional field
)
object UserV2 {
  implicit val schema: Schema[UserV2] = Schema.derived
}

// Build the migration
val migration = MigrationBuilder.withFieldTracking[UserV1, UserV2]
  .renameField(select(_.name), select(_.fullName))
  .keepField(select(_.email))
  .changeFieldType(select(_.age), select(_.age), "Int", "Long")
  .addField(select(_.country), "US")
  .addField(select(_.nickname), None)
  .build

// Use the migration
val v1User = UserV1("Alice Smith", "alice@example.com", 30)
val v2User = migration.apply(v1User)
// Right(UserV2("Alice Smith", "alice@example.com", 30L, "US", None))

// Reverse migration (when possible)
val reversed = migration.reverse
v2User.flatMap(reversed.apply)
// Right(UserV1("Alice Smith", "alice@example.com", 30))
```

## Serialization

Since migrations are pure data, they can be serialized:

```scala
// DynamicMigration is a case class with Vector[MigrationAction]
// MigrationAction is a sealed trait of case classes
// Resolved is a sealed trait of case classes

// All can be serialized using any Schema-based codec:
val jsonCodec = Schema[DynamicMigration].derive(JsonFormat.deriver)
val json = jsonCodec.encode(migration.dynamicMigration)

// Store in a registry, database, or file
// Later, deserialize and apply
val restored = jsonCodec.decode(json)
```

## Migration Optimizer

The optimizer removes redundant operations:

```scala
// These are automatically optimized:
// - Rename A -> B followed by Rename B -> C becomes Rename A -> C
// - Add field followed by Drop same field is removed
// - Rename to same name is removed

val optimized = migration.optimize
```

## Comparison with Into/As

| Feature | Into/As | Migration |
|---------|---------|-----------|
| Direction | One-way or bidirectional | Always bidirectional |
| Representation | Type class with functions | Pure data ADT |
| Serializable | No | Yes |
| Introspectable | No | Yes |
| Path information | No | Yes |
| Compile-time validation | Yes | Yes |
| Use case | Simple conversions | Schema versioning, offline migrations |

Use `Into/As` for simple type conversions. Use `Migration` when you need:
- Serializable migration definitions
- Storage in registries or databases
- Generation of DDL or data transforms
- Full introspection of migration logic

## Limitations

### Cross-Branch Field Operations

**Joint and Split operations require fields to share the same parent path:**

```scala
// ✅ WORKS: Same parent (_.address)
.concat(
  select[V1](_.address.street), 
  select[V1](_.address.city),
  select[V2](_.address.fullAddress)
)

// ❌ DOES NOT WORK: Different parents (_.address vs _.origin)
.concat(
  select[V1](_.address.street),    // parent: _.address
  select[V1](_.origin.country),    // parent: _.origin
  select[V2](_.location.combined)  // ERROR: Runtime/compile-time error
)
```

**Workaround**: Lift values to a common parent (root level) first, then combine:

```scala
MigrationBuilder.withFieldTracking[V1, V2]
  .transformField(select(_.address.street), select(_.tempStreet), identity)
  .transformField(select(_.origin.country), select(_.tempCountry), identity)
  .concat(select(_.tempStreet), select(_.tempCountry), select(_.location.combined))
  .dropField(select(_.tempStreet), "")
  .dropField(select(_.tempCountry), "")
  .build
```

This limitation exists because operations across different nested structures require complex tree reconstruction logic. It may be addressed in a future version.

### Serialization Constraints

**DynamicMigration contains existential types that cannot be fully serialized:**

The `SchemaExpr[DynamicValue, ?]` type in `TransformValue` actions uses existential types for type safety. While the migration structure is pure data and can be inspected, full round-trip serialization/deserialization is not currently supported.

**What works:**
- Inspecting migration structure via `.describe()`
- Pattern matching on `MigrationAction` types
- Storing migrations as Scala source code

**What doesn't work:**
- Serializing migrations to JSON/binary and reconstructing them
- Sending migrations over the wire (RPC)
- Storing migrations in databases as data

This limitation affects migrations using `transformField` with custom expressions. Simple migrations using only `addField`, `dropField`, `renameField`, etc., with literal defaults can be serialized.
