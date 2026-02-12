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

1. **Scope-local types prevent sibling pollution**: A sibling-scoped value `sibling.$[A]` cannot
   enter a child scope because child operations expect `child.$[A]`, and these are different types.

2. **Parent values require explicit `lower()`**: To use a parent-scoped value in a child scope,
   you must explicitly call `child.lower(parentValue)`. This is safe because parents outlive children.

3. **Operations are scope-specific**: After `import child._`, `flatMap` signature is
   `def flatMap[B](f: A => $[B]): $[B]` where `$` is `child.$`. You cannot combine values from
   different scopes within a single scope's operations.

### Proposed Design

```scala
sealed abstract class Scope { self =>
  // Each scope has its own unique scoped value type
  // ZERO-COST: $[A] = A (opaque type, no runtime overhead)
  type $[+A]
  
  // Parent reference for lowering
  type Parent <: Scope
  val parent: Parent
  
  // Zero-cost wrap/unwrap (identity at runtime)
  protected def $wrap[A](a: A): $[A]
  protected def $unwrap[A](sa: $[A]): A
  
  // Allocate returns this scope's $
  def allocate[A](resource: Resource[A]): $[A] = ...
  
  // Lower parent-scoped value into this scope (safe: parent outlives child)
  def lower[A](value: parent.$[A]): $[A] = value.asInstanceOf[$[A]]
  
  // Operations are EAGER (no laziness needed - nothing can escape)
  infix def $[A, B](scoped: $[A])(f: A => B): $[B] = $wrap(f($unwrap(scoped)))
  
  // Child scope creation - requires Unscoped[A] to prevent leaking resources/closures
  def scoped[A: Unscoped](f: ScopeUser[self.type, A]): A = {
    val child = new Scope.Child[self.type](self)
    val result: child.$[A] = f.use(child)
    val unwrapped: A = child.scoped.run(result)  // package-private
    child.close()
    unwrapped
  }
}

// SAM type for Scala 2/3 compatibility
@FunctionalInterface
trait ScopeUser[P <: Scope, +A] {
  def use(child: Scope.Child[P]): child.$[A]
}
```

### No ScopeLift Needed

The old `ScopeLift` typeclass with conditional output types is eliminated. Instead:

- `scoped` always expects `child.$[A]` and unwraps to `A`
- **`Unscoped[A]` constraint** ensures only pure data crosses the boundary (no resources, scopes, or closures)
- This prevents the unsoundness of the old `scopedUnscoped` instance

### Scope Tags Eliminated

Scope identity comes from the path-dependent `$` type, not from tag type parameters.
The `Scope` class no longer needs `ParentTag` and `Tag0` type parameters.

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

## Resolved Design Questions

### 1. The `parent` Type Problem → SOLVED

**Solution**: Global scope is self-referential (`type Parent = global.type`, `val parent = this`).
Child scopes use `type Parent = P` where `P` is the parent scope type parameter.

### 2. Grandparent Lowering → SOLVED

**Solution**: Chain explicit `lower` calls: `child.lower(parent.lower(grandparentValue))`.
This is explicit but type-safe. No implicit conversion chains needed.

### 3. ScopeLift → ELIMINATED

**Solution**: No ScopeLift needed. `scoped[A: Unscoped]` requires `Unscoped` evidence directly.
The `Unscoped[A]` constraint prevents leaking resources, scopes, or closures.

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

The **unwrap-before-close** principle in `scoped` method was kept:
```scala
val result: child.$[A] = f.use(child)
val unwrapped: A = child.scoped.run(result)  // BEFORE close
child.close()
```

With zero-cost `$[A] = A`, "unwrapping" is just identity, but the ordering still matters
for finalizer semantics.

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

## Scala 2 Compatibility: Phantom Type Pattern

The scope-local `$[A]` type uses the same module pattern as the existing `@@` type in Scala 2.

### Scala 3: Opaque Type (Zero-Cost)
```scala
final class Child[P <: Scope](val parent: P) extends Scope {
  opaque type $[+A] = A
  protected def $wrap[A](a: A): $[A] = a
  protected def $unwrap[A](sa: $[A]): A = sa
}
```

### Scala 2: Module Pattern to Emulate Opaque Types (Zero-Cost)

Use the module pattern.

```scala
final class Child[P <: Scope](val parent: P) extends Scope {
  // Phantom type - no runtime representation
  type DollarModule {
    type $[+A]
  }
  private val dollarModule: DollarModule = new DollarModule {
    type $[+A] = A

    def $wrap[A](a: A): $[A] = a

  }

 
  // Zero-cost casts (same as Scoped.eager/Scoped.run pattern)
  protected def $wrap[A](a: A): $[A] = dollarModule.$wrap(a)
  protected def $unwrap[A](sa: $[A]): A = dollarModule.$unwrap(sa)
}
```


---

## Files Involved

- `scope/shared/src/main/scala/zio/blocks/scope/Scope.scala` (shared, cross-compiled)
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeVersionSpecific.scala`
- `scope/shared/src/main/scala-2/zio/blocks/scope/ScopeVersionSpecific.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopePrototype.scala` (exploration, to be deleted)

---

## PR #1053 Status

- [x] Fixed unwrap-before-close ordering in scoped method
- [x] Cleaned up failed macro experiment
- [x] Documented new scope-local `$` approach
- [x] Created working prototype in `ScopePrototype.scala`
- [x] Eliminated ScopeLift - replaced with `Unscoped[A]` constraint
- [x] Zero-cost `$[A] = A` design (no thunks, all eager)
- [x] ScopeUser SAM trait for Scala 2/3 compatibility
- [ ] Implement scope-local `$` design in real Scope
- [ ] Update tests
- [ ] Fix scope-examples

## Working Prototype

See `scope/shared/src/main/scala-3/zio/blocks/scope/ScopePrototype.scala`.

Key features:
- `Scope` is a sealed abstract class with abstract `$[+A]` type
- **ZERO-COST**: `$[A] = A` for all scopes (no thunks, no union types)
- `Scope.global` is self-referential with `$[A] = A`
- `Scope.Child[P]` has `opaque type $[+A] = A` (zero-cost)
- `ScopeUser` SAM trait with dependent return type for Scala 2/3 compatibility
- `scoped[A: Unscoped]` requires `Unscoped` evidence to prevent leaking resources/closures
- `scoped.run` is package-private (UNSOUND if exposed)
- `import child._` pattern preserved: brings in `scoped`, `$`, `allocate`, `lower`, `ScopedOps`, `wrapUnscoped`
- **All operations are EAGER** - no laziness needed since `Unscoped[A]` prevents escape

---

## SOUNDNESS INVARIANT: Boundary-Only Escaping

**Escaping (unwrapping `$[A]` to `A`) may ONLY occur at the boundary of `.scoped`.**

This means:
1. The `scoped` method itself controls when unwrapping occurs
2. User code inside the block CANNOT trigger escaping (`scoped.run` is package-private)
3. Escaping happens exactly once, at the moment `scoped` returns, BEFORE finalizers run

Why this matters:
- If escaping can happen anywhere inside the block, a user could unwrap a value, then continue using the scope
- The unwrapped value may capture resources that get freed when the scope closes
- This leads to use-after-free bugs

**INCORRECT (unsound) design:**
```scala
// DON'T DO THIS - allows escaping anywhere
final class ChildScope[P <: Scope](val scope: Scope.Child[P]) {
  def lift[A](a: A)(using l: scope.ScopeLift[A]): l.Out = l(a)  // UNSOUND!
}
```

**CORRECT design:**
The `scoped` method must:
1. Receive the block's return value (`child.$[A]`)
2. Require `Unscoped[A]` evidence (prevents leaking resources, scopes, closures)
3. Unwrap to `A` at boundary, BEFORE closing the scope

---

## Solution: ScopeUser SAM Type + Unscoped Constraint

The solution uses a SAM (Single Abstract Method) trait with dependent return type,
plus an `Unscoped[A]` constraint on the return type:

```scala
@FunctionalInterface
trait ScopeUser[P <: Scope, +A] {
  def use(child: Scope.Child[P]): child.$[A]
}

def scoped[A: Unscoped](f: ScopeUser[self.type, A]): A
```

Lambda syntax `{ child => ... }` works via SAM conversion in both Scala 2 and Scala 3.

Key points:
1. Block MUST return `child.$[A]` (enforced by type signature)
2. **`Unscoped[A]` required** - prevents leaking resources, scopes, or closures
3. `scoped` unwraps via `child.scoped.run(result)` (package-private)
4. Unwrapping happens at boundary, BEFORE scope closes
5. Returns raw `A` - no conditional output type, no ScopeLift needed

For raw `Unscoped` values, an implicit conversion wraps them:
```scala
implicit def wrapUnscoped[A](a: A)(using Unscoped[A]): $[A] = scoped(a)
```

Usage:
```scala
global.scoped { child =>
  import child._
  val x: $[Int] = scoped(100)
  val doubled: $[Int] = (child $ x)(_ * 2)
  doubled  // returns $[Int], unwrapped to Int at boundary
}

// Or with implicit wrap (Int has Unscoped instance):
global.scoped { child =>
  import child._
  42  // implicitly wrapped to $[Int], then unwrapped to Int
}
```

**Why `Unscoped[A]` is critical:** Without it, users could return:
- `child.$[Resource]` → leaks resource after scope closes
- `child.$[Scope.Child[...]]` → leaks the closed child scope
- `child.$[() => Unit]` → leaks closure capturing scope/resources
