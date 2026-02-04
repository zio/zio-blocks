---
id: scope
title: "Scope"
---

# ZIO Blocks Scope

**Lightweight, compile-time verified dependency injection for Scala.**

## What Is Scope?

Scope is a minimalist dependency injection library that combines **service construction**, **lifecycle management**, and **type-safe dependency tracking** into a single abstraction. It leverages Scala's type system to track available services at compile time, ensuring your dependency graph is complete before your code runs.

Scope supports both **Scala 2.13** and **Scala 3.x**. Scala 3 offers a nicer experience with match types for ergonomic syntax, but the core functionality is identical across versions.

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

Scope gives you compile-time verification with natural, fluent syntax:

```scala
Scope.global
  .injected[Config]
  .injected[Database & Cache]       // Batch inject independent services
  .injected[UserService & App]
  .run { ctx =>
    ctx.get[App].run()
  }
// ^ All services cleaned up in reverse order
```

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

## Quick Example

Here's a complete example showing Scope's features:

```scala
import zio.blocks.scope._
import zio.blocks.context._

// Simple service — no cleanup needed
class Config(val dbUrl: String, val cacheSize: Int)

// AutoCloseable — cleanup auto-registered
class Database(config: Config) extends AutoCloseable {
  val pool = ConnectionPool.create(config.dbUrl)
  def query(sql: String): ResultSet = pool.query(sql)
  def close(): Unit = pool.close()
}

// Manual cleanup via defer (top-level function)
class Cache(config: Config)(using Scope[?]) {
  private val store = new LRUCache(config.cacheSize)
  private val evictor = new Thread(() => evictionLoop())
  evictor.start()
  defer {
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

class App(userService: UserService) {
  def run(): Unit = {
    val user = userService.getUser("123")
    println(s"Hello, ${user.name}")
  }
}

// Wire it up — batch injection for independent services
@main def main(): Unit = {
  Scope.global
    .injected[Config]
    .injected[Database & Cache]     // Both depend only on Config, inject together
    .injected[UserService & App]    // Both can be injected in same layer
    .run { ctx =>
      ctx.get[App].run()
    }
  // Cleanup runs automatically:
  // 1. App (no cleanup)
  // 2. UserService (no cleanup)
  // 3. Cache (evictor stopped, store cleared)
  // 4. Database (pool closed)
  // 5. Config (no cleanup)
}
```

### What's Happening

1. **`Scope.global`** — The immortal root scope with no services
2. **`.injected[Config]`** — Creates a child scope containing `Config`, returns `Scope.Closeable[Context[Config] :: TNil]`
3. **`.injected[Database]`** — Creates another child scope; `Database` needs `Config`, which is found in the parent layer
4. **`.run { ctx => ... }`** — Executes the block, then closes the scope (running all finalizers in reverse order)

---

## Core Concepts

### The Scope Type

A `Scope[Stack]` tracks available services at the type level:

```scala
// Type-level stack of context layers
type MyStack = Context[Request] :: Context[Database & Config] :: TNil

val scope: Scope[MyStack]

scope.get[Database]  // ✓ Found in parent layer
scope.get[Request]   // ✓ Found in current layer
scope.get[Missing]   // ✗ Compile error: not in stack
```

The stack is a type-level list where each element is a `Context` layer. Services can be retrieved from any layer in the stack.

### Scope API

```scala
// Scala 3
trait Scope[Stack] {
  type CurrentLayer    // The top layer's contents (e.g., Request)
  type FullStack = Stack
  type TailStack       // Everything below current layer
  
  def get[X](implicit ev: InStack[X, FullStack], nominal: IsNominalType[X]): X
  def getAll: Context[CurrentLayer]
  def defer(finalizer: => Unit): Unit
  
  def injected[T](wires: Wire[?, ?]*): Scope.Closeable[Contextualize[T] :: Stack]
}

// Scala 2 (equivalent)
trait Scope[Stack] {
  def get[X](implicit ev: InStack[X, Stack], nominal: IsNominalType[X]): X
  def getAll: Context[CurrentLayer]
  def defer(finalizer: => Unit)(implicit scope: Scope[?]): Unit
  
  def injected[T](wires: Wire[?, ?]*): Scope.Closeable[Context[T] :: Stack]
}
```

### Scope.Closeable

When you call `injected`, you get back a `Scope.Closeable` — a scope that can be explicitly closed:

```scala
trait Scope.Closeable[Stack] extends Scope[Stack] with AutoCloseable {
  def close(): Unit
  
  def run[B](f: Scope[FullStack] ?=> Context[CurrentLayer] => B): B = {
    try f(using this)(getAll)
    finally close()
  }
}
```

`Scope.global` is the root scope and is **not** closeable — it only finalizes via JVM shutdown hook.

### Structured Scoping

Scope uses **structured scoping** semantics (like structured concurrency):

- **Parent-close closes children**: When a scope closes, it first closes all its children (in reverse order of creation), then runs its own finalizers.
- **Child-close does not close parent**: Closing a child scope only affects that child and its descendants.
- **Finalizers run LIFO**: Within a scope, finalizers run in reverse order of registration.
- **`close()` is idempotent**: Calling close multiple times is safe.

```scala
val s1 = Scope.global.injected[Database]
val s2 = s1.injected[Request]
val s3 = s1.injected[Cache]  // Another child of s1

s1.close()
// 1. Closes s3 (created after s2, so closed first)
// 2. Closes s2
// 3. Runs s1's finalizers
```

### Thread Safety

All `Scope` methods (`defer`, `get`, `injected`, `close`) are thread-safe. Multiple threads can safely:
- Read services from the same scope
- Register finalizers
- Create child scopes

---

## Dependency Injection

### Implicit Resolution from Stack

The key feature: when you call `injected[T]`, Scope looks for `T`'s dependencies in the existing stack:

```scala
class Handler(db: Database, req: Request)

Scope.global
  .injected[Database]     // Database has no deps (or simple ones)
  .injected[Request]      // Request has no deps
  .injected[Handler]      // Handler needs Database & Request — found in stack!
```

If dependencies are missing, you get a compile-time error with a helpful message.

### Explicit Wires

For complex cases or when you need to override the default construction, provide explicit wires:

```scala
Scope.global
  .injected[Database](
    Wire.Shared { ctx =>
      val db = Database.connect(sys.env("DB_URL"))
      defer(db.close())  // Top-level defer function
      Context(db)
    }
  )
  .injected[App]
  .run { ctx => ctx.get[App].run() }
```

### Batch Injection

Inject multiple services into the same layer using intersection types. This is the preferred style when services at the same "level" of the dependency graph can be injected together:

```scala
// Inject all infrastructure at once
Scope.global
  .injected[Config]
  .injected[Database & Cache & MessageQueue]  // All depend on Config
  .injected[UserService & OrderService]        // All depend on DB/Cache
  .injected[App]
```

```scala
// Scala 3 (match types auto-expand)
Scope.global.injected[Config & Database & Cache]
// = Scope[Context[Config] & Context[Database] & Context[Cache] :: TNil]

// Scala 2 (explicit Context wrapping)
Scope.global.injected[Context[Config] & Context[Database] & Context[Cache]]
```

All services in a batch share the same layer — their finalizers run together when that layer closes.

### Wireable for Interfaces

Traits can specify their default implementation via `Wireable`:

```scala
trait Database {
  def query(sql: String): ResultSet
}

object Database {
  given Wireable[Database] = Wireable.from[DatabaseImpl, Database]
}

class DatabaseImpl(config: Config) extends Database with AutoCloseable {
  def query(sql: String): ResultSet = ???
  def close(): Unit = ???
}

// Now this works:
Scope.global
  .injected[Config]
  .injected[Database]  // Uses DatabaseImpl via Wireable
  .run { ctx => 
    val db: Database = ctx.get[Database]  // Returns as trait type
  }
```

The `Wireable[Database]` produces a wire with output type `Database` (the trait), not `DatabaseImpl`.

---

## Lifecycle Management

### AutoCloseable

If a service implements `AutoCloseable` and doesn't take an implicit `Scope`, the macro automatically registers cleanup:

```scala
class Database(config: Config) extends AutoCloseable {
  def close(): Unit = pool.close()
}

// The injected macro generates:
// scope.defer(instance.close())
```

### Manual Cleanup

For complex cleanup, take an implicit `Scope` and use the top-level `defer` function:

```scala
class Cache(config: Config)(using Scope[?]) {
  private val evictor = startEvictor()
  defer {
    evictor.stop()
    evictor.awaitTermination()
  }
}
```

**Rule**: If your constructor takes `Scope`, you handle cleanup. The macro won't auto-register `close()` even if you're `AutoCloseable`.

| Constructor | Cleanup Strategy |
|-------------|------------------|
| `AutoCloseable` (no `Scope` param) | Macro generates `scope.defer(_.close())` |
| Takes implicit `Scope` | You handle cleanup manually |
| `AutoCloseable` AND takes `Scope` | You handle cleanup manually |

### The `run` Pattern

The `run` method provides scoped execution with automatic cleanup:

```scala
Scope.global
  .injected[Config]
  .injected[Database]
  .injected[App]
  .run { ctx =>
    // ctx: Context[App]
    // Scope available implicitly for defer, nested injected, etc.
    ctx.get[App].start()
    ctx.get[App].awaitTermination()
  }
// ^ Scope closed here, all finalizers run
```

This is equivalent to:
```scala
val scope = Scope.global.injected[Config].injected[Database].injected[App]
try {
  val ctx = scope.getAll
  ctx.get[App].start()
  ctx.get[App].awaitTermination()
} finally {
  scope.close()
}
```

---

## Type-Level Stack

### Required Dependencies

User code should be polymorphic over the tail of the stack. Use `Scope.Required`:

```scala
object Scope {
  type Required[Layer] = Scope[Layer :: ?]
}

// User code only declares what it needs:
def handleRequest(using scope: Scope.Required[Context[Database & Config]]): Unit = {
  val db = scope.get[Database]
  val config = scope.get[Config]
  // Don't care what else is in the stack
}
```

### Match Types (Scala 3)

Scala 3 uses match types to simplify syntax:

```scala
// You write:
scope.injected[Database & Config]

// Match type expands to:
Scope[Context[Database] & Context[Config] :: Stack]

// The match type definition:
type Contextualize[T] = T match {
  case a & b => Contextualize[a] & Contextualize[b]
  case t     => Context[t]
}
```

In Scala 2, you write the expanded form directly.

### Type Members

Each `Scope[Stack]` has type members for convenience:

```scala
trait Scope[Stack] {
  type CurrentLayer    // Contents of top layer
  type FullStack = Stack
  type TailStack       // Everything below current layer
}

// Example:
val scope: Scope[Context[Request] :: Context[Database] :: TNil]
// scope.CurrentLayer = Request
// scope.FullStack = Context[Request] :: Context[Database] :: TNil
// scope.TailStack = Context[Database] :: TNil
```

---

## Compile-Time Errors

Scope provides rich compile-time error messages with ASCII dependency graphs:

**Missing dependency:**

```scala
Scope.global.injected[Database].injected[UserService]
// UserService needs Cache, which isn't in the stack
```

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Missing dependency for UserService

  Stack:
    → Database
    → (root)

  UserService requires:
    ✓ Database  — found in stack
    ✗ Cache     — missing

  Hint: Add .injected[Cache] before .injected[UserService]

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

**Color support:**

Scope detects whether it's running in an interactive terminal and uses ANSI colors automatically. Colors are disabled in non-interactive environments (CI, build logs, IDE error panels).

---

## Request Scoping

For request-scoped services, create nested scopes:

```scala
class Server(using appScope: Scope.Required[Context[Database & Config]]) {
  def handleRequest(request: Request): Response = {
    appScope
      .injected[Request](Wire.value(request))  // Inject the actual request
      .injected[Transaction]                    // Transaction per request
      .injected[RequestHandler]
      .run { ctx =>
        ctx.get[RequestHandler].handle()
      }
    // Transaction and Request cleaned up here
  }
}

// Application startup
Scope.global
  .injected[Config]
  .injected[Database]
  .injected[Server]
  .run { ctx =>
    ctx.get[Server].start()
  }
```

Each request gets its own scope with its own `Transaction`. The `Database` is shared from the parent scope.

---

## Testing

Testing is straightforward — just inject test implementations:

```scala
// Production
Scope.global
  .injected[RealDatabase & RealCache]
  .injected[UserService]
  .run { ctx => runApp(ctx.get[UserService]) }

// Test — swap implementations
Scope.global
  .injected[MockDatabase & MockCache]
  .injected[UserService]  // Same UserService, uses mocks
  .run { ctx => 
    val service = ctx.get[UserService]
    assert(service.getUser("123") == expectedUser)
  }
```

Or override specific services with explicit wires:

```scala
Scope.global
  .injected[Database & Cache](
    Wire.value(new MockDatabase()),
    Wire.value(new MockCache())
  )
  .injected[UserService]
  .run { ctx => /* test */ }
```

---

## API Reference

### Scope

```scala
// Scala 3
trait Scope[Stack] {
  type CurrentLayer
  type FullStack = Stack
  type TailStack
  
  def get[X](implicit ev: InStack[X, FullStack], nominal: IsNominalType[X]): X
  def getAll: Context[CurrentLayer]
  def defer(finalizer: => Unit): Unit
  
  def injected[T](wires: Wire[?, ?]*): Scope.Closeable[Contextualize[T] :: Stack]
}

// Scala 2
trait Scope[Stack] {
  def get[X](implicit ev: InStack[X, Stack], nominal: IsNominalType[X]): X
  def getAll: Context[CurrentLayer]
  def defer(finalizer: => Unit): Unit
  
  def injected[T](wires: Wire[?, ?]*): Scope.Closeable[Context[T] :: Stack]
}

object Scope {
  val global: Scope[TNil]
  
  type Required[Layer] = Scope[Layer :: ?]
  
  trait Closeable[Stack] extends Scope[Stack] with AutoCloseable {
    def close(): Unit
    def run[B](f: Scope[FullStack] ?=> Context[CurrentLayer] => B): B
  }
  
  // Scala 3 only
  type Contextualize[T] = T match {
    case a & b => Contextualize[a] & Contextualize[b]
    case t     => Context[t]
  }
}
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
  // Derive from concrete class Impl, providing as type T
  inline def from[Impl <: T, T]: Wireable[T]
}
```

### Macros

```scala
inline def shared[T]: Wire[???, T]  // Uses Wireable[T] if available, else constructor
inline def unique[T]: Wire[???, T]  // Uses Wireable[T] if available, else constructor
```

### Top-Level Functions

```scala
def defer(finalizer: => Unit)(using Scope[?]): Unit  // Register cleanup on current scope
def get[T](using Scope[?], InStack[T, ?], IsNominalType[T]): T  // Get from stack
```

These are convenience functions that delegate to the implicit `Scope`, avoiding verbose `summon[Scope[?]].defer(...)` calls.

---

## Comparison with Alternatives

| Feature | Scope | ZIO ZLayer | Guice | MacWire |
|---------|-------|------------|-------|---------|
| Compile-time verification | ✅ | ✅ | ❌ | ✅ |
| Lifecycle management | ✅ | ✅ | ✅ | ❌ |
| Effect system required | ❌ | ✅ (ZIO) | ❌ | ❌ |
| Type-level stack tracking | ✅ | ❌ | ❌ | ❌ |
| Implicit dep resolution | ✅ | ❌ | ✅ | ❌ |
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

Scope provides compile-time verified dependency injection with a type-tracked service stack:

- **`Scope[Stack]`** — Tracks available services at the type level
- **`.injected[T]`** — Add a service, auto-wiring from the stack
- **`.run { ctx => ... }`** — Execute with automatic cleanup
- **`.get[T]`** — Retrieve any service from the stack
- **`Scope.global`** — Immortal root scope
- **`Scope.Required[Layer]`** — For polymorphic user code
- **Structured scoping** — Parent-close closes children, LIFO finalizers

The result: type-safe DI where dependencies are resolved from what's already in scope, with compile-time verification and automatic lifecycle management.
