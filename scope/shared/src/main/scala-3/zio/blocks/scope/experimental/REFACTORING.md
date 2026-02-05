# ScopedTagging Refactoring Plan

This document compares the experimental `ScopedTagging` prototype with the production `Scope` implementation and outlines the work needed to integrate leak-prevention into the production code.

## Overview

**Goal**: Prevent scoped resources from escaping their scope at compile time, without requiring full monadic discipline.

**Approach**: Use opaque tagging (`@@`) with path-dependent scope types to track resource lifetime at the type level. Values are "locked" to their scope and can only be accessed via controlled operators that re-tag results appropriately.

---

## Current Status

### Phase 1: Core Integration ✅ COMPLETE

1. **Merge Tag into Scope trait** ✅
   - Added `type Tag` member to production `Scope[Stack]`
   - Child scopes have `type Tag = ParentTag | this.type` (union type)
   - Global scope has `type Tag = this.type` (singleton type)
   - Union types ensure `ParentTag <: (ParentTag | this.type)`, enabling child scopes to use parent-scoped values

2. **Tagged value wrapper** ✅
   - Added `opaque infix type @@[+A, S] = A` in `Tagged.scala`
   - Implemented `Unscoped` typeclass for types that can escape untagged
   - Implemented `Untag` typeclass for conditional untagging
   - Added `$` operator, `map`, `flatMap`, `_1`, `_2` extensions
   - Removed `*` operator in favor of for-comprehensions only

3. **Closeable.value** ✅
   - Added `def value: Head @@ Tag` to `Scope.Closeable` (Scala 3 only)
   - Primary way to access the injected service with compile-time escape prevention

### What's Working

- `Scope.Closeable.value` returns tagged values
- `$` operator requires matching scope capability (`Scope[?] { type Tag >: S }`)
- Unscoped types (primitives, String, collections) escape untagged
- Resourceful types stay tagged after `$` operator
- For-comprehensions work with tag accumulation via union types
- 145 tests passing (138 original + 7 new tagged escape tests)
- Cross-Scala build works (Scala 2 has 92 tests, tagging is Scala 3 only)

---

## Remaining Work

### Phase 2: Wire Integration (Not Started)

4. **Wire returns tagged values**
   - `Wire.construct` should return `Context[T @@ scope.Tag]`
   - Macro-generated wires should tag their output
   - `shared[T]` and `unique[T]` should return tagged results

5. **Update `$[T]` accessor**
   - Current: `def $[T](using Scope.Has[T]): T` returns raw value
   - Consider: Should it return `T @@ scope.Tag`? This would be a breaking change.

### Phase 3: Schema Integration (Not Started)

6. **Schema integration**
   - `given [A: Schema]: Unscoped[A]` - if you can serialize it, it can escape
   - This links serializable types to escapable types

### Phase 4: Compile-Time Safety Tests (Not Started)

7. **Tests that verify code doesn't compile**
   - Using `scala.compiletime.testing.typeCheckErrors`
   - Verify that tagged values can't be used without matching scope

---

## Key Design Decisions Made

1. **Union types for scope hierarchy**: Child scope `Tag = ParentTag | this.type` ensures `ParentTag <: ChildTag`, so the `$` operator's `Tag >: S` constraint is satisfied when a child scope uses parent-scoped values.

2. **Covariant `@@`**: Made `@@[+A, S]` covariant in `A` to work with covariant type parameters in `Closeable[+Head, +Tail]`.

3. **For-comprehensions only**: Removed `*` operator - use `flatMap`/`map` for combining tagged values. This provides a single, idiomatic way to combine scoped values.

4. **Scala 3 only for tagging**: Opaque types require Scala 3. Scala 2 builds work but don't have escape prevention.

---

## File Changes Made

| File | Changes |
|------|---------|
| `Scope.scala` | Added `type Tag` member, updated GlobalScope to use `this.type` |
| `Tagged.scala` | NEW - Opaque `@@`, `Unscoped`, `Untag`, extensions |
| `ScopeVersionSpecific.scala` (Scala 3) | Added `def value: Head @@ Tag` to Closeable |
| `ScopeImplVersionSpecific.scala` (Scala 3) | Added `type Tag = ParentTag \| this.type`, implemented `value` |
| `ScopeImplVersionSpecific.scala` (Scala 2) | Added `type Tag >: ParentTag` for compatibility |
| `Context.scala` | Added `head: Any` method for internal use |
| `ContextEntries.scala` | Added `head: Any` method |
| `TaggedEscapeSpec.scala` | NEW - Tests for escape prevention |

---

## Success Criteria

1. ✅ Scoped resources cannot escape without explicit scope capability
2. ✅ Data types (primitives, serializable) escape freely via Unscoped
3. ✅ Child scopes can use parent-scoped resources (via union tags)
4. ✅ Cleanup order preserved (LIFO)
5. ✅ Zero runtime overhead (opaque types + inline)
6. ✅ For-comprehension syntax works naturally
7. ✅ All existing tests pass (145 total)
8. ⬜ Macro-based wiring works with tagging (future work)
