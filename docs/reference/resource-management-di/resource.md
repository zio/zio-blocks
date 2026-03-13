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

Resources can be created in several ways: from values, from explicit acquire/release pairs, from `AutoCloseable` types, or from custom functions.

### `Resource.apply` — wrap a value

Wraps a by-name value as a resource. If the value implements `AutoCloseable`, its `close()` method is automatically registered as a finalizer.

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

```scala mdoc:compile-only
import zio.blocks.scope._

var counter = 0

val uniqueResource = Resource.unique[Int] { _ =>
  counter += 1
  counter
}
```

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

## Shared vs. Unique

The fundamental difference is **reuse semantics**:

| Aspect            | Shared                            | Unique                                       |
|-------------------|-----------------------------------|----------------------------------------------|
| **Creation**      | `Resource.shared(f)`              | `Resource.unique(f)` or `Resource(value)`    |
| **Memoization**   | Yes, with reference counting      | No, fresh per allocation                     |
| **When to use**   | Expensive resources (DB connections, thread pools) | Per-request state, stateful handlers |
| **Instance reuse** | Same instance across nested scopes | New instance per allocation                 |
| **Finalization**  | Runs when last reference released | Runs when scope closes                       |

In a diamond dependency pattern (where `AppService` depends on both `UserService` and `OrderService`, both depending on `Database`), using `Resource.shared[Database]` ensures both services receive the same instance.

## Advanced Usage

Deriving Resources with `Resource.from` macros eliminates boilerplate by automatically constructing resources from type signatures at compile time.

### `Resource.from[T]` — automatic derivation with no dependencies

This macro inspects your type's constructor and automatically synthesizes a `Resource[T]`. It works best for types with no external dependencies — types whose constructor has no parameters, or only parameters of type `Scope` or `Finalizer` (which the macro provides automatically).

When you need a Resource for a simple type without external dependencies:

```scala mdoc:compile-only
import zio.blocks.scope._

class SimpleService {
  def run(): String = "service initialized"
}

// The macro derives Resource[SimpleService] automatically
val serviceResource = Resource.from[SimpleService]
```

The macro's internal process:
1. Analyzes the target type's primary constructor at compile time
2. Verifies that all constructor parameters either are satisfied by Scope/Finalizer injection or are absent
3. Creates a `Resource.shared` that instantiates your type using the constructor
4. If your type extends `AutoCloseable`, automatically registers its `close()` method as a finalizer

### `Resource.from[T](wires: Wire[?, ?]*)` — derivation with dependency wires

This macro accepts explicit `Wire` overrides that provide constructor dependencies for your type. It builds a complete dependency graph by analyzing both the wires you provide and the type's constructor, auto-deriving any missing dependencies it can satisfy.

When you need a Resource for a complex type with multiple dependencies that you want to configure:

```scala mdoc:compile-only
import zio.blocks.scope._

case class Config(host: String, port: Int)

class Logger(config: Config) extends AutoCloseable {
  def log(msg: String): Unit = println(s"[$msg] from ${config.host}:${config.port}")
  def close(): Unit = log("Logger shutting down")
}

class Service(logger: Logger) extends AutoCloseable {
  def run(): Unit = logger.log("Service running")
  def close(): Unit = logger.log("Service shutting down")
}

// Provide wires for Config; the macro derives Logger and Service automatically
val serviceResource = Resource.from[Service](
  Wire(Config("localhost", 8080))
)
```

The macro's internal process:
1. Parses each provided wire to extract its input and output types
2. Inspects T's constructor and identifies all required dependencies
3. For each dependency: uses the provided wire if available, or auto-derives a new wire if possible
4. Topologically sorts all wires (provided and auto-derived) to determine correct acquisition order
5. Composes all wires into a single Resource using `flatMap` chains for unique resources or nested acquisitions for shared resources
6. Ensures finalizers run in LIFO order (inner dependencies close before outer ones)

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
