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
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

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
  import scope1.*
  val db1 = allocate(new Database)

  scope1.scoped { scope2 =>
    import scope2.*
    val x: scope2.$[Database] = db1  // Error: type mismatch
    // scope2.$[Database] is not compatible with scope1.$[Database]
  }
}
```

To safely use a parent scope's resource in a child scope, use `lower`:

```scala
Scope.global.scoped { outer =>
  import outer.*
  val db = allocate(new Database)

  outer.scoped { inner =>
    import inner.*
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
  import scope.*
  val db = allocate(new Database)
  db  // Error: No given instance of Unscoped[$[Database]]
}
```

Closures over resources are also rejected:

```scala
// does not compile:
Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(new Database)
  () => db.query("SELECT 1")  // Error: No given instance of Unscoped[() => String]
  // (the closure captures db)
}
```

Only types with an `Unscoped` instance can cross the scope boundary—typically pure data:

```scala
Scope.global.scoped { scope =>
  import scope.*
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
import zio.blocks.scope.*

val result: String = Scope.global.scoped { scope =>
  import scope.*
  "no resources allocated"
}
```

### `Scope#scoped` — Create and Enter a Child Scope

`scoped` creates a new child scope with lexical lifetime. All resources allocated within the lambda are automatically cleaned up (LIFO) when the lambda exits, whether normally or via exception:

The lambda receives the child scope as a parameter. You can import its members to use the short form `$[A]` instead of `scope.$[A]`:

```scala mdoc:compile-only
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("database closed")
}

Scope.global.scoped { scope =>
  import scope.*

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

From `Scope.global`, the type is `Scope.OpenScope` directly:

```scala mdoc:compile-only
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val os: Scope.OpenScope = Scope.global.open()

val db = os.scope.allocate(Resource.fromAutoCloseable(new Database))

// ... use db ...

val finalization = os.close()
if (finalization.nonEmpty) {
  finalization.orThrow()
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
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope.*

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
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = allocate(new Database)

  // Infix syntax
  val result1 = (scope $ db)(_.query("SELECT 1"))

  // Unqualified after `import scope.*`
  val result2 = $(db)(_.query("SELECT 2"))

  result1 + result2
}
```

**Multiple values:** Use unqualified syntax only:

```scala mdoc:compile-only
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Cache extends AutoCloseable {
  def key(): String = "cache_key"
  def close(): Unit = ()
}

Scope.global.scoped { scope =>
  import scope.*

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
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { outer =>
  import outer.*

  val db: $[Database] = allocate(new Database)

  // Create an inner scope that needs the database
  outer.scoped { inner =>
    import inner.*

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

`defer` is useful for resources that are not wrapped in `Resource`, or when you need explicit control over finalization:

```scala mdoc:compile-only
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val in = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))

  val handle: DeferHandle = defer(in.close())

  val first = in.read()

  // Cancel the finalizer if you manually cleaned up
  // handle.cancel()
  ()
}
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

`Scope.global` returns `false` until JVM shutdown:

```scala mdoc:compile-only
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  assert(!scope.isClosed) // Open scope

  scope.scoped { child =>
    assert(!child.isClosed) // Child is open while the block runs
  }
  // After child block exits, child is closed
  // But parent scope is still open

  assert(!scope.isClosed) // Still open
}
// Scope is now closed
```

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
import zio.blocks.scope.*

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

## Scope Patterns: Choosing the Right Approach

The Scope API provides two primary patterns for managing resource lifetimes. Choosing between them depends on whether you know the scope's lifetime at design time.

### Pattern 1: Lexical Scopes with `scoped`

Use `Scope#scoped` when **the resource lifetime is lexically bounded**—that is, you can write the code that acquires and releases the resource in the same expression.

The Quickstart section earlier shows a complete example of this pattern. For details on different allocation approaches (with `Resource.fromAutoCloseable()` or directly with `AutoCloseable`), see [Core Operations — allocate](#scope-allocate--acquire-a-resource).

**Advantages:**
- **Automatic cleanup:** Finalizers run when the block exits, even on exception
- **Thread-safe by default:** Lexical scopes are thread-owned, preventing accidental cross-thread use
- **Composable:** Nest `scoped` blocks to express hierarchical resource dependencies
- **Clear intent:** The code structure matches the resource lifetime

**Disadvantages:**
- Requires knowing the scope lifetime upfront
- Cannot easily extend lifetime across function boundaries without returning resources

**Real-world use cases:**
- Processing data from a connection within a single function
- Building a layered service stack (database → connection pool → HTTP client)
- Integration tests that need clean setup/teardown
- One-off resource acquisition in an application entry point

### Pattern 2: Explicit Scopes with `open()`

Use `Scope#open()` when **the resource lifetime is not lexically bounded**—you need manual control over when resources are acquired and released.

Instead of the lexical `scoped { }` block, you explicitly open a scope, allocate resources, and control when to close. Here's the key pattern—returning an `OpenScope` handle from a function:

```scala mdoc:compile-only
import zio.blocks.scope.*

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

For construction and finalization details, see [Scope#open](#scopeopen--create-an-unowned-child-scope).

**Advantages:**
- **Manual lifetime control:** You decide when to close and finalize resources
- **Non-lexical lifetimes:** Resources can outlive the function that created them
- **Shareable across threads:** Open scopes are unowned (usable from any thread)
- **Flexible patterns:** Enables resource pools, lazy initialization, and streaming

**Disadvantages:**
- Manual responsibility for cleanup (easy to forget `close()`)
- Exceptions during cleanup won't prevent the program from continuing (you must call `orThrow()`)
- Thread-safety is the caller's responsibility

**Real-world use cases:**
- Database connection pools (acquire once, release on app shutdown)
- Resource caches or lazy initialization patterns
- Streaming pipelines with external lifetime management
- Testing frameworks that need custom setup/teardown hooks
- Service factories that return resources to be managed by a DI container

### Decision Tree

Ask yourself these questions:

1. **Can I write the code that closes the resource in the same scope/block where I open it?**
   - Yes → Use `scoped { }`
   - No → Use `open()`

2. **Do all callers need thread-safe resource isolation?**
   - Yes (most user code) → Use `scoped { }`
   - No (shared caches, pools) → Use `open()`

3. **Do I need to return the resource or pass it between functions?**
   - No → Use `scoped { }`
   - Yes → Use `open()` with an `OpenScope` handle

### Nesting and Hierarchies

When resources depend on each other, nest `scoped` blocks to express the hierarchy:

```scala mdoc:compile-only
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Connection extends AutoCloseable {
  def close(): Unit = println("connection closed")
}

Scope.global.scoped { outerScope =>
  import outerScope.*

  val db = allocate(Resource.fromAutoCloseable(new Database))

  // Child scope for connection
  outerScope.scoped { innerScope =>
    import innerScope.*

    val conn = allocate(Resource.fromAutoCloseable(new Connection))
    // Use conn and db here
    // conn closes first (LIFO)
  }

  // Can still use db here
  // db closes when outerScope exits
}
```

The parent scope always outlives its children, so you can safely use parent resources in child scopes via `lower`.

## Dependency Injection

For dependency injection patterns with `Wire` and `Resource.from`, see the [Wire reference](./wire.md) and [Resource reference](./resource.md). Scope integrates naturally with resource factories and constructor-based DI.

## Runtime Errors

The following runtime errors occur when scope rules are violated:

### `IllegalStateException` — allocate on closed scope

**When:** You call `allocate`, `open`, or `$` on a scope that has already closed.

**Message:** "Cannot acquire resource: scope has already been closed. ..."

**Fix:** Ensure you only access scoped resources while the scope is still alive. Debug by checking `scope.isClosed` before operations.

### `IllegalStateException` — cross-thread scope usage

**When:** You call `scoped { }` on a scope from a different thread than the owner.

**Message:** "Cannot create child scope: current thread '...' does not own this scope (owner: '...')"

**Fix:** Child scopes created via `scoped` are thread-owned. Use `Scope.global.open()` or `open()` to create unowned scopes that can be shared across threads.

## Compile Errors

The following compile errors occur when `Scope` type rules are violated. All examples below use this scoping pattern (see [Quickstart](#quickstart) for full context):

```scala
Scope.global.scoped { scope =>
  import scope.*
  val db: $[Database] = allocate(new Database)
  // ... usage or error ...
}
```

### `No given instance of Unscoped[MyType]` — escaping a scope

**When:** You try to return a value from a `scoped { }` block that is not known to be safe data.

```scala
db  // ERROR: No given instance of Unscoped[$[Database]]
```

**Fix:** Only return values with an `Unscoped` instance. If your type is pure data, add `Unscoped` (see [Unscoped reference](./unscoped.md)). Otherwise, extract the data you need from the resource before returning:

```scala
$(db)(_.query("data"))  // ✓ Correct: Returns String, which is Unscoped
```

### `Scoped values may only be used as a method receiver` — macro violation

**When:** You use a scoped value in a way other than as a method receiver (e.g., passing as an argument, capturing in a closure).

```scala
$(db)(d => store(d))  // ERROR: Parameter cannot be passed as argument
```

**Fix:** Only call methods on the parameter. If you need to extract data, call a method and return the result:

```scala
$(db)(_.query("data"))  // ✓ Correct: method call
```

## Practical Guidance

### Choosing Scope vs Alternatives

**Use Scope when:**
- You need **compile-time resource safety** — catching escape bugs at compile time rather than runtime
- Building **resource hierarchies** — multiple resources with dependencies where LIFO cleanup order matters
- Writing **synchronous code** — pure functions, CLI tools, batch processing, or traditional imperative code
- You want **zero-cost abstraction** — no runtime overhead, no boxing, no GC pressure

**Use alternatives when:**
- **Async code:** Use `ZIO Scope` (part of ZIO) if you're already in a ZIO application
- **Single resource:** A simple `try/finally` or `Using` is sufficient and more readable
- **Limited scope control:** Java's `try-with-resources` works if you only have `AutoCloseable` types
- **Maximum simplicity:** For one-off scripts or non-critical code where the overhead of Scope learning curve doesn't justify the benefit

### Best Practices: Organizing Your Code

**1. Entry point pattern — use `Scope.global.scoped` at the top level**

Wrap your entire application's resource acquisition in a single lexical scope:

```scala mdoc:compile-only
import zio.blocks.scope.*

object MyApp {
  def main(args: Array[String]): Unit = {
    Scope.global.scoped { scope =>
      import scope.*
      // All resources acquired here
      // Automatic cleanup when main exits
    }
  }
}
```

This is your "outer boundary" for resource safety. Everything inside is protected.

**2. Composition — use `Resource` builders before allocation**

Build your acquisition/release logic first using `Resource` combinators, then allocate in the scope:

```scala mdoc:compile-only
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val dbResource = Resource.fromAutoCloseable(new Database)
val cachedDb = dbResource  // Could compose with other Resources here

Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(cachedDb)
  // Use db
}
```

Keep resource construction and allocation separate for reusability and clarity.

**3. Hierarchy — nest scopes to express resource dependencies**

Nest `scoped` blocks to express resource dependencies, as shown in [Nesting and Hierarchies](#nesting-and-hierarchies).

**4. Control flow — use `$` operator correctly**

The `$` operator is not a traditional function call. Use block syntax. For comprehensive examples of single and multiple scoped value access, see the [`$` operator documentation](#--access-a-scoped-value) in Core Operations.

Common mistakes to avoid:

```scala
// Don't try to store, return, or pass the parameter
$(db)(d => someFunction(d))  // ERROR: can't pass as argument
val result = $(db)(d => d)    // ERROR: can't return parameter
```

### Common Anti-Patterns to Avoid

**Anti-pattern 1: Storing scoped values**

Do NOT try to store scoped values in fields:

```scala
// DON'T do this
class MyService {
  val db: $[Database] = ???  // ERROR: storing a scoped value in a field
}
```

**Fix:** Keep scoped values local within the scope block or use `open()` for manual management.

**Anti-pattern 2: Trying to return resources**

Attempting to return a scoped value from a `scoped` block causes a compile-time error, as detailed in [No given instance of Unscoped[MyType]](#no-given-instance-of-unscopedmytype).

**Fix:** If you need to return a resource, use `open()` and return an `OpenScope` handle instead.

**Anti-pattern 3: Forgetting to use the `$` operator**

Calling methods directly on scoped values is prohibited and causes a compile-time error, as detailed in [Scoped values may only be used as a method receiver](#scoped-values-may-only-be-used-as-a-method-receiver).

**Fix:** Always use the `$` operator to access methods on scoped values.

### Type Safety Tips

**Adding `Unscoped` to your data types:**

If you create custom data types that hold only pure data (no resources), add an `Unscoped` instance to allow them to escape scopes safely:

```scala mdoc:compile-only
import zio.blocks.scope.*
import zio.blocks.scope.Unscoped

case class QueryResult(rows: List[String], count: Int)

object QueryResult {
  implicit val unscoped: Unscoped[QueryResult] = new Unscoped[QueryResult] {}
}

// Now QueryResult can be returned from scoped blocks
Scope.global.scoped { scope =>
  import scope.*
  // ... acquire database ...
  QueryResult(List("a", "b"), 2)  // Returns safely
}
```

**Only add `Unscoped` for pure data types.** Never add it for types that hold resources (connections, streams, file handles).

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
