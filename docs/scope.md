# ZIO Blocks — Scope (compile-time safe resource management)

`zio.blocks.scope` provides **compile-time verified resource safety** for synchronous code by tagging values with an unnameable, type-level **scope identity**. Values allocated in a scope can only be used when you hold a compatible `Scope`, and values allocated in a *child* scope cannot be returned to the parent in a usable form.

If you've used `try/finally`, `Using`, or ZIO `Scope`, this library lives in the same problem space, but it focuses on:

- **Compile-time prevention of scope leaks**
- **Zero runtime overhead for the scoped tag** (`A @@ S` is represented as `A`)
- **Simple, synchronous lifecycle management** (finalizers run LIFO on scope close)

---

## Table of contents

- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
  - [1) `Scope[ParentTag, Tag]`](#1-scopeparenttag-tag)
  - [2) Scoped values: `A @@ S`](#2-scoped-values-a--s)
  - [3) `Resource[A]`: acquisition + finalization](#3-resourcea-acquisition--finalization)
  - [4) `Scoped[Tag, A]`: deferred computations](#4-scopedtag-a-deferred-computations)
  - [5) `ScopeEscape` and `Unscoped`: what may escape](#5-scopeescape-and-unscoped-what-may-escape)
  - [6) `Wire[-In, +Out]`: dependency recipes](#6-wire-in-out-dependency-recipes)
  - [7) `Wireable[Out]`: DI for traits/abstract classes](#7-wireableout-di-for-traitsabstract-classes)
- [Safety model (why leaking is prevented)](#safety-model-why-leaking-is-prevented)
- [Usage examples](#usage-examples)
  - [Allocating and using a resource](#allocating-and-using-a-resource)
  - [Nested scopes (child can use parent, not vice versa)](#nested-scopes-child-can-use-parent-not-vice-versa)
  - [Building a `Scoped` program (map/flatMap)](#building-a-scoped-program-mapflatmap)
  - [Registering cleanup manually with `defer`](#registering-cleanup-manually-with-defer)
  - [Dependency injection with `Wire` + `Context`](#dependency-injection-with-wire--context)
  - [Supplying dependencies with `Resource.from[T](wire1, wire2, ...)`](#supplying-dependencies-with-resourcefromtwire1-wire2-)
  - [DI for traits via `Wireable`](#di-for-traits-via-wireable)
  - [Interop escape hatch: `leak`](#interop-escape-hatch-leak)
- [API reference (selected)](#api-reference-selected)

---

## Quick start

```scala
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  val db: Database @@ scope.Tag =
    scope.allocate(Resource(new Database))

  val result: String =
    scope.$(db)(_.query("SELECT 1"))

  println(result)
}
```

Key things to notice:

- `scope.allocate(...)` returns a **scoped** value: `Database @@ scope.Tag`
- You **cannot** call `db.query(...)` directly (methods are intentionally hidden)
- You must use `scope.$(db)(...)` or build a `Scoped` computation
- When the `scoped { ... }` block exits, finalizers run **LIFO** and errors are handled safely

---

## Core concepts

### 1) `Scope[ParentTag, Tag]`

A `Scope` manages finalizers and ties values to a *type-level identity* called a **Tag**.

- `Scope[ParentTag, Tag]` has **two** type parameters:
  - `ParentTag`: the parent scope's tag (capability boundary)
  - `Tag <: ParentTag`: this scope's unique identity (used to tag values)

Every `Scope` also exposes a *path-dependent* member type:

```scala
type Tag = Tag0
```

So in code you'll typically write:

```scala
Scope.global.scoped { scope =>
  val x: Something @@ scope.Tag = ???
}
```

#### Global scope

`Scope.global` is the root of the tag hierarchy:

```scala
object Scope {
  type GlobalTag
  lazy val global: Scope[GlobalTag, GlobalTag]
}
```

- The global scope is intended to live for the lifetime of the process.
- Its finalizers run on JVM shutdown.
- Values allocated in `Scope.global` typically **escape** as raw values via `ScopeEscape` (see below).

---

### 2) Scoped values: `A @@ S`

`A @@ S` means: "a value of type `A` that is locked to scope tag `S`".

- **Runtime representation:** just `A` (no wrapper allocation)
- **Key effect:** methods on `A` are hidden, so you can't call `a.method` without proving scope access
- **Access paths:**
  - `scope.$(a)(f)` to use a scoped value immediately
  - `a.map / a.flatMap` to build a `Scoped` computation, then run it via `scope(scoped)`

#### Scala 3 vs Scala 2 note

- In **Scala 3**, `@@` is implemented as an `opaque` type.
- In **Scala 2**, the library emulates the same "opaque-like" behavior using the *module pattern* (still zero-overhead at runtime).

---

### 3) `Resource[A]`: acquisition + finalization

`Resource[A]` describes how to **acquire** an `A` and how to **release** it when a scope closes. It is intentionally lazy: you *describe what to do*, and allocation happens only through:

```scala
scope.allocate(resource)
```

Common constructors:

- `Resource(a)`
  - Wraps a by-name value; if it's `AutoCloseable`, `close()` is registered automatically.
- `Resource.acquireRelease(acquire)(release)`
  - Explicit lifecycle.
- `Resource.fromAutoCloseable(thunk)`
  - A type-safe helper for `AutoCloseable`.
- `Resource.from[T]` (macro)
  - Derives a resource from `T`'s constructor.
  - If `T` is `AutoCloseable`, registers `close()` automatically.
  - If `T` has dependencies, either:
    - use `Wire` + `toResource(deps)`, or
    - use `Resource.from[T](wire1, wire2, ...)` (see below).

#### Resource "sharing" vs "uniqueness"

`Resource` has two important internal flavors:

- `Resource.Unique[A]`
  - Produces a **fresh** instance every time you allocate it (typical for `Resource(...)`, `acquireRelease`, etc.).
- `Resource.Shared[A]`
  - Produces a **shared** instance per `Resource.Shared` value, with **reference counting**:
    - the first allocation initializes the value and collects finalizers
    - each allocating scope registers a decrement finalizer
    - when the reference count reaches zero, the collected finalizers run

**Important clarification:** sharing is **not** "memoized within a Wire graph" or "within a scope" by magic. Sharing happens **within the specific `Resource.Shared` instance** you reuse.

---

### 4) `Scoped[Tag, A]`: deferred computations

`Scoped[-Tag, +A]` represents a computation that produces `A`, but can only be executed by a scope whose tag is compatible with `Tag`.

Execution happens via:

```scala
scope(scopedComputation)
```

How to build them:

- From scoped values:
  - `val s: Scoped[S, B] = (a: A @@ S).map(f)`
  - `flatMap` composes scoped values while tracking combined requirements
- Or directly:
  - `Scoped.create(() => ...)` (advanced/internal style)

`Scoped` is contravariant in `Tag`, which is what allows a **child** scope (more specific tag) to run computations that only require a **parent** tag.

---

### 5) `ScopeEscape` and `Unscoped`: what may escape

Whenever you access a scoped value via:

- `scope.$(value)(f)`, or
- `scope(scopedComputation)`,

…the return type is controlled by `ScopeEscape[A, S]`, which decides whether a result:

- escapes as raw `A`, or
- remains tracked as `A @@ S`.

Rule of thumb:

- Pure data (e.g. `Int`, `String`, small case classes you mark `Unscoped`) should escape as raw values.
- Resource-like values should remain scoped unless you explicitly `leak`.

---

### 6) `Wire[-In, +Out]`: dependency recipes

`Wire` is a recipe for constructing services, commonly used for dependency injection.

- `In` is the required dependencies (provided as a `Context[In]`)
- `Out` is the produced service

There are two wire flavors:

- `Wire.Shared`: a shared recipe
- `Wire.Unique`: a unique recipe

**Important clarification:** `Wire` itself is just a recipe. The actual memoization/sharing behavior happens when you convert the wire into a `Resource`:

```scala
val r: Resource[Out] = wire.toResource(deps)
val out: Out @@ scope.Tag = scope.allocate(r)
```

- `Wire.Shared#toResource` produces a `Resource.Shared`, which is where the reference-counted sharing is implemented.
- `Wire.Unique#toResource` produces a `Resource.Unique`.

Macros available at package level:

- `shared[T]`: derive a shared wire from `T`'s constructor (or from a `Wireable[T]` if present)
- `unique[T]`: derive a unique wire

---

### 7) `Wireable[Out]`: DI for traits/abstract classes

`shared[T]` / `unique[T]` can derive wires from **concrete classes** with constructors. But traits and abstract classes are not instantiable, so you need a way to tell the macros "when someone asks for `T`, build it like *this*".

That's what `Wireable[T]` is: a typeclass that supplies a `Wire` for a service.

Typical use:

- Define a `Wireable[MyTrait]` in `MyTrait`'s companion object.
- `shared[MyTrait]` or `unique[MyTrait]` will pick it up automatically.

This is especially useful when you want to inject an interface but construct a concrete implementation (and still register finalizers correctly).

---

## Safety model (why leaking is prevented)

The library prevents scope leaks via two reinforcing mechanisms:

### A) Existential child tags (fresh, unnameable types)

Child scopes are created with:

```scala
Scope.global.scoped { scope =>
  scope.scoped { child =>
    // allocate in child
  }
}
```

The child scope has an existential tag (fresh per invocation). You can allocate in the child, but you can't return those values to the parent in a usable form because the parent cannot name (or satisfy) the child tag.

Compile-time safety is verified in tests, e.g.:
`ScopeCompileTimeSafetyScala3Spec`.

### B) Tag invariance + "opaque-like" `@@` blocks subtyping escape

Even if you try to "widen" a child-tagged value to a parent-tagged value, invariance and hidden members prevent it from typechecking. The only sanctioned access route is through `scope.$` / `scope.apply`, which require tag evidence.

---

## Usage examples

### Allocating and using a resource

```scala
import zio.blocks.scope._

final class FileHandle(path: String) extends AutoCloseable {
  def readAll(): String = s"contents of $path"
  def close(): Unit = println(s"closed $path")
}

Scope.global.scoped { scope =>
  val h = scope.allocate(Resource(new FileHandle("data.txt")))

  val contents: String =
    scope.$(h)(_.readAll())

  println(contents)
}
```

---

### Nested scopes (child can use parent, not vice versa)

```scala
import zio.blocks.scope._

Scope.global.scoped { parent =>
  val parentDb = parent.allocate(Resource(new Database))

  parent.scoped { child =>
    // child can use parent-scoped values:
    val ok: String = child.$(parentDb)(_.query("SELECT 1"))
    println(ok)

    val childDb = child.allocate(Resource(new Database))

    // You can use childDb *inside* the child:
    val ok2: String = child.$(childDb)(_.query("SELECT 2"))
    println(ok2)

    // But you cannot return childDb to the parent in a usable way:
    // childDb : Database @@ child.Tag
    // parent cannot prove parent.Tag <:< child.Tag
  }

  // parentDb is still usable here:
  val stillOk = parent.$(parentDb)(_.query("SELECT 3"))
  println(stillOk)
}
```

---

### Building a `Scoped` program (map/flatMap)

```scala
import zio.blocks.scope._

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

---

### Registering cleanup manually with `defer`

Use `scope.defer` when you already have a value and just need to register cleanup.

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  val handle = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  scope.defer { handle.close() }

  val firstByte = handle.read()
  println(firstByte)
}
```

There is also a package-level helper `defer` that only requires a `Finalizer`:

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  given Finalizer = scope
  defer { println("cleanup") }
}
```

---

### Classes with `Finalizer` parameters

If your class needs to register cleanup logic, accept a `Finalizer` parameter (not `Scope`). The wire and resource macros automatically inject the `Finalizer` when constructing such classes.

```scala
import zio.blocks.scope._

class ConnectionPool(config: Config)(implicit finalizer: Finalizer) {
  private val pool = createPool(config)
  finalizer.defer { pool.shutdown() }
  
  def getConnection(): Connection = pool.acquire()
}

// The macro sees the implicit Finalizer and injects it automatically:
val resource = Resource.from[ConnectionPool](Wire(Config("jdbc://localhost")))

Scope.global.scoped { scope =>
  val pool = scope.allocate(resource)
  // pool.shutdown() will be called when scope closes
}
```

Why `Finalizer` instead of `Scope`?
- `Finalizer` is the minimal interface—it only has `defer`
- Classes that need cleanup should not have access to `allocate` or `$`
- The macros pass a `Finalizer` at runtime, so declaring `Scope` would be misleading

---

### Dependency injection with `Wire` + `Context`

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(debug: Boolean)

val w: Wire.Shared[Boolean, Config] = shared[Config]
val deps: Context[Boolean] = Context[Boolean](true)

Scope.global.scoped { scope =>
  val cfg: Config @@ scope.Tag =
    scope.allocate(w.toResource(deps))

  val debug: Boolean =
    scope.$(cfg)(_.debug) // Boolean typically escapes

  println(debug)
}
```

Sharing vs uniqueness at the wire level:

```scala
import zio.blocks.scope._

val ws = shared[Config] // shared recipe; sharing happens via Resource.Shared when allocated
val wu = unique[Config] // unique recipe; each allocation is fresh
```

---

### Supplying dependencies with `Resource.from[T](wire1, wire2, ...)`

`Resource.from[T]` can also be used as a "standalone mini graph" by providing wires for constructor dependencies.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(url: String)

trait Logger {
  def info(msg: String): Unit
}

final class ConsoleLogger extends Logger {
  def info(msg: String): Unit = println(msg)
}

final class Service(cfg: Config, logger: Logger) extends AutoCloseable {
  def run(): Unit = logger.info(s"running with ${cfg.url}")
  def close(): Unit = println("service closed")
}

// Provide wires for *all* dependencies of Service:
val serviceResource: Resource[Service] =
  Resource.from[Service](
    Wire(Config("jdbc:postgresql://localhost/db")),
    Wire(new ConsoleLogger: Logger)
  )

Scope.global.scoped { scope =>
  val svc = scope.allocate(serviceResource)
  scope.$(svc)(_.run())
}
```

Notes:
- All dependencies of `T` must be covered by the provided wires, otherwise you get a compile-time error.
- If `T` is `AutoCloseable`, `close()` is registered automatically.

---

### DI for traits via `Wireable`

When you want to inject a trait (or abstract class), define a `Wireable` in the companion so `shared[T]` / `unique[T]` can resolve it.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

trait DatabaseApi {
  def query(sql: String): String
}

final class LiveDatabaseApi(cfg: Config) extends DatabaseApi with AutoCloseable {
  def query(sql: String): String = s"[${cfg.url}] $sql"
  def close(): Unit = println("LiveDatabaseApi closed")
}

object DatabaseApi {
  // Tell Scope how to build the trait by wiring a concrete implementation.
  given Wireable.Typed[Config, DatabaseApi] =
    Wireable.fromWire(shared[LiveDatabaseApi].shared.asInstanceOf[Wire[Config, DatabaseApi]])
}

final case class Config(url: String)

Scope.global.scoped { scope =>
  val deps = Context(Config("jdbc:postgresql://localhost/db"))

  val db: DatabaseApi @@ scope.Tag =
    scope.allocate(shared[DatabaseApi].toResource(deps))

  val out: String =
    scope.$(db)(_.query("SELECT 1"))

  println(out)
}
```

Practical guidance:
- Prefer `Wireable.fromWire(...)` when you already have a `Wire` you trust.
- Put `given Wireable[...]` / `implicit val wireable: Wireable[...]` in the companion of the trait being injected.

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

## API reference (selected)

### `Scope`

Core methods (Scala 3 `using` vs Scala 2 `implicit` differs, but the shapes are the same):

```scala
final class Scope[ParentTag, Tag0 <: ParentTag] {
  type Tag = Tag0

  def allocate[A](resource: Resource[A]): A @@ Tag
  def defer(f: => Unit): Unit

  def $[A, B, S](scoped: A @@ S)(f: A => B)(
    using ev: this.Tag <:< S,
          escape: ScopeEscape[B, S]
  ): escape.Out

  def apply[A, S](scoped: Scoped[S, A])(
    using ev: this.Tag <:< S,
          escape: ScopeEscape[A, S]
  ): escape.Out

  // Creates a child scope with an existential tag (fresh per call)
  def scoped[A](f: Scope[this.Tag, ? <: this.Tag] => A): A
}
```

### `Resource`

```scala
sealed trait Resource[+A]

object Resource {
  def apply[A](value: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]

  // Macro-derived constructors:
  def from[T]: Resource[T]
  def from[T](wires: Wire[?, ?]*): Resource[T]

  // Internal / produced by wires:
  def shared[A](f: Finalizer => A): Resource.Shared[A]
  def unique[A](f: Finalizer => A): Resource.Unique[A]

  final class Shared[+A] extends Resource[A]
  final class Unique[+A] extends Resource[A]
}
```

### `Wire` and `Wireable`

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
  def toResource(deps: zio.blocks.context.Context[In]): Resource[Out]
}

trait Wireable[+Out] {
  type In
  def wire: Wire[In, Out]
}
```

---

## Mental model recap

- Use `Scope.global.scoped { scope => ... }` to create a safe region.
- Allocate managed things with `scope.allocate(Resource(...))` (or `Resource.from[...]`).
- Use scoped values only via `scope.$(value)(...)` or via `Scoped` computations executed by `scope(scoped)`.
- Nest with `scope.scoped { child => ... }` to create a tighter lifetime boundary.
- If it doesn't typecheck, it would have been unsafe at runtime.
