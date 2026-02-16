# ZIO Blocks — Scope (compile-time safe resource management)

`zio.blocks.scope` provides **compile-time verified resource safety** for synchronous code by tagging values with an unnameable, type-level **scope identity**. Values allocated in a scope can only be used when you hold a compatible `Scope`, and values allocated in a *child* scope cannot be returned to the parent in a usable form.

**Structured scopes.** Scopes follow the structured-concurrency philosophy: child scopes are nested within parent scopes, resources are tied to the lifetime of the scope that allocated them, and cleanup happens deterministically when the scope exits (finalizers run LIFO). This "nesting = lifetime" structure provides clear ownership boundaries in addition to compile-time leak prevention.

If you've used `try/finally`, `Using`, or ZIO `Scope`, this library lives in the same problem space, but it focuses on:

- **Compile-time prevention of scope leaks**
- **Zero-cost opaque type** (`$[A]` is the scoped type, equal to `A` at runtime; there is no `$[A]` constructor method — values become scoped through `allocate`)
- **Simple, synchronous lifecycle management** (finalizers run LIFO on scope close)
- **Eager allocation** (all scope operations execute immediately; `Resource` is a lazy *description* that becomes eager when passed to `allocate`)

---

## Table of contents

- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
  - [1) `Scope`](#1-scope)
  - [2) Scoped values: `$[+A]`](#2-scoped-values-a)
  - [3) `Resource[A]`: acquisition + finalization](#3-resourcea-acquisition--finalization)
  - [4) `Unscoped`: marking pure data types](#4-unscoped-marking-pure-data-types)
  - [5) `lower`: accessing parent-scoped values](#5-lower-accessing-parent-scoped-values)
  - [6) `Wire[-In, +Out]`: dependency recipes](#6-wire-in-out-dependency-recipes)
- [Safety model (why leaking is prevented)](#safety-model-why-leaking-is-prevented)
- [Usage examples](#usage-examples)
  - [Allocating and using a resource](#allocating-and-using-a-resource)
  - [Nested scopes (child can use parent, not vice versa)](#nested-scopes-child-can-use-parent-not-vice-versa)
  - [Chaining resource acquisition](#chaining-resource-acquisition)
  - [Registering cleanup manually with `defer`](#registering-cleanup-manually-with-defer)
  - [Classes with `Finalizer` parameters](#classes-with-finalizer-parameters)
  - [Classes with `Scope` parameters (scope injection)](#classes-with-scope-parameters-scope-injection)
  - [Non-lexical scopes with `open()`](#non-lexical-scopes-with-open)
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
  import scope._

  val db: $[Database] = allocate(Resource(new Database))

  // scope $ db applies a function to the scoped value, returning a scoped result
  val result: String = (scope $ db)(_.query("SELECT 1")).get
  println(result)
}
```

Key things to notice:

- `allocate(...)` returns a **scoped** value of type `$[Database]` (the path-dependent type of the enclosing scope)
- `$[A] = A` at runtime — zero-cost opaque type, no boxing
- All operations are **eager** — values are computed immediately, no lazy thunks
- `(scope $ value)(f)` is macro-enforced to prevent capturing scoped values; returns `$[B]`
- `.get` on `$[A]` extracts the underlying value when `A` has an `Unscoped` instance
- When the `scoped { ... }` block exits, finalizers run **LIFO** and errors are handled safely
- The `scoped` method requires `Unscoped[A]` evidence on the return type

---

## Core concepts

### 1) `Scope`

`Scope` is a `sealed abstract class` with **no** type parameters. It manages finalizers and ties values to a *type-level identity* via abstract type members.

- **`type $[+A]`** — a path-dependent opaque type that tags values to this scope. Covariant in `A`. Equal to `A` at runtime (zero-cost).
- **`type Parent <: Scope`** — the parent scope's type.
- **`val parent: Parent`** — reference to the parent scope.

Each scope instance exposes its own `$[+A]`, so a parent's `$[Database]` is a different type than a child's `$[Database]`, even though both equal `Database` at runtime.

```scala
type $[+A]  // = A at runtime (zero-cost)
```

So in code you'll typically write:

```scala
Scope.global.scoped { scope =>
  import scope._
  val x: $[Something] = ???  // or scope.$[Something]
}
```

Child scopes are represented by `Scope.Child[P <: Scope]`, a `final class` nested in the `Scope` companion object.

#### Global scope

`Scope.global` is the root of the scope hierarchy:

```scala
object Scope {
  object global extends Scope {
    type $[+A]  = A
    type Parent = global.type
    val parent: Parent = this
  }
}
```

- The global scope is intended to live for the lifetime of the process.
- On the JVM, its finalizers run on shutdown via a shutdown hook. On Scala.js, no shutdown hook is available, so global finalizers do not run automatically.

---

### 2) Scoped values: `$[+A]`

`$[+A]` (or `scope.$[A]` in type annotations) is a path-dependent opaque type representing a value of type `A` that is locked to a specific scope. It is covariant in `A`.

- **Runtime representation:** `$[A] = A` — zero-cost opaque type, no boxing or wrapping
- **Key effect:** methods on `A` are hidden at the type level; you can't call `a.method` directly
- **All operations are eager:** `allocate(resource)` acquires the resource **immediately** and returns a scoped value
- **Access paths:**
  - `(scope $ a)(f)` — macro-enforced access; returns `$[B]`
  - `.get` on `$[A]` when `A: Unscoped` — extracts the underlying value

#### `ScopedOps`: `.get` on `$[A]`

`Scope` provides an implicit class `ScopedOps[A]` that adds `.get` to `$[A]` values when `A` has an `Unscoped` instance:

```scala
Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(Resource.from[Database])
  val result: String = (scope $ db)(_.query("SELECT 1")).get
  val count: Int = (scope $ db)(_.rowCount).get
}
```

- `.get` requires `Unscoped[A]` evidence, ensuring only pure data can be extracted
- Resources (`$[Database]`, `$[Socket]`) cannot be `.get`-ed — they don't have `Unscoped` instances
- `Resource[A]` has an `Unscoped` instance (it's a lazy description, not a live resource), enabling patterns like `allocate((scope $ db)(_.beginTransaction()).get)`

---

### 3) `Resource[A]`: acquisition + finalization

`Resource[A]` describes how to **acquire** an `A` and how to **release** it when a scope closes. It is intentionally lazy: you *describe what to do*, and allocation happens only through:

```scala
allocate(resource)
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
    - the first allocation initializes the value using a child scope parented to `Scope.global`
    - each allocating scope registers a decrement finalizer
    - when the reference count reaches zero, the shared scope is closed (running its finalizers)

**Important clarification:** sharing is **not** "memoized within a Wire graph" or "within a scope" by magic. Sharing happens **within the specific `Resource.Shared` instance** you reuse.

---

### 4) `Unscoped`: marking pure data types

The `Unscoped[A]` typeclass marks types as pure data that don't hold resources. The `scoped` method requires `Unscoped[A]` evidence on the return type to ensure only safe values can exit a scope.

**Built-in Unscoped types:**
- Primitives: `Int`, `Long`, `Boolean`, `Double`, etc.
- `String`, `Unit`, `Nothing`
- Collections of Unscoped types
- `Resource[A]` (lazy descriptions, not live resources)

**Custom Unscoped types:**
```scala
// Scala 3:
case class Config(debug: Boolean)
object Config {
  given Unscoped[Config] = Unscoped.derived
}

// Scala 2:
case class Config(debug: Boolean)
object Config {
  implicit val unscopedConfig: Unscoped[Config] = Unscoped.derived[Config]
}
```

**Allowed return types from `scoped`:**

- **`Unscoped` types**: Pure data that can safely exit
- **`Nothing`**: For blocks that throw

**Rejected return types (no Unscoped instance):**

- **Closures**: `() => A` could capture the child scope
- **Scoped values**: `$[A]` would be use-after-close
- **The scope itself**: Would allow operations after close

```scala
Scope.global.scoped { parent =>
  import parent._

  // ✅ OK: String is Unscoped
  val result: String = scoped { child =>
    "hello"
  }

  // ❌ COMPILE ERROR: $[Database] has no Unscoped instance
  // val escaped = scoped { child =>
  //   import child._
  //   allocate(Resource(new Database))  // $[Database] can't escape
  // }
}
```

---

### 5) `lower`: accessing parent-scoped values

When working in a child scope, you may need to access values allocated in a parent scope. Use `lower(parentValue)` to "lower" a parent-scoped value into the child scope:

```scala
Scope.global.scoped { parent =>
  import parent._

  val parentDb: $[Database] = allocate(Resource(new Database))

  scoped { child =>
    import child._

    // Use lower() to access parent-scoped value in child scope
    val db: $[Database] = lower(parentDb)
    (child $ db)(_.query("SELECT 1"))

    "done"
  }
}
```

The `lower` operation is necessary because each scope has its own `$[A]` opaque type. A parent's `$[A]` is a different type than a child's `$[A]`, even though both equal `A` at runtime.

---

### 6) `Wire[-In, +Out]`: dependency recipes

`Wire` is a recipe for constructing services. It describes **how** to build a service given its dependencies, but does not resolve those dependencies itself.

- `In` is the required dependencies (provided as a `Context[In]`)
- `Out` is the produced service

There are two wire flavors:

- `Wire.Shared`: produces a shared (memoized) instance
- `Wire.Unique`: produces a fresh instance each time

**Important clarification:** `Wire` itself is just a recipe. The sharing/uniqueness behavior is realized when the wire is used inside `Resource.from`, which composes `Resource.Shared` or `Resource.Unique` instances accordingly. Sharing is **per `Resource.Shared` instance**, not per-scope or per-graph: if `wire.toResource(ctx)` is called twice, you get two independent ref-counted singletons that don't share with each other. Inside `Resource.from`, each wire produces exactly one `Resource`, so diamond dependencies correctly share a single instance.

#### Creating wires

There are exactly **3 DI macro entry points**:

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
  import scope._
  scoped { child =>
    import child._
    // allocate in child
  }
}
```

The child scope has a fresh, unnameable `$[A]` type (created per invocation). You can allocate in the child, but you can't return those values to the parent in a usable form because the parent cannot name (or satisfy) the child's `$[A]` type.

Compile-time safety is verified in tests, e.g.:
`ScopeCompileTimeSafetyScala3Spec`.

### B) Opaque types prevent escape

Each scope defines its own `$[A]` opaque type. Even though `$[A] = A` at runtime, the compiler treats each scope's `$[A]` as distinct. A child's `$[Database]` is a different type than the parent's `$[Database]`.

Additionally, the opaque type hides `A`'s methods at the type level — you can't call `db.query(...)` directly on a `$[Database]`. The only access routes are `(scope $ value)(f)` (macro-enforced to prevent capture) and `.get` (requires `Unscoped[A]` evidence).

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
  import scope._

  val h: $[FileHandle] = allocate(Resource(new FileHandle("data.txt")))

  // scope $ h applies function to scoped value, returns $[String]
  val contents: String = (scope $ h)(_.readAll()).get
  println(contents)
}
```

---

### Nested scopes (child can use parent, not vice versa)

```scala
import zio.blocks.scope._

Scope.global.scoped { parent =>
  import parent._

  val parentDb: $[Database] = allocate(Resource(new Database))

  scoped { child =>
    import child._

    // Use lower() to access parent-scoped values in child scope:
    val db: $[Database] = lower(parentDb)
    println((child $ db)(_.query("SELECT 1")).get)

    val childDb: $[Database] = allocate(Resource(new Database))

    // You can use childDb *inside* the child:
    println((child $ childDb)(_.query("SELECT 2")).get)

    // But you cannot return childDb to the parent:
    // $[Database] has no Unscoped instance — compile error

    // Return an Unscoped value
    "done"
  }

  // parentDb is still usable here:
  println((parent $ parentDb)(_.query("SELECT 3")).get)
}
```

---

### Chaining resource acquisition

When a method returns `Resource[A]`, you can chain `$` + `.get` + `allocate` to acquire nested resources without `leak()`:

```scala
import zio.blocks.scope._

class Pool extends AutoCloseable {
  def lease(): Resource[Connection] = Resource.fromAutoCloseable(new Connection)
  def close(): Unit = println("pool closed")
}

class Connection extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("connection closed")
}

Scope.global.scoped { scope =>
  import scope._

  val pool: $[Pool] = allocate(Resource.from[Pool])

  // $ extracts Pool, .lease() returns Resource[Connection],
  // .get extracts the Resource (Unscoped), allocate acquires it
  val conn: $[Connection] = allocate((scope $ pool)(_.lease()).get)

  val result: String = (scope $ conn)(_.query("SELECT 1")).get
  println(result)
}
// Output: result: SELECT 1
// Then: connection closed, pool closed (LIFO)
```

This works because `Resource[A]` has an `Unscoped` instance — it's a lazy description,
not a live resource. Extracting it with `.get` doesn't leak anything; the actual
resource is only acquired when `allocate` is called.

---

### Registering cleanup manually with `defer`

Use `defer` when you already have a value and just need to register cleanup. `defer` returns a `DeferHandle` that can be used to cancel the finalizer before the scope closes.

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  import scope._

  val handle = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  val deferHandle: DeferHandle = defer { handle.close() }

  val firstByte = handle.read()
  println(firstByte)

  // Optionally cancel the finalizer if you've already cleaned up:
  // deferHandle.cancel()
}
```

There is also a package-level helper `defer` that only requires a `Finalizer`:

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  import scope._
  given Finalizer = scope

  defer { println("cleanup") }
}
```

---

### Classes with `Finalizer` parameters

If your class only needs to register cleanup logic, accept a `Finalizer` parameter. The wire and resource macros automatically inject the `Finalizer` when constructing such classes.

```scala
import zio.blocks.scope._

class ConnectionPool(config: Config)(implicit finalizer: Finalizer) {
  private val pool = createPool(config)
  finalizer.defer { pool.shutdown() }  // or: defer { ... } with import zio.blocks.scope._
  
  def getConnection(): Connection = pool.acquire()
}

// The macro sees the implicit Finalizer and injects it automatically:
val resource = Resource.from[ConnectionPool](Wire(Config("jdbc://localhost")))

Scope.global.scoped { scope =>
  import scope._
  val pool = allocate(resource)
  // pool.shutdown() will be called when scope closes
}
```

When to use `Finalizer` instead of `Scope`:
- `Finalizer` is the minimal interface—it only has `defer`
- Use it when your class only needs to register cleanup actions
- Since `Scope extends Finalizer`, a `Scope` is always passed at runtime; declaring `Finalizer` simply narrows the visible API

---

### Classes with `Scope` parameters (scope injection)

If your class needs full scope capabilities—creating child scopes, allocating sub-resources, or managing per-request lifetimes—accept a `Scope` parameter instead of `Finalizer`. The macros automatically inject the `Scope` when constructing such classes.

```scala
import zio.blocks.scope._

class RequestHandler(config: Config)(implicit scope: Scope) {
  // Create child scopes for per-request resource management:
  def handle(request: String): String = {
    scope.scoped { child =>
      import child._
      val conn = allocate(Resource(new Connection(config)))
      (child $ conn)(_.query(request)).get
    }
  }
}

// The macro sees the implicit Scope and injects it automatically:
val resource = Resource.from[RequestHandler](Wire(Config("jdbc://localhost")))

Scope.global.scoped { scope =>
  import scope._
  val handler = allocate(resource)
  val result: String = (scope $ handler)(_.handle("SELECT 1")).get
  println(result)
}
```

The `Scope` parameter can appear in any parameter list position—value, implicit, or using (Scala 3). The macros detect `Scope` parameters the same way they detect `Finalizer` parameters.

When to use `Scope` instead of `Finalizer`:
- The class needs to create child scopes (via `scoped` or `open()`)
- The class needs to allocate sub-resources (via `allocate`)
- The class manages per-request or per-operation lifetimes

---

### Non-lexical scopes with `open()`

The `scoped` method creates a lexically-scoped child that closes when the block exits. For cases where you need a scope whose lifetime isn't tied to a block—such as class-level resource management or resources shared across method calls—use `open()`:

```scala
import zio.blocks.scope._

// From Scope.global, open() returns OpenScope directly ($[A] = A for global):
val os: Scope.OpenScope = Scope.global.open()

// Use the child scope to allocate resources:
val db = os.scope.allocate(Resource(new Database))

// ... use db across multiple method calls or threads ...

// Explicitly close when done (runs finalizers, returns Finalization):
os.close()
```

Within a child scope, `open()` returns `$[OpenScope]`, so you need to unwrap it:

```scala
Scope.global.scoped { scope =>
  import scope._

  val os: $[Scope.OpenScope] = scope.open()
  val openScope: Scope.OpenScope = leak(os)  // unwrap the scoped wrapper

  val db = openScope.scope.allocate(Resource(new Database))
  // ...
  openScope.close()
}
```

`OpenScope` is a case class with two fields:
- `scope: Scope` — the child scope for allocating resources and registering finalizers
- `close: () => Finalization` — explicitly closes the scope and runs its finalizers

Key properties of `open()`:
- The child scope is **unowned**: `isOwner` always returns `true`, so it can be used from any thread
- The child is still **parent-linked**: if the parent scope closes before `close()` is called, the child's finalizers still run (the parent registered a safety finalizer)
- Calling `close()` cancels the parent's safety finalizer to avoid double-finalization
- You must keep the `OpenScope` handle and call `close()` when done to release resources deterministically

---

### Dependency injection with `Wire` + `Context`

For manual wiring (when you already have dependencies assembled), use `wire.toResource(ctx)`:

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(debug: Boolean)

// Wire.shared[Config] infers the constructor deps (here: Boolean → Config):
val w: Wire.Shared[Boolean, Config] = Wire.shared[Config]
val deps: Context[Boolean] = Context[Boolean](true)

Scope.global.scoped { scope =>
  import scope._

  val cfg: $[Config] = allocate(w.toResource(deps))

  val debug: Boolean = (scope $ cfg)(_.debug).get

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
  import scope._
  val svc: $[Service] = allocate(serviceResource)
  (scope $ svc)(_.run())
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
  import scope._
  val app: $[App] = allocate(appResource)
  (scope $ app)(_.run())
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

Sometimes you must hand a raw value to code that cannot work with `$[A]` types.

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  import scope._
  val db: $[Database] = allocate(Resource(new Database))

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
  ├── Config
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
     If you know this data type is not resourceful, then add an Unscoped
     instance for it so you do not need to leak it.

────────────────────────────────────────────────────────────────────────────
```

---

## API reference (selected)

### `Scope`

Core methods (Scala 3 `using` vs Scala 2 `implicit` differs, but the shapes are the same):

```scala
sealed abstract class Scope extends Finalizer {
  type $[+A]         // = A at runtime (zero-cost)
  type Parent <: Scope
  val parent: Parent

  def allocate[A](resource: Resource[A]): $[A]
  def allocate[A <: AutoCloseable](value: => A): $[A]
  def defer(f: => Unit): DeferHandle

  // Create a non-lexical child scope (usable from any thread)
  def open(): $[Scope.OpenScope]

  // Macro-enforced access (infix): validates lambda param is only used as receiver
  // Scala 3: transparent inline def $[A, B](sa: $[A])(inline f: A => B): $[B]
  // Scala 2: def $[A, B](sa: $[A])(f: A => B): $[B]  // whitebox macro
  // Usage: (scope $ value)(f)
  def $[A, B](sa: $[A])(f: A => B): $[B]

  // Lower parent-scoped value into this scope
  def lower[A](value: parent.$[A]): $[A]

  // Escape hatch: unwrap scoped value (emits compiler warning)
  // Scala 3: inline def leak[A](inline sa: $[A]): A
  // Scala 2: def leak[A](sa: $[A]): A  // macro
  def leak[A](sa: $[A]): A

  // Creates a child scope - requires Unscoped evidence on return type
  // Same signature in both Scala 2 and 3:
  def scoped[A](f: Scope.Child[self.type] => A)(implicit ev: Unscoped[A]): A

  implicit class ScopedOps[A](sa: $[A]) {
    def get(implicit ev: Unscoped[A]): A  // extract pure data from $[A]
  }
}
```

### `Resource`

```scala
sealed trait Resource[+A]

object Resource {
  def apply[A](value: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]

  // Macro - DI entry point:
  def from[T]: Resource[T]                      // zero-dep classes
  def from[T](wires: Wire[?, ?]*): Resource[T]  // with dependency wires

  // Low-level constructors (also used by generated code):
  def shared[A](f: Scope => A): Resource[A]
  def unique[A](f: Scope => A): Resource[A]
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
  def shared[T]: Wire.Shared[?, T]  // derive from T's constructor
  def unique[T]: Wire.Unique[?, T]  // derive from T's constructor
  
  // Wrap pre-existing value (auto-finalizes if AutoCloseable):
  def apply[T](value: T): Wire.Shared[Any, T]
  
  final case class Shared[-In, +Out] extends Wire[In, Out]
  final case class Unique[-In, +Out] extends Wire[In, Out]
}
```

### `DeferHandle`

```scala
abstract class DeferHandle {
  def cancel(): Unit  // Remove the finalizer so it won't run at scope close
}
```

### `Scope.OpenScope`

```scala
case class OpenScope private[scope] (
  scope: Scope,               // the child scope (unowned, usable from any thread)
  close: () => Finalization    // explicitly close the scope and run its finalizers
)
```

---

## Mental model recap

- Use `Scope.global.scoped { scope => import scope._; ... }` to create a safe region.
- For simple resources: `allocate(Resource(value))` or `allocate(Resource.acquireRelease(...)(...))`
- For dependency injection: `allocate(Resource.from[App](Wire(config), ...))` — auto-wires concrete classes, you provide leaves and overrides.
- `(scope $ value)(f)` is macro-enforced to work with scoped values — all operations are eager.
- `.get` on `$[A]` extracts pure data when `A: Unscoped` — use for results like `String`, `Int`, etc.
- `Resource[A]` has an `Unscoped` instance, enabling chained acquisition: `allocate((scope $ pool)(_.lease()).get)`.
- `$[A] = A` at runtime — zero-cost opaque type.
- The `scoped` method requires `Unscoped[A]` evidence on the return type.
- Use `lower(parentValue)` to access parent-scoped values in child scopes.
- Use `scope.open()` for non-lexical child scopes (class-level, cross-thread); keep the `OpenScope` handle and call `close()` when done.
- Classes can accept `Scope` (full capabilities) or `Finalizer` (only `defer`) as DI-injected parameters.
- `defer` returns a `DeferHandle` for O(1) cancellation of registered finalizers.
- Return `Unscoped` types from child scopes to extract raw values.
- If it doesn't typecheck, it would have been unsafe at runtime.

**The 3 DI macro entry points:**
- `Wire.shared[T]` — shared wire from constructor
- `Wire.unique[T]` — unique wire from constructor
- `Resource.from[T](wires*)` — wire up T and all dependencies
