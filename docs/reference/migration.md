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

## Nested Migrations

For nested types, use `inField()` with a pre-built `Migration` object. This approach ensures compile-time validation at all nesting levels:

```scala
case class PersonV0(name: String, address: AddressV0)
case class AddressV0(street: String)

case class PersonV1(name: String, address: AddressV1)
case class AddressV1(street: String, city: String)

// First, build the nested migration
val addressMigration = MigrationBuilder.withFieldTracking[AddressV0, AddressV1]
  .keepField(select(_.street))
  .addField(select(_.city), "Unknown")
  .build

// Then compose into the parent migration
val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
  .keepField(select(_.name))
  .inField(select(_.address), select(_.address))(addressMigration)
  .build
```

The nested migration's actions are automatically prefixed with the field path using `DynamicOptic.field()`, ensuring correct application at any nesting depth. This design addresses the maintainer's requirement for "lists of lists" where the top level encodes depth and the bottom level encodes operations.

Additional methods for other nested contexts:
- `inElements()` - For migrating sequence/list elements
- `inMapValues()` - For migrating map values
- `inCase()` - For migrating variant/enum cases

## Serialization

Since migrations are pure data, they are **fully serializable**. Complete `Schema` instances are provided for all migration types:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Schema instances are provided implicitly
// - Schema[DynamicMigration]
// - Schema[MigrationAction] (12 variants)
// - Schema[Resolved] (20 variants including RootAccess and At)
// - Schema[DynamicOptic]

// Serialize to JSON
val jsonCodec = JsonCodec.from(Schema[DynamicMigration])
val json = jsonCodec.encode(migration.dynamicMigration)

// Store in a registry, database, or file
// Later, deserialize and apply
val restored = jsonCodec.decode(json)
restored.flatMap(_.apply(dynamicValue))

// Round-trip is fully supported
val original = DynamicMigration(Vector(
  MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(0)),
  MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
))
val roundTripped = jsonCodec.decode(jsonCodec.encode(original))
assert(roundTripped == Right(original))
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

## Cross-Branch Field Operations

ZIO Blocks migrations support combining or splitting fields across different branches of a document. This is achieved through two key expression types: `RootAccess` and `At`.

### Joining Fields from Different Branches

Use `joinFields` to combine fields from different parts of the document:

```scala
case class AddressV1(street: String)
case class OriginV1(country: String)
case class PersonV1(address: AddressV1, origin: OriginV1)

case class LocationV2(combined: String)
case class PersonV2(address: AddressV1, origin: OriginV1, location: LocationV2)

val migration = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
  .keepField(select(_.address))
  .keepField(select(_.origin))
  .joinFields(
    sources = Vector(
      select[PersonV1](_.address.street),
      select[PersonV1](_.origin.country)
    ),
    target = select[PersonV2](_.location.combined),
    separator = ", "
  )
  .build

// Input: PersonV1(AddressV1("123 Main St"), OriginV1("USA"))
// Output: PersonV2(AddressV1("123 Main St"), OriginV1("USA"), LocationV2("123 Main St, USA"))
```

### Splitting Fields to Different Branches

Use `splitField` to divide a single field into multiple target locations:

```scala
case class FullNameV1(value: String)
case class PersonV1(fullName: FullNameV1)

case class NameV2(firstName: String, lastName: String)
case class PersonV2(name: NameV2)

val migration = MigrationBuilder.withFieldTracking[PersonV1, PersonV2]
  .splitField(
    source = select[PersonV1](_.fullName.value),
    targets = Vector(
      select[PersonV2](_.name.firstName),
      select[PersonV2](_.name.lastName)
    ),
    separator = " "
  )
  .build

// Input: PersonV1(FullNameV1("John Doe"))
// Output: PersonV2(NameV2("John", "Doe"))
```

> **Note**: `splitField` operations are NOT reversible. The reverse migration will fail.
> For tracked field removal, use `splitFieldTracked` which provides compile-time tracking.

### RootAccess Expression

`RootAccess` enables accessing values from the root document, regardless of the current context:

```scala
// Access root.external from within a nested transformation
val expr = Resolved.RootAccess(DynamicOptic.root.field("external"))

// Use in an AddField action
MigrationAction.AddField(
  DynamicOptic.root,
  "combined",
  Resolved.Concat(
    Vector(
      Resolved.RootAccess(DynamicOptic.root.field("address").field("street")),
      Resolved.Literal.string(", "),
      Resolved.RootAccess(DynamicOptic.root.field("origin").field("country"))
    ),
    ""
  )
)
```

### At Expression

`At` extracts an element at a specific index from a sequence:

```scala
// Get the second element (index 1) from a split string
val splitAndExtract = Resolved.At(
  1,
  Resolved.SplitString(" ", Resolved.FieldAccess("fullName", Resolved.Identity))
)
```

### Nested Path Support

The `select` macro now captures the full path for nested field access:

```scala
// Captures full path: address → street
val selector = select[Person](_.address.street)
selector.optic  // DynamicOptic.root.field("address").field("street")
selector.name   // "street" (leaf field name)
```

This enables cross-branch operations to correctly navigate to deeply nested fields.
