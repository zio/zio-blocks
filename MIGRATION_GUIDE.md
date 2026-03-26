# ZIO Schema Migrations Guide

Welcome to the **Algebraic Migration Engine** for ZIO Blocks Schema. 
This system provides a compile-time safe, stack-safe, purely algebraic layout for migrating your `Schema` versions using an advanced Macro Builder API.

## Core Concepts

The architecture relies on the following layers:
1. **`MigrationAction` ADT**: A pure algebraic structure encoding structural modifications without enclosing opaque functions (preserves high-efficiency serialization workflows).
2. **`DynamicMigration`**: An untyped core traversal engine executing the actions safely via trampolining over deep structures.
3. **`Migration[A, B]`**: The typed wrapper combining your definitions with the execution layer safely.
4. **`MigrationBuilder[A, B]`**: The fluent user-facing composition layer powering the AST derivations for optic properties.

## Getting Started

Using the `MigrationBuilder` allows you to smoothly transform schemas without managing underlying DynamicValue loops yourself.

```scala
case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, age: Int, active: Boolean)

// The Builder
val boolSchema = Schema[Boolean]
val trueValue = true

val V1ToV2: Migration[PersonV1, PersonV2] = 
  MigrationBuilder.make[PersonV1, PersonV2]
    .renameField(_.name, _.fullName)
    .addField(_.active, SchemaExpr.Literal(trueValue, boolSchema))
    .build
```

### Shape Diff Validation Safety
When you invoke `.build` on the `MigrationBuilder`, an inline compiler macro immediately compares `A` and `B` structural identities.
If you target a `PersonV2` containing a new field (like `active`) but fail to declare an `addField(...)` instruction during construction, **compilation will immediately abort from the IDE** notifying you with the exact fields you forgot:
`"Field(s) [active] in target schema are missing from source and have no default value provided."`

If you are dealing with complex shapes and intentionally wish to skip Shape Diff Validation, you can securely bypass it via:
```scala
val V1ToV2: Migration[PersonV1, PersonV2] = MigrationBuilder.make[PersonV1, PersonV2]
  .renameField(_.name, _.fullName)
  .buildPartial // Skips target schema field validation
```

### Sequential Composition
All migrations behave seamlessly via axiomatic bounds allowing robust structural combining and sequential evaluation over large data schemas:
```scala
val v1ToV2: Migration[V1, V2] = ???
val v2ToV3: Migration[V2, V3] = ???

val v1ToV3: Migration[V1, V3] = v1ToV2 ++ v2ToV3
```
