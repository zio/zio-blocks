---
id: finalizer
title: "Finalizer"
---

`Finalizer` is a minimal capability interface for registering cleanup actions. It exposes only the `defer` method, preventing code from accessing scope internals like resource allocation or closing.

The structural definition:

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

This trait serves as a boundary between scope management internals and user code that only needs to register cleanup actions. By exposing only `defer`, code can safely request cleanup registration without requiring full scope access.

## Motivation / Use Case

`Finalizer` integrates with `Scope` to enable resource management patterns:

```scala mdoc:compile-only
import zio.blocks.scope.{Scope, Finalizer}

def openConnection(url: String)(using fin: Finalizer): String = {
  fin.defer {
    println(s"Closing connection to $url")
  }
  s"Connected to $url"
}

Scope.global.scoped { scope =>
  import scope.*
  openConnection("https://example.com")
  // Connection closes when scope exits
  ()
}
```

By decoupling code that needs cleanup registration from code that manages the complete scope lifecycle, `Finalizer` allows functions to safely register finalizers without requiring full scope access.

## Construction / Creating Instances

`Finalizer` is not typically constructed directly. Instead, it is obtained through a scope:

### From a `Scope`

Any `Scope` instance can be used as a `Finalizer` since `Scope extends Finalizer`:

`Finalizer` is not typically constructed directly. Instead, it is obtained through a scope:

### From a `Scope`

Any `Scope` instance can be used as a `Finalizer` since `Scope extends Finalizer`:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope.*
  // scope is both a Scope and a Finalizer
  val handle = scope.defer {
    println("Cleanup")
  }
  ()  // Return unit
}
```

### As a Context Bound

Functions can request a `Finalizer` via `using` parameter, enabling decoupled cleanup registration:

```scala mdoc:compile-only
import zio.blocks.scope.Finalizer

def setupResource(name: String)(using fin: Finalizer): String = {
  fin.defer {
    println(s"Closing $name")
  }
  name
}
```

## Core Operations

### `Finalizer#defer`

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

### Package-Level `defer` Helper

A package-level convenience function allows writing `defer { cleanup }` when a `Finalizer` is in scope. The signature is:

```scala
def defer(finalizer: => Unit)(using fin: Finalizer): DeferHandle
```

This removes the need to write `fin.defer { cleanup }`. Here's the convenience function in use:

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

## Integration

### Relationship to Scope

`Finalizer` is a supertrait of `Scope`. The structural definition shows this relationship:

```scala
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific
```

This means any `Scope` instance can be used where a `Finalizer` is expected. However, the converse is not true—a `Finalizer` reference does not provide `Scope#allocate`, `Scope#$`, or other scope operations.

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
