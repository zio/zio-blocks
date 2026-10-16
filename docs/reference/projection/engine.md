---
id: engine
title: "ProjectionEngine"
sidebar_label: "ProjectionEngine"
description: "ProjectionEngine executes Projection descriptors by reading events, applying handlers, and persisting read models to SQLite or in-memory stores."
keywords:
  - "Event Sourcing"
  - "Read Model Runtime"
  - "Schema Evolution"
  - "ProjectionEngine"
  - "SQLite Projections"
---

`ProjectionEngine` is the runtime that turns a `Projection[A]` descriptor into a live read model. It reads events from `EventStore` instances, calls each projection's handlers, and writes the resulting state into `ProjectionStore` instances — SQLite files on the JVM, or in-memory maps in tests. On startup it validates schemas, rebuilds any store whose schema has drifted, and catches up on the history of each source binding; once running it keeps one fiber per source binding that follows new events, keeping every read model up to date.

The structural shape of the class is:

```scala
final class ProjectionEngine private[projection] (
  val config: ProjectionEngineConfig,
  // ... private stores, shards, migration registry, semaphore
) {
  def transactorCache: TransactorCache
  def storesMap: Map[String, ProjectionStore[_]]
  def eventStoresMap: Map[String, EventStore[_]]

  def start: ZIO[Scope, Nothing, Unit]

  def query[A](spec: Projection[A], entityId: String): Task[Option[A]]
  def queryByName[A](specName: String, entityId: String): Task[Option[A]]
  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long]

  def registerMigration[Old, New](
    spec: Projection[New],
    migration: Migration[Old, New]
  ): Task[Unit]
}
```

## Creating the Engine

The companion object provides four factory methods. Two of them — `make` and `makeWithConfig` — create primary stores from the projection descriptors but supply no event stores, so the engine cannot read events until stores are wired in separately. One of them — `makeWithStores` — accepts both primary stores and event stores explicitly; the other, `makeWithStoresVarargs`, accepts event stores explicitly but creates primary stores from the specs automatically (like `make` and `makeWithConfig` do). Choose the right factory based on how you wire your event stores:

```scala
object ProjectionEngine {
  def make(specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine]

  def makeWithConfig(
    config: ProjectionEngineConfig,
    specs: Projection[_]*
  ): ZIO[Scope, Throwable, ProjectionEngine]

  def makeWithStores(
    specs: List[Projection[_]],
    stores: Map[String, ProjectionStore[_]],
    eventStores: Map[String, EventStore[_]],
    cache: TransactorCache,
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  ): UIO[ProjectionEngine]

  def makeWithStoresVarargs(
    cache: TransactorCache,
    eventStores: Map[String, EventStore[_]],
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  )(specs: Projection[_]*): Task[ProjectionEngine]
}
```

**`make`** validates each projection's name and base path, then creates a primary `ProjectionStore` for each spec: `global/<name>.db` for global projections and `<basePath>/<name>.db` otherwise. A store opens its file only when it is first used, so the engine is created even if the SQLite JDBC driver is missing or a folder does not exist; the failure shows up when the store is first used. The created engine has no event stores, and they cannot be added later, so it cannot process any events. Use `makeWithStores` or `makeWithStoresVarargs` to give it event stores.

**`makeWithConfig`** behaves identically to `make` but accepts a `ProjectionEngineConfig` for batch tuning and rebuild settings.

**`makeWithStores`** takes all stores as arguments rather than creating them. This is the factory used by `TestEngine` and by applications that manage their own `EventStore` lifecycle. An invalid projection name or base path is treated as a defect (`.orDie`), not a typed error. The return type is `UIO[ProjectionEngine]` because all failure modes are covered by the up-front validation.

**`makeWithStoresVarargs`** combines the two patterns: it accepts event stores explicitly (like `makeWithStores`) but creates primary stores from the specs automatically (like `make`). The curried signature groups configuration in the first parameter list and the projection list in the second:

```scala
for {
  cache  <- TransactorCache.make()
  engine <- ProjectionEngine.makeWithStoresVarargs(cache, Map("users" -> userEventStore))(userSpec, statsSpec)
} yield engine
```

All four factories validate that projection names are non-empty alphanumeric identifiers (letters, digits, `_`, `-`) and that base paths do not escape the storage directory (no `..`, no leading `/`). An invalid name or path causes a failure or defect depending on the factory.

## Configuration

`ProjectionEngineConfig` controls batching during catch-up replay, the live hub capacity, and the schema-evolution strategy:

```scala
final case class ProjectionEngineConfig(
  batchSize: Int                                = 100,
  batchTimeout: zio.Duration                    = 50.millis,
  ringCapacity: Int                             = 4096,
  rebuildParallelism: Int                       = 4,
  lazyRebuild: Boolean                          = false,
  evolution: SchemaEvolutionConfig              = SchemaEvolutionConfig.default
)

object ProjectionEngineConfig {
  val default: ProjectionEngineConfig = ProjectionEngineConfig()
}
```

Each field controls a distinct aspect of engine behavior:

- **`batchSize`** — the most events gathered into a single batch during catch-up. Larger values reduce per-batch overhead but increase memory usage and the window between a failure and when the watermark is saved.
- **`batchTimeout`** — how long catch-up waits to fill a batch before processing whatever has arrived. At low event rates this prevents the replay from stalling indefinitely waiting for a full batch.
- **`ringCapacity`** — the capacity of the bounded `Hub[EventEnvelope[E]]` created when `TestEngine` builds its in-memory event hub. Has no effect when an external `EventStore` already supplies its own hub.
- **`rebuildParallelism`** and **`lazyRebuild`** — whether a projection whose row type changed is rebuilt during `start` (the default) or later, and in which groups; see [Rebuilding Later](./migration.md#rebuilding-later--lazyrebuild).
- **`evolution`** — not read by the engine; see [`SchemaEvolution`](./migration.md#schemaevolution).

## Starting and Stopping

Call `ProjectionEngine#start` inside a `Scope` to bring the engine to life:

```scala
final class ProjectionEngine private[projection] (...) {
  def start: ZIO[Scope, Nothing, Unit]
}
```

`start` blocks until catch-up replay has finished for all source bindings, then forks a live-streaming fiber for each one and returns. The live fibers run until the enclosing `Scope` closes. There is no separate `stop` method; closing the scope is how the engine is stopped.

The method performs three phases in order:

1. **Schema checks** — for each projection, `start` checks whether the stored rows still match the row type, and brings them up to date if not, or leaves that to a background fiber when `lazyRebuild` is on. See [Changing the Read Model](./migration.md#changing-the-read-model).

2. **Catch-up replay** — for each source binding, `start` reads the events the projection has not processed yet. It begins right after the last event it saved a record of, the watermark, and works through the stored events in batches. It moves on only when it has read to the end of the stored events, so `start` waits for the catch-up to finish.

3. **Live streaming** — only after catch-up completes does the engine subscribe to the event store's live hub. This sequencing prevents a race where a live event arrives while catch-up is still processing the same region of history. The live fiber runs until the scope closes.

:::note
An event whose handler throws, or for which no handler matches, is logged and skipped. The watermark advances only after a successful event; a failed event is not retried once a later event succeeds.
:::

## Querying Read Models

The engine exposes three query methods on the `ProjectionEngine` class:

```scala
final class ProjectionEngine private[projection] (...) {
  def query[A](spec: Projection[A], entityId: String): Task[Option[A]]
  def queryByName[A](specName: String, entityId: String): Task[Option[A]]
  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long]
}
```

**`query`** retrieves the row for `entityId` from the primary store of `spec`. Before reading it applies any pending lazy rebuild for that projection, blocking the call until the rebuild completes. For cross-entity projections (those that use `routedBy`), if the row is not in the primary store `query` also searches all shard stores, because the entity may live in a per-routing-key shard.

**`queryByName`** looks up a row by the projection's string name rather than by a `Projection[A]` instance. It does not apply a pending lazy rebuild and does not search shards. Use it when you have the name but not the spec value, and be aware that the result type `A` is unchecked — the caller must match it to the real projection type.

**`getLastProcessedSeq`** returns the sequence number of the last event the projection has successfully applied. This is the position from which the engine would resume after a restart. A value of `0` means the projection has never processed any event.

:::caution
A read model is updated shortly after an event is appended, not synchronously. A `query` made immediately after an `append` may not yet reflect the new event. 
:::

## Connection Management

A `TransactorCache` keeps one open SQLite connection (a `Transactor`) for each database file and reuses it, so the engine does not reopen files for every event. It also limits how many files are open at once, which matters for a `CrossEntity` projection with one shard file for each routing key. It is the pool behind every SQLite store.

You usually do not create one. `ProjectionEngine.make` creates its own and `ProjectionEngine#transactorCache` returns it. Create one yourself when you build the engine with event stores, so the engine and the stores share it. A cache is created and used like this:

```scala
object TransactorCache {
  def make(maxSize: Int = 256): ZIO[Scope, Nothing, TransactorCache]
  def makeUnscoped(maxSize: Int = 256): UIO[TransactorCache]
  def live(maxSize: Int = 256): ZLayer[Scope, Nothing, TransactorCache]
  def liveConfig(config: TransactorCacheConfig): ZLayer[Scope, Nothing, TransactorCache]
}

final case class TransactorCacheConfig(maxSize: Int = 256)

trait TransactorCache {
  def get(path: String): Task[Transactor]
  def evict(path: String): Task[Unit]
  def close: Task[Unit]
  def size: Task[Int]
}
```

`maxSize` is the most database files the cache keeps open at the same time, 256 by default. Every open file holds a connection, and a process can only have so many files open, so a `CrossEntity` projection with a shard file for each routing key needs a limit. When the cache is full and a new path is asked for, it closes the least recently used file to make room. That file is not lost: its data stays on disk, and the next `get` for it opens it again, which is slower. If your projections have more active shards than `maxSize`, the cache keeps closing and reopening files, so raise it. A `maxSize` of 0 or less means no limit.

`make` closes every connection when the enclosing `Scope` ends. `makeUnscoped` is for a cache whose lifetime you manage yourself, by calling `close`. `live` and `liveConfig` wrap `make` as a `ZLayer` for dependency injection.

`get` returns the connection for a file path, opening the file if the cache has not seen it before. `evict` closes and forgets one file, which is useful when a database file is deleted or replaced. `size` is the number of files open at the moment.

When a new connection is opened, the cache enables Write-Ahead Logging and configures two additional pragmas:

- `PRAGMA journal_mode=WAL` — concurrent readers do not block writers and are never blocked by them.
- `PRAGMA synchronous=NORMAL` — writes are durable at WAL checkpoints rather than on every commit, trading a small data-loss window on power failure for meaningfully better write throughput.
- `PRAGMA busy_timeout=5000` — when the database is locked the connection waits up to five seconds before returning an error, preventing spurious failures under brief write contention.

The engine takes the cache as an argument. `makeWithStores`, described under [Creating the Engine](#creating-the-engine), is how you give an engine event stores to read from, and the cache to use. `eventStores` maps each source name, the one you pass to `from`, to its `EventStore`. `stores` maps each projection name to its primary store, which you create yourself. If an event store is a `SQLiteEventStore`, get its `Transactor` from the same `cache`, so the engine and the event stores share one limit on open files.

The next example runs the `userProfiles` projection with `makeWithStores`. One cache opens the event store file and the projection store file, and the same cache is given to the engine:

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

val projection = Projection[UserProfile]("userProfiles")
  .from("users")
  .routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val program: ZIO[Scope, Throwable, ProjectionEngine] = for {
  cache    <- TransactorCache.make(maxSize = 64)
  tx       <- cache.get("events/users.db")                                            // the event store file
  users    <- SQLiteEventStore.make[UserCreated](tx)
  profiles <- SQLiteProjectionStore.make[UserProfile]("users/userProfiles.db", cache) // the projection store file
  engine   <- ProjectionEngine.makeWithStores(
                List(projection),
                Map("userProfiles" -> profiles), // projection name -> its store
                Map("users" -> users),           // source name given to `from` -> its event store
                cache
              )
  _        <- engine.start
} yield engine
```

## Introspection

Four accessors expose the engine's runtime state for testing, debugging, and advanced direct-store access:

```scala
final class ProjectionEngine private[projection] (...) {
  val config: ProjectionEngineConfig
  def transactorCache: TransactorCache
  def storesMap: Map[String, ProjectionStore[_]]
  def eventStoresMap: Map[String, EventStore[_]]
}
```

`config` is the `ProjectionEngineConfig` the engine was created with. `transactorCache` is the shared SQLite connection pool, exposed so tests and advanced consumers can open transactors or inspect cache size directly. `storesMap` returns the primary store of each projection keyed by name; `eventStoresMap` returns the event stores keyed by source name. These maps reflect the values the engine was constructed with and do not change after construction.

## Companion utilities

The companion object also exposes three low-level utilities useful when building custom event or projection stores:

```scala
object ProjectionEngine {
  def boundedHub[E](capacity: Int = 4096): UIO[Hub[EventEnvelope[E]]]
  def validateName(name: String): Unit
  def validateBasePath(path: String): Unit
}
```

`boundedHub[E]` creates a bounded `Hub[EventEnvelope[E]]` suitable for use as a live-streaming bus when implementing a custom `EventStore`. The `capacity` defaults to 4096.

`validateName` checks that a projection name is safe to use as a file name: it must be non-empty and composed solely of letters, digits, `_`, and `-`. It throws `IllegalArgumentException` on failure.

`validateBasePath` checks that a folder path cannot escape the storage directory: it rejects empty paths, backslashes, `..` segments, double slashes, and absolute paths. It also throws `IllegalArgumentException` on failure.

## See Also

- [Projection[A]](./projection.md) — the declarative descriptor that `ProjectionEngine` executes: event sources, routing modes, handlers, and `ProjectionAction` types.
- [Migration](./migration.md) — what happens when the row type or the events change: the schema check in `start`, rebuilds, `registerMigration` and renamed events.
- [SQL Module](../sql/index.md) — the `Transactor` and `DbCon` primitives that back `SQLiteEventStore` and `SQLiteProjectionStore`; also the source of `SqlNameMapper` and the DDL helpers used for per-entity store creation.
