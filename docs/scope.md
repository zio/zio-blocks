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
- **You can't accidentally leak a resource** — it's "locked" to its scope (Scala 3)

Traditional DI prevents "app won't start" errors. Scope prevents "3am production incident" errors.

> **Unique feature**: Scope is the only DI library that prevents resource escape at compile time. Resources tagged with `@@` cannot have their methods called outside their scope — trying to leak them is a compile error, not a runtime bug.

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
  injectedValue(TempFile.create()).run {
    // TempFile is in THIS scope's type — cleanup is automatic
    doWork()  // Has access to TempFile
  }
  // TempFile cleaned up here
  
  finalize()  // Can NOT access TempFile — it's not in the type
}

def doWork()(using Scope.Has[TempFile]): Unit = {
  $[TempFile].write("example data")  // Compiler verified TempFile is available
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

// Classes with no-arg constructors are auto-wired
class Config {
  val dbUrl: String = "jdbc://localhost/mydb"
}
class Database(config: Config)
class App(db: Database) {
  def run(): Unit = println("Running")
}

@main def main(): Unit =
  Scope.global.injected[App].run {
    $[App].run()
  }
```

The `injected[App]` macro discovers that `App` needs `Database` needs `Config`, wires them up, runs your code, then cleans up in reverse order. Note that `injected[T]` can be called without parentheses when no explicit wires are needed.

For classes that require runtime configuration, use `Wire(...)` to inject specific values:

```scala
case class DbUrl(value: String)
class Config(dbUrl: DbUrl) {
  val url: String = dbUrl.value
}

@main def main(): Unit = {
  val dbUrl = DbUrl(sys.env.getOrElse("DB_URL", "jdbc://localhost/mydb"))
  Scope.global.injected[App](Wire(dbUrl)).run {
    $[App].run()
  }
}
```

### With Lifecycle Management

```scala
import zio.blocks.scope._

class Config {
  val dbUrl: String = "jdbc://localhost/mydb"
  val cacheSize: Int = 1000
}

// AutoCloseable — cleanup auto-registered
class Database(config: Config) extends AutoCloseable {
  private var open = true
  def query(sql: String): String = s"Result from ${config.dbUrl}"
  def close(): Unit = { open = false }
}

// Manual cleanup via defer
class Cache(config: Config)(using Scope.Any) {
  private var running = true
  defer { running = false }  // Cleanup registered on scope
}

class UserService(db: Database, cache: Cache) {
  def getUser(id: String): String = db.query(s"SELECT * FROM users WHERE id = $id")
}

class App(userService: UserService) {
  def run(): Unit = println(s"Hello, ${userService.getUser("123")}")
}

@main def main(): Unit =
  Scope.global.injected[App].run {
    $[App].run()
  }
  // Cleanup runs automatically in reverse order:
  // Cache (defer runs), Database (close() called)
```

**Scala 2 equivalent:**

```scala
// Scala 2 — use implicit parameters instead of `using`
class Cache(config: Config)(implicit scope: Scope.Any) {
  private var running = true
  defer { running = false }
}

def main(): Unit =
  Scope.global.injected[App].run { scope =>
    scope.get[App].run()
  }
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
  val global: Scope[TNil]                        // Root scope, closes on JVM shutdown
  type Any = Scope[scala.Any]                    // Use in constructors needing cleanup
  type Has[+T] = Scope[Context[T] :: scala.Any]  // Scope that has T available
}
```

The `InStack[T, Stack]` evidence is resolved at compile time, ensuring you can only `get` services that exist in the stack. Variance allows a `Scope.Has[Dog]` to satisfy a `Scope.Has[Animal]` requirement.

**Key semantics:**
- Finalizers run in **reverse order** of registration (LIFO)
- Parent close **closes children first**, then runs own finalizers
- `close()` is **idempotent** — safe to call multiple times
- `close()` returns `Chunk[Throwable]` containing any errors (all finalizers run even if some fail)
- `run` can only be called **once** — subsequent calls throw `IllegalStateException`

**The global scope:**

`Scope.global` never closes during normal execution — it finalizes via JVM shutdown hook. Use it for application-lifetime services:

```scala
Scope.global.injected[App].run {
  $[App].run()
}
```

For tests, create a child scope for deterministic cleanup:

```scala
val testScope = Scope.global.injected[TestFixture]
try { testScope.run { /* test */ } } finally { testScope.close() }
```

### The Type-Level Stack

Each `injected[T]` call creates a child scope and pushes a layer onto the stack:

```scala
Scope.global                              // Scope[TNil]
  .injected[Config]                       // Scope[Context[Config] :: TNil]
  .injected[Database]                     // Scope[Context[Database] :: Context[Config] :: TNil]
```

Services can be retrieved from any layer in the stack. The stack type ensures at compile time that requested services exist.

### Resource Escape Prevention (Scala 3)

**The Problem**: Even with type-level stack tracking, resources can escape their scope:

```scala
var leaked: InputStream = null

Scope.global.injected[Request].run {
  leaked = $[Request].body  // Captured reference survives scope!
}

leaked.read()  // Use-after-close bug — compiles but fails at runtime
```

**The Solution**: Scope provides compile-time escape prevention via opaque tagging:

```scala
import zio.blocks.scope.@@

val closeable = Scope.global.injectedValue(new Request(new InputStream))

// Get tagged value — methods are hidden by opaque type
val request: Request @@ closeable.Tag = closeable.value

// request.body  ← Compile error! 'body' is not a member of Request @@ Tag

// Must use $ operator with scope in context:
val body: InputStream @@ closeable.Tag = request.$(_.body)(using closeable)(using summon)

// Primitives and Unscoped types escape freely:
val n: Int = request.$(_.body.read())(using closeable)(using summon)  // Int is Unscoped

closeable.closeOrThrow()
```

**How It Works**:

1. **Opaque tagging**: `A @@ S` is an opaque type alias — the underlying `A` methods are hidden
2. **Scope capability**: The `$` operator requires `Scope[?] { type Tag >: S }` — you can only access the value when the matching scope is in context
3. **Conditional untagging**: The `Untag` typeclass controls what escapes:
   - `Unscoped` types (primitives, String, collections) return raw values
   - Resource types return re-tagged values `B @@ S`

**For-comprehensions** work naturally with tag accumulation:

```scala
val stream1: InputStream @@ scope1.Tag = ...
val stream2: InputStream @@ scope2.Tag = ...

// Union tag requires both scopes to be in context
val combined: (InputStream, InputStream) @@ (scope1.Tag | scope2.Tag) = for {
  s1 <- stream1
  s2 <- stream2
} yield (s1, s2)
```

**Child scopes can use parent-scoped values** because child tags are supertypes of parent tags:

```scala
val parent = Scope.global.injectedValue(new Resource)
val parentValue: Resource @@ parent.Tag = parent.value

val child = parent.injectedValue(new OtherResource)
// child.Tag >: parent.Tag, so we can use parentValue:
val n: Int = parentValue.$(_.getData)(using child)(using summon)
```

**Zero overhead**: The tagging is purely compile-time:
- `@@` is an opaque type alias — erased at runtime
- `@@.tag`, `map`, `flatMap` are `inline` — no method calls
- `$` operator incurs minimal overhead (one virtual call for `Untag.apply`)

**Unscoped types** include all primitives, String, collections of Unscoped elements, and tuples of Unscoped elements. Resource types (streams, connections, file handles) are NOT Unscoped and stay tagged.

> **Note**: Resource escape prevention is Scala 3 only (requires opaque types). Scala 2 builds work but don't have this feature.

### Scope.Closeable

`injected` returns a `Scope.Closeable` — a scope that can be explicitly closed:

```scala
trait Scope.Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with AutoCloseable {
  def close(): Chunk[Throwable]             // Close and return any errors
  def closeOrThrow(): Unit                  // Close; throw first error if any
  def run[B](f: Scope.Has[Head] ?=> B): B   // Execute then close automatically
  def runWithErrors[B](f: ...): (B, Chunk[Throwable])  // Run and capture errors
}
```

The `run` method executes your code then closes the scope automatically. If finalizers throw, errors are logged to stderr. Use `runWithErrors` to capture finalizer errors explicitly. Note that `run` can only be called once — this prevents accidental double-cleanup bugs.

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

## Error Handling

### Finalizer Errors

When `close()` or `run` executes finalizers, errors don't stop remaining finalizers — all registered cleanups run regardless:

```scala
val scope = Scope.global.injected[App]
scope.defer(throw new RuntimeException("error 1"))
scope.defer(throw new RuntimeException("error 2"))
scope.defer(println("this still runs"))

val errors: Chunk[Throwable] = scope.close()
// "this still runs" prints first (LIFO order)
// errors contains both RuntimeExceptions
```

Use `closeOrThrow()` when you want the first error thrown:

```scala
scope.closeOrThrow()  // Throws "error 2" (last registered, runs first)
```

### Handling Errors from run

The `run` method logs finalizer errors to stderr but does not throw them. Use `runWithErrors` if you need to handle errors programmatically:

```scala
val (result, errors) = scope.runWithErrors {
  // your code
  "done"
}
if (errors.nonEmpty) {
  // handle cleanup failures
}
```

---

## Thread Safety

Scope's finalizer implementation uses **lock-free atomic operations** for thread safety. Multiple threads can safely:
- Call `defer` concurrently
- Call `close()` concurrently (only the first call runs finalizers)
- Add finalizers while another thread is closing (they are silently ignored)

This makes Scope safe for multi-threaded applications without explicit synchronization.

---

## Advanced Parameter Patterns

The macro derivation (`shared[T]`, `unique[T]`, `Wireable.from[T]`) analyzes constructor parameters to determine dependencies and how to pass them.

### Regular Parameters

Regular constructor parameters become dependencies. Their types are combined into the `In` type of the resulting wire:

```scala
class UserService(db: Database, cache: Cache)

val wire = shared[UserService]
// wire has type Wire.Shared[Database & Cache, UserService]
```

### Scope.Any Parameters

An implicit/using `Scope.Any` parameter receives the current scope but doesn't add a dependency:

```scala
class Cache(config: Config)(using Scope.Any) {
  defer { /* cleanup */ }
}

val wire = shared[Cache]
// wire has type Wire.Shared[Config, Cache]
// (Scope.Any is NOT part of the In type)
```

### Scope.Has[Y] Parameters

Parameters of type `Scope.Has[Y]` extract `Y` as a dependency and receive a narrowed scope:

```scala
class MergeSort(
  input: Scope.Has[InputStream],
  output: Scope.Has[OutputStream],
  config: MergeConfig
) {
  val in = input.get[InputStream]
  val out = output.get[OutputStream]
}

val wire = shared[MergeSort]
// wire has type Wire.Shared[InputStream & OutputStream & MergeConfig, MergeSort]
```

This is useful when you need the scope for more than just `defer` — for example, to pass it to helper functions or store it for later use.

### Multiple Parameter Lists

Macro derivation preserves parameter list structure:

```scala
class Service(db: Database)(cache: Cache)(using Scope.Any) {
  defer { /* cleanup */ }
}

// Generates: new Service(scope.get[Database])(scope.get[Cache])(scope)
```

### Parameter Handling Summary

| Parameter Type | Added to `In`? | Passed as |
|---------------|----------------|-----------|
| Regular `T` | Yes, `T` | `scope.get[T]` |
| `Scope.Any` | No | `scope` |
| `Scope.Has[Y]` | Yes, `Y` | `scope` (narrowed) |

---

## Request Scoping

For request-scoped services, use `injected` with the implicit parent scope:

```scala
class App(userService: UserService)(using Scope.Any) {
  def handleRequest(request: Request): Response =
    injected[RequestHandler](
      Wire.value(request)        // Inject the request value
    ).run {
      $[RequestHandler].handle()
    }
    // RequestHandler and dependencies cleaned up here
}
```

Each request gets its own child scope. The `UserService` from the parent scope is available; the `RequestHandler` is created fresh and cleaned up after.

**Public vs. private dependencies:**

```scala
// Private: Cache is a hidden dependency of RequestHandler
injected[RequestHandler](shared[Cache]).run { /* ... */ }

// Public: Cache is visible in the scope stack
Scope.global.injected[Cache].injected[RequestHandler].run { /* ... */ }
```

### Type Safety with Multiple Lifecycles

When multiple resources have different lifecycles, the type system ensures you can't mix them up:

```scala
def processUpload(fileBytes: Array[Byte])(using Scope.Any): Result = {
  // Request scope — socket lives for this request
  injectedValue(Socket.connect("validation.example.com")).run {
    // Socket is now in scope, cleanup automatic
    
    // Operation scope — temp file lives only for validation
    injectedValue(TempFile.create()).run {
      // TempFile is now in scope, cleanup automatic
      
      $[TempFile].write(fileBytes)
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
  $[Socket].send($[TempFile].readAll())
}

def finalize()(using Scope.Has[Socket]): Unit = {
  // Type guarantees Socket is available
  // Can't accidentally require TempFile here — it would fail to compile
  // at the call site where TempFile is out of scope
  $[Socket].sendComplete()
}

// This would NOT compile:
def badFinalize()(using Scope.Has[Socket & TempFile]): Unit = { ... }
// Called from the outer scope where TempFile is gone:
// badFinalize()  // ✗ Compile error: TempFile not in Stack
```

**Scala 2 equivalent:**

```scala
def validate()(implicit scope: Scope.Has[Socket with TempFile]): ValidationResult = {
  $[Socket].send($[TempFile].readAll())
}

def finalize()(implicit scope: Scope.Has[Socket]): Unit = {
  $[Socket].sendComplete()
}
```

**The advantage**: Each scope has a distinct type based on its contents. The compiler prevents:
- Accessing resources that aren't in scope (compile error on `$[TempFile]` after its scope closed)
- Passing a scope that doesn't have required resources (compile error on call site)
- Registering cleanup on the wrong scope (cleanup is always on the current scope via `defer`)

---

## Testing

Swap implementations by providing different wires:

```scala
// Production
Scope.global.injected[UserService].run {
  $[UserService].getUser("123")
}

// Test with mocks
Scope.global.injected[UserService](
  Wire.value(new MockDatabase()),
  Wire.value(new MockCache())
).run {
  assert($[UserService].getUser("123") == expectedUser)
}
```

Or inject mock types directly:

```scala
// MockDatabase and MockCache extend the traits
Scope.global.injected[MockDatabase & MockCache]
  .injected[UserService]
  .run {
    val svc = $[UserService]
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
Scope.global.injected[App].run {
  $[App].run()  // Uses DatabaseImpl via Wireable
}
```

The `Wireable` trait:

```scala
trait Wireable[+Out] {
  type In
  def wire: Wire[In, Out]
}

object Wireable {
  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }
  transparent inline def from[T]: Wireable[T]
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
val configWire = Wire.Shared[Any, Config] {
  Context(Config.load(sys.env("CONFIG_PATH")))
}

val dbWire = Wire.Shared[Config, Database] {
  val config = $[Config]
  val db = Database.connect(config.dbUrl)
  defer(db.close())  // Register cleanup
  Context(db)
}

// Wire that produces multiple services
val infraWire = Wire.Shared[Config, Database & Cache] {
  val config = $[Config]
  val db = Database.connect(config.dbUrl)
  val cache = Cache.create(config.cacheSize)
  defer(db.close())
  defer(cache.close())
  Context(db).add(cache)
}
```

```scala
// Scala 2 — scope is an explicit function parameter
val dbWire = Wire.Shared.fromFunction[Config, Database] { scope =>
  val config = scope.get[Config]
  val db = Database.connect(config.dbUrl)
  scope.defer(db.close())
  Context(db)
}
```

### Using Manual Wires

Pass manual wires to `injected` alongside or instead of macro-generated ones:

```scala
// Mix manual and derived wires
Scope.global.injected[App](configWire, dbWire).run {
  $[App].run()
}

// Override a derived wire with a manual one
Scope.global.injected[App](
  Wire.Shared[Any, Config] { Context(Config(debug = true)) }  // Custom config
).run {
  $[App].run()
}
```

### Wire.value for Injecting Existing Values

Use `Wire.value` to inject a pre-existing value:

```scala
def handleRequest(request: Request)(using Scope.Any): Response =
  injected[RequestHandler](
    Wire.value(request),           // Inject the request
    Wire.value(Transaction.begin()) // Inject a fresh transaction
  ).run {
    $[RequestHandler].handle()
  }
```

---

## Compile-Time Errors

Scope provides rich error messages with ASCII formatting and color support (when running in an interactive terminal). Colors are automatically disabled when `NO_COLOR` is set or `sbt.log.noformat=true`.

### Not a Class

When you try to derive a wire for a trait or abstract type without a `Wireable` instance:

```
── Scope Error ────────────────────────────────────────────────────────────────

  Cannot derive Wire for DatabaseTrait: not a class.

  Hint: Provide a Wireable[DatabaseTrait] instance
        or use Wire.Shared / Wire.Unique directly.

────────────────────────────────────────────────────────────────────────────────
```

### Subtype Conflict

When two dependencies have a subtype relationship, Context cannot reliably distinguish them:

```scala
class BadService(
  input: InputStream,      // General type
  file: FileInputStream    // Subtype of InputStream
)
```

```
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency type conflict in BadService

  FileInputStream is a subtype of InputStream.

  When both types are dependencies, Context cannot reliably distinguish
  them. The more specific type may be retrieved when the more general
  type is requested.

  To fix this, wrap one or both types in a distinct wrapper:

    case class WrappedInputStream(value: InputStream)
    or
    opaque type WrappedInputStream = InputStream

────────────────────────────────────────────────────────────────────────────────
```

### Missing Dependency

When a required dependency is not available in the scope or provided as a wire:

```
── Scope Error ────────────────────────────────────────────────────────────────

  Missing dependency: Cache

  Stack:
    → Database
    → (root)

  Dependency Tree:
    App
    ├── Config ✓
    └── UserService
        ├── Database ✓
        │   └── Config ✓
        └── Cache ✗

  UserService requires:
    ✓ Database  — found in stack
    ✗ Cache     — missing

  Hint: Either:
    • .injected[Cache].injected[UserService]     — Cache visible in stack
    • .injected[UserService](shared[Cache])      — Cache as private dependency

────────────────────────────────────────────────────────────────────────────────
```

The dependency tree shows the full hierarchy of types and their dependencies, with `✓` for found and `✗` for missing. This helps you trace exactly where in the dependency chain a type is required.

### Duplicate Provider

When the same type is provided by multiple wires:

```
── Scope Error ────────────────────────────────────────────────────────────────

  Multiple providers for Config

  Conflicting wires:
    1. shared[Config] at MyApp.scala:15
    2. Wire.value(...) at MyApp.scala:16

  Hint: Remove duplicate wires or use distinct wrapper types.

────────────────────────────────────────────────────────────────────────────────
```

### Dependency Cycle

When dependencies form a circular reference:

```
── Scope Error ────────────────────────────────────────────────────────────────

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
  
  type Any = Scope[scala.Any]                   // Scope with unknown stack  
  type Has[+T] = Scope[Context[T] :: scala.Any] // Scope that has T available
  
  trait Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] with AutoCloseable {
    def close(): Chunk[Throwable]            // Returns all finalizer errors
    def closeOrThrow(): Unit                 // Throws first error if any
    def run[B](f: Scope.Has[Head] ?=> B): B  // Scala 3
    def run[B](f: Scope.Has[Head] => B): B   // Scala 2
    def runWithErrors[B](f: ...): (B, Chunk[Throwable])  // Explicit error capture
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
  
  def apply[T](t: T)(implicit ev: IsNominalType[T]): Wire.Shared[Any, T]  // Inject a value
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
inline def injected[T](using Scope.Any): Scope.Closeable[T, ?]
inline def injected[T](wires: Wire[?,?]*)(using Scope.Any): Scope.Closeable[T, ?]
def injectedValue[T](t: T)(using Scope.Any, IsNominalType[T]): Scope.Closeable[T, ?]
def $[T](using Scope.Has[T], IsNominalType[T]): T
def defer(finalizer: => Unit)(using Scope.Any): Unit

// Scala 2
def shared[T]: Wire.Shared[_, T] = macro ...
def unique[T]: Wire.Unique[_, T] = macro ...
def injected[T](implicit scope: Scope.Any): Scope.Closeable[T, _] = macro ...
def injected[T](wires: Wire[_,_]*)(implicit scope: Scope.Any): Scope.Closeable[T, _] = macro ...
def injectedValue[T](t: T)(implicit scope: Scope.Any, nom: IsNominalType[T]): Scope.Closeable[T, _]
def $[T](implicit scope: Scope.Has[T], nom: IsNominalType[T]): T
def defer(finalizer: => Unit)(implicit scope: Scope.Any): Unit
```

**`injectedValue`** wraps an existing value in a closeable scope. If the value is `AutoCloseable`, its `close()` method is automatically registered as a finalizer.

### Tagged Values (Scala 3 Only)

```scala
// Opaque type for tagging values with scope identity
opaque infix type @@[+A, S] = A

object @@ {
  inline def tag[A, S](a: A): A @@ S
  
  extension [A, S](tagged: A @@ S) {
    // Access value with scope capability check
    inline infix def $[B](inline f: A => B)(using Scope[?] { type Tag >: S })(using Untag[B, S]): Untag[B, S]#Out
    
    // For-comprehension support
    inline def map[B](inline f: A => B): B @@ S
    inline def flatMap[B, T](inline f: A => B @@ T): B @@ (S | T)
    
    // Tuple accessors
    inline def _1[X, Y](using A =:= (X, Y)): X @@ S
    inline def _2[X, Y](using A =:= (X, Y)): Y @@ S
  }
}

// Types that can escape untagged (primitives, String, collections)
trait Unscoped[A]

// Conditional untagging based on Unscoped evidence
trait Untag[A, S] {
  type Out  // A if Unscoped[A] exists, else A @@ S
  def apply(a: A): Out
}
```

**Scope.Closeable additions**:

```scala
trait Closeable[+Head, +Tail] extends Scope[Context[Head] :: Tail] {
  // ... existing methods ...
  
  // Get head value tagged with scope identity (Scala 3 only)
  def value: Head @@ Tag
}
```

---

## Comparison with Alternatives

| Feature | Scope | ZIO ZLayer | Guice | MacWire |
|---------|-------|------------|-------|---------|
| Compile-time verification | ✅ | ✅ | ❌ | ✅ |
| Lifecycle management | ✅ | ✅ | ✅ | ❌ |
| Effect system required | ❌ | ✅ (ZIO) | ❌ | ❌ |
| Type-level stack tracking | ✅ | ❌ | ❌ | ❌ |
| **Resource escape prevention** | ✅ (Scala 3) | ❌ | ❌ | ❌ |
| Async construction | ❌* | ✅ | ❌ | ❌ |
| Scoped instances | ✅ | ✅ | ✅ | ❌ |

*Async construction can be added later if needed.

**Resource escape prevention** is a unique feature of Scope — resources cannot accidentally escape their intended lifecycle, preventing use-after-close bugs at compile time.

---

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

Scope depends on:
- `zio-blocks-chunk` (for `Chunk[Throwable]` error collection)
- `zio-blocks-context` (for `Context`)
- `zio-blocks-typeid` (for type identity)

---

## Summary

### Core API

- **`injected[T]`** — Create child scope, wire dependencies from stack (no parentheses needed)
- **`injected[T](wires...)`** — Wire with explicit/private dependencies (supports arbitrary arity)
- **`injectedValue(x)`** — Wrap existing value in a closeable scope (auto-registers `close()` for AutoCloseable)
- **`.run { ... }`** — Execute with automatic cleanup; can only be called once (Scala 3: context function; Scala 2: lambda with scope)
- **`.runWithErrors { ... }`** — Like `run` but returns `(B, Chunk[Throwable])` for explicit error handling
- **`.close()`** — Returns `Chunk[Throwable]` containing any finalizer errors
- **`.closeOrThrow()`** — Throws the first finalizer error if any occurred
- **`shared[T]`** / **`unique[T]`** — Derive wires that are memoized vs fresh per use
- **`Scope.Any`** — Use in constructors needing `defer`
- **`defer { ... }`** — Register cleanup on current scope
- **`$[T]`** — Retrieve service from current scope
- **`Wire(x)`** — Inject a specific value as a wire
- **`Wireable[T]`** — Enable `injected[T]` for traits

### Resource Escape Prevention (Scala 3)

- **`A @@ S`** — Value of type `A` tagged with scope identity `S`; methods hidden
- **`.value`** — Get tagged value from `Scope.Closeable`
- **`tagged $ (_.method())`** — Access tagged value with scope capability check
- **`tagged.map(f)`** — Transform tagged value, preserving tag
- **`tagged.flatMap(f)`** — Chain tagged operations with union tags
- **`Unscoped[A]`** — Marker for types that can escape untagged (primitives, String, collections)

**Key guarantee**: Resources cannot accidentally escape their scope — use-after-close bugs are compile-time errors.
