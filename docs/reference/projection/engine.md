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

`ProjectionEngine` is the runtime that turns a `Projection[A]` descriptor into a live read model. It reads events from `EventStore` instances, calls each projection's handlers, and writes the resulting state into `ProjectionStore` instances — SQLite files on JVM, or in-memory maps when SQLite is unavailable or during tests. On startup it validates schemas and rebuilds any store whose schema has drifted; once running it maintains one catch-up fiber and one live-streaming fiber per source binding, keeping every read model up to date. See the [overview](./index.md) for how `EventStore`, `ProjectionStore`, and `TestEngine` fit alongside it.

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

**`make`** validates each projection's name and base path, then creates a primary `ProjectionStore` for each spec: `global/<name>.db` for global projections and `<basePath>/<name>.db` otherwise. If the SQLite JDBC driver is absent or the file cannot be opened, the engine falls back to an `InMemoryProjectionStore` without failing — data is lost on restart in that case. The created engine has no event stores; it cannot process any events until they are supplied. Use `makeWithStores` or `makeWithStoresVarargs` to give it event stores.

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
- **`rebuildParallelism`** — the batch size used by the background pre-warm when `lazyRebuild` is enabled: the pre-warm rebuilds pending projections in sequential batches of this size.
- **`lazyRebuild`** — when `false` (the default), any projection whose schema has changed is fully rebuilt during `start`, blocking initialization until the replay completes. When `true`, stale projections are only marked as pending at startup, then rebuilt on their first `query` call or in the background by the pre-warm fiber. Lazy rebuild shortens startup at the cost of a potential brief stall on the first read.
- **`evolution`** — fine-grained control over the schema-evolution strategy; see the `SchemaEvolutionConfig` signature in the [Schema Evolution](#schema-evolution) section.

## Starting and Stopping

Call `ProjectionEngine#start` inside a `Scope` to bring the engine to life:

```scala
final class ProjectionEngine private[projection] (...) {
  def start: ZIO[Scope, Nothing, Unit]
}
```

`start` blocks until catch-up replay has finished for all source bindings, then forks a live-streaming fiber for each one and returns. The live fibers run until the enclosing `Scope` closes. There is no separate `stop` method; closing the scope is how the engine is stopped.

The method performs three phases in order:

1. **Schema checks** — for each projection, `start` computes the current `SchemaHash` and compares it with the value stored in the primary store. If the hashes differ and `lazyRebuild` is `false`, the engine immediately attempts a migration shortcut (an `ALTER TABLE ADD COLUMN` for simple field additions) or falls back to a full event replay. If `lazyRebuild` is `true`, the projection is only marked pending; no rebuild happens yet.

2. **Catch-up replay** — for each source binding, the engine starts a background task that reads the events it has not processed yet. It begins right after the last event it saved a record of, the watermark, and works through the stored events in batches. When it has read to the end of the stored events, the task finishes.

3. **Live streaming** — only after catch-up completes does the engine subscribe to the event store's live hub. This sequencing prevents a race where a live event arrives while catch-up is still processing the same region of history. The live fiber runs until the scope closes.

When `lazyRebuild` is enabled, a fourth background fiber runs concurrently with the source bindings: it finds every pending spec and rebuilds them sequentially in batches of `config.rebuildParallelism`, so that reads do not have to trigger rebuilds once the pre-warm has finished.

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

## Schema Evolution

The projection engine detects schema changes automatically and rebuilds or migrates stores without manual intervention. Three supporting objects — `TagResolver`, `SchemaHash`, and `SchemaEvolution` — implement this internally; `registerMigration` is the only engine method most users interact with.

### Event Tag Aliases — `TagResolver` and `TagInfo`

`TagResolver` and `TagInfo` are used internally by event stores to handle renamed case classes. You do not call them directly; the event store calls them when it is created, using whatever `Migration` you supplied.

```scala
final case class TagInfo(
  aliases: Map[String, String],  // old tag → current tag (chains already followed)
  allTags: Set[String],          // current + all old tags
  currentTags: Set[String],      // tags the type produces now
  migration: DynamicMigration    // rewrites old event values
) {
  def isOldTag(tag: String): Boolean
  def currentTagFor(oldTag: String): Option[String]
  def normalize(tag: String): String
  def migrateValue(dv: DynamicValue): Either[SchemaError, DynamicValue]
  def expandRequested(requested: Set[String]): Set[String]
  def unknownTags(distinctDbTags: Set[String]): Set[String]
}

trait TagResolver[E] {
  def tagInfo: TagInfo
  def aliases: Map[String, String]
  def allTags: Set[String]
  def currentTags: Set[String]
}
```

`TagInfo` records both the current tags and all old names left behind by renames. When an event store reads old events from a database, it calls `expandRequested` to include aliases in the SQL tag filter and `normalize` to compare tags without caring when the event was written. Rename chains are resolved transitively at construction time: if case `A` was renamed to `B` and `B` to `C`, the `aliases` map maps `A` directly to `C`.

The companion functions build a `TagInfo` from a `Schema[E]` and an optional migration. In Scala 2 use `resolveImplicit`; in Scala 3, `resolveAuto` summons the migration at the call site via `inline`:

```scala
object TagResolver {
  def resolveOpt[E](opt: Option[Migration[E, E]])(using Schema[E]): TagInfo
  def resolve[E: Schema](migration: Migration[E, E]): TagInfo
  def resolve[E: Schema]: TagInfo
  // Scala 2:
  def resolveImplicit[E: Schema](implicit migration: Migration[E, E] = null): TagInfo
  // Scala 3:
  inline def resolveAuto[E](using Schema[E]): TagInfo
}
```

### Schema Change Detection — `SchemaHash`

`SchemaHash` is an internal helper used by `ProjectionEngine` during startup and lazy-rebuild checks. You rarely call it directly.

```scala
object SchemaHash {
  def compute[A: Schema]: String
}
```

`SchemaHash#compute` produces a stable SHA-256 fingerprint of a schema structure. It recurses into record fields — capturing field names, types, and their order in the declaration — so a field addition, removal, rename, or type change all produce a different hash. Nested records are traversed fully. For variants, case names and their field schemas are included. The engine stores this hash alongside each projection's rows; on the next startup, if the stored hash no longer matches the computed one, the store is stale and needs rebuilding.

### Rebuild Logic — `SchemaEvolution`

When you change the shape of a read model, for example by adding a field to `UserProfile`, the rows already stored on disk no longer match your code. `SchemaEvolution` is the part of the library that fixes this. The engine uses it for you at startup, and you normally never call it yourself.

It looks at each projection and chooses one of three outcomes:

- **Nothing changed.** The stored fingerprint of the schema is the same as the current one, so nothing needs to be done.
- **Only new fields were added, and you told the engine about them.** If you registered a migration that only adds fields, the engine adds the matching columns to the existing table and saves the new fingerprint. No event is read again, so this is quick.
- **Anything else changed, or no migration was registered.** The engine rebuilds the read model from scratch. It empties the table, sets the watermark back to the start, replays every stored event through your handlers, and then saves the new fingerprint.

The rebuild can be done at any time, because the events are the source of truth and the read model is only a view of them. It is slower than the shortcut, because it has to replay the whole history.

The methods behind this are listed below. You can skip them unless you are writing your own runner:

```scala
object SchemaEvolution {
  def needsRebuild[A: Schema](store: ProjectionStore[A]): Task[Boolean]

  def rebuild[A](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    currentHash: String
  ): Task[Unit]

  def tryMigrationShortcut[A: Schema: EntityPath](
    store: ProjectionStore[A],
    migrationOpt: Option[Migration[?, A]],
    currentHash: String
  ): Task[Boolean]

  def checkAndRebuild[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    config: SchemaEvolutionConfig = SchemaEvolutionConfig.default,
    migrationOpt: Option[Migration[?, A]] = None
  ): Task[Boolean]
}
```

- `needsRebuild` answers whether the stored fingerprint is different from the current one. It answers `false` for a store that has no saved fingerprint yet, such as a brand new one.
- `rebuild` does the full rebuild described above. If one event cannot be applied while replaying, the error is logged and the replay goes on with the next event.
- `tryMigrationShortcut` tries the quick route. It returns `true` when it handled the change and `false` when it could not, for example because there is no migration or the migration does more than add top-level fields. For each new field it adds a column, with the type of the field, or with a plain text type when it cannot work the type out. A column that fails to be added is logged as a warning and skipped.
- `checkAndRebuild` is the one that ties it together. It compares the fingerprints, tries the shortcut first, and falls back to `rebuild`. It returns `true` when it changed the store, either way.

`SchemaEvolutionConfig` holds the settings:

```scala
final case class SchemaEvolutionConfig(
  rebuildParallelism: Int        = 4,
  lazyRebuild: Boolean           = false,
  enableMigrationShortcut: Boolean = true
)
```

- `enableMigrationShortcut` turns the quick route on or off. When it is `false`, every schema change is handled with a full rebuild, even if a migration is registered.
- `lazyRebuild` and `rebuildParallelism` are for the engine's lazy rebuild, described under `start`: with `lazyRebuild` on, a changed projection is only marked at startup and rebuilt later, and `rebuildParallelism` is how many are rebuilt in each batch, one batch after the other.

### Registering a Migration

To give the engine a migration so it can attempt the `ALTER TABLE` shortcut instead of a full rebuild, call `registerMigration` on the running engine before the next schema check triggers:

```scala
final class ProjectionEngine private[projection] (...) {
  def registerMigration[Old, New](
    spec: Projection[New],
    migration: Migration[Old, New]
  ): Task[Unit]
}
```

The migration is stored in an internal registry keyed by projection name. When the engine detects a schema change for that projection, it retrieves the migration and passes it to `SchemaEvolution.tryMigrationShortcut`. If the migration contains only `AddField` actions targeting top-level fields, the shortcut runs `store.addColumn` for each new column and updates the stored hash — no event replay needed. Any other migration type, or any shortcut failure, causes a full rebuild.

A typical usage pairs `registerMigration` with a schema change that adds an optional field:

```scala
val migration = Migration.newBuilder[UserV1, UserV2]
  .addField(_.age, 0L)
  .build

for {
  _ <- engine.registerMigration(userSpec, migration)
  _ <- engine.start
} yield ()
```

Registering the migration before calling `start` ensures the shortcut is available during the schema check phase.

## Connection Management

`TransactorCache` is the LRU pool of open SQLite connections that backs all SQLite stores. `ProjectionEngine` creates and manages its own cache internally; you do not need to construct one yourself unless you are integrating with `makeWithStores` directly.

```scala
trait TransactorCache {
  def get(path: String): Task[Transactor]
  def evict(path: String): Task[Unit]
  def close: Task[Unit]
  def size: Task[Int]
}

final case class TransactorCacheConfig(maxSize: Int = 256)

object TransactorCache {
  def make(maxSize: Int = 256): ZIO[Scope, Nothing, TransactorCache]
  def makeUnscoped(maxSize: Int = 256): UIO[TransactorCache]
  def live(maxSize: Int = 256): ZLayer[Scope, Nothing, TransactorCache]
  def liveConfig(config: TransactorCacheConfig): ZLayer[Scope, Nothing, TransactorCache]
}
```

`TransactorCache#get` returns a cached `Transactor` for the given file path, opening a new one if the path has not been seen before. When the cache reaches `maxSize`, the least-recently-used entry is evicted and its connection is closed. `TransactorCache#evict` removes a single entry explicitly — useful if a database file is deleted or replaced. `TransactorCache#close` shuts down all open connections, which `make` arranges automatically through the enclosing `Scope`.

When a new connection is opened, the cache enables Write-Ahead Logging and configures two additional pragmas:

- `PRAGMA journal_mode=WAL` — concurrent readers do not block writers and are never blocked by them.
- `PRAGMA synchronous=NORMAL` — writes are durable at WAL checkpoints rather than on every commit, trading a small data-loss window on power failure for meaningfully better write throughput.
- `PRAGMA busy_timeout=5000` — when the database is locked the connection waits up to five seconds before returning an error, preventing spurious failures under brief write contention.

`makeUnscoped` is available when you need a cache whose lifetime you manage manually. The `live` and `liveConfig` ZLayer constructors wrap `make` for ZIO dependency injection.

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
- [SQL Module](../sql/index.md) — the `Transactor` and `DbCon` primitives that back `SQLiteEventStore` and `SQLiteProjectionStore`; also the source of `SqlNameMapper` and the DDL helpers used for per-entity store creation.
