# 🚀 ZIO Blocks - Into/As Macro Derivation (Issue #518)

**Status:** 🟡 IN PROGRESS (30-40% Complete)  
**Goal:** Complete implementation of `Into` and `As` type classes using Scala 3 Macros (Quotes) following all requirements from issue #518.

## 🏆 Golden Rules Reference (See ANALYSIS_REGOLE_DORO.md)
1. NO Experimental Features.
2. Cross-Platform Mandatory (JVM, JS, Native).
3. Generic Recursion (No hardcoded arity).
4. No Bloat (Touch only necessary files).

---

## 📅 Roadmap & Progress

### Phase 1: Foundation (✅ DONE)
- [x] Clean Start (Repo Reset).
- [x] Scaffolding `Into.scala` & `As.scala` (Interfaces).
- [x] Scaffolding `IntoAsVersionSpecific.scala` (Macro Implementation).
- [x] Build Configuration Check (Scala 3.3.7).
- [x] Add `derived` method to `Into` and `As` (API pubblica).

✅ **Milestone Reached:** Base infrastructure complete, API pubblica disponibile.

---

### Phase 2: Core Logic - Product Types (🟡 PARTIAL)
- [x] Implement `derivedIntoImpl` for Case Classes (Product -> Product).
- [x] Fix Reflection API syntax errors (Scala 3.3).
- [x] Cleanup compilation warnings.
- [x] Verify with Runtime Test (`RedemptionSpec`).
- [x] Implement Field Mappings (Name matching only).
- [ ] ⚠️ **MISSING**: Complete disambiguation algorithm (unique type, position-based).
- [ ] ⚠️ **MISSING**: Tuple support (case class ↔ tuple, tuple ↔ tuple).
- [ ] ⚠️ **MISSING**: Field reordering tests.
- [ ] ⚠️ **MISSING**: Field renaming tests (unique type matching).

✅ **Milestone Reached:** Basic case class conversion works with name matching.  
⚠️ **Limitation**: Only name matching implemented, missing advanced disambiguation.

---

### Phase 3: Advanced Logic - Collections (✅ DONE)
- [x] Implement `List` -> `Vector` / `Seq` conversion (Container conversion).
- [x] Implement `Array` support (via `ArraySeq.unsafeWrapArray` for cross-platform compatibility).
- [x] Runtime helper `sequenceCollectionEither` (supports `IterableOnce` for both `Iterable` and `Iterator`).
- [x] Container conversion (List -> Vector, Seq -> List, Set, etc.).
- [x] Element-wise conversion with primitives (✅ RESOLVED: Hardcoded Primitives pattern).
- [x] Recursion check with primitive fields (✅ VERIFIED: All test cases pass).

✅ **Milestone Reached:** Collections support fully functional with primitive conversions, container conversions, and complex recursion. All 15 test cases pass.

---

### Phase 4: Core Logic - Coproduct Types (Enums) (🟡 PARTIAL)
- [x] Implement `derivedIntoImpl` for Sealed Traits / Enums.
- [x] Handle Subtype matching (exact name match only).
- [x] Support singleton cases (enum values, case objects) via PRIORITY 0.5 in `findOrDeriveInto`.
- [x] Allow partial subtype matching (unmatched subtypes hit catch-all case at runtime).
- [x] Fix `buildSubtypeConstruction` for case objects in wrapper objects (using `companionModule`).
- [x] Complete test suite (`IntoCoproductSpec` - 12 test cases).
- [ ] ⚠️ **MISSING**: Structural matching for subtypes with different names (PRIORITY 2 TODO).

✅ **Milestone Reached:** Coproducts support functional for exact name matches. All 12 test cases pass.  
⚠️ **Limitation**: Only exact name matching works; structural matching (different names) is documented as future enhancement.

---

### Phase 5: The `As` Type Class (Bidirectional) (🟡 PARTIAL)
- [x] Implement `derivedAsImpl` using composition approach (Into[A, B] + Into[B, A]).
- [x] Verify bidirectional round-trip conversions (basic cases).
- [x] Create test suite (`AsProductSpec` - 4 test cases).
- [ ] ⚠️ **MISSING**: Round-trip tests for tuples.
- [ ] ⚠️ **MISSING**: Round-trip tests for collections.
- [ ] ⚠️ **MISSING**: Round-trip tests for opaque types.
- [ ] ⚠️ **MISSING**: Round-trip tests for numeric narrowing.
- [ ] ⚠️ **MISSING**: Default values detection (compile error for As).

✅ **Milestone Reached:** `As[A, B]` functional using composition approach. Basic round-trip tests pass.  
⚠️ **Limitation**: Only basic round-trip tested, missing comprehensive test matrix.

---

### Phase 6: Final Verification (🟡 PARTIAL)
- [x] Cross-Platform Check (JVM, JS).
  - ✅ JVM: All 31 tests pass (IntoCoproductSpec: 12, AsProductSpec: 4, IntoCollectionSpec: 15)
  - ✅ JS: All 12 Into/As tests pass (zero runtime reflection issues)
  - ⚠️ Native: Not testable without LLVM/clang toolchain, but code uses only compile-time reflection (`scala.quoted.*`) - should work
- [x] Full Regression Suite: All existing Into/As tests pass on both JVM and JS.
- [ ] ⚠️ **MISSING**: Complete test matrix (only ~10% implemented).

✅ **Milestone Reached:** Cross-platform compatibility verified for existing tests. Zero runtime reflection confirmed.  
⚠️ **Limitation**: Test coverage is only ~10% of the required test matrix from issue #518.

---

## 🚨 CRITICAL MISSING FEATURES

### Phase 7: Complete Disambiguation Algorithm (❌ NOT STARTED)
**Priority:** 🔴 CRITICAL  
**Estimated Time:** 2-3 days

**Requirements:**
- [ ] PRIORITY 1: Exact match (same name + same type) - ✅ Partially (only name)
- [ ] PRIORITY 2: Name match with coercion (same name + coercible type)
- [ ] PRIORITY 3: Unique type match (type appears only once in both)
- [ ] PRIORITY 4: Position + unique type (positional correspondence with unambiguous type)
- [ ] PRIORITY 5: Fallback with clear compile error if ambiguous

**Current Implementation:**
- Only name matching: `aFields.find(_.name == bField.name)` (line 543)

**Examples that DON'T work:**
```scala
// Field renaming (should work with unique type match)
case class V1(name: String, age: Int)
case class V2(fullName: String, yearsOld: Int)
// Currently fails, should work: String→String (unique), Int→Int (unique)
```

---

### Phase 8: Tuple Support (❌ NOT STARTED)
**Priority:** 🔴 CRITICAL  
**Estimated Time:** 1-2 days

**Requirements:**
- [ ] Case class ↔ Tuple conversion
- [ ] Tuple ↔ Tuple conversion
- [ ] Position-based mapping for tuples

**Examples required:**
```scala
case class RGB(r: Int, g: Int, b: Int)
type ColorTuple = (Int, Int, Int)

Into[RGB, ColorTuple].into(RGB(255, 128, 0)) // => Right((255, 128, 0))
Into[ColorTuple, RGB].into((255, 128, 0))    // => Right(RGB(255, 128, 0))
```

---

### Phase 9: Opaque Types Validation (❌ NOT STARTED)
**Priority:** 🟡 IMPORTANT  
**Estimated Time:** 2-3 days

**Requirements:**
- [ ] Detect companion with `apply(underlying): Either[_, OpaqueType]`
- [ ] Generate validation calls
- [ ] Error accumulation for multiple validation failures

**Examples required:**
```scala
opaque type Age = Int
object Age {
  def apply(value: Int): Either[String, Age] = 
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")
}

case class Raw(age: Int)
case class Validated(age: Age)

Into[Raw, Validated].into(Raw(30))  // => Right(Validated(Age(30)))
Into[Raw, Validated].into(Raw(-5))  // => Left(SchemaError("Invalid age: -5"))
```

**Current Status:** Comment present in code, implementation missing.

---

### Phase 10: Complete Test Matrix (❌ NOT STARTED)
**Priority:** 🟡 IMPORTANT  
**Estimated Time:** 3-4 days

**Current Status:** 31 test cases (~10% of required test matrix)

**Missing Test Categories:**
- [ ] `products/`: Tuple conversions, Field reordering, Field renaming, Nested products
- [ ] `coproducts/`: Advanced matching, Nested coproducts, Ambiguous cases
- [ ] `primitives/`: Complete narrowing validation, Option/Either coercion
- [ ] `validation/`: Opaque types, Error accumulation, Nested validation
- [ ] `evolution/`: Optional fields, Type refinement, Default values
- [ ] `disambiguation/`: All disambiguation scenarios, Ambiguous compile errors
- [ ] `edge/`: Empty products, Large products, Recursive types, Mutually recursive
- [ ] `as/reversibility/`: Complete round-trip test matrix

**Required:** ~300+ test cases organized in the structure proposed in issue #518.

---

### Phase 11: Schema Evolution Patterns (❌ NOT STARTED)
**Priority:** 🟡 IMPORTANT  
**Estimated Time:** 1-2 days

**Requirements:**
- [ ] Add optional fields support
- [ ] Remove optional fields support
- [ ] Type refinement support
- [ ] Default values detection (for As: compile error)

**Examples required:**
```scala
// Add optional field
case class V1(name: String)
case class V2(name: String, age: Option[Int])
// Should work: age can be None

// Remove optional field
case class V1(name: String, age: Option[Int])
case class V2(name: String)
// Should work: age can be dropped

// Default value (should fail for As)
case class V1(name: String)
case class V2(name: String, active: Boolean = true)
// Should compile error for As (default value breaks round-trip)
```

---

## 📊 Implementation Status Summary

### ✅ Completed (30-40%)
- Core infrastructure (Into/As traits, API pubblica)
- Collections support (complete)
- Basic product types (case class with name matching)
- Basic coproducts (exact name match)
- Primitives (widening/narrowing with validation)
- Basic As (bidirectional via composition)
- Basic test suite (31 test cases)

### ⚠️ Partial (10-20%)
- Product types (only name matching, missing advanced disambiguation)
- Coproducts (only exact name match, missing structural matching)
- As round-trip (basic cases only, missing comprehensive tests)

### ❌ Missing (40-50%)
- Complete disambiguation algorithm
- Tuple support
- Opaque types validation
- Complete test matrix (~90% missing)
- Schema evolution patterns
- Structural types (documented as limitation)

---

## ⏱️ Time Estimates

### Remaining Work
- **Phase 7** (Disambiguation): 2-3 days
- **Phase 8** (Tuple): 1-2 days
- **Phase 9** (Opaque Types): 2-3 days
- **Phase 10** (Test Matrix): 3-4 days
- **Phase 11** (Evolution): 1-2 days

**Total Remaining:** 9-14 days of full-time work

### Total Project Time
- **Completed:** ~6-8 days
- **Remaining:** 9-14 days
- **Total Estimated:** 15-22 days of full-time work

**Note:** With part-time work or interruptions, this could take 4-6 weeks.

---

## 🎯 Next Steps (Priority Order)

1. **Phase 7** - Implement complete disambiguation algorithm (CRITICAL)
2. **Phase 8** - Add tuple support (CRITICAL)
3. **Phase 9** - Implement opaque types validation (IMPORTANT)
4. **Phase 10** - Complete test matrix (IMPORTANT)
5. **Phase 11** - Schema evolution patterns (IMPORTANT)

---

## 📝 Notes

- All implemented code follows Golden Rules (no experimental features, cross-platform, generic recursion)
- Current implementation is solid foundation but incomplete
- Main gaps: disambiguation algorithm, tuple support, test coverage
- See `ANALYSIS_REGOLE_DORO.md` for detailed requirements and action plan
