# 🐛 Known Issues - Into/As Macro Derivation

**Last Updated:** Phase 5 Completion  
**Status:** Active Issues Tracking

---

## ✅ RESOLVED: Primitive Instance Resolution

**Priority:** HIGH  
**Phase:** Phase 3 (Collections)  
**Status:** ✅ RESOLVED

### Description
`Implicits.search` in macro context fails to find primitive `Into` instances (`intToLong`, `longToInt`, `intToDouble`, etc.) defined in `object Into` as `implicit val`.

### Impact (Historical)
- ~~Cannot derive `Into[List[Int], List[Long]]`~~ ✅ Fixed
- ~~Cannot derive `Into[Vector[Int], List[Long]]`~~ ✅ Fixed
- ~~Cannot derive recursive conversions with primitive fields~~ ✅ Fixed
- ~~Blocks Phase 3 completion~~ ✅ Resolved

### Error Message
```
Cannot derive Into[scala.Int, scala.Long]: No implicit instance found and types are not both collections or case classes.
```

### Root Cause
When `TypeRepr` is constructed dynamically using `TypeRepr.of[Into[?, ?]].appliedTo(List(aTpe, bTpe))`, the `Implicits.search` API doesn't have access to companion object instances. The macro context's implicit resolution scope doesn't include the companion object `Into` where the primitive instances are defined.

**Technical Details:**
- Primitive instances are defined as `implicit val intToLong: Into[Int, Long]` in `object Into`
- `Implicits.search(intoType)` is called with a dynamically constructed `TypeRepr`
- Scala 3's macro implicit resolution doesn't search companion objects when the type is constructed at macro expansion time
- This is a known limitation of `Implicits.search` in Scala 3 macros

### Solution Implemented
✅ **RESOLVED** by implementing "Hardcoded Primitives" as PRIORITY 0 in `findOrDeriveInto`:

**Implementation:**
- Added `isPrimitiveType` helper using `dealias` + `=:=` for robust type matching (handles type aliases)
- Added `generatePrimitiveInto` helper to wrap conversion functions in `Into` instances
- Pattern matching on primitive type pairs using `dealias` + `=:=` before `Implicits.search`
- All widening conversions (Int->Long, Int->Double, etc.) and narrowing conversions (Long->Int, Double->Long, etc.) implemented
- Uses Quotes (`'{ ... }`) to generate conversion logic, reusing validation from `Into.scala`

**Result:**
- ✅ Zero runtime overhead (compile-time only)
- ✅ No implicit search needed for primitives
- ✅ Performance optimal (direct code generation)
- ✅ Conforms to Golden Rules (not hardcoding arity, but handling well-defined primitive set)
- ✅ Cross-platform compatible (no runtime reflection)

### References
- `ARCHITECTURE_DECISIONS.md` - Detailed analysis and rationale
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:257` - `findOrDeriveInto` method
- `schema/shared/src/main/scala/zio/blocks/schema/Into.scala:41-106` - Primitive instances definition

---

## ✅ RESOLVED: Lambda Construction for Collections

**Priority:** MEDIUM  
**Phase:** Phase 3 (Collections)  
**Status:** ✅ RESOLVED (via Refactoring)

### Description (Historical)
Previously, collection conversions failed with `AssertionError: Expected fun.tpe to widen into a MethodType` when constructing `.map` lambda via AST for complex terms.

### Solution Implemented
✅ **RESOLVED** by refactoring to use runtime helper `mapAndSequence`:

**Implementation:**
- Added `mapAndSequence[A, B](source: Iterable[A], f: A => Either[SchemaError, B])` runtime helper
- Eliminated fragile AST construction of `.map` on complex terms (e.g., `ArraySeq.unsafeWrapArray(...)`)
- Macro now constructs only the element conversion lambda (simpler, more reliable)
- Helper handles both map and sequence operations in a single runtime call
- Cross-platform compatible (uses standard `Iterable` interface)

**Result:**
- ✅ No more `AssertionError` on complex terms
- ✅ Simpler macro code (no manual `.map` AST construction)
- ✅ Consistent with existing pattern (`sequenceEither`, `sequenceCollectionEither`)
- ✅ Works for all collection types (Array, List, Vector, etc.)

### References
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:70-95` - `mapAndSequence` helper
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:753-759` - Usage in `deriveCollectionInto`
- `ARCHITECTURE_DECISIONS.md` - Runtime helper pattern rationale

---

## ✅ RESOLVED: Type Mismatch in Runtime Helper

**Priority:** HIGH  
**Phase:** Phase 3 (Collections)  
**Status:** ✅ RESOLVED

### Description (Historical)
After refactoring to use `mapAndSequence` runtime helper, the macro generated 10 compilation errors: `Found: Either[SchemaError, X], Required: Either[SchemaError, ?][X]`.

### Impact (Historical)
- ~~All collection conversions fail at compile-time~~ ✅ Fixed
- ~~Blocks Phase 3 completion~~ ✅ Resolved
- ~~Affects both primitive element conversions and container conversions~~ ✅ Fixed

### Error Message (Historical)
```
[E007] Type Mismatch Error:
Found:    Either[zio.blocks.schema.SchemaError, Long]
Required: Either[zio.blocks.schema.SchemaError, ?][Long]
```

### Root Cause
The return type of the Lambda constructed dynamically (`TypeRepr`) did not unify correctly with the generic type parameter `B` expected by the static helper `mapAndSequence[A, B]`.

**Technical Details:**
- `mapAndSequence` has static type parameters: `def mapAndSequence[A, B](source: Iterable[A], f: A => Either[SchemaError, B])`
- The macro was constructing the lambda using dynamic `TypeRepr` (`aElem`, `bElem`)
- When constructing `MethodType` for the lambda, we used `TypeRepr.of[Either].appliedTo(List(TypeRepr.of[SchemaError], bElem))`
- The compiler could not infer that the dynamically constructed `Either[SchemaError, bElem]` type corresponds to the static `Either[SchemaError, B]` type parameter
- This created a type mismatch: the lambda's return type was seen as `Either[SchemaError, ?][B]` (partially applied) instead of `Either[SchemaError, B]` (fully applied)

### Solution Implemented
✅ **RESOLVED** by using the `.asType` pattern to convert dynamic `TypeRepr` into static type variables before generating the helper call:

**Implementation:**
- Used `aElem.asType match { case '[ae] => bElem.asType match { case '[be] => ... } }` to extract static types
- Converted `Term` to `Expr` using `.asExprOf[Iterable[ae]]` and `.asExprOf[Into[ae, be]]`
- Built element conversion lambda using Quotes: `'{ (elem: ae) => $elemIntoExpr.into(elem) }`
- Called `mapAndSequence` using typed Quotes: `'{ IntoAsVersionSpecificImpl.mapAndSequence[ae, be](...) }`
- Used `companion.from` pattern for building target collections (e.g., `Vector.from`, `Set.from`)

**Result:**
- ✅ Type inference works correctly (static types)
- ✅ No type mismatch (compiler can unify types)
- ✅ Simpler code (Quotes instead of complex AST)
- ✅ Consistent with existing patterns (case class derivation uses Quotes)
- ✅ All 15 test cases pass

### References
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:692-783` - Final implementation
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:70-95` - `mapAndSequence` helper definition
- `ARCHITECTURE_DECISIONS.md` - Quotes vs AST trade-offs and Collection Support section

---

---

## ✅ RESOLVED: Coproduct Derivation for Singleton Cases

**Priority:** HIGH  
**Phase:** Phase 4 (Coproducts)  
**Status:** ✅ RESOLVED

### Description (Historical)
When deriving `Into[A, B]` for coproduct types (enums, sealed traits), the recursive call to `findOrDeriveInto` for singleton cases (enum values, case objects) failed because these types were not recognized as collections or case classes.

### Impact (Historical)
- ~~Cannot derive `Into[Status, State]` for Scala 3 enums (e.g., `Status.Active -> State.Active`)~~ ✅ Fixed
- ~~Cannot derive `Into[Color, Hue]` for sealed traits with case objects~~ ✅ Fixed
- ~~Blocks Phase 4 completion (Coproducts support)~~ ✅ Resolved
- ~~Affects all singleton case conversions (enum values, case objects)~~ ✅ Fixed

### Error Message (Historical)
```
Cannot derive Into[into.IntoCoproductSpec.Status.Active, into.IntoCoproductSpec.State.Active]: 
No implicit instance found and types are not both collections or case classes.
```

### Root Cause (Historical)
**Problem 1: Recursive Derivation Failure**
When `deriveCoproductInto` found a matching subtype pair (e.g., `Status.Active` and `State.Active`), it called `findOrDeriveInto(using q)(aSubtype, bSubtype)` recursively. However, `findOrDeriveInto` only handled:
- Identity case (same type)
- Primitive conversions (hardcoded)
- Collections (via `extractCollectionElementType`)
- Case classes (via `isCaseClass`)

**Singleton cases** (enum values, case objects) were not handled, so the recursive call failed.

**Problem 2: Type Representation**
For enum values, `directSubTypes` returns `TypeRepr` that represent the singleton type (e.g., `Status.Active.type`). When trying to derive `Into[Status.Active.type, State.Active.type]`, these were not recognized as any supported category.

### Solution Implemented
✅ **RESOLVED** by adding singleton case handling as PRIORITY 0.5 in `findOrDeriveInto`:

**Implementation:**
- Added `isCaseObjectOrEnumVal` helper to detect singleton types (enum values, case objects)
- Added `getSubtypeName` helper to extract names from both `termSymbol` (singletons) and `typeSymbol` (case classes)
- Added `generateSingletonInto` helper to create `Into` instances for singleton conversions
- Added PRIORITY 0.5 check in `findOrDeriveInto` (after primitives, before collections)
- Singleton-to-singleton conversions now work: `(_: A) => Right(bValue)` where `bValue` is constructed via `buildSubtypeConstruction`
- Name matching ensures only compatible singletons are converted

**Result:**
- ✅ Enum values (e.g., `Status.Active -> State.Active`) work correctly
- ✅ Case objects (e.g., `Color.Red -> Hue.Red`) work correctly
- ✅ Recursive derivation in `deriveCoproductInto` now handles singletons
- ✅ Cross-platform compatible (compile-time only)
- ✅ No runtime overhead (direct code generation)

### References
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:223-234` - `isCaseObjectOrEnumVal` helper
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:535-542` - `getSubtypeName` helper
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:548-554` - `generateSingletonInto` helper
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:664-679` - PRIORITY 0.5 singleton handling in `findOrDeriveInto`
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:289-386` - `deriveCoproductInto` implementation
- `schema/shared/src/test/scala/zio/blocks/schema/into/IntoCoproductSpec.scala` - Test cases (simple enums work)

---

## 🟡 KNOWN LIMITATION: Coproduct Subtype Name Matching

**Priority:** MEDIUM  
**Phase:** Phase 4 (Coproducts)  
**Status:** 🟡 KNOWN LIMITATION

### Description
The `findMatchingSubtype` function in `deriveCoproductInto` requires **exact name matches** between source and target subtypes. This means that subtypes with different names (e.g., `RedColor` vs `RedHue`, `EventCreated` vs `ActionCreated`) cannot be matched, even if they represent semantically equivalent concepts.

### Impact
- ⚠️ Cannot derive `Into[Color, Hue]` when subtypes have different names (`RedColor` vs `RedHue`)
- ⚠️ Cannot derive `Into[Event, Action]` when subtypes have different names (`EventCreated` vs `ActionCreated`)
- ⚠️ Limits flexibility in schema evolution scenarios where subtypes are renamed
- ⚠️ Requires manual renaming or wrapper types to work around the limitation

### Current Behavior
```scala
sealed trait Color
case object RedColor extends Color
case object BlueColor extends Color

sealed trait Hue
case object RedHue extends Hue
case object BlueHue extends Hue

// This fails at compile-time:
val derivation = Into.derived[Color, Hue]  // Error: Missing subtype mapping
```

### Root Cause
The `findMatchingSubtype` function uses exact string comparison:
```scala
val aName = getSubtypeName(using q)(aSubtype)  // "RedColor"
val bName = getSubtypeName(using q)(bSubtype)  // "RedHue"
if (aName == bName) { ... }  // false - no match
```

**Technical Details:**
- `findMatchingSubtype` is called for each source subtype to find a matching target subtype
- It uses `getSubtypeName` which extracts the name from `termSymbol` (singletons) or `typeSymbol` (case classes)
- Only exact matches are considered (PRIORITY 1: Exact name match)
- PRIORITY 2 (Structural match) is marked as TODO and not implemented

### Proposed Solution (Future Enhancement)
Implement smarter matching logic in `findMatchingSubtype`:

**Option 1: Prefix/Suffix Matching**
```scala
// Extract common parts: "Red" from "RedColor" and "RedHue"
val aBase = extractBaseName(aName)  // "Red"
val bBase = extractBaseName(bName)  // "Red"
if (aBase == bBase) { ... }
```

**Option 2: Structural Matching (PRIORITY 2)**
- Compare field names and types for case classes
- Compare parent type for singletons
- Use semantic similarity heuristics

**Option 3: Annotation-Based Matching**
```scala
@AsMapping("RedHue")
case object RedColor extends Color
```

**Benefits:**
- ✅ More flexible schema evolution
- ✅ Handles common renaming patterns
- ✅ Maintains type safety (compile-time only)

### Workaround
For now, users can:
1. Rename subtypes to match exactly
2. Use wrapper types with matching names
3. Manually implement `Into` instances for non-matching cases

### References
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:243-275` - `findMatchingSubtype` implementation
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:277` - PRIORITY 2 TODO comment
- `schema/shared/src/test/scala/zio/blocks/schema/into/IntoCoproductSpec.scala` - Test cases demonstrating the limitation

---

## ✅ Status Summary

**Active Issues:**
- 🟡 Coproduct Subtype Name Matching (Phase 4) - Known limitation, not blocking

**Resolved Issues:**
- ✅ Primitive Instance Resolution (Hardcoded Primitives pattern)
- ✅ Lambda Construction for Collections (Runtime helper `mapAndSequence`)
- ✅ Type Mismatch in Runtime Helper (Quotes with `.asType` pattern)
- ✅ Coproduct Derivation for Singleton Cases (PRIORITY 0.5 in `findOrDeriveInto`)

**Phase Status:**
- Phase 3 (Collections) is **complete** with all 15 test cases passing.
- Phase 4 (Coproducts) is **mostly complete** - basic functionality works (enums with matching names, sealed traits with matching names). Name matching limitation is documented.
- Phase 5 (As) is **complete** with all 4 test cases passing.

---

## 📝 Notes

- All issues are tracked here for transparency
- Issues are prioritized by impact on project completion
- Solutions are implemented and verified with test suite
- Cross-platform compatibility (JVM, JS, Native) must be maintained for all solutions

---

**Contributors:** AI Assistant + User  
**References:** 
- `PROGRESS_TRACKER.md` - Current progress
- `ARCHITECTURE_DECISIONS.md` - Technical decisions
- `ANALYSIS_REGOLE_DORO.md` - Golden Rules

