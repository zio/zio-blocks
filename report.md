# Migration System Review (issue.md vs implementation)

## Scope
- Re-checked the migration implementation against `issue.md` and the previous `report.md` findings.
- Implemented the missing behavior and added targeted tests.
- Verified on Windows with:
  - `sbt -no-colors "schemaJVM/test" "schemaJS/test"`

## What Was Fixed

### Selector macros now match the optic-style grammar
- Added Scala 2 + Scala 3 selector macro support for:
  - `.when[T]`, `.each`, `.eachKey`, `.eachValue`, `.wrapped[T]`, `.at(i)`, `.atIndices(is*)`, `.atKey(k)`, `.atKeys(ks*)`
- Added type-safe builder syntax and `MigrationBuilder.paths.from` helpers (`MigrationBuilderSyntax`).
- Added tests in `SelectorMacrosSpec`.

### DefaultValue is resolved at build time
- `DynamicSchemaExpr.DefaultValue` is now treated as a build-time sentinel and rewritten to `ResolvedDefault` using schema defaults for forward migrations (strict; fails if missing).
- Reverse expressions resolve defaults best-effort (resolved when possible, otherwise left as `DefaultValue`).
- Added tests in `DefaultValueResolutionSpec`.

### Transform evaluation context is correct
- `TransformValue` and `ChangeType` now evaluate expressions on the *parent context* (record/collection) rather than on the leaf value, enabling expressions like “use sibling field X” inside a transformation.
- `AddField` / `Mandate` defaults evaluate against the record/parent they modify.
- Error paths for expression failures are attributed to the correct action path (e.g. include `.field(name)`).

### Runtime path handling supports the expanded node set
- Dynamic migration application supports `AtIndices` and `AtMapKeys` traversal nodes.

### Validation checks optionality and understands wrapper/advanced paths
- `MigrationValidator` now compares optionality where previously ignored, and treats `Wrapped` as transparent.
- Validator navigation supports `AtIndex`, `AtIndices`, `AtMapKey`, and `AtMapKeys`.
- Added tests in `MigrationValidatorOptionalitySpec`.

### Typed `Migration.apply` is safe for structural schemas
- Structural schemas still cannot be deconstructed/constructed at runtime (bindings throw), so `Migration.apply` now fails with a clear `MigrationError` instead of throwing an exception, and recommends using `applyDynamic`.
- Added Scala 3-only tests in `StructuralMigrationApplySpec`.

## Remaining Known Limitations
- **Typed `Migration.apply` for structural source/target types is not supported** (by design of `Schema.structural` bindings); use `Migration.applyDynamic` when working with structural-only schemas.
- **Scala 2.13 cannot derive `Schema.structural`** for true structural / union types (Scala 3 language feature).
- `DynamicSchemaExpr.fromSchemaExpr` supports a subset of `SchemaExpr` (e.g. excludes `StringRegexMatch`).

## Test Results
- `schemaJVM/test` + `schemaJS/test`: **1206 tests passed**, 0 failed (run on February 3, 2026).

## Merge Readiness
- Core gaps from the prior review are addressed, tests are green, and docs/tests cover the newly added behavior.
