---
id: wire
title: "Wire"
---

# ZIO Blocks Wire

**Lightweight, compile-time verified dependency injection for Scala.**

## What Is Wire?

Wire is a minimalist dependency injection library that leverages Scala macros to wire up your application's dependency graph at compile time. It builds on ZIO Blocks Context to provide type-safe service lookup and composition.

Wire supports both **Scala 2.13** and **Scala 3.x**. Scala 3 offers a nicer experience with context functions for implicit scope threading, but the core functionality is identical across versions.

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
val ctx = Autowire[App](
  shared[Config],
  shared[Database],
  shared[Cache],
  shared[UserService],
  shared[App]
)(using Scope.global)

ctx.get[App].run()
```

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

## Quick Example

Here's a complete example showing Wire's features:

```scala
import zio.blocks.wire._
import zio.blocks.context._

// Simple service — no cleanup needed
class Config(val dbUrl: String, val cacheSize: Int)

// AutoCloseable — macro auto-generates scope.defer(_.close())
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def query(sql: String): ResultSet = pool.query(sql)
  def close(): Unit = pool.close()
}

// Manual scope control — for complex cleanup logic
class Cache(config: Config)(using scope: Scope) {
  private val store = new LRUCache(config.cacheSize)
  private val evictor = new Thread(() => evictionLoop())
  evictor.start()
  scope.defer {
    evictor.interrupt()
    evictor.join()
    store.clear()
  }
  
  def get(key: String): Option[String] = store.get(key)
  def put(key: String, value: String): Unit = store.put(key, value)
}

class UserService(db: Database, cache: Cache) {
  def getUser(id: String): User = 
    cache.get(id).map(User.parse).getOrElse {
      val user = db.query(s"SELECT * FROM users WHERE id = $id")
      cache.put(id, user.toString)
      user
    }
}

// Server with request-scoped handling
class WebServer(userService: UserService)(using appScope: Scope) {
  val server = startHttpServer { request =>
    // Create child scope for this request
    Autowire.run[RequestHandler](
      Wire.Shared[Any, Request] { _ => Context(request) },
      shared[Transaction],  // One transaction per request
      shared[RequestHandler]
    ) { handler =>
      handler.handle()
    }  // Transaction cleaned up automatically
  }
  appScope.defer(server.stop())
}

// Wire it up
@main def run(): Unit = {
  Autowire.run[WebServer](
    shared[Config],
    shared[Database],
    shared[Cache],
    shared[UserService],
    shared[WebServer]
  )(using Scope.global) { server =>
    server.server.awaitTermination()
  }  // Cleans up in reverse order: WebServer, Database
}
```

### What's Happening

1. **`shared[Config]`** — Macro inspects `Config`'s constructor, sees no dependencies, creates a wire that produces `Config`
2. **`shared[Database]`** — Macro sees `Database` needs `Config`, creates a wire with input type `Config`
3. **`Autowire[WebServer]`** — Macro analyzes all wires, builds a dependency graph, verifies completeness, generates construction code
4. **Lifecycle** — `Database` registers cleanup via `AutoCloseable`; when scope closes, finalizers run in reverse order

---

## Core Concepts

### Scope

A `Scope` is a **structured scope** — a lifecycle boundary that manages cleanup in a hierarchical, predictable way. If you're familiar with structured concurrency, this is the same idea applied to resource lifecycle.

```scala
sealed abstract class Scope {
  def defer(finalizer: => Unit): Unit  // Register cleanup
  def child: Scope                      // Create child scope
  def close(): Unit                     // Run all finalizers (LIFO order)
  
  // Stack-based scoping
  def push(): Unit                      // Push a new scope level onto stack
  def pop(): Unit                       // Pop and close the top scope level
}

object Scope {
  def global: Scope  // The immortal root scope (see below)
}
```

**Structured scope semantics:**

- **Parent-close closes children**: When a scope closes, it first closes all its children, then runs its own finalizers. This guarantees no resource leaks at shutdown.
- **Child-close does not close parent**: Closing a child scope only affects that child and its descendants.
- **Finalizers run LIFO**: Within a scope, finalizers run in reverse order of registration.
- **`close()` is idempotent**: Calling close multiple times is safe; subsequent calls are no-ops.
- **`Scope` is sealed**: You cannot extend it, only use the provided implementations.

**The global scope:**

`Scope.global` is a special singleton scope that **never closes** during normal execution. It only finalizes via JVM shutdown hook. Use it as the root for long-lived application services:

```scala
// Application services live in global scope
val ctx = Autowire[App](
  shared[Database],
  shared[Cache],
  shared[App]
)(using Scope.global)

// These services live until JVM shutdown
```

For tests or controlled shutdown, create your own root scope instead:

```scala
val testScope = Scope.global.child
try {
  // ... run tests ...
} finally {
  testScope.close()  // Deterministic cleanup
}
```

**Stack-based scoping with push/pop:**

For situations where you want temporary scope levels without creating separate `Scope` instances:

```scala
def processItems(items: List[Item])(using scope: Scope): Unit = {
  for (item <- items) {
    scope.push()  // New scope level
    try {
      val resource = acquireResource(item)
      scope.defer(resource.release())  // Registered on current level
      process(resource)
    } finally {
      scope.pop()  // Closes current level, runs its finalizers
    }
  }
}
```

`push`/`pop` is useful for loop iterations or recursive processing where you want cleanup after each iteration but don't need independent `Scope` instances.

**child vs push/pop:**

| Operation | Use Case |
|-----------|----------|
| `child` | Independent lifetime boundary, can be passed around, used concurrently |
| `push`/`pop` | Sequential bracketing on same scope (loops, recursion) |

**Thread safety:**

All `Scope` methods (`defer`, `child`, `close`) are thread-safe. However, `push`/`pop` behavior is undefined when called concurrently from multiple threads on the same scope — use `child` for concurrent scenarios.

**Usage patterns:**

```scala
// Request scope using child
def handleRequest(request: Request)(using appScope: Scope): Response = {
  val requestScope = appScope.child
  try {
    val ctx = Autowire[RequestHandler](wires...)(using requestScope)
    ctx.get[RequestHandler].handle(request)
  } finally {
    requestScope.close()  // Cleans up request-scoped resources
  }
}

// Request scope using push/pop (simpler for sequential processing)
def handleRequest(request: Request)(using scope: Scope): Response = {
  scope.push()
  try {
    val ctx = Autowire[RequestHandler](wires...)(using scope)
    ctx.get[RequestHandler].handle(request)
  } finally {
    scope.pop()  // Cleans up this request's resources
  }
}
```

### Wire

A `Wire[-In, +Out]` describes how to construct services:

```scala
// Scala 3
sealed trait Wire[-In, +Out] {
  def construct: Scope ?=> Context[In] => Context[Out]
  def isShared: Boolean
  
  def shared: Wire[In, Out]  // Convert to shared (memoized)
  def unique: Wire[In, Out]  // Convert to unique (fresh each time)
}

// Scala 2 (equivalent encoding)
sealed trait Wire[-In, +Out] {
  def construct(implicit scope: Scope): Context[In] => Context[Out]
  def isShared: Boolean
  
  def shared: Wire[In, Out]
  def unique: Wire[In, Out]
}
```

- **`In`** — The dependencies required (intersection type, e.g., `Database & Config`)
- **`Out`** — The services produced (intersection type, e.g., `UserService`)
- **`Scope`** — Passed implicitly (context function in Scala 3, implicit param in Scala 2)

### shared vs. unique

The two wire variants control memoization within an `Autowire` call:

| Variant | Behavior | Use Case |
|---------|----------|----------|
| `shared[T]` | Memoized — one instance per `Autowire` call | Database pools, configs, caches |
| `unique[T]` | Fresh — new instance each time it's needed | Request handlers, transactions |

```scala
Autowire[App](
  shared[Database],     // One Database for the whole graph
  unique[Transaction],  // Fresh Transaction for each service that needs one
  shared[UserService],
  shared[OrderService]
)(using scope)
```

### Composition Operators

Wires compose with two operators:

```scala
// Sequential: feed output of first into input of second
val dbThenUser: Wire[Config, Database & UserService] = 
  dbWire >>> userServiceWire

// Merge: both wires see same input, outputs combined
val dbAndCache: Wire[Config, Database & Cache] = 
  dbWire ++ cacheWire
```

These are useful for manual wire construction, but `Autowire` handles composition automatically.

### Diamond Dependencies

When multiple services depend on the same type (e.g., `A` needs `B` and `C`, both need `D`):

- **`shared[D]`**: One `D` instance is created and reused for both `B` and `C`
- **`unique[D]`**: Two separate `D` instances are created (one per injection edge)

This follows naturally from the memoization semantics: shared wires are cached by output type within an `Autowire` call; unique wires are never cached.

### Subtype Satisfaction

Wire uses `Context`'s subtype-aware lookup (powered by `TypeId.isSubtypeOf`). If you provide `shared[DatabaseImpl]` and something needs `Database`, it will be satisfied if `DatabaseImpl <: Database`. However, using `Wireable[Database]` is preferred for clarity.

---

## The Macros

### `shared[T]` and `unique[T]`

These macros generate wires for a type:

```scala
inline def shared[T]: Wire[???, T] = ${ sharedImpl[T] }
inline def unique[T]: Wire[???, T] = ${ uniqueImpl[T] }
```

**What the macro does:**

1. **Check for Wireable**: First, look for `given Wireable[T]` in scope. If found, use it.
2. **Fall back to constructor**: If no Wireable, inspect `T`'s primary constructor.
3. **Flatten parameter lists**: All parameter lists are flattened into a single set of dependencies.
4. **Extract dependencies**: Parameter types become the wire's `In` type (excluding `Scope`).
5. **Handle Scope**: If constructor takes `Scope` (regular or implicit param), inject the current scope.
6. **Handle cleanup** (see below).
7. **Return** `Wire.Shared[In, T]` or `Wire.Unique[In, T]`.

**Wireable for interfaces:**

Traits and abstract classes don't have constructors, so `shared[Database]` would fail — unless there's a `Wireable[Database]` in scope. Define one in the companion object:

```scala
trait Database {
  def query(sql: String): ResultSet
}

object Database {
  // Default implementation for Database
  given Wireable[Database] = Wireable.from[DatabaseImpl]
}

class DatabaseImpl(config: Config) extends Database with AutoCloseable {
  def query(sql: String): ResultSet = ???
  def close(): Unit = ???
}

// Now this works naturally:
Autowire[App](
  shared[Config],
  shared[Database],  // Uses DatabaseImpl via Wireable
  shared[App]
)
```

The `Wireable` trait:

```scala
trait Wireable[T] {
  def wire: Wire[?, T]  // Note: output type is T, not the implementation type
}

object Wireable {
  // Derive from a concrete class, but provide as trait type T
  inline def from[Impl <: T, T]: Wireable[T] = ${ fromImpl[Impl, T] }
}
```

**Important:** `Wireable[Database]` produces a `Wire[?, Database]`, even if it constructs `DatabaseImpl` internally. This ensures the trait type is registered in the `Context`, enabling consumers to depend on `Database` (the interface), not `DatabaseImpl` (the implementation).

**Precedence:** If you provide an explicit `Wire[?, T]` in the Autowire call, it takes precedence over any `Wireable[T]` in scope.

**Cleanup handling — AutoCloseable vs implicit Scope:**

The macro uses one of two strategies for cleanup, but **not both**:

| Constructor | Cleanup Strategy |
|-------------|------------------|
| `T extends AutoCloseable` (no `Scope` param) | Macro generates `scope.defer(instance.close())` automatically |
| `T` takes implicit `Scope` | You handle cleanup manually via `scope.defer(...)` in your constructor |
| `T extends AutoCloseable` AND takes `Scope` | You handle cleanup manually (implicit Scope wins) |

If your class takes an implicit `Scope`, the macro assumes you're handling cleanup yourself and won't generate any automatic cleanup code. This gives you full control for complex cases — including calling `close()` with additional logic.

```scala
// AutoCloseable: macro handles cleanup
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def close(): Unit = pool.close()
}
// Macro generates: scope.defer(instance.close())

// Implicit Scope: you handle cleanup
class Cache(config: Config)(using scope: Scope) {
  private val evictor = startEvictorThread()
  scope.defer {
    evictor.interrupt()
    evictor.join()
  }
}
// Macro passes scope but generates no automatic defer
```

**Scala 2 vs Scala 3:**

In Scala 3, the scope is passed via context function (`Scope ?=>`), making it available as a `using` parameter automatically.

In Scala 2, the scope is passed as an implicit parameter. The generated wire looks like:

```scala
// Scala 3
Wire.Shared[Config, Database] { ctx =>
  Context(new Database(ctx.get[Config]))
}

// Scala 2
Wire.Shared[Config, Database] { implicit scope => ctx =>
  Context(new Database(ctx.get[Config]))
}
```

### `Autowire[T]`

The `Autowire` object provides macros to wire up the entire dependency graph:

```scala
object Autowire {
  // Build context only
  inline def apply[T](inline wires: Wire[?, ?]*)(using Scope): Context[T]
  
  // Build and use in one step
  inline def run[T](inline wires: Wire[?, ?]*)(inline use: T => Unit)(using Scope): Unit
}
```

**`Autowire.apply`** builds the dependency graph and returns a `Context[T]`:

```scala
val ctx = Autowire[App](
  shared[Config],
  shared[Database],
  shared[App]
)(using Scope.global)

val app = ctx.get[App]
app.run()
```

**`Autowire.run`** is a convenience for the common pattern of wiring up, using, then cleaning up:

```scala
Autowire.run[App](
  shared[Config],
  shared[Database],
  shared[App]
) { app =>
  app.run()  // Runs in a child scope
}            // Child scope closed after (cleanup runs)
```

This is equivalent to:
```scala
val child = scope.child
try {
  val ctx = Autowire[App](wires...)(using child)
  use(ctx.get[App])
} finally {
  child.close()
}
```

Note: `Autowire.run` creates a **child scope**, so the caller's scope (including `Scope.global`) is never closed.

**What the macro does:**

1. Collects all wires and their input/output types
2. Builds a dependency graph
3. Verifies:
   - No missing dependencies
   - No ambiguous providers (multiple wires producing the same type)
   - No cycles
4. Topologically sorts for construction order
5. Generates construction code with memoization for `shared` wires
6. Returns `Context[T]`

**Compile-time errors:**

Wire provides rich compile-time error messages with ASCII dependency graphs and colors to help you quickly identify and fix wiring problems.

**Missing dependency:**

```scala
Autowire[UserService](shared[Database])
```

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Missing wire for Config

  Dependency graph:

    UserService
    └── Database
        └── Config  ← missing

  Config is required by:
    → Database (shared)
    → UserService (shared)

  Hint: Add shared[Config] or provide a Wire[?, Config]
        If Config is a trait, add a Wireable[Config] to its companion object.

────────────────────────────────────────────────────────────────────────────────
```

**Ambiguous provider:**

```scala
Autowire[App](shared[Config], shared[Config], shared[App])
```

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Multiple wires provide Config

  Dependency graph:

    App
    └── Config  ← ambiguous (2 providers)

  Conflicting wires:
    1. shared[Config] at MyApp.scala:15
    2. shared[Config] at MyApp.scala:16

  Hint: Remove duplicate wires or use distinct types

────────────────────────────────────────────────────────────────────────────────
```

**Dependency cycle:**

```scala
Autowire[ServiceA](shared[ServiceA], shared[ServiceB])  // A needs B, B needs A
```

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

**Complex missing dependencies:**

```scala
Autowire[WebServer](shared[WebServer], shared[UserService])
```

```
── Autowire Error ──────────────────────────────────────────────────────────────

  Missing wires for 3 types

  Dependency graph:

    WebServer
    ├── UserService
    │   ├── Database
    │   │   └── Config  ← missing
    │   └── Cache
    │       └── Config  ← missing (same)
    └── Config  ← missing (same)

  Missing types:
    ✗ Config     — required by Database, Cache, WebServer
    ✗ Database   — required by UserService
    ✗ Cache      — required by UserService

  Provided wires:
    ✓ WebServer
    ✓ UserService

  Hint: Add the missing wires:
    shared[Config],
    shared[Database],
    shared[Cache]

────────────────────────────────────────────────────────────────────────────────
```

**Color support:**

Wire detects whether it's running in an interactive terminal and uses ANSI colors automatically:
- 🟢 Green for provided/satisfied dependencies
- 🔴 Red for missing dependencies  
- 🟡 Yellow for ambiguous/conflicting wires
- 🔵 Blue for hints and suggestions

Colors are disabled in non-interactive environments (CI, build logs, IDE error panels) for readability.

---

## Manual Wires

For complex construction logic, create wires directly:

```scala
// Scala 3 syntax — scope is available via context function

// Manual shared wire
val configWire: Wire.Shared[Any, Config] = Wire.Shared { ctx =>
  val config = Config.load(sys.env("CONFIG_PATH"))
  Context(config)
}

// Manual wire with cleanup
val dbWire: Wire.Shared[Config, Database] = Wire.Shared { ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  summon[Scope].defer(db.close())  // Access scope via summon
  Context(db)
}

// Wire that produces multiple services
val infraWire: Wire.Shared[Config, Database & Cache] = Wire.Shared { ctx =>
  val config = ctx.get[Config]
  val db = Database.connect(config.dbUrl)
  val cache = Cache.create(config.cacheSize)
  val scope = summon[Scope]
  scope.defer(db.close())
  scope.defer(cache.close())
  Context(db, cache)
}
```

```scala
// Scala 2 syntax — scope is an implicit parameter

val dbWire: Wire.Shared[Config, Database] = Wire.Shared { implicit scope => ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  scope.defer(db.close())
  Context(db)
}
```

Use manual wires in Autowire:

```scala
Autowire[App](
  configWire,
  infraWire,
  shared[UserService],
  shared[App]
)(using scope)
```

---

## Nested Scopes

For request-scoped or transaction-scoped services, use child scopes or `Autowire.run`:

```scala
class Server(userService: UserService)(using appScope: Scope) {
  def handleRequest(request: Request): Response = {
    // Option 1: Using Autowire.run (recommended)
    Autowire.run[RequestHandler](
      shared[RequestContext],  // Shared within this request
      shared[Transaction],     // One transaction per request
      shared[RequestHandler]
    ) { handler =>
      handler.handle(request)
    }  // Cleans up Transaction, RequestContext automatically
    
    // Option 2: Manual child scope
    val requestScope = appScope.child
    try {
      val ctx = Autowire[RequestHandler](
        shared[RequestContext],
        shared[Transaction],
        shared[RequestHandler]
      )(using requestScope)
      ctx.get[RequestHandler].handle(request)
    } finally {
      requestScope.close()
    }
  }
}
```

**Pattern:** Services wired with a parent scope (like `UserService`) are passed as input context; services wired with the child scope are request-scoped.

---

## Testing

Wire makes testing straightforward — just provide different wires:

```scala
// Production wires
val prodWires = Seq(
  shared[RealDatabase],
  shared[RealCache],
  shared[UserService]
)

// Test wires with mocks
val testWires = Seq(
  Wire.Shared[Any, Database] { _ => Context(new MockDatabase()) },
  Wire.Shared[Any, Cache] { _ => Context(new MockCache()) },
  shared[UserService]
)

// In tests
val testScope = Scope.global.child
val ctx = Autowire[UserService](testWires*)(using testScope)
val userService = ctx.get[UserService]
// userService uses mocks

// Clean up after test
testScope.close()
```

---

## API Reference

### Scope

```scala
sealed abstract class Scope {
  def defer(finalizer: => Unit): Unit  // Register cleanup
  def child: Scope                      // Create child scope (structured)
  def close(): Unit                     // Close this scope and all children
  def push(): Unit                      // Push stack frame
  def pop(): Unit                       // Pop and close stack frame
}

object Scope {
  def global: Scope  // Immortal root scope (closes on JVM shutdown)
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
  // Derive from concrete class Impl, providing as type T
  inline def from[Impl <: T, T]: Wireable[T]
}
```

### Macros

```scala
inline def shared[T]: Wire[???, T]  // Uses Wireable[T] if available, else constructor
inline def unique[T]: Wire[???, T]  // Uses Wireable[T] if available, else constructor
```

---

## Comparison with Alternatives

| Feature | Wire | ZIO ZLayer | Guice | MacWire |
|---------|------|------------|-------|---------|
| Compile-time verification | ✅ | ✅ | ❌ | ✅ |
| Lifecycle management | ✅ | ✅ | ✅ | ❌ |
| Effect system required | ❌ | ✅ (ZIO) | ❌ | ❌ |
| Async construction | ❌* | ✅ | ❌ | ❌ |
| Runtime flexibility | ❌ | ❌ | ✅ | ❌ |
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

Wire provides compile-time verified dependency injection with minimal ceremony:

- **`shared[T]`** — Macro-generated wire, memoized within Autowire call
- **`unique[T]`** — Macro-generated wire, fresh each time
- **`Autowire[T](wires...)`** — Builds the dependency graph, verifies at compile time
- **`Autowire.run[T](wires...) { t => ... }`** — Build, use, and cleanup in one step
- **`Scope`** — Structured lifecycle via `defer`, `child`, and `close`
- **`Scope.global`** — Immortal root scope for application-lifetime services
- **`Wire.Shared` / `Wire.Unique`** — Manual wire construction for complex cases

The result: type-safe DI that's easy to understand, easy to test, and has zero runtime magic.
