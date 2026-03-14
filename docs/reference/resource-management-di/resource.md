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

## Construction

Resources can be created in several ways: from values, from explicit acquire/release pairs, from `AutoCloseable` types, from custom functions, or derived automatically from a type's constructor using the `Resource.from` macros.

### `Resource.apply` — wrap a value

Wraps a by-name value as a resource. If the value implements `AutoCloseable`, its `close()` method is automatically registered as a finalizer.

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

### `Resource.acquireRelease` — explicit lifecycle

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

### `Resource.fromAutoCloseable` — type-safe wrapping

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

### `Resource.shared` — memoized with reference counting

Creates a shared resource that memoizes its value across multiple allocations. The first call initializes the value; subsequent calls return the same instance with reference counting. Finalizers run only when the last reference is released:

```scala
object Resource {
  def shared[A](f: Scope => A): Resource[A]
}
```

Here's an example showing memoization:

```scala mdoc:compile-only
import zio.blocks.scope._

var initCount = 0

val sharedResource = Resource.shared[Int] { _ =>
  initCount += 1
  initCount
}
```

### `Resource.unique` — fresh instances

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

### `Resource.from[T]` — derive from constructor

Derives a `Resource[T]` from `T`'s primary constructor, requiring no external dependencies.
If `T` extends `AutoCloseable`, `Resource.from` automatically registers its `close()` method
as a finalizer.

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

### `Resource.from[T](wires: Wire[?, ?]*)` — derive with dependency overrides

Derives a `Resource[T]` from `T`'s constructor, with `Wire` values provided as dependency
overrides. Any dependency not covered by an explicit wire is auto-derived if the macro can
construct it; otherwise a compile-time error is produced.

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

### `Resource#map` — transform the value

Transforms the value produced by a resource without affecting finalization. The transformation function is applied after the resource is acquired.

```scala mdoc:compile-only
import zio.blocks.scope._

val portResource = Resource(8080)
val urlResource = portResource.map(port => s"http://localhost:$port")
```

### `Resource#flatMap` — sequence resources

Sequences two resources, using the result of the first to create the second. Both sets of finalizers are registered and run in LIFO order (inner before outer):

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

### `Resource#zip` — combine resources

Combines two resources into a single resource that produces a tuple of both values. Both resources are acquired and both sets of finalizers are registered:

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

### `Resource#allocate` — acquire within a scope

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

### `$[Resource[A]]#allocate` — allocate a scoped resource

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

## Shared vs. Unique

The fundamental difference is **reuse semantics**:

| Aspect             | Shared                                             | Unique                                    |
|--------------------|----------------------------------------------------|-------------------------------------------|
| **Creation**       | `Resource.shared(f)`                               | `Resource.unique(f)` or `Resource(value)` |
| **Memoization**    | Yes, with reference counting                       | No, fresh per allocation                  |
| **When to use**    | Expensive resources (DB connections, thread pools) | Per-request state, stateful handlers      |
| **Instance reuse** | Same instance across nested scopes                 | New instance per allocation               |
| **Finalization**   | Runs when last reference released                  | Runs when scope closes                    |

In a diamond dependency pattern (where `AppService` depends on both `UserService` and `OrderService`, both depending on `Database`), using `Resource.shared[Database]` ensures both services receive the same instance.

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Basic lifecycle management with temporary files**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.TempFileHandlingExample"
```

**Acquiring and releasing database connections**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.DatabaseConnectionExample"
```

**Shared resources with memoization and reference counting**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.CachingSharedLoggerExample"
```

**Managing shared expensive resources**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.ConnectionPoolExample"
```

**Transactional resource management**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.TransactionBoundaryExample"
```

**Multi-layer service construction**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.LayeredWebServiceExample"
```
