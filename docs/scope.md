# ZIO Blocks — Scope (`zio.blocks.scope`)

`zio.blocks.scope` is a **compile-time safe, zero-cost** resource management library for **Scala 3** (and Scala 2.13). It prevents a large class of lifetime bugs by tagging allocated values with an *unnameable*, scope-specific type and restricting how those values may be used.

At runtime the model stays simple:

- **Allocate eagerly** (no lazy thunks)
- **Register finalizers**
- **Run finalizers deterministically** when a scope closes (**LIFO** order)
- Collect finalizer failures into a `Finalization` (and throw/suppress appropriately)

## Why Scope?

Most resource bugs in Scala are "escape" bugs:

- storing a connection/stream in a field and using it after it was closed
- capturing a resource in a closure that outlives a scope
- passing a resource to code that might retain it
- mixing values from different lifetimes ("which scope owns this?")

Scope addresses these with a *tight* design:

| Feature | `zio.blocks.scope` |
|---|---|
| Compile-time leak prevention | ✓ (`scope.$[A]` + `$` macro + `Unscoped` boundary) |
| Runtime overhead | ~0 (scoped values erase to `A`) |
| Allocation model | Eager (allocation happens at `allocate`) |
| Finalization | Deterministic, LIFO, errors collected |
| Structured lifetime | Parent/child scopes, `lower` for explicit lifetime widening |
| Escape hatch | `leak` (warns) |

If you've used `try/finally`, `Using`, or ZIO's `Scope`, this is the same problem space—but optimized for **synchronous code** with **compile-time boundaries**.

---

## Quick start (Scala 3)

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable:
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")

@main def quickStart(): Unit =
  val out: String =
    Scope.global.scoped { scope =>
      import scope.*

      val db: $[Database] =
        Resource.fromAutoCloseable(new Database).allocate

      // Safe access: the lambda parameter can only be used as a receiver
      $(db)(_.query("SELECT 1"))
    }

  println(out)
```

Key points:

- `allocate(...)` returns a **scoped value**: `scope.$[Database]` (or `$[Database]` after `import scope.*`).
- You **cannot** call `db.query(...)` directly on `$[Database]`.
- You use the `$` access operator: `$(db)(...)` (or `(scope $ db)(...)` without the import).
- The `scoped` block returns a plain `String` because `String: Unscoped`.
- Finalizers run when the block exits, in **LIFO** order.

---

## Core mental model

### 1) `Scope`: finalizers + type identity

`Scope` is a finalizer registry plus a unique type identity:

- `type $[+A]` — a scope-tagged, path-dependent type (erases to `A` at runtime)
- `type Parent <: Scope` / `val parent: Parent` — the scope hierarchy

Every scope instance defines a **different** `$` type, so values from different scopes don't accidentally mix.

```scala
Scope.global.scoped { scope =>
  import scope.*
  val x: $[Int] = 1 // ok (in global, $[A] = A)
}
```

#### Global scope

`Scope.global` is the root:

- In the global scope: `type $[+A] = A` (identity)
- On the JVM: global finalizers run on shutdown via a shutdown hook
- On Scala.js: there is no shutdown hook, so global finalizers are **not** run automatically

---

### 2) Scoped values: `scope.$[A]` / `$[A]`

A value of type `scope.$[A]` means:

> "This is an `A`, but it is only valid while `scope` is alive."

Properties:

- **Zero-cost**: `$[A]` is just `A` at runtime (casts/identity)
- **Incompatible across scopes**: `outer.$[A]` is not `inner.$[A]`
- **Methods are hidden** at the type level; you must use `$` to access

#### Access operator: `(scope $ value)(f)`

The intended way to use a scoped value is:

```scala
(scope $ scopedValue)(a => a.method(...))
```

This is enforced by a macro that checks the lambda uses its parameter only in **receiver position**.

Allowed:

```scala
(scope $ db)(_.query("SELECT 1"))
(scope $ db)(d => d.query("a") + d.query("b"))
(scope $ db)(_.query("x").toUpperCase)
(scope $ db)(_.field) // field access is allowed
```

Rejected at compile time:

```scala
(scope $ db)(d => store(d))            // parameter used as an argument
(scope $ db)(d => () => d.query("x"))  // captured in a nested lambda
(scope $ db)(d => d)                   // returning the parameter
(scope $ db)(d => { val x = d; 1 })    // binding/storing the parameter itself
```

##### "Auto-unwrap" rule (`Unscoped`)

`$` *auto-unwraps* when the result type is known to be safe data:

- if `B: Unscoped` → `(scope $ sa)(f)` returns **`B`**
- otherwise → it returns **`scope.$[B]`**

```scala
Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = Resource.from[Database].allocate

  val s: String = $(db)(_.query("SELECT 1"))      // String is Unscoped => unwrapped
  val n: Int    = $(db)(_.query("x").length)      // Int is Unscoped => unwrapped
}
```

---

### 3) `Resource[A]`: acquisition + finalization

A `Resource[A]` is a **lazy description** of how to acquire a value and register cleanup in a scope. Nothing happens until you call `scope.allocate(resource)` (or `.allocate` syntax).

#### Constructors

From the source:

- `Resource(value: => A)`
  Wraps a by-name value; if it's `AutoCloseable`, `close()` is registered automatically (runtime check).
- `Resource.fromAutoCloseable(thunk: => A <: AutoCloseable)`
  Type-safe helper that registers `close()`.
- `Resource.acquireRelease(acquire: => A)(release: A => Unit)`
- `Resource.shared(f: Scope => A)`
  **Memoized + reference-counted**, thread-safe.
- `Resource.unique(f: Scope => A)`
  Fresh instance per allocation.
- `Resource.from[T]` and `Resource.from[T](wires*)` (macros)
  Constructor-based dependency injection (covered below).

#### Composition

`Resource` composes with:

- `map`
- `flatMap`
- `zip`

Finalizers remain tied to the allocation scope; in composed resources, finalizers still run LIFO.

#### Sharing vs uniqueness (important)

There are two distinct ideas:

1. **Uniqueness**: "each allocation yields a fresh instance"
   - Use `Resource.unique(...)`, or most ordinary `Resource(...)` / `acquireRelease(...)` resources.
   - Each `allocate` runs the acquisition again and registers an independent finalizer.

2. **Sharing**: "reusing the same instance across multiple allocations"
   - Use `Resource.shared(...)` (or wires/resources that convert to shared).
   - Sharing is tied to **reusing the same `Resource.Shared` value**, not "magic caching inside a scope".
   - The first allocation initializes via an `OpenScope` parented to `Scope.global`; subsequent allocations increment a reference count. When the last referencing scope closes, the shared scope is closed.

---

### 4) `Unscoped[A]`: types that may escape a scope

`Unscoped[A]` is a marker typeclass for *pure data*. It's used in two places:

1. `Scope.scoped` requires `Unscoped[A]` for the block's result type
   ⇒ prevents returning resources, closures, or scoped values.
2. `$` auto-unwraps results of type `B` when `B: Unscoped`.

Built-in instances include primitives, `String`, many collections/containers, time values, `java.util.UUID`, and `zio.blocks.chunk.Chunk` (when element types are unscoped).

#### Deriving / defining your own instances

Scala 3 (derivation via `Unscoped.derived`):

```scala
import zio.blocks.scope.*

final case class Config(debug: Boolean)
object Config:
  given Unscoped[Config] = Unscoped.derived
```

Scala 2.13:

```scala
import zio.blocks.scope.*

final case class Config(debug: Boolean)
object Config {
  implicit val unscopedConfig: Unscoped[Config] = Unscoped.derived[Config]
}
```

#### Scope boundary example

```scala
import zio.blocks.scope.*

Scope.global.scoped { parent =>
  import parent.*

  val ok: String =
    parent.scoped { child =>
      "hello" // String is Unscoped
    }

  // Does not compile: returning a resourceful value from a scoped block
  // val leaked: Database =
  //   parent.scoped { child =>
  //     import child.*
  //     Resource.fromAutoCloseable(new Database).allocate
  //   }

  ok
}
```

---

### 5) `lower`: using a parent-scoped value in a child scope

Because each scope has its own `$[A]` type, a child cannot directly use a parent's `$[A]`. Use `lower` to retag a parent-scoped value into the child:

```scala
import zio.blocks.scope.*

Scope.global.scoped { outer =>
  import outer.*

  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate

  outer.scoped { inner =>
    import inner.*
    val innerDb: $[Database] = lower(db)
    $(innerDb)(_.query("child"))
  }
}
```

This is safe because **parents always outlive children** (child finalizers run before the parent closes).

---

### 6) `defer`: manual finalizers (+ cancellation)

Use `defer` to register cleanup. It returns a `DeferHandle` you can cancel.

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val in = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  val h: DeferHandle =
    defer(in.close())

  val first = in.read()
  println(first)

  // If you already cleaned up manually:
  // h.cancel() // thread-safe, idempotent
}
```

There is also a **package-level** helper that only requires a `Finalizer`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*
  given Finalizer = scope

  defer(println("cleanup")) // uses the package-level helper
}
```

---

### 7) `open()`: non-lexical, explicitly-managed child scopes

`scoped` ties lifetime to a block. `open()` creates a child scope you close explicitly.

- The child scope is **unowned** (can be used from any thread)
- Still **linked to the parent**: parent closing will also close the child
- You must call `close()` on the handle to detach + finalize now

From `Scope.global` the returned type is `Scope.OpenScope` directly (because global `$[A] = A`):

```scala
import zio.blocks.scope.*

val os: Scope.OpenScope = Scope.global.open()

val db = os.scope.allocate(Resource.fromAutoCloseable(new Database))

// ... use db ...

os.close().orThrow()
```

Inside a child scope, `open()` returns `$[Scope.OpenScope]`. Prefer using it safely via `$`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { parent =>
  import parent.*

  val os: $[Scope.OpenScope] = open()

  $(os) { h =>
    val child = h.scope
    val db    = child.allocate(Resource.fromAutoCloseable(new Database))

    // ...
    h.close().orThrow()
  }
}
```

---

### 8) Escape hatch: `leak`

Sometimes you must hand a raw value to code that cannot work with `$[A]`. Use `leak`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate

  val raw: Database = leak(db) // emits a compiler warning
  // thirdParty(raw)
}
```

`leak` bypasses compile-time guarantees—use only for unavoidable interop. If the type is genuinely pure data, prefer adding `Unscoped` so you don't need to leak.

---

## Safety model (why leaking is prevented)

Scope's safety comes from *three reinforcing layers*.

### 1) Type barrier: scope-specific `$[A]`

Every scope has a distinct `$[A]` type. You cannot accidentally use values across scopes without an explicit conversion (`lower` for parent → child).

### 2) Controlled access: `$` macro restricts lambda usage

The `$` operator only allows using the unwrapped value as a **method/field receiver**. This prevents:

- returning the resource
- storing it in a local val/var
- passing it as an argument
- capturing it in a closure

Also note: `$` requires a **lambda literal**. Method references / variables are rejected:

```scala
// does not compile:
val f: Database => String = _.query("x")
(scope $ db)(f) // "$ requires a lambda literal ..."
```

### 3) Scope boundary rule: `scoped` requires `Unscoped[A]`

A `scoped { ... }` block can only return pure data (or `Nothing`). Resources and closures cannot escape.

**Pragmatic safety.** The type-level tagging prevents *accidental* scope misuse in normal code, but it is not a security boundary. A determined developer can bypass it via `leak` (which emits a compiler warning), unsafe casts (`asInstanceOf`), or storing scoped references in mutable state (`var`).

### Closed-scope safety (runtime)

If a scope reference escapes its `scoped { }` block and an operation is attempted after closing, Scope throws `IllegalStateException` with a detailed, actionable error message:

- **`allocate`** on a closed scope:

  ```
  ── Scope Error ─────────────────────────────────────────────────────────────────

    Cannot allocate resource: scope is already closed.

    Scope: Scope.Child

    What happened:
      A call to allocate was made on a scope whose finalizers have
      already run. The resource was never acquired.

    Common causes:
      • A scope reference escaped a scoped { } block (e.g. stored in a
        field, captured in a Future or passed to another thread).
      • close() was called on an OpenScope before all
        allocations inside it completed.

    Fix:
      Call allocate only inside a live scoped { } block, or before
      calling close() on an OpenScope.

      // Correct usage:
      Scope.global.scoped { scope =>
        import scope.*
        val db = allocate(Resource(new Database))
        $(db)(_.query("SELECT 1"))
      }

  ────────────────────────────────────────────────────────────────────────────────
  ```

- **`open()`** on a closed scope gives the same treatment, explaining that no child scope was created and directing the user to call `open()` only on a live scope.

- **`$`** on a closed scope explains that the resource may have already been released and accessing it would be undefined behaviour.

The following operations on a closed scope do **not** throw:

- `defer` — silently ignored (no-op)
- `scoped` — runs normally but creates a born-closed child scope
- `lower` — zero-cost cast, no closed check needed

### Thread ownership rule (JVM)

- Scopes created by `scoped` are **owned** by the entering thread.
- Calling `scoped` on a scope you don't own throws `IllegalStateException`.
- `open()` creates an **unowned** child scope (`isOwner == true` from any thread).

(Scala.js uses a trivial ownership model; `isOwner` is effectively always true.)

---

## Usage examples (patterns)

### Allocating and using a resource

```scala
import zio.blocks.scope.*

final class FileHandle(path: String) extends AutoCloseable:
  def readAll(): String = s"contents of $path"
  def close(): Unit = println(s"closed $path")

@main def fileExample(): Unit =
  Scope.global.scoped { scope =>
    import scope.*

    val h: $[FileHandle] =
      Resource(new FileHandle("data.txt")).allocate

    val contents: String =
      $(h)(_.readAll())

    println(contents)
  }
```

---

### Nested scopes (child can use parent, not vice versa)

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable:
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")

@main def nested(): Unit =
  Scope.global.scoped { parent =>
    import parent.*

    val parentDb: $[Database] = Resource.fromAutoCloseable(new Database).allocate

    val done: String =
      parent.scoped { child =>
        import child.*

        val db: $[Database] = lower(parentDb)
        println($(db)(_.query("SELECT 1")))

        val childDb: $[Database] = Resource.fromAutoCloseable(new Database).allocate
        println($(childDb)(_.query("SELECT 2")))

        // childDb cannot be returned to the parent (not Unscoped)
        "done"
      }

    println($(parentDb)(_.query("SELECT 3")))
    done
  }
```

Finalizers run **child first, then parent**.

---

### Chaining resource acquisition (`$[Resource[A]]` + `.allocate`)

If a method returns `Resource[A]`, `$` returns a **scoped** `Resource[A]` (because `Resource[A]` is not `Unscoped`). Allocate it without leaking:

```scala
import zio.blocks.scope.*

final class Pool extends AutoCloseable:
  def lease(): Resource[Conn] = Resource.fromAutoCloseable(new Conn)
  def close(): Unit = println("pool closed")

final class Conn extends AutoCloseable:
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("connection closed")

@main def chaining(): Unit =
  Scope.global.scoped { scope =>
    import scope.*

    val pool: $[Pool] = Resource.fromAutoCloseable(new Pool).allocate

    // $(pool)(_.lease()) : $[Resource[Conn]]
    val conn: $[Conn] =
      $(pool)(_.lease()).allocate

    val result: String =
      $(conn)(_.query("SELECT 1"))

    println(result)
  }
```

This `.allocate` comes from `Scope.ScopedResourceOps` (an extension on `$[Resource[A]]`).

---

### Allocating a bare `Resource[A]` with `.allocate`

A plain `Resource[A]` also has `.allocate` as syntax sugar for `scope.allocate(resource)`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  $(db)(_.query("SELECT 1"))
}
```

---

### Classes with `Finalizer` parameters (cleanup-only capability)

If a class only needs cleanup registration, accept a `Finalizer`. DI macros inject it automatically.

```scala
import zio.blocks.scope.*

final case class Config(url: String)
object Config:
  given Unscoped[Config] = Unscoped.derived

final class ConnectionPool(config: Config)(using Finalizer):
  private val pool = s"pool(${config.url})"
  defer(println(s"shutdown $pool"))

val poolResource: Resource[ConnectionPool] =
  Resource.from[ConnectionPool](
    Wire(Config("jdbc://localhost"))
  )

@main def finalizerInjection(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val pool: $[ConnectionPool] = poolResource.allocate
    ()
  }
```

When to prefer `Finalizer` over `Scope`:

- you only need `defer`
- you want to expose minimal power to the class

---

### Classes with `Scope` parameters (scope injection)

If a class needs to allocate resources or create child scopes, accept a `Scope`:

```scala
import zio.blocks.scope.*

final case class Config(url: String)
object Config:
  given Unscoped[Config] = Unscoped.derived

final class Connection(config: Config) extends AutoCloseable:
  def query(sql: String): String = s"[${config.url}] $sql"
  def close(): Unit = println("connection closed")

final class RequestHandler(config: Config)(using scope: Scope):
  def handle(sql: String): String =
    scope.scoped { child =>
      import child.*
      val conn: $[Connection] = Resource.fromAutoCloseable(new Connection(config)).allocate
      $(conn)(_.query(sql))
    }

val handlerResource: Resource[RequestHandler] =
  Resource.from[RequestHandler](
    Wire(Config("jdbc://localhost"))
  )

@main def scopeInjection(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val handler: $[RequestHandler] = handlerResource.allocate
    val out: String = $(handler)(_.handle("SELECT 1"))
    println(out)
  }
```

The `Scope`/`Finalizer` parameter can appear in any parameter list position; it's recognized specially by the derivation macros.

---

## Dependency injection (DI) with `Wire` + `Resource.from`

Scope includes a small constructor-based DI layer built on top of `zio.blocks.context.Context`.

### `Wire[-In, +Out]`: a dependency recipe

A `Wire` is a recipe for constructing `Out` from a `Context[In]` (and a `Scope` for finalization):

- `Wire.Shared` → converts to `Resource.shared` (ref-counted sharing)
- `Wire.Unique` → converts to `Resource.unique` (fresh instance)

#### Manual wire + Context

```scala
import zio.blocks.scope.*
import zio.blocks.context.Context

final case class Config(debug: Boolean)
object Config:
  given Unscoped[Config] = Unscoped.derived

val w: Wire.Shared[Boolean, Config] =
  Wire.shared[Config] // Boolean => Config

val deps: Context[Boolean] =
  Context(true)

@main def wireAndContext(): Unit =
  Scope.global.scoped { scope =>
    import scope.*

    val cfg: $[Config] =
      allocate(w.toResource(deps))

    val debug: Boolean =
      $(cfg)(_.debug)

    println(debug)
  }
```

#### Sharing vs uniqueness at the wire level

```scala
import zio.blocks.scope.*

val ws = Wire.shared[Config] // shared recipe
val wu = Wire.unique[Config] // unique recipe
```

The difference is realized when converting to resources (`toResource`) and allocating.

---

### `Resource.from[T](wires*)`: derive a whole object graph

`Resource.from[T](wires*)` is the primary entry point for DI. It:

- uses provided wires as overrides
- auto-creates missing wires for **concrete classes** (defaulting to shared)
- rejects unmakeable/abstract types unless you provide a wire
- detects cycles, duplicate providers, and subtype conflicts
- generates a composed `Resource[T]` via `flatMap` chains (preserving sharing/uniqueness)

Example:

```scala
import zio.blocks.scope.*

final case class Config(url: String)
object Config:
  given Unscoped[Config] = Unscoped.derived

final class Logger:
  def info(msg: String): Unit = println(msg)

final class Database(cfg: Config) extends AutoCloseable:
  def query(sql: String): String = s"[${cfg.url}] $sql"
  def close(): Unit = println("database closed")

final class Service(db: Database, logger: Logger) extends AutoCloseable:
  def run(): Unit = logger.info(s"running with ${db.query("SELECT 1")}")
  def close(): Unit = println("service closed")

val serviceResource: Resource[Service] =
  Resource.from[Service](
    Wire(Config("jdbc:postgresql://localhost/db")) // leaf value
  )

@main def di(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val svc: $[Service] = serviceResource.allocate
    $(svc)(_.run())
  }
```

#### Injecting traits via subtype wires

When a dependency is abstract, provide a wire for a concrete implementation:

```scala
import zio.blocks.scope.*

trait Logger:
  def info(msg: String): Unit

final class ConsoleLogger extends Logger:
  def info(msg: String): Unit = println(msg)

final class App(logger: Logger):
  def run(): Unit = logger.info("Hello!")

val appResource: Resource[App] =
  Resource.from[App](
    Wire.shared[ConsoleLogger] // satisfies Logger via subtyping
  )

@main def traitInjection(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val app: $[App] = appResource.allocate
    $(app)(_.run())
  }
```

#### Diamond patterns share a single instance (when appropriate)

```scala
import zio.blocks.scope.*

trait Service
final class LiveService extends Service
final class NeedsService(s: Service)
final class NeedsLive(l: LiveService)
final class App(a: NeedsService, b: NeedsLive)

val appResource: Resource[App] =
  Resource.from[App](
    Wire.shared[LiveService]
  )
// LiveService instantiations: 1
```

---

## Common runtime errors (and what they mean)

These `IllegalStateException`s are thrown when a scope operation is attempted on a closed scope. Each message identifies the scope type, explains what went wrong, lists common causes, and shows a correct usage example.

### `allocate` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot allocate resource: scope is already closed.

  Scope: Scope.Child

  What happened:
    A call to allocate was made on a scope whose finalizers have
    already run. The resource was never acquired.

  Common causes:
    • A scope reference escaped a scoped { } block (e.g. stored in a
      field, captured in a Future or passed to another thread).
    • close() was called on an OpenScope before all
      allocations inside it completed.

  Fix:
    Call allocate only inside a live scoped { } block, or before
    calling close() on an OpenScope.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val db = allocate(Resource(new Database))
      $(db)(_.query("SELECT 1"))
    }

────────────────────────────────────────────────────────────────────────────────
```

### `open()` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot open child scope: scope is already closed.

  Scope: Scope.Child

  What happened:
    A call to open() was made on a scope whose finalizers have
    already run. No child scope was created.

  Common causes:
    • A scope reference escaped a scoped { } block and open()
      was called after the block exited.
    • close() was called on the parent OpenScope before
      open() was called on it.

  Fix:
    Call open() only on a live (not yet closed) scope.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val child = open()
      $(child)(_.scope.allocate(Resource(new Database)))
    }

────────────────────────────────────────────────────────────────────────────────
```

### `$` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot access scoped value: scope is already closed.

  Scope: Scope.Child

  What happened:
    The $ operator was called on a scope whose finalizers have
    already run. The underlying resource may have been released.
    Accessing it would be undefined behavior.

  Common causes:
    • A $[A] value or its owning scope escaped a scoped { }
      block (e.g. captured in a Future, stored in a field, or
      passed to another thread).
    • close() was called on an OpenScope that still has
      live $[A] values being accessed.

  Fix:
    Ensure all $ calls occur strictly within the scoped { }
    block that owns the value, and that the scope has not been closed.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val db = allocate(Resource(new Database))
      $(db)(_.query("SELECT 1"))  // $ used inside the block
    }

────────────────────────────────────────────────────────────────────────────────
```

---

## Common compile errors (and what they mean)

This module produces two kinds of compile-time feedback:

- **Plain macro aborts** for unsafe `$` usage
- **ASCII-rendered** errors/warnings for DI derivation + leak warnings (via `internal.ErrorMessages`)

### Unsafe use inside `$`

Typical messages include:

```
Unsafe use of scoped value: the lambda parameter cannot be passed as an argument to a function or method.
```

Other variants:

- `Unsafe use of scoped value: the lambda parameter cannot be captured in a nested lambda or closure.`
- `Unsafe use of scoped value: the lambda parameter must only be used as a method receiver ...`
- `$ requires a lambda literal: (scope $ x)(a => a.method()). Method references and variables are not supported.`

### Not a class (`Wire.shared/unique` on a trait / abstract)

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot derive Wire for MyTrait: not a class.

  Hint: Use Wire.Shared / Wire.Unique directly.

───────────────────────────────────────────────────────────────────────────────
```

### No primary constructor

```
── Scope Error ─────────────────────────────────────────────────────────────────

  MyType has no primary constructor.

  Hint: Use Wire.Shared / Wire.Unique directly
        with a custom construction strategy.

───────────────────────────────────────────────────────────────────────────────
```

### `Resource.from[T]` used when `T` has dependencies

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Resource.from[MyService] cannot be derived.

  MyService has dependencies that must be provided:
    • Config
    • Logger

  Hint: Use Resource.from[MyService](wire1, wire2, ...)
        to provide wires for all dependencies.

───────────────────────────────────────────────────────────────────────────────
```

### Unmakeable type (primitives, functions, collections)

```
── Scope Error ─────────────────────────────────────────────────────────────────

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

───────────────────────────────────────────────────────────────────────────────
```

### Abstract type (trait / abstract class dependency)

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot auto-create Logger

  This type is abstract (trait or abstract class).

  Required by:
  └── App

  Fix: Provide a wire for a concrete implementation:

    Resource.from[...](
      Wire.shared[ConcreteImpl],  // provides Logger
      ...
    )

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate providers (ambiguous wires)

```
── Scope Error ────────────────────────────────────────────────────────────────

  Multiple providers for Service

  Conflicting wires:
    1. LiveService
    2. TestService

  Hint: Remove duplicate wires or use distinct wrapper types.

───────────────────────────────────────────────────────────────────────────────
```

### Dependency cycle

```
── Scope Error ────────────────────────────────────────────────────────────────

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

───────────────────────────────────────────────────────────────────────────────
```

### Subtype conflict (related dependency types)

```
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency type conflict in MyService

  FileInputStream is a subtype of InputStream.

  When both types are dependencies, Context cannot reliably distinguish
  them. The more specific type may be retrieved when the more general
  type is requested.

  To fix this, wrap one or both types in a distinct wrapper:

    case class WrappedInputStream(value: InputStream)
  or
    opaque type WrappedInputStream = InputStream

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate parameter types in a constructor

```
── Scope Error ────────────────────────────────────────────────────────────────

  Constructor of App has multiple parameters of type String

  Context is type-indexed and cannot supply distinct values for the same type.

  Fix: Wrap one parameter in an opaque type to distinguish them:

    opaque type FirstString = String
  or
    case class FirstString(value: String)

───────────────────────────────────────────────────────────────────────────────
```

### Leak warning

```
── Scope Warning ───────────────────────────────────────────────────────────────

  leak(db)
       ^
       |

  Warning: db is being leaked from scope zio.blocks.scope.Scope.Child[...].
  This may result in undefined behavior.

  Hint:
     If you know this data type is not resourceful, then add an Unscoped
     instance for it so you do not need to leak it.

───────────────────────────────────────────────────────────────────────────────
```

---

## API reference (from source)

Examples below use Scala 3 syntax. Scala 2.13 has equivalent APIs, but macro signatures differ slightly (notably `$`'s return type encoding).

### `Scope`

```scala
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific
```

Associated types and hierarchy:

- `type $[+A]`
- `type Parent <: Scope`
- `val parent: Parent`
- `def isClosed: Boolean`
- `def isOwner: Boolean`

Core operations:

```scala
def scoped[A](f: (child: Scope.Child[this.type]) => A)(using Unscoped[A]): A

def allocate[A](resource: Resource[A]): $[A]
def allocate[A <: AutoCloseable](value: => A): $[A]

infix transparent inline def $[A, B](sa: $[A])(inline f: A => B): B | $[B]

def lower[A](value: parent.$[A]): $[A]

override def defer(f: => Unit): DeferHandle

def open(): $[Scope.OpenScope]

inline def leak[A](inline sa: $[A]): A
```

Notes:

- `$` requires a **lambda literal** and enforces safe receiver-only usage.
- `$` returns `B` if `Unscoped[B]` exists; otherwise returns `$[B]`.
- If the scope is closed, `$`, `allocate`, and `open` throw `IllegalStateException` with a detailed error message. `defer` and `lower` are unaffected.

Syntax enrichments available after `import scope.*` inside a scope:

```scala
implicit class ScopedResourceOps[A](sr: $[Resource[A]]):
  def allocate: $[A]

implicit class ResourceOps[A](r: Resource[A]):
  def allocate: $[A]
```

---

### `Scope.global`

```scala
object Scope:
  object global extends Scope
```

Properties:

- `type $[+A] = A` (identity)
- `isOwner` always returns `true`
- JVM: finalizers run at shutdown via a shutdown hook
- Scala.js: shutdown hook is not available

---

### `Scope.OpenScope`

```scala
case class OpenScope(scope: Scope, close: () => Finalization)
```

- `scope`: the child scope
- `close()`: detaches from parent, runs child finalizers (LIFO), returns `Finalization`

---

### `Finalizer`

```scala
trait Finalizer:
  def defer(f: => Unit): DeferHandle
```

A minimal capability interface for registering cleanup.

Also available as a package-level helper:

```scala
def defer(finalizer: => Unit)(using fin: Finalizer): DeferHandle
```

---

### `DeferHandle`

```scala
abstract class DeferHandle:
  def cancel(): Unit
```

- `cancel()` is thread-safe and idempotent
- cancellation is O(1) (true removal from a concurrent map)

---

### `Finalization`

```scala
final class Finalization(val errors: zio.blocks.chunk.Chunk[Throwable]):
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def orThrow(): Unit
  def suppress(initial: Throwable): Throwable

object Finalization:
  val empty: Finalization
  def apply(errors: Chunk[Throwable]): Finalization
```

---

### `Resource[+A]`

```scala
sealed trait Resource[+A]:
  def map[B](f: A => B): Resource[B]
  def flatMap[B](f: A => Resource[B]): Resource[B]
  def zip[B](that: Resource[B]): Resource[(A, B)]
```

Companion constructors:

```scala
object Resource:
  def apply[A](value: => A): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def shared[A](f: Scope => A): Resource[A]
  def unique[A](f: Scope => A): Resource[A]

  inline def from[T]: Resource[T]
  inline def from[T](inline wires: Wire[?, ?]*): Resource[T]
```

Notes:

- `Resource.from[T]` (no args) only works when `T` has **no non-scope dependencies** (constructor params may include `Scope`/`Finalizer`).
- Use `Resource.from[T](wires*)` to provide/override dependencies and derive the full graph.

---

### `Wire[-In, +Out]`

```scala
sealed trait Wire[-In, +Out]:
  def isShared: Boolean
  def isUnique: Boolean = !isShared

  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]

  def toResource(deps: zio.blocks.context.Context[In]): Resource[Out]
```

Wires:

```scala
object Wire:
  final case class Shared[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out]
  final case class Unique[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out]

  def apply[T](t: T): Wire.Shared[Any, T]

  transparent inline def shared[T]: Wire.Shared[?, T]
  transparent inline def unique[T]: Wire.Unique[?, T]
```

Notes:

- `Wire(t)` wraps a pre-existing value; if it's `AutoCloseable`, `close()` is registered automatically when used.

---

### `Unscoped[A]`

```scala
trait Unscoped[A]

object Unscoped:
  inline given derived[A](using scala.deriving.Mirror.Of[A]): Unscoped[A]
  // plus many built-in givens (primitives, collections, time, UUID, Chunk, ...)
```

---

## Practical guidance (summary)

- Allocate in a scope: `resource.allocate` (inside `Scope.global.scoped { scope => import scope.* ... }`)
- Use scoped values only through: `(scope $ value)(...)`
- Return only `Unscoped` data from `scoped` blocks
- Use `lower` to use parent values inside a child
- If `$` returns `$[Resource[A]]`, call `.allocate` on it (scoped resource chaining)
- Use `open()` for explicitly-managed, cross-thread capable scopes
- Use `leak` only when interop forces it; prefer `Unscoped` for pure data
