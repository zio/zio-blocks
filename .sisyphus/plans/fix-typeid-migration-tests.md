# Fix TypeId Migration Test Failures

## Context

### Original Request
Complete the TypeName to TypeId migration on branch `471-type-id`. During the session, some Reflect.scala changes were accidentally reverted. Several tests are now failing.

### Current State
- Branch: `471-type-id`
- Uncommitted changes exist in: Schema.scala, test files, TypeId files
- Reflect.scala was reset to committed state (missing the error-handling fix)
- 5 test suites failing: SchemaSpec, SchemaVersionSpecificSpec, JsonBinaryCodecDeriverSpec, NeotypeSupportSpec, ZIOPreludeSupportSpec

### Test Failures Identified
1. **"preserves SchemaError details"** - Wrapper.fromDynamicValue wraps errors instead of preserving them
2. **"toDynamicValue and fromDynamicValue with wrapper in a case class"** - ArrayIndexOutOfBoundsException  
3. **JsonBinaryCodecDeriverSpec - PosInt** - ClassCastException with custom AnyVal schema
4. **NeotypeSupportSpec** - TypeId-related failures
5. **ZIOPreludeSupportSpec** - TypeId-related failures

---

## Work Objectives

### Core Objective
Fix all failing tests related to the TypeId migration so the branch can be merged.

### Concrete Deliverables
- All schemaJVM tests passing on Scala 3.3.7
- All schemaJVM tests passing on Scala 2.13.18
- Code formatted on both Scala versions

### Definition of Done
- [ ] `sbt "++3.3.7; schemaJVM/test"` passes
- [ ] `sbt "++2.13.18; schemaJVM/test"` passes
- [ ] Code formatted: `sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"`

### Must Have
- Fix Wrapper.fromDynamicValue to preserve original SchemaError
- Fix any other test failures

### Must NOT Have (Guardrails)
- Do not change test expectations unless the test is wrong
- Do not remove tests to make CI pass
- Do not break Scala 2/3 cross-compilation

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES (ZIO Test)
- **User wants tests**: Tests already exist, just need to pass
- **Framework**: ZIO Test via sbt

### Verification Commands
```bash
# Single test for quick iteration
sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.SchemaSpec"

# Full test suite
sbt "++3.3.7; schemaJVM/test"
sbt "++2.13.18; schemaJVM/test"
```

---

## TODOs

- [ ] 1. Fix Wrapper.fromDynamicValue error handling

  **What to do**:
  - Edit `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`
  - Find `Wrapper.fromDynamicValue` method (around line 1078-1088)
  - Change from:
    ```scala
    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      (wrapped.fromDynamicValue(value, trace) match {
        case Right(unwrapped) =>
          binding.wrap(unwrapped) match {
            case Left(error) => new Left(SchemaError.expectationMismatch(trace, s"Expected ${typeId.name}: $error"))
            case right       => right
          }
        case left => left
      }).asInstanceOf[Either[SchemaError, A]]
    ```
  - To:
    ```scala
    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      wrapped.fromDynamicValue(value, trace) match {
        case Right(unwrapped) => binding.wrap(unwrapped)
        case left             => left.asInstanceOf[Either[SchemaError, A]]
      }
    ```

  **Must NOT do**:
  - Change the method signature
  - Break other Wrapper functionality

  **Parallelizable**: NO (must be done first)

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:1078-1088` - Current implementation
  - `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala:1734-1745` - Test that expects preserved error

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.SchemaSpec -- -t 'preserves SchemaError'"` passes

  **Commit**: NO (group with other fixes)

---

- [ ] 2. Investigate and fix ArrayIndexOutOfBoundsException in Wrapper test

  **What to do**:
  - Run the failing test to get full stack trace
  - The error is in `Reflect$Record.toDynamicValue` - may be a register allocation issue
  - Investigate if this is related to the Wrapper changes or a separate issue
  - Fix the root cause

  **Must NOT do**:
  - Skip or delete the test

  **Parallelizable**: NO (depends on understanding TODO 1)

  **References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala:1691` - Test location
  - `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala:420` - Record.toDynamicValue
  - `schema/shared/src/main/scala/zio/blocks/schema/binding/Register.scala` - Register implementation

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.SchemaSpec -- -t 'wrapper in a case class'"` passes

  **Commit**: NO (group with other fixes)

---

- [ ] 3. Fix JsonBinaryCodecDeriverSpec PosInt ClassCastException

  **What to do**:
  - Investigate the ClassCastException: `Integer cannot be cast to PosInt`
  - This happens in the encoder at `JsonBinaryCodecDeriver.scala:1889`
  - Likely an issue with how custom wrapper schemas interact with the JSON codec
  - May need to adjust how PosInt schema is defined or how the codec handles wrappers

  **Must NOT do**:
  - Change how PosInt works if it would break other usages

  **Parallelizable**: YES (with TODO 4, 5 - can investigate in parallel)

  **References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecDeriverSpec.scala:3402` - PosInt definition
  - `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecDeriverSpec.scala:1699` - Failing test
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala:1889` - Encoder location

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.json.JsonBinaryCodecDeriverSpec -- -t 'AnyVal'"` passes

  **Commit**: NO (group with other fixes)

---

- [ ] 4. Fix NeotypeSupportSpec failures

  **What to do**:
  - Run the test to see specific failures
  - These are likely related to TypeId derivation for newtype wrappers
  - Compare with how the tests expect TypeId to work vs how it's implemented

  **Must NOT do**:
  - Break newtype support

  **Parallelizable**: YES (with TODO 3, 5)

  **References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/NeotypeSupportSpec.scala` - Test file
  - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala` - TypeId implementation

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.NeotypeSupportSpec"` passes

  **Commit**: NO (group with other fixes)

---

- [ ] 5. Fix ZIOPreludeSupportSpec failures

  **What to do**:
  - Run the test to see specific failures
  - Similar to NeotypeSupportSpec, likely TypeId-related
  - Fix any issues with how ZIO Prelude newtypes work with TypeId

  **Must NOT do**:
  - Break ZIO Prelude newtype support

  **Parallelizable**: YES (with TODO 3, 4)

  **References**:
  - `schema/shared/src/test/scala-3/zio/blocks/schema/ZIOPreludeSupportSpec.scala` - Test file

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.ZIOPreludeSupportSpec"` passes

  **Commit**: NO (group with other fixes)

---

- [ ] 6. Run full test suite on Scala 3.3.7

  **What to do**:
  - Run all JVM tests on Scala 3
  - Fix any remaining failures

  **Parallelizable**: NO (depends on 1-5)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; testJVM"` passes with 0 failures

  **Commit**: NO (continue to next step)

---

- [ ] 7. Run full test suite on Scala 2.13.18

  **What to do**:
  - Run all JVM tests on Scala 2
  - Fix any Scala 2-specific failures

  **Parallelizable**: NO (depends on 6)

  **Acceptance Criteria**:
  - [ ] `sbt "++2.13.18; testJVM"` passes with 0 failures

  **Commit**: NO (continue to next step)

---

- [ ] 8. Format code and commit

  **What to do**:
  - Format all code on both Scala versions
  - Stage and commit all changes
  - Use descriptive commit message

  **Parallelizable**: NO (final step)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"` completes
  - [ ] `git status` shows clean working tree after commit
  - [ ] Commit message describes TypeId migration fixes

  **Commit**: YES
  - Message: `fix: Complete TypeId migration - fix Wrapper error handling and test failures`
  - Files: All modified files
  - Pre-commit: `sbt check` (if available)

---

## Success Criteria

### Verification Commands
```bash
# All tests pass on both Scala versions
sbt "++3.3.7; testJVM"    # Expected: All tests pass
sbt "++2.13.18; testJVM"  # Expected: All tests pass

# Code is formatted
sbt check                  # Expected: No formatting issues
```

### Final Checklist
- [ ] All "preserves SchemaError" test passes
- [ ] All Wrapper-related tests pass
- [ ] All JsonBinaryCodecDeriver tests pass
- [ ] All NeotypeSupportSpec tests pass
- [ ] All ZIOPreludeSupportSpec tests pass
- [ ] Tests pass on Scala 3.3.7
- [ ] Tests pass on Scala 2.13.18
- [ ] Code formatted
- [ ] Changes committed
