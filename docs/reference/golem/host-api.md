---
id: host-api
title: "HostApi"
---

`HostApi` is the primary Scala.js interface for accessing Golem's runtime APIs. It provides methods to inspect and control agent execution, durability, persistence, and oplog behavior. All methods are final wrappers around the underlying WebAssembly host interface.

```scala mdoc:compile-only
object HostApi {
  def getOplogIndex(): OplogIndex
  def markBeginOperation(): OplogIndex
  def markEndOperation(begin: OplogIndex): Unit
  def getRetryPolicy(): RetryPolicy
  def setRetryPolicy(policy: RetryPolicy): Unit
  // ... many more methods
}
```

## Overview

`HostApi` is your gateway to Golem's advanced features from within agent code:

| Category | Purpose | Example |
|----------|---------|---------|
| **Oplog** | Track atomic operation boundaries | `markBeginOperation()`, `markEndOperation()` |
| **Retry Policy** | Control automatic retry behavior | `getRetryPolicy()`, `setRetryPolicy()` |
| **Persistence** | Manage oplog persistence strategy | `getOplogPersistenceLevel()`, `setOplogPersistenceLevel()` |
| **Idempotence** | Toggle idempotent request handling | `getIdempotenceMode()`, `setIdempotenceMode()` |
| **Agent Registry** | Query running agents, trigger updates | `getSelfMetadata()`, `getAgentMetadata()`, `updateAgent()` |
| **Database** | Access relational databases (MySQL, PostgreSQL) | `Rdbms` provides SQL methods |
| **Key-Value Store** | Persistent key-value storage | `KeyValue` API via `golem.wasi.KeyValue` |
| **Blobstore** | Large binary object storage | `Blobstore` API via `golem.wasi.Blobstore` |

All methods are Scala.js-only (compiled to WebAssembly).

## Core Operations: Oplog Tracking

The **oplog** (operation log) records all side effects. Mark operation boundaries to enable automatic rollback on failure:

```scala
import golem.HostApi

val begin = HostApi.markBeginOperation()
try {
  // Perform side effects: database writes, network calls, etc.
  println("Doing work...")
} catch {
  case e: Throwable =>
    // On failure, oplog rolls back to `begin`
    throw e
} finally {
  HostApi.markEndOperation(begin)
}
```

When an operation fails (throws an exception), Golem automatically:
1. Rolls back all changes recorded in the oplog
2. Restores the agent state to before `markBeginOperation()`
3. Retries the entire agent method (governed by retry policy)

## Oplog Index Management

Access the current oplog position:

```scala
import golem.HostApi

val currentIndex = HostApi.getOplogIndex()
HostApi.setOplogIndex(currentIndex) // Reset to known state (rare)
```

More commonly, use `markBeginOperation()` and `markEndOperation()` to establish atomic regions rather than manipulating indices directly.

## Retry Policy Control

Configure automatic retry behavior:

```scala
import golem.HostApi
import scala.concurrent.Future

val policy = HostApi.RetryPolicy(
  maxAttempts = 3,
  minDelayNanos = BigInt(1_000_000),      // 1ms
  maxDelayNanos = BigInt(1_000_000_000),  // 1s
  multiplier = 2.0,
  maxJitterFactor = Some(0.1)
)

HostApi.setRetryPolicy(policy)
```

The retry policy controls:
- **maxAttempts** — How many times to retry before giving up (Int)
- **minDelayNanos** — Starting delay between retries (BigInt, nanoseconds)
- **maxDelayNanos** — Maximum delay between retries (BigInt, nanoseconds)
- **multiplier** — Exponential backoff factor (Double)
- **maxJitterFactor** — Random jitter to prevent thundering herd (Option[Double])

## Persistence Level Control

Control how aggressively the oplog persists:

```scala
import golem.HostApi

// Persist nothing (fastest, least reliable)
HostApi.setOplogPersistenceLevel(HostApi.PersistenceLevel.PersistNothing)

// Persist only remote side effects (default, balanced)
HostApi.setOplogPersistenceLevel(HostApi.PersistenceLevel.PersistRemoteSideEffects)

// Smart persistence (handles most cases)
HostApi.setOplogPersistenceLevel(HostApi.PersistenceLevel.Smart)
```

Persistence levels:
- **PersistNothing** — No durability guarantees; fastest
- **PersistRemoteSideEffects** — Persist calls to external systems; balances speed and reliability
- **Smart** — Golem automatically detects what to persist

## Idempotence Mode

Enable or disable idempotent request deduplication:

```scala
import golem.HostApi

HostApi.setIdempotenceMode(true)  // Deduplicate duplicate requests
HostApi.setIdempotenceMode(false) // Allow duplicates
```

When idempotence is enabled, Golem tracks request IDs and suppresses duplicate executions (useful for at-least-once delivery patterns).

## Agent Registry Operations

Query and manage running agents:

```scala
import golem.HostApi

// Get metadata about this agent
val metadata = HostApi.getSelfMetadata()
println(s"Agent ID: ${metadata.agentId}")
println(s"Agent Type: ${metadata.agentType}")
println(s"Status: ${metadata.status}")

// Get metadata about another agent
val otherMetadata = HostApi.getAgentMetadata(agentId)

// Trigger an update (after code deployment)
HostApi.updateAgent(agentId, targetVersion, updateMode)
```

## Relational Database Access

Access MySQL and PostgreSQL databases via the resource API:

```scala mdoc:compile-only
import golem.wasi.Rdbms

// Open a connection and execute queries
val connection = Rdbms.PostgresConnection.open("postgres://localhost/mydb")
val result = connection.query("SELECT name FROM users WHERE id = $1", List("user123"))

result match {
  case Right(rows) => println(s"Found: $rows")
  case Left(error) => println(s"Error: $error")
}
```

The Rdbms API provides resource-based access to PostgreSQL and MySQL databases with synchronous Either-based error handling.

## Key-Value Store

Persistent key-value storage beyond the agent's memory using buckets:

```scala mdoc:compile-only
import golem.wasi.KeyValue

// Open a bucket and access key-value pairs
val bucket = KeyValue.Bucket.open("my-bucket")

val value: Option[Array[Byte]] = bucket.get("my-key")
bucket.set("my-key", Array(1, 2, 3))
bucket.delete("my-key")
```

The KeyValue API provides synchronous, resource-based access to persistent storage.

## Blobstore

Store and retrieve large binary objects using the resource API:

```scala mdoc:compile-only
import golem.wasi.Blobstore
import scala.scalajs.js.typedarray.Uint8Array

val data: Uint8Array = ??? // binary data to store
val objectStore = Blobstore.ObjectStore.open("my-bucket")

val objectId: String = objectStore.put(data)
val retrieved: Option[Uint8Array] = objectStore.get(objectId)
```

The Blobstore API provides synchronous, resource-based access to large binary object storage.

## Oplog Commit

Force commit of the current oplog position (advanced usage):

```scala
import golem.HostApi

HostApi.oplogCommit(replicas = 1) // Commit to 1 replica
```

Normally, Golem handles commit automatically. Use this for explicit control over persistence.

## Relation to Other Types

- **`Transactions`** — Uses `HostApi` internally to track operation boundaries and rollback
- **`HostApi` layers** — Granular APIs: `OplogApi`, `DurabilityApi`, `ContextApi` (rarely used directly)
- **`Rdbms`, `KeyValue`, `Blobstore`** — Specialized host API modules

The typical pattern is to use high-level helpers like [`Transactions`](./transactions.md) rather than calling `HostApi` directly.

## Best Practices

- **Use `Transactions` for multi-step operations** — Higher-level and handles compensation automatically
- **Mark operation boundaries carefully** — Broad regions reduce fine-grained error recovery
- **Don't manipulate oplog indices** — Use `markBeginOperation()` / `markEndOperation()` instead
- **Cache retry/persistence settings** — Set once at agent startup, not per-method
- **Test with failure scenarios** — Verify oplog rollback behaves correctly under failure
