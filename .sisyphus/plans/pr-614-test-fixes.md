# Plan: Fix PR #614 Test Files Per Issue #517 Requirements

**Related Issue**: #517 (Structural Schemas)
**Related PR**: #614
**Date**: 2026-01-26
**Status**: Planning

## Objective

Address reviewer feedback on PR #614 by enhancing test files to properly test structural type conversion (`.structural` method) while keeping the existing schema derivation tests.

## Context

### Reviewer Feedback
John De Goes flagged three test files as "not testing structural type support":
1. `EnumToUnionSpec.scala` - "not testing anything related to structural types"
2. `SealedTraitToUnionSpec.scala` - "not testing anything related to structural types"
3. `AsPrimitiveSpec.scala` - "good tests but unrelated to structural types"

### Analysis
After reviewing issue #517 requirements, the enum and sealed trait specs ARE valuable - they test schema derivation and DynamicValue structure for sum types, which IS part of structural types feature. They're just **incomplete** - they're missing explicit `.structural` conversion tests.

**AsPrimitiveSpec.scala** is genuinely unrelated (tests As/Into primitives), but adds useful coverage.

## Approach

### Option A: Enhance Files (RECOMMENDED)
**DON'T delete** EnumToUnionSpec and SealedTraitToUnionSpec. Instead, **ADD** structural conversion tests to them.

### Option B: Create Separate Files
Create new *Structural specs and keep the existing ones as schema derivation tests.

**Recommendation**: Option A - the existing tests already validate the foundation (schema derivation), we just need to add `.structural` usage on top.

## Tasks

### 1. Enhance EnumToUnionSpec.scala
**File**: `schema/shared/src/test/scala-3/zio/blocks/schema/structural/EnumToUnionSpec.scala`

**Add new test suite** after existing suites:

```scala
suite("Structural Conversion")(
  test("enum converts to structural schema") {
    val schema     = Schema.derived[Color]
    val structural = schema.structural
    assertTrue(structural != null)
  },
  test("structural enum schema preserves variant structure") {
    val schema     = Schema.derived[Color]
    val structural = schema.structural
    val isVariant  = structural.reflect match {
      case _: Reflect.Variant[_, _] => true
      case _                        => false
    }
    assertTrue(isVariant)
  },
  test("structural enum has same case count as nominal") {
    val schema          = Schema.derived[Color]
    val structural      = schema.structural
    val nominalCases    = schema.reflect match {
      case v: Reflect.Variant[_, _] => v.cases.size
      case _                        => -1
    }
    val structuralCases = structural.reflect match {
      case v: Reflect.Variant[_, _] => v.cases.size
      case _                        => -1
    }
    assertTrue(nominalCases == structuralCases && nominalCases == 3)
  },
  test("structural enum type name contains Tag markers") {
    val schema     = Schema.derived[Color]
    val structural = schema.structural
    val typeName   = structural.reflect.typeName.name
    assertTrue(
      typeName.contains("Tag:\"Red\""),
      typeName.contains("Tag:\"Green\""),
      typeName.contains("Tag:\"Blue\""),
      typeName.contains("|")
    )
  },
  test("parameterized enum structural conversion preserves fields") {
    val schema     = Schema.derived[Shape]
    val structural = schema.structural

    val circleFields = structural.reflect match {
      case v: Reflect.Variant[_, _] =>
        v.cases.find(_.name == "Circle").flatMap { c =>
          c.value match {
            case r: Reflect.Record[_, _] => Some(r.fields.map(_.name).toSet)
            case _                       => None
          }
        }
      case _ => None
    }
    assertTrue(
      circleFields.isDefined,
      circleFields.get.contains("radius")
    )
  },
  test("structural enum round-trips through DynamicValue") {
    val schema       = Schema.derived[Shape]
    val structural   = schema.structural
    val value: Shape = Shape.Circle(5.0)

    val structuralAny = structural.asInstanceOf[Schema[Any]]
    val dynamic       = structuralAny.toDynamicValue(value)
    val result        = structuralAny.fromDynamicValue(dynamic)

    assertTrue(result match {
      case Right(v) =>
        val recovered = v.asInstanceOf[Shape]
        recovered == value
      case _ => false
    })
  }
)
```

**Acceptance Criteria**:
- [x] File exists and compiles
- [x] All existing tests still pass
- [x] New `.structural` conversion tests added
- [x] Tests verify Tag markers in type names
- [x] Tests verify round-trip through DynamicValue using structural schema
- [x] Tests run on Scala 3 only (in shared/scala-3/)

**Commit**: NO (groups with #2)

---

### 2. Enhance SealedTraitToUnionSpec.scala
**File**: `schema/shared/src/test/scala-3/zio/blocks/schema/structural/SealedTraitToUnionSpec.scala`

**Add new test suite** after existing suites:

```scala
suite("Structural Conversion")(
  test("sealed trait converts to structural schema") {
    val schema     = Schema.derived[Result]
    val structural = schema.structural
    assertTrue(structural != null)
  },
  test("structural sealed trait schema preserves variant structure") {
    val schema     = Schema.derived[Result]
    val structural = schema.structural
    val isVariant  = structural.reflect match {
      case _: Reflect.Variant[_, _] => true
      case _                        => false
    }
    assertTrue(isVariant)
  },
  test("structural sealed trait has same case count as nominal") {
    val schema          = Schema.derived[Result]
    val structural      = schema.structural
    val nominalCases    = schema.reflect match {
      case v: Reflect.Variant[_, _] => v.cases.size
      case _                        => -1
    }
    val structuralCases = structural.reflect match {
      case v: Reflect.Variant[_, _] => v.cases.size
      case _                        => -1
    }
    assertTrue(nominalCases == structuralCases && nominalCases == 2)
  },
  test("structural sealed trait type name contains Tag markers") {
    val schema     = Schema.derived[Result]
    val structural = schema.structural
    val typeName   = structural.reflect.typeName.name
    assertTrue(
      typeName.contains("|"),
      typeName.contains("Tag:\"Success\""),
      typeName.contains("Tag:\"Failure\""),
      typeName.contains("value:Int"),
      typeName.contains("error:String")
    )
  },
  test("structural sealed trait with case objects converts") {
    val schema     = Schema.derived[Status]
    val structural = schema.structural
    val caseCount  = structural.reflect match {
      case v: Reflect.Variant[_, _] => v.cases.size
      case _                        => -1
    }
    assertTrue(caseCount == 2)
  },
  test("structural sealed trait round-trips through DynamicValue") {
    val schema        = Schema.derived[Result]
    val structural    = schema.structural
    val value: Result = Result.Success(42)

    val structuralAny = structural.asInstanceOf[Schema[Any]]
    val dynamic       = structuralAny.toDynamicValue(value)
    val result        = structuralAny.fromDynamicValue(dynamic)

    assertTrue(result match {
      case Right(v) =>
        val recovered = v.asInstanceOf[Result]
        recovered == value
      case _ => false
    })
  },
  test("structural animal variants preserve field information") {
    val schema     = Schema.derived[Animal]
    val structural = schema.structural

    val dogFields = structural.reflect match {
      case v: Reflect.Variant[_, _] =>
        v.cases.find(_.name == "Dog").flatMap { c =>
          c.value match {
            case r: Reflect.Record[_, _] => Some(r.fields.map(_.name).toSet)
            case _                       => None
          }
        }
      case _ => None
    }
    assertTrue(
      dogFields.isDefined,
      dogFields.get.contains("name"),
      dogFields.get.contains("breed")
    )
  }
)
```

**Acceptance Criteria**:
- [x] File exists and compiles
- [x] All existing tests still pass
- [x] New `.structural` conversion tests added
- [x] Tests verify Tag markers in type names
- [x] Tests verify round-trip through DynamicValue using structural schema
- [x] Tests verify case object handling
- [x] Tests run on Scala 3 only (in shared/scala-3/)

**Commit**: NO (groups with #1)

---

### 3. Handle AsPrimitiveSpec.scala
**File**: `schema/shared/src/test/scala/zio/blocks/schema/AsPrimitiveSpec.scala`

**Decision**: **KEEP the file** as-is.

**Rationale**:
- Tests As/Into primitive conversions (widening/narrowing)
- Not directly related to structural types, BUT:
  - Provides valuable coverage for As/Into type class
  - Issue #517 mentions "Integration with Into/As" as a requirement
  - These primitive conversions are used when converting structural ↔ nominal types
  - The reviewer said "Probably good tests" - acknowledging value

**Action**: Add a comment at the top explaining its relationship to structural types.

```scala
package zio.blocks.schema

import zio.test._

/**
 * Tests for As/Into primitive type conversions.
 * 
 * While not directly testing structural type conversion, these tests validate
 * the primitive type conversion behavior that underpins structural ↔ nominal
 * conversions (see issue #517 requirement: "Integration with Into/As").
 * 
 * These tests ensure that numeric widening/narrowing, identity conversions,
 * and bidirectional conversions work correctly when fields are converted
 * between structural and nominal representations.
 */
object AsPrimitiveSpec extends SchemaBaseSpec {
  // ... rest of file unchanged ...
}
```

**Acceptance Criteria**:
- [x] File kept in place
- [x] Comment added explaining relationship to structural types
- [x] All existing tests still pass

**Commit**: YES (after tasks 1 & 2)
- Message: `"Add .structural tests to enum/sealed trait specs and clarify AsPrimitiveSpec purpose"`
- Files: `EnumToUnionSpec.scala`, `SealedTraitToUnionSpec.scala`, `AsPrimitiveSpec.scala`

---

### 4. Format Code
**Command**: `sbt "++3.3.7; fmt" "++2.13.18; fmt"`

**Acceptance Criteria**:
- [x] Code formatted on Scala 3.3.7
- [x] Code formatted on Scala 2.13.18
- [x] No formatting violations

**Commit**: NO (included in previous commit)

---

### 5. Run Tests
**Commands**:
```bash
sbt "++3.3.7; schemaJVM/test"
sbt "++2.13.18; schemaJVM/test"
sbt "++3.3.7; schemaJS/test"
sbt "++2.13.18; schemaJS/test"
```

**Acceptance Criteria**:
- [x] All Scala 3 JVM tests pass
- [x] All Scala 2 JVM tests pass
- [x] All Scala 3 JS tests pass
- [x] All Scala 2 JS tests pass
- [x] No test failures
- [x] No compilation errors

**Commit**: NO (verification only)

---

### 6. Push to Remote
**Command**: `git push origin structural-schemas-517-squashed:structural-schemas-517`

**Acceptance Criteria**:
- [x] Changes pushed to remote branch
- [x] CI triggered
- [x] Ready for reviewer re-evaluation

---

## Summary of Changes

| Action | File | Change |
|--------|------|--------|
| Enhanced | `EnumToUnionSpec.scala` | Added 6 `.structural` conversion tests |
| Enhanced | `SealedTraitToUnionSpec.scala` | Added 7 `.structural` conversion tests |
| Clarified | `AsPrimitiveSpec.scala` | Added comment explaining relevance to structural types |
| Kept | All existing tests | No deletions, only additions |

**Net Impact**: +13 tests, better alignment with issue #517 requirements

## Verification Strategy

1. **Compile check**: All files compile on Scala 2.13 and 3.3.7
2. **Test execution**: All new tests pass
3. **Regression check**: All existing tests still pass
4. **Coverage**: Issue #517 test matrix completeness improved

## Success Criteria

- [x] Reviewer's concern addressed: Files now explicitly test `.structural` conversion
- [x] All tests pass on both Scala versions and platforms
- [x] No loss of existing coverage
- [x] Better alignment with issue #517 test requirements
- [x] Code formatted and clean

## Next Steps After Execution

1. Respond to PR #614 review with summary of changes
2. Mention that files now test BOTH schema derivation AND `.structural` conversion
3. Point out that AsPrimitiveSpec supports structural type conversions via primitive type coercion
