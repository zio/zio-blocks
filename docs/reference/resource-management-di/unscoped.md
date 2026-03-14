---
id: unscoped
title: "Unscoped"
---

`Unscoped[A]` is a marker typeclass for types that can safely escape a scope without tracking. Types with an `Unscoped` instance are considered "safe data"—they don't hold resources and can be freely extracted from a scope:

```scala
trait Unscoped[A]
```

The `Unscoped` typeclass distinguishes between two categories of types:

1. **Unscoped types** (have an instance): Primitives, strings, collections, value types, and pure data. These can leave a scope without risk.
2. **Scoped types** (no instance): Resources like streams, connections, handles. These must remain tracked within a scope.

When the `$` operator is used to access a scoped value, if the result type has an `Unscoped` instance, it returns the value directly (unwrapped). Otherwise, it returns the value still wrapped in `$`.

## Motivation / Use Case

The `Unscoped` typeclass enables compile-time verification that you're only extracting safe data from scopes. This prevents accidental resource leaks where a database connection, stream, or file handle escapes its scope:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Resource}

Scope.global.scoped { scope =>
  import scope.*

  val intValue = allocate(Resource(42))
  // Int: Unscoped, so $ unwraps it
  val n: Int = $(intValue)(x => x + 1)

  val text = allocate(Resource("hello"))
  // String: Unscoped, so $ unwraps it
  val s: String = $(text)(x => x.toUpperCase)

  (n, s)
}
```

### Returning Aggregated Data from Scopes

Extract computed results that don't hold resources:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Resource, Unscoped}
import scala.concurrent.duration.{Duration, FiniteDuration}

case class ProcessingResult(count: Int, elapsed: FiniteDuration)

object ProcessingResult {
  implicit val unscoped: Unscoped[ProcessingResult] = new Unscoped[ProcessingResult] {}
}

def processData(): ProcessingResult = Scope.global.scoped { scope =>
  import scope.*

  val startTime = java.time.Instant.now()
  val input = allocate(Resource(Seq(1, 2, 3, 4, 5)))
  val count = $(input)(_.length)

  val elapsed = Duration.fromNanos(
    java.time.Instant.now().toEpochMilli - startTime.toEpochMilli
  ).toNanos

  ProcessingResult(count, FiniteDuration(elapsed, "ns"))
}

val result = processData()
println(result)
```

### Type-Safe Resource Boundary

The compiler prevents accidentally extracting resources:

```scala
import zio.blocks.scope.Scope
import java.io.ByteArrayInputStream

Scope.global.scoped { scope =>
  import scope.*

  val stream = scope.allocate(ByteArrayInputStream("data".getBytes))

  // ✗ This doesn't compile: InputStream is not Unscoped
  // val s: ByteArrayInputStream = stream(x => x)

  // ✓ This works: you must stay within the scope
  stream(s => s.toString)
}
```

## Construction / Creating Instances

Mark your own types as safe to escape scopes by defining an implicit instance:

```scala mdoc:compile-only
import zio.blocks.scope.Unscoped

case class UserId(value: Long)

object UserId {
  implicit val unscoped: Unscoped[UserId] = new Unscoped[UserId] {}
}

case class User(id: UserId, name: String)

object User {
  implicit val unscoped: Unscoped[User] = new Unscoped[User] {}
}
```

Only add `Unscoped` instances for pure data types that don't hold resources. Resource types (streams, connections, handles) should NOT have instances:

```scala mdoc:compile-only
import zio.blocks.scope.Unscoped
import java.io.InputStream

// ✗ DO NOT do this!
// implicit val unscoped: Unscoped[InputStream] = new Unscoped[InputStream] {}
// InputStream is a resource and must stay scoped

// ✓ DO this instead
case class StreamMetadata(name: String, size: Long)
implicit val unscoped: Unscoped[StreamMetadata] = new Unscoped[StreamMetadata] {}
```

## Predefined Instances

The following types have built-in `Unscoped` instances:

### Primitives

- `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`, `Double`, `Unit`

### Text

- `String`

### Numeric

- `BigInt`, `BigDecimal`

### Collections

- `Array[A]` (when `A: Unscoped`)
- `List[A]` (when `A: Unscoped`)
- `Vector[A]` (when `A: Unscoped`)
- `Set[A]` (when `A: Unscoped`)
- `Seq[A]` (when `A: Unscoped`)
- `IndexedSeq[A]` (when `A: Unscoped`)
- `Iterable[A]` (when `A: Unscoped`)
- `Map[K, V]` (when `K: Unscoped` and `V: Unscoped`)

### Containers

- `Option[A]` (when `A: Unscoped`)
- `Either[A, B]` (when `A: Unscoped` and `B: Unscoped`)

### Tuples

- 2-tuples through 4-tuples (when all elements are `Unscoped`)

### Time Types

- `java.time.Instant`
- `java.time.LocalDate`, `LocalTime`, `LocalDateTime`
- `java.time.ZonedDateTime`, `OffsetDateTime`
- `java.time.Duration`, `Period`, `ZoneId`, `ZoneOffset`
- `scala.concurrent.duration.Duration`, `FiniteDuration`

### Other Types

- `java.util.UUID`
- `zio.blocks.chunk.Chunk[A]` (when `A: Unscoped`)

### Low-Priority Instances

The `Nothing` type always has an `Unscoped` instance (at low priority) since it's the bottom type:

```scala
implicit val given_Unscoped_Nothing: Unscoped[Nothing] = new Unscoped[Nothing] {}
```

This is rarely used in practice but ensures there are no type errors for impossible cases.

## Core Operations: How `Unscoped` Works with `$`

The `$` operator's return type depends on whether the result has an `Unscoped` instance:

```scala
infix transparent inline def $[A, B](sa: $[A])(inline f: A => B): B | $[B]
```

When the result type `B` has an `Unscoped` instance, the `$` operator returns `B` directly (unwrapped). Otherwise, it returns `$[B]` (still scoped):

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Resource}
import java.io.ByteArrayInputStream

Scope.global.scoped { scope =>
  import scope.*

  val intValue = allocate(Resource(42))
  val result1 = $(intValue)(x => x + 1)  // returns Int (Unscoped exists)
  val result1Type = result1: Int  // verifies it's Int, not $[Int]

  val stream = allocate(ByteArrayInputStream("data".getBytes))
  val result2 = $(stream)(s => s.toString)  // returns String (Unscoped exists)
  val result2Type = result2: String  // works because String is Unscoped
}
```

## Advanced Usage: Properties and Considerations

### Thread Safety

`Unscoped` instances themselves are immutable and thread-safe. However, the types they mark must be truly immutable for safe concurrent use. For example, `Array[Int]` is mutable—if shared across threads without synchronization, it could cause data races.

### Performance

`Unscoped` instances are zero-cost abstractions:
- No runtime overhead—instances are marker types with no state
- The `$` operator's return type is determined at compile time based on the presence of an `Unscoped` instance
- No runtime type checks or casting needed

## Integration

- [`Scope.$`](./scope.md) — the operator that uses `Unscoped`
- [`Resource`](./resource.md) — types that provide `Unscoped` may be wrapped in resources
- [`Scope`](./scope.md) — manages the lifecycle of resources
