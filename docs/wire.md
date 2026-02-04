---
id: wire
title: "Wire"
---

# ZIO Blocks Wire

**Lightweight, compile-time verified dependency injection for Scala.**

## What Is Wire?

Wire is a minimalist dependency injection library that leverages Scala macros to wire up your application's dependency graph at compile time. It builds on ZIO Blocks Context to provide type-safe service lookup and composition.

Wire supports both **Scala 2.13** and **Scala 3.x**. The core functionality is identical across versions.

The core insight: dependency injection is really about **three things**:

1. **Constructing services** from their dependencies
2. **Managing lifecycles** (cleanup when done)
3. **Sharing vs. isolation** (singletons vs. fresh instances)

Wire handles all three with a tiny API surface.

## Why Wire?

### The Problem

Typical Scala applications end up with one of these approaches:

**Manual wiring** — correct but tedious:
```scala
val config = new Config()
val db = new Database(config)
val cache = new Cache(config)
val userService = new UserService(db, cache)
val app = new App(userService)
// ... and don't forget to close everything in reverse order
```

**Runtime DI frameworks** — convenient but opaque:
```scala
// Magic happens at runtime, errors at startup, hard to understand
val injector = Guice.createInjector(new AppModule())
val app = injector.getInstance(classOf[App])
```

**ZIO ZLayer** — powerful but heavyweight:
```scala
// Requires buying into ZIO's effect system
val layer: ZLayer[Any, Nothing, App] = 
  Config.live >>> (Database.live ++ Cache.live) >>> UserService.live >>> App.live
```

### The Solution

Wire gives you compile-time verification without the ceremony:

```scala
Autowire.run[App](shared[App]) { _.run() }
```

That's it. The macro inspects `App`'s constructor, discovers it needs `UserService`, which needs `Database` and `Cache`, which need `Config` — and wires everything up. If any dependency is missing or ambiguous, you get a compile error.

**What you get:**
- Compile-time errors if dependencies are missing or ambiguous
- Automatic lifecycle management via `Scope`
- Explicit control over sharing (singleton vs. fresh)
- No reflection, no runtime surprises
- Works with any effect system (or none)

## What Wire Is Not

Wire is deliberately **not**:

- **An effect system**: Wire doesn't manage async, concurrency, or errors. Use ZIO, Cats Effect, or plain Scala for that.
- **A configuration library**: Wire constructs objects; it doesn't read config files. Pair it with your preferred config library.
- **A replacement for ZLayer**: If you're fully invested in ZIO, ZLayer offers tighter integration. Wire is for when you want DI without the effect system dependency.
- **Runtime flexible**: The dependency graph is fixed at compile time. No runtime module swapping (use different builds for that).

---

## Quick Start

### Minimal Example

```scala
import zio.blocks.wire._

class Config(val dbUrl: String)
class Database(config: Config)
class App(db: Database) {
  def run(): Unit = println("Running")
}

@main def main(): Unit =
  Autowire.run[App](shared[App]) { _.run() }
```

The `shared[App]` macro recursively discovers and wires all dependencies.

### With Lifecycle Management

```scala
import zio.blocks.wire._

class Config(val dbUrl: String, val cacheSize: Int)

// AutoCloseable — cleanup auto-registered
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def close(): Unit = pool.close()
}

// Manual cleanup via defer
class Cache(config: Config)(using Scope) {
  private val evictor = startEvictorThread()
  defer {
    evictor.interrupt()
    evictor.join()
  }
}

class UserService(db: Database, cache: Cache)

class App(userService: UserService) {
  def run(): Unit = println(s"Hello, ${userService.getUser("123").name}")
}

@main def main(): Unit =
  Autowire.run[App](shared[App]) { _.run() }
  // Cleanup runs automatically in reverse order:
  // Cache (evictor stopped), Database (pool closed)
```

---

## Core Concepts

### Scope

A `Scope` manages resource lifecycle with structured cleanup:

```scala
sealed abstract class Scope {
  def defer(finalizer: => Unit): Unit  // Register cleanup
  def child: Scope                      // Create child scope
  def close(): Unit                     // Run all finalizers (LIFO)
}

object Scope {
  def global: Scope  // Root scope, closes on JVM shutdown
}
```

**Key semantics:**
- Finalizers run in **reverse order** of registration (LIFO)
- Parent close **closes children first**, then runs own finalizers
- `close()` is **idempotent** — safe to call multiple times

**The global scope:**

`Scope.global` never closes during normal execution — it finalizes via JVM shutdown hook. Use it for application-lifetime services:

```scala
Autowire.run[App](shared[App])(using Scope.global) { _.run() }
```

For tests, create a child scope for deterministic cleanup:

```scala
val testScope = Scope.global.child
try { /* test */ } finally { testScope.close() }
```

### Wire

A `Wire[-In, +Out]` describes how to construct services:

```scala
sealed trait Wire[-In, +Out] {
  def construct: Scope ?=> Context[In] => Context[Out]
  def isShared: Boolean
}

object Wire {
  final case class Shared[-In, +Out](...) extends Wire[In, Out]
  final case class Unique[-In, +Out](...) extends Wire[In, Out]
}
```

- **`In`** — Dependencies required (e.g., `Database & Config`)
- **`Out`** — Services produced (e.g., `UserService`)

### shared vs. unique

| Variant | Behavior | Use Case |
|---------|----------|----------|
| `shared[T]` | One instance per `Autowire` call | Database pools, configs, caches |
| `unique[T]` | Fresh instance each time needed | Transactions, request handlers |

```scala
Autowire.run[App](
  shared[Database],     // One Database for the whole graph
  unique[Transaction],  // Fresh Transaction for each consumer
  shared[App]
) { _.run() }
```

### Autowire

`Autowire` resolves the dependency graph at compile time:

```scala
object Autowire {
  inline def apply[T](inline wires: Wire[?, ?]*)(using Scope): Context[T]
  inline def run[T](inline wires: Wire[?, ?]*)(inline use: T => Unit)(using Scope): Unit
}
```

**`Autowire.run`** creates a child scope, wires dependencies, runs your code, then cleans up:

```scala
Autowire.run[App](shared[App]) { app => app.run() }
// Equivalent to:
// val child = scope.child
// try { use(Autowire[App](...)(using child).get[App]) } finally { child.close() }
```

---

## Cleanup Patterns

Wire handles cleanup automatically for `AutoCloseable` types, or you can manage it manually with `defer`.

### AutoCloseable (Automatic)

```scala
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def close(): Unit = pool.close()
}
// Macro generates: scope.defer(instance.close())
```

### Manual Cleanup with defer

```scala
class Cache(config: Config)(using Scope) {
  private val store = new LRUCache(config.cacheSize)
  private val evictor = new Thread(() => evictionLoop())
  evictor.start()
  
  defer {
    evictor.interrupt()
    evictor.join()
    store.clear()
  }
}
// Macro passes scope, you handle cleanup
```

### Precedence

| Constructor | Cleanup Strategy |
|-------------|------------------|
| `extends AutoCloseable`, no `Scope` param | Macro auto-generates `defer(_.close())` |
| Takes `Scope` (implicit or explicit) | You handle cleanup manually |
| Both `AutoCloseable` AND takes `Scope` | You handle cleanup (Scope wins) |

---

## Request Scoping

For request-scoped services, use `Autowire.run` to create a child scope per request:

```scala
class App(userService: UserService)(using appScope: Scope) {
  def handleRequest(request: Request): Response =
    Autowire.run[RequestHandler](
      Wire.value(request),       // Inject the request value
      shared[RequestHandler]
    ) { handler =>
      handler.handle()
    }
    // Transaction, RequestHandler cleaned up here
}
```

Each request gets its own scope. The `UserService` from the parent scope is available; the `RequestHandler` and any request-scoped dependencies are created fresh and cleaned up after.

### Caution: Scope Confusion with Multiple Lifecycles

When multiple scopes are in play, all have the same type (`Scope`), making it easy to use the wrong one:

```scala
def processUpload(file: UploadedFile)(using appScope: Scope): Result = {
  // Request scope — socket lives for this request
  val requestScope = appScope.child
  val socket = Socket.connect(validationServiceUrl)
  requestScope.defer(socket.close())
  
  // Operation scope — temp file lives only for validation
  val opScope = requestScope.child
  val tempFile = TempFile.create()
  opScope.defer(tempFile.delete())
  
  // Write to temp file, validate via socket
  tempFile.write(file.bytes)
  val result = validate(socket, tempFile, opScope)
  
  opScope.close()  // Clean up temp file
  
  if (result.valid) {
    // BUG: Using wrong scope! This should use requestScope, not opScope
    // opScope is already closed, but this compiles fine
    finalize(socket, opScope)  // Runtime error or undefined behavior
  }
  
  requestScope.close()
  result
}

def validate(socket: Socket, tempFile: TempFile, scope: Scope): ValidationResult = {
  // BUG: Registering cleanup on wrong scope!
  // tempFile cleanup should be on opScope, but we might pass requestScope
  scope.defer(tempFile.delete())  // Compiles fine, wrong behavior
  socket.send(tempFile.readAll())
}

def finalize(socket: Socket, scope: Scope): Unit = {
  // Which scope is this? We can't tell from the type
  scope.defer(socket.close())  // Might be wrong scope
}
```

**The problem**: All scopes are `Scope` — the type system can't distinguish them. You must rely on discipline and testing to catch:
- Passing a closed scope to a function
- Registering cleanup on the wrong scope
- Mixing up which resource belongs to which scope

---

## Testing

Swap implementations by providing different wires:

```scala
// Production
Autowire.run[UserService](shared[UserService]) { svc =>
  svc.getUser("123")
}

// Test with mocks
Autowire.run[UserService](
  Wire.value(new MockDatabase()),
  Wire.value(new MockCache()),
  shared[UserService]
) { svc =>
  assert(svc.getUser("123") == expectedUser)
}
```

Or define reusable wire sets:

```scala
val prodWires = Seq(shared[Database], shared[Cache], shared[UserService])
val testWires = Seq(
  Wire.value(new MockDatabase()),
  Wire.value(new MockCache()),
  shared[UserService]
)

Autowire.run[UserService](testWires*) { svc => /* test */ }
```

---

## Wireable for Traits

Traits don't have constructors, so `shared[Database]` fails unless there's a `Wireable[Database]`:

```scala
trait Database {
  def query(sql: String): ResultSet
}

object Database {
  given Wireable[Database] = Wireable.from[DatabaseImpl]
}

class DatabaseImpl(config: Config) extends Database with AutoCloseable {
  def query(sql: String): ResultSet = ???
  def close(): Unit = ???
}

// Now works:
Autowire.run[App](shared[Database], shared[App]) { _.run() }
```

The `Wireable` trait:

```scala
trait Wireable[T] {
  def wire: Wire[?, T]
}

object Wireable {
  inline def from[Impl <: T, T]: Wireable[T]
}
```

**Precedence:** Explicit wires in `Autowire` override `Wireable` in scope.

---

## Manual Wires

For complex construction logic, create wires directly:

```scala
// Scala 3
val configWire: Wire.Shared[Any, Config] = Wire.Shared { ctx =>
  Context(Config.load(sys.env("CONFIG_PATH")))
}

val dbWire: Wire.Shared[Config, Database] = Wire.Shared { ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  defer(db.close())
  Context(db)
}

// Scala 2
val dbWire: Wire.Shared[Config, Database] = Wire.Shared { implicit scope => ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  defer(db.close())
  Context(db)
}
```

Use manual wires alongside macro-generated ones:

```scala
Autowire.run[App](configWire, dbWire, shared[App]) { _.run() }
```

---

## Compile-Time Errors

Wire provides rich error messages with dependency graphs.

**Missing dependency:**

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Missing wire for Config

  Dependency graph:

    UserService
    └── Database
        └── Config  ← missing

  Config is required by:
    → Database (shared)

  Hint: Add shared[Config] or provide a Wire[?, Config]

────────────────────────────────────────────────────────────────────────────────
```

**Ambiguous provider:**

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Multiple wires provide Config

  Conflicting wires:
    1. shared[Config] at MyApp.scala:15
    2. shared[Config] at MyApp.scala:16

  Hint: Remove duplicate wires or use distinct types

────────────────────────────────────────────────────────────────────────────────
```

**Dependency cycle:**

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Dependency cycle detected

  Cycle:
    ┌─────────────┐
    │             ▼
    ServiceA ──► ServiceB
    ▲             │
    └─────────────┘

  Break the cycle by:
    • Introducing an interface/trait
    • Using lazy initialization
    • Restructuring dependencies

────────────────────────────────────────────────────────────────────────────────
```

---

## API Reference

### Scope

```scala
sealed abstract class Scope {
  def defer(finalizer: => Unit): Unit
  def child: Scope
  def close(): Unit
}

object Scope {
  def global: Scope
}
```

### Wire

```scala
// Scala 3
sealed trait Wire[-In, +Out] {
  def construct: Scope ?=> Context[In] => Context[Out]
  def isShared: Boolean
  def shared: Wire[In, Out]
  def unique: Wire[In, Out]
  def >>>[Out2](that: Wire[In & Out, Out2]): Wire[In, Out & Out2]
  def ++[In2 <: In, Out2](that: Wire[In2, Out2]): Wire[In2, Out & Out2]
}

// Scala 2
sealed trait Wire[-In, +Out] {
  def construct(implicit scope: Scope): Context[In] => Context[Out]
  // ... same methods
}

object Wire {
  final case class Shared[-In, +Out](...) extends Wire[In, Out]
  final case class Unique[-In, +Out](...) extends Wire[In, Out]
  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire[Any, T]
}
```

### Autowire

```scala
object Autowire {
  inline def apply[T](inline wires: Wire[?, ?]*)(using Scope): Context[T]
  inline def run[T](inline wires: Wire[?, ?]*)(inline use: T => Unit)(using Scope): Unit
}
```

### Wireable

```scala
trait Wireable[T] {
  def wire: Wire[?, T]
}

object Wireable {
  inline def from[Impl <: T, T]: Wireable[T]
}
```

### Top-Level Functions

```scala
inline def shared[T]: Wire[???, T]
inline def unique[T]: Wire[???, T]
def defer(finalizer: => Unit)(using Scope): Unit
```

---

## Comparison with Alternatives

| Feature | Wire | ZIO ZLayer | Guice | MacWire |
|---------|------|------------|-------|---------|
| Compile-time verification | ✅ | ✅ | ❌ | ✅ |
| Lifecycle management | ✅ | ✅ | ✅ | ❌ |
| Effect system required | ❌ | ✅ (ZIO) | ❌ | ❌ |
| Async construction | ❌* | ✅ | ❌ | ❌ |
| Scoped instances | ✅ | ✅ | ✅ | ❌ |

*Async construction can be added later if needed.

---

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-wire" % "@VERSION@"
```

Wire depends on:
- `zio-blocks-context` (for `Context`)
- `zio-blocks-typeid` (for type identity)

---

## Summary

- **`shared[T]`** — Macro-generated wire, memoized
- **`unique[T]`** — Macro-generated wire, fresh each time
- **`Autowire.run[T](wires...) { t => ... }`** — Build, use, cleanup
- **`Scope`** — Structured lifecycle via `defer`, `child`, `close`
- **`defer { ... }`** — Register cleanup on current scope
- **`Wire.value(x)`** — Inject a specific value
- **`Wireable[T]`** — Enable `shared[T]` for traits
