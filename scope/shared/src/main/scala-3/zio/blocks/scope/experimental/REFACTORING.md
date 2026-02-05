# ScopedTagging Refactoring - COMPLETE

This document tracks the integration of compile-time escape prevention into the production `Scope` module.

## Overview

**Goal**: Prevent scoped resources from escaping their scope at compile time, without requiring full monadic discipline.

**Status**: ✅ COMPLETE (Phase 1-4)

---

## Completed Work

### Phase 1: Core Integration ✅

1. **Added `type Tag` member to `Scope[Stack]` trait**
   - Child scopes have `type Tag = ParentTag | this.type` (union type)
   - Global scope has `type Tag = this.type` (singleton type)
   - Union types ensure `ParentTag <: (ParentTag | this.type)`, enabling child scopes to use parent-scoped values

2. **Created `Tagged.scala` with opaque type and operations**
   - `opaque infix type @@[+A, S] = A` - covariant for compatibility with `Closeable[+Head, +Tail]`
   - `@@.tag` (inline) - zero overhead tagging
   - `$` operator - access value with scope capability check
   - `map`, `flatMap` (inline) - for-comprehension support with tag accumulation
   - `_1`, `_2` (inline) - tuple accessors preserving tag

3. **Implemented `Unscoped` typeclass**
   - Primitives: Int, Long, Short, Byte, Char, Boolean, Float, Double, Unit
   - String, BigInt, BigDecimal
   - Collections of Unscoped: Array, List, Vector, Set, Option, Seq, Map
   - Tuples of Unscoped elements (2-4 arity)

4. **Implemented `Untag` typeclass**
   - Conditional untagging based on `Unscoped` evidence
   - `Unscoped` types → raw value
   - Non-Unscoped types → re-tagged value

5. **Added `Closeable.value: Head @@ Tag`** (Scala 3 only)
   - Primary entry point for escape-safe resource access

### Phase 2: Wire Integration (Deferred)

**Decision**: Wire integration is deferred because:
- Current `$[T]` returns raw `T` - changing to `T @@ scope.Tag` is a breaking change
- Users who want escape prevention should use `Closeable.value` explicitly
- The core feature is complete without Wire changes

### Phase 3: Schema Integration (User Responsibility)

**Decision**: Schema integration is left to users because:
- `scope` module doesn't depend on `schema` module
- Adding the dependency would make `scope` heavier
- Users can add `given [A: Schema]: Unscoped[A] = new Unscoped[A] {}` if needed

### Phase 4: Compile-Time Safety Tests ✅

Created `TaggedCompileTimeSpec.scala` with 5 tests:
- Verifies opaque type hides methods (using `typeCheckErrors`)
- Verifies `map` preserves exact tag type
- Verifies `flatMap` produces union tag
- Verifies Unscoped types escape correctly
- Verifies resourceful types stay tagged

---

## Zero Overhead Verification

Bytecode audit confirmed:
- `@@.tag`, `@@.untag`: `inline` → erased at compile time
- `map`, `flatMap`: `inline` → direct function application
- `$` operator: One virtual call to `Untag.apply` (identity or tag, both trivial)
- Opaque type `@@`: No runtime representation

---

## Test Results

- **Scala 3**: 150 tests pass (138 original + 7 escape prevention + 5 compile-time safety)
- **Scala 2**: 92 tests pass (escape prevention is Scala 3 only)

---

## Files Changed

| File | Changes |
|------|---------|
| `Scope.scala` | Added `type Tag` member, updated GlobalScope |
| `Tagged.scala` | NEW - `@@`, `Unscoped`, `Untag`, extensions |
| `ScopeVersionSpecific.scala` (Scala 3) | Added `def value: Head @@ Tag` |
| `ScopeImplVersionSpecific.scala` (Scala 3) | Added `type Tag = ParentTag \| this.type`, implemented `value` |
| `ScopeImplVersionSpecific.scala` (Scala 2) | Added `type Tag >: ParentTag` |
| `Context.scala` | Added `head: Any` method |
| `ContextEntries.scala` | Added `head: Any` method |
| `TaggedEscapeSpec.scala` | NEW - Runtime behavior tests |
| `TaggedCompileTimeSpec.scala` | NEW - Compile-time safety tests |
| `docs/scope.md` | Updated with escape prevention documentation |

---

## Success Criteria

1. ✅ Scoped resources cannot escape without explicit scope capability
2. ✅ Data types (primitives, collections) escape freely via `Unscoped`
3. ✅ Child scopes can use parent-scoped resources (via union tags)
4. ✅ Cleanup order preserved (LIFO)
5. ✅ Zero runtime overhead (opaque types + inline)
6. ✅ For-comprehension syntax works naturally
7. ✅ All existing tests pass
8. ✅ Compile-time safety verified with `typeCheckErrors`

---

## Usage Example

```scala
import zio.blocks.scope._

val closeable = Scope.global.injectedValue(new InputStream(...))
val stream: InputStream @@ closeable.Tag = closeable.value

// stream.read()  ← Compile error! Methods are hidden.

// Must use $ operator with scope in context:
val n: Int = stream.$(_.read())(using closeable)(using summon)

// Primitives escape; resources stay tagged:
val body: InputStream @@ closeable.Tag = request.$(_.body)(using closeable)(using summon)

closeable.closeOrThrow()
// stream can no longer be used (scope reference gone)
```
