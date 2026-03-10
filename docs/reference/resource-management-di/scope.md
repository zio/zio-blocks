---
id: scope
title: "Scope"
---

`zio.blocks.scope` is a **compile-time safe, zero-cost** resource management library for **Scala 3** (and Scala 2.13). It prevents a large class of lifetime bugs by tagging allocated values with an *unnameable*, scope-specific type and restricting how those values may be used.

Each scope instance has a distinct `$[A]` type that is unique to that scope and cannot be named or manipulated directly. This means values allocated in one scope have a structurally incompatible type from values in another scope — attempting to use a resource outside its owning scope is a **compile-time type error**, not a runtime crash.

`Scope`:
- allocates resources eagerly (no lazy thunks)
- registers finalizers in a stack-like registry
- runs finalizers deterministically in LIFO order when a scope closes
- collects finalizer failures instead of stopping cleanup
- enforces structured concurrency with parent-child relationships on the JVM

```scala
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific {
  type $[+A]
  type Parent <: Scope
  val parent: Parent
  def isClosed: Boolean
  def isOwner: Boolean
}
```

## Overview

`Scope` is the foundation for safe resource management in ZIO Blocks. If you're new to Scope, start with the [Scope Tutorial](../../guides/scope-tutorial.md), which provides a comprehensive step-by-step introduction with realistic examples and explanations of core concepts. This reference page documents the API details and common errors.

## Installation

For Scala 3.x and Scala 2.13.x:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "<version>"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-scope" % "<version>"
```

## Creating Scopes

### Scope.global (predefined, no close)

```scala
object Scope:
  object global extends Scope
```

Properties:

- `type $[+A] = A` (identity)
- `isOwner` always returns `true`
- JVM: finalizers run at shutdown via a shutdown hook
- Scala.js: shutdown hook is not available

### scoped {} (lexical child scope)

```scala
def scoped[A](f: (child: Scope.Child[this.type]) => A)(using Unscoped[A]): A
```

Creates a child scope that closes when the block exits. On the JVM, the child is owned by the thread that creates it.

### open() (non-lexical child scope)

```scala
def open(): $[Scope.OpenScope]

case class OpenScope(scope: Scope, close: () => Finalization)
```

Creates an unowned scope that can be closed explicitly from any thread. The `close()` function detaches from the parent, runs all child finalizers in LIFO order, and returns a `Finalization` result.

## Core Types

### Scope

```scala
sealed abstract class Scope extends Finalizer with ScopeVersionSpecific
```

Associated types and hierarchy:

- `type $[+A]` — scope-specific ownership tag
- `type Parent <: Scope` — parent scope type
- `val parent: Parent` — reference to parent scope
- `def isClosed: Boolean` — whether scope has closed
- `def isOwner: Boolean` — whether current thread owns this scope (JVM only)

Core operations:

```scala
def scoped[A](f: (child: Scope.Child[this.type]) => A)(using Unscoped[A]): A

def allocate[A](resource: Resource[A]): $[A]
def allocate[A <: AutoCloseable](value: => A): $[A]

// N=1 (infix available: `scope $ sa`)
infix transparent inline def $[A, B](sa: $[A])(inline f: A => B): B | $[B]

// N=2..5 (unqualified syntax: `$(sa1, sa2)(f)` after `import scope.*`)
transparent inline def $[A1, A2, B](sa1: $[A1], sa2: $[A2])(inline f: (A1, A2) => B): B | $[B]
transparent inline def $[A1, A2, A3, B](sa1: $[A1], sa2: $[A2], sa3: $[A3])(inline f: (A1, A2, A3) => B): B | $[B]
transparent inline def $[A1, A2, A3, A4, B](sa1: $[A1], sa2: $[A2], sa3: $[A3], sa4: $[A4])(inline f: (A1, A2, A3, A4) => B): B | $[B]
transparent inline def $[A1, A2, A3, A4, A5, B](sa1: $[A1], sa2: $[A2], sa3: $[A3], sa4: $[A4], sa5: $[A5])(inline f: (A1, A2, A3, A4, A5) => B): B | $[B]

def lower[A](value: parent.$[A]): $[A]

override def defer(f: => Unit): DeferHandle

def open(): $[Scope.OpenScope]

inline def leak[A](inline sa: $[A]): A
```

Notes:

- `$` (all arities) requires a **lambda literal** and enforces safe receiver-only usage at compile time.
- `$` returns `B` if `Unscoped[B]` exists; otherwise returns `$[B]`.
- N=1 is `infix`; N≥2 are not — use unqualified syntax after `import scope.*`.
- For N>5, call `$` once per resource and combine the resulting plain (Unscoped) values.
- If the scope is closed, `$`, `allocate`, and `open` throw `IllegalStateException` with a detailed error message. `defer` and `lower` are unaffected.

Syntax enrichments available after `import scope.*` inside a scope:

```scala
implicit class ScopedResourceOps[A](sr: $[Resource[A]]):
  def allocate: $[A]

implicit class ResourceOps[A](r: Resource[A]):
  def allocate: $[A]
```

### Resource[+A]

```scala
sealed trait Resource[+A]:
  def map[B](f: A => B): Resource[B]
  def flatMap[B](f: A => Resource[B]): Resource[B]
  def zip[B](that: Resource[B]): Resource[(A, B)]
```

Companion constructors:

```scala
object Resource:
  def apply[A](value: => A): Resource[A]
  def fromAutoCloseable[A <: AutoCloseable](thunk: => A): Resource[A]
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
  def shared[A](f: Scope => A): Resource[A]
  def unique[A](f: Scope => A): Resource[A]

  inline def from[T]: Resource[T]
  inline def from[T](inline wires: Wire[?, ?]*): Resource[T]
```

Notes:

- `Resource.from[T]` (no args) only works when `T` has **no non-scope dependencies** (constructor params may include `Scope`/`Finalizer`).
- Use `Resource.from[T](wires*)` to provide/override dependencies and derive the full graph.

### Finalizer

```scala
trait Finalizer:
  def defer(f: => Unit): DeferHandle
```

A minimal capability interface for registering cleanup. Also available as a package-level helper:

```scala
def defer(finalizer: => Unit)(using fin: Finalizer): DeferHandle
```

### DeferHandle

```scala
abstract class DeferHandle:
  def cancel(): Unit
```

- `cancel()` is thread-safe and idempotent
- cancellation is O(1) (true removal from a concurrent map)

### Finalization

```scala
final class Finalization(val errors: zio.blocks.chunk.Chunk[Throwable]):
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def orThrow(): Unit
  def suppress(initial: Throwable): Throwable

object Finalization:
  val empty: Finalization
  def apply(errors: Chunk[Throwable]): Finalization
```

### Wire[-In, +Out]

```scala
sealed trait Wire[-In, +Out]:
  def isShared: Boolean
  def isUnique: Boolean = !isShared

  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]

  def toResource(deps: zio.blocks.context.Context[In]): Resource[Out]
```

Wires:

```scala
object Wire:
  final case class Shared[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out]
  final case class Unique[-In, +Out](makeFn: (Scope, Context[In]) => Out) extends Wire[In, Out]

  def apply[T](t: T): Wire.Shared[Any, T]

  transparent inline def shared[T]: Wire.Shared[?, T]
  transparent inline def unique[T]: Wire.Unique[?, T]
```

Notes:

- `Wire(t)` wraps a pre-existing value; if it's `AutoCloseable`, `close()` is registered automatically when used.

### Unscoped[A]

```scala
trait Unscoped[A]

object Unscoped:
  inline given derived[A](using scala.deriving.Mirror.Of[A]): Unscoped[A]
  // plus many built-in givens (primitives, collections, time, UUID, Chunk, ...)
```

## Common runtime errors (and what they mean)

These `IllegalStateException`s are thrown when a scope operation is attempted on a closed scope. Each message identifies the scope type, explains what went wrong, lists common causes, and shows a correct usage example.

### `allocate` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot allocate resource: scope is already closed.

  Scope: Scope.Child

  What happened:
    A call to allocate was made on a scope whose finalizers have
    already run. The resource was never acquired.

  Common causes:
    • A scope reference escaped a scoped { } block (e.g. stored in a
      field, captured in a Future or passed to another thread).
    • close() was called on an OpenScope before all
      allocations inside it completed.

  Fix:
    Call allocate only inside a live scoped { } block, or before
    calling close() on an OpenScope.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val db = allocate(Resource(new Database))
      $(db)(_.query("SELECT 1"))
    }

────────────────────────────────────────────────────────────────────────────────
```

### `open()` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot open child scope: scope is already closed.

  Scope: Scope.Child

  What happened:
    A call to open() was made on a scope whose finalizers have
    already run. No child scope was created.

  Common causes:
    • A scope reference escaped a scoped { } block and open()
      was called after the block exited.
    • close() was called on the parent OpenScope before
      open() was called on it.

  Fix:
    Call open() only on a live (not yet closed) scope.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val child = open()
      $(child)(_.scope.allocate(Resource(new Database)))
    }

────────────────────────────────────────────────────────────────────────────────
```

### `$` on a closed scope

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot access scoped value: scope is already closed.

  Scope: Scope.Child

  What happened:
    The $ operator was called on a scope whose finalizers have
    already run. The underlying resource may have been released.
    Accessing it would be undefined behavior.

  Common causes:
    • A $[A] value or its owning scope escaped a scoped { }
      block (e.g. captured in a Future, stored in a field, or
      passed to another thread).
    • close() was called on an OpenScope that still has
      live $[A] values being accessed.

  Fix:
    Ensure all $ calls occur strictly within the scoped { }
    block that owns the value, and that the scope has not been closed.

    // Correct usage:
    Scope.global.scoped { scope =>
      import scope.*
      val db = allocate(Resource(new Database))
      $(db)(_.query("SELECT 1"))  // $ used inside the block
    }

────────────────────────────────────────────────────────────────────────────────
```

---

## Common compile errors (and what they mean)

This module produces two kinds of compile-time feedback:

- **Plain macro aborts** for unsafe `$` usage
- **ASCII-rendered** errors/warnings for DI derivation + leak warnings (via `internal.ErrorMessages`)

### Unsafe use inside `$`

All messages name the offending parameter by its 1-based index and source name, and end with the receiver-only reminder. Typical messages:

```
Parameter 1 ('d') cannot be passed as an argument to a function or method.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```
Parameter 1 ('d') must only be used as a method receiver.
It cannot be returned, stored, passed as an argument, or captured.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```
Parameter 1 ('d') cannot be captured in a nested lambda, def, or anonymous class.
Scoped values may only be used as a method receiver (e.g., d.method()).
```

```
Parameter 2 ('cache') cannot be passed as an argument to a function or method.
Scoped values may only be used as a method receiver (e.g., cache.method()).
```

```
$ requires a lambda literal, e.g. $(x)(a => a.method()).
Method references and variables are not supported.
```

### Not a class (`Wire.shared/unique` on a trait / abstract)

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot derive Wire for MyTrait: not a class.

  Hint: Use Wire.Shared / Wire.Unique directly.

───────────────────────────────────────────────────────────────────────────────
```

### No primary constructor

```
── Scope Error ─────────────────────────────────────────────────────────────────

  MyType has no primary constructor.

  Hint: Use Wire.Shared / Wire.Unique directly
        with a custom construction strategy.

───────────────────────────────────────────────────────────────────────────────
```

### `Resource.from[T]` used when `T` has dependencies

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Resource.from[MyService] cannot be derived.

  MyService has dependencies that must be provided:
    • Config
    • Logger

  Hint: Use Resource.from[MyService](wire1, wire2, ...)
        to provide wires for all dependencies.

───────────────────────────────────────────────────────────────────────────────
```

### Unmakeable type (primitives, functions, collections)

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot auto-create String

  This type (primitive, collection, or function) cannot be auto-created.

  Required by:
  ├── Config
    └── App

  Fix: Provide Wire(value) with the desired value:

    Resource.from[...](
      Wire(...),  // provide a value for String
      ...
    )

───────────────────────────────────────────────────────────────────────────────
```

### Abstract type (trait / abstract class dependency)

```
── Scope Error ─────────────────────────────────────────────────────────────────

  Cannot auto-create Logger

  This type is abstract (trait or abstract class).

  Required by:
  └── App

  Fix: Provide a wire for a concrete implementation:

    Resource.from[...](
      Wire.shared[ConcreteImpl],  // provides Logger
      ...
    )

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate providers (ambiguous wires)

```
── Scope Error ────────────────────────────────────────────────────────────────

  Multiple providers for Service

  Conflicting wires:
    1. LiveService
    2. TestService

  Hint: Remove duplicate wires or use distinct wrapper types.

───────────────────────────────────────────────────────────────────────────────
```

### Dependency cycle

```
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency cycle detected

  Cycle:
    ┌───────────┐
    │           ▼
    A ──► B ──► C
    ▲           │
    └───────────┘

  Break the cycle by:
    • Introducing an interface/trait
    • Using lazy initialization
    • Restructuring dependencies

───────────────────────────────────────────────────────────────────────────────
```

### Subtype conflict (related dependency types)

```
── Scope Error ────────────────────────────────────────────────────────────────

  Dependency type conflict in MyService

  FileInputStream is a subtype of InputStream.

  When both types are dependencies, Context cannot reliably distinguish
  them. The more specific type may be retrieved when the more general
  type is requested.

  To fix this, wrap one or both types in a distinct wrapper:

    case class WrappedInputStream(value: InputStream)
  or
    opaque type WrappedInputStream = InputStream

───────────────────────────────────────────────────────────────────────────────
```

### Duplicate parameter types in a constructor

```
── Scope Error ────────────────────────────────────────────────────────────────

  Constructor of App has multiple parameters of type String

  Context is type-indexed and cannot supply distinct values for the same type.

  Fix: Wrap one parameter in an opaque type to distinguish them:

    opaque type FirstString = String
  or
    case class FirstString(value: String)

───────────────────────────────────────────────────────────────────────────────
```

### Leak warning

```
── Scope Warning ───────────────────────────────────────────────────────────────

  leak(db)
       ^
       |

  Warning: db is being leaked from scope zio.blocks.scope.Scope.Child[...].
  This may result in undefined behavior.

  Hint:
     If you know this data type is not resourceful, then add an Unscoped
     instance for it so you do not need to leak it.

───────────────────────────────────────────────────────────────────────────────
```

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Basic database connection lifecycle management**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runDatabaseExample"
```

**Using scoped values within for-comprehensions**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ScopedForComprehensionExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ScopedForComprehensionExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.scopedForComprehensionExample"
```

**Managing a connection pool with multiple allocations**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.connectionPoolExample"
```

**Handling temporary file resources with automatic cleanup**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.tempFileHandlingExample"
```

**Managing database transactions with commit/rollback semantics**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runTransactionBoundaryExample"
```

**Implementing an HTTP client pipeline with request/response interceptors**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.httpClientPipelineExample"
```

**Managing a shared, cached logger across multiple services**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runCachingExample"
```

**Building a layered web service with dependency injection**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.layeredWebServiceExample"
```

**Reading configuration from a file with scope management**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runConfigReaderExample"
```

**Implementing a plugin architecture with automatic resource discovery**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.pluginArchitectureExample"
```

**Demonstrating thread ownership enforcement in scope hierarchies**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runThreadOwnershipExample"
```

**Detecting and demonstrating circular dependency scenarios**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.circularDependencyDemoExample"
```

**Using scope with legacy libraries that don't support managed resources**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.legacyLibraryInteropExample"
```

**Integration testing with automatic setup and teardown**

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.IntegrationTestHarnessExample"
```
