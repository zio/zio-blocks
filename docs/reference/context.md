# Context

`Context[+R]` is a type-indexed heterogeneous collection. It stores values of different types, indexed by their types, with compile-time type safety for lookups.

## Overview

```scala
import zio.blocks.context._

case class Config(debug: Boolean)
case class Metrics(count: Int)

// Create a context with multiple values
val ctx: Context[Config & Metrics] = Context(
  Config(debug = true),
  Metrics(count = 42)
)

// Retrieve values by type
val config: Config = ctx.get[Config]
val metrics: Metrics = ctx.get[Metrics]
```

## Construction

Create contexts using overloaded `Context.apply` (supports up to 10 values):

```scala
val ctx1 = Context(value1)                      // Context[Type1]
val ctx2 = Context(value1, value2)              // Context[Type1 & Type2]
val ctx3 = Context(v1, v2, v3, v4, v5)          // Context[T1 & T2 & T3 & T4 & T5]
```

Or build incrementally from empty:

```scala
val ctx = Context.empty
  .add(Config(debug = true))
  .add(Metrics(count = 0))
// Type: Context[Config & Metrics]
```

## Retrieving Values

### get

Retrieves a value by type. The type must be in `R`:

```scala
val config: Config = ctx.get[Config]
```

Supertypes work too:

```scala
trait Named { def name: String }
case class Person(name: String, age: Int) extends Named

val ctx = Context(Person("Alice", 30))
val named: Named = ctx.get[Named]  // Returns the Person
```

### getOption

Retrieves a value if present, without requiring the type to be in `R`:

```scala
val maybeConfig: Option[Config] = ctx.getOption[Config]  // Some(...)
val maybeOther: Option[Other] = ctx.getOption[Other]     // None
```

## Modifying Contexts

### add

Adds a value, returning a new context with an expanded type:

```scala
val ctx1 = Context(Config(true))             // Context[Config]
val ctx2 = ctx1.add(Metrics(0))              // Context[Config & Metrics]
```

Adding a value of an existing type replaces it:

```scala
val ctx1 = Context(Config(debug = false))
val ctx2 = ctx1.add(Config(debug = true))
ctx2.get[Config].debug  // true
```

### update

Transforms an existing value:

```scala
val ctx = Context(Metrics(count = 0))
val updated = ctx.update[Metrics](m => m.copy(count = m.count + 1))
updated.get[Metrics].count  // 1
```

### ++ (union)

Combines two contexts. Right values override left:

```scala
val ctx1 = Context(Config(debug = false))
val ctx2 = Context(Config(debug = true), Metrics(0))

val merged = ctx1 ++ ctx2
// Config comes from ctx2 (right wins)
```

### prune

Narrows a context to specific types:

```scala
val ctx: Context[Config & Metrics & Other] = ...
val pruned: Context[Config] = ctx.prune[Config]
```

## Covariance

`Context` is covariant, so `Context[Specific]` is a subtype of `Context[General]`:

```scala
def process(ctx: Context[Named]): Unit = {
  val named = ctx.get[Named]
  println(named.name)
}

val ctx: Context[Person] = Context(Person("Bob", 25))
process(ctx)  // Works: Context[Person] <: Context[Named]
```

## Type Safety: IsNominalType

Only nominal types can be stored. The `IsNominalType[A]` typeclass is derived automatically for:

- Classes, case classes, traits, objects
- Enums (Scala 3)
- Applied types (`List[Int]`, `Map[K, V]`)

Not supported (compile error):

- Intersection types: `A & B`
- Union types: `A | B`  
- Structural types: `{ def foo: Int }`

## Performance

- **Caching**: Retrieved values are cached for O(1) subsequent lookups
- **Subtype matching**: Supertype lookups find matching subtypes and cache results
- **Platform-optimized**: JVM uses `ConcurrentHashMap`; JS uses efficient mutable maps
