# ZIO Blocks — Scope (compile-time safe resource management)

`zio.blocks.scope` provides **compile-time verified resource safety** for synchronous code by tagging values with an unnameable, type-level **scope identity**. Values allocated in a scope can only be used when you hold a compatible `Scope`, and values allocated in a *child* scope cannot be returned to the parent in a usable form.

**Structured scopes.** Scopes follow the structured-concurrency philosophy: child scopes are nested within parent scopes, resources are tied to the lifetime of the scope that allocated them, and cleanup happens deterministically when the scope exits (finalizers run LIFO). This "nesting = lifetime" structure provides clear ownership boundaries in addition to compile-time leak prevention.

If you've used `try/finally`, `Using`, or ZIO `Scope`, this library lives in the same problem space, but it focuses on:

- **Compile-time prevention of scope leaks**
- **Zero-cost opaque type** (`$[A]` is the scoped type, equal to `A` at runtime)
- **Simple, synchronous lifecycle management** (finalizers run LIFO on scope close)
- **Eager evaluation** (all operations execute immediately, no deferred thunks)

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

  // scope.use applies a function to the scoped value, returning a scoped result
  val result: $[String] = scope.use(db)(_.query("SELECT 1"))
  println(result)  // $[String] = String at runtime, prints directly
}
```

Key things to notice:

- `allocate(...)` returns a **scoped** value: `$[Database]` (or `scope.$[Database]`)
- `$[A] = A` at runtime — zero-cost opaque type, no boxing
- All operations are **eager** — values are computed immediately, no lazy thunks
- Use `scope.use(value)(f)` to work with scoped values; returns `$[B]`
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
    type $[+A] = A
    type Parent = global.type
  }
}
```

- The global scope is intended to live for the lifetime of the process.
- Its finalizers run on JVM shutdown.

---

### 2) Scoped values: `$[+A]`

`$[+A]` (or `scope.$[+A]`) is a path-dependent opaque type representing a value of type `A` that is locked to a specific scope. It is covariant in `A`.

- **Runtime representation:** `$[A] = A` — zero-cost opaque type, no boxing or wrapping
- **Key effect:** methods on `A` are hidden at the type level; you can't call `a.method` directly
- **All operations are eager:** `allocate(resource)` acquires the resource **immediately** and returns a scoped value
- **Access paths:**
  - `scope.use(a)(f)` to apply a function and get `$[B]`

#### `ScopedOps`: `map` and `flatMap` on `$[A]`

`Scope` provides an implicit class `ScopedOps[A]` that adds `map` and `flatMap` to `$[A]` values, enabling for-comprehension syntax:

```scala
Scope.global.scoped { scope =>
  import scope._

  val x: $[Int] = $(42)
  val y: $[String] = x.map(_.toString)
  val z: $[String] = x.flatMap(v => $(s"value: $v"))
}
```

- `sa.map(f: A => B): $[B]` — applies `f` to the unwrapped value, re-wraps the result
- `sa.flatMap(f: A => $[B]): $[B]` — applies `f` to the unwrapped value (where `f` returns a scoped value)
- All operations are **eager** (zero-cost)

#### Scala 2 note

In Scala 2, the `scoped` method must be called with a lambda literal. Passing a variable or method reference is not supported due to macro limitations:

```scala
// ✅ OK: lambda literal
Scope.global.scoped { scope => ... }

// ❌ ERROR in Scala 2 (works in Scala 3):
val f: Scope.Child[_] => Any = scope => ...
Scope.global.scoped(f)
```

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
    - the first allocation initializes the value and collects finalizers
    - each allocating scope registers a decrement finalizer
    - when the reference count reaches zero, the collected finalizers run

**Important clarification:** sharing is **not** "memoized within a Wire graph" or "within a scope" by magic. Sharing happens **within the specific `Resource.Shared` instance** you reuse.

---

### 4) `Unscoped`: marking pure data types

The `Unscoped[A]` typeclass marks types as pure data that don't hold resources. The `scoped` method requires `Unscoped[A]` evidence on the return type to ensure only safe values can exit a scope.

**Built-in Unscoped types:**
- Primitives: `Int`, `Long`, `Boolean`, `Double`, etc.
- `String`, `Unit`, `Nothing`
- Collections of Unscoped types

**Custom Unscoped types:**
```scala
case class Config(debug: Boolean)
object Config {
  given Unscoped[Config] = Unscoped.derived
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
    scope.use(db)(_.query("SELECT 1"))

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

Additionally, the opaque type hides `A`'s methods at the type level — you can't call `db.query(...)` directly on a `$[Database]`. The only access route is `scope.use(value)(f)`.

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

  // scope.use applies function to scoped value, returns $[String]
  val contents: $[String] = scope.use(h)(_.readAll())
  println(contents)  // $[String] = String at runtime
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
    println(child.use(db)(_.query("SELECT 1")))

    val childDb: $[Database] = allocate(Resource(new Database))

    // You can use childDb *inside* the child:
    println(child.use(childDb)(_.query("SELECT 2")))

    // Return an Unscoped value
    "done"
    
    // But you cannot return childDb to the parent:
    // $[Database] has no Unscoped instance
  }

  // parentDb is still usable here:
  println(parent.use(parentDb)(_.query("SELECT 3")))
}
```

---

### Chaining resource acquisition

Since all operations are eager, you work with scoped values directly:

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
  import scope._

  val pool: $[Pool] = allocate(Resource.from[Pool])

  // Allocate a connection from the pool's resource
  val connection: $[Connection] = allocate(scope.use(pool)(_.lease))

  val result: $[String] = scope.use(connection)(_.query("SELECT 1"))

  println(result)
}
// Output: result: SELECT 1
// Then: connection closed, pool closed (LIFO)
```

---

### Registering cleanup manually with `defer`

Use `defer` when you already have a value and just need to register cleanup.

```scala
import zio.blocks.scope._

Scope.global.scoped { scope =>
  import scope._

  val handle = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  defer { handle.close() }

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
  import scope._
  val pool = allocate(resource)
  // pool.shutdown() will be called when scope closes
}
```

Why `Finalizer` instead of `Scope`?
- `Finalizer` is the minimal interface—it only has `defer`
- Classes that need cleanup should not have access to `allocate` or `use`
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
  import scope._

  val cfg: $[Config] = allocate(w.toResource(deps))

  val debug: $[Boolean] = scope.use(cfg)(_.debug)

  println(debug)  // $[Boolean] = Boolean at runtime
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
  scope.use(svc)(_.run())
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
  scope.use(app)(_.run())
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
  def defer(f: => Unit): Unit

  // Apply function to scoped value, returns scoped result
  def use[A, B](scoped: $[A])(f: A => B): $[B]

  // Construct a scoped value from a raw value
  def $[A](a: A): $[A]

  // Lower parent-scoped value into this scope
  def lower[A](value: parent.$[A]): $[A]

  // Creates a child scope - requires Unscoped evidence on return type
  // Scala 3:
  def scoped[A](f: (child: Scope.Child[self.type]) => child.$[A])(using Unscoped[A]): A
  // Scala 2:
  def scoped[A: Unscoped](f: Scope.Child[_] => A): A  // macro

  implicit class ScopedOps[A](sa: $[A]) {
    def map[B](f: A => B): $[B]
    def flatMap[B](f: A => $[B]): $[B]
  }

  implicit def wrapUnscoped[A: Unscoped](a: A): $[A]
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
  
  final case class Shared[-In, +Out] extends Wire[In, Out]
  final case class Unique[-In, +Out] extends Wire[In, Out]
}
```

---

## Mental model recap

- Use `Scope.global.scoped { scope => import scope._; ... }` to create a safe region.
- For simple resources: `allocate(Resource(value))` or `allocate(Resource.acquireRelease(...)(...))`
- For dependency injection: `allocate(Resource.from[App](Wire(config), ...))` — auto-wires concrete classes, you provide leaves and overrides.
- Use `scope.use(value)(f)` to work with scoped values — all operations are eager.
- `$[A] = A` at runtime — zero-cost opaque type.
- The `scoped` method requires `Unscoped[A]` evidence on the return type.
- Use `lower(parentValue)` to access parent-scoped values in child scopes.
- Return `Unscoped` types from child scopes to extract raw values.
- If it doesn't typecheck, it would have been unsafe at runtime.

**The 3 macro entry points:**
- `Wire.shared[T]` — shared wire from constructor
- `Wire.unique[T]` — unique wire from constructor
- `Resource.from[T](wires*)` — wire up T and all dependencies
