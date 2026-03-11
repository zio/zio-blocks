---
id: finalization
title: "Finalization"
---

`Finalization` is the result of running all finalizers in a scope. It collects any errors that occurred during cleanup and provides convenient methods for inspecting and re-throwing those errors.

```scala
final class Finalization(val errors: Chunk[Throwable]) {
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def orThrow(): Unit
  def suppress(initial: Throwable): Throwable
}
```

When a scope closes, each registered finalizer runs in LIFO order. If any finalizer throws an exception, that error is caught and collected into a `Finalization`. This type ensures that all finalizers run even if some fail, and allows the caller to decide how to handle accumulated errors.

## Overview

`Finalization` collects errors from finalizers in a `Chunk[Throwable]`. The first error in the chunk corresponds to the head of the chunk (the first finalizer that failed in LIFO execution order).

The type is immutable and provides four main operations:
- **Check if empty**: `isEmpty`, `nonEmpty`
- **Throw errors**: `orThrow()`
- **Suppress errors**: `suppress(initial)`

## Core Methods

### `isEmpty: Boolean`

```scala
def isEmpty: Boolean
```

Returns `true` if no finalizer errors were collected.

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*
  scope.defer {
    println("Cleanup")
  }
}

if (fin.isEmpty) println("No errors") else println("Errors occurred")
```

### `nonEmpty: Boolean`

```scala
def nonEmpty: Boolean
```

Returns `true` if at least one finalizer error was collected.

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*
  scope.defer {
    throw new Exception("Cleanup failed")
  }
}

if (fin.nonEmpty) {
  println(s"Errors occurred: ${fin.errors.length}")
}
```

### `orThrow(): Unit`

```scala
def orThrow(): Unit
```

Throws the first collected error with all remaining errors added as suppressed exceptions. Does nothing if there are no errors.

The first error corresponds to the head of the chunk (the first finalizer that failed in LIFO execution order). Remaining errors are attached as suppressed exceptions using `addSuppressed()`.

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*
  scope.defer { throw Exception("Error 2") }
  scope.defer { throw Exception("Error 1") }
}

try {
  fin.orThrow()
} catch {
  case e: Exception =>
    println(s"Primary: ${e.getMessage}")
    e.getSuppressed.foreach(s => println(s"Suppressed: ${s.getMessage}"))
}
```

### `suppress(initial: Throwable): Throwable`

```scala
def suppress(initial: Throwable): Throwable
```

Adds all collected finalizer errors as suppressed exceptions to `initial` and returns it. If there are no errors, `initial` is returned unchanged.

This is useful when you want to preserve the original error context while attaching cleanup errors:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val initialError = Exception("Original error")

val fin = Scope.global.scoped { scope =>
  import scope.*
  scope.defer { throw Exception("Cleanup error") }
}

val combined = fin.suppress(initialError)
println(s"Primary: ${combined.getMessage}")
combined.getSuppressed.foreach(s => println(s"Suppressed: ${s.getMessage}"))
```

## Predefined Instance

### `Finalization.empty`

```scala
object Finalization {
  val empty: Finalization  // singleton with no errors
}
```

A predefined `Finalization` with no errors, useful as a no-op result.

```scala mdoc:compile-only
import zio.blocks.scope.Finalization

val empty = Finalization.empty
println(s"Empty finalization has ${empty.errors.length} errors")
```

## Companion Constructor

### `Finalization.apply(errors: Chunk[Throwable]): Finalization`

```scala
object Finalization {
  def apply(errors: Chunk[Throwable]): Finalization
}
```

Creates a new `Finalization` from a chunk of errors.

```scala mdoc:compile-only
import zio.blocks.scope.Finalization
import zio.blocks.chunk.Chunk

val errors = Chunk(Exception("Error 1"), Exception("Error 2"))
val fin = Finalization(errors)
println(s"Finalization with ${fin.errors.length} errors")
```

## Error Ordering

Errors in the finalization are ordered by when finalizers ran (in LIFO sequence):

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*

  // Registered first, runs last (LIFO)
  scope.defer { throw Exception("Error 1") }

  // Registered second, runs first
  scope.defer { throw Exception("Error 2") }

  // Registered third, runs first
  scope.defer { throw Exception("Error 3") }
}

// Errors list order: [Error 3, Error 2, Error 1]
println(s"First error: ${fin.errors.head.getMessage}")
println(s"Total errors: ${fin.errors.length}")
```

## Use Cases

### Conditional Error Handling

Check if errors occurred and handle them appropriately:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*

  scope.defer {
    println("Cleanup complete")
  }
}

if (fin.nonEmpty) {
  fin.orThrow()
} else {
  println("No errors during finalization")
}
```

### Combining Multiple Error Sources

Attach cleanup errors to an existing error:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

def doWork(): Unit = {
  Scope.global.scoped { scope =>
    import scope.*

    try {
      throw Exception("Work failed")
    } catch {
      case workError: Exception =>
        val fin = scope.defer { throw Exception("Cleanup failed") }
        val combined = fin.suppress(workError)
        throw combined
    }
  }
}

try {
  doWork()
} catch {
  case e: Exception =>
    println(s"Work: ${e.getMessage}")
    e.getSuppressed.foreach(s => println(s"During cleanup: ${s.getMessage}"))
}
```

### Logging All Cleanup Errors

Inspect and log all errors without stopping execution:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val fin = Scope.global.scoped { scope =>
  import scope.*

  scope.defer { throw Exception("Error 1") }
  scope.defer { throw Exception("Error 2") }
}

if (fin.nonEmpty) {
  println(s"Finalization collected ${fin.errors.length} errors:")
  fin.errors.foreach(e => println(s"  - ${e.getMessage}"))
}
```

## Relationship to Scope

`Finalization` is returned by:

- `Scope.open().close()` — when explicitly closing a scope
- `Scope.global.runFinalizers()` — when running global finalizers on shutdown

## See Also

- [`Scope.defer`](./scope-reference.md#registering-finalizers) — registers finalizers that produce errors
- [`DeferHandle`](./defer-handle.md) — handle for cancelling finalizers
- [`Finalizer`](./finalizer.md) — the trait for registering cleanup actions
