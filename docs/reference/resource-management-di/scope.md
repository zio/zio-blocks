---
id: scope
title: "Scope"
---

`Scope` is a **compile-time safe resource lifecycle manager** that tags allocated values with a scope-specific type, preventing use-after-close at compile time. Each scope instance has a distinct `$[A]` type that is unique to that scope, making values from different scopes structurally incompatible. The `$` operator macro and `Unscoped` typeclass create multiple layers of compile-time protection, eliminating an entire class of lifetime bugs without runtime overhead.

`Scope`:
- prevents resource leaks and use-after-close via compile-time type checking
- allocates resources eagerly and runs finalizers deterministically in LIFO order
- is purely synchronous with zero runtime overhead (scoped values erase to underlying types)

Here's the interface definition:

```scala
trait Scope {
  type $[+A]

  def scoped[A](f: Scope => A): A
  def allocate[A](resource: Resource[A]): $[A]
  def allocate(value: => AutoCloseable): $[AutoCloseable]
  def open(): $[OpenScope]
  def defer(f: => Unit): DeferHandle
  def lower[A](value: parent.$[A]): $[A]
  def isClosed: Boolean
  def isOwner: Boolean
}
```

## Motivation

Most resource bugs in Scala are "escape" bugs—scenarios where a resource is used outside of its intended lifetime, leading to undefined behavior, crashes, or data corruption:

- **Storing in fields:** You open a database connection and store it in a field, intending to close it in a finalizer. But if the finalizer runs before you're truly done with the connection, or if you forget to close it, the connection is silently used after closure.
- **Capturing in closures:** You create a file handle and pass it to an async framework via a callback. The callback might be invoked long after your scope has closed and the file has been released, causing the program to crash or silently read/write corrupted data.
- **Passing to untrusted code:** You pass a resource to a library function that might store a reference and use it later, outside your scope. You have no way to know when it's safe to close.
- **Mixing lifetimes:** In large codebases, it becomes unclear which scope owns which resource. A developer might use a resource in the wrong scope, or two scopes might try to close the same resource.

Scope addresses these with a *tight* design. Each design choice solves a specific problem and works together with the others:

1. **Compile-time leak prevention via type tagging** — Every scope has its own `$[A]` type, combined with the `$` macro that restricts how you can use values and the `Unscoped` typeclass that marks safe return types. Together, these prevent returning resources from their scope at compile time. No runtime wrapper objects needed.

2. **Zero runtime overhead** — Scoped values erase to the underlying type `A` at runtime (via casts). There's no boxing, no extra objects, no GC pressure. The compile-time safety is "free."

3. **Eager allocation** — Resources are acquired immediately when you call `allocate`, not deferred to some later point. This makes lifetimes predictable and your code matches your mental model.

4. **Deterministic, LIFO finalization** — Finalizers are guaranteed to run in reverse order of allocation when a scope closes. If acquisition order implies dependencies (common in resource hierarchies), cleanup order is automatically correct. Exceptions in finalizers are collected rather than stopping cleanup.

5. **Structured scopes with parent-child relationships** — Scopes form a hierarchy; children always close before parents. The `lower` operator lets you safely use parent-scoped values in children, since parent will outlive child.

If you've used `try/finally`, `Using`, or ZIO's `Scope`, this is the same problem space—but optimized for **synchronous code** with **compile-time boundaries**.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

Supported Scala versions: **2.13.x** and **3.x**.

## Quickstart

Here's a minimal example showing resource allocation, usage, and cleanup. This example introduces a canonical `Database` stub that we'll reuse throughout this guide:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val out: String =
  Scope.global.scoped { scope =>
    import scope._

    val db: $[Database] =
      Resource.fromAutoCloseable(new Database).allocate

    // Safe access: the lambda parameter can only be used as a receiver
    $(db)(_.query("SELECT 1"))
  }

println(out)
```

What's happening in this code:

**Allocating resources in a scope.** When you call `Resource.fromAutoCloseable(new Database).allocate`, you're acquiring a database connection. The `allocate` method returns a **scoped value** of type `scope.$[Database]`—notice the `$` wrapper. This type is unique to the `scope` instance. You can import the scope to use the short form `$[Database]`.

**The `$` operator restricts access.** You cannot call `db.query(...)` directly on `$[Database]` because the methods are hidden at the type level. Instead, you use the `$` access operator: `$(db)(f)`, which takes a lambda. The lambda's parameter must be used only as a receiver (for method/field access), preventing accidental capture or escape.

**Safe return from scoped.** The `scoped` block returns a plain `String` (the result of `_.query("SELECT 1")`). This is safe because `String` is marked as `Unscoped`—a typeclass that says "this type is pure data, safe to leave a scope." If you tried to return `db` instead, the compiler would error.

**LIFO cleanup.** When the `scoped` block exits (normally or via exception), all finalizers run in reverse order. The database's `close()` method is registered automatically because `Database` extends `AutoCloseable`. So cleanup happens at the right time, in the right order, even if an exception occurred.

## Safety Model

Scope's compile-time safety comes from *three reinforcing layers* that work together to prevent resource leaks.

1. **Type identity per scope.** Every scope has a distinct `$[A]` type. This makes values from different scopes **structurally incompatible** at compile time, so you cannot accidentally use a resource in the wrong scope without an explicit conversion (`lower` for parent → child). For example, `scope1.$[Database]` and `scope2.$[Database]` are different types—the compiler refuses to mix them:

```scala
// does not compile:
Scope.global.scoped { scope1 =>
  import scope1._
  val db1 = allocate(new Database)

  scope1.scoped { scope2 =>
    import scope2._
    val x: scope2.$[Database] = db1  // Error: type mismatch
    // scope2.$[Database] is not compatible with scope1.$[Database]
  }
}
```

To safely use a parent scope's resource in a child scope, use `lower`:

```scala
Scope.global.scoped { outer =>
  import outer._
  val db = allocate(new Database)

  outer.scoped { inner =>
    import inner._
    val dbInChild = inner.lower(db)  // ✓ Correct: retags for child scope
    $(dbInChild)(_.query("SELECT 1"))
  }
  // db is still alive here after child closes
}
```

2. **Controlled access via the `$` macro.** The `$` operator only allows using an unwrapped value as a **method/field receiver**. This prevents returning the resource, storing it in a local val/var, passing it as an argument to a function, or capturing it in a closure. The `$` macro also requires a **lambda literal** (not a method reference or variable):

```scala
// does not compile:
val f: Database => String = _.query("x")
(scope $ db)(f) // Error: "$ requires a lambda literal ..."
```

A lambda literal is an anonymous function written directly in code (e.g., `_.query("x")` or `x => x + 1`). The macro inspects the actual code you pass, so you must pass the lambda directly: `$(db)(_.query("x"))` compiles, but storing it in a variable first defeats this check. Without this restriction, you could smuggle the resource out indirectly via a stored function:

```scala
// hypothetical: if the macro didn't require a lambda literal
var leaked: Database = null

val f: Database => String = { db =>
  leaked = db  // Store the database somewhere the macro can't see
  db.query("x")
}

$(db)(f)  // Macro sees the call but can't detect the smuggling above

// After the scope closes, the resource is still accessible:
leaked.query("SELECT *")  // Use-after-close bug!
```

By requiring a lambda literal, the macro can analyze the actual code syntax. It rejects any attempt to store or capture the parameter, making smuggling impossible.

3. **Scope boundary enforcement via `Unscoped`.** A `scoped { ... }` block can only return values with an `Unscoped` instance (pure data). Resources and closures cannot escape the scope boundary at compile time. For example, trying to return a resource directly fails:

```scala
// does not compile:
Scope.global.scoped { scope =>
  import scope._
  val db = allocate(new Database)
  db  // Error: No given instance of Unscoped[$[Database]]
}
```

Closures over resources are also rejected:

```scala
// does not compile:
Scope.global.scoped { scope =>
  import scope._
  val db = allocate(new Database)
  () => db.query("SELECT 1")  // Error: No given instance of Unscoped[() => String]
  // (the closure captures db)
}
```

Only types with an `Unscoped` instance can cross the scope boundary—typically pure data:

```scala
Scope.global.scoped { scope =>
  import scope._
  val db = allocate(new Database)
  $(db)(_.query("SELECT 1"))  // ✓ Correct: returns String, which is Unscoped
}
```

## Construction / Creating Instances

### `Scope.global` — The Root Scope

`Scope.global` is the predefined root scope instance. It exists for the lifetime of your application and is the entry point for all scope-based resource management.

In `Scope.global`, the `$[A]` type is an identity type (i.e., `$[A] = A`). Finalizers registered in the global scope run on JVM shutdown via a shutdown hook. On Scala.js, global finalizers are not automatically invoked.

Use `Scope.global` to access the root scope:

```scala mdoc:compile-only
import zio.blocks.scope._

val result: String = Scope.global.scoped { scope =>
  import scope._
  "no resources allocated"
}
```

### `Scope#scoped` — Create and Enter a Child Scope

`scoped` creates a new child scope with lexical lifetime. All resources allocated within the lambda are automatically cleaned up (LIFO) when the lambda exits, whether normally or via exception:

The lambda receives the child scope as a parameter. You can import its members to use the short form `$[A]` instead of `scope.$[A]`:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("database closed")
}

Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  // Use the database within the scope
  val result = $(db)(_.query("SELECT 1"))
  result
  // db is automatically closed here (scope exits)
}
```

### `Scope#open` — Create an Unowned Child Scope

`open()` creates a child scope you explicitly close, returning an `OpenScope` handle. Unlike `scoped { }`, this allows non-lexical lifetime management:

The child scope is **unowned** (usable from any thread) but remains **linked to the parent** (if the parent closes, the child's finalizers also run). You must call `close()` to detach and finalize immediately.

This is useful for resource pools, lazy initialization, or service factories where you need to decouple resource acquisition from cleanup. Unlike `scoped { }`, which ties lifetime to a lexical block, `open()` lets you keep resources alive across function boundaries and explicit time boundaries.

Here's a practical application initialization pattern:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// Application initialization: open resources early, return handle for later cleanup
val appResources = Scope.global.open()
val db = appResources.scope.allocate(Resource.fromAutoCloseable(new Database))

try {
  // Use database from anywhere in the application
  val result = appResources.scope.scoped { scope =>
    import scope._
    // Can create child scopes and use parent resources with lower()
    val dbInChild = scope.lower(db)
    $(dbInChild)(_.query("SELECT 1"))
  }

  println(s"Query result: $result")

  // ... rest of application code ...

} finally {
  // Application shutdown: explicit cleanup (decoupled from creation)
  appResources.close().orThrow()
}
```

## Core Operations

### `Scope#allocate` — Acquire a Resource

Allocates a `Resource[A]` in this scope, acquiring the underlying value immediately and registering its finalizer:

```scala
trait Scope {
  def allocate[A](resource: Resource[A]): $[A]
  def allocate[A <: AutoCloseable](value: => A): $[A]
}
```

The first overload accepts any `Resource`. The second is a convenience for `AutoCloseable` values—their `close()` method is automatically registered as a finalizer.

If the scope is already closed, `allocate` throws `IllegalStateException`. Otherwise, the resource is acquired eagerly and its finalizer is registered to run LIFO when the scope closes:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope._

  // Using Resource factory
  val db1: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  // Using AutoCloseable overload (convenience)
  val db2: $[Database] = allocate(new Database)

  // Both are equivalent; use whichever is more readable
  ()
}
```

### `$` — Access a Scoped Value

The `$` operator safely accesses a scoped value by enforcing it is only used as a method/field receiver, preventing accidental capture or escape:

**Single value:** Use infix or unqualified syntax:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(new Database)

  // Infix syntax
  val result1 = (scope $ db)(_.query("SELECT 1"))

  // Unqualified after `import scope._`
  val result2 = $(db)(_.query("SELECT 2"))

  result1 + result2
}
```

**Multiple values:** Use unqualified syntax only:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Cache extends AutoCloseable {
  def key(): String = "cache_key"
  def close(): Unit = ()
}

Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(new Database)
  val cache: $[Cache] = allocate(new Cache)

  // Multiple values: each parameter may only be a receiver
  val result = $(db, cache)((d, c) => d.query(c.key()))
  result
}
```

The `$` macro enforces receiver-only rules at compile time:
- ✓ Allowed: `d.method()`, `d.method(c.key())` (method calls, field access)
- ✗ Rejected: `store(d)`, `() => d.method()`, `d` (returned), `{ val x = d; 1 }` (binding)

If a result type is `Unscoped[B]` (pure data), `$` auto-unwraps it to `B`. Otherwise, it returns `scope.$[B]`.

### `Scope#lower` — Use a Parent Value in a Child Scope

`lower` retagges a parent-scoped value into a child scope. This is safe because a parent scope always outlives its children:

This is useful when a child scope needs access to resources allocated in its parent:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { outer =>
  import outer._

  val db: $[Database] = allocate(new Database)

  // Create an inner scope that needs the database
  outer.scoped { inner =>
    import inner._

    // Retag the parent's database into the child
    val dbInChild: inner.$[Database] = inner.lower(db)

    // Now use it in the child
    $(dbInChild)(_.query("child query"))
  }
  // When inner exits, its finalizers run
  // When outer exits, db's finalizers run (still alive for the outer scope)
}
```

### `Finalizer#defer` — Register a Manual Finalizer

`defer` registers a cleanup action to run when the scope closes. It returns a `DeferHandle` that can cancel the registration:

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

`defer` is useful for resources that are not wrapped in `Resource`, or when you need explicit control over finalization. Here's a practical example—managing a temporary file and a logger that don't implement `AutoCloseable`:

```scala mdoc:compile-only
import zio.blocks.scope._
import java.nio.file.*

// A logger that needs manual cleanup but doesn't implement AutoCloseable
class Logger {
  def log(msg: String): Unit = println(s"[LOG] $msg")
  def close(): Unit = println("Logger closed")
}

Scope.global.scoped { scope =>
  import scope._

  // Create a temporary file (not AutoCloseable from standard library)
  val tempFile = Files.createTempFile("app", ".tmp")
  defer {
    Files.deleteIfExists(tempFile)
    println(s"Temp file deleted: $tempFile")
  }

  // Create a logger (not AutoCloseable)
  val logger = new Logger
  val loggerHandle = defer(logger.close())

  // Use both resources
  logger.log("Processing file: " + tempFile)
  Files.write(tempFile, "data".getBytes())

  // If needed, cancel the finalizer and clean up manually
  val data = Files.readAllBytes(tempFile)
  logger.log(s"Read ${data.length} bytes")

  // loggerHandle.cancel()  // Would prevent auto-cleanup
}
// When the scope exits: logger closes, then temp file is deleted (LIFO order)
```

If the scope is already closed, `defer` is silently ignored (no-op). The finalizer is guaranteed to run in LIFO order with other finalizers when the scope closes.

### `Scope#isClosed` — Check If Closed

Returns whether this scope's finalizers have already run:

```scala
trait Scope {
  def isClosed: Boolean
}
```

Once `isClosed` returns `true`, subsequent calls to `allocate`, `open`, or `$` throw `IllegalStateException`. This is checked to prevent use-after-close bugs.

Here's a practical example—a resource manager that guards against using a closed scope:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// A service that holds and manages a scope
class DatabaseService {
  private val serviceScope = Scope.global.open()

  // Initialize database once at startup
  private val db = {
    try {
      serviceScope.scope.allocate(Resource.fromAutoCloseable(new Database))
    } catch {
      case e: IllegalStateException =>
        serviceScope.close().orThrow()
        throw e
    }
  }

  def isAvailable: Boolean = !serviceScope.scope.isClosed

  def execute(query: String): Either[String, String] = {
    if (serviceScope.scope.isClosed) {
      Left("Database service has been shut down")
    } else {
      try {
        Right(serviceScope.scope.scoped { scope =>
          import scope._
          val dbInChild = scope.lower(db)
          $(dbInChild)(_.query(query))
        })
      } catch {
        case e: IllegalStateException => Left(s"Service error: ${e.getMessage}")
      }
    }
  }

  def shutdown(): Unit = {
    if (!serviceScope.scope.isClosed) {
      serviceScope.close().orThrow()
      println("Service shutdown complete")
    }
  }
}

// Usage
val service = new DatabaseService
println(s"Service available: ${service.isAvailable}")

val result1 = service.execute("SELECT 1")
println(s"Query result: $result1")

service.shutdown()

// Attempting to use after shutdown is now safe
val result2 = service.execute("SELECT 2")
println(s"Query after shutdown: $result2")
```

`Scope.global` returns `false` until JVM shutdown. Child scopes created with `scoped { }` are closed when the block exits, while those created with `open()` remain open until you call `close()`.

### `Scope#isOwner` — Check Thread Ownership

Returns whether the calling thread is the owner of this scope:

```scala
trait Scope {
  def isOwner: Boolean
}
```

Ownership is used to detect cross-thread scope misuse. Thread ownership rules:
- `Scope.global`: always returns `true` (any thread may use it)
- Child scopes created via `scoped { }`: returns `true` only on the thread that entered the block
- Child scopes created via `open()`: always returns `true` (unowned, usable cross-thread)

Calling `scoped { }` on a scope you don't own throws `IllegalStateException` at runtime:

```scala mdoc:compile-only
import zio.blocks.scope._

Scope.global.scoped { scope =>
  // On the thread that entered scoped, isOwner is true
  assert(scope.isOwner)

  // On a different thread, isOwner returns false
  val thread = new Thread {
    override def run(): Unit = {
      assert(!scope.isOwner)
    }
  }
  thread.start()
  thread.join()
}
```

**Example 1: Thread-owned scope (scoped) — fails on worker thread**

Thread-owned scopes cannot be used to create child scopes from a different thread:

```scala mdoc:compile-only
import zio.blocks.scope._
import java.util.concurrent.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val executor = Executors.newFixedThreadPool(1)

try {
  Scope.global.scoped { scope =>
    import scope._
    val db = allocate(new Database)

    // Try to create a child scope from a different thread
    val future = executor.submit { () =>
      try {
        scope.scoped { childScope =>
          import childScope._
          val dbInChild = childScope.lower(db)
          $(dbInChild)(_.query("SELECT 1"))
        }
      } catch {
        case e: IllegalStateException => s"Error: ${e.getMessage}"
      }
    }
    println(future.get())
  }
} finally {
  executor.shutdown()
}

// Example Output:
// Error: Cannot create child scope: current thread 'pool-1-thread-1' does not own this scope (owner: 'main')
// db closed
```

**Example 2: Unowned scope (open) — works across threads**

Open scopes are unowned and usable from any thread:

```scala mdoc:compile-only
import zio.blocks.scope._
import java.util.concurrent.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val executor = Executors.newFixedThreadPool(1)

try {
  val poolScope = Scope.global.open()
  val db = poolScope.scope.allocate(Resource.fromAutoCloseable(new Database))

  // Use the resource from a worker thread
  val future = executor.submit { () =>
    poolScope.scope.scoped { scope =>
      import scope._
      val dbInChild = scope.lower(db)
      $(dbInChild)(_.query("SELECT 1"))
    }
  }
  println(future.get())

  poolScope.close().orThrow()
} finally {
  executor.shutdown()
}

// Output:
// result: SELECT 1
// db closed
```

The key difference: `scoped { }` creates **owned** scopes (tied to the entering thread), while `open()` creates **unowned** scopes (usable from any thread). Choose based on whether your resources need to cross thread boundaries.

## Returning Unscoped Data from a Scope

A `scoped { }` block can only return values that have an `Unscoped` instance—that is, pure data types with no embedded resources or cleanup logic. This restriction prevents resource leaks: you cannot accidentally return a resource that would be cleaned up before you could use it.

For built-in types like `String`, `Int`, or `List[String]`, `Unscoped` instances exist automatically. However, when your custom type contains a field whose type has no predefined `Unscoped` instance (such as `java.util.Date`, a legacy Java type that is pure data but not automatically recognized), automatic derivation won't work. In such cases, you must provide an `Unscoped` instance explicitly, asserting that your type holds only pure data:

```scala mdoc:compile-only
import java.util.Date
import zio.blocks.scope._
import zio.blocks.scope.Unscoped

// java.util.Date has no predefined Unscoped instance, so Unscoped.derived
// won't work here — we must provide the instance explicitly
case class QueryResult(rows: List[String], count: Int, executedAt: Date)

object QueryResult {
  implicit val unscoped: Unscoped[QueryResult] = new Unscoped[QueryResult] {}
}

Scope.global.scoped { scope =>
  import scope._
  // ... acquire database ...
  QueryResult(List("a", "b"), 2, new Date())  // Returns safely
}
```

**Only add `Unscoped` for pure data types.** Never add it for types that hold resources (connections, streams, file handles). If you encounter the compile error [`No given instance of Unscoped[MyType]`](#no-given-instance-of-unscopedmytype--escaping-a-scope), see the compile errors section for how to fix it. For the complete API and examples, see the [Unscoped reference](./unscoped.md).

## Scope Patterns: Choosing the Right Approach

The Scope API provides two primary patterns for managing resource lifetimes. Choose `Scope#scoped` if you can write both the code that acquires and the code that releases the resource in the same expression; choose `Scope#open()` if the resource lifetime must outlive the function that creates it. Most user code should prefer `scoped` for automatic cleanup and thread safety—use `open()` only when you need manual lifetime control, such as in connection pools or DI containers.

### Lexical Scopes with `Scope#scoped`

Use `Scope#scoped` when the resource lifetime is lexically bounded. Lexical scopes are thread-owned by default, preventing accidental cross-thread access and providing automatic cleanup even on exception. This makes them safe and composable: you can nest `scoped` blocks to express hierarchical resource dependencies, and the code structure naturally matches the resource lifetime.

Here's a basic pattern showing how to acquire and use a resource within a single scope:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope._

  val db = allocate(new Database)
  $(db)(_.query("SELECT * FROM users"))
  // db closes when scope exits
}
```

The only trade-off is that you must know the scope's lifetime upfront and cannot easily extend resource lifetime across function boundaries without returning resources themselves. For details on different allocation approaches (with `Resource.fromAutoCloseable()` or directly with `AutoCloseable`), see [Core Operations — allocate](#scopeallocate--acquire-a-resource).

#### Nesting for hierarchical resources

When resources depend on each other, nest `scoped` blocks to express the hierarchy. Parent scopes always outlive their children, so you can safely use parent resources in child scopes:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Connection extends AutoCloseable {
  def close(): Unit = println("connection closed")
}

Scope.global.scoped { outerScope =>
  import outerScope._

  val db = allocate(new Database)

  // Child scope for connection
  outerScope.scoped { innerScope =>
    import innerScope._

    val conn = allocate(new Connection)
    // Use conn and db here
    // conn closes first (LIFO)
  }

  // Can still use db here
  // db closes when outerScope exits
}
```

### Explicit Scopes with `Scope#open`

Use `Scope#open()` when the resource lifetime is not lexically bounded. Open scopes are unowned (usable from any thread), which makes them suitable for patterns like connection pools, resource caches, and DI containers where resources must outlive the function that creates them. This pattern gives you full control over resource acquisition and release timing.

The trade-off is that you accept full responsibility for cleanup: forgetting to call `close()` leaves resources open, and any exception during cleanup must be explicitly handled. Here's the key pattern—returning an `OpenScope` handle from a function:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

def acquireDatabase(): Scope.OpenScope = {
  val os = Scope.global.open()
  val _ = os.scope.allocate(Resource.fromAutoCloseable(new Database))
  os
}

val handle = acquireDatabase()
try {
  // Use handle.scope as needed
  ()
} finally {
  handle.close().orThrow()
}
```

## Dependency Injection

`Scope` integrates seamlessly with `Wire` and `Resource.from` for automatic dependency injection. `Wire` describes a recipe for constructing a service and its dependencies, while `Scope` manages the resource lifetime. Together they eliminate manual dependency passing and ensure proper cleanup in LIFO order.

Here's an example using `Wire` and `Resource.from` within a scope:

```scala mdoc:compile-only
import zio.blocks.scope._

final class Config(val dbUrl: String)

final class Database(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println(s"db closed (${config.dbUrl})")
}

final class UserService(db: Database) {
  def getUser(id: Int): String = s"user $id from ${db.query("SELECT * FROM users")}"
}

final class App(service: UserService) {
  def run(): Unit = println(service.getUser(1))
}

// Wire describes the dependency graph: App -> UserService -> Database -> Config
// Resource.from uses the Wire to automatically construct the entire graph
Scope.global.scoped { scope =>
  import scope._
  val config = Config("jdbc:postgres://localhost/db")
  val app = allocate(Resource.from[App](
    Wire(config)
  ))
  $(app)(_.run())
  // All resources (Database, App) clean up automatically in reverse order
}
```

For more details on `Wire` sharing strategies, resource composition, and advanced DI patterns, see the [Wire reference](./wire.md) and [Resource reference](./resource.md).

## Runtime Errors

Runtime errors occur when you violate scope rules at runtime—typically by accessing resources after the scope has already cleaned them up, or by mixing scopes across threads.

### `IllegalStateException` — accessing a closed scope

This error occurs when you attempt to acquire resources (via `allocate`, `open`, or the `$` operator) on a scope that has already closed. Every `scoped { }` block cleans up its resources as soon as the block exits, so any attempt to use the scope after that point fails.

The error message is typically:
```
Cannot acquire resource: scope has already been closed. ...
```

This usually happens when you:
- Store a scope in a field or closure and try to use it later after the enclosing `scoped` block has exited
- Accidentally pass a scope to an async operation that runs after cleanup

To avoid this, keep the scope's lifetime clear: allocate resources, use them, then let them clean up when the scope exits. If you need resources to survive longer, use `Scope.global.open()` to get a handle you can manage manually. You can also check `scope.isClosed` before attempting operations as a defensive check.

### `IllegalStateException` — cross-thread scope usage

Scopes are thread-owned by default. When you create a child scope using `scoped { }`, it's owned by the thread that created it. If you try to access that scope (allocate resources, create child scopes) from a different thread, the scope will reject it.

The error message is typically:
```
Cannot create child scope: current thread '...' does not own this scope (owner: '...')
```

This happens when you try to:
- Pass a scope to another thread and use it there
- Share a scope across multiple threads that call `scoped { }` on it

To fix this, use thread-unowned scopes when you need to share across threads. Instead of `scope.scoped { }` (which creates a thread-owned child), use `Scope.global.open()` or the scope's `open()` method directly to get an `OpenScope` handle. These unowned scopes can be safely passed and used from any thread, though you're responsible for manual cleanup via the returned handle.

## Compile Errors

The following compile errors occur when `Scope` type rules are violated. All examples below use this scoping pattern (see [Quickstart](#quickstart) for full context):

```scala
Scope.global.scoped { scope =>
  import scope._
  val db: $[Database] = allocate(new Database)
  // ... usage or error ...
}
```

### `No given instance of Unscoped[MyType]` — escaping a scope

This error occurs when the type you return from a `scoped { }` block has no `Unscoped` instance. See [Scope boundary enforcement via `Unscoped`](#safety-model) for the full explanation of why this restriction exists.

If you write:
```scala
db  // ERROR: No given instance of Unscoped[$[Database]]
```

The compiler rejects this because `db` is a scoped resource (type `$[Database]`), not safe data. Even though you're inside the `scoped` block, the type system prevents you from returning it because it would be useless outside the scope (the resource would already be cleaned up).

To fix this, you have two options:

**Option 1: Extract data from the resource before returning**

Call a method on the resource to get pure data (strings, numbers, etc.) that are naturally `Unscoped`:

```scala
$(db)(_.query("data"))  // ✓ Correct: Returns String, which is Unscoped
```

The `String` returned by `query()` is pure data with no cleanup logic, so it can safely escape the scope.

**Option 2: Implement `Unscoped` for your custom types**

If you create custom types that hold only pure data, add an `Unscoped` instance so they can escape scopes. See the [Unscoped reference](./unscoped.md) for details and examples.

### `Scoped values may only be used as a method receiver` — macro violation

**What this means:** A scoped value (the parameter inside a `$(value)` lambda) can only be used as the receiver of a method call—the object you call `.method()` on. It cannot be passed to other functions, stored in variables, or captured in nested lambdas. This restriction prevents the resource from leaking out of its scope and being used after cleanup.

**When you hit this error:**

The macro detects several violations:

- **Passing as an argument:**
  ```scala
  $(db)(d => store(d))    // ERROR: cannot pass scoped value to a function
  $(db)(d => println(d))  // ERROR: cannot pass to println
  ```

- **Storing in a variable:**
  ```scala
  $(db)(d => {
    val conn = d           // ERROR: cannot bind to val/var
    conn.query()
  })
  ```

- **Returning the value itself:**
  ```scala
  $(db)(d => d)           // ERROR: must call a method, not return bare reference
  ```

- **Capturing in a nested lambda or closure:**
  ```scala
  $(db)(d =>
    () => d.query()       // ERROR: cannot capture in nested lambda
  )
  ```

**What works — calling methods on the parameter:**

```scala
$(db)(d => d.query("SELECT * FROM users"))      // ✓ Method call on receiver
$(db)(_.query("data"))                           // ✓ Using underscore shorthand
$(db)(d => d.execute(statement).rows)           // ✓ Chain method calls
```

If you need to transform or extract data from a resource before using it elsewhere, call a method to extract what you need:

```scala
$(db)(d => d.query("SELECT COUNT(*)"))  // ✓ Returns String (pure data)
// The returned String can now be passed to other functions
```

**Why this restriction exists:** Scoped values are bound to a specific cleanup phase. Allowing them to escape (via arguments or closures) would let them be used after cleanup, causing crashes or data corruption. By restricting usage to method calls only, the macro ensures the resource never leaves its scope.

## Practical Guidance

### Best Practices: Organizing Your Code

#### Entry point pattern — use `Scope.global.scoped` at the top level

Wrap your entire application's resource acquisition in a single lexical scope:

```scala mdoc:compile-only
import zio.blocks.scope._

object MyApp {
  def main(args: Array[String]): Unit = {
    Scope.global.scoped { scope =>
      import scope._
      // All resources acquired here
      // Automatic cleanup when main exits
    }
  }
}
```

This is your "outer boundary" for resource safety. Everything inside is protected.

#### Composition — use `Resource` builders before allocation

Build resource acquisition/release logic outside the scope, then allocate once inside. This separates *construction* (how) from *allocation* (when), making code testable and reusable.

**Key combinators:**

- **`.map(f)`** — Transform a resource's value
- **`.flatMap(f)`** — Chain resources where the second depends on the first
- **`.zip(other)`** — Combine two independent resources

**Example: Using `.zip()` to combine independent resources:**

```scala mdoc:compile-only
import zio.blocks.scope._

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Cache extends AutoCloseable {
  def get(key: String): Option[String] = None
  def close(): Unit = println("cache closed")
}

// Compose outside scope — reusable across multiple applications
val dbResource = Resource.fromAutoCloseable(new Database)
val cacheResource = Resource.fromAutoCloseable(new Cache)
val appResources = dbResource.zip(cacheResource)

// Allocate once inside scope
Scope.global.scoped { scope =>
  import scope._
  val (db, cache) = allocate(appResources)
  // Use both — cleanup happens in LIFO order (cache first, then db)
}
```

**Example: Using `.flatMap()` for dependent resources:**

```scala mdoc:compile-only
import zio.blocks.scope._

final class Config(val host: String, val port: Int)

final class Database(val config: Config) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// Config resource must be acquired first, then database
val configResource = Resource(new Config("localhost", 5432))
val dbResource = configResource.flatMap { cfg =>
  Resource.fromAutoCloseable(new Database(cfg))
}

// Allocate the dependent chain
Scope.global.scoped { scope =>
  import scope._
  val db = allocate(dbResource)
  // db was initialized with config; cleanup happens in reverse order
}
```

## Integration

Scope integrates seamlessly with ZIO Blocks' other data types for building complex resource management systems.

### Resource

`Scope` manages the lifecycle of `Resource[A]` values through the `allocate` method. A `Resource` describes how to acquire and clean up a value; `Scope` executes that plan and tracks finalizers. For comprehensive information on constructing, composing, and sharing resources, see the [Resource reference](./resource.md).

Key integration points:
- Use `Resource[A].allocate` to acquire within a scope
- Compose resources with `flatMap`, `andThen`, and other combinators before allocating
- Use `Resource.shared` for multiple-use resources within a scope

### Finalizer

`Scope` extends `Finalizer`, the interface for registering cleanup actions. The `defer` method registers a finalizer that runs when the scope closes. For information on the `DeferHandle` and cancellation, see the [Finalizer reference](./finalizer.md).

Key integration points:
- `scope.defer(f)` registers a cleanup action
- `DeferHandle.cancel()` prevents a finalizer from running
- Finalizers run in LIFO order regardless of whether registered via `allocate` or `defer`

### Wire + Resource.from

For dependency injection patterns, Scope works naturally with [`Wire`](./wire.md) and [`Resource.from`](./resource.md) to build layered service architectures. Allocate resources in a parent scope, then use `lower` to pass them to child scopes as needed.

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Database Connection Lifecycle Management

This example demonstrates how to allocate a database connection within a scope, ensure proper cleanup, and handle the connection's lifecycle safely.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.DatabaseConnectionExample"
```

### Managing a Connection Pool with Multiple Allocations

This example demonstrates allocating multiple connections from a pool within the same scope and ensuring all are cleaned up correctly.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ConnectionPoolExample"
```

### Handling Temporary File Resources with Automatic Cleanup

This example shows how to allocate temporary file resources and ensure they are automatically cleaned up when the scope closes, even if errors occur.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.TempFileHandlingExample"
```

### Managing Database Transactions with Commit/Rollback Semantics

This example demonstrates managing database transactions within a scope, showing how to handle commit and rollback operations correctly.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.TransactionBoundaryExample"
```

### Implementing an HTTP Client Pipeline with Request/Response Interceptors

This example shows how to build an HTTP client pipeline with interceptors for logging, authentication, and error handling, all managed within a scope.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.HttpClientPipelineExample"
```

### Managing a Shared, Cached Logger Across Multiple Services

This example demonstrates allocating a logger once at the top level and sharing it across multiple services, ensuring it is properly closed when the application shuts down.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.CachingSharedLoggerExample"
```

### Building a Layered Web Service with Dependency Injection

This example shows how to build a multi-layered web service using Scope for dependency injection, allocating services at different layers and passing them down through child scopes.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.LayeredWebServiceExample"
```

### Reading Configuration from a File with Scope Management

This example demonstrates loading configuration from a file within a scope, ensuring the file handle is properly closed when no longer needed.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ConfigReaderExample"
```

### Implementing a Plugin Architecture with Automatic Resource Discovery

This example shows how to build a plugin system that discovers and loads plugins dynamically, managing their lifecycle with scopes.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.PluginArchitectureExample"
```

### Demonstrating Thread Ownership Enforcement in Scope Hierarchies

This example demonstrates how Scope enforces thread ownership, preventing cross-thread scope misuse and illustrating the difference between owned and unowned scopes.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ThreadOwnershipExample"
```

### Detecting and Demonstrating Circular Dependency Scenarios

This example shows how to detect and handle circular dependencies in resource management, illustrating how scopes help prevent subtle bugs in complex dependency graphs.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.CircularDependencyDemoExample"
```

### Using Scope with Legacy Libraries that Don't Support Managed Resources

This example demonstrates how to integrate Scope with legacy libraries that don't natively support resource management, using wrapper resources and the `leak` escape hatch when necessary.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.LegacyLibraryInteropExample"
```

### Integration Testing with Automatic Setup and Teardown

This example shows how to use Scope to manage test fixtures and resources in integration tests, ensuring automatic cleanup between test runs and proper resource finalization.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.IntegrationTestHarnessExample"
```
