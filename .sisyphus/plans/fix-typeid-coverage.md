# Fix TypeId Branch Coverage

## TL;DR

> **Quick Summary**: Move Scala 3-only functions (`appearsInUnion`, `typeReprContains`) from shared `TypeIdOps.scala` back to Scala 3 `TypeId.scala` to fix branch coverage failure on Scala 2.
> 
> **Deliverables**:
> - Fixed branch coverage meeting 65% threshold for Scala 2
> - Clean separation of Scala 3-only code from shared code
> - All tests passing on both Scala versions
> 
> **Estimated Effort**: Quick
> **Parallel Execution**: NO - sequential
> **Critical Path**: Move code → Test Scala 2 coverage → Test Scala 3 → Format → Commit

---

## Context

### Original Request
Extract shared code from TypeId.scala (Scala 2 and Scala 3 versions) to reduce duplication.

### Interview Summary
**Key Discussions**:
- Created `TypeIdOps.scala` with shared helper functions
- Created `TypeIdInstances.scala` with predefined TypeId instances
- Discovered that Scala 3-only functions in shared code cause Scala 2 coverage failure

**Research Findings**:
- Branch coverage on Scala 2 is 56.74% (below 65% threshold)
- Root cause: `appearsInUnion` and `typeReprContains` are only called for Scala 3 union/intersection types
- These functions have branches that are never executed in Scala 2

---

## Work Objectives

### Core Objective
Fix Scala 2 branch coverage by moving Scala 3-specific code out of shared sources.

### Concrete Deliverables
- Modified `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdOps.scala` - remove `appearsInUnion` and `typeReprContains`
- Modified `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala` - add private helper functions

### Definition of Done
- [x] `sbt '++2.13.18; project typeidJVM; coverage; test; coverageReport'` passes with Branch >= 65%
- [x] `sbt '++3.3.7; typeidJVM/test'` passes
- [x] All changes committed

### Must Have
- Branch coverage >= 65% on Scala 2
- All existing tests still pass
- Code properly formatted

### Must NOT Have (Guardrails)
- Do NOT change `build.sbt` coverage settings
- Do NOT add stub tests just to hit coverage
- Do NOT break Scala 3 functionality

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES
- **User wants tests**: Existing tests are sufficient
- **Framework**: ZIO Test via sbt

### Manual Execution Verification

**For coverage check:**
- [x] Command: `sbt '++2.13.18; project typeidJVM; coverage; test; coverageReport'`
- [x] Expected: Statement coverage >= 75%, Branch coverage >= 65% (Actual: 86.84% stmt, 68.86% branch)
- [x] Exit code: 0

**For Scala 3 test:**
- [x] Command: `sbt '++3.3.7; typeidJVM/test'`
- [x] Expected: All tests pass (397 tests passed)
- [x] Exit code: 0

---

## TODOs

- [x] 1. Move `appearsInUnion` from TypeIdOps.scala to Scala 3 TypeId.scala

  **What to do**:
  - Remove `appearsInUnion` function from `TypeIdOps.scala` (lines 153-163)
  - Add as private function in Scala 3 `TypeId.scala` object

  **Must NOT do**:
  - Change the function signature
  - Break the call site in `isSubtypeOf`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple code move operation, single function
  - **Skills**: []
    - No special skills needed for simple edit

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 2, 3, 4, 5
  - **Blocked By**: None

  **References**:
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdOps.scala:153-163` - Function to remove
  - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala:143` - Call site using `TypeIdOps.appearsInUnion`

  **Acceptance Criteria**:
  - [x] Function removed from `TypeIdOps.scala`
  - [x] Function added to Scala 3 `TypeId.scala` as private helper
  - [x] Call site in `isSubtypeOf` updated to use local function

  **Commit**: NO (groups with 2)

---

- [x] 2. Move `typeReprContains` from TypeIdOps.scala to Scala 3 TypeId.scala

  **What to do**:
  - Remove `typeReprContains` function from `TypeIdOps.scala` (lines 201-207)
  - Add as private function in Scala 3 `TypeId.scala` object

  **Must NOT do**:
  - Change the function signature
  - Break the call site in `isSubtypeOf`

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple code move operation, single function
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 3, 4, 5
  - **Blocked By**: Task 1

  **References**:
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdOps.scala:201-207` - Function to remove
  - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala:149` - Call site using `TypeIdOps.typeReprContains`

  **Acceptance Criteria**:
  - [x] Function removed from `TypeIdOps.scala`
  - [x] Function added to Scala 3 `TypeId.scala` as private helper
  - [x] Call site in `isSubtypeOf` updated to use local function

  **Commit**: NO (groups with 3)

---

- [x] 3. Run Scala 2 coverage to verify fix

  **What to do**:
  - Run coverage command and verify branch coverage >= 65%

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single command execution
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 4
  - **Blocked By**: Task 1, 2

  **References**:
  - `build.sbt:99-100` - Coverage thresholds (75% statement, 65% branch)

  **Acceptance Criteria**:
  - [x] Command: `sbt '++2.13.18; project typeidJVM; coverage; test; coverageReport'`
  - [x] Expected output contains: Branch coverage >= 65% (Actual: 68.86%)
  - [x] Exit code: 0

  **Commit**: NO (groups with 4)

---

- [x] 4. Run Scala 3 tests to verify no regression

  **What to do**:
  - Run Scala 3 tests to confirm union/intersection type handling still works

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single command execution
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 5
  - **Blocked By**: Task 3

  **References**:
  - `typeid/shared/src/test/scala-3/zio/blocks/typeid/Scala3DerivationSpec.scala` - Scala 3 specific tests

  **Acceptance Criteria**:
  - [x] Command: `sbt '++3.3.7; typeidJVM/test'`
  - [x] All tests pass (397 tests)
  - [x] Exit code: 0

  **Commit**: NO (groups with 5)

---

- [x] 5. Format and commit all changes

  **What to do**:
  - Format modified files
  - Create commit with descriptive message

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Standard formatting and git operations
  - **Skills**: [`git-master`]
    - `git-master`: Needed for proper commit workflow

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential (final)
  - **Blocks**: None
  - **Blocked By**: Task 4

  **References**:
  - `AGENTS.md` - Formatting workflow (scalafmt + Test/scalafmt)

  **Acceptance Criteria**:
  - [x] Command: `sbt 'typeidJVM/scalafmt; typeidJVM/Test/scalafmt'`
  - [x] No formatting changes (or format applied)
  - [x] Commit created with message: `refactor: extract shared TypeId code to reduce duplication`
  - [x] Commit includes all modified files:
    - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdOps.scala`
    - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdInstances.scala` (new)
    - `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeId.scala`
    - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala`
    - `typeid/shared/src/test/scala-3/zio/blocks/typeid/Scala3DerivationSpec.scala`

  **Commit**: YES
  - Message: `refactor: extract shared TypeId code to reduce duplication`
  - Files: All modified typeid files
  - Pre-commit: Tests pass, coverage verified

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 5 | `refactor: extract shared TypeId code to reduce duplication` | All modified typeid files | Coverage + tests |

---

## Success Criteria

### Verification Commands
```bash
# Scala 2 coverage (must pass)
sbt '++2.13.18; project typeidJVM; coverage; test; coverageReport'
# Expected: Branch coverage >= 65%

# Scala 3 tests (must pass)
sbt '++3.3.7; typeidJVM/test'
# Expected: All tests pass
```

### Final Checklist
- [x] Branch coverage >= 65% on Scala 2 (Actual: 68.86%)
- [x] All Scala 3 tests pass (397 tests)
- [x] `appearsInUnion` only in Scala 3 source
- [x] `typeReprContains` only in Scala 3 source
- [x] All changes committed (1a79ca2b)
