---
id: scope
title: "Scope"
---

`Scope` is a **type-safe resource manager** that enforces compile-time verification of resource lifecycles. Every resource allocated within a scope is tagged with that scope's type identity, preventing accidental use after the scope closes. The fundamental operations are `allocate` (acquire a resource), `defer` (register cleanup), and `scoped` (create a child scope).

`Scope`:
- enforces compile-time resource safety through phantom types (`$[A]`)
- runs finalizers in LIFO order when a scope closes
- supports parent-child hierarchies with safe downcasting via `lower`
- provides thread-ownership checking to detect cross-thread usage

```scala
sealed abstract class Scope extends Finalizer { self =>
  type $[+A]
  type Parent <: Scope
  val parent: Parent
  def allocate[A](resource: Resource[A]): $[A]
  def allocate[A <: AutoCloseable](value: => A): $[A]
  def open(): $[Scope.OpenScope]
  def isClosed: Boolean
  def isOwner: Boolean
}

object Scope {
  object global extends Scope
  case class OpenScope(scope: Scope, close: () => Finalization)
  final class Child[P <: Scope](val parent: P, ...)
}
```

## Overview

Resource management is hard. When you allocate a resource—a database connection, file handle, or network socket—you must close it eventually. But what if you accidentally use it after closing? What if the closing order is wrong? What if an exception during cleanup prevents other cleanups from running?

Scope solves this by making resource ownership explicit and enforcing it at compile time. Every value acquired within a scope is tagged with that scope's type identity (via the phantom type `$[A]`). The compiler prevents mixing values from one scope with operations on another. If a resource escapes its scope, the code doesn't compile.

This guide covers the core concepts: the global scope, creating child scopes, allocating resources, registering finalizers, and managing scope hierarchies.

## Installation

Scope is included in the `zio-blocks` core module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks" % "<version>"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x.

## Construction / Creating Instances

### `Scope.global` — The Root Scope

The global scope is the root of all scope hierarchies. It never closes under normal operation (its finalizers run on JVM shutdown), and its `$[A]` type is just `A` (zero-cost).

Access the global scope directly:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val root: Scope = Scope.global
```

### `Scope#scoped` — Create and Enter a Child Scope

Enter a child scope using `scoped`, which creates a temporary scope, runs a block of code, closes the scope, and returns the plain result (unwrapped from `$`).

Here we create a child scope, allocate a resource, use it, and return a computed value:

```scala mdoc:silent:reset
import zio.blocks.scope.{Scope, Resource}

class Database extends AutoCloseable {
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit = println("Database closed")
}
```

Inside the `scoped` block, we can allocate resources:

```scala mdoc:compile-only
Scope.global.scoped { scope =>
  import scope._

  val db: $[Database] = allocate(Resource {
    val d = new Database()
    d
  })

  // Use the database and return a plain value
  42
}
```

The block's return type must be `Unscoped` (safe to escape), so results like `Int`, `String`, or plain data structures can leave the scope. Resources like `Database` cannot.

### `Scope#open` — Create an Unowned Child Scope

For cases where you need explicit control over when a scope closes, use `open()`. This returns an `OpenScope` handle containing the scope and a `close()` function.

The returned scope is **unowned** (no thread affinity), and you must close it manually:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope._

  val handle = scope.open()

  // Extract and use the OpenScope
  $(handle) { openScope =>
    val finalization = openScope.close()
  }
}
```

## Predefined Instances

The `global` scope is the only predefined instance:

- **`Scope.global`** — the root scope, never closes under normal operation, `isOwner` always returns `true`

## Core Operations

### Resource Acquisition

#### `Scope#allocate` — Acquire a Resource

Acquires a resource within this scope. The resource's `make` method is called to produce a value and register cleanup actions. The result is wrapped in `$[A]`, tagged with this scope's type identity.

```scala
trait Scope {
  def allocate[A](resource: Resource[A]): $[A]
}
```

We set up a simple `Database` type, then allocate it within a scope:

```scala mdoc:silent:nest
import zio.blocks.scope.{Scope, Resource}

class Database extends AutoCloseable {
  def status: String = "connected"
  def close(): Unit = println("Closing DB")
}
```

Now we allocate the resource in a scope:

```scala mdoc:compile-only
Scope.global.scoped { scope =>
  import scope._

  val db = allocate(Resource {
    val d = new Database()
    d
  })

  // db is of type $[Database], wrapped in the scope's type identity
}
```

#### `Scope#allocate` (AutoCloseable) — Allocate AutoCloseable Directly

Convenience overload for `AutoCloseable` values. The value's `close()` method is automatically registered as a finalizer.

```scala
trait Scope {
  def allocate[A <: AutoCloseable](value: => A): $[A]
}
```

This is equivalent to `allocate(Resource(value))`, but more concise:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.io.ByteArrayInputStream

Scope.global.scoped { scope =>
  import scope._

  val stream = allocate(new ByteArrayInputStream("data".getBytes))
  // stream is of type $[ByteArrayInputStream]
}
```

### Cleanup and Finalization

#### `Finalizer#defer` — Register a Finalizer

Registers a cleanup action to run when the scope closes. Finalizers run in LIFO order (last registered, first executed). Returns a `DeferHandle` that can cancel the registration before the scope closes.

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

We set up the imports needed for our example:

```scala mdoc:silent
import zio.blocks.scope.Scope
```

Here we register cleanup actions, which execute in reverse order:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope._

  defer { println("Cleanup 3") }
  defer { println("Cleanup 2") }
  defer { println("Cleanup 1") }
  // On scope close: prints "Cleanup 1", then "Cleanup 2", then "Cleanup 3"
  42
}
```

Finalizers always run, even if earlier finalizers throw exceptions. If you need to cancel a finalizer before it runs, hold onto the returned `DeferHandle` and call `.cancel()` on it.

### Scope Inspection

#### `Scope#isClosed` — Check if Scope Is Closed

Returns `true` if this scope's finalizers have already been executed. For `Scope.global`, always returns `false` until JVM shutdown.

```scala
trait Scope {
  def isClosed: Boolean = finalizers.isClosed
}
```

Attempting to allocate in a closed scope throws `IllegalStateException`:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Resource}

val result: Int = Scope.global.scoped { scope =>
  import scope._
  val value = allocate(Resource(42))
  $(value)(identity)
}

// After the scoped block, the scope is closed.
// If we tried to use it again, it would throw IllegalStateException
```

#### `Scope#isOwner` — Check Thread Ownership

Returns `true` if the current thread is the owner of this scope. For `Scope.global` and scopes created via `open()`, always returns `true`. For scopes created via `scoped`, returns `true` only on the thread that entered the block.

```scala
trait Scope {
  def isOwner: Boolean
}
```

This detects cross-thread scope usage:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  // Current thread owns the scope
  assert(scope.isOwner)

  new Thread(() => {
    // Different thread does not own this scope
    assert(!scope.isOwner)
  }).start()
}
```

### Scope Hierarchy

#### `Scope#lower` — Convert Parent-Scoped Values

Safely converts a value scoped to the parent into a value scoped to this child. This is safe because the parent scope always outlives its children: when the child closes, its finalizers run first, then the parent's.

```scala
trait Scope {
  final def lower[A](value: parent.$[A]): $[A]
}
```

When composing scopes, you can bring parent-scoped resources into a child:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { parentScope =>
  import parentScope._

  val db = allocate(Resource(42))

  // Create a child scope
  parentScope.scoped { childScope =>
    import childScope._

    // Convert parent-scoped resource to child-scoped
    val dbInChild = childScope.lower(db)
    // Now dbInChild is usable in the child scope
  }
}
```

#### `Scope#open` (Manual Close) — Create an Unowned Child Scope

Creates a child scope that must be explicitly closed. Unlike `Scope#scoped`, the returned `OpenScope` gives you a `close()` function to call manually.

```scala
trait Scope {
  def open(): $[Scope.OpenScope]
}
```

You can create a child scope using `open()` and manage it explicitly:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope._

  // Create an explicitly-managed child scope
  val childHandle = scope.open()

  // Extract the child and close it manually
  $(childHandle) { child =>
    val finalization = child.close()
    // finalization contains any errors from cleanups
  }
}
```

## Subtypes / Variants

### `Scope.global` — The Global (Root) Scope

The single instance of the global scope. Never closes under normal operation; finalizers registered with `Scope.global.defer` run on JVM shutdown. The `$[A]` type is simply `A` (zero-cost identity type).

**When to use:** As the root for all scope hierarchies. Most user code starts with `Scope.global.scoped { ... }`.

### `Scope.Child[P <: Scope]` — A Child Scope

A child scope created by `scoped { }` or `open()`. Has its own finalizer registry and a reference to its parent. The `$[A]` type is distinct from the parent's, preventing accidental mixing (use `lower` to convert explicitly).

**When to use:** Inside `scoped { }` blocks. You receive a child as the parameter to the block.

**Thread ownership:** If created via `scoped`, owned by the creating thread. If created via `open()`, unowned (all threads can use it).

### `Scope.OpenScope` — An Explicit Handle

A case class containing a child scope and a `close()` function. Returned by `Scope#open()`.

**When to use:** When you need manual control over scope lifetime (e.g., resource pools, streaming pipelines, or testing).

**Key difference from `scoped`:** You control when to close; finalizers don't run until you call `close()`.

## Comparison Sections

### Scope vs. try/finally

| Aspect                  | try/finally                       | Scope                                  |
|-------------------------|-----------------------------------|----------------------------------------|
| **Nesting depth**       | Grows exponentially (pyramid)     | Flat, composable                       |
| **Type safety**         | No compile-time verification      | Compile-time phantom types             |
| **Exception during cleanup** | May skip later finalizers      | All finalizers run (with aggregation)  |
| **Resource escape**     | Silent use-after-free             | Compile error                          |
| **Scope creation**      | Implicit (where you write code)   | Explicit (via `scoped` or `open`)      |

### Scope vs. Java's try-with-resources

| Aspect                  | try-with-resources                | Scope                              |
|-------------------------|-----------------------------------|------------------------------------|
| **Type support**        | `AutoCloseable` only              | Any value with a `Resource`        |
| **Custom acquisition**  | Limited                           | Full control via `acquireRelease`  |
| **Nested resources**    | Nested try blocks                 | Flat composition with `scoped`     |
| **Use-after-close**     | Runtime error when accessing      | Compile error                      |
| **Scope reuse**         | No; scope created/destroyed once  | Yes; can create many child scopes  |

## Advanced Usage

### Resource Sharing with `Resource.shared`

When multiple paths need the same resource, wrap it with `Resource.shared` to ensure it's acquired once and cleaned up once:

```scala mdoc:silent
import zio.blocks.scope.{Scope, Resource}

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(s"[LOG] $msg")
  def close(): Unit = println("Logger closed")
}
```

Shared resources are acquired on first use and closed when all references are released:

```scala mdoc:compile-only
import zio.blocks.scope.Resource

val sharedLogger = Resource.shared { scope =>
  val logger = new Logger()
  logger
}

Scope.global.scoped { scope =>
  import scope._

  val logger1 = allocate(sharedLogger)
  val logger2 = allocate(sharedLogger)  // Same instance!
  // Closed once when scope exits
}
```

### Combining Scopes with Wire

For dependency injection, create wires to describe how resources should be constructed and automatically allocate them in a scope:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Wire}
import zio.blocks.context.Context

// Create a wire that provides a configuration value
val configWire = Wire("app.conf")

// Create a context with the configuration available
val ctx = Context.empty

Scope.global.scoped { scope =>
  import scope._

  // Allocate the config wire as a resource
  val config = allocate(configWire.toResource(ctx))
  // config is now available in this scope
  42
}
```

See [Wire](../scope.md) for more on dependency injection.

### Using Scope with Unscoped

The `Unscoped` typeclass prevents resources from escaping their scope. Any value that exits a scope must have an `Unscoped` instance:

```scala mdoc:silent
import zio.blocks.scope.{Scope, Unscoped}

case class ProcessResult(count: Int, duration: Long)

object ProcessResult {
  implicit val unscoped: Unscoped[ProcessResult] = new Unscoped[ProcessResult] {}
}
```

Now `ProcessResult` can be extracted from a scope:

```scala mdoc:compile-only
Scope.global.scoped { scope =>
  import scope._

  val result = ProcessResult(count = 100, duration = 1000L)
  result  // Compiles: ProcessResult is Unscoped
}
```

See [Unscoped](./unscoped.md) for full details.

## Integration

Scope integrates with [Resource](./resource.md) for resource definitions and [Wire](../../../wire/index.md) for dependency injection. See each page for integration patterns.

When using scopes in larger applications:
- Use `Scope.global.scoped { }` to wrap your main application entry point
- Register cleanup with `defer { }` for any side effects
- Use `open()` for explicit resource management (e.g., connection pools)
- Combine with `Wire.toResource` for type-safe dependency graphs

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Resource Allocation

This example demonstrates how to allocate a simple resource within a scope and use it before the scope closes.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.DatabaseConnectionExample"
```

### Nested Scopes and Scope Hierarchies

This example shows how to create parent-child scope relationships and use `lower` to bring parent-scoped values into child scopes.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.LayeredWebServiceExample"
```

### Thread Ownership and Safety

This example demonstrates thread ownership checking to prevent cross-thread scope usage.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ThreadOwnershipExample"
```

### Connection Pool with Explicit Scope Management

This example shows how to use `open()` to create an explicitly-managed scope for a connection pool.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ConnectionPoolExample"
```

### Transaction Boundaries

This example demonstrates using scopes to enforce transactional boundaries in database operations.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.TransactionBoundaryExample"
```

### Shared Resources

This example illustrates how to use `Resource.shared` to allocate a resource once and share it across multiple scope paths.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.CachingSharedLoggerExample"
```
