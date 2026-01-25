# Stage 1: Fix Test and Commit Migration Work

**Date:** 2026-01-25
**Branch:** 471-type-id
**Prerequisite:** Uncommitted TypeName → TypeId migration work

---

## Overview

Fix the failing test in ZIOPreludeSupportSpec and commit the migration work.

---

## TODOs

### 1. Fix ZIOPreludeSupportSpec test assertion

**What to do:**
- File: `schema/shared/src/test/scala-3/zio/blocks/schema/ZIOPreludeSupportSpec.scala`
- Line 134: Change owner assertion from `containsString("NInt")` to `containsString("NewtypeCustom")`

**Why:**
- `NInt` extends `Newtype[Int]` without defining `given TypeId[NInt.Type]`
- TypeId.derived now searches for implicit TypeId and finds the one from base trait `NewtypeCustom`
- The owner being `NewtypeCustom` is correct behavior - it's the type that provides the TypeId

**Before:**
```scala
test("TypeId.derived auto-derives when no given TypeId is available") {
  val derivedTypeId1 = TypeId.derived[NInt.Type]
  val derivedTypeId2 = TypeId.derived[NInt.Type]
  assert(derivedTypeId1)(equalTo(derivedTypeId2)) &&
  assert(derivedTypeId1.name)(equalTo("Type")) &&
  assert(derivedTypeId1.owner.toString)(containsString("NInt"))
}
```

**After:**
```scala
test("TypeId.derived auto-derives when no given TypeId is available") {
  val derivedTypeId1 = TypeId.derived[NInt.Type]
  val derivedTypeId2 = TypeId.derived[NInt.Type]
  assert(derivedTypeId1)(equalTo(derivedTypeId2)) &&
  assert(derivedTypeId1.name)(equalTo("Type")) &&
  assert(derivedTypeId1.owner.toString)(containsString("NewtypeCustom"))
}
```

**Acceptance Criteria:**
- [ ] Owner assertion still present (tests the owner value)
- [ ] Assertion expects "NewtypeCustom" instead of "NInt"

**Commit:** NO (groups with verification)

---

### 2. Re-run Scala 3 tests

**What to do:**
```bash
sbt "++3.3.7; schemaJVM/test" 2>&1 | tee /tmp/test-scala3-stage1.txt
```

**Acceptance Criteria:**
- [ ] All tests pass
- [ ] ZIOPreludeSupportSpec passes

**Commit:** NO

---

### 3. Run Scala 2 tests

**What to do:**
```bash
sbt "++2.13.18; schemaJVM/test" 2>&1 | tee /tmp/test-scala2-stage1.txt
```

**Acceptance Criteria:**
- [ ] All tests pass

**Commit:** NO

---

### 4. Format code on both Scala versions

**What to do:**
```bash
sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"
```

**Acceptance Criteria:**
- [ ] All code formatted
- [ ] No formatting errors

**Commit:** NO

---

### 5. Commit migration work

**What to do:**
- Stage all changes (including deleted TypeName files)
- Commit with descriptive message

**Files to commit:**
- `schema-avro/src/test/scala/zio/blocks/schema/avro/AvroFormatSpec.scala`
- `schema/js-jvm/src/test/scala-3/zio/blocks/schema/NeotypeSupportSpec.scala`
- `schema/shared/src/main/scala-2/zio/blocks/schema/TypeNameCompanionVersionSpecific.scala` (DELETED)
- `schema/shared/src/main/scala-3/zio/blocks/schema/TypeNameCompanionVersionSpecific.scala` (DELETED)
- `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala`
- `schema/shared/src/test/scala-2/zio/blocks/schema/TypeNameVersionSpecificSpec.scala` (DELETED)
- `schema/shared/src/test/scala-2/zio/blocks/schema/ZIOPreludeSupportSpec.scala`
- `schema/shared/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala`
- `schema/shared/src/test/scala-3/zio/blocks/schema/TypeNameVersionSpecificSpec.scala` (DELETED)
- `schema/shared/src/test/scala-3/zio/blocks/schema/ZIOPreludeSupportSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/DynamicOpticSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/TypeNameSpec.scala` (DELETED)
- `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecDeriverSpec.scala`
- `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeId.scala`
- `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeIdMacros.scala`
- `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala`
- `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeIdMacros.scala`

**Commit Message:**
```
feat(typeid): Migrate TypeName to TypeId with implicit search support

Major changes:
- Remove TypeName in favor of TypeId throughout Schema module
- TypeId.derived now searches for existing given TypeId before deriving fresh
- TypeId.normalize preserves type arguments through alias chains
- Improved structural equality for TypeIds with recursive type args

API changes:
- Schema.withTypeName[B] → Schema.withTypeId[B]
- Schema.asOpaqueType[B] now requires implicit TypeId[B]
- Removed TypeNameCompanionVersionSpecific (both Scala 2 & 3)

Test updates:
- Added explicit given TypeId for newtypes (Name, Kilogram, Meter, EmojiDataId)
- Updated test assertions for new TypeId behavior
- Removed obsolete TypeNameSpec and TypeNameVersionSpecificSpec

Fixes #471
```

**Acceptance Criteria:**
- [ ] All files staged (including deletions)
- [ ] Commit created successfully
- [ ] `git status` shows clean working tree

---

## After Stage 1

Proceed to Stage 2: Execute TypeId Registry plan from `.sisyphus/plans/schema-macro-typeid-registry.md`
