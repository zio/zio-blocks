---
id: scope
title: "Scope"
---

# ZIO Blocks Scope

**Compile-time verified dependency injection with lifecycle safety for Scala.**

## What Is Scope?

Scope is a minimalist dependency injection library that makes **lifecycle errors into compile-time errors**. Most DI libraries verify that your dependencies are wired correctly — Scope goes further by verifying that resources are used within their intended lifecycle.

The key insight: **resources should be part of the scope's type**. A `Scope.Has[Database & Cache]` is a different type from `Scope.Has[Database]`. This means:

- You can't access a resource that isn't in scope — compile error
- You can't pass the wrong scope to a function — compile error  
- You can't use a resource after its scope closes — compile error

Traditional DI prevents "app won't start" errors. Scope prevents "3am production incident" errors.

Scope supports both **Scala 2.13** and **Scala 3.x**. The core functionality is identical across versions.

## Why Scope?

### The Problem

Most DI solutions solve **construction correctness** — making sure dependencies exist:

```scala
// Missing dependency? Runtime error at startup
val injector = Guice.createInjector(new AppModule())
val app = injector.getInstance(classOf[App])
```

But the expensive bugs are **lifecycle errors** — using resources with the wrong scope:

```scala
def processRequest()(using appScope: Scope): Response = {
  val requestScope = appScope.child
  val tempFile = TempFile.create()
  
  // BUG: Registered on wrong scope! Temp files accumulate until app restart
  appScope.defer(tempFile.delete())  // Should be requestScope
  
  // BUG: Passed wrong scope! Function uses closed scope
  doWork(tempFile, requestScope)
  requestScope.close()
  finalize(requestScope)  // Runtime error or silent corruption
}
```

When scopes are untyped, all scopes look the same to the compiler. You rely on discipline and testing to catch these bugs.

### The Solution

Scope makes resources part of the scope's type, so lifecycle errors become type errors:

```scala
def processRequest()(using Scope.Any): Response = {
  injected[TempFile](Wire.value(TempFile.create())).run { _ =>
    // TempFile is in THIS scope's type — cleanup is automatic
    doWork()  // Has access to TempFile
  }
  // TempFile cleaned up here
  
  finalize()  // Can NOT access TempFile — it's not in the type
}

def doWork()(using Scope.Has[TempFile]): Unit = {
  $[TempFile].write(data)  // Compiler verified TempFile is available
}

def finalize()(using Scope.Has[TempFile]): Unit = { ... }
// ^ Would NOT compile when called after TempFile scope closes!
```

**What you get:**
- Compile-time errors if dependencies are missing (like other DI)
- **Compile-time errors if resources are used outside their lifecycle** (unique to Scope)
- Automatic cleanup in correct order
- Type signatures document what resources a function needs
- No reflection, no runtime surprises
- Works with any effect system (or none)

## What Scope Is Not

Scope is deliberately **not**:

- **An effect system**: Scope doesn't manage async, concurrency, or errors. Use ZIO, Cats Effect, or plain Scala for that.
- **A configuration library**: Scope constructs objects; it doesn't read config files. Pair it with your preferred config library.
- **Runtime flexible**: The dependency graph is fixed at compile time. No runtime module swapping (use different builds for that).

**Scope vs. ZIO ZLayer**: ZLayer is more powerful (async construction, error handling, resource finalization integrated with the effect system). Scope is lighter — it's the ZLayer capability-tracking pattern extracted into a standalone library without the effect system dependency. Use Scope when you want lifecycle safety without buying into a full effect system.

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

The `injected[App]` macro discovers that `App` needs `Database` needs `Config`, wires them up, runs your code, then cleans up in reverse order.

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

**Scala 2 equivalent:**

```scala
// Scala 2 — use implicit parameters instead of `using`
class Cache(config: Config)(implicit scope: Scope.Any) {
  private val evictor = startEvictorThread()
  defer {
    evictor.interrupt()
    evictor.join()
  }
}

def main(): Unit =
  injected[App].run { _.run() }(Scope.global)
```

---

## Core Concepts

### Scope

A `Scope[Stack]` manages resource lifecycle and tracks available services at the type level:

```scala
sealed trait Scope[+Stack] {
  def get[T](using InStack[T, Stack], IsNominalType[T]): T
  def defer(finalizer: => Unit): Unit
  inline def injected[T](wires: Wire[?,?]*): Scope.Closeable[T, ?]
}

object Scope {
  val global: Scope[TNil]                      // Root scope, closes on JVM shutdown
  type Any = Scope[?]                          // Use in constructors needing cleanup
  type Has[+T] = Scope[Context[T] :: scala.Any] // Scope that has T available
}
```

The `InStack[T, Stack]` evidence is resolved at compile time, ensuring you can only `get` services that exist in the stack. Variance allows a `Scope.Has[Dog]` to satisfy a `Scope.Has[Animal]` requirement.

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
trait Scope.Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with AutoCloseable {
  def close(): Unit
  def run[B](f: Scope.Has[Head] ?=> B): B  // Scala 3: scope available via context function
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

### Type Safety with Multiple Lifecycles

When multiple resources have different lifecycles, the type system ensures you can't mix them up:

```scala
def processUpload(file: UploadedFile)(using Scope.Any): Result = {
  // Request scope — socket lives for this request
  injected[Socket](Wire.value(Socket.connect(validationServiceUrl))).run { _ =>
    // Socket is now in scope, cleanup automatic
    
    // Operation scope — temp file lives only for validation
    injected[TempFile](Wire.value(TempFile.create())).run { _ =>
      // TempFile is now in scope, cleanup automatic
      
      get[TempFile].write(file.bytes)
      validate()  // Can access both Socket and TempFile
    }
    // TempFile cleaned up here
    
    if (validationPassed) {
      finalize()  // Can access Socket but NOT TempFile — it's out of scope
    }
  }
  // Socket cleaned up here
}

def validate()(using Scope.Has[Socket & TempFile]): ValidationResult = {
  // Type guarantees both resources are available
  get[Socket].send(get[TempFile].readAll())
}

def finalize()(using Scope.Has[Socket]): Unit = {
  // Type guarantees Socket is available
  // Can't accidentally require TempFile here — it would fail to compile
  // at the call site where TempFile is out of scope
  get[Socket].sendComplete()
}

// This would NOT compile:
def badFinalize()(using Scope.Has[Socket & TempFile]): Unit = { ... }
// Called from the outer scope where TempFile is gone:
// badFinalize()  // ✗ Compile error: TempFile not in Stack
```

**Scala 2 equivalent:**

```scala
def validate()(implicit scope: Scope.Has[Socket with TempFile]): ValidationResult = {
  get[Socket].send(get[TempFile].readAll())
}

def finalize()(implicit scope: Scope.Has[Socket]): Unit = {
  get[Socket].sendComplete()
}
```

**The advantage**: Each scope has a distinct type based on its contents. The compiler prevents:
- Accessing resources that aren't in scope (compile error on `get[TempFile]` after its scope closed)
- Passing a scope that doesn't have required resources (compile error on call site)
- Registering cleanup on the wrong scope (cleanup is always on the current scope via `defer`)

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

For complex construction logic, create wires directly instead of relying on macro derivation.

### Wire.Shared vs Wire.Unique

| Type | Behavior | Use Case |
|------|----------|----------|
| `Wire.Shared[In, Out]` | One instance per `injected` call | Database pools, caches, configs |
| `Wire.Unique[In, Out]` | Fresh instance each time needed | Transactions, request handlers |

### Creating Manual Wires

```scala
// Scala 3 — scope available via context function
val configWire: Wire.Shared[Any, Config] = Wire.Shared { ctx =>
  Context(Config.load(sys.env("CONFIG_PATH")))
}

val dbWire: Wire.Shared[Config, Database] = Wire.Shared { ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  defer(db.close())  // Register cleanup
  Context(db)
}

// Wire that produces multiple services
val infraWire: Wire.Shared[Config, Database & Cache] = Wire.Shared { ctx =>
  val config = ctx.get[Config]
  val db = Database.connect(config.dbUrl)
  val cache = Cache.create(config.cacheSize)
  defer(db.close())
  defer(cache.close())
  Context(db, cache)
}
```

```scala
// Scala 2 — scope is an explicit implicit parameter
val dbWire: Wire.Shared[Config, Database] = Wire.Shared { implicit scope => ctx =>
  val db = Database.connect(ctx.get[Config].dbUrl)
  defer(db.close())
  Context(db)
}
```

### Using Manual Wires

Pass manual wires to `injected` alongside or instead of macro-generated ones:

```scala
// Mix manual and derived wires
injected[App](configWire, dbWire).run { _.run() }

// Override a derived wire with a manual one
injected[App](
  Wire.Shared[Any, Config] { _ => Context(Config(debug = true)) }  // Custom config
).run { _.run() }
```

### Wire.value for Injecting Existing Values

Use `Wire.value` to inject a pre-existing value:

```scala
def handleRequest(request: Request)(using Scope.Any): Response =
  injected[RequestHandler](
    Wire.value(request),           // Inject the request
    Wire.value(Transaction.begin()) // Inject a fresh transaction
  ).run { handler =>
    handler.handle()
  }
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
sealed trait Scope[+Stack] {
  def get[T](using InStack[T, Stack], IsNominalType[T]): T
  def defer(finalizer: => Unit): Unit
  inline def injected[T](wires: Wire[?,?]*): Scope.Closeable[T, ?]
}

object Scope {
  val global: Scope[TNil]
  
  type Any = Scope[?]                           // Scope-polymorphic  
  type Has[+T] = Scope[Context[T] :: scala.Any] // Scope that has T available
  
  trait Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with AutoCloseable {
    def close(): Unit
    def run[B](f: Scope.Has[Head] ?=> B): B  // Scala 3
    def run[B](f: Scope.Has[Head] => B): B   // Scala 2
  }
}

// Type-level evidence that T exists somewhere in the Stack
trait InStack[-T, +Stack]
```

### Wire

```scala
sealed trait Wire[-In, +Out] {
  def construct(using Scope.Has[In]): Context[Out]  // Scala 3
  def construct(implicit scope: Scope.Has[In]): Context[Out]  // Scala 2

  def isShared: Boolean
  def isUnique: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
}

object Wire {
  final class Shared[-In, +Out](constructFn: Scope.Has[In] => Context[Out]) extends Wire[In, Out]
  final class Unique[-In, +Out](constructFn: Scope.Has[In] => Context[Out]) extends Wire[In, Out]
  
  object Shared {
    def apply[In, Out](f: Scope.Has[In] ?=> Context[Out]): Wire.Shared[In, Out]  // Scala 3
    def apply[In, Out](f: Scope.Has[In] => Context[Out]): Wire.Shared[In, Out]   // Scala 2
  }
  
  def value[T](t: T)(implicit ev: IsNominalType[T]): Wire.Shared[Any, T]
}
```

### Wireable

```scala
trait Wireable[+Out] {
  type In
  def wire: Wire[In, Out]
}

object Wireable {
  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }
  
  transparent inline def from[T]: Wireable[T]  // Scala 3
  def from[T]: Wireable[T] = macro ...         // Scala 2
}
```

### Top-Level Functions

```scala
// Scala 3
transparent inline def shared[T]: Wire.Shared[?, T]
transparent inline def unique[T]: Wire.Unique[?, T]
inline def injected[T](wires: Wire[?,?]*)(using Scope.Any): Scope.Closeable[T, ?]
def $[T](using Scope.Has[T], IsNominalType[T]): T
def defer(finalizer: => Unit)(using Scope.Any): Unit

// Scala 2
def shared[T]: Wire.Shared[_, T] = macro ...
def unique[T]: Wire.Unique[_, T] = macro ...
def injected[T](wires: Wire[_,_]*)(implicit scope: Scope.Any): Scope.Closeable[T, _] = macro ...
def $[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T
def defer(finalizer: => Unit)(implicit scope: Scope.Any): Unit
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
