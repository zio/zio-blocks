---
id: resource
title: "Resource"
---

`Resource[A]` is a **lazy recipe for managing resource lifecycles**, encapsulating both acquisition and finalization tied to a `Scope`. Resources describe *what* to do, not *when* — creation only happens when a resource is passed to `scope.allocate()`. They compose naturally with `map`, `flatMap`, and `zip` to build complex dependency graphs with automatic cleanup in LIFO order.

```scala
sealed trait Resource[+A] {
  def map[B](f: A => B): Resource[B]
  def flatMap[B](f: A => Resource[B]): Resource[B]
  def zip[B](that: Resource[B]): Resource[(A, B)]
}
```

Key properties:
- **Lazy**: Resources don't acquire anything until allocated via `scope.allocate()`
- **Covariant**: `Resource[Dog]` is a subtype of `Resource[Animal]` when `Dog <: Animal`
- **Composable**: `map`, `flatMap`, and `zip` combine resources into larger structures
- **Two strategies**: Shared (memoized with reference counting) and Unique (fresh per allocation)
- **Auto-cleanup**: Finalizers run automatically in LIFO order when scopes close

## Motivation

Without Resources, managing complex initialization and cleanup is tedious and error-prone. Resources eliminate manual bookkeeping by tying value lifecycles to Scopes and registering finalizers automatically.

**Benefits:**
- Automatic cleanup even on exceptions
- LIFO finalization order (inner resources close before outer ones)
- Compositional: build complex dependency graphs declaratively
- Type-safe: compiler ensures you have dependencies available
- Works seamlessly with `Wire` for constructor-based dependency injection

## Installation

Add the ZIO Blocks Scope module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "@VERSION@"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-scope" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## Shared and Unique Resources

Resource offers two distinct types for managing instance lifecycles: **Shared** for singleton-like resources that are allocated once and reused across multiple scopes, and **Unique** for fresh resources created on each allocation. Understanding when to use each type is critical for building efficient and correct resource-management architectures.

### Comparison

| Aspect             | Shared                                             | Unique                                    |
|--------------------|----------------------------------------------------|-------------------------------------------|
| **Creation**       | `Resource.shared(f)`                               | `Resource.unique(f)` or `Resource(value)` |
| **Memoization**    | Yes, with reference counting                       | No, fresh per allocation                  |
| **When to use**    | Expensive resources (DB connections, thread pools) | Per-request state, isolated handlers      |
| **Instance reuse** | Same instance across all allocations               | New instance per allocation               |
| **Finalization**   | Runs when last reference released                  | Runs immediately when scope closes        |
| **Thread safety**  | Lock-free atomic reference counting                | Per-scope, no global coordination needed  |

### Shared Resources

**Shared resources are memoized**: the first allocation initializes the instance; subsequent allocations return the same reference with automatic reference counting. Finalizers run only when the last reference is released. Use shared resources for **expensive, globally singular components** — database connection pools, thread pools, logging systems, caches, and other heavyweight infrastructure that should exist exactly once for the lifetime of the application.

The canonical example is a **database connection pool**. Building a fresh pool for each service layer is wasteful and defeats pooling's purpose. Instead, wrap the pool in a shared resource: both the user service and order service allocate the same pool instance, with the system automatically tracking references and closing the pool only when the last service releases it.

Here's what shared acquisition looks like:

```scala
object Resource {
  def shared[A](f: Scope => A): Resource[A]
}
```

When you allocate a shared resource, reference counting ensures cleanup happens exactly once:

```scala mdoc:compile-only
import zio.blocks.scope._

class DatabasePool extends AutoCloseable {
  def close(): Unit = println("Pool closed (after all services released)")
}

val poolResource = Resource.shared { scope =>
  println("Creating database pool (first allocation only)")
  new DatabasePool
}

// Allocate in ServiceA
Scope.global.scoped { scopeA =>
  import scopeA._
  val poolA: $[DatabasePool] = poolResource.allocate
  println("ServiceA allocated pool")

  // Allocate in ServiceB within a nested scope
  scopeA.scoped { scopeB =>
    import scopeB._
    val poolB: $[DatabasePool] = lower(poolA)
    println("ServiceB using same pool instance (reference count incremented)")
  }
  println("ServiceB released, but pool stays open for ServiceA")
}
println("All services released, pool closed")
```

### Unique Resources

**Unique resources create fresh instances each time**: every allocation produces a new value. Finalizers run when their owning scope closes, independent of other allocations. Use unique resources for **per-request state, isolated services, and stateful handlers** — anything that should never be shared because it encapsulates request-specific or scope-specific data.

A typical scenario is **per-request caches**: each API request gets its own cache instance to avoid one request polluting another's cached data. Similarly, stateful handlers (parser state machines, transaction contexts, event buffers) need isolation to prevent cross-contamination.

Here's what unique acquisition looks like:

```scala
object Resource {
  def unique[A](f: Scope => A): Resource[A]
}
```

When you allocate a unique resource, each allocation is independent:

```scala mdoc:compile-only
import zio.blocks.scope._

class RequestCache extends AutoCloseable {
  def close(): Unit = println("Request cache closed")
}

val cacheResource = Resource.unique { scope =>
  println("Creating new request cache")
  new RequestCache
}

// Two allocations in the same scope produce different instances
Scope.global.scoped { scope =>
  import scope._
  println("Creating first cache...")
  val cache1: $[RequestCache] = cacheResource.allocate

  println("Creating second cache...")
  val cache2: $[RequestCache] = cacheResource.allocate

  println("Both caches are independent instances")
}
```

### Diamond Dependency Pattern

A classic architecture uses shared resources to solve **diamond dependencies**, where multiple services depend on the same expensive component. Consider an e-commerce application where both `ProductService` and `OrderService` depend on `Logger`:

```
       CachingApp
        /      \
       /        \
  ProductService  OrderService
       \        /
        \      /
         Logger
```

Without shared resources, each service would receive a different `Logger` instance. With `Resource.shared`, both services automatically receive the same singleton instance:

```scala mdoc:compile-only
import zio.blocks.scope._

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(s"LOG: $msg")
  def close(): Unit = println("Logger closed")
}

class ProductService(val logger: Logger) {
  def findProduct(id: String): String = {
    logger.log(s"Finding product $id")
    s"Product-$id"
  }
}

class OrderService(val logger: Logger) {
  def createOrder(productId: String): String = {
    logger.log(s"Creating order for $productId")
    s"ORD-123"
  }
}

class CachingApp(val productService: ProductService, val orderService: OrderService) extends AutoCloseable {
  def close(): Unit = println("App closed")
}

Scope.global.scoped { scope =>
  import scope._

  // Use Wire.shared[Logger] to ensure only one instance is created
  val app: $[CachingApp] = allocate(
    Resource.from[CachingApp](
      Wire.shared[Logger]  // Both services get the same Logger instance
    )
  )

  $(app) { a =>
    println(s"ProductService and OrderService share Logger? ${a.productService.logger eq a.orderService.logger}")
    a.productService.findProduct("P001")
    a.orderService.createOrder("P001")
  }
}
```

In this pattern, `Resource.shared` (via `Wire.shared`) guarantees a single `Logger` instance across all services, eliminating duplication while maintaining proper cleanup semantics.

### Multiple Shared Dependencies

Most applications need more than one shared resource. Here's a realistic example where `CachingApp` depends on both a shared `Logger` and a shared `MetricsCollector`. Both resources are singletons that all services share:

```
          CachingApp
           /      \
          /        \
         /          \
    ProductService  OrderService
     /  \            /  \
    /    \          /    \
   Logger Metrics  Logger Metrics
    \    /          \    /
     \  /            \  /
      \/              \/
   (shared singleton instances)
```

Here's the implementation:

```scala mdoc:compile-only
import zio.blocks.scope._

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(s"[LOG] $msg")
  def close(): Unit = println("[Logger] Closed")
}

class MetricsCollector extends AutoCloseable {
  private var eventCount = 0
  def recordEvent(name: String): Unit = {
    eventCount += 1
    println(s"[METRICS] Event: $name (total: $eventCount)")
  }
  def close(): Unit = println(s"[MetricsCollector] Closed after $eventCount events")
}

class ProductService(val logger: Logger, val metrics: MetricsCollector) {
  def findProduct(id: String): String = {
    logger.log(s"Finding product $id")
    metrics.recordEvent("product.find")
    s"Product-$id"
  }
}

class OrderService(val logger: Logger, val metrics: MetricsCollector) {
  def createOrder(productId: String): String = {
    logger.log(s"Creating order for $productId")
    metrics.recordEvent("order.create")
    s"ORD-123"
  }
}

class CachingApp(
  val productService: ProductService,
  val orderService: OrderService
) extends AutoCloseable {
  def close(): Unit = println("[CachingApp] Closed")
}

Scope.global.scoped { scope =>
  import scope._

  // Both Logger and MetricsCollector are shared (singleton instances)
  val app: $[CachingApp] = allocate(
    Resource.from[CachingApp](
      Wire.shared[Logger],              // One Logger for all services
      Wire.shared[MetricsCollector]     // One MetricsCollector for all services
    )
  )

  $(app) { a =>
    println(s"ProductService and OrderService share Logger? ${a.productService.logger eq a.orderService.logger}")
    println(s"ProductService and OrderService share Metrics? ${a.productService.metrics eq a.orderService.metrics}")

    a.productService.findProduct("P001")
    a.orderService.createOrder("P001")
  }
}
```

When this scope closes, both the `Logger` and `MetricsCollector` are finalized exactly once, regardless of how many services reference them. The macro automatically builds a dependency graph, verifies all wires, and ensures LIFO cleanup order.

## Construction

Resources can be created in several ways: from values, from explicit acquire/release pairs, from `AutoCloseable` types, from custom functions, or derived automatically from a type's constructor using the `Resource.from` macros.

### `Resource.apply` — Wrap a Value

Wraps a by-name value as a resource. If the value implements `AutoCloseable`, its `close()` method is automatically registered as a finalizer:

```scala
object Resource {
  def apply[A](value: => A): Resource[A]
}
```

Here's how to use it:

```scala mdoc:compile-only
import zio.blocks.scope._

case class Config(debug: Boolean)

class Database(val name: String) extends AutoCloseable {
  def close(): Unit = println(s"Closing database $name")
}

val configResource = Resource(Config(debug = true))
val dbResource = Resource(new Database("mydb"))
```

### `Resource.acquireRelease` — Explicit Lifecycle

Creates a resource with separate acquire and release functions. The acquire thunk runs during allocation; the release function is registered as a finalizer:

```scala
object Resource {
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
}
```

Here's an example with file handling:

```scala mdoc:compile-only
import zio.blocks.scope._
import java.io.FileInputStream

val fileResource = Resource.acquireRelease {
  new FileInputStream("data.txt")
} { stream =>
  stream.close()
}
```

### `Resource.fromAutoCloseable` — Type-Safe Wrapping

Creates a resource specifically for `AutoCloseable` subtypes. This is a compile-time verified alternative to `Resource(value)` when you know the value is closeable:

```scala
object Resource {
  def fromAutoCloseable[A <: AutoCloseable](value: => A): Resource[A]
}
```

Here's an example with a stream:

```scala mdoc:compile-only
import zio.blocks.scope._
import java.io.BufferedInputStream
import java.io.FileInputStream

val streamResource = Resource.fromAutoCloseable {
  new BufferedInputStream(new FileInputStream("data.bin"))
}
```

### `Resource.shared` — Memoized with Reference Counting

Creates a shared resource that memoizes its value across multiple allocations using reference counting. The first allocation initializes the value using an `OpenScope` parented to `Scope.global`; subsequent allocations increment the reference count and return the same instance. Each scope that receives the shared value registers a finalizer that decrements the count. When the count reaches zero, the shared scope closes automatically. This mechanism is **thread-safe and lock-free**, implemented using `AtomicReference` with atomic compare-and-swap operations to avoid contention.

Under the hood, a shared resource progresses through four states: (1) **Uninitialized** — the resource has never been allocated and the first call triggers initialization; (2) **Pending** — initialization is in progress and other threads wait via spin-yield for the value to become available; (3) **Created** — the value is fully initialized and ready, each allocation increments the reference count, and each scope registers a finalizer to decrement it; (4) **Destroyed** — the reference count reached zero, the resource was cleaned up, and further allocations will fail. This state machine ensures that no matter how many threads try to allocate simultaneously, the underlying value initializes exactly once, and cleanup happens exactly once when the last reference is released.

Use shared resources for **expensive, singleton-like components** (database connection pools, thread pools, logging systems, caches) that should exist exactly once for the lifetime of the application, even when multiple services depend on them.

Here's the signature:

```scala
object Resource {
  def shared[A](f: Scope => A): Resource[A]
}
```

Here's a realistic example showing reference counting and automatic cleanup:

```scala mdoc:compile-only
import zio.blocks.scope._

class ExpensiveComponent extends AutoCloseable {
  println("ExpensiveComponent initialized (expensive operation)")
  def close(): Unit = println("ExpensiveComponent cleaned up (last reference released)")
}

val sharedResource = Resource.shared { scope =>
  new ExpensiveComponent()
}

// Multiple services allocating the same shared resource
Scope.global.scoped { scope =>
  import scope._

  // First allocation initializes the component
  println("ServiceA allocating...")
  val componentA: $[ExpensiveComponent] = sharedResource.allocate

  // ServiceB in a nested scope receives the same instance
  scope.scoped { innerScope =>
    import innerScope._
    println("ServiceB allocating (same instance, ref count += 1)...")
    val componentB: $[ExpensiveComponent] = sharedResource.allocate
    println("Both services have the same instance")
  }

  println("ServiceB released, but component stays alive (ref count -= 1)")
}

println("ServiceA released, component cleaned up (ref count == 0)")
```

### `Resource.unique` — Fresh Instances

Creates a unique resource that produces a fresh instance each time it's allocated. Use for per-request state or resources that should never be shared:

```scala
object Resource {
  def unique[A](f: Scope => A): Resource[A]
}
```

Here's an example showing fresh instances:

```scala mdoc:compile-only
import zio.blocks.scope._

var counter = 0

val uniqueResource = Resource.unique[Int] { _ =>
  counter += 1
  counter
}
```

### `Resource.from[T]` — Derive from Constructor

Derives a `Resource[T]` from `T`'s primary constructor, requiring no external dependencies.
If `T` extends `AutoCloseable`, `Resource.from` automatically registers its `close()` method
as a finalizer:

```scala
object Resource {
  def from[T]: Resource[T]
}
```

To derive a resource for a type that has no constructor dependencies:

```scala mdoc:compile-only
import zio.blocks.scope._

class MetricsCollector extends AutoCloseable {
  def record(event: String): Unit = println(s"Recording: $event")
  def close(): Unit               = println("MetricsCollector closed")
}

val metricsResource = Resource.from[MetricsCollector]
```

Internally, the macro inspects `T`'s constructor at compile time, verifies that no external
dependencies are needed, and synthesizes a `Resource.shared` that instantiates `T` and —
when `T` extends `AutoCloseable` — registers `close()` as a scope finalizer. Any attempt
to use `Resource.from[T]` on a type with unsatisfied constructor parameters results in a
compile-time error.

### `Resource.from[T](wires: Wire[?, ?]*)` — Derive with Dependency Overrides

Derives a `Resource[T]` from `T`'s constructor, with `Wire` values provided as dependency
overrides. Any dependency not covered by an explicit wire is auto-derived if the macro can
construct it; otherwise a compile-time error is produced:

```scala
object Resource {
  def from[T](wires: Wire[?, ?]*): Resource[T]
}
```

To derive a resource for a type whose dependencies are provided via wires:

```scala mdoc:compile-only
import zio.blocks.scope._

case class Config(host: String, port: Int)

class Logger(config: Config) extends AutoCloseable {
  def log(msg: String): Unit = println(s"[$msg] ${config.host}:${config.port}")
  def close(): Unit          = log("Logger shutting down")
}

class Service(logger: Logger) extends AutoCloseable {
  def run(): Unit  = logger.log("Service running")
  def close(): Unit = logger.log("Service shutting down")
}

val serviceResource = Resource.from[Service](
  Wire(Config("localhost", 8080))
)
```

Internally, the macro builds a complete dependency graph at compile time: it inspects each
provided wire's input and output types, identifies all constructor parameters of `T`, and
auto-derives wires for any remaining dependencies. It then topologically sorts all wires to
determine acquisition order and composes them into a single `Resource` whose finalizers run
in LIFO order — inner dependencies close before outer ones.

## Core Operations

Resources support transformation and composition through `map`, `flatMap`, and `zip`.

### `Resource#map` — Transform the Value

Transforms the value produced by a resource without affecting finalization. The transformation function is applied after the resource is acquired:

```scala
trait Resource[+A] {
  def map[B](f: A => B): Resource[B]
}
```

Here's a usage example:

```scala mdoc:compile-only
import zio.blocks.scope._

val portResource = Resource(8080)
val urlResource = portResource.map(port => s"http://localhost:$port")
```

### `Resource#flatMap` — Sequence Resources

Sequences two resources, using the result of the first to create the second. Both sets of finalizers are registered and run in LIFO order (inner before outer):

```scala
trait Resource[+A] {
  def flatMap[B](f: A => Resource[B]): Resource[B]
}
```

Here's an example with dependent resources:

```scala mdoc:compile-only
import zio.blocks.scope._

case class DbConfig(url: String)

class Database(config: DbConfig) extends AutoCloseable {
  def query(sql: String): String = s"Result from ${config.url}: $sql"
  def close(): Unit = println("Database closed")
}

val configResource = Resource(DbConfig("jdbc:postgres://localhost"))
val dbResource = configResource.flatMap { config =>
  Resource.fromAutoCloseable(new Database(config))
}
```

### `Resource#zip` — Combine Resources

Combines two resources into a single resource that produces a tuple of both values. Both resources are acquired and both sets of finalizers are registered:

```scala
trait Resource[+A] {
  def zip[B](that: Resource[B]): Resource[(A, B)]
}
```

Here's an example combining multiple resources:

```scala mdoc:compile-only
import zio.blocks.scope._

case class DbConfig(url: String)

class Database(config: DbConfig) extends AutoCloseable {
  def close(): Unit = println("Database closed")
}

class Cache extends AutoCloseable {
  def close(): Unit = println("Cache closed")
}

val dbResource = Resource.fromAutoCloseable(new Database(DbConfig("jdbc:postgres://localhost")))
val cacheResource = Resource.fromAutoCloseable(new Cache())
val combined = dbResource.zip(cacheResource)
```

### `Resource#allocate` — Acquire Within a Scope

Allocates a `Resource[A]` within the current `Scope`, returning a scoped `$[A]`. This is syntax sugar for `scope.allocate(resource)`, available via `import scope._` inside a `scoped` block:

```scala
implicit class ResourceOps[A](private val r: Resource[A]) {
  def allocate: $[A]
}
```

To allocate a resource and use its value inside a scope:

```scala mdoc:compile-only
import zio.blocks.scope._

class Database extends AutoCloseable {
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit              = println("Database closed")
}

Scope.global.scoped { implicit scope =>
  import scope._
  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate
  $(db)(_.query("SELECT 1"))
}
```

### `$[Resource[A]]#allocate` — Allocate a Scoped Resource

Allocates a `$[Resource[A]]` — a Resource that is itself a scoped value — returning `$[A]`. Use this when a method on a scoped object returns a Resource and you need to immediately acquire it while keeping the result scoped.

**What is a scoped resource?** A scoped value `$[A]` represents an `A` that is valid only while a scope is alive. A **scoped resource** `$[Resource[A]]` is a Resource object that exists *inside* that scope. When you call a method like `$(pool)(_.lease())` that returns a Resource, the result is typed as `$[Resource[Connection]]` — the Resource itself is scoped. The `.allocate` extension method unwraps this scoped resource and acquires it, returning the acquired value as a new scoped value `$[A]`.

**Motivation:** This pattern appears frequently in resource factories. A scoped object (like a database pool) has methods that produce new Resources. Without this extension, you'd need to unwrap the `$[Resource[A]]` from the `$` context, allocate it separately, and re-wrap the result. The `.allocate` method chains naturally, letting you write `$(pool)(_.lease()).allocate` instead of dealing with intermediate unwrapping.

The implicit class:

```scala
implicit class ScopedResourceOps[A](private val sr: $[Resource[A]]) {
  def allocate: $[A]
}
```

Here's a realistic example where a database pool (a scoped object) has a method that returns a Resource for individual connections:

```scala mdoc:compile-only
import zio.blocks.scope._

class Connection extends AutoCloseable {
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit              = println("Connection closed")
}

class Pool extends AutoCloseable {
  def lease(): Resource[Connection] = Resource.fromAutoCloseable(new Connection)
  def close(): Unit                 = println("Pool closed")
}

Scope.global.scoped { implicit scope =>
  import scope._
  val pool: $[Pool]           = Resource.fromAutoCloseable(new Pool).allocate
  val conn: $[Connection]     = $(pool)(_.lease()).allocate
  $(conn)(_.query("SELECT 1"))
}
```


## Integration

Resource is a core abstraction in ZIO Blocks' resource management ecosystem:

- **[`Scope`](./scope.md)** — Resources require a `Scope` for allocation. The `Scope` manages the lifetime of acquired resources and automatically runs finalizers when the scope closes.
- **[`Wire`](./wire.md)** — The `Resource.from[T](wires)` macro builds dependency graphs using `Wire` values, enabling constructor-based dependency injection with automatic resource management.
- **[`Finalizer`](./finalizer.md)** — Resources register their cleanup logic with `Finalizer` objects, which execute in LIFO order when scopes close.

For example, `Resource.from[T]` uses `Wire` to construct instances with their dependencies, automatically registering any `AutoCloseable` cleanup:

```scala
val serviceResource = Resource.from[Service](
  Wire(Config("localhost", 8080))
)
```

This builds a complete dependency graph: `Config` → `Logger` → `Service`, with all finalizers managed by the containing `Scope`.

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Lifecycle Management with Temporary Files

This example demonstrates creating and automatically cleaning up temporary files using Resource's lifecycle management. It shows how Resource ensures files are closed even if exceptions occur:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.TempFileHandlingExample"
```

### Acquiring and Releasing Database Connections

This example demonstrates the acquire-release pattern using Resource to manage database connections. It shows proper connection initialization and guaranteed cleanup:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.DatabaseConnectionExample"
```

### Shared Resources with Memoization and Reference Counting

This example demonstrates Resource.shared to create a singleton logger instance that is automatically cleaned up only when the last service releases it. Shows reference counting in action:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.CachingSharedLoggerExample"
```

### Managing Shared Expensive Resources

This example demonstrates using Resource.shared for a database connection pool—an expensive resource that should exist exactly once. Shows how multiple services safely share the same pool instance with automatic cleanup:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.ConnectionPoolExample"
```

### Transactional Resource Management

This example demonstrates combining Resource with transaction boundaries. Shows how to manage resources (connections, transactions) that must be coordinated across scopes with proper rollback on failure:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.TransactionBoundaryExample"
```

### Multi-Layer Service Construction

This example demonstrates Resource.from macro to automatically build a complex dependency graph with multiple services. Shows automatic wiring of constructor dependencies and cleanup in correct LIFO order:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.LayeredWebServiceExample"
```
