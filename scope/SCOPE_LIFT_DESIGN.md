# ScopeLift Redesign - Design Document

## Current Status: Scope-Local Opaque Type Approach

After the macro approach failed (see "Failed Experiments" below), we identified a new approach
that uses **scope-local opaque types** to achieve compile-time soundness without macros.

---

## The New Approach: Scope-Local `$[A]`

### Key Insight

The macro approach failed because we couldn't distinguish sibling-scoped values from child-scoped
values at compile time—both have tags that are subtypes of the parent tag.

**Solution**: Make the scoped value type (`$[A]`) an **opaque type defined inside each Scope instance**.

- `parent.$[A]` and `child.$[A]` are **structurally incompatible types**
- `sibling.$[A]` is also incompatible with `child.$[A]`
- No tag comparison needed—the type system enforces isolation automatically

### Why This Works

1. **Current `flatMap` requires matching tags**: After the "import scope._" refactor, `flatMap` 
   signature is `def flatMap[B](f: A => B @@ self.ScopeTag): B @@ self.ScopeTag`. You cannot 
   combine values from different scopes within a single scope's operations.

2. **Contravariance still allows parent values**: Parent-scoped values can be used in child scopes
   (their tags narrow to the child), which is safe because parents outlive children.

3. **Scope-local types prevent sibling pollution**: A sibling-scoped value `sibling.$[A]` cannot
   enter a child scope because child operations expect `child.$[A]`, and these are different types.

### Proposed Design

```scala
class Scope { self =>
  // Each scope has its own unique scoped value type
  opaque type $[+A] = A | LazyScoped[A]
  
  // Parent reference for lowering (see Open Questions below)
  type Parent <: Scope
  val parent: Parent
  
  // Allocate returns this scope's $
  def allocate[A](resource: Resource[A]): $[A] = ...
  
  // Lower parent-scoped value into this scope (safe: parent outlives child)
  def lower[A](value: parent.$[A]): $[A] = value.asInstanceOf[$[A]]
  
  // Operations only work with this scope's $
  def $[A, B](scoped: $[A])(f: A => B): $[B] = ...
  
  // Child scope creation
  def scoped[A](f: Scope { type Parent = self.type } => A)(using lift: ScopeLift[A]): lift.Out = {
    val child = new Scope { 
      type Parent = self.type
      val parent: Parent = self 
    }
    // ... use child, apply lift, close
  }
}
```

### ScopeLift Simplification

At scope boundary, ScopeLift only needs to handle **this scope's `$`**:

- `self.$[A]` where `A` is `Unscoped` → unwrap to `A`
- `Unscoped` types → pass through
- `parent.$[A]` → pass through unchanged (different type, no unwrapping needed)

Since `parent.$[A]` is a structurally different type from `self.$[A]`, it won't match the
unwrapping instance and will pass through safely.

### Scope Tags Eliminated

With this approach, scope identity comes from the path-dependent `$` type, not from tag type
parameters. The `Scope` class no longer needs `ParentTag` and `Tag0` type parameters.

### Parent Value Usage

To use a parent-scoped value in a child scope:

```scala
parent.scoped { child =>
  val parentValue: parent.$[A] = ...
  val childValue: child.$[A] = child.lower(parentValue)  // explicit
  // OR with implicit conversion:
  // implicit def lowerParent[A](v: parent.$[A]): child.$[A] = child.lower(v)
}
```

---

## Open Questions (To Explore in Prototype)

### 1. The `parent` Type Problem

We need each scope to have a typed reference to its parent so that `parent.$[A]` is a valid type.

**Attempt 1** (doesn't work):
```scala
val parent: Scope  // parent.$ is not accessible because Scope.$ is opaque
```

**Attempt 2** (recursive type issue):
```scala
type Parent <: Scope
val parent: Parent
// But for global scope: type Parent = Scope.type creates recursion
```

**Possible solutions to explore**:
- Use `Null` or a sentinel for global scope's parent
- Use a type member with `Aux` pattern: `type Typed[P <: Scope] = Scope { type Parent = P }`
- Use intersection types or match types
- Make global scope special-cased

### 2. Grandparent Lowering

With nested scopes (grandparent → parent → child), how does child use grandparent values?

Options:
- Chain: `child.lower(parent.lower(grandparentValue))`
- Transitive lowering: `child.lowerFrom(grandparent)(value)`
- Implicit conversion chain

### 3. ScopeLift Type Parameter

Does `ScopeLift` need to know about the scope's `$` type?

```scala
// Option A: ScopeLift is scope-local
class Scope {
  trait ScopeLift[A] { type Out; def apply(a: A): Out }
  given liftScoped[A: Unscoped]: ScopeLift[$[A]] = ...
}

// Option B: ScopeLift takes scope as parameter
trait ScopeLift[A, S <: Scope] { ... }
```

---

## Prototype Plan

Create a standalone prototype in `scope/shared/src/main/scala-3/zio/blocks/scope/ScopePrototype.scala`
to explore:

1. Scope-local opaque `$[A]` type
2. Parent type representation (the recursive type challenge)
3. Lowering mechanism
4. ScopeLift with scope-local types
5. Sibling isolation verification

---

## Failed Experiments (Historical Reference)

### Macro Approach (FAILED)

Attempted to use Scala 3 macros to inspect type trees and strip child tags at compile time.

**Why it failed**:

1. **Subtype checking doesn't work across path-dependent types at macro expansion time**
   - `child.ScopeTag <:< parent.ScopeTag` returns `false` in the macro

2. **TermRef name matching is unsound**
   - Stripping by name strips ALL tags with different names, including sibling-scoped tags
   - Sibling-scoped values capture dead resources - stripping allows use-after-close

3. **Inline methods + existential types don't mix**
   - Nested `scopedMacro` calls fail because parent scope has existential type

4. **The fundamental problem**: At compile time, we couldn't distinguish "current closing child" 
   from "already-dead sibling"—both have different TermRef names from parent.

### Cleanup Done

The following experimental code was removed:
- `ScopeLiftMacros.scala`
- `ScopeLiftMacroExploreSpec.scala`
- `scopedMacro`, `liftScopedResult`, `scopedMacroWithChild` methods
- `createChildScope` helper

### What Was Kept

The **lift-before-close fix** in `scoped` method is valid and was kept:
```scala
try {
  val result = f(childScope)
  out = lift(result)  // BEFORE close - thunk evaluated while scope open
} finally {
  childScope.close()
}
```

A regression test was added to verify this behavior.

---

## Original Problem Statement

The `scopedUnscoped` instance in `ScopeLift` is **UNSOUND**:

```scala
given scopedUnscoped[A, T, S](using Unscoped[A]): ScopeLift.Aux[A @@ T, S, A]
```

It outputs raw `A` instead of `A @@ S` (parent-scoped), losing all scope tracking. Values become
accessible even after the parent scope closes.

### The Sibling Problem

```scala
parent.scoped { sibling =>
  val x = allocate(...)  // x: A @@ sibling.ScopeTag, captures resource
}
// sibling scope CLOSED, resource freed

parent.scoped { child =>
  // If somehow x reaches here and we strip sibling tag...
  // x becomes usable but captures a DEAD resource
  // Forcing x accesses freed memory = UB
}
```

The new scope-local `$` approach solves this because `sibling.$[A]` is incompatible with `child.$[A]`.

---

## Files Involved

- `scope/shared/src/main/scala-3/zio/blocks/scope/Scope.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeLift.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/Scoped.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeVersionSpecific.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopePrototype.scala` (new, for exploration)

---

## PR #1053 Status

- [x] Fixed lift-before-close bug in ScopeVersionSpecific
- [x] Added regression test for lift-before-close
- [x] Cleaned up failed macro experiment
- [x] Documented new scope-local `$` approach
- [x] Created working prototype in `ScopePrototype.scala`
- [x] ScopeLift defined inside Scope - dramatically simplified
- [ ] Implement scope-local `$` design in real Scope
- [ ] Update tests
- [ ] Fix scope-examples

## Working Prototype

See `scope/shared/src/main/scala-3/zio/blocks/scope/ScopePrototype.scala`.

Key features:
- `Scope` is a sealed abstract class with abstract `$[+A]` type
- `Scope.global` is self-referential with `$[A] = A` (zero overhead)
- `Scope.Child[P]` has `opaque type $[+A] = A | (() => A)` (lazy thunks)
- `ScopeLift` is defined INSIDE each Scope, so it knows about `self.$`
- Eager/deferred logic based on `isClosed` state

ScopeLift instances:
1. `selfScopedLift`: `self.$[A]` with A: Unscoped → unwrap to A
2. `passThrough`: Everything else → pass through unchanged
