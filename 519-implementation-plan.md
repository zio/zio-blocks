# Implementation Plan: Pure Algebraic Migration System (Issue #519)

## Status: Core Implementation Complete

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

## MigrationBuilder Methods (per spec)

### Record Operations
| Method | Status | Notes |
|--------|--------|-------|
| `addField(target, default: SchemaExpr)` | ✅ | Primary API |
| `addField(target, default: DynamicValue)` | ✅ | Convenience overload |
| `dropField(source, defaultForReverse: SchemaExpr = DefaultValue)` | ✅ | Primary API |
| `dropFieldWithDefault(source, defaultForReverse: DynamicValue)` | ✅ | Convenience overload |
| `renameField(from, to)` | ✅ | |
| `transformField(from, to, transform: SchemaExpr)` | ✅ | Primary API |
| `transformField(from, to, transform: DynamicValue)` | ✅ | Convenience overload |
| `mandateField(source, target, default: SchemaExpr)` | ✅ | Primary API |
| `mandateField(source, target, default: DynamicValue)` | ✅ | Convenience overload |
| `optionalizeField(source, target)` | ✅ | |
| `changeFieldType(source, target, converter: SchemaExpr)` | ✅ | Primary API |
| `changeFieldType(source, target, converter: DynamicValue)` | ✅ | Convenience overload |

### Enum Operations
| Method | Status | Notes |
|--------|--------|-------|
| `renameCase(from, to)` | ✅ | |
| `transformCase(caseName)(caseMigration)` | ✅ | |

### Collection/Map Operations
| Method | Status | Notes |
|--------|--------|-------|
| `transformElements(at, transform: SchemaExpr)` | ✅ | Primary API |
| `transformElements(at, transform: DynamicValue)` | ✅ | Convenience overload |
| `transformKeys(at, transform: SchemaExpr)` | ✅ | Primary API |
| `transformKeys(at, transform: DynamicValue)` | ✅ | Convenience overload |
| `transformValues(at, transform: SchemaExpr)` | ✅ | Primary API |
| `transformValues(at, transform: DynamicValue)` | ✅ | Convenience overload |

### Build
| Method | Status | Notes |
|--------|--------|-------|
| `build` | ✅ | Currently same as buildPartial |
| `buildPartial` | ✅ | |

## SchemaExpr Integration
- `SchemaExpr.Literal` - wraps DynamicValue for literal values
- `SchemaExpr.DefaultValue` - placeholder for schema default values

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

### 3. Missing Tests
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
├── SchemaExpr.scala          (includes Literal, DefaultValue)
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
├── MigrationBuilderSyntax.scala   (macros)
├── MigrationSelectorSyntax.scala  (version-specific)
└── SelectorMacros.scala

schema/shared/src/test/scala/zio/blocks/schema/migration/
└── MigrationSpec.scala
```

---

## Remaining Work (Priority Order)

1. **Add missing tests** - Low effort, high value
2. **Schema.structural[T]** - High effort, required for full workflow
3. **Build validation** - Medium effort

