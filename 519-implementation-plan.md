# Implementation Plan: Pure Algebraic Migration System (Issue #519)

## Status: Core Implementation Complete, Some Items Remaining

## Requirements Checklist (from 519.md Success Criteria)

| Requirement | Status | Notes |
|-------------|--------|-------|
| `DynamicMigration` fully serializable | ✅ Complete | Pure case classes, no closures |
| `Migration[A, B]` wraps schemas and actions | ✅ Complete | |
| All actions path-based via `DynamicOptic` | ✅ Complete | |
| User API uses selector functions (`S => A`) | ✅ Complete | Macros convert to `DynamicOptic` |
| Macro validation in `.build` | ⚠️ Partial | Same as `.buildPartial` currently |
| `.buildPartial` supported | ✅ Complete | |
| Structural reverse implemented | ✅ Complete | `m.reverse.reverse == m` |
| Identity & associativity laws hold | ✅ Complete | Tested |
| Enum rename / transform supported | ✅ Complete | `RenameCase`, `TransformCase` |
| Errors include path information | ✅ Complete | Uses `SchemaError` with `MigrationErrorKind` |
| Comprehensive tests | ⚠️ Partial | Core tests done, some operations untested |
| Scala 2.13 and Scala 3.5+ supported | ✅ Complete | Cross-compiles |

## Recent Changes

### Error Handling Refactored ✅
- Removed separate `MigrationError` class
- Extended `SchemaError.Single` with `MigrationError` case
- Added `MigrationErrorKind` sum type for migration-specific errors:
  - `PathNotFound`, `TypeMismatch`, `MissingDefault`, `TransformFailed`
  - `FieldNotFound`, `FieldAlreadyExists`, `CaseNotFound`
  - `InvalidValue`, `MandateFailed`
- Added helper methods: `SchemaError.pathNotFound()`, `SchemaError.fieldNotFound()`, etc.

## Missing/Incomplete Items

### 1. Schema.structural[T] - NOT IMPLEMENTED
The spec describes using structural types like:
```scala
type PersonV0 = { val firstName: String; val lastName: String }
implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
```
This macro is NOT implemented. Currently only works with case classes.

### 2. Build Validation - PARTIAL
`.build` should perform macro validation that migration is complete.
Currently identical to `.buildPartial`.

### 3. SchemaExpr Integration - PARTIAL
The spec uses `SchemaExpr[A, ?]` for transformations. Currently using `DynamicTransform`.
`SchemaExpr` already exists but needs integration with migration builder.

### 4. Missing Tests
- `mandateField` / `optionalizeField`
- `changeFieldType`  
- `transformCase`
- `transformElements`
- `transformKeys` / `transformValues`
- `Join` / `Split` actions
- Nested collection paths

---

## Files Structure

```
schema/shared/src/main/scala/zio/blocks/schema/
├── SchemaError.scala         (extended with MigrationError + MigrationErrorKind)
└── migration/
    ├── DynamicMigration.scala    
    ├── Migration.scala           
    ├── MigrationAction.scala     
    └── package.scala             (shared)

schema/shared/src/main/scala-3/zio/blocks/schema/migration/
├── MigrationBuilder.scala
├── MigrationSelectorSyntax.scala  (version-specific)
└── SelectorMacros.scala

schema/shared/src/main/scala-2/zio/blocks/schema/migration/
├── MigrationBuilder.scala
├── MigrationBuilderSyntax.scala
├── MigrationSelectorSyntax.scala  (version-specific)
└── SelectorMacros.scala

schema/shared/src/test/scala/zio/blocks/schema/migration/
└── MigrationSpec.scala
```

---

## Remaining Work (Priority Order)

1. **Add missing tests** - Low effort, high value
2. **Schema.structural[T]** - High effort, required for full workflow
3. **SchemaExpr integration** - Medium effort, per spec requirements
4. **Build validation** - Medium effort
5. **Join/Split builder methods** - Low effort

