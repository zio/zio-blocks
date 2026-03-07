---
id: scope-tutorial
title: "Scope Tutorial"
---

# Scope: A Newcomer's Guide to Compile-Time Resource Safety

Welcome to ZIO Blocks Scope—a library that makes resource management safe, composable, and verifiable at compile time. If you've ever struggled with `try/finally` chains, wondered when to close a database connection, or worried about resources outliving their owners, this guide is for you.

This tutorial introduces Scope step-by-step, starting with the problems it solves and progressing through practical examples. Each section builds on the last, so we recommend reading from top to bottom.

## Section 1 — The Problem: Why Resource Management Is Hard

Managing resources safely is deceptively difficult. When you open a database connection, read a file, or create a network socket, you must eventually close it—but only once, and only after you're done using it. Forget to close it, and you leak a resource. Close it too early, and you get a crash. Close it twice, and you get an error.

Nested resources make this worse. If you're reading a config file and then opening a database connection, you need nested `try/finally` blocks. If the inner resource throws an exception, the outer one may not close. Callbacks and closures can capture resources that outlive their intended scope, creating subtle use-after-free bugs.

Consider a typical `try/finally` pattern in Scala:

```scala
try {
  val connection = openConnection()
  try {
    val statement = connection.createStatement()
    try {
      val result = statement.executeQuery(sql)
      process(result)
    } finally {
      statement.close()
    }
  } finally {
    connection.close()
  }
} catch {
  case e: Exception => handleError(e)
}
```

This is hard to read, easy to get wrong, and doesn't compose—every additional resource adds another level of nesting. If an exception happens during cleanup, subsequent finalizers may not run. And if you pass a resource to another function, there's no compile-time guarantee that function won't use it after your scope ends.

Scope eliminates these problems by making resource ownership explicit and enforcing it at compile time.

## Section 2 — Your First Scope

Let's start with the simplest possible example: allocating a single resource, using it, and letting it close automatically.

Scope builds on the concept of `Scope.global`—the root scope that outlives your entire program. You enter a scoped region using `scoped { }`, and inside that block, you can allocate resources. When the block exits, all resources close in reverse order (last allocated, first closed).

Here's a database connection that prints messages when opening and closing:

```scala
import zio.blocks.scope.*

class Database extends AutoCloseable {
  def connect(): Unit = println("Database: connecting")
  def query(sql: String): String = s"Results of: $sql"
  override def close(): Unit = println("Database: closing")
}

Scope.global.scoped { scope =>
  import scope.*
  val db: $[Database] = allocate(Resource {
    val database = new Database()
    database.connect()
    database
  })

  $(db) { database =>
    val result = database.query("SELECT * FROM users")
    println(s"Query result: $result")
  }
}
```

Let's break down what happens:

- `Scope.global.scoped { scope => ... }` — Creates a scoped region. When the block exits, all allocated resources are closed.
- `import scope.*` — Imports scope operations: `$`, `allocate`, and `defer`.
- `allocate(Resource { ... })` — Allocates a resource. Since `Database` extends `AutoCloseable`, its `close()` method is automatically registered as a finalizer.
- `$[Database]` — A scoped value of type `Database`. It can only be used within the scope where it was allocated.
- `$(db) { database => ... }` — Unwraps the scoped value and passes it to the block. This is the only way to access a resource.

When the scope exits, `database.close()` runs automatically, printing `"Database: closing"`.

With multiple resources, finalizers run in LIFO order (last allocated, first closed):

```scala
import zio.blocks.scope.*

class Connection extends AutoCloseable {
  def name: String = this.getClass.getSimpleName
  override def close(): Unit = println(s"$name: closing")
}

Scope.global.scoped { scope =>
  import scope.*

  val conn1 = allocate(Resource(new Connection() { override def name = "Connection-1" }))
  val conn2 = allocate(Resource(new Connection() { override def name = "Connection-2" }))
  val conn3 = allocate(Resource(new Connection() { override def name = "Connection-3" }))

  println("All connections allocated")
  println("Exiting scope - connections will close in reverse order (3, 2, 1)")
}
```

★ Insight ─────────────────────────────────────
The `$[A]` type is central to Scope's compile-time safety: each scope instance has a unique `$` type that cannot be mixed with other scopes. Resources allocated in one scope literally cannot be used in another, even at the type level. This prevents entire classes of resource-lifetime bugs at compile time.
─────────────────────────────────────────────────

## Section 3 — The `$[A]` Type and the `$` Operator

To understand Scope, you need to understand `$[A]`. It is a marker type that says "this value is owned by a specific scope." At runtime, `$[A]` is erased to `A` (zero overhead), but at compile time, it enforces a fundamental rule: **you can only use a resource via the operator defined by the scope it belongs to**.

Every scope instance has its own unique `$` type. Two different scopes have incompatible `$` types, so you cannot accidentally mix them at the type level:

```scala
import zio.blocks.scope.*

class Database extends AutoCloseable {
  override def close(): Unit = ()
}

// This code illustrates the type incompatibility; it does not compile:
//
// Scope.global.scoped { outer =>
//   outer.allocate(Resource(new Database()))
//   outer.scoped { inner =>
//     // Trying to use outer.$[Database] in inner scope:
//     // val db: outer.$[Database] = ???
//     // $(db) { ... }  // ERROR: outer.$ incompatible with inner.$
//   }
// }
```

To use a resource, apply the `$(value)` operator (it's a macro) with a single-argument block. The parameter must be used as the receiver of all operations:

```scala
import zio.blocks.scope.*

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(msg)
  override def close(): Unit = ()
}

Scope.global.scoped { scope =>
  import scope.*
  val logger = allocate(Resource(new Logger()))

  // Correct: parameter used as receiver
  $(logger) { log =>
    log.log("Message 1")
    log.log("Message 2")
  }

  // If you try these patterns, they won't compile:
  // $(logger) { log => logger.log("X") }     // ERROR: can't use logger outside $()
  // $(logger) { log => val x = log; x }      // ERROR: result not Unscoped
  // $(logger) { log => (log, "data") }       // ERROR: result not Unscoped
}
```

The `$` operator automatically unwraps the result if it is an `Unscoped[B]` type. We'll cover `Unscoped` in detail in Section 5, but for now, know that primitives like `Int`, `String`, and `Unit` are always `Unscoped`:

```scala
import zio.blocks.scope.*

class Calculator extends AutoCloseable {
  def add(a: Int, b: Int): Int = a + b
  override def close(): Unit = ()
}

Scope.global.scoped { scope =>
  import scope.*
  val calc = allocate(Resource(new Calculator()))

  // Result is Int, which is Unscoped, so it unwraps automatically
  val sum = $(calc)(_.add(3, 4))
  assert(sum == 7)
}
```

★ Insight ─────────────────────────────────────
The `$` operator is not a regular method—it's a compile-time macro that inspects what you do with its parameter. This macro enforcement is what makes the rule "parameter must be receiver" checkable at compile time, not at runtime.
─────────────────────────────────────────────────

## Section 4 — Resources: Describing Acquisition and Cleanup

A `Resource[A]` is a lazy description of how to acquire a value and register any cleanup needed when the scope closes. Creating a resource does not acquire it—that only happens when you pass it to `scope.allocate()`.

There are several ways to construct a `Resource`:

**`Resource(value: => A)`** — The simplest form. Wraps a by-name value. If the value is `AutoCloseable`, its `close()` method is automatically registered:

```scala
import zio.blocks.scope.*

class Connection extends AutoCloseable {
  override def close(): Unit = println("Connection closed")
}

Scope.global.scoped { scope =>
  import scope.*

  // By-name: creates connection on demand, closes automatically
  val conn = allocate(Resource(new Connection()))

  $(conn) { c =>
    println("Using connection")
  }
}
```

**`Resource.acquireRelease(acquire)(release)`** — Explicit lifecycle control. Useful when cleanup is not a simple method call:

```scala
import zio.blocks.scope.*

case class Connection(id: Int) {
  def query(sql: String): String = s"[$id] $sql"
}

Scope.global.scoped { scope =>
  import scope.*

  // Explicit acquire and release
  val conn = allocate(Resource.acquireRelease {
    println("Opening connection...")
    Connection(42)
  } { c =>
    println(s"Closing connection $c")
  })

  $(conn) { c =>
    println(c.query("SELECT 1"))
  }
}
```

**`Resource.fromAutoCloseable(thunk)`** — Explicit wrapper for `AutoCloseable` subtypes. Type-safe and clear:

```scala
import zio.blocks.scope.*
import java.io._

Scope.global.scoped { scope =>
  import scope.*

  val file = allocate(Resource.fromAutoCloseable(new FileInputStream("/etc/hostname")))

  $(file) { f =>
    val bytes = Array.ofDim[Byte](100)
    val n = f.read(bytes)
    println(s"Read $n bytes")
  }
}
```

Resources compose: you can transform them with `map`, combine them with `zip`, or sequence them with `flatMap`:

```scala
import zio.blocks.scope.*

case class Database(url: String) extends AutoCloseable {
  def getConnection(name: String): Connection =
    Connection(s"$url/$name")
  override def close(): Unit = println(s"Database closed: $url")
}

case class Connection(name: String) extends AutoCloseable {
  def query(sql: String): String = s"[$name] $sql"
  override def close(): Unit = println(s"Connection closed: $name")
}

Scope.global.scoped { scope =>
  import scope.*

  val dbResource = Resource(new Database("localhost:5432"))

  // flatMap sequences: database opens first, then connection opens from it
  val connResource = dbResource.flatMap { db =>
    Resource(db.getConnection("myapp"))
  }

  val conn = allocate(connResource)

  $(conn) { c =>
    println(c.query("SELECT 1"))
  }
}
```

When you use `flatMap` to open a connection from an already-open database, the database stays open until the scope closes, ensuring the connection is always valid.

## Section 5 — Returning Values: The `Unscoped[A]` Typeclass

When you exit a `scoped { }` block, the scope closes and all resources are finalized. But what can you return from a `scoped` block? A scoped value `$[A]` cannot escape—it would be used after the scope closes. That's where `Unscoped[A]` comes in.

`Unscoped[A]` is a typeclass that marks types as safe to return from a scoped block. It means "this type contains no scope-bound resources; it is pure data." The type system only allows returning a value if it has an `Unscoped` instance:

```scala
import zio.blocks.scope.*

case class Config(host: String, port: Int)

Scope.global.scoped { scope =>
  import scope.*

  // Config is a case class—it has Unscoped by default
  Config("localhost", 5432)
}
```

Built-in `Unscoped` instances include primitives (`Int`, `String`, `Boolean`), collections (`List[A]`, `Map[K, V]`), and common library types (`UUID`, `java.time.LocalDate`). If you define a case class with no resource fields, it automatically gets an `Unscoped` instance:

```scala
import zio.blocks.scope.*

case class Result(count: Int, message: String)
case class ServerConfig(host: String, port: Int, timeout: Long)

Scope.global.scoped { scope =>
  import scope.*

  // Both can be returned—they have implicit Unscoped instances
  Result(42, "success") -> ServerConfig("0.0.0.0", 8080, 30000)
}
```

If you define a custom class and want to return it from `scoped`, you need to either derive or provide an `Unscoped` instance. In Scala 3, use the `derives` clause:

```scala
import zio.blocks.scope.*

case class CustomData(x: Int, y: String) derives Unscoped

Scope.global.scoped { scope =>
  import scope.*
  CustomData(10, "hello")
}
```

Alternatively, define it explicitly using a `given`:

```scala
import zio.blocks.scope.*

case class CustomData(x: Int, y: String)

given Unscoped[CustomData] = Unscoped.derived

Scope.global.scoped { scope =>
  import scope.*
  CustomData(10, "hello")
}
```

If you try to return a scoped value without an `Unscoped` instance, you get a compile error:

```scala
import zio.blocks.scope.*

class Connection extends AutoCloseable {
  override def close(): Unit = ()
}

// This does not compile:
// val conn = Scope.global.scoped { scope =>
//   import scope.*
//   allocate(Resource(new Connection()))  // ERROR: $[Connection] has no Unscoped
// }
```

This compile-time barrier prevents entire classes of resource-lifetime bugs—you cannot accidentally return a resource reference from a scoped block.

## Section 6 — Finalizers and Error Handling

Sometimes you need to register cleanup that is not a simple resource `close()`. The `defer` operator lets you register arbitrary cleanup actions:

```scala
import zio.blocks.scope.*

case class Transaction(id: Int) {
  def begin(): Unit = println(s"Transaction $id: begin")
  def commit(): Unit = println(s"Transaction $id: commit")
  def rollback(): Unit = println(s"Transaction $id: rollback")
}

Scope.global.scoped { scope =>
  import scope.*

  val txn = Transaction(1)
  txn.begin()

  // Register rollback as cleanup
  scope.defer {
    txn.rollback()
  }

  // If we commit, cancel the rollback
  txn.commit()
}
```

`scope.defer()` returns a `DeferHandle` that lets you cancel the finalizer before it runs:

```scala
import zio.blocks.scope.*

case class Transaction(id: Int) {
  def begin(): Unit = println(s"Transaction $id: begin")
  def commit(): Unit = println(s"Transaction $id: commit")
  def rollback(): Unit = println(s"Transaction $id: rollback")
}

Scope.global.scoped { scope =>
  import scope.*

  val txn = Transaction(1)
  txn.begin()

  // Register rollback, but keep the handle so we can cancel it
  val rollbackHandle = scope.defer {
    txn.rollback()
  }

  // On success, cancel the rollback finalizer
  txn.commit()
  rollbackHandle.cancel()

  println("Scope exiting - rollback will NOT run because we cancelled it")
}
```

Finalizers run in **LIFO order** (last registered, first executed) and are guaranteed to run even if the scoped block throws an exception. If multiple finalizers throw, they are collected:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  scope.defer { println("Finalizer 1") }
  scope.defer { println("Finalizer 2") }
  scope.defer { println("Finalizer 3") }

  println("Block executing")
}
```

★ Insight ─────────────────────────────────────
The `DeferHandle.cancel()` operation is O(1)—it marks the finalizer as cancelled without traversing the entire registry. This makes it safe to use in performance-sensitive code, like transaction commits in tight loops.
─────────────────────────────────────────────────

## Section 7 — Nested Scopes and `lower`

Scopes form a tree: each scope can create child scopes via `scope.scoped { }`. Children are guaranteed to close before their parent, which is the foundation of hierarchical resource management.

But child scopes have a different `$[A]` type than their parent, so a parent-scoped value cannot be directly used in a child. That's where `lower` comes in. `Scope#lower` re-tags a parent-scoped value into a child scope, which is safe because the parent always outlives the child:

```scala
import zio.blocks.scope.*

class Database(name: String) extends AutoCloseable {
  def query(sql: String): String = s"[$name] $sql"
  override def close(): Unit = println(s"Database [$name] closed")
}

class Connection(db: String, id: Int) extends AutoCloseable {
  def query(sql: String): String = s"[$db/$id] $sql"
  override def close(): Unit = println(s"Connection [$db/$id] closed")
}

Scope.global.scoped { parentScope =>
  import parentScope.*

  // Open database in parent scope
  val db = allocate(Resource(new Database("maindb")))

  // Use database in parent scope
  $(db) { database =>
    println(s"Parent: ${database.query("SELECT 1")}")
  }

  // Create a child scope (e.g., for a request)
  parentScope.scoped { childScope =>
    import childScope.*

    // Lower the parent-scoped database into the child scope
    val dbInChild = childScope.lower(db)

    // Now we can use the database in the child scope
    val conn = allocate(Resource(new Connection("maindb", 1)))

    $(dbInChild) { database =>
      $(conn) { connection =>
        println(s"Child: ${connection.query("SELECT 2")}")
      }
    }

    println("Child scope exiting - connection closes first")
  }

  println("Parent scope exiting - database closes after children")
}
```

When the child scope exits, all resources allocated in it close first. Then the parent scope's finalizers run. This ensures that if a child holds a reference to a parent's resource, that resource is not closed until all children have finished.

## Section 8 — Explicit Lifetime Management with `open()`

The `scoped { }` syntax ties resource lifetime to a lexical block. But sometimes you need explicit, decoupled lifetime management—for example, a request handler that opens a connection when processing begins and closes it when processing ends, independent of any fixed scope nesting.

Child scopes created via `scoped { }` are owned by the thread that creates them and must close within the creating thread. But `Scope.global.open()` creates an unowned scope that can be closed from any thread. This is useful for bridging structured scope-based resource management with callbacks or cross-thread communication:

```scala
import zio.blocks.scope.*

class ConnectionPool extends AutoCloseable {
  def acquire(): String = "conn-001"
  override def close(): Unit = println("Pool closed")
}

// Simulate a request handler in an async framework
case class RequestContext(id: Int) {
  var connection: Option[String] = None

  def process(): Unit = {
    println(s"Processing request ${id}, connection: $connection")
  }
}

// In practice, request handling might be asynchronous
val request = RequestContext(1)

Scope.global.scoped { scope =>
  import scope.*

  val pool = allocate(Resource(new ConnectionPool()))

  $(pool) { p =>
    request.connection = Some(p)
    request.process()
  }
}
```

The standard pattern for managing resource lifetimes in your application is to use `scoped { }` with careful nesting. The `open()` method is reserved for low-level integration points (like application startup/shutdown boundaries) and is not typically needed in application code.

★ Insight ─────────────────────────────────────
Thread ownership is enforced for child scopes created with `scoped { }` but not for unowned scopes from `open()`. This difference allows Scope to prevent thread-related bugs in structured code while still supporting integration with callback-driven or asynchronous frameworks that require explicit lifetime management.
─────────────────────────────────────────────────

## Section 9 — Shared Resources and Reference Counting

When multiple parts of your application need the same heavyweight resource (like a database connection pool), you want to create it once and destroy it only when the last user is done. `Resource.shared` provides reference-counted sharing:

```scala
import zio.blocks.scope.*

class ConnectionPool(id: Int) extends AutoCloseable {
  def getConnection(): String = s"conn-from-pool-$id"
  override def close(): Unit = println(s"Pool $id closed")
}

case class UserService(poolId: String)
case class OrderService(poolId: String)

Scope.global.scoped { scope =>
  import scope.*

  // Create a shared resource: only one pool instance, reference-counted
  val sharedPool = Resource.shared[ConnectionPool] { _ =>
    println("Creating shared pool...")
    new ConnectionPool(42)
  }

  // Both services allocate from the same shared resource
  // The pool is created once, destroyed after both services release it
  val pool1 = allocate(sharedPool)
  val pool2 = allocate(sharedPool)

  $(pool1) { p1 =>
    $(pool2) { p2 =>
      // Both p1 and p2 point to the same pool instance
      assert(p1 == p2)
      println(s"Service 1 got: ${p1.getConnection()}")
      println(s"Service 2 got: ${p2.getConnection()}")
    }
  }

  println("Scope exiting - shared pool closed (once)")
}
```

`Resource.shared` is memoized: the first `allocate` creates the pool, and subsequent `allocate` calls get the same instance. The finalizer runs only after all allocations have released their reference (implicitly when the scope closes).

This pattern is essential for applications with a shared database connection pool, cache, or logging infrastructure.

## Section 10 — Dependency Injection with Wire

Applications often have many services with interdependencies. Manual wiring is error-prone: forget a dependency, pass the wrong type, create a cycle, or accidentally duplicate an instance where sharing was intended.

`Wire` and `Resource.from` provide compile-time dependency injection. Wires are builders that describe how to construct instances, and `Resource.from` resolves the entire dependency graph:

```scala
import zio.blocks.scope.*

case class DbConfig(url: String)
case class Database(config: DbConfig) extends AutoCloseable {
  override def close(): Unit = println("Database closed")
}

case class CacheService(db: Database)
case class AuthService(db: Database)
case class AppService(cache: CacheService, auth: AuthService)

Scope.global.scoped { scope =>
  import scope.*

  // Wire.shared means all dependents get the same instance
  val configWire = Wire(DbConfig("localhost"))
  val dbWire = Wire.shared[Database]
  val cacheWire = Wire.shared[CacheService]
  val authWire = Wire.shared[AuthService]
  val appWire = Wire.shared[AppService]

  // Resource.from resolves all wires and returns the root app service
  val app = allocate(Resource.from[AppService](
    configWire, dbWire, cacheWire, authWire, appWire
  ))

  $(app) { a =>
    println(s"App service created: $a")
  }
}
```

The compiler verifies that:
- Every dependency can be satisfied.
- No unsatisfiable circular dependencies exist.
- Types match correctly.

If you violate any of these rules, you get a clear compile error before runtime.

## Section 11 — Thread Ownership

On the JVM, Scope enforces a structured concurrency guarantee: each `Scope.Child` (any scope created with `scoped { }` or as a child of another scope) is owned by the thread that created it. This prevents a subtle class of bugs where a scope reference escapes to another thread and resources are used or closed on the wrong thread.

You can check ownership with `Scope#isOwner`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  println(s"Global scope owned by current thread: ${scope.isOwner}")

  scope.scoped { childScope =>
    println(s"Child scope owned by current thread: ${childScope.isOwner}")
  }
}
```

If you try to use a child scope from a different thread, operations like `allocate`, `defer`, and `$(value)()` throw an `IllegalStateException`:

```scala
import zio.blocks.scope.*
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

class Database extends AutoCloseable {
  override def close(): Unit = ()
}

Scope.global.scoped { scope =>
  import scope.*

  val db = allocate(Resource(new Database()))

  // This would throw on the spawned thread:
  // Future {
  //   $(db) { _ => }  // ERROR: ownership violation
  // }
}
```

To pass a resource to another thread safely, use `Scope.global.open()` to create an unowned scope, or redesign to keep all operations on the creating thread.

For platforms like Scala.js (single-threaded), thread ownership checks are disabled—ownership is always considered valid.

★ Insight ─────────────────────────────────────
Thread ownership enforcement is not about thread safety in the traditional sense—it's about structured concurrency. It prevents subtle bugs where a scope escapes to another thread and resources are closed on a different thread than they were allocated.
─────────────────────────────────────────────────

## Section 12 — Common Errors and Troubleshooting

This section lists the most common runtime and compile errors, explains what caused them, and how to fix them.

### Runtime Errors

**`IllegalStateException: Scope is closed`** when calling `allocate`, `defer`, `$`, or `open` on a closed scope:

```scala
import zio.blocks.scope.*

class Database extends AutoCloseable {
  override def close(): Unit = ()
}

var db: Option[Scope#$[Database]] = None

Scope.global.scoped { scope =>
  import scope.*
  db = Some(allocate(Resource(new Database())))
}

// scope is now closed; this throws IllegalStateException:
// db.foreach(d => $(d) { _ => })
```

**Fix:** Ensure all resource usage happens before the scoped block exits. If you need to return a resource reference, return only the underlying value (wrapped in an `Unscoped` type).

**`IllegalStateException: Thread ownership violation`** when calling operations on a child scope from a different thread:

```scala
import zio.blocks.scope.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

var scope: Option[Scope] = None

Scope.global.scoped { s =>
  scope = Some(s)
}

Future {
  // This throws: the scope was created on the main thread, not this one
  // scope.foreach(_.scoped { _ => })
}
```

**Fix:** Use `Scope.global.open()` to create an unowned scope that can be shared across threads, or keep all operations on the creating thread.

### Compile Errors

**No `Unscoped` instance for type `T`** when trying to return a value from `scoped`:

```scala
import zio.blocks.scope.*

class Connection extends AutoCloseable {
  override def close(): Unit = ()
}

// This does not compile:
// val conn = Scope.global.scoped { scope =>
//   import scope.*
//   allocate(Resource(new Connection()))  // ERROR: $[Connection] has no Unscoped
// }
```

**Fix:** Only return types with an `Unscoped` instance (primitives, case classes, collections). If you need to return a resource reference, extract its underlying value first, or use `Wire` to manage the resource's lifetime.

**Cannot call method directly on `$[T]`**:

```scala
import zio.blocks.scope.*

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(msg)
  override def close(): Unit = ()
}

// This does not compile:
// Scope.global.scoped { scope =>
//   import scope.*
//   val logger = allocate(Resource(new Logger()))
//   logger.log("test")  // ERROR: method log not visible on $[Logger]
// }
```

**Fix:** Use the `$` operator to unwrap: `$(logger)(_.log("test"))`.

**`Wire` cannot resolve dependency** when wiring fails due to missing constructor arguments:

```scala
import zio.blocks.scope.*

case class Database(url: String) extends AutoCloseable {
  override def close(): Unit = ()
}

// If Database constructor is not satisfied by wires, compilation fails:
// val dbWire: Wire[Database] = Wire.shared[Database]  // ERROR: no Wire[String] for url
```

**Fix:** Provide a wire for every required dependency:

```scala
import zio.blocks.scope.*

case class Database(url: String) extends AutoCloseable {
  override def close(): Unit = ()
}

// Correct: provide the String
val urlWire = Wire("localhost")
val dbWire = Wire.shared[Database]

Scope.global.scoped { scope =>
  import scope.*
  val db = allocate(Resource.from[Database](urlWire, dbWire))
  $(db) { d => println(s"Connected to $d") }
}
```

---

## Summary

You now understand Scope's core concepts:

- **`$[A]`** — a type-level owner tag that prevents resources from escaping their scope.
- **`scoped { }`** — the syntax for entering and exiting a scope.
- **`allocate` and `defer`** — operations to register resources and cleanup.
- **`Resource`** — lazy descriptions of acquisition and cleanup.
- **`Unscoped`** — a compile-time guarantee that a type is safe to return from a scope.
- **Nesting and `lower`** — hierarchical resource management with compile-time parent-child relationships.
- **Shared resources** — reference counting for multiply-used resources.
- **`Wire` and dependency injection** — compile-time-verified wiring of complex applications.
- **Thread ownership** — JVM enforcement of structured concurrency.

For complete API documentation, see the [Scope Reference](../scope.md).

