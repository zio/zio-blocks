# Schema Migration

Schema migration provides a pure, algebraic system for transforming data between schema versions.

## Overview

The migration system enables:
- **Type-safe migrations**: Define transformations between typed schemas
- **Dynamic migrations**: Operate on untyped `DynamicValue` for flexibility
- **Reversibility**: All migrations can be structurally reversed
- **Serialization**: Migrations are pure data that can be serialized and stored
- **Build-time validation**: Structural correctness is verified when you call `build`
- **Path-aware errors**: Detailed error messages with exact location information

## Core Types

### Migration[A, B]

A typed migration from schema `A` to schema `B`. Use `MigrationBuilder` to construct one:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Import serialization schemas when (de)serializing migrations
import zio.blocks.schema.migration.MigrationSchemas._

@schema case class PersonV1(name: String, age: Int)
@schema case class PersonV2(fullName: String, age: Int, country: String)

val migration: Migration[PersonV1, PersonV2] =
  Migration
    .newBuilder[PersonV1, PersonV2]
    .renameField(MigrationBuilder.paths.field("name"), MigrationBuilder.paths.field("fullName"))
    .addField(MigrationBuilder.paths.field("country"), "US")
    .buildPartial  // skips structural validation
```

`build` validates that the migration actions produce a structurally correct target schema.
`buildPartial` skips validation and is useful during development or when validation is too strict.

### DynamicMigration

An untyped, serializable migration that operates directly on `DynamicValue`. Every `Migration[A, B]`
contains a `DynamicMigration` accessible via `.dynamicMigration`:

```scala
val dynamicMigration: DynamicMigration = migration.dynamicMigration

import zio.blocks.chunk.Chunk

val oldValue: DynamicValue = DynamicValue.Record(Chunk(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))

val newValue: Either[MigrationError, DynamicValue] = dynamicMigration(oldValue)
// Right(Record(Chunk("fullName" -> Primitive(String("John")), "age" -> Primitive(Int(30)), "country" -> Primitive(String("US")))))
```

### MigrationAction

Individual migration steps are represented as an algebraic data type. Each action is reversible:

| Action | Description |
|--------|-------------|
| `AddField` | Add a new field with a default value expression |
| `DropField` | Remove a field (stores a reverse default) |
| `RenameField` | Rename a field |
| `TransformValue` | Transform a field's value using a `DynamicSchemaExpr` |
| `Mandate` | Make an optional field mandatory (unwrap `Option`) |
| `Optionalize` | Make a mandatory field optional (wrap in `Option`) |
| `ChangeType` | Convert between primitive types (e.g., `Int` → `Long`) |
| `Join` | Combine multiple source fields into one target field |
| `Split` | Split one source field into multiple target fields |
| `RenameCase` | Rename a case in a variant/enum |
| `TransformCase` | Apply nested actions within a specific variant case |
| `TransformElements` | Transform every element in a sequence |
| `TransformKeys` | Transform every key in a map |
| `TransformValues` | Transform every value in a map |
| `Identity` | No-op action (useful as a placeholder) |

### DynamicSchemaExpr

A serializable expression language for computing values during migration. Expressions are evaluated
against `DynamicValue` at runtime:

```scala
// Literal value
val lit = DynamicSchemaExpr.Literal(
  DynamicValue.Primitive(PrimitiveValue.Int(42))
)

// Extract a value by path
val nameExpr = DynamicSchemaExpr.Path(DynamicOptic.root.field("name"))

// Arithmetic on numeric fields
val doubled = DynamicSchemaExpr.Arithmetic(
  nameExpr,
  DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
  DynamicSchemaExpr.ArithmeticOperator.Multiply
)

// String operations
val concat = DynamicSchemaExpr.StringConcat(expr1, expr2)
val length = DynamicSchemaExpr.StringLength(stringExpr)

// Primitive type coercion (e.g., Int → String)
val coerced = DynamicSchemaExpr.CoercePrimitive(intExpr, "String")
```

## MigrationBuilder API

The builder provides a fluent API for constructing migrations. All path arguments are `DynamicOptic`
values — use `MigrationBuilder.paths` helpers or the type-safe selector syntax below:

```scala
Migration
  .newBuilder[OldType, NewType]
  // Record operations
  .addField(path, defaultExpr)                            // add a field with a default
  .dropField(path, defaultForReverse)                     // remove a field
  .renameField(fromPath, toPath)                          // rename a field
  .transformField(path, transform, reverseTransform)      // transform a field value
  .mandateField(path, default)                            // Option[T] → T
  .optionalizeField(path)                                 // T → Option[T]
  .changeFieldType(path, converter, reverseConverter)     // change primitive type
  .joinFields(targetPath, sourcePaths, combiner, splitter)
  .splitField(sourcePath, targetPaths, splitter, combiner)
  // Enum/variant operations
  .renameCaseAt(path, from, to)
  .transformCaseAt(path, caseName, nestedActions)
  // Collection operations
  .transformElements(path, transform, reverseTransform)
  .transformKeys(path, transform, reverseTransform)
  .transformValues(path, transform, reverseTransform)
  // Build
  .build        // validates structural correctness, then builds
  .buildPartial // builds without validation
```

## Type-Safe Selector Syntax (Scala 2 & 3)

For ergonomic, type-checked paths, import the selector syntax. The macro inspects selector
lambdas like `_.fieldName.nested` and converts them to `DynamicOptic` paths at compile time:

```scala
import zio.blocks.schema.migration.MigrationBuilderSyntax._

val migration: Migration[PersonV1, PersonV2] =
  Migration
    .newBuilder[PersonV1, PersonV2]
    .renameField(_.name, _.fullName)
    .addField(_.country, "US")
    .buildPartial
```

Selector lambdas support optic-like projections for nested structures:

| Projection | Meaning |
|------------|---------|
| `_.field` | Select a record field |
| `_.field.nested` | Select a nested field |
| `_.each` | Traverse into sequence elements |
| `_.eachKey` | Traverse into map keys |
| `_.eachValue` | Traverse into map values |

## Path Helpers

When you don't need compile-time type checking, use the `paths` object:

```scala
import MigrationBuilder.paths

paths.field("name")               // Single field
paths.field("address", "street")  // Nested field (address.street)
paths.elements                    // Sequence elements
paths.mapKeys                     // Map keys
paths.mapValues                   // Map values
```

## Reversibility

Every migration action stores enough information to be reversed. Call `.reverse` to get
a `Migration[B, A]`:

```scala
val forward: Migration[PersonV1, PersonV2] = ...
val backward: Migration[PersonV2, PersonV1] = forward.reverse

// Reverse of addField is dropField, reverse of rename is rename back, etc.
```

> **Note:** Reverse transforms are resolved best-effort at build time. For `TransformValue`
> and `ChangeType`, provide explicit reverse expressions for reliable round-tripping.

## Composition

Migrations compose sequentially with `++` or `.andThen`:

```scala
val v1ToV2: Migration[V1, V2] = ...
val v2ToV3: Migration[V2, V3] = ...

val v1ToV3: Migration[V1, V3] = v1ToV2 ++ v2ToV3
// or equivalently:
val v1ToV3: Migration[V1, V3] = v1ToV2.andThen(v2ToV3)
```

## Error Handling

All migration operations return `Either[MigrationError, DynamicValue]`. Errors are accumulated
(not short-circuiting) and carry path information:

```scala
migration.applyDynamic(value) match {
  case Right(newValue) => // success
  case Left(migrationError) =>
    migrationError.errors.foreach { error =>
      println(s"At ${error.path}: ${error.message}")
    }
}
```

Error types include:

| Error | Description |
|-------|-------------|
| `FieldNotFound` | Required field missing from source |
| `FieldAlreadyExists` | Field already exists when adding |
| `NotARecord` | Expected a record, found something else |
| `NotAVariant` | Expected a variant, found something else |
| `NotASequence` | Expected a sequence, found something else |
| `NotAMap` | Expected a map, found something else |
| `CaseNotFound` | Variant case not found |
| `TypeConversionFailed` | Primitive type conversion failed |
| `ExprEvalFailed` | Expression evaluation failed |
| `PathNavigationFailed` | Cannot navigate the specified path |
| `DefaultValueMissing` | Default value not resolved for a required field |
| `IndexOutOfBounds` | Sequence index out of range |
| `KeyNotFound` | Map key not found |
| `NumericOverflow` | Arithmetic overflow |
| `ActionFailed` | General action failure |

## Best Practices

1. **Use `build` in production** to catch structural mismatches early; use `buildPartial` during prototyping
2. **Provide explicit reverse expressions** for `transformField` and `changeFieldType` to ensure reliable round-tripping
3. **Compose small migrations** rather than writing one large migration — this improves readability and testability
4. **Test both directions** — apply forward, then reverse, and verify the round-trip
5. **Serialize migrations** alongside schema versions for audit trails and reproducibility
