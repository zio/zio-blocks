---
id: scope
title: "Scope"
---

# ZIO Blocks Scope

**Compile-time verified dependency injection with resource escape prevention for Scala.**

## What Is Scope?

Scope is a minimalist dependency injection library that makes **resource lifecycle errors into compile-time errors**. Most DI libraries verify that your dependencies are wired correctly — Scope goes further by preventing resources from escaping their intended lifecycle.

- You can't access a resource outside its scope — compile error
- You can't accidentally return a scoped resource — its methods are hidden
- You can't use `$` or `.get` without being inside a `.use` block — compile error
- Cleanup runs automatically when the scope closes

Scope supports both **Scala 2.13** and **Scala 3.x**. This documentation focuses on Scala 3; see inline notes for Scala 2 differences.

> **Note:** Despite the `zio.blocks` package prefix, Scope has **no dependency on ZIO**. The prefix is for project grouping only. Scope is effect-system-agnostic and works with any Scala code.

## Why Scope?

Scope exists because **"the dependency graph is correct" is not the same thing as "the lifecycle is correct."** Traditional DI tools are great at construction correctness (everything can be instantiated), but they rarely make **resource lifetime** a *type-level* property. That leaves entire classes of bugs—use-after-close, leaking request resources into app singletons, registering cleanup on the wrong lifecycle—until runtime.

Scope's unique value is: **resources are scoped in the type system**, so many lifecycle mistakes become **compile-time errors**.

### The Problem

Most DI approaches can build the app, but they don't *type* the lifetime of request/temporary resources. It's easy to accidentally let a resource escape.

Here's a simplified "traditional DI" pattern: build request dependencies, run a handler, and close everything afterward:

```scala
final class TempFile {
  def write(s: String): Unit = ()
  def delete(): Unit = ()
}

def withRequest[A](f: TempFile => A): A = {
  val tmp = new TempFile
  try f(tmp)
  finally tmp.delete()
}
```

This *looks* disciplined, but nothing prevents accidental resource escape:

```scala
// BUG: returns a resource that is already finalized
val escaped: TempFile =
  withRequest { tmp =>
    tmp.write("hello")
    tmp
  }

// Compiles, but at runtime you're now using a closed/deleted resource
escaped.write("still here?")
```

Even if you don't explicitly return the resource, similar issues show up when:
- a request-scoped resource gets stored in a longer-lived object,
- a resource is used asynchronously after the request finishes,
- cleanup is registered on the wrong lifecycle because "all scopes look the same."

In an untyped lifecycle model, **the compiler can't help**—a `TempFile` is just a `TempFile`, regardless of which scope it belongs to.

### The Solution

Scope makes "what scope this value belongs to" part of the value's type via the opaque type:

- `$[T]` returns a **scoped value**: `T @@ Tag`
- `T @@ Tag` **hides all methods** on `T`
- You can only use a scoped value by calling `scoped $ (_.method(...))`
- `$` (and `.get`) require `ScopeProof[Tag]`, which is derived **only** from `Scope.Permit[Tag]`
- `Scope.Permit[Tag]` is an **unforgeable token** provided by `.use`

Concretely:

```scala
import zio.blocks.scope._

final class TempFile extends AutoCloseable {
  def write(s: String): Unit = ()
  def close(): Unit = delete()
  def delete(): Unit = ()
}

def good(): Unit =
  Scope.global
    .injected[TempFile]()      // TempFile will be finalized when the scope closes
    .use {
      val tmp = $[TempFile]    // TempFile @@ Tag

      // tmp.write("x")        // DOES NOT COMPILE: methods are hidden on scoped values
      tmp $ (_.write("hello")) // OK: method access via $

      // Returning unscoped data is fine:
      val n: Int = tmp $ (_ => 123)
      n
    }
```

Now look at the "escape" case again—Scope allows you to *physically* return a value, but it becomes **unusable outside its scope**:

```scala
import zio.blocks.scope._

val escaped: TempFile @@ ? =
  Scope.global.injected[TempFile]().use {
    $[TempFile]                // returns TempFile @@ Tag
  }

// escaped.write("x")          // DOES NOT COMPILE (methods hidden)
// escaped $ (_.write("x"))    // DOES NOT COMPILE: no ScopeProof for that Tag outside `.use`
```

That last line fails specifically because `ScopeProof[S]` is only available inside `.use`, where the library provides:

- `Scope.Permit[self.Tag]` (the unforgeable capability)
- `Context[Head] @@ self.Tag`
- `self.type` (so `defer { ... }` registers cleanup on the correct scope)

So the compiler enforces the core guarantee:

- **Inside** `.use`: you have `Scope.Permit[Tag]` ⇒ you get `ScopeProof[Tag]` ⇒ you can use scoped values via `$`.
- **Outside** `.use`: you do *not* have `Scope.Permit[Tag]` ⇒ you cannot obtain `ScopeProof[Tag]` ⇒ you cannot use scoped values.

**What you get:**
- Construction correctness (like other DI approaches)
- **Compile-time prevention of resource use outside its lifecycle**
- Scoped values that hide methods, forcing explicit safe access (`scoped $ (...)`)
- Cleanup that runs automatically when the scope closes, in LIFO order
- No reflection, no runtime container magic—just types and an unforgeable capability token (`Scope.Permit`)

---

## Quick Start

```scala
import zio.blocks.scope._

class Config { val dbUrl: String = "jdbc://localhost/mydb" }
class Database(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"Result from ${config.dbUrl}"
  def close(): Unit = println("Database closed")
}
class App(db: Database) {
  def run(): Unit = println(db.query("SELECT 1"))
}

@main def main(): Unit =
  Scope.global.injected[App](shared[Database], shared[Config]).use {
    val app = $[App]      // App @@ Tag - scoped value
    app $ (_.run())       // Access methods via $ operator
  }
  // Database.close() called automatically
```

The `injected[App]` macro discovers dependencies, wires them up, runs your code, then cleans up in reverse order. The `$[T]` function returns a scoped value that can only be accessed via the `$` operator.

---

## Core Concepts

### Scoped Values (`A @@ S`)

Values retrieved from a scope are wrapped in `A @@ S`, an opaque type that hides all methods on `A`. This prevents resources from accidentally escaping their scope.

```scala
closeable.use {
  val stream = $[InputStream]   // InputStream @@ Tag
  stream.read()                 // Compile error: read() is not a member
  stream $ (_.read())           // Works: returns Int (unscoped)
}
```

The `$` operator requires `Scope.Permit[S]`, which is only available inside `.use` blocks.

### The `.use` Method

The `.use` method on `Scope.Closeable` executes code within a scope and provides three implicit parameters:

1. **`Scope.Permit[Tag]`**: An unforgeable capability marker proving code is inside a `.use` block
2. **`Context[Head] @@ Tag`**: The scoped context containing this scope's services  
3. **`self.type`**: The scope itself for `defer` calls

```scala
closeable.use {
  // All three are implicitly available here
  val app = $[App]              // Uses Permit and Context
  defer { cleanup() }           // Uses the scope
  app $ (_.run())
}
```

### Retrieving Services with `$[T]`

The `$[T]` macro requires:

- `Scope.Permit[S]` in implicit scope (from a `.use` block)
- `Context[T] @@ S` in implicit scope (provided by `.use`)
- `T` must be a nominal type (class or trait, not a type alias or structural type)

```scala
closeable.use {
  val db = $[Database]           // Database @@ Tag
  db $ (_.query("SELECT ..."))   // String (unscoped)
}
```

### Escape Prevention with `ScopeEscape`

When you use `$` or `.get`, the `ScopeEscape[A, S]` typeclass determines whether the result escapes the scope:

**Priority (highest to lowest):**

1. **Root scope** (the global scope that never closes): all types escape as raw `A`
2. **`Unscoped` types**: escape as raw `A` regardless of scope
3. **Resource types**: stay scoped as `A @@ S`

```scala
closeable.use {
  val stream = $[InputStream]
  
  // Int is Unscoped, so it escapes
  val n: Int = stream $ (_.read())
  
  // InputStream is NOT Unscoped, so it stays scoped
  val inner: InputStream @@ Tag = stream $ identity
}
```

### `Unscoped` Types

Types with an `Unscoped` instance are considered safe data that doesn't hold resources:

**Built-in instances:**
- Primitives: `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`, `Double`, `Unit`
- Text: `String`
- Numeric: `BigInt`, `BigDecimal`
- Collections: `Array`, `List`, `Vector`, `Set`, `Seq`, `Map`, `Option`, `Either` (when elements are `Unscoped`)
- Tuples: up to 4-tuples (when elements are `Unscoped`)
- Time: `java.time.*` value types, `scala.concurrent.duration.*`
- Other: `java.util.UUID`, `zio.blocks.chunk.Chunk`

**Deriving for case classes (Scala 3):**

```scala
case class Config(host: String, port: Int)
object Config {
  given Unscoped[Config] = Unscoped.derived[Config]
}
```

---

## Lifecycle Management

### The `defer` Function

Register cleanup actions that run when the scope closes:

```scala
class MyService()(using Scope.Any) {
  val handle = acquire()
  defer { handle.release() }  // Runs on scope close
}
```

Finalizers run in LIFO order (last registered first). If a finalizer throws, subsequent finalizers still run.

### AutoCloseable Support

If a type extends `AutoCloseable`, its `close()` method is automatically registered as a finalizer:

```scala
class Database extends AutoCloseable {
  def close(): Unit = println("Closed")
}

Scope.global.injected[Database](shared[Database]).use {
  // ...
}  // Database.close() called automatically
```

### Error Handling

```scala
// Discard finalizer errors (default)
closeable.use { ... }

// Get finalizer errors
val (result, errors) = closeable.useWithErrors { ... }

// Manual close - returns errors
val errors: Chunk[Throwable] = closeable.close()

// Throw first error if any
closeable.closeOrThrow()
```

---

## Dependency Injection

### `shared[T]` and `unique[T]`

These macros derive wires from constructors:

- **`shared[T]`**: Memoized within a single `injected` call (same instance for all dependents)
- **`unique[T]`**: Fresh instance each time

```scala
Scope.global.injected[App](
  shared[Config],    // One Config instance
  shared[Database],  // One Database instance
  unique[RequestId]  // Fresh ID per dependent
).use { ... }
```

### `Wire(value)`

Inject a pre-existing value:

```scala
val config = Config.load()
Scope.global.injected[App](Wire(config), shared[Database]).use { ... }
```

### `injected(value)`

Wrap an existing value in a closeable scope. Requires a parent scope:

```scala
val config = Config.load()
given Scope.Any = Scope.global
injected(config).use {
  val cfg = $[Config]
  cfg $ (_.dbUrl)
}
```

If the value is `AutoCloseable`, its `close()` method is automatically registered.

### Custom Wiring with Wireable

When `shared[T]` or `unique[T]` can't derive a wire automatically (e.g., for traits or abstract classes), define a `Wireable` in the companion object:

```scala
// Scala 3
trait Database { def query(sql: String): String }

class PostgresDatabase(config: Config) extends Database with AutoCloseable {
  def query(sql: String): String = s"Result from ${config.url}"
  def close(): Unit = println("Database closed")
}

object Database {
  given Wireable.Typed[Config, Database] = new Wireable[Database] {
    type In = Config
    def wire: Wire[Config, Database] = Wire.Shared[Config, Database] {
      val config = $[Config]
      val db = new PostgresDatabase(config $ identity)
      defer(db.close())
      Context[Database](db)
    }
  }
}

// Now shared[Database] works even though Database is a trait
Scope.global.injected[Database](shared[Config]).use {
  val db = $[Database]
  db $ (_.query("SELECT 1"))
}
```

In Scala 2, use `implicit val` instead of `given`:

```scala
object Database {
  implicit val wireable: Wireable.Typed[Config, Database] = new Wireable[Database] {
    type In = Config
    def wire: Wire[Config, Database] = Wire.Shared.fromFunction[Config, Database] { scope =>
      val config = scope.get[Config]
      val db = new PostgresDatabase(config)
      scope.defer(db.close())
      Context[Database](db)
    }
  }
}
```

---

## Scoped Value Operations

### The `$` Operator

Apply a function to the underlying value:

```scala
val scoped: Database @@ Tag = $[Database]
val result: String = scoped $ (_.query("SELECT 1"))  // String escapes (Unscoped)
```

### The `.get` Method

Extract the value (equivalent to `$ identity`):

```scala
val n: Int = intScoped.get          // Int escapes
val db: Database @@ Tag = dbScoped.get  // Database stays scoped
```

### `map` and `flatMap`

Transform scoped values (no scope proof required):

```scala
val conn: Connection @@ S = ...
val stmt: Statement @@ S = conn.map(_.createStatement())

// For-comprehension with tag intersection
val result: Result @@ (S & T) = for {
  a <- scopedA  // A @@ S
  b <- scopedB  // B @@ T
} yield combine(a, b)
```

### Tuple Extraction

```scala
val pair: (Int, String) @@ S = ...
val first: Int @@ S = pair._1
val second: String @@ S = pair._2
```

---

## The Scope Type Hierarchy

```scala
sealed trait Scope {
  type Tag  // Path-dependent tag identifying this scope
  def defer(finalizer: => Unit): Unit
}

object Scope {
  type GlobalTag                          // Root of tag hierarchy
  type Any = Scope                        // Scope with unknown stack
  type Has[+T] = ::[T, Scope]             // Scope that has T available
  
  sealed abstract class Permit[S]         // Unforgeable capability from .use
  
  class Global extends Scope {
    type Tag <: GlobalTag
  }
  
  class ::[+H, +T <: Scope] extends Scope with Closeable[H, T] {
    type Tag <: tail.Tag  // Child tags are subtypes of parent tags
  }
  
  trait Closeable[+Head, +Tail <: Scope] extends Scope {
    def use[B](f: ... ?=> B): B
    def useWithErrors[B](f: ... ?=> B): (B, Chunk[Throwable])
    def close(): Chunk[Throwable]
    def closeOrThrow(): Unit
  }
  
  val global: Global  // Singleton, finalizes on JVM shutdown
}
```

**Tag Hierarchy:**
- `Global.Tag <: GlobalTag` (base case)
- `(H :: T).Tag <: T.Tag` (child tags are subtypes of parent tags)

This enables child scopes to use parent-scoped values (a value tagged with a parent's tag is usable in child scopes).

---

## Interop: Escaping Scoped Values

### The `leak` Function

Escape a scoped value when you need to pass it to third-party or Java code that can't work with scoped values (emits compiler warning):

```scala
closeable.use {
  val stream = leak($[Request] $ (_.body.getInputStream()))
  ThirdPartyProcessor.process(stream)  // Third-party code that needs raw InputStream
}
```

Suppress warning with `@nowarn("msg=is being leaked")`.

---

## API Reference

### Package Functions

```scala
// Retrieve service from scope (returns T @@ S)
transparent inline def $[T]: Any

// Register cleanup on current scope
def defer(finalizer: => Unit)(using Scope.Any): Unit

// Derive wires from constructors
transparent inline def shared[T]: Wire.Shared[?, T]
transparent inline def unique[T]: Wire.Unique[?, T]

// Create child scope with injected value
def injected[T](t: T)(using Scope.Any, IsNominalType[T]): Scope.::[T, ?]
inline def injected[T](using Scope.Any): Scope.Closeable[T, ?]
inline def injected[T](wires: Wire[?, ?]*)(using Scope.Any): Scope.Closeable[T, ?]

// Escape scoped value (emits warning)
inline def leak[A, S](scoped: A @@ S): A
```

### Scoped Values (`@@`)

```scala
opaque infix type @@[+A, +S] = A

object @@ {
  inline def scoped[A, S](a: A): A @@ S
  
  extension [A, S](scoped: A @@ S) {
    inline infix def $[B](f: A => B)(using Scope.Permit[S])(using ScopeEscape[B, S]): ...
    inline def get(using Scope.Permit[S])(using ScopeEscape[A, S]): ...
    inline def map[B](f: A => B): B @@ S
    inline def flatMap[B, T](f: A => B @@ T): B @@ (S & T)
    inline def _1[X, Y](using A =:= (X, Y)): X @@ S
    inline def _2[X, Y](using A =:= (X, Y)): Y @@ S
  }
}
```

### Wire

```scala
sealed trait Wire[-In, +Out] {
  def construct(implicit scope: Scope.Has[In]): Context[Out]
  def isShared: Boolean
  def isUnique: Boolean
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
}

object Wire {
  def apply[T](t: T)(implicit ev: IsNominalType[T]): Wire.Shared[Any, T]
  
  class Shared[-In, +Out] extends Wire[In, Out]
  class Unique[-In, +Out] extends Wire[In, Out]
}
```

### ScopeEscape

```scala
// Controls whether values escape or stay scoped
trait ScopeEscape[A, S] {
  type Out  // Either A (escaped) or A @@ S (scoped)
  def apply(a: A): Out
}
```

### Wireable

```scala
// Define in companion objects for traits/abstract classes
trait Wireable[+Out] {
  type In                   // Dependencies required
  def wire: Wire[In, Out]   // The wire for construction
}

object Wireable {
  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }
  
  def apply[T](value: T)(implicit ev: IsNominalType[T]): Wireable.Typed[Any, T]
  def fromWire[In0, Out](w: Wire[In0, Out]): Wireable.Typed[In0, Out]
  transparent inline def from[T]: Wireable[T]  // Derives from constructor (Scala 3)
}
```

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

**Core Workflow:**
1. Create a closeable scope with `injected[T](wires...)`
2. Use `.use { ... }` to execute code within the scope
3. Retrieve services with `$[T]` (returns `T @@ Tag`)
4. Access methods via `$` operator: `scoped $ (_.method())`
5. Scope closes automatically, running finalizers in LIFO order

**Key Guarantees:**
- Scoped values (`A @@ S`) hide all methods, preventing accidental escape
- `$` and `.get` require `Scope.Permit[S]`, only available inside `.use`
- `Unscoped` types (primitives, strings, collections) escape freely
- Resource types stay scoped unless explicitly leaked
- Global scope values always escape (global scope never closes)
