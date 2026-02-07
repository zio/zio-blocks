# How Scope Is Changing in the New Design

This guide explains how the Scope library is evolving from its current design to a new architecture with stronger compile-time safety guarantees. If you're familiar with the current implementation, this will help you understand what's changing and why.

---

## Executive Summary

| Aspect | Current Design | New Design |
|--------|---------------|------------|
| Safety mechanism | `Scope.Permit[S]` token | Existential types via SAM |
| Permit leakage | Possible (can return from `.use`) | Impossible (existential unknowable) |
| Scope structure | HList with `Context[H]` at each level | Two-parameter `Scope[ParentTag, Tag]` |
| Resource creation | `injected[T](wires...)` | `scope.create(factory)` |
| Method access | `scoped $ (_.method())` | `scope.$(scoped)(_.method())` |
| `map`/`flatMap` | Eager execution | Scoped (free monad) |
| Wire output | `Context[Out]` (multiple values) | `Out` (single value) |

---

## What's Staying the Same

### `@@` — Opaque Scoped Type

The core `A @@ S` opaque type remains unchanged:

```scala
opaque infix type @@[+A, +S] = A
```

Values are still "locked" to a scope tag, hiding all methods on `A`.

### `ScopeEscape` — Escape Behavior

The typeclass determining whether values escape or stay scoped is unchanged:

```scala
trait ScopeEscape[A, S] {
  type Out  // Either A (escaped) or A @@ S (stays scoped)
  def apply(a: A): Out
}
```

Priority remains:
1. Global scope → escapes
2. `Unscoped` types → escapes  
3. Resource types → stays scoped

### `Unscoped` — Safe Types

The marker typeclass for types that don't hold resources is unchanged.

### Tag Hierarchy

Child tags are still subtypes of parent tags, enabling children to access parent resources:
- Current: `type Tag <: tail.Tag`
- New: `Tag <: ParentTag` (explicit type parameter)

---

## What's Changing

### 1. Scope Structure

**Current:** HList-like structure mixing scope identity with service storage

```scala
sealed trait Scope {
  type Tag
  def defer(finalizer: => Unit): Unit
}

final class ::[+H, +T <: Scope](
  val head: Context[H],    // Services stored here
  val tail: T,
  private val finalizers: Finalizers
) extends Scope {
  type Tag <: tail.Tag
}

type Has[+T] = ::[T, Scope]
```

**New:** Clean separation — scope manages lifecycle, not service storage

```scala
final class Scope[ParentTag, Tag <: ParentTag](
  private val finalizers: Finalizers
) {
  def create[A](factory: Factory[A]): A @@ Tag
  def scoped[A](f: ScopedFunction[Tag, A]): A
  def defer(f: => Unit): Unit
  // ...
}
```

**Why:** The current design conflates two concerns: scope identity (Tag) and service availability (Context). The new design separates them — scopes manage lifecycle, factories create values.

---

### 2. Safety Mechanism

**Current:** `Scope.Permit[S]` — a forgeable token

```scala
sealed abstract class Permit[S] private[scope] ()

// Inside .use:
given Scope.Permit[self.Tag] = Scope.Permit[self.Tag]

// Problem: can leak!
val leaked = scope.use { summon[Scope.Permit[scope.Tag]] }
```

The permit can be returned from `.use`, defeating safety.

**New:** Existential types via SAM — unforgeable by construction

```scala
abstract class ScopedFunction[ParentTag, +A] {
  type Tag <: ParentTag  // Fresh existential per instantiation
  def apply(scope: Scope[ParentTag, Tag]): A
}

// Inside scoped block:
scope.scoped { childScope =>
  // childScope.Tag is existential — cannot be named outside!
}
```

When you try to return something tagged with the existential, it becomes `? <: ParentTag` — useless outside the lambda because no scope can match an unknowable type.

**Why:** The current `Permit` approach has a fundamental flaw: the permit is a normal value that can escape. Existential types are unknowable by construction — the compiler enforces safety, not runtime checks.

---

### 3. The `.use` / `.scoped` API

**Current:** Context function providing implicit parameters

```scala
// Scala 3
def use[B](f: (Scope.Permit[self.Tag], Context[Head] @@ self.Tag, self.type) ?=> B): B

// Usage
closeable.use {
  val app = $[App]           // Uses implicit Permit + Context
  app $ (_.run())
}
```

**New:** SAM with scope parameter

```scala
def scoped[A](f: ScopedFunction[Tag, A]): A = {
  val childScope = new Scope[Tag, f.Tag](new Finalizers)
  try f.apply(childScope)
  finally childScope.close()
}

// Usage
scope.scoped { s =>
  val app = s.create(Factory[App])
  s.$(app)(_.run())
}
```

**Why:** The SAM approach creates a fresh existential type for each lambda, making leakage impossible. The scope is passed explicitly, which is actually clearer.

---

### 4. Service Retrieval

**Current:** `$[T]` macro searches implicit context

```scala
closeable.use {
  val db = $[Database]           // Database @@ Tag
  db $ (_.query("SELECT ..."))   // String
}
```

**New:** `scope.create(factory)` — explicit creation

```scala
scope.scoped { s =>
  val db = s.create(Factory[Database])   // Database @@ s.Tag
  s.$(db)(_.query("SELECT ..."))         // String
}
```

**Why:** No more magic implicit search. Creation is explicit and tied to a specific scope. The factory contains all wiring logic.

---

### 5. Method Access on Scoped Values

**Current:** `$` is an extension method on `@@`, requires implicit `Permit`

```scala
extension [A, S](scoped: A @@ S) {
  inline infix def $[B](inline f: A => B)(using
    permit: Scope.Permit[S]
  )(using
    u: ScopeEscape[B, S]
  ): u.Out
}

// Usage
db $ (_.query("..."))
```

**New:** `$` is a method on `Scope`, takes scoped value as parameter

```scala
def $[A, B, S](scoped: A @@ S)(f: A => B)(using 
  Tag <:< S,
  escape: ScopeEscape[B, S]
): escape.Out

// Usage
scope.$(db)(_.query("..."))
```

**Why:** The scope itself is the capability. You can only call `$` if you have a scope, and the `Tag <:< S` constraint ensures the scope can access that tag.

---

### 6. `map` and `flatMap`

**Current:** Eager execution, returns `B @@ S` or `B @@ (S & T)`

```scala
inline def map[B](inline f: A => B): B @@ S = f(scoped)

inline def flatMap[B, T](inline f: A => B @@ T): B @@ (S & T) = f(scoped)
```

These execute immediately, which is problematic — you could use `map` to access a value outside any scope.

**New:** Scoped execution via free monad

```scala
def map[B](f: A => B): Scoped[S, B] = 
  Scoped.Map(Scoped.Pure(scoped), f)

def flatMap[B, T](f: A => B @@ T): Scoped[S & T, B] = 
  Scoped.FlatMap(Scoped.Pure(scoped), f)
```

To execute, you need a scope:

```scala
scope.scoped { s =>
  val program = for {
    a <- scopedA.map(_.process())
    b <- scopedB.map(_.transform())
  } yield (a, b)
  
  s { program }  // Execute via scope.apply
}
```

**Why:** The current design allows `map` to execute anywhere, even outside scopes. The new design makes all operations on scoped values produce inert `Scoped` trees that require a scope to interpret.

---

### 7. Wire Output Type

**Current:** Wire produces `Context[Out]` (can contain multiple values)

```scala
sealed trait Wire[-In, +Out] {
  def construct(implicit scope: Scope.Has[In]): Context[Out]
}
```

**New:** Wire produces a single `Out` value

```scala
sealed trait Wire[-In, +Out] {
  def make(scope: Scope[?, ?], input: Context[In]): Out
}
```

**Why:** Wires should produce one thing. If you need multiple services, use multiple wires or a tuple type. This simplifies the mental model.

---

### 8. Factory — New Type

**Current:** No explicit factory type; `injected[T](wires...)` does everything

```scala
Scope.global.injected[App](shared[Database], shared[Config]).use { ... }
```

**New:** `Factory[A]` — fully resolved recipe

```scala
sealed trait Factory[+A] {
  def make(scope: Scope[?, ?]): A
}

// Usage
val appFactory = Factory[App]  // Macro resolves all dependencies
scope.create(appFactory)
```

**Why:** Separates "resolving dependencies" from "executing in a scope". Factories can be created anywhere, stored, passed around. They only execute when given to `scope.create`.

---

## Migration Patterns

### Before: Creating a scoped app

```scala
// Current
Scope.global.injected[App](shared[Database], shared[Config]).use {
  val app = $[App]
  app $ (_.run())
}
```

### After: Creating a scoped app

```scala
// New
Scope.global.scoped { scope =>
  val app = scope.create(Factory[App])
  scope.$(app)(_.run())
}
```

### Before: Nested scopes

```scala
// Current
Scope.global.injected[Config](shared[Config]).use {
  val config = $[Config]
  
  Scope.global.injected[Database](shared[Database]).use {
    val db = $[Database]
    db $ (_.query("..."))
  }
}
```

### After: Nested scopes

```scala
// New
Scope.global.scoped { appScope =>
  val config = appScope.create(Factory[Config])
  
  appScope.scoped { requestScope =>
    val db = requestScope.create(Factory[Database])
    requestScope.$(db)(_.query("..."))
  }
}
```

### Before: For-comprehension

```scala
// Current (eager, potentially unsafe)
val result: Result @@ Tag = for {
  a <- scopedA
  b <- scopedB
} yield process(a, b)
```

### After: For-comprehension

```scala
// New (Scoped, safe)
val program: Scoped[Tag, Result] = for {
  a <- scopedA.map(identity)
  b <- scopedB.map(identity)
} yield process(a, b)

val result: Result = scope { program }  // Execute in scope
```

---

## Summary of Breaking Changes

1. **`Scope.Permit`** — Removed entirely; existential types provide safety
2. **`Scope.::`** — Replaced by `Scope[ParentTag, Tag]`
3. **`Scope.Has[T]`** — Removed; no longer store services in scope
4. **`$[T]` macro** — Replaced by `scope.create(Factory[T])`
5. **`scoped $ f`** — Replaced by `scope.$(scoped)(f)`
6. **`@@.map`/`@@.flatMap`** — Now return `Scoped` instead of `@@`
7. **`.use`** — Replaced by `.scoped` with SAM parameter
8. **`Wire.construct`** — Returns `Out` instead of `Context[Out]`
9. **`injected[T](wires...)`** — Replaced by `scope.create(factory)`

---

## Why This Matters

The fundamental improvement is **compile-time safety that cannot be circumvented**:

| Scenario | Current | New |
|----------|---------|-----|
| Leak permit from `.use` | ✅ Compiles, unsafe | ❌ Impossible |
| Use `map` outside scope | ✅ Compiles, unsafe | ✅ Compiles, but produces inert `Scoped` |
| Access child resource from parent | ❌ Compile error | ❌ Compile error |
| Access parent resource from child | ✅ Works | ✅ Works |

The new design achieves what the current design promises but can't fully deliver: **if it compiles, resource access is safe**.
