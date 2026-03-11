---
id: defer-handle
title: "DeferHandle"
---

`DeferHandle` is a handle returned by `Scope.defer` that allows cancelling a registered finalizer before the scope closes.

```scala
abstract class DeferHandle {
  def cancel(): Unit
}
```

When `scope.defer(cleanup)` is called, the cleanup action is registered and a `DeferHandle` is returned. This handle can be used to remove that finalizer early, preventing it from running when the scope closes. This is useful when a resource is explicitly released before the scope ends, and running the finalizer again would be unnecessary or harmful.

## Core Method

### `cancel(): Unit`

```scala
def cancel(): Unit
```

Removes the registered finalizer so it will not run when the scope closes. This method is:

- **Thread-safe**: can be called from any thread without synchronization
- **Idempotent**: calling it multiple times has the same effect as calling once
- **O(1)**: cancellation is a simple removal from an internal map (not O(n))

If the scope has already closed (and the finalizer has already run or been discarded), calling `cancel()` is a no-op.

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.io.ByteArrayOutputStream

Scope.global.scoped { scope =>
  import scope.*

  val buffer = scope.allocate(ByteArrayOutputStream())
  val closeHandle = scope.defer {
    println("Auto-closing buffer")
  }

  // Manually close the buffer
  buffer(b => b.close())

  // Cancel the automatic finalizer since we already closed it
  closeHandle.cancel()
}
```

## Use Cases

### Preventing Duplicate Cleanup

When a resource is explicitly released before the scope ends, cancel the automatic finalizer to avoid duplicate cleanup:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.io.ByteArrayOutputStream

Scope.global.scoped { scope =>
  import scope.*

  val buffer = scope.allocate(ByteArrayOutputStream())

  val finalizeHandle = scope.defer {
    buffer(b => println(s"Finalizer running, buffer closed"))
  }

  // Explicit cleanup
  buffer(b => {
    b.write("data".getBytes)
    b.close()
    println(s"Manual close: buffer has ${b.size()} bytes")
  })

  // Cancel the automatic finalizer
  finalizeHandle.cancel()
}
```

### Conditional Cleanup

Cancel finalizers based on runtime conditions:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

def acquireResource(shouldCleanup: Boolean) = Scope.global.scoped { scope =>
  import scope.*

  val resource = "important resource"
  val handle = scope.defer {
    println("Cleaning up resource")
  }

  if (!shouldCleanup) {
    handle.cancel()
    println("Cleanup disabled")
  }

  resource
}

acquireResource(shouldCleanup = false)
acquireResource(shouldCleanup = true)
```

### Transferring Ownership

When transferring a resource to external management, cancel its finalizer so the external system can control cleanup:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.io.ByteArrayInputStream

class ExternalResourceManager {
  var resources = List[java.io.InputStream]()

  def adopt(stream: java.io.InputStream): Unit = {
    resources = stream :: resources
  }

  def closeAll(): Unit = {
    resources.foreach(_.close())
  }
}

Scope.global.scoped { scope =>
  import scope.*

  val manager = ExternalResourceManager()
  val stream = scope.allocate(ByteArrayInputStream("data".getBytes))
  val handle = scope.defer {
    stream(s => println("Scope finalizer would close stream"))
  }

  // Transfer ownership to external manager
  stream(s => {
    manager.adopt(s)
    handle.cancel()  // Let the manager handle cleanup
  })
}
```

## Performance Characteristics

- **Creation**: O(1) — `defer()` adds a single entry to an internal concurrent map
- **Cancellation**: O(1) — `cancel()` removes the entry from the map
- **Scope Closure**: O(n) — finalizers run sequentially in LIFO order

The concurrent map used for storing finalizers ensures that cancellation is fast and doesn't require scanning the entire list.

## Noop Handle

When `defer()` is called on an already-closed scope, a no-op handle is returned:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

val scope = Scope.global

val handle = scope.defer {
  println("Will not run")
}

// Subsequent calls to cancel() are no-ops
handle.cancel()
```

## Thread Safety

`DeferHandle` is thread-safe. Multiple threads can call `cancel()` on the same handle without external synchronization:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

Scope.global.scoped { scope =>
  import scope.*

  val handle = scope.defer {
    println("Finalizer")
  }

  val futures = (1 to 5).map { i =>
    Future {
      handle.cancel()
      println(s"Thread $i cancelled")
    }
  }

  // Wait for all threads to finish
  scala.concurrent.Await.ready(
    Future.sequence(futures),
    scala.concurrent.duration.Duration(5, "seconds")
  )
}
```

## See Also

- [`Scope.defer`](./scope-reference.md#registering-finalizers) — the method that returns a `DeferHandle`
- [`Finalizer`](./finalizer.md) — the trait defining `defer`
- [`Finalization`](./finalization.md) — the result of running all finalizers
