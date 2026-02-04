---
id: scope
title: "Scope"
---

# ZIO Blocks Scope

**Lightweight, compile-time verified dependency injection for Scala.**

## What Is Scope?

Scope is a minimalist dependency injection library that combines **service construction**, **lifecycle management**, and **type-safe dependency tracking** into a single abstraction. It leverages Scala's type system to track available services at compile time, ensuring your dependency graph is complete before your code runs.

Scope supports both **Scala 2.13** and **Scala 3.x**. The core functionality is identical across versions.

The core insight: a **scope** is both a lifecycle boundary AND a typed context of available services. By tracking the service stack at the type level, dependencies can be resolved implicitly from what's already in scope.

## Why Scope?

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

Scope gives you compile-time verification with fluent syntax:

```scala
injected[App].run { app => app.run() }
```

That's it. The macro inspects `App`'s constructor, discovers it needs `UserService`, which needs `Database` and `Cache`, which need `Config` — and wires everything up. If any dependency is missing, you get a compile error.

**What you get:**
- Compile-time errors if dependencies are missing
- Automatic lifecycle management (structured scoping)
- Implicit dependency resolution from available services
- No reflection, no runtime surprises
- Works with any effect system (or none)

## What Scope Is Not

Scope is deliberately **not**:

- **An effect system**: Scope doesn't manage async, concurrency, or errors. Use ZIO, Cats Effect, or plain Scala for that.
- **A configuration library**: Scope constructs objects; it doesn't read config files. Pair it with your preferred config library.
- **A replacement for ZLayer**: If you're fully invested in ZIO, ZLayer offers tighter integration. Scope is for when you want DI without the effect system dependency.
- **Runtime flexible**: The dependency graph is fixed at compile time. No runtime module swapping (use different builds for that).

---

## Quick Start

### Minimal Example

```scala
import zio.blocks.scope._

class Config(val dbUrl: String)
class Database(config: Config)
class App(db: Database) {
  def run(): Unit = println("Running")
}

@main def main(): Unit =
  injected[App].run { _.run() }
```

The `injected[App]` macro recursively discovers and wires all dependencies.

### With Lifecycle Management

```scala
import zio.blocks.scope._

class Config(val dbUrl: String, val cacheSize: Int)

// AutoCloseable — cleanup auto-registered
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def close(): Unit = pool.close()
}

// Manual cleanup via defer
class Cache(config: Config)(using Scope.Any) {
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
  injected[App].run { _.run() }
  // Cleanup runs automatically in reverse order:
  // Cache (evictor stopped), Database (pool closed)
```

---

## Core Concepts

### Scope

A `Scope[Stack]` manages resource lifecycle and tracks available services at the type level:

```scala
sealed trait Scope[Stack] {
  def get[T](using InStack[T, Stack], IsNominalType[T]): T
  def defer(finalizer: => Unit): Unit
  def child: Scope.Closeable[Stack]
  def injected[T](wires: Wire[?,?]*): Scope.Closeable[Context[T] :: Stack]
}

object Scope {
  val global: Scope[TNil]               // Root scope, closes on JVM shutdown
  type Any = Scope[?]                   // Use in constructors needing cleanup
}
```

The `InStack[T, Stack]` evidence is resolved at compile time, ensuring you can only `get` services that exist in the stack.

**Key semantics:**
- Finalizers run in **reverse order** of registration (LIFO)
- Parent close **closes children first**, then runs own finalizers
- `close()` is **idempotent** — safe to call multiple times

**The global scope:**

`Scope.global` never closes during normal execution — it finalizes via JVM shutdown hook. Use it for application-lifetime services:

```scala
injected[App](using Scope.global).run { _.run() }
```

For tests, create a child scope for deterministic cleanup:

```scala
val testScope = Scope.global.child
try { /* test */ } finally { testScope.close() }
```

### The Type-Level Stack

Each `injected[T]` call creates a child scope and pushes a layer onto the stack:

```scala
Scope.global                              // Scope[TNil]
  .injected[Config]                       // Scope[Context[Config] :: TNil]
  .injected[Database]                     // Scope[Context[Database] :: Context[Config] :: TNil]
```

Services can be retrieved from any layer in the stack. The stack type ensures at compile time that requested services exist.

### Scope.Closeable

`injected` returns a `Scope.Closeable` — a scope that can be explicitly closed:

```scala
trait Scope.Closeable[Stack] extends Scope[Stack] with AutoCloseable {
  def close(): Unit
  def run[B](f: Context[CurrentLayer] => B): B
}
```

The `run` method executes your code then closes the scope automatically.

---

## Cleanup Patterns

Scope handles cleanup automatically for `AutoCloseable` types, or you can manage it manually with `defer`.

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
class Cache(config: Config)(using Scope.Any) {
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
| Takes `Scope.Any` (implicit or explicit) | You handle cleanup manually |
| Both `AutoCloseable` AND takes `Scope` | You handle cleanup (Scope wins) |

---

## Request Scoping

For request-scoped services, use `injected` with the implicit parent scope:

```scala
class App(userService: UserService)(using Scope.Any) {
  def handleRequest(request: Request): Response =
    injected[RequestHandler](
      Wire.value(request)        // Inject the request value
    ).run { handler =>
      handler.handle()
    }
    // RequestHandler and dependencies cleaned up here
}
```

Each request gets its own child scope. The `UserService` from the parent scope is available; the `RequestHandler` is created fresh and cleaned up after.

**Public vs. private dependencies:**

```scala
// Private: Cache is a hidden dependency of RequestHandler
injected[RequestHandler](shared[Cache]).run { ... }

// Public: Cache is visible in the scope stack
injected[Cache].injected[RequestHandler].run { ... }
```

---

## Testing

Swap implementations by providing different wires:

```scala
// Production
injected[UserService].run { svc =>
  svc.getUser("123")
}

// Test with mocks
injected[UserService](
  Wire.value(new MockDatabase()),
  Wire.value(new MockCache())
).run { svc =>
  assert(svc.getUser("123") == expectedUser)
}
```

Or inject mock types directly:

```scala
// MockDatabase and MockCache extend the traits
injected[MockDatabase & MockCache]
  .injected[UserService]
  .run { ctx =>
    val svc = ctx.get[UserService]
    assert(svc.getUser("123") == expectedUser)
  }
```

---

## Wireable for Traits

Traits don't have constructors, so `injected[Database]` fails unless there's a `Wireable[Database]`:

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
injected[App].run { _.run() }  // Uses DatabaseImpl via Wireable
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

**Precedence:** Explicit wires in `injected` override `Wireable` in scope.

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
injected[App](configWire, dbWire).run { _.run() }
```

---

## Compile-Time Errors

Scope provides rich error messages with dependency graphs.

**Missing dependency:**

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Missing dependency: Cache

  Stack:
    → Database
    → (root)

  UserService requires:
    ✓ Database  — found in stack
    ✗ Cache     — missing

  Hint: Either:
    • .injected[Cache].injected[UserService]     — Cache visible in stack
    • .injected[UserService](shared[Cache])      — Cache as private dependency

────────────────────────────────────────────────────────────────────────────────
```

**Ambiguous provider:**

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Multiple providers for Config

  Conflicting wires:
    1. shared[Config] at MyApp.scala:15
    2. Wire.value(...) at MyApp.scala:16

  Hint: Remove duplicate wires or use distinct types

────────────────────────────────────────────────────────────────────────────────
```

**Dependency cycle:**

```
── Scope Error ─────────────────────────────────────────────────────────────────

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
sealed trait Scope[Stack] {
  def get[T](using InStack[T, Stack], IsNominalType[T]): T
  def defer(finalizer: => Unit): Unit
  def child: Scope.Closeable[Stack]
  def injected[T](wires: Wire[?,?]*): Scope.Closeable[Context[T] :: Stack]
}

object Scope {
  val global: Scope[TNil]
  
  type Any = Scope[?]                          // Scope-polymorphic
  type Required[T] = Scope[Context[T] :: ?]    // Requires T in stack
  
  trait Closeable[Stack] extends Scope[Stack] with AutoCloseable {
    def close(): Unit
    def run[B](f: Scope[Stack] ?=> Context[CurrentLayer] => B): B
  }
}

// Type-level evidence that T exists somewhere in the Stack
trait InStack[T, Stack]
```

### Wire

```scala
// Scala 3
sealed trait Wire[-In, +Out] {
  def construct: Scope[?] ?=> Context[In] => Context[Out]
}

// Scala 2
sealed trait Wire[-In, +Out] {
  def construct(implicit scope: Scope[?]): Context[In] => Context[Out]
}

object Wire {
  final case class Shared[-In, +Out](...) extends Wire[In, Out]
  final case class Unique[-In, +Out](...) extends Wire[In, Out]
  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire[Any, T]
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
inline def injected[T](wires: Wire[?,?]*)(using Scope.Any): Scope.Closeable[...]
inline def shared[T]: Wire[???, T]
inline def unique[T]: Wire[???, T]
def defer(finalizer: => Unit)(using Scope.Any): Unit
```

---

## Comparison with Alternatives

| Feature | Scope | ZIO ZLayer | Guice | MacWire |
|---------|-------|------------|-------|---------|
| Compile-time verification | ✅ | ✅ | ❌ | ✅ |
| Lifecycle management | ✅ | ✅ | ✅ | ❌ |
| Effect system required | ❌ | ✅ (ZIO) | ❌ | ❌ |
| Type-level stack tracking | ✅ | ❌ | ❌ | ❌ |
| Async construction | ❌* | ✅ | ❌ | ❌ |
| Scoped instances | ✅ | ✅ | ✅ | ❌ |

*Async construction can be added later if needed.

---

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

Scope depends on:
- `zio-blocks-context` (for `Context`)
- `zio-blocks-typeid` (for type identity)

---

## Summary

- **`injected[T]`** — Create child scope, wire dependencies from stack
- **`injected[T](wires...)`** — Wire with explicit/private dependencies
- **`.run { ctx => ... }`** — Execute with automatic cleanup
- **`Scope.Any`** — Use in constructors needing `defer`
- **`defer { ... }`** — Register cleanup on current scope
- **`Wire.value(x)`** — Inject a specific value
- **`Wireable[T]`** — Enable `injected[T]` for traits
