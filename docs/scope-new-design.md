# ZIO Blocks Scope — New Design Guide

## Overview

This design provides **compile-time verified resource safety** through existential types. The key insight: each scope execution creates a fresh, unknowable type (via SAM abstract type members), making it impossible to leak permits or access scoped values outside their lifecycle.

---

## Core Data Types

### `@@` — Scoped Values

```scala
opaque infix type @@[+A, +S] = A
```

A value `A @@ S` is a value of type `A` "locked" to scope tag `S`. The opaque type hides all methods on `A`, preventing direct access.

**Extension methods:**

```scala
extension [A, S](scoped: A @@ S) {
  def map[B](f: A => B): Deferred[S, B] = 
    Deferred.Map(Deferred.Pure(scoped), f)
  
  def flatMap[B, T](f: A => B @@ T): Deferred[S & T, B] = 
    Deferred.FlatMap(Deferred.Pure(scoped), f)
}
```

These build a `Deferred` tree — they don't execute. Execution requires a `Scope`.

---

### `Deferred[-Tag, +A]` — Deferred Computations

```scala
sealed trait Deferred[-Tag, +A] {
  def map[B](f: A => B): Deferred[Tag, B] = 
    Deferred.Map(this, f)
  
  def flatMap[B, T](f: A => Deferred[T, B]): Deferred[Tag & T, B] = 
    Deferred.Chain(this, f)
}

object Deferred {
  final case class Pure[S, A](value: A @@ S) extends Deferred[S, A]
  final case class Map[S, A, B](source: Deferred[S, A], f: A => B) extends Deferred[S, B]
  final case class FlatMap[S, T, A, B](source: Deferred[S, A], f: A => B @@ T) extends Deferred[S & T, B]
  final case class Chain[S, T, A, B](source: Deferred[S, A], f: A => Deferred[T, B]) extends Deferred[S & T, B]
}
```

A **free monad** representing operations on scoped values. The tree is pure data — no effects until interpreted by a `Scope`.

**Contravariance in `Tag`:** A `Deferred[Parent, A]` can be executed by any scope with `Tag <: Parent`. Child scopes have more access than parents (their tag is a subtype), so they can execute parent-level deferred computations.

**Note:** There are two flatMap-like operations:
- `@@.flatMap`: Takes `A => B @@ T`, produces `Deferred[S & T, B]`
- `Deferred.flatMap`: Takes `A => Deferred[T, B]`, chains deferred computations

---

### `Scope[ParentTag, Tag <: ParentTag]` — Runtime Scope

```scala
final class Scope[ParentTag, Tag <: ParentTag] private[scope] (
  private val finalizers: Finalizers
) {
  
  /** Create a value in this scope, tagged with this scope's Tag */
  def create[A](factory: Factory[A]): A @@ Tag
  
  /** Apply a function to a scoped value, escaping if possible */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(using 
    Tag <:< S,
    escape: ScopeEscape[B, S]
  ): escape.Out
  
  /** Execute a deferred computation */
  def apply[A, S](deferred: Deferred[S, A])(using 
    Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out
  
  /** Create a child scope with fresh existential tag */
  def scoped[A](f: ScopedFunction[Tag, A]): A
  
  /** Register a finalizer */
  def defer(f: => Unit): Unit
  
  private[scope] def close(): Chunk[Throwable]
}

object Scope {
  type GlobalTag
  
  val global: Scope[GlobalTag, GlobalTag] = new Scope(new Finalizers)
}
```

**Key methods:**

| Method | Purpose |
|--------|---------|
| `create` | Instantiate a factory, tag result with scope's Tag |
| `$` | Apply function to scoped value, escape if Unscoped |
| `apply` | Interpret a `Deferred` tree |
| `scoped` | Create child scope with existential tag |
| `defer` | Register cleanup action |

**The `Tag <:< S` constraint:** Ensures this scope can access values tagged with `S`. Since `Tag <: ParentTag <: ... <: GlobalTag`, a child scope can access all ancestor-tagged values.

---

### `ScopedFunction[ParentTag, +A]` — SAM for Scoped Execution

```scala
abstract class ScopedFunction[ParentTag, +A] {
  type Tag <: ParentTag  // Existential — fresh for each instantiation
  
  def apply(scope: Scope[ParentTag, Tag]): A
}
```

When you write a lambda for `scoped`, Scala creates an anonymous class with a **fresh `Tag` type**. This type is existential — it exists but cannot be named outside the lambda body.

**Implementation of `scoped`:**

```scala
def scoped[A](f: ScopedFunction[Tag, A]): A = {
  val childScope = new Scope[Tag, f.Tag](new Finalizers)
  try f.apply(childScope)
  finally childScope.close()
}
```

---

### `ScopeEscape[A, S]` — Escape Behavior

```scala
trait ScopeEscape[A, S] {
  type Out  // Either A (escaped) or A @@ S (stays scoped)
  def apply(a: A): Out
}
```

Determines whether a result escapes as raw `A` or stays scoped as `A @@ S`:

1. **Global scope:** Everything escapes (global never closes)
2. **`Unscoped` types:** Primitives, String, collections — always escape
3. **Resource types:** Stay scoped as `A @@ S`

---

### `Factory[+A]` — Fully Resolved Recipe

```scala
sealed trait Factory[+A] {
  def make(scope: Scope[?, ?]): A
}
```

A factory has all dependencies resolved. When `make` is called, it:
1. Creates the value
2. Registers finalizers (e.g., `AutoCloseable.close()`)

**Creation:**

```scala
val appFactory: Factory[App] = Factory[App]  // Macro derives from constructor
```

---

### `Wire[-In, +Out]` — Recipe with Dependencies

```scala
sealed trait Wire[-In, +Out] {
  def make(scope: Scope[?, ?], input: Context[In]): Out
}

object Wire {
  final class Shared[-In, +Out] extends Wire[In, Out]
  final class Unique[-In, +Out] extends Wire[In, Out]
}
```

A wire produces `Out` given `Context[In]`. Use `toFactory` to resolve dependencies:

```scala
val wire: Wire[Config, Database] = Wire[Database]  // Macro-derived
val factory: Factory[Database] = wire.toFactory(configWire)
```

---

## How Safety Works

### The Existential Trick

```scala
Scope.global.scoped { scope =>
  // scope: Scope[GlobalTag, scope.Tag]
  // scope.Tag is EXISTENTIAL — cannot be named outside this lambda
  
  val db: Database @@ scope.Tag = scope.create(dbFactory)
  
  db  // Type outside: Database @@ ? — unknowable existential
}
```

Outside the lambda:
- The result type is `Database @@ ?` (existential)
- You cannot call `$` on it because no scope matches `?`
- The value is **useless** outside — compile-time safety!

### Nested Scopes Thread Existentials

```scala
Scope.global.scoped { parent =>
  // parent.Tag: P? <: GlobalTag
  
  val parentDb: Database @@ P? = parent.create(dbFactory)
  
  parent.scoped { child =>
    // child.Tag: C? <: P? (bounded by parent's existential!)
    
    // Child can access parent resources:
    child.$(parentDb)(_.query())  // Works: C? <: P?
    
    val childCache: Cache @@ C? = child.create(cacheFactory)
  }
  
  // parentDb still valid here
  // childCache is gone (existential C? unknowable)
}
```

### Deferred Prevents Eager Access

```scala
val scoped: A @@ Tag = ...

// Outside any scope:
val deferred: Deferred[Tag, B] = scoped.map(_.method())  // Just data!

// To execute:
scope { deferred }  // Only works inside a scope with matching Tag
```

The free monad pattern ensures operations are **descriptions**, not **executions**. Execution is gated by scope availability.

### Covariant Tags in Scope Hierarchy

The tag hierarchy follows:
- `child.Tag <: parent.Tag <: ... <: GlobalTag`

A child scope with `Tag = C` where `C <: P` can access:
- Its own values: `A @@ C`
- Parent values: `A @@ P` (because `C <: P` satisfies `Tag <:< P`)

This enables the natural "child can use parent's resources" behavior.

---

## Usage Examples

### Basic Scope Usage

```scala
Scope.global.scoped { scope =>
  scope.defer { println("Cleanup!") }
  
  val db: Database @@ scope.Tag = scope.create(Factory[Database])
  
  // Access methods via $
  val result: String = scope.$(db)(_.query("SELECT 1"))
  
  println(result)
}
// Prints: "Cleanup!" after block exits
```

### Nested Scopes

```scala
Scope.global.scoped { appScope =>
  val config = appScope.create(Factory[Config])
  
  appScope.scoped { requestScope =>
    val request = requestScope.create(Factory[Request])
    
    // Can access both:
    requestScope.$(config)(_.dbUrl)    // Parent resource
    requestScope.$(request)(_.body)    // Child resource
  }
  // request is gone, config still valid
}
```

### For-Comprehension with Deferred

```scala
Scope.global.scoped { scope =>
  val db: Database @@ scope.Tag = scope.create(Factory[Database])
  val cache: Cache @@ scope.Tag = scope.create(Factory[Cache])
  
  val program: Deferred[scope.Tag, Result] = for {
    conn <- db.map(_.connect())
    data <- cache.map(_.get("key"))
  } yield process(conn, data)
  
  // Execute the deferred computation
  val result: Result = scope { program }
}
```

### Leaking is Useless

```scala
val leaked: Database @@ ? = Scope.global.scoped { scope =>
  scope.create(Factory[Database])
}

// leaked.query(...)        // Won't compile: methods hidden
// scope.$(leaked)(...)     // Won't compile: no scope with matching Tag
// leaked.map(...)          // Creates Deferred[?, B] — can't execute!
```

---

## Summary

| Type | Purpose |
|------|---------|
| `A @@ S` | Value locked to scope tag `S` |
| `Deferred[-Tag, +A]` | Free monad for deferred operations |
| `Scope[ParentTag, Tag]` | Runtime scope with tag hierarchy |
| `ScopedFunction[P, A]` | SAM with existential Tag for safe scoping |
| `Factory[A]` | Fully resolved recipe to create `A` |
| `Wire[-In, +Out]` | Recipe with dependencies |
| `ScopeEscape[A, S]` | Determines escape vs. stay-scoped |

**Core invariants:**
1. `Tag <: ParentTag` — child tags are subtypes of parent tags
2. Existential tags are unknowable outside their lambda
3. `Deferred` is pure data until interpreted by matching `Scope`
4. `ScopeEscape` controls what crosses scope boundaries
