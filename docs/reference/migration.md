---
id: migration
title: "Schema Migration"
sidebar_label: "Schema Migration"
---

Schema migration provides a declarative, serializable system for transforming data as schemas evolve over time. Unlike the automatic `Into`/`As` type classes, migrations are represented as data structures that can be serialized, stored, versioned, and introspected at runtime.

## Overview

When your application evolves, you need to migrate existing data to new schema versions. ZIO Blocks provides two complementary approaches:

| Approach | Type | Use Case |
|----------|------|----------|
| **`Into`/`As`** | Type classes | Automatic compile-time conversions |
| **`Migration`** | Data structures | Declarative, serializable, versionable migrations |

### When to Use Migrations

Use the migration system when you need:

- **Serializable transformations**: Store migration definitions alongside data versions
- **Runtime introspection**: Inspect what transformations will be applied
- **Automatic reversal**: Generate inverse migrations automatically
- **Audit trail**: Track what transformations were applied to data
- **Database migrations**: Apply transformations to persisted data
- **Versioned data storage**: Maintain multiple schema versions with explicit migrations

### When to Use Into/As

Use `Into`/`As` type classes when you need:

- **Simple automatic conversions**: Let the compiler figure out the transformation
- **Compile-time only**: No need to serialize or inspect migrations
- **Quick prototyping**: Get started fast without defining explicit migrations

## Quick Start

### Basic Migration

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Version 1 of your data
case class PersonV1(name: String, age: Int)
object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }

// Version 2 adds a country field
case class PersonV2(fullName: String, age: Int, country: String)
object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }

// Create migration using selector-based API
val migration = MigrationBuilder(PersonV1.schema, PersonV2.schema)
  .renameField(_.name, _.fullName)  // Type-safe field selection
  .addField(_.country, "USA")       // Add field with default value
  .build                            // Compile-time validation

// Apply migration
val v1 = PersonV1("Alice", 30)
val v2: Either[SchemaError, PersonV2] = migration.migrate(v1)
// Right(PersonV2("Alice", 30, "USA"))

// Reverse migration (automatically generated)
val reversed: Either[SchemaError, PersonV1] = migration.reverse.migrate(v2.toOption.get)
// Right(PersonV1("Alice", 30))
```

## Builder API

The `MigrationBuilder` provides two complementary APIs for constructing migrations.

### Selector-Based API (Recommended)

Type-safe, IDE-friendly API using selector functions:

```scala
val migration = MigrationBuilder(sourceSchema, targetSchema)
  .renameField(_.name, _.fullName)           // Type-safe field selection
  .addField(_.country, "USA")                // Compile-time field validation
  .dropField(_.oldField)                     // IDE autocomplete support
  .optionalizeField(_.middleName)            // Make field optional
  .build                                     // Compile-time validation
```

**Benefits:**
- **Type safety**: Compile errors if fields don't exist
- **IDE support**: Autocomplete and refactoring work seamlessly
- **Macro-powered**: Field names extracted at compile-time from lambda expressions
- **Cross-platform**: Works on both Scala 2.13 and Scala 3.x

### String-Based API (Alternative)

Traditional string-based API for dynamic scenarios:

```scala
val migration = MigrationBuilder(sourceSchema, targetSchema)
  .renameFieldByName("name", "fullName")
  .addFieldWithDefault("country", "USA")
  .dropFieldByName("oldField")
  .optionalizeFieldByName("middleName")
  .build
```

Use the string-based API when:
- Field names come from configuration or user input
- Building migrations dynamically at runtime
- Working with schemas not known at compile-time

## Compile-Time Validation

The `build()` method validates at compile-time that migrations are complete:

```scala
case class Source(name: String, age: Int, email: String)
case class Target(fullName: String, age: Int)

// This will fail to compile - missing field mappings
val migration = MigrationBuilder(Source.schema, Target.schema)
  .renameField(_.name, _.fullName)
  .build  // ❌ Compile error: field 'email' not handled, field 'age' not produced
```

The macro checks:
- All source fields are handled (renamed, dropped, or implicitly carried over)
- All target fields are produced (added, renamed, or implicitly carried over)
- Provides clear error messages showing exactly what's missing

Use `buildUnchecked()` to skip validation when needed:

```scala
val migration = MigrationBuilder(sourceSchema, targetSchema)
  .renameFieldByName("name", "fullName")
  .buildUnchecked  // No compile-time validation
```

## Migration Actions

The migration system supports 14 action types covering records, variants, and collections.

### Record Actions

#### Add Field

Add a new field with a default value:

```scala
// Selector API
.addField(_.country, "USA")
.addField(_.score, 100)
.addField(_.active, true)

// String API
.addFieldWithDefault("country", "USA")
```

#### Drop Field

Remove an existing field:

```scala
// Selector API
.dropField(_.oldField)

// String API
.dropFieldByName("oldField")
```

#### Rename Field

Rename a field:

```scala
// Selector API
.renameField(_.name, _.fullName)

// String API
.renameFieldByName("name", "fullName")
```

#### Optionalize Field

Make a mandatory field optional:

```scala
// Selector API
.optionalizeField(_.middleName)

// String API
.optionalizeFieldByName("middleName")
```

Transforms `String` → `Option[String]`, wrapping the value in `Some`.

#### Mandate Field

Make an optional field mandatory with a default value:

```scala
// Selector API
.mandateField(_.email, "unknown@example.com")

// String API
.mandateFieldByName("email", "unknown@example.com")
```

Transforms `Option[String]` → `String`, using the default when `None`.

### Advanced Examples

#### Nested Migrations

Migrate nested structures:

```scala
case class AddressV1(street: String, city: String)
case class AddressV2(street: String, city: String, country: String)

case class PersonV1(name: String, address: AddressV1)
case class PersonV2(name: String, address: AddressV2)

// Migrate nested address
val addressMigration = MigrationBuilder(AddressV1.schema, AddressV2.schema)
  .addField(_.country, "USA")
  .build

val personMigration = MigrationBuilder(PersonV1.schema, PersonV2.schema)
  .transformField(_.address, addressMigration)
  .build
```

#### Chaining Migrations

Compose multiple migrations:

```scala
case class PersonV1(name: String)
case class PersonV2(name: String, age: Int)
case class PersonV3(fullName: String, age: Int)

val v1ToV2 = MigrationBuilder(PersonV1.schema, PersonV2.schema)
  .addField(_.age, 0)
  .build

val v2ToV3 = MigrationBuilder(PersonV2.schema, PersonV3.schema)
  .renameField(_.name, _.fullName)
  .build

// Compose migrations
val v1ToV3 = v1ToV2 ++ v2ToV3

// Apply composed migration
val v1 = PersonV1("Alice")
val v3: Either[SchemaError, PersonV3] = v1ToV3.migrate(v1)
// Right(PersonV3("Alice", 0))
```

#### Bidirectional Migrations

Every migration has an automatic reverse:

```scala
val forward = MigrationBuilder(PersonV1.schema, PersonV2.schema)
  .renameField(_.name, _.fullName)
  .addField(_.country, "USA")
  .build

val backward = forward.reverse

// Round-trip
val v1 = PersonV1("Alice", 30)
val v2 = forward.migrate(v1).toOption.get
val v1Again = backward.migrate(v2).toOption.get
// v1 == v1Again (true)
```

## Dynamic Migrations

For runtime scenarios, use `DynamicMigration`:

```scala
import zio.blocks.schema.migration.DynamicMigration
import zio.blocks.schema.DynamicValue

// Create dynamic migration
val dynamicMigration = DynamicMigration(
  sourceSchema = PersonV1.schema,
  targetSchema = PersonV2.schema,
  actions = Chunk(
    MigrationAction.Rename("name", "fullName"),
    MigrationAction.AddField("country", DynamicValue.Primitive("USA", Schema[String]))
  )
)

// Apply to dynamic values
val dynamicV1 = DynamicValue.fromSchemaAndValue(PersonV1.schema, PersonV1("Alice", 30))
val dynamicV2 = dynamicMigration.migrate(dynamicV1)
```

## Error Handling

Migrations return `Either[SchemaError, B]` with detailed error messages:

```scala
val result = migration.migrate(value)

result match {
  case Right(migrated) =>
    println(s"Success: $migrated")

  case Left(error) =>
    error match {
      case SchemaError.MigrationError(message, path) =>
        println(s"Migration failed at path ${path.mkString(".")}: $message")

      case SchemaError.ValidationError(message, path) =>
        println(s"Validation failed at path ${path.mkString(".")}: $message")

      case _ =>
        println(s"Error: ${error.message}")
    }
}
```

## Best Practices

### 1. Use Selector API for Type Safety

Prefer the selector-based API for compile-time safety:

```scala
// ✅ Good - type-safe
.renameField(_.name, _.fullName)

// ❌ Avoid - typos not caught until runtime
.renameFieldByName("nmae", "fullName")  // Typo!
```

### 2. Enable Compile-Time Validation

Always use `build()` instead of `buildUnchecked()`:

```scala
// ✅ Good - catches incomplete migrations at compile-time
.build

// ❌ Avoid - errors only at runtime
.buildUnchecked
```

### 3. Test Round-Trip Migrations

Verify that forward and reverse migrations work correctly:

```scala
test("round-trip migration") {
  val v1 = PersonV1("Alice", 30)
  val v2 = forward.migrate(v1).toOption.get
  val v1Again = backward.migrate(v2).toOption.get
  assertTrue(v1 == v1Again)
}
```

### 4. Version Your Migrations

Store migrations with version information:

```scala
case class VersionedMigration[A, B](
  fromVersion: Int,
  toVersion: Int,
  migration: Migration[A, B]
)

val migrations = Map(
  (1, 2) -> VersionedMigration(1, 2, v1ToV2),
  (2, 3) -> VersionedMigration(2, 3, v2ToV3)
)
```

### 5. Handle Nested Structures Carefully

When migrating nested structures, migrate inner types first:

```scala
// ✅ Good - migrate inner type first
val addressMigration = MigrationBuilder(AddressV1.schema, AddressV2.schema)
  .addField(_.country, "USA")
  .build

val personMigration = MigrationBuilder(PersonV1.schema, PersonV2.schema)
  .transformField(_.address, addressMigration)
  .build
```

## Comparison with Into/As

| Feature | Migration | Into/As |
|---------|-----------|---------|
| **Serializable** | ✅ Yes | ❌ No (functions) |
| **Runtime introspection** | ✅ Yes | ❌ No |
| **Automatic reversal** | ✅ Yes | ❌ No (As only) |
| **Compile-time validation** | ✅ Yes (with macros) | ✅ Yes |
| **Type safety** | ✅ Yes (selector API) | ✅ Yes |
| **IDE support** | ✅ Yes (selector API) | ✅ Yes |
| **Versioning** | ✅ Easy | ❌ Difficult |
| **Audit trail** | ✅ Yes | ❌ No |
| **Setup complexity** | Medium | Low |
| **Performance** | Good | Excellent |

**Rule of thumb**: Use `Into`/`As` for simple automatic conversions. Use `Migration` when you need serialization, versioning, or runtime introspection.

## See Also

- [Schema Evolution](schema-evolution.md) - Automatic type class-based conversions
- [Schema](schema.md) - Core schema definitions
- [Optics](optics.md) - Type-safe data access and modification

