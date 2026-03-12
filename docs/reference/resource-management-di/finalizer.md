---
id: finalizer
title: "Finalizer"
---

`Finalizer` is a minimal capability interface for registering cleanup actions. It exposes only the `defer` method, preventing code from accessing scope internals like resource allocation or closing:

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

This trait serves as a boundary between scope management internals and user code that only needs to register cleanup actions. By exposing only `defer`, code can safely request cleanup registration without requiring full scope access.

## Overview

`Finalizer` is typically encountered in two ways:

1. **Implicit dependency**: As a context bound via `using fin: Finalizer` in user functions
2. **Via `Scope`**: Since `Scope extends Finalizer`, any scope can be used where a `Finalizer` is expected

The trait is useful for decoupling code that needs cleanup registration from code that manages the complete scope lifecycle.

## Core Method

### `defer(f: => Unit): DeferHandle`

Registers a finalizer (cleanup action) to run when the scope closes. The cleanup action runs in LIFO order along with other finalizers registered on the same scope. Returns a `DeferHandle` that can cancel the registration before the scope closes:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope.*

  val handle1 = scope.defer {
    println("Cleanup 1")
  }

  val handle2 = scope.defer {
    println("Cleanup 2")
  }

  // Finalizers run in LIFO: Cleanup 2, then Cleanup 1
  // Can cancel before scope closes
  handle1.cancel()
  // Now only Cleanup 2 runs
}
```

## Package-Level Helper

A package-level convenience function is available:

```scala
def defer(finalizer: => Unit)(using fin: Finalizer): DeferHandle
```

This allows writing `defer { cleanup }` when a `Finalizer` is in scope, rather than `fin.defer { cleanup }`:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, defer, Finalizer}

def setupWithCleanup()(using fin: Finalizer) = {
  defer {
    println("Cleanup")
  }
}

Scope.global.scoped { scope =>
  import scope.*
  setupWithCleanup()
  // Cleanup prints when scope closes
  ()  // Return unit (which is Unscoped)
}
```

## Use Cases

### Decoupling Cleanup from Scope Internals

Functions that only need to register cleanup don't need to see the full `Scope` API. They can accept a `Finalizer` parameter:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Finalizer}

def setupResource(name: String)(using fin: Finalizer): String = {
  fin.defer {
    println(s"Closing $name")
  }
  name
}

Scope.global.scoped { scope =>
  import scope.*
  setupResource("MyResource")
}
```

## Relationship to Scope

`Finalizer` is a supertrait of `Scope`:

```scala
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific
```

This means any `Scope` instance can be used where a `Finalizer` is expected. However, the converse is not true—a `Finalizer` reference does not provide `allocate`, `$`, or other scope operations.

## Finalization Order

Finalizers registered with `defer` run in **LIFO order** (last registered runs first) when the scope closes. This ensures that resources acquired in order can be cleaned up in reverse order:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope.*

  scope.defer { println("First registered, runs last") }
  scope.defer { println("Second registered, runs first") }
  // Output on scope close:
  // Second registered, runs first
  // First registered, runs last
  ()  // Return unit (which is Unscoped)
}
```

## See Also

- [`Scope`](./scope.md) — the full scope lifecycle management
- [`DeferHandle`](./defer-handle.md) — the handle returned by `defer` for cancellation
- [`Finalization`](./finalization.md) — the result of running all finalizers
