---
id: schema-migration
title: "Schema Migration"
---

The **Schema Migration** system provides pure, algebraic transformations between schema versions. Migrations are represented as first-class, serializable data, enabling schema evolution, backward/forward compatibility, and automatic migration when reading older serialized data.

## Overview

- **`Migration[A, B]`** — Typed migration from type `A` to type `B`. Wraps a `DynamicMigration` with source and target schemas.
- **`DynamicMigration`** — Untyped, fully serializable migration that operates on `DynamicValue`. Can be stored in registries and applied dynamically.
- **`MigrationAction`** — Individual steps (AddField, DropField, Rename, TransformValue, RenameCase, TransformCase, etc.) each with a path (`DynamicOptic`) and a structural reverse.
- **`MigrationExpr`** — Serializable value expressions for defaults and transforms: `Literal(value)` and `SourcePath(path)`.

## Quick Example

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Old version: name only
case class PersonV0(name: String)
object PersonV0 { implicit val schema: Schema[PersonV0] = Schema.derived[PersonV0] }

// New version: name and age
case class PersonV1(name: String, age: Int)
object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1] }

// Build migration: add field "age" with default 0
val migration = Migration
  .newBuilder(PersonV0.schema, PersonV1.schema)
  .addField(
    DynamicOptic.root.field("age"),
    MigrationExpr.Literal(Schema[Int].toDynamicValue(0))
  )
  .build

val v0 = PersonV0("Alice")
migration(v0)  // Right(PersonV1("Alice", 0))
```

## Migration Laws

- **Identity**: `Migration.identity[A].apply(a) == Right(a)`
- **Associativity**: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
- **Structural reverse**: `m.reverse.reverse == m`
- **Best-effort semantic inverse**: `m.apply(a) == Right(b)` ⇒ `m.reverse.apply(b) == Right(a)` (when sufficient information exists)

## API Summary

### Migration

- `Migration.identity[A]` — No-op migration (requires `Schema[A]`).
- `Migration.newBuilder[A, B](sourceSchema, targetSchema)` — Start building a migration.
- `migration(value: A): Either[SchemaError, B]` — Apply migration.
- `m1 ++ m2` / `m1.andThen(m2)` — Compose migrations.
- `migration.reverse` — Reverse migration (best-effort).

### MigrationBuilder

Path-based builder (use `DynamicOptic` to specify locations):

- `addField(at, default)` — Add a new field with a default value.
- `dropField(at, defaultForReverse)` — Remove a field.
- `renameField(from, toName)` — Rename a field.
- `transformField(at, transform)` — Set value at path from an expression.
- `mandateField(at, default)` — Promote optional to required with default.
- `optionalizeField(at)` — Make field optional.
- `changeFieldType(at, converter)` — Primitive-to-primitive conversion.
- `renameCase(at, from, to)` — Rename a variant case.
- `transformCase(at, caseActions)` — Transform the value inside a case.
- `transformElements(at, transform)` — Transform each element of a sequence.
- `transformKeys(at, transform)` / `transformValues(at, transform)` — Transform map keys/values.
- `join(at, sourcePaths, combineOp)` — Combine multiple source paths into one (e.g. `CombineOp.StringConcat(" ")`).
- `split(at, targetPaths, splitOp)` — Split one value into multiple target paths (e.g. `SplitOp.StringSplit(" ")`).
- `build` / `buildPartial` — Produce `Migration[A, B]`.

### DynamicMigration

- `DynamicMigration(actions: Chunk[MigrationAction])` — Build from a list of actions.
- `dm(value: DynamicValue): Either[SchemaError, DynamicValue]` — Apply to a dynamic value.
- `dm1 ++ dm2` — Compose.
- `dm.reverse` — Structural reverse.

### MigrationExpr

- `MigrationExpr.Literal(value: DynamicValue)` — Constant.
- `MigrationExpr.SourcePath(path: DynamicOptic)` — Value at path from root.
- `expr.eval(root: DynamicValue): Either[SchemaError, DynamicValue]` — Evaluate with a given root.

## Error Handling

All runtime errors use `SchemaError` (path + message), so diagnostics can report e.g. “Failed to apply TransformValue at `.addresses.each.streetNumber`”.

## Integration

The migration types live in `zio.blocks.schema.migration`. Add no extra dependency; they are part of `zio-blocks-schema`.

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
import zio.blocks.schema.migration._
```

## Relation to Into / As

[Into](./schema-evolution/into.md) and [As](./schema-evolution/as.md) provide automatic one-way or bidirectional conversion between two types when their structures are compatible or when defaults can be derived. The **Migration** system is explicit and path-based: you describe each step (add field, rename, transform) so that migrations are serializable, composable, and reversible at the data level. Use Migration when you need versioned, inspectable, or registry-stored migration definitions; use Into/As when you want derivation-only conversions between two fixed types.
