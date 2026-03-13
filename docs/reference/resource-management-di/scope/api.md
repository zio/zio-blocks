---
id: scope-api
title: "Scope — API Reference"
---

## API reference (from source)

Examples below use Scala 3 syntax. Scala 2.13 has equivalent APIs, but macro signatures differ slightly (notably `$`'s return type encoding).

### `Scope.global`

The root scope instance with identity type semantics:

```scala
object Scope:
  object global extends Scope
```

Properties:

- `type $[+A] = A` (identity)
- `isOwner` always returns `true`
- JVM: finalizers run at shutdown via a shutdown hook
- Scala.js: shutdown hook is not available

---

### `Scope.OpenScope`

Represents an explicitly opened child scope:

```scala
case class OpenScope(scope: Scope, close: () => Finalization)
```

- `scope`: the child scope
- `close()`: detaches from parent, runs child finalizers (LIFO), returns `Finalization`

---

For detailed information on other types used in Scope:

- See [Finalizer](./finalizer.md) for registering cleanup functions
- See [DeferHandle](./defer-handle.md) for handle-based cancellation
- See [Finalization](./finalization.md) for error collection and finalizer results
- See [Unscoped](./unscoped.md) for typeclass definition and instance derivation

---

## Practical guidance (summary)

- Allocate in a scope: `resource.allocate` (inside `Scope.global.scoped { scope => import scope.* ... }`)
- Access one scoped value: `$(sa)(v => v.method())` — parameter can only be a receiver
- Access two or more scoped values simultaneously: `$(sa1, sa2)((v1, v2) => v1.method(v2.result()))` (N=2..5)
- For N>5: call `$` once per resource, combine the plain results
- Return only `Unscoped` data from `scoped` blocks
- Use `lower` to use parent values inside a child
- If `$` returns `$[Resource[A]]`, call `.allocate` on it (scoped resource chaining)
- Use `open()` for explicitly-managed, cross-thread capable scopes
- Use `leak` only when interop forces it; prefer `Unscoped` for pure data

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
