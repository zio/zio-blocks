# ZIO Blocks — Scope (compile-time safe resource management)

This module provides **compile-time verified resource safety** using **existential scope tags** and an **opaque scoped value type**. The design ensures that values allocated in a child scope **cannot be used after that scope closes**, and—crucially—cannot even be *typed* in a way that allows them to leak to an outer scope.

If you've used `try/finally`, `Using`, or ZIO `Scope`, this library is in the same space, but it focuses on **zero-runtime-overhead**, **compile-time gating**, and **simple synchronous lifecycle management**.

---

## Table of contents

- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
  - [1) `Scope[ParentTag, Tag]`](#1-scopeparenttag-tag)
  - [2) Scoped values: `A @@ S`](#2-scoped-values-a--s)
  - [3) `Resource[A]`: acquisition + finalization](#3-resourcea-acquisition--finalization)
  - [4) `Scoped[Tag, A]`: deferred computations](#4-scopedtag-a-deferred-computations)
  - [5) `Wire[-In, +Out]`: dependency recipes](#5-wire-in--out-dependency-recipes)
  - [6) `ScopeEscape[A, S]`: when results may "escape"](#6-scopeescapea-s-when-results-may-escape)
- [Safety model (why leaking is prevented)](#safety-model-why-leaking-is-prevented)
- [Usage examples](#usage-examples)
  - [Allocating and using a resource](#allocating-and-using-a-resource)
  - [Nested scopes (child can use parent, not vice versa)](#nested-scopes-child-can-use-parent-not-vice-versa)
  - [Building a `Scoped` program (map/flatMap)](#building-a-scoped-program-mapflatmap)
  - [Registering cleanup manually with `defer`](#registering-cleanup-manually-with-defer)
  - [Dependency injection with `Wire` + `Context`](#dependency-injection-with-wire--context)
  - [Interop escape hatch: `leak`](#interop-escape-hatch-leak)
- [API reference](#api-reference)
  - [`Scope`](#scope)
  - [`Resource`](#resource)
  - [`@@`](#-1)
  - [`Scoped`](#scoped)
  - [`Wire`](#wire)
  - [`ScopeEscape` and `Unscoped`](#scopeescape-and-unscoped)

---

## Quick start

```scala
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { (using scope) =>
  val db = Resource(new Database).allocate

  val result: String =
    scope.$(db)(_.query("SELECT 1"))

  println(result)
}
```

Key things to notice:

- `allocate` returns a **scoped** value: `Database @@ scope.Tag`
- You **cannot** call `db.query(...)` directly
- You must go through `scope.$(db)(...)` (or build a `Scoped` computation)
- When the `scoped { ... }` block exits, all finalizers run **LIFO** and exceptions are handled safely

---

## Core concepts

### 1) `Scope[ParentTag, Tag]`

A `Scope` manages finalizers and ties values to a *type-level identity* called a **Tag**.

- `Scope[ParentTag, Tag]` has **two** type parameters:
  - `ParentTag`: the parent scope's tag (capability boundary)
  - `Tag <: ParentTag`: this scope's unique identity (used to tag values)

Every `Scope` instance also exposes a *path-dependent* member type:

```scala
type Tag = Tag0
```

So in code you'll typically write:

```scala
Scope.global.scoped { (using scope) =>
  val x: Something @@ scope.Tag = ???
}
```

#### Global scope

`Scope.global` is the root of the tag hierarchy:

```scala
type Scope.GlobalTag
lazy val Scope.global: Scope[GlobalTag, GlobalTag]
```

The global scope is intended to live for the lifetime of the process (finalizers run on JVM shutdown).

---

### 2) Scoped values: `A @@ S`

`A @@ S` is an **opaque type** (Scala 3) representing "a value of type `A` that is locked to scope tag `S`".

Important properties:

- **Zero overhead** at runtime (it's represented as `A`)
- The opaque type **hides methods on `A`**, so you can't directly call `a.method` on a scoped value
- You must use `scope.$(...)` or build a `Scoped` computation via `map`/`flatMap`

This is a core mechanism preventing accidental misuse and leakage.

---

### 3) `Resource[A]`: acquisition + finalization

`Resource[A]` describes how to **acquire** a value and how to **release** it when the scope closes.

It is intentionally lazy: you describe *what to do*, and allocation happens only through:

```scala
scope.allocate(resource)
```

Common constructors:

- `Resource(a)`  
  Wraps a by-name value; if it's `AutoCloseable`, `close()` is registered automatically.
- `Resource.acquireRelease(acquire)(release)`  
  Explicit lifecycle.
- `Resource.fromAutoCloseable(thunk)`  
  A type-safe helper for `AutoCloseable`.
- `Resource[T]` (Scala 3 macro)  
  Derives a resource from a constructor (and registers `close()` if applicable).

Resource "sharing" vs "uniqueness":
- `Resource.Unique[A]`: fresh instance per allocation (typical when you create resources directly)
- `Resource.Shared[A]`: memoized within a single `Wire` graph (typically produced via `Wire.toResource`)

---

### 4) `Scoped[Tag, A]`: deferred computations

`Scoped[-Tag, +A]` represents a computation that will produce an `A` but can only be **executed by a scope whose Tag is a subtype of `Tag`**.

- You can create `Scoped` computations by calling `map`/`flatMap` on `A @@ S`
- Or by constructing them directly (advanced/internal-style) via `Scoped.create(() => ...)`

Execution happens via:

```scala
scope(scopedComputation)
```

`Scoped` is contravariant in `Tag`, enabling a child scope (more specific tag) to execute computations requiring a parent tag.

---

### 5) `Wire[-In, +Out]`: dependency recipes

`Wire` is a recipe for building services, commonly used for dependency injection.

- `In` is the required dependencies (provided as a `Context[In]`)
- `Out` is the produced value (a single service type)

Wires come in two flavors:
- `Wire.Shared`: memoized within a scope (default)
- `Wire.Unique`: fresh instance each time

A `Wire` can be converted into a `Resource` once you have its dependencies:

```scala
val r: Resource[Out] = wire.toResource(deps)
val out: Out @@ scope.Tag = scope.allocate(r)
```

Macros in `zio.blocks.scope` package object:
- `shared[T]`: derive a shared wire from the constructor
- `unique[T]`: derive a unique wire

---

### 6) `ScopeEscape[A, S]`: when results may "escape"

When you access a scoped value via:

- `scope.$(value)(f)` or
- `scope(scopedComputation)`

…the return type is controlled by `ScopeEscape[A, S]`.

It decides whether the result is returned as:
- raw `A` (escaped), or
- scoped `A @@ S` (still tracked)

Priority rules:

1. **Global scope** (`S =:= Scope.GlobalTag`): everything escapes as raw `A`
2. **Unscoped types**: escape as raw `A` for any scope
3. Otherwise: treated as "resourceful" and remains scoped as `A @@ S`

This gives an ergonomic default: pure data flows out, but resource-like values remain tracked.

---

## Safety model (why leaking is prevented)

This library prevents resource leakage via *two reinforcing mechanisms*:

### A) Existential child tags (fresh, unnameable types)

Child scopes are created with:

```scala
scope.scoped { child => ... }
```

The type of `child` is:

```scala
Scope[scope.Tag, ? <: scope.Tag]
```

That `?` is an **existential tag**: it is fresh for each invocation and cannot be named outside the lambda.

Result: if you allocate in the child scope, the value's type includes the child's existential tag, and **you cannot return it** in a way that the parent can later use.

This is verified by compile-time tests in the repository (`CompileTimeSafetySpec`).

### B) Tag invariance + opaque `@@` prevents subtyping escape

The `@@` type is defined as:

```scala
opaque type @@[+A, S] = A
```

- The tag parameter `S` is **invariant** (not `+S` or `-S`)
- You cannot widen `A @@ childTag` to `A @@ parentTag` via subtyping
- Additionally, you cannot call methods directly on a scoped value, which prevents accidental misuse without scope proof

### C) Controlled access through `scope.$` and `scope.apply`

To use a scoped value you must provide a scope that proves it can access it:

```scala
(using ev: scope.Tag <:< S)
```

This evidence exists precisely when the accessing scope's tag is a subtype of the value's tag. Since child tags are subtypes of parent tags, **children can use parent resources**, but not the other way around.

### D) Finalizer correctness (exceptions + suppression)

When a scope closes:

- finalizers run in **LIFO** order
- all finalizers run even if some throw
- if the main block throws, finalizer exceptions are added as **suppressed** on the primary exception
- if the block succeeds but finalizers throw, the first finalizer error is thrown and the rest are suppressed

This behavior is also covered by tests (`ScopeNewApiSpec`).

---

## Usage examples

### Allocating and using a resource

```scala
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("closed")
}

Scope.global.scoped { scope =>
  val db = scope.allocate(Resource(new Database))
  val s  = scope.$(db)(_.query("SELECT 1")) // returns String (escapes)
  println(s)
}
```

Why `String` "escapes": it is considered `Unscoped` (safe data), so it returns as raw `String` instead of `String @@ scope.Tag`.

---

### Nested scopes (child can use parent, not vice versa)

```scala
Scope.global.scoped { parent =>
  val db = parent.allocate(Resource(new Database))

  parent.scoped { child =>
    val ok = child.$(db)(_.query("child can use parent"))
    println(ok)
  }

  // Not allowed: parent cannot use child-created resources after child closes
  // (this will not compile)
  //
  // val fromChild = parent.scoped { child =>
  //   child.allocate(Resource(new Database))
  // }
  // parent.$(fromChild)(_.query("nope"))
}
```

---

### Building a `Scoped` program (map/flatMap)

You can build deferred computations from scoped values:

```scala
Scope.global.scoped { scope =>
  val db = scope.allocate(Resource(new Database))

  val program: Scoped[scope.Tag, String] =
    for {
      a <- db.map(_.query("SELECT 1"))
      b <- db.map(_.query("SELECT 2"))
    } yield s"$a | $b"

  val result: String = scope(program)
  println(result)
}
```

Notes:

- `db.map` returns a `Scoped` computation
- `scope(program)` is the interpreter: it runs the thunk using the scope's permissions
- The type system ensures the computation cannot run without an appropriate scope

---

### Registering cleanup manually with `defer`

```scala
Scope.global.scoped { (using scope) =>
  val handle = new java.io.ByteArrayInputStream(Array[Byte](1,2,3))

  scope.defer { handle.close() }

  // use handle (unscoped here; you can choose to wrap as Resource if preferred)
}
```

There is also a package-level helper that uses an implicit `Finalizer`:

```scala
import zio.blocks.scope._

Scope.global.scoped { (using scope) =>
  given Finalizer = scope
  defer { println("cleanup") }
}
```

Note: `defer` requires only a `Finalizer`, not a full `Scope`. This allows cleanup
registration in contexts where only finalization capability is needed.

---

### Dependency injection with `Wire` + `Context`

Suppose:

```scala
final case class Config(debug: Boolean)
```

Derive a wire:

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

val w: Wire.Shared[Boolean, Config] = shared[Config]
val deps = Context[Boolean](true)

Scope.global.scoped { scope =>
  val cfg = scope.allocate(w.toResource(deps)) // Config @@ scope.Tag
  val debug = scope.$(cfg)(_.debug)            // Boolean (escapes)
  println(debug)
}
```

Choose sharing vs uniqueness:

```scala
val ws = shared[Config] // memoized within a scope
val wu = unique[Config] // new instance for each use
```

---

### Interop escape hatch: `leak`

Sometimes you must hand a raw value to code that cannot work with `@@` types.

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  val db = scope.allocate(Resource(new Database))

  val raw: Database = leak(db) // emits a compiler warning
  // thirdParty(raw)
}
```

**Warning:** leaking bypasses compile-time guarantees. The value may be used after its scope closes. Use only when unavoidable.

---

## API reference

### `Scope`

```scala
final class Scope[ParentTag, Tag <: ParentTag] {
  type Tag

  def allocate[A](resource: Resource[A]): A @@ Tag
  def defer(f: => Unit): Unit

  // Scala 3-only extensions (see ScopeVersionSpecific):
  def $[A, B, S](scoped: A @@ S)(f: A => B)(
    using ev: this.Tag <:< S,
          escape: ScopeEscape[B, S]
  ): escape.Out

  def apply[A, S](scoped: Scoped[S, A])(
    using ev: this.Tag <:< S,
          escape: ScopeEscape[A, S]
  ): escape.Out

  def scoped[A](f: Scope[this.Tag, ? <: this.Tag] ?=> A): A
}
```

#### `Scope.global`

```scala
object Scope {
  type GlobalTag
  lazy val global: Scope[GlobalTag, GlobalTag]
}
```

- Global scope finalizers run on JVM shutdown.
- Values tagged with `Scope.GlobalTag` always escape as raw values via `ScopeEscape`.

---

### `Resource`

```scala
sealed trait Resource[+A]

object Resource {
  def apply[A](value: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]

  // internal / produced by wires:
  def shared[A](f: Finalizer => A): Resource.Shared[A]
  def unique[A](f: Finalizer => A): Resource.Unique[A]

  final class Shared[+A] extends Resource[A]
  final class Unique[+A] extends Resource[A]
}
```

Behavioral notes:

- `Resource(value)` auto-registers `close()` iff `value` is `AutoCloseable`
- `acquireRelease` registers `release` as a finalizer
- Resources are only "interpreted" by `Scope.allocate`, which tags the result

---

### `@@`

```scala
opaque type @@[+A, S] = A

object @@ {
  inline def scoped[A, S](a: A): A @@ S

  extension [A, S](scoped: A @@ S) {
    def map[B](f: A => B): Scoped[S, B]
    def flatMap[B, T](f: A => B @@ T): Scoped[S & T, B]

    def _1[X, Y](using A =:= (X, Y)): X @@ S
    def _2[X, Y](using A =:= (X, Y)): Y @@ S
  }
}
```

- Opaque: prevents direct method access on `A`
- `map`/`flatMap` build deferred `Scoped` computations

---

### `Scoped`

```scala
final case class Scoped[-Tag, +A] private (private val executeFn: () => A) {
  def map[B](f: A => B): Scoped[Tag, B]
  def flatMap[B, T](f: A => B @@ T): Scoped[Tag & T, B]
}

object Scoped {
  def create[Tag, A](f: () => A): Scoped[Tag, A]
}
```

- Contravariant in `Tag`: a child scope can run parent-level computations
- Execution is intentionally gated through `Scope.apply`

---

### `Wire`

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
  def toResource(deps: zio.blocks.context.Context[In]): Resource[Out]
}

object Wire {
  final class Shared[-In, +Out] extends Wire[In, Out]
  final class Unique[-In, +Out] extends Wire[In, Out]

  def apply[T](t: T): Wire.Shared[Any, T] // inject a pre-existing value
}
```

Also available at package level:

```scala
transparent inline def shared[T]: Wire.Shared[?, T]
transparent inline def unique[T]: Wire.Unique[?, T]
```

Wires are typically derived from constructors, using `Context` to provide dependencies.

---

### `ScopeEscape` and `Unscoped`

```scala
trait ScopeEscape[A, S] {
  type Out
  def apply(a: A): Out
}

object ScopeEscape {
  given globalScope[A]: ScopeEscape[A, Scope.GlobalTag] { type Out = A }
  given unscoped[A, S](using Unscoped[A]): ScopeEscape[A, S] { type Out = A }
  // fallback:
  given resourceful[A, S]: ScopeEscape[A, S] { type Out = A @@ S }
}
```

Conceptually:

- Mark *data-like* types as `Unscoped` so they can leave scopes as raw values.
- Keep *resource-like* types scoped so they can't leak accidentally.

(See the `Unscoped` typeclass in the codebase for how types are classified.)

---

## Mental model recap

- Use `Scope.global.scoped { scope => ... }` to create a safe region.
- Allocate managed things with `scope.allocate(Resource(...))`.
- Use managed things only via `scope.$(value)(...)` or via `Scoped` computations.
- Nest with `scope.scoped { child => ... }` to create an even tighter lifetime.
- Trust the compiler: if it doesn't typecheck, it would have been unsafe at runtime.
