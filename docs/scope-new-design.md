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
  def map[B](f: A => B): Scoped[S, B] = Scoped { _ => @@.scoped(f(@@.unscoped(scoped))) }    
  
  def flatMap[B, T](f: A => B @@ T): Scoped[S & T, B] = Scoped { _ => @@.scoped(@@.unscoped(f(@@.unscoped(scoped)))) }
}
```

These build a `Scoped` tree — they don't execute. Execution requires a `Scope`.

---

### `Scoped[-Tag, +A]` — Scoped Computations

```scala
final case class Scoped[-Tag, +A](execute: Scope[?, Tag] => A) {
  def map[B](f: A => B): Scoped[Tag, B] = ...
  
  def flatMap[B, T](f: A => B @@ T): Scoped[Tag & T, B] = ...
}
```

**Contravariance in `Tag`:** A `Scoped[Parent, A]` can be executed by any scope with `Tag <: Parent`. Child scopes have more access than parents (their tag is a subtype), so they can execute parent-level Scoped computations.


---

### `Scope[ParentTag, Tag <: ParentTag]` — Runtime Scope


```scala
final class Scope[ParentTag, Tag <: ParentTag] private[scope] (
  private val finalizers: Finalizers
) {
  
  /** Create a value in this scope, tagged with this scope's Tag */
  def create[A](factory: Factory[A]): A @@ Tag = @@.scoped(factory.make(this))
  
  /** Apply a function to a scoped value, escaping if possible */
  def $[A, B, S](scoped: A @@ S)(f: A => B)(using 
    Tag <:< S,
    escape: ScopeEscape[B, S]
  ): escape.Out = self { scoped.map(f) }
  
  /** Execute a Scoped computation */
  def apply[A, S](scoped: Scoped[S, A])(using 
    Tag <:< S,
    escape: ScopeEscape[A, S]
  ): escape.Out = escape(@@.unscoped(scoped.execute(this)))
  
  /** Create a child scope with fresh existential tag*/
  def scoped[A](f: ScopedFunction[Tag, A]): A = {
    val childScope = new Scope[Tag, f.Tag](new Finalizers)
    try f.apply(childScope)
    finally childScope.close() // TODO: Don't lose errors!
  }
  
  /** Register a finalizer */
  def defer(f: => Unit): Unit = finalizers.add(f)
  
  private[scope] def close(): Chunk[Throwable] = finalizers.close()
}

object Scope {
  type GlobalTag // = Any???
  
  val global: Scope[GlobalTag, GlobalTag] = new Scope(new Finalizers)
}
```

**Key methods:**

| Method | Purpose |
|--------|---------|
| `create` | Instantiate a factory, tag result with scope's Tag |
| `$` | Apply function to scoped value, escape if Unscoped |
| `apply` | Interpret a `Scoped` tree |
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

### Scoped Prevents Eager Access

```scala
val scoped: A @@ Tag = ...

// Outside any scope:
val deferred: Scoped[Tag, B] = scoped.map(_.method())  // Just data!

// To execute:
scope { deferred }  // Only works inside a scope with matching Tag
```

The scoped monad ensures operations are **descriptions**, not **executions**. Execution is gated by scope availability.

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

### For-Comprehension with Scoped

```scala
Scope.global.scoped { scope =>
  val db: Database @@ scope.Tag = scope.create(Factory[Database])
  val cache: Cache @@ scope.Tag = scope.create(Factory[Cache])
  
  val program: Scoped[scope.Tag, Result] = for {
    conn <- db.map(_.connect())
    data <- cache.map(_.get("key"))
  } yield process(conn, data)
  
  // Execute the Scoped computation
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
// leaked.map(...)          // Creates Scoped[?, B] — can't execute!
```

---

## Summary

| Type | Purpose |
|------|---------|
| `A @@ S` | Value locked to scope tag `S` |
| `Scoped[-Tag, +A]` | Free monad for Scoped operations |
| `Scope[ParentTag, Tag]` | Runtime scope with tag hierarchy |
| `ScopedFunction[P, A]` | SAM with existential Tag for safe scoping |
| `Factory[A]` | Fully resolved recipe to create `A` |
| `Wire[-In, +Out]` | Recipe with dependencies |
| `ScopeEscape[A, S]` | Determines escape vs. stay-scoped |

**Core invariants:**
1. `Tag <: ParentTag` — child tags are subtypes of parent tags
2. Existential tags are unknowable outside their lambda
3. `Scoped` is pure data until interpreted by matching `Scope`
4. `ScopeEscape` controls what crosses scope boundaries
