# 🧪 Grand Regression Test Report

**Date:** Dec 25, 2025  
**Status:** ⚠️ **29 Test Failures Detected** (218 passed, 29 failed)

---

## 📊 Test Summary

- ✅ **218 tests passed**
- ❌ **29 tests failed**
- 📁 **Total:** 247 tests executed

---

## 🔍 Failure Categories

### 1. **Error Message Text Mismatches** (14 failures)
**Issue:** Tests expect specific error message text, but actual messages use slightly different wording.

**Examples:**
- Expected: `"cannot be safely converted to Int"`
- Actual: `"Long value 2147483648 out of Int range"`

**Affected Tests:**
- `NumericNarrowingSpec`: 7 failures (Long→Int, Double→Float, Double→Long)
- `EitherCoercionSpec`: 5 failures (Long→Int, Double→Float)
- `OptionCoercionSpec`: 1 failure (Float→Double precision)
- `EitherCoercionSpec`: 1 failure (Float→Double precision)

**Root Cause:** Error messages are functionally correct but don't match exact test expectations.

**Recommendation:** Update test assertions to match actual error messages OR update error messages to match test expectations.

---

### 2. **Missing Compile-Time Error Detection** (12 failures)
**Issue:** Tests expect compile-time errors (using `typeCheck`), but code compiles successfully.

**Affected Tests:**
- `AmbiguousCompileErrorSpec`: 7 failures
  - Should fail when ambiguous field mappings exist
  - Should fail when source has more fields than target
  - Should fail when name matches but types incompatible
  - Should fail when unique type match is ambiguous
- `NameDisambiguationSpec`: 1 failure
  - Should fail when multiple fields have same name
- `UniqueTypeDisambiguationSpec`: 1 failure
  - Should fail when unique type match is ambiguous
- `RemoveOptionalFieldSpec`: 1 failure
  - Should fail when trying to remove non-optional field
- `PositionDisambiguationSpec`: 3 failures
  - Should fail when positional match exists but types not unique

**Root Cause:** The macro doesn't detect all ambiguous/invalid cases at compile-time. It allows compilation and may fail at runtime or silently succeed.

**Recommendation:** Enhance compile-time validation in `findMatchingField` and `generateIntoInstance` to detect:
- Ambiguous mappings (multiple candidates)
- Unmapped source fields (when source has more fields than target)
- Incompatible types even when names match

---

### 3. **Float Precision Issues** (2 failures)
**Issue:** Float→Double conversions have precision differences (expected behavior, not a bug).

**Affected Tests:**
- `EitherCoercionSpec`: 1 failure (Float→Double in Either)
- `OptionCoercionSpec`: 1 failure (Float→Double in Option)

**Root Cause:** Float has ~7 decimal digits precision, Double has ~15. Converting `3.14f` to Double results in `3.140000104904175` (normal floating-point behavior).

**Recommendation:** Update tests to use approximate equality checks for Float→Double conversions.

---

### 4. **Runtime Type Cast Exception** (1 failure)
**Issue:** `ClassCastException` when swapping Either types.

**Affected Test:**
- `EitherCoercionSpec`: "should swap types: Either[String, Int] -> Either[String, Long] with Left unchanged"

**Root Cause:** The macro generates incorrect code for Either type swaps when only one side needs coercion.

**Recommendation:** Fix Either derivation logic to handle type swaps correctly.

---

## 🎯 Priority Fixes

### 🔴 **CRITICAL** (Blocks functionality)
1. **Missing Compile-Time Validation** (12 failures)
   - Add validation for ambiguous mappings
   - Add validation for unmapped source fields
   - Add validation for incompatible types

### 🟡 **HIGH** (Affects user experience)
2. **Error Message Consistency** (14 failures)
   - Standardize error message format
   - Update tests OR update messages

### 🟢 **MEDIUM** (Test quality)
3. **Float Precision Tests** (2 failures)
   - Use approximate equality for Float→Double

### 🟢 **MEDIUM** (Edge case)
4. **Either Type Swap** (1 failure)
   - Fix Either derivation for type swaps

---

## 📝 Detailed Failure List

### NumericNarrowingSpec (7 failures)
1. ❌ `should fail when Long exceeds Int.MaxValue` - Error message mismatch
2. ❌ `should fail when Long is below Int.MinValue` - Error message mismatch
3. ❌ `should fail when Double is too large for Float` - Error message mismatch
4. ❌ `should fail when Double to Long conversion is out of range` - Should fail but succeeds
5. ❌ `should fail when Vector[Double] contains value too large for Float` - Error message mismatch
6. ❌ `should fail when List[Long] contains value exceeding Int.MaxValue` - Error message mismatch
7. ❌ `should fail when List[Long] contains value below Int.MinValue` - Error message mismatch

### AmbiguousCompileErrorSpec (7 failures)
1. ❌ `should fail compilation for ambiguous Int fields` - Compiles successfully
2. ❌ `should fail compilation for multiple ambiguous types` - Compiles successfully
3. ❌ `should fail when name matches but types incompatible` - Compiles successfully
4. ❌ `should fail when unique type match is ambiguous` - Compiles successfully
5. ❌ `should fail when position match exists but types not unique` - Compiles successfully
6. ❌ `should fail when source has more fields than target` - Compiles successfully
7. ❌ `should provide helpful error message with available fields` - Error message format mismatch

### EitherCoercionSpec (6 failures)
1. ❌ `should coerce Left(Float) to Left(Double)` - Float precision
2. ❌ `should coerce Right(Float) to Right(Double)` - Float precision
3. ❌ `should fail when Right(Long) exceeds Int.MaxValue` - Error message mismatch
4. ❌ `should fail when Right(Long) is below Int.MinValue` - Error message mismatch
5. ❌ `should fail when Left(Long) exceeds Int.MaxValue` - Error message mismatch
6. ❌ `should fail when Left(Long) is below Int.MinValue` - Error message mismatch
7. ❌ `should fail when Right(Double) is too large for Float` - Error message mismatch
8. ❌ `should swap types: Either[String, Int] -> Either[String, Long] with Left unchanged` - ClassCastException

### NameDisambiguationSpec (1 failure)
1. ❌ `should handle multiple fields with same name (should fail)` - Compiles successfully

### UniqueTypeDisambiguationSpec (1 failure)
1. ❌ `should fail when unique type match is ambiguous` - Compiles successfully

### OptionCoercionSpec (1 failure)
1. ❌ `should coerce Some(Float) to Some(Double)` - Float precision

### RemoveOptionalFieldSpec (1 failure)
1. ❌ `should fail when trying to remove non-optional field` - Compiles successfully

### PositionDisambiguationSpec (3 failures)
1. ❌ `should fail when positional match exists but types not unique` - Compiles successfully
2. ❌ `should fail when position match but one type is ambiguous` - Compiles successfully
3. ❌ `should work when position match with one unique and one ambiguous` - Compiles successfully (should fail)

---

## ✅ What's Working

- ✅ **218 tests passing** - Core functionality is solid
- ✅ Collections conversions
- ✅ Coproducts (exact name matching)
- ✅ Tuples (all conversions)
- ✅ Opaque types validation
- ✅ Basic disambiguation (Priority 1-4 when unambiguous)
- ✅ Field reordering and renaming (when unambiguous)
- ✅ Round-trip conversions (As)

---

## 🔧 Next Steps

1. **Fix Compile-Time Validation** (Priority 1)
   - Add checks in `findMatchingField` for ambiguous cases
   - Add validation for unmapped source fields
   - Generate compile-time errors using `report.error`

2. **Standardize Error Messages** (Priority 2)
   - Create consistent error message format
   - Update all error generation points
   - Update tests to match new format

3. **Fix Either Type Swap** (Priority 3)
   - Debug Either derivation for type swaps
   - Add test case for type swap scenarios

4. **Update Float Precision Tests** (Priority 4)
   - Use approximate equality for Float→Double
   - Document expected precision behavior

---

## 📌 Notes

- **No Reverse Opaque Derivation limitation** was not found in `KNOWN_ISSUES.md` (already removed or never added)
- Most failures are **test assertion issues** rather than functional bugs
- Core functionality is **working correctly** for unambiguous cases
- The main gap is **compile-time validation** for ambiguous/invalid cases

