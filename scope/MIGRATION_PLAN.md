# Scope Migration Plan: Tag-Based → Scope-Local Opaque Types

## Executive Summary

This document outlines the migration from the current tag-based `@@[A, S]` design to the new scope-local `$[A]` opaque type design. The new design eliminates the unsound `scopedUnscoped` ScopeLift instance by making sibling-scoped values structurally incompatible at the type level.

---

## Side-by-Side Comparison

### Core Types

| Aspect | OLD (Tag-Based) | NEW (Scope-Local) |
|--------|-----------------|-------------------|
| Scoped value type | `A @@ S` (global opaque) | `scope.$[A]` (per-scope opaque) |
| Scope class | `Scope[ParentTag, Tag0]` | `sealed abstract class Scope` |
| Scope identity | Type parameters | Path-dependent `$` type |
| Parent reference | Implicit via tag hierarchy | Explicit `parent: Parent` member |
| Parent access | Contravariance (`A @@ Parent <: A @@ Child`) | `lower()` method |
| Global scope | `Scope[GlobalTag, GlobalTag]` | `object global extends Scope` (self-referential) |

### ScopeLift → Eliminated

| Aspect | OLD | NEW |
|--------|-----|-----|
| Mechanism | `ScopeLift` typeclass with 5 priority levels | `Unscoped[A]` constraint on `scoped` |
| Soundness | `scopedUnscoped` is UNSOUND | Sound by design - only pure data escapes |
| Complexity | Conditional output types, priority chains | Simple: always unwrap `$[A]` to `A` |
| Parent handling | Requires subtype check `S <:< T` | Parent `$` is different type, use `lower()` |

### Method Signatures

```scala
// OLD: Using @@ and ScopeTag
infix def $[A, B](scoped: A @@ self.ScopeTag)(f: A => B): B @@ self.ScopeTag
def allocate[A](resource: Resource[A]): A @@ ScopeTag
def scoped[A](f: Scope[ScopeTag, ? <: ScopeTag] => A)(using ScopeLift[A, ScopeTag]): lift.Out

// NEW: Using scope-local $ with ScopeUser SAM and Unscoped constraint
infix def $[A, B](scoped: $[A])(f: A => B): $[B]
def allocate[A](resource: Resource[A]): $[A]
def scoped[A: Unscoped](f: ScopeUser[self.type, A]): A
```

---

## What Can Be Reused

### 1. Internal Mechanics (Keep As-Is)
- **`Finalizers`**: Resource cleanup management unchanged
- **`Resource[A]`**: Acquisition/release pattern unchanged  
- **`PlatformScope`**: Shutdown hook registration unchanged

### 2. Core Patterns (Adapt)
- **All operations are EAGER**: No laziness needed - `Unscoped[A]` prevents escape
- **No `isClosed` checks**: User cannot call scope methods after close
- **No `LazyScoped`**: `$[A] = A` (zero-cost opaque type)
- **Exception handling**: Same try/finally structure in `scoped`
- **`ScopedOps`**: Same map/flatMap signatures, just change type (all eager)

### 3. Tests (Modify Types Only)
- Most test logic stays the same
- Change `A @@ ScopeTag` → `scope.$[A]`
- Change `import scope._` pattern is kept

---

## What Must Change

### 1. Scope Class Definition

```scala
// OLD
final class Scope[ParentTag, Tag0 <: ParentTag] private[scope] (
  private[scope] val finalizers: Finalizers
) extends ScopeVersionSpecific[ParentTag, Tag0] with Finalizer {
  type ScopeTag = Tag0
  // ...
}

// NEW
sealed abstract class Scope { self =>
  type $[+A]           // Each scope's unique scoped type (ZERO-COST: $[A] = A)
  type Parent <: Scope
  val parent: Parent
  
  // Zero-cost wrap/unwrap (identity at runtime)
  protected def $wrap[A](a: A): $[A]
  protected def $unwrap[A](sa: $[A]): A
  
  // All operations EAGER - no laziness, no isClosed checks
  // ... rest of implementation
}
```

### 2. Scoped.scala → Remove or Repurpose

The global `@@` type is no longer needed:

```scala
// OLD: Global opaque type with lazy thunks
object Scoped {
  opaque type Scoped[+A, -S] = A | LazyScoped[A]
  // ...
}
infix type @@[+A, -S] = Scoped.Scoped[A, S]

// NEW: No global scoped type - each Scope defines its own $[A]
// No LazyScoped needed - $[A] = A (zero-cost)
```

### 3. ScopeLift.scala → DELETED

```scala
// OLD: External with 5 priority levels (UNSOUND)
trait ScopeLift[A, S] { type Out; def apply(a: A): Out }
object ScopeLift extends ScopeLiftMidPriority { ... }
// ... complex priority chain with unsound scopedUnscoped

// NEW: No ScopeLift needed!
// Instead, scoped requires Unscoped[A] evidence directly:
def scoped[A: Unscoped](f: ScopeUser[self.type, A]): A = {
  val child = new Scope.Child[self.type](self)
  val result: child.$[A] = f.use(child)
  child.scoped.run(result)  // package-private unwrap
  // close and return
}
```

### 4. Parent Value Access

```scala
// OLD: Implicit via contravariance
// parent.$[A] is subtype of child.$[A], so it just works

// NEW: Explicit lower() method
sealed abstract class Scope { self =>
  final def lower[A](value: parent.$[A]): $[A] =
    value.asInstanceOf[$[A]]  // Sound: parent outlives child
}

// Usage:
parent.scoped { child =>
  val parentValue: parent.$[Int] = ...
  val inChild: child.$[Int] = child.lower(parentValue)
}
```

### 5. Global Scope

```scala
// OLD
object Scope {
  type GlobalTag
  lazy val global: Scope[GlobalTag, GlobalTag] = new Scope(new Finalizers)
}

// NEW
object Scope {
  object global extends Scope {
    type $[+A] = A           // Zero-cost opaque type
    type Parent = global.type
    val parent: Parent = this
    
    protected def $wrap[A](a: A): $[A] = a
    protected def $unwrap[A](sa: $[A]): A = sa
  }
}
```

### 6. Child Scope

```scala
// OLD: Same class with different type parameters
new Scope[self.ScopeTag, self.ScopeTag](childFinalizers)

// NEW: Separate Child class (zero-cost)
final class Child[P <: Scope](val parent: P) extends Scope {
  type Parent = P
  opaque type $[+A] = A  // Zero-cost!
  
  def close(): Unit = ()  // TODO: integrate with Finalizers
  
  protected def $wrap[A](a: A): $[A] = a
  protected def $unwrap[A](sa: $[A]): A = sa
}
```

---

## Impact on Dependent Code

### 1. Resource.scala

```scala
// OLD
trait Resource[A] {
  def make(finalizer: Finalizer): A
}

// NEW: No change needed
// Resource just produces A, scope wraps it in $[A]
```

### 2. Wire Module

Need to check how Wire uses scopes. Likely changes:
- Type signatures using `@@` → use scope's `$`
- If Wire creates scopes, update to new pattern

### 3. scope-examples

```scala
// OLD (TransactionBoundaryExample.scala)
val conn: DbConnection @@ ScopeTag = allocate(...)
val program = for {
  c  <- conn
  tx <- allocate(c.beginTransaction("tx-001"))
} yield { ... }

// NEW
val conn: connScope.$[DbConnection] = connScope.allocate(...)
val program = for {
  c  <- conn
  tx <- txScope.allocate(c.beginTransaction("tx-001"))
} yield { ... }
```

### 4. User Code Migration

| Old Pattern | New Pattern |
|-------------|-------------|
| `val x: A @@ scope.ScopeTag` | `val x: scope.$[A]` |
| `import scope._` | `import child._` |
| `$(resource)(f)` | `(child $ resource)(f)` (infix `$` method) |
| Implicit parent access | `lower(parentValue)` (after import) |
| `@@.scoped(a)` | `scoped(a)` (creates scoped value) |
| Return from block | Return `$[A]`, unwrapped to `A` at boundary |
| Raw `Unscoped` return | Implicitly wrapped via `wrapUnscoped` |
| `Scoped.run(x)` | N/A - `scoped.run` is package-private |

---

## Migration Steps

### Phase 1: Prepare (Non-Breaking)
1. [ ] Add `ScopePrototype.scala` to codebase ✓ (done)
2. [ ] Run prototype tests to validate design
3. [ ] Document new API patterns

### Phase 2: Core Changes
1. [ ] Rewrite `Scope` as sealed abstract class
2. [ ] Create `Scope.global` as self-referential singleton
3. [ ] Create `Scope.Child` class with opaque `$`
4. [ ] Add `ScopeUser` SAM trait
5. [ ] Add `lower()` method
6. [ ] Add `Unscoped[A]` constraint to `scoped`
7. [ ] Integrate with `Finalizers` properly

### Phase 3: Remove Old Code
1. [ ] Remove global `@@` type alias
2. [ ] Remove `Scoped.Scoped` opaque type
3. [ ] Delete `ScopeLift.scala` entirely
4. [ ] Remove `ScopeTag` type alias
5. [ ] Remove type parameters from Scope
6. [ ] Remove `LazyScoped` (no thunks needed)

### Phase 4: Update Dependents
1. [ ] Update `Resource` if needed
2. [ ] Update `Wire` module
3. [ ] Update `scope-examples`
4. [ ] Update all tests

### Phase 5: Cleanup
1. [ ] Remove `ScopeVersionSpecific` (merge into Scope)
2. [ ] Remove `ScopePrototype.scala` (was just for exploration)
3. [ ] Update documentation

---

## Test Changes

### Tests to Keep (with type changes)
- Finalizer ordering tests
- Resource allocation/release tests
- Exception handling in scoped
- Unwrap-before-close ordering test

### Tests to Add
- Sibling isolation (compile-time check that `sibling.$[A]` can't enter `child.$`)
- Grandparent lowering chain: `child.lower(parent.lower(grandparent.value))`
- Global scope identity: `global.$[A]` is just `A`
- Zero-overhead verification for global scope

### Tests to Remove/Modify
- Any tests relying on tag variance
- Tests using `@@` type alias

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking change for users | High | Provide migration guide, deprecation period |
| Lower vs implicit parent | Medium | Consider implicit conversion as convenience |
| Performance regression | Low | Profile; global scope is zero-overhead by design |
| Scala 2 compatibility | High | Need Scala 2 version of Child class |

---

## Open Questions

1. **Should `lower` be implicit?** Could add:
   ```scala
   implicit def autoLower[A](v: parent.$[A]): $[A] = lower(v)
   ```
   Pro: Ergonomic. Con: Less explicit about scope relationships.

2. **How to handle grandparent lowering?** Currently requires chaining:
   ```scala
   child.lower(parent.lower(grandparent.value))
   ```
   Could add transitive lower, but adds complexity.

3. **Scala 2 compatibility?** The opaque type inside Scope.Child needs equivalent in Scala 2 (type alias + private constructor).

---

## Files to Change

| File | Action |
|------|--------|
| `Scope.scala` | Major rewrite |
| `ScopeVersionSpecific.scala` | Merge into Scope, delete |
| `ScopeLift.scala` | Delete entirely |
| `Scoped.scala` | Delete (no LazyScoped needed) |
| `Resource.scala` | Minor updates if any |
| `Unscoped.scala` | Keep as-is |
| `ScopePrototype.scala` | Delete after migration |
| `scope-examples/*.scala` | Update types |
| All test files | Update types |

---

## Conclusion

The migration from tag-based to scope-local opaque types is a significant but worthwhile change:

- **Eliminates unsoundness**: No more `scopedUnscoped` that strips tags
- **Eliminates ScopeLift**: Replaced with simple `Unscoped[A]` constraint
- **Type system enforces isolation**: Sibling values are incompatible types
- **Zero-cost**: `$[A] = A` for all scopes (no thunks, all operations eager)
- **Scala 2/3 compatible**: `ScopeUser` SAM trait works in both

The main cost is the breaking change to user code (`@@` → `$`, implicit parent access → explicit `lower()`), but the soundness guarantee is worth it.
