# Migration Branch Fix Plan

## TL;DR

> **Quick Summary**: Reconcile diverged migration branches, implement deep type-level tracking with intersection types, split SchemaExpr into typed + dynamic components, and add comprehensive error message tests.
> 
> **Deliverables**:
> - Unified migration branch with all features preserved
> - MigrationBuilder with proper type-level tracking for nested/recursive types
> - DynamicSchemaExpr (serializable) + SchemaExpr[A,B] (typed wrapper)
> - Error tests with explicit message assertions
> 
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 3 waves
> **Critical Path**: Branch reconciliation → SchemaExpr split → Type tracking → Error tests

---

## Context

### Original Request
User identified that the migration branch is in a broken state. Key issues:
1. PR #966 was overwritten to point to `schema-migration-clean` instead of `schema-migration-system-519`
2. Type-level tracking in MigrationBuilder needs improvement for nested/recursive types
3. SchemaExpr needs to be split into serializable dynamic + typed wrapper
4. Error tests only assert `.isLeft` without checking actual error messages

### Interview Summary
**Key Discussions**:
- Branch strategy: Use `schema-migration-system-519` as base (has full history), cherry-pick improvements from `schema-migration-clean`
- Type tracking: Use intersection types (`FieldName["name"] & FieldName["age"]`) for both Scala 2/3 - **NO Tuples**
- Nested fields: Use dot notation paths (`FieldName["address.street"]`) for nested field tracking
- Nested API: Support **both** flat paths (`.addField(_.address.zip, default)`) and nested builder (`.transformNested(_.address)(...)`)
- **Migration composition**: `.migrateField` supports both:
  - Explicit: `.migrateField(_.user, _.user, userMigration)`
  - Implicit: `.migrateField(_.user, _.user)` - summons `Migration[User, UserV2]` from scope
  - Full expansion: When composing, expand all nested fields into type tracking
- SchemaExpr: Split so `DynamicSchemaExpr` is serializable, `SchemaExpr[A,B]` wraps it with schemas
- FieldName wrapper: Keep `FieldName[N <: String & Singleton]` to preserve literal types in macros

**Research Findings**:
- Schema uses `Reflect.Deferred` for recursive types with cycle guards
- Into uses macro-time tracking (mutable sets), not type-level
- ZIO Schema (original) uses ADT-based migrations with DynamicValue as intermediate
- Existing tests: `StructuralMigrationSpec`, `MigrationSpec`, `MigrationBuildValidationSpec`
- Error types: `SchemaError.MigrationError` with kinds like `PathNotFound`, `TypeMismatch`, `FieldNotFound`

### Metis Review
**Identified Gaps** (addressed):
- Branch state confusion: Explicitly define canonical branch and reconciliation steps
- SchemaExpr serialization: Detailed design for dynamic/typed split
- Test coverage: Specific error scenarios to test with expected messages

---

## Work Objectives

### Core Objective
Fix the migration branch to have:
1. Complete feature set from both diverged branches
2. Strong type-level field tracking that works for nested/recursive types
3. Fully serializable migration expressions
4. Comprehensive error tests with explicit assertions

### Concrete Deliverables
- Unified branch pushed to `origin/schema-migration-system-519`
- `DynamicSchemaExpr.scala` - new file with serializable expression ADT
- Updated `SchemaExpr.scala` - typed wrapper around dynamic expressions
- Updated `MigrationAction.scala` - uses DynamicSchemaExpr
- Updated `MigrationBuilder.scala` (Scala 2 & 3) - proper type tracking
- New tests for nested/recursive migration scenarios
- Error tests with explicit message assertions

### Definition of Done
- [ ] `sbt '++3.7.4; schemaJVM/test'` passes
- [ ] `sbt '++2.13.18; schemaJVM/test'` passes
- [ ] Coverage ≥ 80% for migration module
- [ ] All SchemaExpr expressions are serializable (no `Schema[A]` embedded)
- [ ] Error tests verify actual error messages, not just `.isLeft`

### Must Have
- Branch reconciliation with all tests passing
- DynamicSchemaExpr serializable and round-trippable
- Type-level tracking for MigrationBuilder
- At least 10 error message tests

### Must NOT Have (Guardrails)
- Do NOT merge unrelated #517 structural work unless explicitly needed
- Do NOT change public API signatures without justification
- Do NOT embed `Schema[A]` in any case class that needs serialization
- Do NOT write tests that only assert `.isLeft`

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES
- **Automated tests**: YES (tests-after approach)
- **Framework**: ZIO Test (bun test / sbt test)

### Agent-Executed QA Scenarios (MANDATORY)

**Scenario: Migration tests pass on Scala 3**
```
Tool: Bash
Steps:
  1. Run: sbt '++3.7.4; schemaJVM/test'
  2. Assert: Exit code 0
  3. Assert: Output contains "All tests passed" or similar
Expected Result: All migration tests pass
Evidence: Terminal output captured
```

**Scenario: Migration tests pass on Scala 2**
```
Tool: Bash
Steps:
  1. Run: sbt '++2.13.18; schemaJVM/test'
  2. Assert: Exit code 0
Expected Result: All migration tests pass
Evidence: Terminal output captured
```

**Scenario: DynamicSchemaExpr serialization round-trip**
```
Tool: Bash (sbt console or test)
Steps:
  1. Create DynamicSchemaExpr instances (Literal, Select, Relational, etc.)
  2. Convert to JSON using Schema[DynamicSchemaExpr]
  3. Parse back from JSON
  4. Assert: Parsed == original
Expected Result: All expression types serialize and deserialize correctly
Evidence: Test output
```

**Scenario: Error message contains expected text**
```
Tool: Bash (sbt test with specific test filter)
Steps:
  1. Run: sbt '++3.7.4; schemaJVM/testOnly *MigrationSpec* -- -t "error"'
  2. Assert: Tests pass
  3. Assert: Error message assertions are explicit (not just isLeft)
Expected Result: Error tests verify message content
Evidence: Test output
```

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: Branch reconciliation
└── Task 2: Research/design DynamicSchemaExpr

Wave 2 (After Wave 1):
├── Task 3: Implement DynamicSchemaExpr
├── Task 4: Update SchemaExpr to wrapper
└── Task 5: Update MigrationAction

Wave 3 (After Wave 2):
├── Task 6: Nested field tracking with dot notation
├── Task 7: Add nested/recursive type tests
└── Task 8: Add error message tests

Wave 3b (After Task 6):
└── Task 6b: Migration composition with .migrateField

Wave 4 (After Wave 3 + 3b):
└── Task 9: Final verification and PR update
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 3,4,5,6,7,8 | 2 |
| 2 | None | 3,4,5 | 1 |
| 3 | 1,2 | 5 | 4 |
| 4 | 1,2 | 5 | 3 |
| 5 | 3,4 | 6,6b,7,8 | None |
| 6 | 5 | 6b,9 | 7,8 |
| 6b | 6 | 9 | None |
| 7 | 5 | 9 | 6,8 |
| 8 | 5 | 9 | 6,7 |
| 9 | 6b,7,8 | None | None (final) |

---

## TODOs

- [ ] 1. Branch Reconciliation

  **What to do**:
  - Create integration branch from `schema-migration-system-519`
  - Cherry-pick intersection type improvements from `schema-migration-clean`:
    - `7408621e` - type alias for Scala 2
    - `09031b49` - intersection types for Scala 3
    - `b139457a` - FieldName wrapper documentation
    - `669167fe` - Scala 2 type tracking
    - `55029665` - format:off for macro syntax
  - Cherry-pick coverage/build improvements:
    - `9868afd4` - coverage exclusions
    - `646d4819` - expanded exclusions
  - Resolve any conflicts
  - Verify tests pass on both Scala versions

  **Must NOT do**:
  - Do NOT force-push to origin until verified
  - Do NOT include commits already present in system-519

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`git-master`]
    - `git-master`: Git operations, cherry-pick, conflict resolution

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Tasks 3,4,5,6,7,8
  - **Blocked By**: None

  **References**:
  - `schema-migration-system-519` branch - base branch with full history
  - `schema-migration-clean` branch - improvements to cherry-pick
  - Commits listed above with their specific changes

  **Acceptance Criteria**:
  - [ ] Integration branch created
  - [ ] All cherry-picks applied without conflict (or conflicts resolved)
  - [ ] `sbt '++3.7.4; schemaJVM/compile'` succeeds
  - [ ] `sbt '++2.13.18; schemaJVM/compile'` succeeds

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: Branch compiles on Scala 3
    Tool: Bash
    Steps:
      1. git checkout migration-519-integration
      2. sbt '++3.7.4; schemaJVM/compile'
      3. Assert: exit code 0
    Expected: Compilation succeeds
    Evidence: Terminal output

  Scenario: Branch compiles on Scala 2
    Tool: Bash
    Steps:
      1. sbt '++2.13.18; schemaJVM/compile'
      2. Assert: exit code 0
    Expected: Compilation succeeds
    Evidence: Terminal output
  ```

  **Commit**: YES
  - Message: `chore: reconcile migration branches with intersection type improvements`
  - Files: Multiple (cherry-picked changes)

---

- [ ] 2. Design DynamicSchemaExpr

  **What to do**:
  - Review current SchemaExpr case classes
  - Identify which need type parameters at runtime vs construction
  - Design DynamicSchemaExpr ADT that mirrors SchemaExpr but without type params
  - Design typed SchemaExpr wrapper that holds Schema + DynamicSchemaExpr
  - Document in notepad

  **Must NOT do**:
  - Do NOT implement yet - just design
  - Do NOT change files

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
  - **Skills**: []
    - Complex design task requiring deep analysis

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Tasks 3,4,5
  - **Blocked By**: None

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala` - current implementation
  - `schema/shared/src/main/scala/zio/blocks/schema/DynamicOptic.scala` - serializable path model
  - `schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala` - dynamic value ADT
  - `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationAction.scala` - uses SchemaExpr

  **Acceptance Criteria**:
  - [ ] DynamicSchemaExpr case class list defined
  - [ ] Typed SchemaExpr wrapper structure defined
  - [ ] Evaluation strategy documented
  - [ ] Serialization approach documented

  **Commit**: NO (design document only)

---

- [ ] 3. Implement DynamicSchemaExpr

  **What to do**:
  - Create `DynamicSchemaExpr.scala` with sealed trait and case classes:
    - `Literal(value: DynamicValue)`
    - `Select(path: DynamicOptic)` (replaces Optic with embedded Schema)
    - `Relational(left, right, operator)`
    - `Logical(left, right, operator)`
    - `Arithmetic(left, right, operator, numericKind: NumericKind)`
    - `Bitwise(left, right, operator)`
    - `BitwiseNot(expr)`
    - `Not(expr)`
    - `PrimitiveConversion(conversionType)`
    - String operations: `StringConcat`, `StringLength`, etc.
  - Implement `eval(input: DynamicValue): Either[SchemaError, DynamicValue]` for each
  - Reuse path traversal logic from current `SchemaExpr.Optic.walkPath`
  - Add `Schema[DynamicSchemaExpr]` for serialization

  **Must NOT do**:
  - Do NOT embed Schema[A] in any case class
  - Do NOT break existing SchemaExpr API yet (that's Task 4)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
    - Significant implementation work

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 4)
  - **Blocks**: Task 5
  - **Blocked By**: Tasks 1, 2

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala:478-632` - Optic.walkPath implementation to adapt
  - `schema/shared/src/main/scala/zio/blocks/schema/DynamicOptic.scala` - path traversal nodes
  - `schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala` - error types to use
  - Design from Task 2 notepad

  **Acceptance Criteria**:
  - [ ] `DynamicSchemaExpr.scala` created with all case classes
  - [ ] `eval` method implemented for each case
  - [ ] Unit tests for each expression type
  - [ ] Schema[DynamicSchemaExpr] derived and tested

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: DynamicSchemaExpr.Literal evaluates correctly
    Tool: Bash
    Steps:
      1. Run test: sbt 'testOnly *DynamicSchemaExprSpec*'
      2. Assert: Literal(DynamicValue.Primitive(Int(42))).eval(any) == Right(...)
    Expected: Literal returns its value
    Evidence: Test output

  Scenario: DynamicSchemaExpr serialization round-trip
    Tool: Bash
    Steps:
      1. Create various DynamicSchemaExpr instances
      2. schema.toDynamicValue(expr) -> JSON -> schema.fromDynamicValue
      3. Assert: original == deserialized
    Expected: All expressions serialize correctly
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `feat(schema): add DynamicSchemaExpr for serializable expressions`
  - Files: `schema/shared/src/main/scala/zio/blocks/schema/DynamicSchemaExpr.scala`

---

- [ ] 4. Refactor SchemaExpr to Typed Wrapper

  **What to do**:
  - Rename current `SchemaExpr` to preserve existing functionality initially
  - Create new `SchemaExpr[A, B]` as:
    ```scala
    final case class SchemaExpr[A, B](
      dynamic: DynamicSchemaExpr,
      inputSchema: Schema[A],
      outputSchema: Schema[B]
    ) {
      def eval(input: A): Either[OpticCheck, Seq[B]] = ...
      def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = ...
      def toDynamic: DynamicSchemaExpr = dynamic
    }
    ```
  - Update Optic DSL methods to construct SchemaExpr via DynamicSchemaExpr
  - Ensure backward compatibility of public API

  **Must NOT do**:
  - Do NOT break existing Optic DSL usage
  - Do NOT remove existing SchemaExpr case classes until Task 5 migrates usages

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 3)
  - **Blocks**: Task 5
  - **Blocked By**: Tasks 1, 2

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/SchemaExpr.scala` - current implementation
  - `schema/shared/src/main/scala/zio/blocks/schema/Optic.scala:78-277` - DSL methods that create SchemaExpr
  - DynamicSchemaExpr from Task 3

  **Acceptance Criteria**:
  - [ ] SchemaExpr[A, B] wraps DynamicSchemaExpr
  - [ ] Optic DSL methods produce new SchemaExpr
  - [ ] Existing tests still pass
  - [ ] `eval` and `evalDynamic` work via dynamic evaluation

  **Commit**: YES
  - Message: `refactor(schema): make SchemaExpr a typed wrapper around DynamicSchemaExpr`
  - Files: `SchemaExpr.scala`, `Optic.scala`

---

- [ ] 5. Update MigrationAction to use DynamicSchemaExpr

  **What to do**:
  - Change MigrationAction fields from `SchemaExpr[_, _]` to `DynamicSchemaExpr`:
    - `AddField.default`
    - `DropField.defaultForReverse`
    - `TransformValue.transform`
    - `Mandate.default`
    - `Join.combiner`
    - `Split.splitter`
    - `ChangeType.converter`
    - `TransformElements.transform`
    - `TransformKeys.transform`
    - `TransformValues.transform`
  - Update DynamicMigration to use DynamicSchemaExpr.eval
  - Update MigrationBuilder methods to convert SchemaExpr → DynamicSchemaExpr

  **Must NOT do**:
  - Do NOT change MigrationBuilder public API signatures

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after 3,4)
  - **Blocks**: Tasks 6,7,8
  - **Blocked By**: Tasks 3, 4

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationAction.scala` - action definitions
  - `schema/shared/src/main/scala/zio/blocks/schema/migration/DynamicMigration.scala:128-145` - extractDynamicValue
  - DynamicSchemaExpr from Task 3

  **Acceptance Criteria**:
  - [ ] All MigrationAction expression fields use DynamicSchemaExpr
  - [ ] DynamicMigration uses DynamicSchemaExpr.eval instead of extractDynamicValue
  - [ ] All existing migration tests pass
  - [ ] Migrations are fully serializable (no Schema[A] embedded)

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: Migration with transform expression works
    Tool: Bash
    Steps:
      1. Run: sbt 'testOnly *MigrationSpec* -- -t "transform"'
      2. Assert: Tests pass
    Expected: Transform expressions evaluate correctly
    Evidence: Test output

  Scenario: Migration serialization round-trip
    Tool: Bash
    Steps:
      1. Create Migration with various expressions
      2. Serialize to JSON
      3. Deserialize
      4. Apply to test value
      5. Assert: Same result as original
    Expected: Serialized migrations work identically
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `refactor(migration): use DynamicSchemaExpr for serializable migrations`
  - Files: `MigrationAction.scala`, `DynamicMigration.scala`, `MigrationBuilder.scala`

---

- [ ] 6. Improve MigrationBuilder Type Tracking for Nested Types

  **What to do**:
  - Keep intersection types with `FieldName[N]` wrapper (NO Tuples)
  - Add **nested field tracking with dot notation**: 
    - When migrating `address` in `Person(name, address: Address)`, track both:
      - `FieldName["address"]` (the field itself)
      - `FieldName["address.street"]`, `FieldName["address.city"]` (nested fields)
  - Support **both APIs** for nested migrations:
    - Flat path: `.addField(_.address.zip, default)` - direct access to nested fields
    - Nested builder: `.transformNested(_.address)(b => b.addField(_.zip, default))`
  - Update validation macros to check nested field completeness
  - Add compile-time tests for incomplete nested migrations
  - Example types for testing:
    ```scala
    case class PersonV1(name: String, address: AddressV1)
    case class AddressV1(street: String, city: String)
    
    case class PersonV2(name: String, address: AddressV2)
    case class AddressV2(street: String, city: String, zip: String)
    ```
  - Migration should fail at compile-time if `address.zip` is not provided

  **Must NOT do**:
  - Do NOT use Tuples - keep intersection types
  - Do NOT break existing migration builder usage
  - Do NOT change the `FieldName[N]` wrapper approach

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
  - **Skills**: []
    - Complex type-level programming with macros

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 7,8)
  - **Blocks**: Task 9
  - **Blocked By**: Task 5

  **References**:
  - `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilder.scala` - Scala 3 builder
  - `schema/shared/src/main/scala-2/zio/blocks/schema/migration/MigrationBuilder.scala` - Scala 2 builder
  - `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationValidationMacros.scala` - validation macros
  - `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderMacros.scala` - field extraction
  - `schema/jvm/src/test/scala/zio/blocks/schema/into/structural/ProductToStructuralSpec.scala` - nested type examples

  **Acceptance Criteria**:
  - [ ] Nested fields tracked with dot notation: `FieldName["address.street"]`
  - [ ] `.transformNested` API added for nested builder pattern
  - [ ] Flat path API works: `.addField(_.address.zip, default)`
  - [ ] Compile-time error when nested field is missing
  - [ ] Tests for 2-level nested types
  - [ ] Tests for Option-wrapped nested fields

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: Incomplete nested migration fails at compile-time
    Tool: Bash
    Steps:
      1. Create migration PersonV1 → PersonV2 without providing address.zip
      2. Call .build
      3. Assert: Compilation fails with error about missing field
    Expected: Compile-time error mentioning "address.zip"
    Evidence: Compiler error message

  Scenario: Complete nested migration compiles and runs
    Tool: Bash
    Steps:
      1. Create migration PersonV1 → PersonV2 with address.zip provided
      2. Call .build
      3. Apply to test value
      4. Assert: Migration succeeds with correct result
    Expected: Migration works end-to-end
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `feat(migration): add nested field tracking with dot notation paths`
  - Files: `MigrationBuilder.scala` (both), `MigrationBuilderMacros.scala`, `MigrationValidationMacros.scala`

---

- [ ] 6b. Add Migration Composition with `.migrateField`

  **What to do**:
  - Add `.migrateField` method with two overloads:
    ```scala
    // Explicit - pass migration directly
    def migrateField[F1, F2](
      source: A => F1, 
      target: B => F2, 
      migration: Migration[F1, F2]
    ): MigrationBuilder[A, B, SourceHandled & FieldName["fieldPath"], TargetProvided & ...]
    
    // Implicit - summon Migration from scope
    def migrateField[F1, F2](
      source: A => F1, 
      target: B => F2
    )(using Migration[F1, F2]): MigrationBuilder[...]
    ```
  - Macro implementation should:
    - Scala 3: Use `Expr.summon[Migration[F1, F2]]` or `Implicits.search`
    - Scala 2: Use `c.inferImplicitValue(migrationType, silent = true)`
  - **Full field expansion**: When using a composed migration, expand ALL its tracked fields:
    - If `userMigration` tracks `name`, `age`, `email`
    - Then `profileMigration.migrateField(_.user, _.user, userMigration)` adds:
      - `FieldName["user"]`
      - `FieldName["user.name"]`
      - `FieldName["user.age"]`
      - `FieldName["user.email"]`
  - Also support automatic resolution in `.build`:
    - For unhandled fields where source/target types differ, search for implicit `Migration`

  **Must NOT do**:
  - Do NOT silently fail if no migration found - clear compile error
  - Do NOT allow partial migrations (all nested fields must be tracked)

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
  - **Skills**: []
    - Complex macro work with implicit summoning

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 (after Task 6)
  - **Blocks**: Task 9
  - **Blocked By**: Task 6

  **References**:
  - `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala:34-50` - `Expr.summon` pattern
  - `schema/shared/src/main/scala-2/zio/blocks/schema/IntoVersionSpecific.scala:536-566` - `inferImplicitValue` pattern
  - `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderMacros.scala` - existing macros
  - Chimney's `TotalOuterTransformer` pattern for composition

  **Acceptance Criteria**:
  - [ ] `.migrateField(src, tgt, migration)` works with explicit migration
  - [ ] `.migrateField(src, tgt)` summons implicit/given Migration
  - [ ] All nested fields from composed migration are expanded into type tracking
  - [ ] Clear compile error when no migration found
  - [ ] Test: Compose `Migration[User, UserV2]` inside `Migration[Profile, ProfileV2]`

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: Explicit migration composition works
    Tool: Bash
    Steps:
      1. Define Migration[User, UserV2]
      2. Use .migrateField(_.user, _.user, userMigration) in Profile migration
      3. Call .build
      4. Apply to Profile value
      5. Assert: User is correctly migrated
    Expected: Composition works end-to-end
    Evidence: Test output

  Scenario: Implicit migration composition works
    Tool: Bash
    Steps:
      1. Define given Migration[User, UserV2]
      2. Use .migrateField(_.user, _.user) without explicit param
      3. Call .build
      4. Assert: Migration compiles and runs
    Expected: Implicit is found and used
    Evidence: Test output

  Scenario: Missing migration gives clear compile error
    Tool: Bash
    Steps:
      1. Use .migrateField(_.user, _.user) without Migration in scope
      2. Call .build
      3. Assert: Compilation fails with "No Migration[User, UserV2] found"
    Expected: Clear error message
    Evidence: Compiler error
  ```

  **Commit**: YES
  - Message: `feat(migration): add .migrateField for migration composition`
  - Files: `MigrationBuilder.scala` (both), `MigrationBuilderMacros.scala`

---

- [ ] 7. Add Nested/Recursive Type Migration Tests

  **What to do**:
  - Add test cases with 2-4 level nested case classes
  - Add test cases migrating nested fields
  - Add tests for recursive types (if supported by migration)
  - Add tests for collection-wrapped nested types (List[Nested], Option[Nested])
  - Document any limitations with recursive types

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 6,8)
  - **Blocks**: Task 9
  - **Blocked By**: Task 5

  **References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationSpec.scala` - existing tests
  - `schema/jvm/src/test/scala-3/zio/blocks/schema/migration/StructuralMigrationSpec.scala` - structural tests
  - `schema/shared/src/test/scala/zio/blocks/schema/stress/DeepNestingStressSpec.scala` - nesting examples

  **Acceptance Criteria**:
  - [ ] At least 5 tests for nested type migrations
  - [ ] At least 3 tests for collection-wrapped migrations
  - [ ] Tests document recursive type limitations (if any)

  **Commit**: YES
  - Message: `test(migration): add comprehensive nested type migration tests`
  - Files: `MigrationSpec.scala`

---

- [ ] 8. Add Error Message Tests

  **What to do**:
  - For each MigrationErrorKind, add test that verifies message content:
    - `PathNotFound` - message contains path
    - `TypeMismatch` - message contains expected and actual types
    - `MissingDefault` - message contains field name
    - `TransformFailed` - message contains reason
    - `FieldNotFound` - message contains field name
    - `FieldAlreadyExists` - message contains field name
    - `CaseNotFound` - message contains case name
    - `InvalidValue` - message contains reason
    - `MandateFailed` - message contains reason
  - Use pattern: `assertTrue(result.swap.exists(_.message.contains("expected text")))`
  - Also add structured checks: `assertTrue(result.swap.exists(_.errors.head.isInstanceOf[SchemaError.MigrationError]))`

  **Must NOT do**:
  - Do NOT write tests that only check `.isLeft`
  - Do NOT use overly brittle exact message equality

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with 6,7)
  - **Blocks**: Task 9
  - **Blocked By**: Task 5

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala` - error types and message formats
  - `schema/shared/src/main/scala/zio/blocks/schema/migration/DynamicMigration.scala` - where errors are created
  - `schema/shared/src/test/scala/zio/blocks/schema/IntoSpec.scala` - example error assertions

  **Acceptance Criteria**:
  - [ ] At least 10 error message tests
  - [ ] Each MigrationErrorKind has at least 1 test
  - [ ] Tests verify message contains expected content
  - [ ] Tests verify error type (SchemaError.MigrationError)

  **Agent-Executed QA Scenarios**:
  ```
  Scenario: PathNotFound error contains path info
    Tool: Bash
    Steps:
      1. Create migration that accesses non-existent path
      2. Apply to input
      3. Assert: result.isLeft
      4. Assert: result.swap.exists(_.message.contains("not found"))
      5. Assert: result.swap.exists(_.message.contains("fieldName"))
    Expected: Error message is descriptive
    Evidence: Test output

  Scenario: TypeMismatch error contains type info
    Tool: Bash
    Steps:
      1. Create migration with type mismatch
      2. Apply to input
      3. Assert: message contains expected and actual types
    Expected: Error message helps debugging
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `test(migration): add explicit error message assertions`
  - Files: `MigrationSpec.scala`

---

- [ ] 9. Final Verification and PR Update

  **What to do**:
  - Run full test suite on Scala 3.7, 3.3, 2.13
  - Run coverage report, verify ≥80%
  - Format code with scalafmt
  - Update PR #966 description with new features
  - Push to `origin/schema-migration-system-519`

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`git-master`]

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4 (final)
  - **Blocks**: None
  - **Blocked By**: Tasks 6b,7,8

  **Acceptance Criteria**:
  - [ ] All tests pass on Scala 3.7.4
  - [ ] All tests pass on Scala 3.3.x
  - [ ] All tests pass on Scala 2.13.x
  - [ ] Coverage ≥80% for migration module
  - [ ] Code formatted
  - [ ] PR updated and pushed

  **Commit**: YES
  - Message: `chore: final migration system polish`
  - Files: Any formatting/cleanup changes

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `chore: reconcile migration branches` | Cherry-picked | Compile test |
| 3 | `feat(schema): add DynamicSchemaExpr` | DynamicSchemaExpr.scala | Unit tests |
| 4 | `refactor(schema): SchemaExpr as wrapper` | SchemaExpr.scala, Optic.scala | Existing tests |
| 5 | `refactor(migration): use DynamicSchemaExpr` | Migration*.scala | Migration tests |
| 6 | `feat(migration): nested field tracking` | MigrationBuilder*.scala | Type tests |
| 6b | `feat(migration): add .migrateField composition` | MigrationBuilder*.scala | Composition tests |
| 7 | `test(migration): nested type tests` | MigrationSpec.scala | New tests |
| 8 | `test(migration): error message tests` | MigrationSpec.scala | New tests |
| 9 | `chore: final polish` | Various | Full suite |

---

## Success Criteria

### Verification Commands
```bash
# Full test suite
sbt '++3.7.4; schemaJVM/test'
sbt '++2.13.18; schemaJVM/test'

# Coverage
sbt '++3.7.4; project schemaJVM; coverage; test; coverageReport'

# Specific migration tests
sbt '++3.7.4; schemaJVM/testOnly *MigrationSpec*'
```

### Final Checklist
- [ ] All tests pass on Scala 2.13.x, 3.3.x, 3.7.x
- [ ] Coverage ≥80% for migration module
- [ ] DynamicSchemaExpr is fully serializable
- [ ] SchemaExpr[A,B] wraps DynamicSchemaExpr
- [ ] MigrationBuilder tracks nested fields with dot notation (e.g., `FieldName["address.street"]`)
- [ ] Both flat path and nested builder APIs work for nested migrations
- [ ] `.migrateField` works with explicit and implicit migrations
- [ ] Migration composition expands all nested fields into type tracking
- [ ] Error tests verify message content
- [ ] PR #966 updated with all changes
