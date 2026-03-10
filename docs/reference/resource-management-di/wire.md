---
id: wire
title: "Wire"
---

`Wire[-In, +Out]` is a **compile-time safe recipe for constructing a service and its dependencies**. Wires describe how to construct an `Out` value given access to its dependencies via a `Context[In]` and a `Scope` for finalization.

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def isUnique: Boolean = !isShared

  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]

  def toResource(deps: Context[In]): Resource[Out]
}
```

The type parameters are:
- **`In` (contravariant)**: the dependencies required to construct the service
- **`Out` (covariant)**: the service produced

Wires are the building blocks of dependency injection in Scope. They form the foundation for constructor-based dependency injection via `Resource.from[T](wires*)`.

## Overview

A `Wire` is a **lazy recipe**, not an execution. It holds a construction function `(Scope, Context[In]) => Out` and a **sharing strategy** — either `Shared` (reference-counted via `Resource.shared`) or `Unique` (fresh instance per allocation). The Wire itself does nothing until you convert it to a `Resource` and allocate it within a scope.

Here's the typical flow:

```
Wire (recipe)
  ↓
Resource (lazy, composable)
  ↓
scope.allocate(...)
  ↓
$[T] (scoped value in scope)
```

## Motivation

Without Wires, building a multi-layer application requires manual dependency passing:

```scala
final case class Config(dbUrl: String)

final class Database(config: Config) extends AutoCloseable {
  def close(): Unit = println(s"closing connection to ${config.dbUrl}")
}

final class UserService(db: Database) {
  def getUser(id: Int): String = s"user $id"
}

final class App(service: UserService) {
  def run(): Unit = println(service.getUser(1))
}

// Manual wiring:
Scope.global.scoped { scope =>
  import scope.*
  val config = Config("jdbc:postgres://localhost/db")
  val db = Resource.fromAutoCloseable(new Database(config)).allocate
  val service = new UserService($(db)(identity))
  val app = new App(service)
  app.run()
}
```

With `Wire` + `Resource.from`, the macro handles the dependency graph:

```scala
Scope.global.scoped { scope =>
  import scope.*
  val app = Resource.from[App](
    Wire(Config("jdbc:postgres://localhost/db"))
  ).allocate
  $(app)(_.run())
}
```

**Benefits:**
- **Compile-time graph validation** — cycle detection, duplicate providers, missing dependencies
- **Automatic finalization** — `AutoCloseable` resources are finalized in LIFO order
- **Sharing control** — choose which services are singletons (shared) vs fresh per allocation (unique)
- **Type-safe construction** — no stringly-typed dependency resolution

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "<version>"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-scope" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x.

## Construction

### Wire.shared[T] — derive a shared wire

The `Wire.shared[T]` macro inspects `T`'s primary constructor and generates a shared wire that reuses the same instance across dependents.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(debug: Boolean)

final class Database(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"[db] $sql"
  def close(): Unit = println("database closed")
}

val wire: Wire.Shared[Config, Database] = Wire.shared[Database]

Scope.global.scoped { scope =>
  import scope.*
  val config = Config(debug = true)
  val deps = Context[Config](config)
  val db = allocate(wire.toResource(deps))
  $(db)(_.query("SELECT 1"))
}
```

### Wire.unique[T] — derive a unique wire

Like `shared[T]`, but creates a fresh instance each time the wire is used. Use for request-scoped or per-call services.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final class RequestHandler {
  val id = scala.util.Random.nextInt()
}

val wire: Wire.Unique[Any, RequestHandler] = Wire.unique[RequestHandler]

Scope.global.scoped { scope =>
  import scope.*
  val deps = Context.empty[Any]
  val resource = wire.toResource(deps)

  val h1 = allocate(resource)
  val h2 = allocate(resource)

  val ids: (Int, Int) = (
    $(h1)(_.id),
    $(h2)(_.id)
  )
  // ids._1 != ids._2 (different instances)
}
```

### Wire.apply[T] — lift a pre-existing value

Creates a shared wire that injects a value you already have. If the value is `AutoCloseable`, its `close()` method is automatically registered as a finalizer.

```scala
import zio.blocks.scope._

final case class Config(dbUrl: String)

val config = Config("jdbc:postgres://localhost/db")
val wire: Wire.Shared[Any, Config] = Wire(config)

Scope.global.scoped { scope =>
  import scope.*
  val cfg = allocate(wire.toResource(Context.empty[Any]))
  $(cfg)(_.dbUrl)
}
```

### Wire.Shared.fromFunction — manual shared wire construction

Use this for custom construction logic when macro derivation doesn't fit.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(timeout: Int)

final class Client(config: Config) {
  def call(): String = s"calling with timeout=${config.timeout}"
}

val wire: Wire.Shared[Config, Client] =
  Wire.Shared.fromFunction { (scope, ctx) =>
    val config = ctx.get[Config]
    new Client(config)
  }

Scope.global.scoped { scope =>
  import scope.*
  val config = Config(30)
  val deps = Context[Config](config)
  val client = allocate(wire.toResource(deps))
  $(client)(_.call())
}
```

### Wire.Unique.fromFunction — manual unique wire construction

Like `fromFunction`, but for unique wires.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final class RequestContext {
  val id = scala.util.Random.nextInt()
}

val wire: Wire.Unique[Any, RequestContext] =
  Wire.Unique.fromFunction { (_, _) =>
    new RequestContext
  }

Scope.global.scoped { scope =>
  import scope.*
  val deps = Context.empty[Any]
  val resource = wire.toResource(deps)

  val r1 = allocate(resource)
  val r2 = allocate(resource)

  val different: Boolean = $(r1)(_.id) != $(r2)(_.id)
  // different == true
}
```

## Shared vs Unique

The fundamental difference is **reuse semantics**:

| Aspect | Shared | Unique |
|--------|--------|--------|
| **Construction** | `Wire.shared[T]` macro or `Wire.Shared.fromFunction` | `Wire.unique[T]` macro or `Wire.Unique.fromFunction` |
| **Resource type** | `Resource.shared[T]` (reference-counted) | `Resource.unique[T]` (fresh per call) |
| **When to use** | Singletons, expensive resources (connections, thread pools) | Request scoped, stateful per-call (request handlers) |
| **Instance reuse** | Same instance across entire dependency graph | New instance per allocation |
| **Finalization** | Runs when last referencing scope closes | Runs when each scope closes |

In the diamond pattern (where `App` depends on both `UserService` and `OrderService`, both of which depend on `Database`), a shared wire ensures `Database` is constructed once and both services receive the same instance.

```scala
import zio.blocks.scope._

final class Database {
  val id = scala.util.Random.nextInt()
}

final class UserService(db: Database) {
  def getDbId(): Int = db.id
}

final class OrderService(db: Database) {
  def getDbId(): Int = db.id
}

final class App(userService: UserService, orderService: OrderService) {
  def check(): Boolean = {
    // With shared Database, these should be equal
    userService.getDbId() == orderService.getDbId()
  }
}

// Using shared wires for all dependencies
val resource = Resource.from[App](
  Wire.shared[Database],
  Wire.shared[UserService],
  Wire.shared[OrderService]
)

Scope.global.scoped { scope =>
  import scope.*
  val app = resource.allocate
  $(app)(_.check())  // true: Database is shared
}
```

## Core Operations

### `Wire#isShared` and `Wire#isUnique`

Check the sharing strategy of a wire:

```scala
import zio.blocks.scope._

val sharedWire = Wire.shared[String]
val uniqueWire = Wire.unique[String]

println(s"sharedWire.isShared: ${sharedWire.isShared}")      // true
println(s"sharedWire.isUnique: ${sharedWire.isUnique}")      // false
println(s"uniqueWire.isShared: ${uniqueWire.isShared}")      // false
println(s"uniqueWire.isUnique: ${uniqueWire.isUnique}")      // true
```

### `Wire#shared` and `Wire#unique` — convert between strategies

Convert a wire to the opposite strategy:

```scala
import zio.blocks.scope._

val original = Wire.shared[String]

val converted: Wire.Unique[Any, String] = original.unique
println(s"original.isShared: ${original.isShared}")          // true
println(s"converted.isUnique: ${converted.isUnique}")        // true

// Converting back returns a new shared wire
val backToShared: Wire.Shared[Any, String] = converted.shared
println(s"backToShared.isShared: ${backToShared.isShared}")  // true
```

Calling `shared` on an already-shared wire returns `this` (identity); likewise `unique` on a unique wire returns `this`.

### `Wire#toResource` — convert a wire to a Resource

Converts the wire to a lazy `Resource` by providing the dependency context:

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final case class Config(value: String)

val wire = Wire.shared[Config]
val deps = Context[Config](Config("hello"))

val resource: Resource[Config] = wire.toResource(deps)

Scope.global.scoped { scope =>
  import scope.*
  val cfg = allocate(resource)
  $(cfg)(_.value)
}
```

### `Wire#make` — construct directly from a wire

Directly construct a value without going through `Resource.toResource`. This is a low-level operation; prefer `allocate(wire.toResource(...))` for safety.

```scala
import zio.blocks.scope._
import zio.blocks.context.Context

final class Service {
  def getName: String = "service"
}

val wire = Wire.shared[Service]

Scope.global.scoped { scope =>
  import scope.*
  val service = wire.asInstanceOf[Wire.Shared[Any, Service]].make(scope, Context.empty[Any])
  println(service.getName)
}
```

## Macro Derivation

When you call `Wire.shared[T]` or `Wire.unique[T]`, the macro performs these checks:

1. **Is `T` a class?** — traits and abstract classes are rejected; only concrete classes can be auto-wired.
2. **Does `T` have a primary constructor?** — the macro inspects constructor parameters to determine dependencies.
3. **Is each parameter either a dependency type or a special injected type?** — parameters of type `Scope` or `Finalizer` are recognized and injected automatically; others are looked up in the context.
4. **Does `T` extend `AutoCloseable`?** — if yes, `close()` is automatically registered as a finalizer in the scope.

Example with all three features:

```scala
import zio.blocks.scope._

final case class Config(dbUrl: String)

final class Logger(using Finalizer) {
  def log(msg: String): Unit = println(msg)
}

final class Database(config: Config)(using scope: Scope) extends AutoCloseable {
  def connect(): Unit = {
    scope.defer(println("database connection closed"))
    println(s"connecting to ${config.dbUrl}")
  }

  def query(sql: String): String = s"result: $sql"

  def close(): Unit = println("database closed")
}

final class Service(db: Database, logger: Logger) {
  def run(): Unit = {
    logger.log(db.query("SELECT 1"))
  }
}

// Macro handles Finalizer injection, Scope injection, and AutoCloseable registration
val wire = Wire.shared[Service]
```

### What happens with subtype conflicts

If a constructor has dependencies of related types (e.g., both `FileInputStream` and `InputStream`), the macro rejects the wire because `Context` is type-indexed and cannot reliably disambiguate.

```scala
// This will NOT compile
final class App(input: InputStream, fileInput: FileInputStream)
val wire = Wire.shared[App]  // error: subtype conflict

// Fix: wrap one type to make it distinct
final case class FileInputWrapper(value: FileInputStream)
final class App(input: InputStream, fileInput: FileInputWrapper)
val wire = Wire.shared[App]  // ok
```

## Integration with Resource.from

`Wire` is designed for use with `Resource.from[T](wires*)`, which performs whole-graph dependency injection:

```scala
import zio.blocks.scope._

final case class AppConfig(dbUrl: String)

final class Database(config: AppConfig) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = ()
}

final class Repository(db: Database) {
  def query(): String = db.query("SELECT *")
}

final class Service(repo: Repository) extends AutoCloseable {
  def run(): String = repo.query()
  def close(): Unit = ()
}

final class App(service: Service) {
  def run(): String = service.run()
}

// Provide only the leaf dependency; Resource.from derives the rest
val appResource: Resource[App] = Resource.from[App](
  Wire(AppConfig("jdbc:postgres://localhost/db"))
)

Scope.global.scoped { scope =>
  import scope._
  val app = allocate(appResource)
  $(app)(_.run())
}
```

When `Resource.from` composes wires, it respects the sharing strategy:
- **Shared wires** → reference-counted (single instance in the graph)
- **Unique wires** → fresh per allocation

The macro detects cycles, duplicate providers, and missing dependencies at compile time.

## Comparison with Alternatives

| Feature | Wire | Manual Passing | Service Locator |
|---------|------|----------------|-----------------|
| **Type safety** | ✓ (compile-time validation) | ✓ (implicit) | ✗ (string keys) |
| **Cycle detection** | ✓ (compile time) | ✗ | ✗ (runtime) |
| **Sharing semantics** | ✓ (configurable) | Manual | ✓ (singleton pattern) |
| **Finalization** | ✓ (LIFO, automatic) | Manual | Manual |
| **Performance** | ~0 overhead (macro-generated) | ~0 overhead | ~1 allocation overhead |

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Basic wire construction: deriving shared wires, lifting values, and converting to resources**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/wire/WireBasicExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireBasicExample.scala))

```bash
sbt "scope-examples/runMain wire.WireBasicExample"
```

**Comparing shared vs unique semantics: shared wires reuse the same instance across dependents, while unique wires create fresh instances**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/wire/WireSharedUniqueExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireSharedUniqueExample.scala))

```bash
sbt "scope-examples/runMain wire.WireSharedUniqueExample"
```

**Manual wire construction: using fromFunction for custom construction logic**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/wire/WireFromFunctionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireFromFunctionExample.scala))

```bash
sbt "scope-examples/runMain wire.WireFromFunctionExample"
```
