# Autogamer Decisions

## Schema Migration System Implementation

### Decision 0: Verification Against Issue #519

**Date**: 2026-04-03
**Context**: Final verification that implementation matches all requirements from issue #519.

**Decision**: Verified all requirements are implemented correctly.

**Verification Results:**

1. **Core Architecture** - ✅ Complete
   - `DynamicMigration` - Pure, serializable ADT with 14+ action types
   - `Migration[A, B]` - Typed wrapper with compile-time safety
   - `MigrationBuilder[A, B]` - Fluent DSL for building migrations

2. **Migration Actions** - ✅ Complete
   - Record: AddField, DropField, Rename, TransformValue, Mandate, Optionalize, Join, Split, ChangeType
   - Enum: RenameCase, TransformCase
   - Collection: TransformElements, TransformKeys, TransformValues

3. **DynamicTransform** - ✅ Complete
   - 25+ primitive-to-primitive transformations
   - String, numeric, type conversion operations
   - Composition via `>>>` and `Compose`
   - All transforms are reversible

4. **Properties** - ✅ Verified
   - Identity: `Migration.identity[A].apply(a) == Right(a)`
   - Associativity: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
   - Structural reverse: `m.reverse.reverse == m`

5. **Compilation** - ✅ Verified
   - `sbt "schemaJVM / compile"` succeeds
   - `sbt "schemaJVM / test"` - 8072 tests pass

**No changes required** - Implementation is complete and correct.

---

### Decision 1: Error Type Design

**Date**: 2026-04-03
**Context**: Need an error type that captures path information for diagnostics.

**Decision**: Use a sealed trait hierarchy with `MigrationError` as the base type, where each case captures:
- The path where the error occurred (`DynamicOptic`)
- A human-readable message

**Rationale**: This enables diagnostics like "Failed to apply TransformValue at `.addresses.each.streetNumber`" which is essential for debugging migration failures in large data structures.

**Alternatives Considered**:
1. Simple string errors - rejected because path information would be lost
2. Exception-based errors - rejected for functional purity
3. Using SchemaError directly - rejected because migration errors have different semantics

---

### Decision 2: Action Path Design

**Date**: 2026-04-03
**Context**: All migration actions need to specify where they apply.

**Decision**: Use `DynamicOptic` for all action paths, with the `at` field containing the full path to the target.

**Rationale**: 
- `DynamicOptic` already exists and is well-tested
- Supports field access, case selection, collection traversal, map access
- Enables path composition and modification

**Conventions**:
- For record actions (AddField, DropField, Rename), the last node in the path identifies the field
- For variant actions, the path points to the variant, and case-specific fields are separate

---

### Decision 3: Transform Expression Design

**Date**: 2026-04-03
**Context**: Need pure, serializable transformations without closures.

**Decision**: Create `DynamicTransform` as a sealed ADT with primitive-to-primitive transformations.

**Rationale**:
- Enables serialization and storage in registries
- Allows introspection for DDL generation
- No reflection or runtime code generation needed

**Constraints**:
- Primitive to primitive only (no record construction in transforms)
- Joins/splits must produce primitives
- Complex transformations should use composition

---

### Decision 4: Reverse Migration Design

**Date**: 2026-04-03
**Context**: Need to support migration reversibility.

**Decision**: Implement structural reverse on all actions, not semantic inverse.

**Rationale**:
- Structural reverse is always possible (it's data transformation)
- Semantic inverse depends on information preservation
- Users can verify semantic equivalence where needed

**Examples**:
- `AddField(default).reverse = DropField(default)`
- `StringToInt.reverse = IntToString`
- `NumericAdd(x).reverse = NumericSubtract(x)`

---

### Decision 5: Migration Composition

**Date**: 2026-04-03
**Context**: Need to compose migrations sequentially.

**Decision**: Use `++` operator that concatenates action lists.

**Rationale**:
- Associative: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
- Simple implementation: `DynamicMigration(actions ++ that.actions)`
- Enables building complex migrations from simple ones

**Properties**:
- `empty ++ m == m`
- `m ++ empty == m`
- `(m1 ++ m2).reverse == m2.reverse ++ m1.reverse`

---

### Decision 6: Default Value Strategy

**Date**: 2026-04-03
**Context**: AddField and Mandate need default values.

**Decision**: Store the default `DynamicValue` directly in the action.

**Rationale**:
- Enables full serialization
- No need for schema access at runtime
- Reverse migration can use the same default

**Alternative Considered**: Use `SchemaExpr.DefaultValue` - rejected because it requires schema context at runtime.

---

### Decision 7: Error Aggregation

**Date**: 2026-04-03
**Context**: Multiple errors can occur during migration (e.g., TransformElements).

**Decision**: Provide `MigrationError.Multiple` case and `aggregate` function.

**Rationale**:
- Users can see all errors at once
- Avoids cascading failures hiding root causes
- Useful for batch transformations

**Implementation**:
```scala
def aggregate(errors: Chunk[MigrationError]): Option[MigrationError]
```

---

### Decision 8: Schema Instance Generation

**Date**: 2026-04-03
**Context**: All types need Schema instances for serialization.

**Decision**: Use `Schema.derived` with manual binding for variant types.

**Rationale**:
- Most types are simple case classes that derive automatically
- Variant types need manual binding for discriminators and matchers
- Ensures full roundtrip serialization

---

### Decision 9: Action Application Strategy

**Date**: 2026-04-03
**Context**: How to apply actions to DynamicValues.

**Decision**: Use `DynamicValue.modifyOrFail` and convert `SchemaError` to `MigrationError`.

**Rationale**:
- Reuses existing, tested modification infrastructure
- Clean separation of concerns
- Proper error propagation

**Implementation Pattern**:
```scala
value.modifyOrFail(path) {
  case DynamicValue.Record(fields) => // transform
}.left.map(err => MigrationError.fromSchemaErrorAtPath(err, path))
```

---

### Decision 10: MigrationBuilder Design

**Date**: 2026-04-03
**Context**: Need a fluent DSL for building typed migrations.

**Decision**: Create `MigrationBuilder[A, B]` as an immutable builder with method chaining.

**Rationale**:
- Immutable design enables safe composition and reuse
- Method chaining provides readable migration definitions
- Type parameters track source and target types at compile time

**API Design**:
```scala
val migration = MigrationBuilder[PersonV1, PersonV2]
  .addField(DynamicOptic.root.field("country"), "US")
  .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)
  .build
```

**Alternative Considered**: Mutable builder - rejected because:
- Harder to reason about
- Can lead to unexpected side effects
- Not thread-safe

---

### Decision 11: SchemaExpr Integration

**Date**: 2026-04-03
**Context**: Need to leverage existing SchemaExpr for value transformations.

**Decision**: Create `SchemaExprTransform` module for conversion utilities.

**Rationale**:
- SchemaExpr already exists and is well-tested
- Provides rich expression language for computations
- Can be evaluated on DynamicValues with proper schema context

**Integration Points**:
- `literal[A](value)` - Create constant transform
- `defaultValue[A]` - Use schema default
- `fromTransform[A, B]` - Convert typed Transform to DynamicTransform

**Constraints**:
- SchemaExpr evaluation requires schema context
- Not all SchemaExpr operations are serializable as DynamicTransform
- Focused on primitive operations for migration use cases

---

### Decision 12: Transform[A, B] Typed Wrapper

**Date**: 2026-04-03
**Context**: Need type-safe transformation expressions alongside DynamicTransform.

**Decision**: Create `Transform[A, B]` as a typed wrapper around `DynamicTransform`.

**Rationale**:
- Provides compile-time type safety for transformations
- Maintains serializability through underlying DynamicTransform
- Composable with `>>>` operator
- Supports reverse transformation

**Usage**:
```scala
val stringToInt: Transform[String, Int] = Transform.stringToInt
val addFive: Transform[Int, Int] = Transform.intAdd(5)
val composed: Transform[String, Int] = stringToInt >>> addFive
```

**Alternative Considered**: Use functions `A => B` directly - rejected because:
- Not serializable
- Cannot be introspected
- Cannot be stored in registries

---

### Decision 13: Test Organization Strategy

**Date**: 2026-04-03
**Context**: Need comprehensive test coverage for the migration system.

**Decision**: Organize tests by API layer (DynamicMigration, Migration, MigrationBuilder, Transform).

**Rationale**:
- Tests mirror the API structure users interact with
- Each layer has different test requirements:
  - DynamicMigration: Low-level operations, edge cases, error handling
  - Migration: Type safety, composition, round-trip serialization
  - MigrationBuilder: DSL fluency, action accumulation
  - Transform: All transform types and their reverses

**Test Categories**:
1. **Happy Path** - Basic operations that should succeed
2. **Edge Cases** - Empty inputs, nested structures, boundary values
3. **Error Handling** - All error types and their messages
4. **Properties** - Associativity, identity, reversibility
5. **Serialization** - Round-trip encode/decode

---

### Decision 14: Documentation Structure

**Date**: 2026-04-03
**Context**: Need documentation that serves both new users and reference needs.

**Decision**: Create a single comprehensive guide with progressive complexity.

**Rationale**:
- Single source of truth for migration system
- Quick start for immediate productivity
- Full API reference for detailed lookup
- Best practices section for guidance

**Documentation Sections**:
1. **Overview** - What and why
2. **Quick Start** - Immediate productivity
3. **Migration Actions** - All available actions
4. **Transforms** - All available transforms
5. **Typed API** - Transform[A, B] usage
6. **Composition** - How to combine migrations
7. **Error Handling** - All error types
8. **Serialization** - Persistence patterns
9. **Advanced Usage** - DynamicMigration, SchemaExpr
10. **Best Practices** - Recommendations
11. **API Reference** - Method signatures
