# ZIO Blocks — `scope` module

The `zio.blocks.scope` module provides **compile-time safe, zero-cost resource management** for Scala 2 & Scala 3.

It guarantees (via typing + macros) that resourceful values cannot accidentally escape their lifetime, while keeping the runtime model simple:

- allocate resources **eagerly**
- register finalizers
- run finalizers deterministically at scope close in **LIFO** order

It follows a "structured scopes" model (similar spirit to structured concurrency): child scopes are nested inside parent scopes, and **nesting defines lifetime**—child finalizers always run before parent finalizers.

---

## 1) Introduction & motivation

Typical resource safety problems in Scala come from *values escaping their intended lifetime*:

- storing a connection/stream in a field and using it after it was closed
- capturing a resource in a closure that outlives the scope
- passing a resource to "somewhere else" that might retain it
- mixing resources from different lifetimes

`zio.blocks.scope` prevents these mistakes using:

1. **Lexical scoping** via `scope.scoped { child => ... }` which always runs finalizers on exit (even on exceptions).
2. A **scope-tagged value type** `scope.$[A]` that represents "an `A` that is only valid in this scope".
3. A **macro-enforced access operator** `scope.$(scopedValue)(a => a.method(...))` that statically restricts how the underlying `A` can be used.
4. A return-type constraint `Unscoped[A]` on `scoped` blocks, ensuring only *pure data* can leave a scope.

### Zero-cost model

- Scoped values are **zero-cost**: at runtime, `scope.$[A]` is just `A` (identity / cast).
- Operations are **eager**:
  - `allocate` acquires immediately.
  - `$` executes the lambda immediately (unless the scope is closed).

---

## 2) Quick start

```scala
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(Resource(new Database))

  // Safe access: the lambda parameter can only be used as a receiver
  val result: String = scope.$(db)(_.query("SELECT 1"))
  println(result)
}
```

Key points:

- `allocate(...)` returns a **scoped** value: `scope.$[Database]`.
- You cannot call `db.query(...)` directly.
- You must use the scope's `$` operator: `scope.$(db)(_.query(...))`.
- The scoped block returns a **plain** `String` (because `String: Unscoped`).
- There is **no** `.get` on scoped values; access is through `$` (or `leak`, the escape hatch).

---

## 3) Core concepts

### 3.1 `Scope`

A `Scope` is a finalizer registry + a type identity:

- it tracks finalizers (cleanup actions)
- it defines a scope-specific type constructor `type $[+A]`

Scopes form a parent/child hierarchy:

- `Scope.global` is the root
- `scope.scoped { child => ... }` creates a `Scope.Child` linked to `scope`

Important operations:

- `scoped`: create a child scope and run finalizers when the block exits
- `allocate`: acquire a `Resource[A]` (or an `AutoCloseable`) and register cleanup
- `$`: safely use a scoped value
- `lower`: convert a *parent-scoped* value into the child scope
- `defer`: register a finalizer (returns a cancelable `DeferHandle`)
- `open()`: create an explicitly-managed child scope you can use non-lexically (and from any thread)

#### `Scope.global`

- Intended to live for the lifetime of the process.
- On the JVM, its finalizers run on shutdown via a shutdown hook.
- On Scala.js, there is no shutdown hook, so global finalizers do not run automatically.
- In the global scope, `$[A] = A` at runtime *and* at the type level (identity).

### 3.2 `scope.$[A]` (scoped values)

`scope.$[A]` means: "an `A` that is valid only inside `scope`".

Properties:

- Different scopes produce **incompatible** `$` types, preventing accidental mixing at compile time.
- At runtime it is **just `A`** (identity / cast).
- Methods on `A` are hidden at the type level; you can't call them directly.

> There is **no** `.get` on `$[A]`. The supported way to access is `scope.$(value)(...)`, or `scope.leak(value)` (escape hatch).

#### Auto-unwrap

`$` **auto-unwraps** results:

- if the lambda returns `B` and `B: Unscoped`, then `$` returns `B` (plain)
- otherwise it returns `scope.$[B]` (still scoped)

```scala
Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(Resource.from[Database])

  val result: String = scope.$(db)(_.query("SELECT 1"))    // auto-unwrapped
  val len: Int       = scope.$(db)(_.query("x").length)    // auto-unwrapped
}
```

#### Allocating a *scoped* `Resource`

If a method returns `Resource[A]`, `$` returns `$[Resource[A]]` (since `Resource[A]` is not `Unscoped`). Use `.allocate` on it to acquire:

```scala
Scope.global.scoped { scope =>
  import scope._

  val pool: $[Pool] = Resource.from[Pool].allocate

  // scope.$(pool)(_.lease()) returns $[Resource[Connection]]
  // .allocate acquires it, returning $[Connection]
  val conn: $[Connection] = scope.$(pool)(_.lease()).allocate

  scope.$(conn)(_.query("SELECT 1"))
}
```

### 3.3 `Resource[A]`

A `Resource[A]` is a **lazy description** of how to acquire an `A` and register its cleanup with a scope.

- Resources do nothing until `scope.allocate(resource)` (or `resource.allocate`) is called.
- Acquisition is eager at allocation time.
- Resources support composition: `map`, `flatMap`, `zip` (finalizers are registered into the allocating scope).

Main constructors:

- `Resource(a)` (by-name)
  - if `a` is `AutoCloseable`, registers `close()` automatically
- `Resource.fromAutoCloseable(a)`
- `Resource.acquireRelease(acquire)(release)`
- `Resource.shared(scope => a)` (reference-counted memoization)
- `Resource.unique(scope => a)` (fresh instance per allocation)
- `Resource.from[T](...)` macro (constructor-based DI; see below)

#### Sharing vs uniqueness

There are two distinct ideas:

1) **Uniqueness**: "each allocation yields a fresh instance"
- Use `Resource.unique(...)`, or most ordinary `Resource(...)` / `acquireRelease(...)` resources.
- Each `allocate` runs the acquisition again and registers an independent finalizer.

2) **Sharing**: "reusing the same instance across multiple allocations"
- Use `Resource.shared(...)` (or wires/resources that convert to shared).
- Sharing is tied to **reusing the same `Resource.shared` value**, not "magic caching inside a scope".

### 3.4 `Unscoped[A]` (safe-to-escape types)

`Unscoped[A]` is a marker typeclass meaning: `A` is pure data and may escape a scope boundary.

- `scoped` requires the block result type `A` to have `Unscoped[A]`.
- `$` auto-unwraps results of type `B` when `B: Unscoped`.

Defaults include primitives, `String`, many collections/containers (when element types are `Unscoped`), `Chunk`, time values, etc.

For your own data types:

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

Why it matters: `scoped` blocks cannot return scoped values, scopes, or closures that might capture scoped values.

```scala
Scope.global.scoped { parent =>
  import parent._

  // ✅ OK: String is Unscoped
  val ok: String = parent.scoped { _ => "hello" }

  // ❌ COMPILE ERROR: $[Database] has no Unscoped instance
  // val bad = parent.scoped { child =>
  //   import child._
  //   allocate(Resource(new Database))  // can't escape
  // }

  ok
}
```

### 3.5 `lower`

`lower` allows a child scope to use values allocated in the parent scope:

```scala
Scope.global.scoped { outer =>
  import outer._

  val db: $[Database] = allocate(Resource(new Database))

  outer.scoped { inner =>
    import inner._
    val innerDb: $[Database] = lower(db)
    inner.$(innerDb)(_.query("child"))
  }
}
```

This is safe because **parents always outlive children**: child finalizers run before the parent closes.

### 3.6 `Wire` (DI recipe)

A `Wire[-In, +Out]` is a recipe for constructing services from dependencies available in a `Context[In]`.

Two flavors:

- `Wire.Shared`: converts to `Resource.shared` (memoized + ref-counted)
- `Wire.Unique`: converts to `Resource.unique` (fresh each time)

Wires can be:

- derived by macros `Wire.shared[T]` / `Wire.unique[T]` (constructor-based)
- manually created via `Wire.Shared((scope, ctx) => ...)` or `Wire.Unique(...)`
- created from an existing value via `Wire(t)` (a value wire; shared; auto-closes if `AutoCloseable`)

---

## 4) Safety model (why leaking is prevented)

`scope` enforces safety using **three layers**.

### 4.1 Type barrier: `scope.$[A]` is scope-specific

A scoped value can't be accidentally used with another scope's operations because the `$` type differs per scope instance. This stops "cross-scope mixing" at compile time.

### 4.2 Controlled access: `$` macro restricts lambda usage

The `$` macro verifies the lambda parameter is used **only in method-receiver position**.

Allowed:

```scala
scope.$(db)(_.query("SELECT 1"))
scope.$(db)(d => d.query("a") + d.query("b"))
scope.$(db)(_.field)
```

Rejected (compile time):

```scala
scope.$(db)(d => store(d))           // parameter used as an argument
scope.$(db)(d => () => d.query("x")) // parameter captured by nested lambda
scope.$(db)(d => d)                  // returning the parameter
scope.$(db)(d => { val x = d; 1 })   // binding the parameter itself
```

### 4.3 Scope boundary rule: `scoped` requires `Unscoped[A]` result

A `scoped` block can only return types known to be safe data.

**Pragmatic safety.** The type-level tagging prevents *accidental* scope misuse in normal code, but it is not a security boundary. A determined developer can bypass it via `leak` (which emits a compiler warning), unsafe casts (`asInstanceOf`), or storing scoped references in mutable state (`var`).

### Closed-scope defense (runtime guard)

If a scope reference escapes incorrectly and is used after it closes, operations become no-ops returning default values:

- `$`, `allocate`, `open`, `lower` return defaults (`null` / `0` / `false` etc.)
- `$` does **not** execute its function when closed
- `defer` is silently ignored

### Thread ownership rule

- Scopes created by `scoped` are **owned** by the creating thread.
- Calling `scoped` on a scope you don't own throws `IllegalStateException`.
- `Scope.open()` creates an **unowned** child scope (usable from any thread).

---

## 5) Usage examples

### 5.1 Basic allocation + usage

```scala
import zio.blocks.scope._

final class FileHandle extends AutoCloseable {
  def readAll(): String = "data"
  def close(): Unit = println("file closed")
}

val out: String = Scope.global.scoped { scope =>
  import scope._
  val fh: $[FileHandle] = allocate(Resource(new FileHandle))
  scope.$(fh)(_.readAll()) // auto-unwrapped to String
}
```

### 5.2 Nested scopes (child can use parent, not vice versa)

```scala
Scope.global.scoped { parent =>
  import parent._
  val db: $[Database] = allocate(Resource(new Database))

  parent.scoped { child =>
    import child._
    val childDb: $[Database] = lower(db)
    child.$(childDb)(_.query("SELECT 1"))
  }
}
```

Finalizers run **child first, then parent** (LIFO across scopes).

### 5.3 Chaining resource acquisition (resource-returning methods)

```scala
Scope.global.scoped { scope =>
  import scope._

  val pool: $[Pool] = Resource.from[Pool].allocate

  // scope.$(pool)(_.lease()) returns $[Resource[Connection]]
  // .allocate acquires it, returning $[Connection]
  val conn: $[Connection] = scope.$(pool)(_.lease()).allocate

  val result: String = scope.$(conn)(_.query("SELECT 1"))
  println(result)
}
// connection closed, then pool closed (LIFO)
```

This `.allocate` is provided by `ScopedResourceOps` on `$[Resource[A]]`.

### 5.4 Registering cleanup manually with `defer`

`defer` registers a finalizer and returns a `DeferHandle` that supports **O(1) cancellation**.

```scala
Scope.global.scoped { scope =>
  import scope._
  val handle: DeferHandle = defer { println("cleanup") }
  // optionally cancel: handle.cancel()
  ()
}
```

Finalizers run in LIFO order at scope close; canceled finalizers are removed.

### 5.5 Classes with `Finalizer` parameters

If a class only needs to register cleanup (not allocate/lower/open), accept a `Finalizer`:

```scala
class ConnectionPool(config: Config)(implicit finalizer: Finalizer) {
  private val pool = createPool(config)
  finalizer.defer { pool.shutdown() }
}

// The wire/resource macros automatically inject the Finalizer:
val resource = Resource.from[ConnectionPool](Wire(Config("jdbc://localhost")))
```

### 5.6 Classes with `Scope` parameters (scope injection)

If a class needs full scope capabilities—creating child scopes, allocating sub-resources:

```scala
class RequestHandler(config: Config)(implicit scope: Scope) {
  def handle(request: String): String = {
    scope.scoped { child =>
      import child._
      val conn = allocate(Resource(new Connection(config)))
      child.$(conn)(_.query(request))
    }
  }
}
```

### 5.7 Non-lexical scopes with `open()`

Use `open()` when you need an explicitly-managed scope, potentially across threads.

```scala
// From Scope.global, open() returns OpenScope directly ($[A] = A for global):
val os: Scope.OpenScope = Scope.global.open()

// Use the child scope to allocate resources:
val db = os.scope.allocate(Resource(new Database))

// ... use db across multiple method calls or threads ...

// Explicitly close when done (runs finalizers, returns Finalization):
os.close()
```

Key properties:
- The child scope is **unowned**: `isOwner` always returns `true`, so it can be used from any thread
- The child is still **parent-linked**: if the parent closes first, the child's finalizers still run
- Calling `close()` cancels the parent's safety finalizer to avoid double-finalization

### 5.8 Dependency injection with `Resource.from[T](wires...)`

`Resource.from[T]` derives a single `Resource[T]` from `T`'s constructor and all its dependencies.

**What you must provide:**
- Leaf values: primitives, configs, pre-existing instances via `Wire(value)`
- Abstract types: traits/abstract classes via `Wire.shared[ConcreteImpl]`
- Overrides: when you want `unique` instead of the default `shared`

**What is auto-created:**
- Concrete classes with accessible primary constructors (default: `Wire.shared`)

```scala
final case class Config(url: String)

final class Database(cfg: Config) extends AutoCloseable {
  def query(sql: String): String = s"[${cfg.url}] $sql"
  def close(): Unit = println("database closed")
}

final class Service(db: Database) extends AutoCloseable {
  def run(): Unit = println(db.query("SELECT 1"))
  def close(): Unit = println("service closed")
}

// Only provide leaf values — the rest is auto-wired:
val serviceResource: Resource[Service] =
  Resource.from[Service](
    Wire(Config("jdbc:postgresql://localhost/db"))
  )

Scope.global.scoped { scope =>
  import scope._
  val svc: $[Service] = serviceResource.allocate
  scope.$(svc)(_.run())
}
// Output: [jdbc:postgresql://localhost/db] SELECT 1
// Then: service closed, database closed (LIFO order)
```

#### Injecting traits via subtype wires

```scala
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
  Resource.from[App](Wire.shared[ConsoleLogger])

Scope.global.scoped { scope =>
  import scope._
  val app: $[App] = appResource.allocate
  scope.$(app)(_.run())
}
```

#### Diamond reuse (one instance satisfies multiple paths)

```scala
trait Service
class LiveService extends Service
class NeedsService(s: Service)
class NeedsLive(l: LiveService)
class App(a: NeedsService, b: NeedsLive)

// One LiveService instance satisfies both Service and LiveService dependencies:
val appResource = Resource.from[App](Wire.shared[LiveService])
// count of LiveService instantiations: 1
```

### 5.9 Interop escape hatch: `leak`

Sometimes you must hand a raw value to code that cannot work with `$[A]` types.

```scala
Scope.global.scoped { scope =>
  import scope._
  val db: $[Database] = allocate(Resource(new Database))

  val raw: Database = leak(db) // emits a compiler warning
  // thirdParty(raw)
}
```

**Warning:** leaking bypasses compile-time guarantees. The value may be used after its scope closes. Use only when unavoidable. If the type is truly safe data, prefer adding an `Unscoped` instance.

---

## 6) Common compile errors

The macros produce actionable compile-time error messages with ASCII diagrams and hints.

### 6.1 Not a class (derivation on trait / abstract class)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Cannot derive Wire for MyTrait: not a class.

  Hint: Use Wire.Shared / Wire.Unique directly.

────────────────────────────────────────────────────────────────────────────
```

### 6.2 Unmakeable type (primitives, functions, collections)

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

### 6.3 Abstract type (need a subtype wire)

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

### 6.4 Duplicate providers (ambiguous wires)

```
── Scope Error ──────────────────────────────────────────────────────────────

  Multiple providers for Service

  Conflicting wires:
    1. LiveService
    2. TestService

  Hint: Remove duplicate wires or use distinct wrapper types.

────────────────────────────────────────────────────────────────────────────
```

### 6.5 Dependency cycle

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

### 6.6 Dependency subtype conflict

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

### 6.7 Duplicate parameter types in constructor

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

### 6.8 Leak warning

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

## 7) API reference

### 7.1 `Scope`

```scala
sealed abstract class Scope extends Finalizer {
  type $[+A]         // = A at runtime (zero-cost)
  type Parent <: Scope
  val parent: Parent

  def allocate[A](resource: Resource[A]): $[A]
  def allocate[A <: AutoCloseable](value: => A): $[A]
  def defer(f: => Unit): DeferHandle
  def open(): $[Scope.OpenScope]
  def lower[A](value: parent.$[A]): $[A]
  def isClosed: Boolean
  def isOwner: Boolean

  // Macro-enforced access — auto-unwraps Unscoped results:
  // Scala 3: transparent inline def $[A, B](sa: $[A])(inline f: A => B)
  // Scala 2: def $[A, B](sa: $[A])(f: A => B): Any  // whitebox macro
  def $[A, B](sa: $[A])(f: A => B): B | $[B]  // B if B: Unscoped, $[B] otherwise

  // Escape hatch (emits compiler warning):
  def leak[A](sa: $[A]): A

  // Creates a child scope — requires Unscoped evidence on return type:
  // Scala 3: def scoped[A](f: (child: Scope.Child[this.type]) => A)(using Unscoped[A]): A
  // Scala 2: def scoped[A](f: Scope.Child[this.type] => A)(implicit Unscoped[A]): A
  def scoped[A: Unscoped](f: Scope.Child[this.type] => A): A

  implicit class ScopedResourceOps[A](sr: $[Resource[A]]) {
    def allocate: $[A]  // acquire a scoped Resource without extracting it
  }

  implicit class ResourceOps[A](r: Resource[A]) {
    def allocate: $[A]  // sugar for scope.allocate(resource)
  }
}
```

### 7.2 `Resource`

```scala
sealed trait Resource[+A]

object Resource {
  def apply[A](value: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]
  def shared[A](f: Scope => A): Resource[A]
  def unique[A](f: Scope => A): Resource[A]

  // Macro — DI entry point:
  def from[T]: Resource[T]                      // zero-dep classes
  def from[T](wires: Wire[?, ?]*): Resource[T]  // with dependency wires
}
```

### 7.3 `Wire`

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
  def toResource(deps: Context[In]): Resource[Out]
}

object Wire {
  final case class Shared[-In, +Out](makeFn: (Scope, Context[In]) => Out)
  final case class Unique[-In, +Out](makeFn: (Scope, Context[In]) => Out)

  def apply[T](t: T): Wire.Shared[Any, T]  // value wire; auto-closes if AutoCloseable
  def shared[T]: Wire.Shared[?, T]          // macro: derive from T's constructor
  def unique[T]: Wire.Unique[?, T]          // macro: derive from T's constructor
}
```

### 7.4 `Finalizer`

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

### 7.5 `DeferHandle`

```scala
abstract class DeferHandle {
  def cancel(): Unit  // O(1), thread-safe, idempotent
}
```

### 7.6 `Finalization`

```scala
final class Finalization(val errors: Chunk[Throwable]) {
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def orThrow(): Unit
  def suppress(initial: Throwable): Throwable
}
```

### 7.7 `Scope.OpenScope`

```scala
case class OpenScope(scope: Scope, close: () => Finalization)
```

---

## Summary

- Allocate with `resource.allocate` or `scope.allocate(resource)`.
- Use scoped values only through `scope.$(value)(...)`.
- Return only `Unscoped` data from `scoped` blocks.
- Use `lower` to share parent values with children.
- Use `.allocate` on `$[Resource[A]]` for resource-returning methods.
- Use `open()` for explicitly-managed, non-lexical scopes (cross-thread capable).
- Use `leak` only when you must interop with unscoped APIs.
- `$[A] = A` at runtime — zero-cost opaque type.
