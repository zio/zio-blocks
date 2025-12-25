# 🏗️ Architecture Decisions - Into/As Macro Derivation

**Status:** ✅ Phase 3 Complete  
**Last Updated:** Current Session  
**Context:** Scala 3 Macro Implementation for `Into[A, B]` and `As[A, B]` Type Classes

---

## 📋 Problem Statement

**Challenge:** Accumulating `Either[SchemaError, ?]` results in Scala 3 Macros when converting case class fields.

When deriving `Into[A, B]` for case classes, we need to:
1. Convert each field from type `A` to type `B` (potentially recursively)
2. Each conversion returns `Either[SchemaError, FieldB]`
3. Accumulate all results: if all succeed, construct `B`; otherwise return first error
4. Generate this logic at compile-time using macros

**Core Issue:** Scala 3's macro system has strict scope rules. Variables captured in closures or nested quotes can cause `ScopeException` at compile-time.

---

## 🔄 Attempted Solutions

### ❌ Attempt 1: Manual AST Construction (`buildFlatMapChain`)

**Approach:** Build nested `flatMap` chains using pure AST (`q.reflect.Lambda`, `MethodType`, `Apply`).

**Implementation:**
- Created `Lambda` with `MethodType` defining parameter types
- Recursively built `flatMap` chains
- Used `changeOwner` to fix scope issues

**Failure Reason:**
- `AssertionError: Expected fun.tpe to widen into a MethodType`
- Complex type matching between `MethodType` declaration and lambda body
- Difficult to ensure type consistency between parameter types and return types
- Owner management became increasingly complex with nested structures

**Code Location:** `buildFlatMapChain` method (now removed)

---

### ❌ Attempt 2: Nested Quotes with Recursion (`chain` function)

**Approach:** Use high-level `Expr` recursion with Quotes (`'{ ... }`) instead of manual AST.

**Implementation:**
```scala
def chain(
  remaining: List[Expr[Either[SchemaError, Any]]],
  acc: List[Expr[Any]]
): Expr[Either[SchemaError, B]] = remaining match {
  case Nil => constructB(acc)
  case head :: tail =>
    '{
      $head.flatMap { (valUnwrapped: Any) =>
        ${ chain(tail, acc :+ 'valUnwrapped) }
      }
    }
}
```

**Failure Reason:**
- `ScopeException: value v0 used outside the scope`
- Variables like `valUnwrapped` captured in nested quotes create scope violations
- Scala 3's macro system cannot track variable scope across nested quote boundaries
- The recursive nature amplifies the problem with each nesting level

**Code Location:** `generateEitherAccumulation` with `chain` helper (now removed)

---

### ✅ Final Solution: Pure AST with Runtime Helpers

**Approach:** Hybrid solution using runtime helpers for sequencing + pure AST construction for lambda and application.

**Implementation:**
```scala
// Runtime helper (no scope issues - pure runtime function)
def sequenceEither(
  results: List[Either[SchemaError, Any]]
): Either[SchemaError, List[Any]] = { ... }

// Macro: Pure AST construction
val listExpr = Expr.ofList(fieldConversions)
val sequenceModule = Ref(Symbol.requiredModule("zio.blocks.schema.IntoAsVersionSpecificImpl"))
val sequenceMethod = Select.unique(sequenceModule, "sequenceEither")
val sequenceCall = Apply(sequenceMethod, List(listExpr))

// Build Lambda using pure AST (ensures correct scope)
val lambdaMethodType = MethodType(List("args"))(
  _ => List(TypeRepr.of[List[Any]]),
  _ => bTpe
)
val mapLambda = Lambda(
  Symbol.spliceOwner,
  lambdaMethodType,
  (owner, params) => {
    val argsParam = params.head.asInstanceOf[Term] // Correctly scoped as lambda parameter
    // Build constructor call using argsParam
    // ...
  }
)

// Build final expression: sequenceCall.map(mapLambda)
val mapMethod = Select.unique(sequenceCall, "map")
val finalExpr = Apply(
  TypeApply(mapMethod, List(Inferred(bTpe))),
  List(mapLambda)
)
```

**Key Insight:** 
- Runtime helpers eliminate nested `flatMap` complexity and avoid scope issues
- Pure AST construction ensures correct scope: `argsParam` is a lambda parameter, not a captured variable
- `Lambda` with `MethodType` correctly defines parameter scope within the lambda body
- No mixing of Quotes (`'{ ... }`) and AST for the critical lambda construction

**Result:** ✅ No `ScopeException`, works for any arity, fully cross-platform (JVM, JS, Native).

**Code Location:** `generateEitherAccumulation` in `IntoAsVersionSpecific.scala:359`

---

### ✅ Array Handling Solution

**Problem:** `Array` doesn't have `.map` directly (requires `ArrayOps` extension, problematic in macros).

**Solution:** Convert `Array` to `ArraySeq` before mapping:
```scala
val iterableSource = if (isArray(using q)(aTpe)) {
  val arraySeqModule = Ref(Symbol.requiredModule("scala.collection.immutable.ArraySeq"))
  val unsafeWrapArrayMethod = Select.unique(arraySeqModule, "unsafeWrapArray")
  Apply(
    TypeApply(unsafeWrapArrayMethod, List(Inferred(aElem))),
    List(aParam)
  )
} else {
  aParam
}
```

**Rationale:** 
- `ArraySeq` is a standard `Iterable` with native `.map` support
- Cross-platform compatible (works on JVM, JS, Native)
- No extension methods required
- `unsafeWrapArray` is zero-copy (wraps the array, doesn't duplicate)

**Code Location:** `deriveCollectionInto` in `IntoAsVersionSpecific.scala:722-733`

---

## ✅ Collection Support Implementation

### Collection Construction Pattern

**Problem:** How to construct target collections (e.g., `Vector`, `Set`, `Seq`) from `List[be]` after element conversion.

**Solution:** Use `companion.from` pattern for standard collections, with special handling for `List` and `Array`:

**Implementation:**
```scala
// For List: use directly (listExpr is already List[be])
if (targetTypeName == "scala.collection.immutable.List") {
  listExpr  // Already List[be], no conversion needed
} else {
  // For other collections: use companion.from
  val factory = getCollectionFactory(using q)(targetType)
  val fromMethod = Select.unique(factory, "from")
  Apply(
    TypeApply(fromMethod, List(Inferred(elemType))),
    List(listExpr)
  )
}
```

**Rationale:**
- `companion.from` is the standard Scala 3 pattern for collection construction
- Works for all standard collections (`Vector`, `Set`, `Seq`, `IndexedSeq`, etc.)
- `List` is handled specially since the source is already `List[be]`
- `Array` is handled separately using `Array.from` with `ClassTag` (see Array Handling Solution)

**Array Construction:**
- Arrays require `ClassTag` for construction
- Uses `Array.from[et](list)(using classTag)` pattern
- `ClassTag` is summoned using `Expr.summon[ClassTag[et]]`
- Cross-platform compatible (works on JVM, JS, Native)

**Code Location:** 
- `buildCollectionFromList` in `IntoAsVersionSpecific.scala:641-685`
- `deriveCollectionInto` in `IntoAsVersionSpecific.scala:692-783`

---

## ✅ Map Support Implementation

**Problem:** Map types (`Map[K, V]`) have two type parameters, unlike single-element collections (`List[T]`, `Vector[T]`). The existing `extractCollectionElementType` only handles single-parameter collections, so Map conversions (especially nested ones like `Map[String, List[Int]] → Map[String, Vector[Long]]`) were not supported.

**Solution:** Implement dedicated Map detection and conversion logic before collection detection:

**Implementation:**
1. **Map Type Detection** (`extractMapTypes`):
   - Detects `Map[K, V]` types by checking for `scala.collection.immutable.Map` or `scala.collection.Map` base class
   - Extracts both key and value types separately
   - Handles type aliases via `dealias`

2. **Map Conversion** (`deriveMapInto`):
   - Derives key conversion if keys differ (`Map[Int, String] → Map[Long, String]`)
   - Always derives value conversion (supports nested collections recursively)
   - Uses `sequenceEither` to accumulate conversion errors
   - Handles lazy defs for cycle detection

3. **Priority Integration**:
   - Map detection added with Priority 0.75 (before Collections at Priority 1)
   - Ensures Map types are detected before falling through to generic collection handling

**Rationale:**
- Map has 2 type parameters, requiring separate detection logic
- Recursive value conversion enables nested collections (e.g., `List[Int] → Vector[Long]` inside Map values)
- Key conversion is optional (only when keys differ), reducing overhead
- Uses same error accumulation pattern as collections (`sequenceEither`)

**Code Location:**
- `extractMapTypes` in `IntoAsVersionSpecific.scala:575-587`
- `deriveMapInto` in `IntoAsVersionSpecific.scala:589-702`
- Map detection in `derivedIntoImpl` at Priority 0.75 (before Collections)

**Status:** ✅ **COMPLETED** (Dec 25, 2025) - All Map conversion tests passing

---

## 🐛 Known Issues

### ✅ Issue 1: Primitive Instance Resolution (RESOLVED)

**Problem (Historical):** `Implicits.search` in macro context didn't find primitive `Into` instances from `object Into`.

**Solution Implemented:** Hardcoded primitive handling as PRIORITY 0 in `findOrDeriveInto`:
- Pattern match using `dealias` + `=:=` for robust type matching
- Generate conversion code directly (no implicit search)
- Zero runtime overhead, compile-time only
- Conforms to Golden Rules (not hardcoding arity, but handling well-defined primitive set)

**Status:** ✅ RESOLVED - All primitive conversions working

**References:** See `KNOWN_ISSUES.md` for detailed analysis.

---

## 📚 Lessons Learned

1. **Scope is King:** Scala 3 macros have strict scope rules. Variables must be explicitly in scope when used in quotes.

2. **AST vs Quotes Trade-off:**
   - **AST:** More control, but complex type management and owner handling
   - **Quotes:** Simpler syntax, but scope issues with nested structures

3. **Runtime Helpers:** Moving logic to runtime can help, but closures still need careful scope management.

4. **Recursion in Macros:** Recursive macro code generation amplifies scope issues. Flat structures are safer.

---

## 🔍 Related Issues

- Scope management in Scala 3 macros
- Type inference in macro-generated code
- Owner chain correctness in nested structures
- Closure capture in macro-generated code

---

## 📝 Notes

- All attempts focused on avoiding hardcoded arity (generic recursion)
- Cross-platform compatibility (JVM, JS, Native) must be maintained
- No experimental features allowed (see `ANALYSIS_REGOLE_DORO.md`)

---

**Contributors:** AI Assistant + User  
**References:** 
- `ANALYSIS_REGOLE_DORO.md` - Golden Rules
- `PROGRESS_TRACKER.md` - Current progress
- Scala 3 Macro Documentation

