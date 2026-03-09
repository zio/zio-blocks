---
id: context
title: "Context"
---

## Definition

`Context[+R]` is a type-indexed heterogeneous collection that stores values of different types, indexed by their types, with compile-time type safety for lookups. It provides an immutable, cache-aware dependency container where the phantom type `R` (using intersection types) tracks which types are present.

The core type looks like this:

```scala
// Signature (showing public API structure, not actual implementation)
final class Context[+R] {
  def size: Int
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def get[A >: R](implicit ev: IsNominalType[A]): A
  def getOption[A](implicit ev: IsNominalType[A]): Option[A]
  def add[A](a: A)(implicit ev: IsNominalType[A]): Context[R & A]
  def update[A >: R](f: A => A)(implicit ev: IsNominalType[A]): Context[R]
  def ++[R1](that: Context[R1]): Context[R & R1]
  def prune[A >: R](implicit ev: IsNominalIntersection[A]): Context[A]
  override def toString: String
}
```

Key properties: covariant (`+R`), immutable, cached for repeated lookups, supports only nominal types.

## Overview

Context serves as a type-safe registry for heterogeneous dependencies. Here's a quick example:

```scala mdoc:silent
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)
```

We can create and retrieve values by type:

```scala mdoc
val ctx: Context[Config & Logger & Metrics] = Context(
  Config(debug = true),
  Logger("app"),
  Metrics(count = 42)
)

val config: Config = ctx.get[Config]
val logger: Logger = ctx.get[Logger]
```

This ASCII diagram shows how Context maps types to values:

```
┌─────────────────────────────────┐
│         Context[R]              │
│  (Type-Indexed Store)           │
├─────────────────────────────────┤
│  Config         → Config(true)  │
│  Logger         → Logger("app") │
│  Metrics        → Metrics(42)   │
├─────────────────────────────────┤
│ ✓ Type-safe retrieval           │
│ ✓ No casting                    │
│ ✓ Cache-aware (O(1) repeats)    │
└─────────────────────────────────┘
```

## Motivation

When building modular applications, we often need to pass multiple dependencies around—a database connection, a config object, a logger, and so on. Existing approaches each have limitations:

**`Map[Class[_], Any]`** — no compile-time safety. You must cast the result and remember which keys you registered:

```scala
// Unsafe approach — easy to make mistakes
val deps = scala.collection.mutable.Map[Class[_], Any]()
deps(classOf[Config]) = Config(debug = true)
deps(classOf[Logger]) = Logger("app")

val config = deps(classOf[Config]).asInstanceOf[Config]  // Manual cast
val db = deps(classOf[Database]) // Runtime error if missing
```

**ZIO's `ZEnvironment`** — type-safe but requires the full ZIO effect system:

```scala
// Requires ZIO context
import zio._

val makeEnv = for {
  config <- ZIO.service[Config]
  logger <- ZIO.service[Logger]
} yield (config, logger)
```

**Context** — combines compile-time type safety with synchronous, pure code:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)

// Type-safe, no effects needed
val ctx = Context(Config(true), Logger("app"))
val config = ctx.get[Config]  // Compile-time proof it exists
```

## Installation

Add the ZIO Blocks Context module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-context" % "@VERSION@"
```

## Construction

Context provides several ways to create instances. Choose the approach that best fits your use case: start empty and add values incrementally, or construct a fully-populated context directly with `apply`.

### Creating Empty Contexts

Use `Context.empty` to create an empty context with no entries:

```scala mdoc:silent:reset
import zio.blocks.context._
```

```scala mdoc
val emptyCtx: Context[Any] = Context.empty
val isEmpty = emptyCtx.isEmpty
```

An empty context has type `Context[Any]` and represents no stored dependencies. This is a useful starting point for incremental construction.

### Creating Multi-Value Contexts with apply

`Context.apply` is overloaded to accept 1–10 values and returns a context with type `Context[A1 & A2 & ...]`, reflecting all stored types.

#### Single Value

Create a context with one value:

```scala mdoc:silent
case class Config(debug: Boolean)
```

```scala mdoc
val single: Context[Config] = Context(Config(debug = true))
```

#### Multiple Values

Create a context with multiple values—the type parameter automatically becomes an intersection of all stored types:

```scala mdoc:silent
case class Logger(name: String)
```

```scala mdoc
val multi: Context[Config & Logger] = Context(
  Config(debug = true),
  Logger("myapp")
)
```

### Building Incrementally with add

For contexts that grow over time, use `Context#add` to build incrementally from an empty context. This is useful when dependencies become available at different points in your initialization:

```scala mdoc
val ctx = Context.empty
  .add(Config(debug = false))
  .add(Logger("init"))
```

The context accumulates all added entries:

```scala mdoc
val size1 = ctx.size
```

**When to use `add` vs. `apply`:**
- Use `apply` when you know all dependencies upfront and can construct them together
- Use `add` when dependencies are added incrementally or conditionally

## Core Operations

Context supports inspection, retrieval, and modification operations. All methods are type-safe and leverage the phantom type `R` to track what's stored.

### Inspection

The following methods let you check the contents of a context without retrieving specific values:

#### Context#size

Returns the number of entries in the context:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx = Context(Config(true), Logger("app"))
val sz = ctx.size
```

#### Context#isEmpty

Returns `true` if the context contains no entries:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)

val empty = Context.empty
val notEmpty = Context(Config(true))

val e1 = empty.isEmpty
val e2 = notEmpty.isEmpty
```

#### Context#nonEmpty

Returns `true` if the context contains at least one entry (opposite of `isEmpty`):

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)

val empty = Context.empty
val notEmpty = Context(Config(true))

val e1 = empty.nonEmpty
val e2 = notEmpty.nonEmpty
```

### Retrieval

#### Context#get

Retrieves a value by type. The type bound `A >: R` ensures that a value of type `A` (or a subtype of `A`) is present at compile time:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val ctx = Context(Config(debug = true), Logger("app"), Metrics(100))

// Retrieve by exact type
val config = ctx.get[Config]
```

Or by supertype (subtype matching):

```scala mdoc:compile-only
import zio.blocks.context._

trait Animal { def sound: String }
case class Dog(name: String) extends Animal {
  def sound = "Woof"
}

val ctxDog = Context(Dog("Buddy"))
val animal = ctxDog.get[Animal]
```

If you attempt to retrieve a type that is not in the context, the code will not compile because the type bound `A >: R` requires it to be present:

```scala
// This is a compile-time error, not a runtime error:
// val metrics: String = ctx.get[String]  // Error: String is not in context type
```

#### Context#getOption

Retrieves a value if present, returning `Option[A]`. Unlike `get`, this method does not require the type to be in the context's type parameter. Use it for optional lookups:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)

val ctx = Context(Config(debug = true))

val found: Option[Config] = ctx.getOption[Config]
val missing: Option[String] = ctx.getOption[String]
```

### Modification

All modification methods return a new `Context`—the original remains immutable:

#### Context#add

Adds a value to the context, expanding the phantom type by `& A`:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx1 = Context(Config(true))
val ctx2 = ctx1.add(Logger("new"))
```

If a value of the same type already exists, it is replaced:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx1 = Context(Config(true))
val ctx2 = ctx1.add(Logger("new"))
val ctx3 = ctx2.add(Config(debug = false))
val replaced = ctx3.get[Config]
```

#### Context#update

Transforms an existing value if it is present. If the type is not found, the context is returned unchanged:

```scala mdoc:compile-only
import zio.blocks.context._

case class Metrics(count: Int)

val ctx = Context(Metrics(count = 10))
val updated = ctx.update[Metrics](m => m.copy(count = m.count + 5))
val newCount = updated.get[Metrics].count
```

#### Context#++ (Union)

Combines two contexts into a new context containing all entries. When both contexts contain the same type, the value from the right side (second argument) wins:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val left = Context(Config(debug = false), Logger("left"))
val right = Context(Config(debug = true), Metrics(99))
val merged = left ++ right
```

#### Context#prune

Narrows a context to contain only specified types. All other entries are discarded:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val full = Context(Config(true), Logger("app"), Metrics(100))
val justConfig = full.prune[Config]
val configSize = justConfig.size
```

#### Context#toString

Returns a human-readable representation of the context showing all type-value pairs:

```scala mdoc:compile-only
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx = Context(Config(debug = true), Logger("app"))
val str = ctx.toString
```

## Covariance

`Context` is covariant in its type parameter, meaning `Context[Dog] <: Context[Animal]` when `Dog <: Animal`. This allows passing a more-specific context to code expecting a more-general one:

```scala mdoc:compile-only
import zio.blocks.context._

trait Animal { def sound: String }
case class Dog(name: String) extends Animal {
  def sound = "Woof"
}

def processAnimal(ctx: Context[Animal]): String = ctx.get[Animal].sound

val dogCtx = Context(Dog("Buddy"))
val sound = processAnimal(dogCtx)
```

Covariance also applies during retrieval—if you request a supertype, the stored subtype is returned.

## Type Safety: IsNominalType

Context only accepts **nominal types**—concrete classes, case classes, traits, and objects. The compiler automatically derives `IsNominalType[A]` for allowed types and rejects unsupported kinds:

**Supported:**
- Classes: `case class Config(...)`
- Traits: `trait Logger`
- Objects: `object Registry`
- Applied types: `List[Int]`, `Map[String, Int]`
- Enums (Scala 3): `enum Color { case Red, Green, Blue }`

**Not supported (compile error):**
- Intersection types: `A & B` (use the context type parameter instead)
- Union types: `A | B`
- Structural types: `{ def foo: Int }`

Attempting to store an unsupported type:

```scala mdoc:compile-only
// This fails at compile time:
// Context.empty.add(null: (String & Int))  // Error: unsupported type
```

## Performance

**Caching**: When a value is retrieved via `get` or `getOption`, the result is cached. Repeated lookups for the same type return the cached value in O(1) time without traversing the entries again.

**Subtype matching**: Supertype lookups scan the entry list linearly to find a compatible subtype. After the first lookup, the result is cached, so subsequent requests for that supertype are O(1).

**Platform optimizations**: The JVM implementation uses `ConcurrentHashMap` for thread-safe caching. The JavaScript platform uses a simpler in-memory mutable hash map for efficient lookups.

## Comparing Approaches

Here is a comparison of Context with related alternatives:

| Feature | `Map[Class[_], Any]` | `ZEnvironment` | `Context` |
|---------|----------------------|----------------|-----------|
| Type-safe retrieval | ✗ (cast required) | ✓ | ✓ |
| Compile-time proof | ✗ | ✓ | ✓ |
| Effect-free | ✓ | ✗ (requires ZIO) | ✓ |
| Immutable | ✓ | ✓ | ✓ |
| Cached lookups | ✗ | ✓ | ✓ |
| Supertype matching | ✗ | ✓ | ✓ |

## Integration with Wire and Scope

`Context` is the dependency carrier in ZIO Blocks' Wire-based dependency injection system. A `Wire[-In, +Out]` describes how to build an output given input dependencies, and contexts supply those dependencies. `Wire.make` constructs the wire, and `Scope.global.scoped` provides a managed scope in which to instantiate it:

```scala mdoc:compile-only
import zio.blocks.scope._
import zio.blocks.context._

case class Config(debug: Boolean)
case class Logger(name: String)
case class Service(config: Config, logger: Logger)

// Define a wire that requires Config and Logger to build Service
val buildService = Wire.make[Config & Logger, Service]

// Create a context with the required dependencies
val deps = Context(Config(debug = true), Logger("app"))

// Create a scope and instantiate the service
Scope.global.scoped { scope =>
  val service: Service = buildService.make(scope, deps)
}
```

## Running the Examples

The examples in this reference are compiled and checked via [mdoc](https://scalameta.org/mdoc/). We also provide runnable example applications in the `schema-examples` module:

- **`ContextConstructionExample`** — demonstrates `Context.apply`, `Context.empty.add(...)`, and inspection methods (`size`, `isEmpty`, `nonEmpty`, `toString`).
- **`ContextRetrievalExample`** — shows `get`, supertype lookups, and `getOption` for optional retrieval.
- **`ContextModificationExample`** — covers `add` (replacement), `update`, `++` (union), and `prune`.

To run an example:

```bash
sbt "schema-examples/runMain context.ContextConstructionExample"
sbt "schema-examples/runMain context.ContextRetrievalExample"
sbt "schema-examples/runMain context.ContextModificationExample"
```

All example outputs use `util.ShowExpr.show(...)` to display results in a readable format.
