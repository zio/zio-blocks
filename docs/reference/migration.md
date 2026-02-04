# Schema Migration

Schema migration provides a pure, algebraic system for transforming data between schema versions.

## Overview

The migration system enables:
- **Type-safe migrations**: Define transformations between typed schemas
- **Dynamic migrations**: Operate on untyped `DynamicValue` for flexibility
- **Reversibility**: All migrations can be structurally reversed
- **Serialization**: Migrations are pure data that can be serialized and stored
- **Path-aware errors**: Detailed error messages with exact location information

## Core Types

### Migration[A, B]

A typed migration from schema `A` to schema `B`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Needed when (de)serializing DynamicMigration / MigrationAction / DynamicSchemaExpr
import zio.blocks.schema.migration.MigrationSchemas._

case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, age: Int, country: String)

object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }
object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }

val migration: Migration[PersonV1, PersonV2] =
  Migration
    .newBuilder[PersonV1, PersonV2]
    .renameField(MigrationBuilder.paths.field("name"), MigrationBuilder.paths.field("fullName"))
    .addField(MigrationBuilder.paths.field("country"), "US")
    .buildPartial
```

### DynamicMigration

An untyped, serializable migration operating on `DynamicValue`:

```scala
val dynamicMigration = migration.dynamicMigration

// Apply to DynamicValue directly
val oldValue: DynamicValue = DynamicValue.Record(Vector(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))

val newValue: Either[MigrationError, DynamicValue] = dynamicMigration(oldValue)
```

### MigrationAction

Individual migration actions are represented as an algebraic data type:

| Action | Description |
|--------|-------------|
| `AddField` | Add a new field with a default value |
| `DropField` | Remove a field |
| `RenameField` | Rename a field |
| `TransformValue` | Transform a value using an expression |
| `Mandate` | Make an optional field mandatory |
| `Optionalize` | Make a mandatory field optional |
| `ChangeType` | Convert between primitive types |
| `Join` | Combine multiple fields into one |
| `Split` | Split one field into multiple |
| `RenameCase` | Rename a case in a variant/enum |
| `TransformCase` | Transform within a specific case |
| `TransformElements` | Transform all elements in a sequence |
| `TransformKeys` | Transform all keys in a map |
| `TransformValues` | Transform all values in a map |
| `Identity` | No-op action |

### DynamicSchemaExpr

Serializable expressions for value transformations:

```scala
// Literal value
val lit = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))

// Path extraction
val path = DynamicSchemaExpr.Path(DynamicOptic.root.field("name"))

// Arithmetic
val doubled = DynamicSchemaExpr.Arithmetic(
  path,
  DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
  DynamicSchemaExpr.ArithmeticOperator.Multiply
)

// String operations
val concat = DynamicSchemaExpr.StringConcat(expr1, expr2)
val length = DynamicSchemaExpr.StringLength(stringExpr)

// Type coercion
val coerced = DynamicSchemaExpr.CoercePrimitive(intExpr, "String")
```

## MigrationBuilder API

The builder provides a fluent API for constructing migrations:

```scala
Migration
  .newBuilder[OldType, NewType]
  // Record operations
  .addField(path, defaultExpr)
  .dropField(path, defaultForReverse)
  .renameField(fromPath, toPath)
  .transformField(path, transform, reverseTransform)
  .mandateField(path, default)
  .optionalizeField(path)
  .changeType(path, converter, reverseConverter)
  .joinFields(targetPath, sourcePaths, combiner, splitter)
  .splitField(sourcePath, targetPaths, splitter, combiner)
  // Enum operations
  .renameCase(path, from, to)
  .transformCase(path, caseName, nestedActions)
  // Collection operations
  .transformElements(path, transform, reverseTransform)
  .transformKeys(path, transform, reverseTransform)
  .transformValues(path, transform, reverseTransform)
  // Build
  .build        // Full validation
  .buildPartial // Skip validation
```

## Type-Safe Selector Syntax

For more ergonomic, type-safe paths, import the selector syntax extensions:

```scala
import zio.blocks.schema.migration.MigrationBuilderSyntax._

val migration: Migration[PersonV1, PersonV2] =
  Migration
    .newBuilder[PersonV1, PersonV2]
    .renameField(_.name, _.fullName)
    .addField(_.country, "US")
    .buildPartial
```

Selector syntax supports optic-like projections such as:
- `.when[T]`, `.each`, `.eachKey`, `.eachValue`, `.wrapped[T]`, `.at(i)`, `.atIndices(is*)`, `.atKey(k)`, `.atKeys(ks*)`

## Path Helpers

Use the `paths` object for constructing paths:

```scala
import MigrationBuilder.paths

paths.field("name")               // Single field
paths.field("address", "street")  // Nested field
paths.elements                    // Sequence elements
paths.mapKeys                     // Map keys
paths.mapValues                   // Map values
```

## Reversibility

All migrations can be reversed:

```scala
val forward: Migration[A, B] = ...
val backward: Migration[B, A] = forward.reverse

// Law: forward ++ backward should be identity (structurally)
```

## Composition

Migrations can be composed:

```scala
val v1ToV2: Migration[V1, V2] = ...
val v2ToV3: Migration[V2, V3] = ...

val v1ToV3: Migration[V1, V3] = v1ToV2 ++ v2ToV3
// or
val v1ToV3: Migration[V1, V3] = v1ToV2.andThen(v2ToV3)
```

## Error Handling

Migrations return `Either[MigrationError, DynamicValue]`:

```scala
migration.apply(value) match {
  case Right(newValue) => // Success
  case Left(errors) =>
    errors.errors.foreach { error =>
      println(s"At ${error.path}: ${error.message}")
    }
}
```

Error types include:
- `FieldNotFound` - A required field was not found in the source value
- `FieldAlreadyExists` - A field already exists when trying to add it
- `NotARecord` - Expected a record but found a different kind of value
- `NotAVariant` - Expected a variant but found a different kind of value
- `TypeConversionFailed` - Primitive type conversion failed
- `DefaultValueMissing` - Default value not resolved
- `PathNavigationFailed` - Cannot navigate the path
- `ActionFailed` - General action failure

## Best Practices

1. **Use `buildPartial` during development**, switch to `build` for production validation
2. **Provide meaningful reverse transforms** for `TransformValue` actions
3. **Keep migrations small and focused** - compose multiple simple migrations
4. **Test both forward and reverse** directions
5. **Store migrations alongside schema versions** for reproducibility
