# ScopedTagging Refactoring Plan

This document compares the experimental `ScopedTagging` prototype with the production `Scope` implementation and outlines the work needed to integrate leak-prevention into the production code.

## Overview

**Goal**: Prevent scoped resources from escaping their scope at compile time, without requiring full monadic discipline.

**Approach**: Use opaque tagging (`@@`) with path-dependent scope types to track resource lifetime at the type level. Values are "locked" to their scope and can only be accessed via controlled operators that re-tag results appropriately.

---

## A. Similarities

| Aspect | Production | Experiment |
|--------|------------|------------|
| **Core abstraction** | `Scope[Stack]` trait | `Scope { type Tag }` trait |
| **Scope hierarchy** | Parent/child via `injected` | Parent/child via `injected` |
| **Cleanup registration** | `defer(finalizer)` | `defer(finalizer)` |
| **LIFO cleanup order** | Finalizers run in reverse order | Finalizers run in reverse order |
| **Closeable scopes** | `Scope.Closeable[Head, Tail]` | `Scope.Closeable[T, ParentTag]` |
| **Run-and-close pattern** | `.run { ... }` auto-closes | `.run { ... }` auto-closes |
| **Global scope** | `Scope.global` | `Scope.global` |
| **Top-level defer** | `def defer(...)` in package | `def defer(...)` top-level |

---

## B. Differences

### B.1 Fundamental Design Differences

| Aspect | Production | Experiment | Notes |
|--------|------------|------------|-------|
| **Value access** | `$[T]` returns raw `T` | `$` operator on tagged value, returns `T` or `T @@ S` | Experiment controls escape via return type |
| **Type-level tracking** | HList stack (`Context[A] :: Context[B] :: TNil`) | Path-dependent `Tag` type member | Different mechanisms for scope identity |
| **Escape prevention** | None (relies on discipline) | Opaque `@@[A, S]` hides methods | Core safety innovation |
| **Conditional untagging** | N/A | `Unscoped[A]` typeclass | Primitives/data escape; resources stay tagged |
| **Scope capability check** | `InStack[T, Stack]` evidence | `Scope { type Tag >: S }` refinement | Both compile-time verified |
| **Value combination** | Manual tuple construction | `*` operator with union tags | `(A @@ T1) * (B @@ T2) = (A, B) @@ (T1 | T2)` |
| **For-comprehension** | Not applicable | `map`/`flatMap` with tag accumulation | Monadic composition of tagged values |

### B.2 Differences Due to Toy Nature

| Aspect | Production | Experiment | Work Needed |
|--------|------------|------------|-------------|
| **Dependency wiring** | Macro-based constructor inspection | Manual value injection | Integrate with `ScopeMacros` |
| **Wire abstraction** | `Wire[In, Out]` with shared/unique | None | Add Wire support returning tagged values |
| **Wireable** | `Wireable[T]` for traits | None | Extend to return tagged wires |
| **AutoCloseable detection** | Macro auto-registers `close()` | Manual defer calls | Integrate with macro detection |
| **Error collection** | `Chunk[Throwable]` from `close()` | Simple `Unit` close | Add error accumulation |
| **Thread safety** | `AtomicBoolean` for close-once | Simple `var closed` | Use atomic operations |
| **Shutdown hook** | `PlatformScope.registerShutdownHook` | None on global | Add platform-specific hooks |
| **Scala 2 support** | Full cross-build | Scala 3 only (opaque types) | Requires alternative for Scala 2 |
| **Context integration** | Uses `Context[T]` wrapper | Raw values | Integrate with Context |
| **runWithErrors** | Returns `(B, Chunk[Throwable])` | Not implemented | Add variant |

### B.3 API Surface Differences

| Production API | Experiment Equivalent |
|----------------|----------------------|
| `$[T]` | `closeable.value $ (_.method())` |
| `scope.get[T]` | `closeable.value $ identity` (if Unscoped) |
| `shared[T]` | Not yet implemented |
| `unique[T]` | Not yet implemented |
| `Wire(value)` | `@@.tag(value)` (partial) |
| `Scope.Has[T]` | `Scope { type Tag >: S }` |
| `Scope.Any` | `Scope` (any tag) |

---

## C. Refactoring Work Remaining

### Phase 1: Core Integration

1. **Merge Tag into Scope trait**
   - Add `type Tag` member to production `Scope[Stack]`
   - Child scopes get tag that is subtype of parent's tag
   - Global scope has `type Tag = Nothing`

2. **Tagged value wrapper**
   - Add `opaque infix type @@[A, S] = A` to production code
   - Move `Unscoped` and `Untag` typeclasses
   - Add all `@@` extensions (`$`, `*`, `map`, `flatMap`, `_1`, `_2`)

3. **Modify `$[T]` accessor**
   - Current: `def $[T](using Scope.Has[T]): T`
   - New: `def $[T](using Scope.Has[T]): T @@ scope.Tag`
   - Requires scope tag to be in scope (pun intended)

### Phase 2: Wire Integration

4. **Wire returns tagged values**
   - `Wire.construct` returns `Context[T @@ scope.Tag]`
   - Macro-generated wires tag their output
   - `shared[T]` and `unique[T]` return tagged results

5. **Closeable.value**
   - Add `def value: Head @@ Tag` to `Scope.Closeable`
   - Primary way to access the injected service

6. **Update run signature**
   - Current: `def run[B](f: Scope.Has[Head] ?=> B): B`
   - Ensure `B` is checked for escape (or accept that return values are responsibility of caller)

### Phase 3: Unscoped Typeclass

7. **Define Unscoped instances**
   - All primitives (`Int`, `String`, `Boolean`, etc.)
   - Standard collections of Unscoped elements
   - Consider: types with `Schema` are Unscoped (serializable = can escape)

8. **Schema integration**
   - `given [A: Schema]: Unscoped[A]` - if you can serialize it, it can escape
   - This is the conceptual link: Schema types are data, not resources

### Phase 4: Macro Updates

9. **Update ScopeMacros.injectedImpl**
   - Generated code tags values with scope's Tag
   - AutoCloseable detection unchanged (defer still works)

10. **Update ScopeMacros.sharedImpl/uniqueImpl**
    - Wire construction functions receive tagged scope
    - Output values are tagged

### Phase 5: Documentation & Testing

11. **Documentation updates**
    - Update scope.md with new patterns
    - Add examples of for-comprehension usage
    - Document Unscoped typeclass

12. **Compile-time safety tests**
    - Tests that verify certain code *doesn't* compile
    - Using `scala.compiletime.testing.typeCheckErrors`

13. **Runtime behavior tests**
    - All existing tests should pass
    - New tests for tagged value operations

---

## D. Open Questions

1. **Naming**: Is `@@` the best operator? Alternatives: `@:`, `Tagged`, `Scoped`

2. **Unscoped derivation**: Should we auto-derive `Unscoped` for case classes of Unscoped fields?

3. **Escape hatch**: Should there be an explicit unsafe escape? `tagged.unsafeGet: A`

4. **Error messages**: How to make type errors user-friendly when tags don't match?

5. **Performance**: Verify that `inline` eliminates all runtime overhead (benchmark)

6. **Variance**: Is the current `Tag >: S` check the right direction for all use cases?

---

## E. File Changes Summary

| File | Changes |
|------|---------|
| `Scope.scala` | Add `type Tag` member |
| `package.scala` | Update `$[T]` to return tagged, add `@@` and extensions |
| `Wire.scala` | Return tagged values from construct |
| `ScopeMacros.scala` | Tag generated values |
| `ScopeVersionSpecific.scala` | Update run/runWithErrors signatures |
| `internal/ScopeImpl*.scala` | Add Tag type member, value accessor |
| New: `Tagged.scala` | Opaque type and operations (or inline in package) |
| New: `Unscoped.scala` | Typeclass and instances |
| New: `Untag.scala` | Conditional untagging typeclass |

---

## F. Success Criteria

1. ✅ Scoped resources cannot escape without explicit scope capability
2. ✅ Data types (primitives, serializable) escape freely
3. ✅ Child scopes can use parent-scoped resources
4. ✅ Cleanup order preserved (LIFO)
5. ✅ Zero runtime overhead (all tagging is compile-time)
6. ✅ For-comprehension syntax works naturally
7. ⬜ All existing tests pass
8. ⬜ Macro-based wiring works with tagging
