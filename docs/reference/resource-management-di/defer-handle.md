---
id: defer-handle
title: "DeferHandle"
---

`DeferHandle` is a handle returned by `Scope.defer` that allows cancelling a registered finalizer before the scope closes. Here is the class definition:

```scala
abstract class DeferHandle {
  def cancel(): Unit
}
```

When `Scope#defer(cleanup)` is called, the cleanup action is registered and a `DeferHandle` is returned. This handle can be used to remove that finalizer early, preventing it from running when the scope closes. This is useful when a resource is explicitly released before the scope ends, and running the finalizer again would be unnecessary or harmful.

## Core Method: `DeferHandle#cancel(): Unit`

The type signature is:

```scala
def cancel(): Unit
```

Removes the registered finalizer so it will not run when the scope closes. `DeferHandle#cancel` is:

- **Thread-safe**: Can be called from any thread without synchronization
- **Idempotent**: Calling it multiple times has the same effect as calling once
- **O(1)**: cancellation is a simple removal from an internal map (not O(n))

If the scope has already closed (and the finalizer has already run or been discarded), calling `DeferHandle#cancel` is a no-op. Here's an example:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.io.ByteArrayOutputStream

Scope.global.scoped { scope =>
  import scope.*

  val buffer = allocate(ByteArrayOutputStream())
  val closeHandle = defer {
    println("Auto-closing buffer")
    $(buffer)(_.close())
  }

  // Manually close the buffer
  $(buffer)(_.close())

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

val result = Scope.global.scoped { scope =>
  import scope.*

  val buffer = allocate(ByteArrayOutputStream())

  val finalizeHandle = defer {
    println(s"Finalizer running, buffer closing")
    $(buffer)(_.close())
  }

  // Explicit cleanup
  $(buffer) { buf =>
    buf.write("data".getBytes)
    println(s"Manual use: buffer has ${buf.size()} bytes")
    buf.close()
  }

  // Cancel the automatic finalizer
  finalizeHandle.cancel()
  
  "done"
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

val result = Scope.global.scoped { scope =>
  import scope.*

  val stream = allocate(ByteArrayInputStream("data".getBytes))
  val handle = defer {
    println("Scope finalizer would close stream")
    $(stream)(_.close())
  }

  // Transfer ownership to external manager
  // (In real code, this might pass to a thread pool or async framework)
  handle.cancel()  // Let the manager handle cleanup
  
  "transferred"
}
```

## Noop Handle

When `defer()` is called on an already-closed scope, a no-op handle is returned:

```scala mdoc:compile-only
import zio.blocks.scope.Scope

Scope.global.scoped { scope =>
  import scope.*

  val handle = scope.defer {
    println("This will run when scope closes")
  }

  // Subsequent calls to cancel() remove the finalizer
  handle.cancel()

  println("Finalizer has been cancelled")
}
```

## Thread Safety

`DeferHandle` is thread-safe. Multiple threads can call `cancel()` on the same handle without external synchronization:

```scala mdoc:compile-only
import zio.blocks.scope.Scope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

val result = Scope.global.scoped { scope =>
  import scope.*

  val handle = defer {
    println("Finalizer")
  }

  // Use a thread pool to simulate concurrent access
  val executor = Executors.newFixedThreadPool(5)
  val latch = new CountDownLatch(5)

  (1 to 5).foreach { i =>
    executor.submit(new Runnable {
      def run(): Unit = {
        handle.cancel()
        println(s"Thread $i cancelled")
        latch.countDown()
      }
    })
  }

  // Wait for all threads to finish
  latch.await()
  executor.shutdown()
  
  "completed"
}
```

## See Also

- [`Scope.defer`](./scope.md#registering-finalizers) — the method that returns a `DeferHandle`
- [`Finalizer`](./finalizer.md) — the trait defining `defer`
- [`Finalization`](./finalization.md) — the result of running all finalizers
