# ZIO Blocks — Scope (compile-time safe resource management)

`zio.blocks.scope` provides **compile-time verified resource safety** for synchronous code by tagging values with an unnameable, type-level **scope identity**. Values allocated in a scope can only be used when you hold a compatible `Scope`, and values allocated in a *child* scope cannot be returned to the parent in a usable form.

**Structured scopes.** Scopes follow the structured-concurrency philosophy: child scopes are nested within parent scopes, resources are tied to the lifetime of the scope that allocated them, and cleanup happens deterministically when the scope exits (finalizers run LIFO). This "nesting = lifetime" structure provides clear ownership boundaries in addition to compile-time leak prevention.

If you've used `try/finally`, `Using`, or ZIO `Scope`, this library lives in the same problem space, but it focuses on:

- **Compile-time prevention of scope leaks**
- **Unified scoped type** (`A @@ S` is a type alias for `Scoped[A, S]`)
- **Simple, synchronous lifecycle management** (finalizers run LIFO on scope close)

---

## Table of contents

- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
  - [1) `Scope[ParentTag, Tag]`](#1-scopeparenttag-tag)
  - [2) Scoped values: `A @@ S`](#2-scoped-values-a--s)
  - [3) `Resource[A]`: acquisition + finalization](#3-resourcea-acquisition--finalization)
  - [4) `A @@ S`: unified scoped type](#4-a--s-unified-scoped-type)
  - [5) `ScopeEscape` and `Unscoped`: what may escape](#5-scopeescape-and-unscoped-what-may-escape)
  - [6) `Wire[-In, +Out]`: dependency recipes](#6-wire-in-out-dependency-recipes)
- [Safety model (why leaking is prevented)](#safety-model-why-leaking-is-prevented)
- [Usage examples](#usage-examples)
  - [Allocating and using a resource](#allocating-and-using-a-resource)
  - [Nested scopes (child can use parent, not vice versa)](#nested-scopes-child-can-use-parent-not-vice-versa)
  - [Building a `Scoped` program (map/flatMap)](#building-a-scoped-program-mapflatmap)
  - [Registering cleanup manually with `defer`](#registering-cleanup-manually-with-defer)
  - [Dependency injection with `Wire` + `Context`](#dependency-injection-with-wire--context)
  - [Dependency injection with `Resource.from[T](wires*)`](#dependency-injection-with-resourcefromtwires)
  - [Injecting traits via subtype wires](#injecting-traits-via-subtype-wires)
  - [Interop escape hatch: `leak`](#interop-escape-hatch-leak)
- [Common compile errors](#common-compile-errors)
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

`A @@ S` is a type alias for `Scoped[A, S]` — a handle to a value of type `A` that is locked to scope tag `S`.

- **Runtime representation:** a boxed thunk (lightweight wrapper)
- **Key effect:** methods on `A` are hidden; you can't call `a.method` directly
- **Acquisition timing:** `scope.allocate(resource)` acquires the resource **immediately** (eagerly) and returns a scoped handle for accessing the already-acquired value. The thunk defers *access*, not *acquisition*.
- **Access paths:**
  - `scope.$(a)(f)` to execute and apply a function immediately
  - `a.map / a.flatMap` to build composite scoped computations
  - `scope.execute(scoped)` to run a composed computation

#### Scala 2 note

In Scala 2, explicit type annotations are required when assigning scoped values to avoid existential type inference issues:

```scala
// Scala 2 requires explicit type annotation
val db: Database @@ scope.Tag = scope.allocate(Resource[Database])

// Scala 3 can infer the type
val db = scope.allocate(Resource[Database])
```

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
- `Resource.from[T](wires*)` (macro)
  - The primary entry point for dependency injection.
  - Resolves `T` and all its dependencies into a single `Resource[T]`.
  - Auto-creates missing wires using `Wire.shared` for concrete classes.
  - Requires explicit wires for: primitives, functions, collections, and abstract types.
  - If `T` or any dependency is `AutoCloseable`, registers `close()` automatically.

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

### 4) `A @@ S`: unified scoped type

`A @@ S` (type alias for `Scoped[A, S]`) is the core type representing a deferred computation that produces `A` and requires scope tag `S` to execute.

Execution happens via:

```scala
scope.execute(scopedComputation)
```

How to build them:

- From `scope.allocate`:
  - `scope.allocate(resource)` returns `A @@ scope.Tag`
- Using combinators:
  - `(a: A @@ S).map(f: A => B)` returns `B @@ S`
  - `(a: A @@ S).flatMap(f: A => B @@ T)` returns `B @@ (S & T)` (Scala 3) / `B @@ (S with T)` (Scala 2)
  - Use for-comprehensions to chain scoped computations
- From ordinary values:
  - `Scoped(value)` lifts a value into an `A @@ Any` (which can be used anywhere due to contravariance)

**Contravariance:** `A @@ S` is contravariant in `S`. This means `A @@ ParentTag` is a subtype of `A @@ ChildTag` when `ChildTag <: ParentTag`. Child scopes can execute parent-scoped computations automatically.

**For-comprehension example:**

```scala
Scope.global.scoped { scope =>
  val program: Result @@ scope.Tag = for {
    pool <- scope.allocate(Resource[Pool])
    conn <- scope.allocate(Resource(pool.lease()))
    data <- conn.map(_.query("SELECT *"))
  } yield process(data)

  scope.execute(program)
}
```

---

### 5) `ScopeEscape` and `Unscoped`: what may escape

Whenever you access a scoped value via:

- `scope.$(value)(f)`, or
- `scope.execute(scopedComputation)`,

…the return type is controlled by `ScopeEscape[A, S]`, which decides whether a result:

- escapes as raw `A`, or
- remains tracked as `A @@ S`.

Rule of thumb:

- Pure data (e.g. `Int`, `String`, small case classes you mark `Unscoped`) should escape as raw values.
- Resource-like values should remain scoped unless you explicitly `leak`.

---

### 6) `Wire[-In, +Out]`: dependency recipes

`Wire` is a recipe for constructing services. It describes **how** to build a service given its dependencies, but does not resolve those dependencies itself.

- `In` is the required dependencies (provided as a `Context[In]`)
- `Out` is the produced service

There are two wire flavors:

- `Wire.Shared`: produces a shared (memoized) instance
- `Wire.Unique`: produces a fresh instance each time

**Important clarification:** `Wire` itself is just a recipe. The sharing/uniqueness behavior is realized when the wire is used inside `Resource.from`, which composes `Resource.Shared` or `Resource.Unique` instances accordingly.

#### Creating wires

There are exactly **3 macro entry points**:

| Macro | Purpose |
|-------|---------|
| `Wire.shared[T]` | Create a shared wire from `T`'s constructor |
| `Wire.unique[T]` | Create a unique wire from `T`'s constructor |
| `Resource.from[T](wires*)` | Wire up `T` and all dependencies into a `Resource` |

For wrapping pre-existing values:

- `Wire(value)` — wraps a value; if `AutoCloseable`, registers `close()` automatically

#### How `Resource.from[T](wires*)` works

1. **Collect wires**: Uses explicit wires when provided, otherwise auto-creates with `Wire.shared`
2. **Validate**: Checks for cycles, unmakeable types, duplicate providers
3. **Topological sort**: Orders dependencies so leaves are allocated first
4. **Generate composition**: Produces a `Resource[T]` via flatMap chains

Key insight: **Compose Resources, don't accumulate values.** Each wire becomes a `Resource`, and they are composed via `flatMap`. This correctly preserves:

- **Sharing**: Same `Resource.Shared` instance → same value (even in diamond patterns)
- **Uniqueness**: `Resource.Unique` → fresh value per injection site

#### Subtype resolution

When `Resource.from` needs a dependency of type `Service`, it will accept a wire whose output is a subtype (e.g., `Wire.shared[LiveService]` where `LiveService extends Service`). This enables trait injection without extra boilerplate.

If the same concrete wire satisfies multiple types (e.g., `Service` and `LiveService`), only **one instance** is created and reused for both.

---

## Safety model (why leaking is prevented)

**Pragmatic safety.** The type-level tagging prevents *accidental* scope misuse in normal code, but it is not a security boundary. A determined developer can bypass it via `leak` (which emits a compiler warning), unsafe casts (`asInstanceOf`), or storing scoped references in mutable state (`var`). The guarantees are "good enough" to catch mistakes in regular usage, not protection against intentional circumvention.

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

### B) Contravariance prevents child-to-parent widening

`A @@ S` is contravariant in `S`. This means `Db @@ child.Tag` is **not** a subtype of `Db @@ parent.Tag` — the subtyping goes the *other* direction. A child scope can use parent-tagged values (because `child.Tag <: parent.Tag` makes `A @@ parent.Tag <: A @@ child.Tag`), but you cannot widen a child-tagged value to a parent tag.

Additionally, the thunk-based representation hides `A`'s methods — you can't call `db.query(...)` directly on a `Database @@ scope.Tag`. The only sanctioned access routes are `scope.$` and `scope.execute`, which require a scope with a compatible tag.

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

The idiomatic pattern is to work directly with `@@` values in for-comprehensions. Each `<-` uses `@@#flatMap`, which takes `A => B @@ T` and accumulates into a `Scoped`:

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  val db: Database @@ scope.Tag = scope.allocate(Resource(new Database))

  val program: String @@ scope.Tag =
    for {
      d <- db                         // db: Database @@ Tag, d: Database
    } yield d.query("SELECT 1")

  val result: String = scope.execute(program)
  println(result)
}
```

This pattern shines when chaining resource acquisition:

```scala
import zio.blocks.scope._

// A pool that leases connections
class Pool(implicit finalizer: Finalizer) extends AutoCloseable {
  def lease: Resource[Connection] = Resource(new Connection)
  def close(): Unit = println("pool closed")
}

class Connection extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("connection closed")
}

Scope.global.scoped { scope =>
  val pool: Pool @@ scope.Tag = scope.allocate(Resource.from[Pool])

  val program: String @@ scope.Tag =
    for {
      p          <- pool                      // extract Pool from scoped
      connection <- scope.allocate(p.lease)   // allocate returns Connection @@ Tag
    } yield scope.$(connection)(_.query("SELECT 1"))

  val result: String = scope.execute(program)
  println(result)
}
// Output: connection closed, then pool closed (LIFO)
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
  defer { pool.shutdown() }
  
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

For manual wiring (when you already have dependencies assembled), use `wire.toResource(ctx)`:

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(debug: Boolean)

val w: Wire.Shared[Boolean, Config] = Wire.shared[Config]
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

val ws = Wire.shared[Config] // shared recipe; sharing happens via Resource.Shared when allocated
val wu = Wire.unique[Config] // unique recipe; each allocation is fresh
```

---

### Dependency injection with `Resource.from[T](wires*)`

`Resource.from[T](wires*)` is the **primary entry point** for dependency injection. It resolves `T` and all its dependencies into a single `Resource[T]`.

```scala
import zio.blocks.scope._

final case class Config(url: String)

final class Logger {
  def info(msg: String): Unit = println(msg)
}

final class Database(cfg: Config) extends AutoCloseable {
  def query(sql: String): String = s"[${cfg.url}] $sql"
  def close(): Unit = println("database closed")
}

final class Service(db: Database, logger: Logger) extends AutoCloseable {
  def run(): Unit = logger.info(s"running with ${db.query("SELECT 1")}")
  def close(): Unit = println("service closed")
}

// Only provide leaf values (primitives, configs) - the rest is auto-wired:
val serviceResource: Resource[Service] =
  Resource.from[Service](
    Wire(Config("jdbc:postgresql://localhost/db"))
  )

Scope.global.scoped { scope =>
  val svc = scope.allocate(serviceResource)
  scope.$(svc)(_.run())
}
// Output: running with [jdbc:postgresql://localhost/db] SELECT 1
// Then: service closed, database closed (LIFO order)
```

**What you must provide:**
- Leaf values: primitives, configs, pre-existing instances via `Wire(value)`
- Abstract types: traits/abstract classes via `Wire.shared[ConcreteImpl]`
- Overrides: when you want `unique` instead of the default `shared`

**What is auto-created:**
- Concrete classes with accessible primary constructors (default: `Wire.shared`)

---

### Injecting traits via subtype wires

When a dependency is a trait or abstract class, provide a wire for a concrete implementation:

```scala
import zio.blocks.scope._

trait Logger {
  def info(msg: String): Unit
}

final class ConsoleLogger extends Logger {
  def info(msg: String): Unit = println(msg)
}

final class App(logger: Logger) {
  def run(): Unit = logger.info("Hello!")
}

// Wire.shared[ConsoleLogger] satisfies the Logger dependency via subtyping:
val appResource: Resource[App] =
  Resource.from[App](
    Wire.shared[ConsoleLogger]
  )

Scope.global.scoped { scope =>
  val app = scope.allocate(appResource)
  scope.$(app)(_.run())
}
```

**Single instance for diamond patterns:**

```scala
trait Service
class LiveService extends Service
class NeedsService(s: Service)
class NeedsLive(l: LiveService)
class App(a: NeedsService, b: NeedsLive)

// One LiveService instance satisfies both Service and LiveService dependencies:
val appResource = Resource.from[App](
  Wire.shared[LiveService]
)
// count of LiveService instantiations: 1
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

## Common compile errors

The scope macros produce beautiful, actionable compile-time error messages with ASCII diagrams and helpful hints:

### Not a class (Wire.shared/unique on trait or abstract class)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Cannot derive Wire for MyTrait: not a class.

  Hint: Use Wire.Shared / Wire.Unique directly.

────────────────────────────────────────────────────────────────────────────
```

### Unmakeable type (primitives, functions, collections)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Cannot auto-create String

  This type (primitive, collection, or function) cannot be auto-created.

  Required by:
    └── Config
        └── App

  Fix: Provide Wire(value) with the desired value:

    Resource.from[...](
      Wire(...),  // provide a value for String
      ...
    )

────────────────────────────────────────────────────────────────────────────
```

### Abstract type (trait or abstract class)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Cannot auto-create Logger

  This type is abstract (trait or abstract class).

  Required by:
    └── App

  Fix: Provide a wire for a concrete implementation:

    Resource.from[...](
      Wire.shared[ConcreteImpl],  // provides Logger
      ...
    )

────────────────────────────────────────────────────────────────────────────
```

### Duplicate providers (ambiguous wires)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Multiple providers for Service

  Conflicting wires:
    1. LiveService
    2. TestService

  Hint: Remove duplicate wires or use distinct wrapper types.

────────────────────────────────────────────────────────────────────────────
```

### Dependency cycle

```
── Scope Error ──────────────────────────────────────────────────────────────

  Dependency cycle detected

  Cycle:
    ┌───────────┐
    │           ▼
    A ──► B ──► C
    ▲           │
    └───────────┘

  Break the cycle by:
    • Introducing an interface/trait
    • Using lazy initialization
    • Restructuring dependencies

────────────────────────────────────────────────────────────────────────────
```

### Subtype conflict (related dependency types)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Dependency type conflict in MyService

  FileInputStream is a subtype of InputStream.

  When both types are dependencies, Context cannot reliably distinguish
  them. The more specific type may be retrieved when the more general
  type is requested.

  To fix this, wrap one or both types in a distinct wrapper:

    case class WrappedInputStream(value: InputStream)
    or
    opaque type WrappedInputStream = InputStream

────────────────────────────────────────────────────────────────────────────
```

### Duplicate parameter types in constructor

```
── Scope Error ──────────────────────────────────────────────────────────────

  Constructor of App has multiple parameters of type String

  Context is type-indexed and cannot supply distinct values for the same type.

  Fix: Wrap one parameter in an opaque type to distinguish them:

    opaque type FirstString = String
    or
    case class FirstString(value: String)

────────────────────────────────────────────────────────────────────────────
```

### Leak warning

When using `leak(value)` to escape the scoped type system:

```
── Scope Warning ────────────────────────────────────────────────────────────

  leak(db)
       ^
       |

  Warning: db is being leaked from scope MyScope.
  This may result in undefined behavior.

  Hint:
     If you know this data type is not resourceful, then add a given ScopeEscape
     for it so you do not need to leak it.

────────────────────────────────────────────────────────────────────────────
```

---

## API reference (selected)

### `Scope`

Core methods (Scala 3 `using` vs Scala 2 `implicit` differs, but the shapes are the same):

```scala
final class Scope[ParentTag, Tag0 <: ParentTag] {
  type Tag = Tag0

  def allocate[A](resource: Resource[A]): A @@ Tag
  def defer(f: => Unit): Unit

  // Execute scoped value with function, escape based on ScopeEscape
  def $[A, B](scoped: A @@ this.Tag)(f: A => B)(
    using escape: ScopeEscape[B, this.Tag]
  ): escape.Out

  // Execute scoped computation, escape based on ScopeEscape
  def execute[A](scoped: A @@ this.Tag)(
    using escape: ScopeEscape[A, this.Tag]
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

  // Macro - DI entry point (can also be called with no args for zero-dep classes):
  def from[T](wires: Wire[?, ?]*): Resource[T]

  // Internal (used by generated code):
  def shared[A](f: Finalizer => A): Resource[A]
  def unique[A](f: Finalizer => A): Resource[A]
}
```

### `Wire`

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
  def toResource(deps: zio.blocks.context.Context[In]): Resource[Out]
}

object Wire {
  // Macro entry points:
  def shared[T]: Wire[?, T]  // derive from T's constructor
  def unique[T]: Wire[?, T]  // derive from T's constructor
  
  // Wrap pre-existing value (auto-finalizes if AutoCloseable):
  def apply[T](value: T): Wire.Shared[Any, T]
  
  final class Shared[-In, +Out] extends Wire[In, Out]
  final class Unique[-In, +Out] extends Wire[In, Out]
}
```

---

## Mental model recap

- Use `Scope.global.scoped { scope => ... }` to create a safe region.
- For simple resources: `scope.allocate(Resource(value))` or `scope.allocate(Resource.acquireRelease(...)(...))`
- For dependency injection: `scope.allocate(Resource.from[App](Wire(config), ...))` — auto-wires concrete classes, you provide leaves and overrides.
- Use scoped values only via `scope.$(value)(...)` or via `Scoped` computations executed by `scope.execute(scoped)`.
- Nest with `scope.scoped { child => ... }` to create a tighter lifetime boundary.
- If it doesn't typecheck, it would have been unsafe at runtime.

**The 3 macro entry points:**
- `Wire.shared[T]` — shared wire from constructor
- `Wire.unique[T]` — unique wire from constructor
- `Resource.from[T](wires*)` — wire up T and all dependencies
