---
id: projection-engine
title: "ProjectionEngine"
description: "The runtime that runs projections: creating an engine, start and catch-up, queries, configuration, storage files and the SQLite connection cache."
keywords:
  - "Event Sourcing"
  - "Read Model Runtime"
  - "Event Watermark"
  - "SQLite Connection Cache"
  - "ProjectionEngine"
---

`ProjectionEngine` is the **runtime of the projection module**. A [`Projection[A]`](./projection.md) only describes a read model: which sources it reads, how events are routed, and what each event does to a row. The engine is what runs those descriptions. It reads events from [`EventStore`](./event-store.md)s, runs the matching handler for each event, applies the resulting action to a [`ProjectionStore`](./projection-store.md), and answers queries from the stores. Its fundamental operations are `ProjectionEngine#start` and `ProjectionEngine#query`.

`ProjectionEngine`:

- **Resumes where it stopped.** After every event it applies, the engine saves the event's sequence number as the projection's **watermark**, the position of the last event applied. After a restart it continues with the events after the watermark.
- **Catches up before it returns.** `start` first applies the stored events after the watermark, a step called **catch-up**, and returns only when every source has finished it. Each source then gets a **live fiber**, a background ZIO fiber that applies the new events as they are appended.
- **Keeps each source in order.** The events of one source are processed one at a time, in the order they were appended. Separate sources and separate projections run on separate fibers, with no order between them.
- **Is eventually consistent.** Events appended after `start` are applied in the background, so a query right after an append may not see the change yet.
- **Runs inside a `Scope`.** A ZIO `Scope` owns resources and background fibers, and releases them when it closes. The fibers of the engine live until the `Scope` given to `start` closes. There is no stop method.

This is the API that applications run. Tests usually use [`TestEngine`](./testing.md#testengine) instead, which wires an engine on in-memory stores. Add the module and the `sqlite-jdbc` driver as described under [Installation](./index.md#installation). The module is built for the JVM only, and for Scala 3 only (3.3.7 and 3.9.0).

The class has a private constructor, so engines are created with the factories on its companion object. Its public members are:

```scala
final class ProjectionEngine private[projection] (
  // the projections, their stores, the event stores and the cache are private
  val config: ProjectionEngineConfig
  // ...
) {
  def transactorCache: TransactorCache
  def registerMigration[Old, New](spec: Projection[New], migration: Migration[Old, New]): Task[Unit]
  def start: ZIO[Scope, Nothing, Unit]
  def query[A](spec: Projection[A], entityId: String): Task[Option[A]]
  def queryByName[A](specName: String, entityId: String): Task[Option[A]]
  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long]
  def storesMap: Map[String, ProjectionStore[_]]
  def eventStoresMap: Map[String, EventStore[_]]
}
```

`Migration` is `zio.blocks.schema.migration.Migration`. The class has no type parameter: each method that needs a row type takes it as its own type parameter.

## Usage

The program below defines an event, a read model and a projection. It opens an event store and a projection store on SQLite files, creates the engine with `ProjectionEngine.makeWithStores`, and appends two events before it starts the engine. Because `start` returns only after catch-up, the queries right after it already see both rows, and the watermark is the sequence number of the second event:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example13EngineCatchUp.scala

```

It prints `user-1: Alice <alice@example.com>`, `user-2: Bob <bob@example.com>` and `Watermark: 2`. When `ZIO.scoped` ends, its `Scope` closes, so the engine's live fiber stops and the cache closes. The files stay on disk, so an engine started on them again would continue after event 2.

## Creating an Engine

The companion object has four factories. They differ in what they create for you:

- **`ProjectionEngine.makeWithStores`** takes everything: the primary store of each projection, the event stores, and a `TransactorCache`. It needs no `Scope`, and an invalid projection name or folder is a **defect**: the effect dies, instead of failing with an error that `catchAll` can handle.
- **`ProjectionEngine.makeWithStoresVarargs`** takes the event stores and a `TransactorCache`, and creates a SQLite primary store for each projection. It needs no `Scope`, and an invalid name or folder is a **typed failure**: the task fails with an `IllegalArgumentException`, which `catchAll` can handle.
- **`ProjectionEngine.make`** and **`ProjectionEngine.makeWithConfig`** take only the projections. They create the primary stores and a `TransactorCache` that is closed with the enclosing `Scope`, so they need one. An invalid name or folder is a typed failure. They give the engine no event stores, so it has nothing to read.

The **primary store** of a projection is the store the engine reads and writes for it, and where it keeps its watermark. None of the factories opens a file: a store opens its file the first time it is used, so a missing driver or folder shows up then.

### From Your Own Stores — `makeWithStores`

`ProjectionEngine.makeWithStores` builds an engine from stores you created yourself. It is the factory the [Usage](#usage) example uses, and the one `TestEngine` uses for its in-memory stores:

```scala
object ProjectionEngine {
  def makeWithStores(
    specs: List[Projection[_]],
    stores: Map[String, ProjectionStore[_]],
    eventStores: Map[String, EventStore[_]],
    cache: TransactorCache,
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  ): UIO[ProjectionEngine]
}
```

- `stores` is keyed by **projection name**, the name given to `Projection[A](name)`.
- `eventStores` is keyed by **source name**, the name given to `from`. How a source finds its store is described under [`start`](#starting-the-engine--start).
- `stores` must have an entry for every projection. This is not checked when the engine is created: a missing entry makes `start` or `query` die with a `NoSuchElementException`.
- The names and folders of the projections are checked with [`validateName` and `validateBasePath`](#companion-helpers--validatename-validatebasepath-and-boundedhub). An invalid one is a defect, which is why the result is a `UIO`.
- `cache` also opens the SQLite files of the shards of cross-entity projections. A **shard** is the store of one routing key, and a **routing key** is the value that `routedBy` reads from an event to choose the shard; see [CrossEntity](./projection.md#crossentity-one-drawer-per-group). The engine never closes the cache: close it yourself, or create it with `TransactorCache.make` inside a `Scope`.

### Creating the Primary Stores — `makeWithStoresVarargs`

`ProjectionEngine.makeWithStoresVarargs` takes the event stores, like `makeWithStores`, but creates a `SQLiteProjectionStore` for each projection itself. The projections go in a second parameter list:

```scala
object ProjectionEngine {
  def makeWithStoresVarargs(
    cache: TransactorCache,
    eventStores: Map[String, EventStore[_]],
    config: ProjectionEngineConfig = ProjectionEngineConfig.default
  )(specs: Projection[_]*): Task[ProjectionEngine]
}
```

Each primary store is the file `<basePath>/<name>.db`, where `basePath` comes from the projection's [`EntityPath`](./projection.md#entity-paths), or `global/<name>.db` for a global projection. The paths are relative to the working directory, and SQLite does not create folders, so the folders must exist before the stores are used.

```scala mdoc:invisible
// The definitions of Example13EngineCatchUp, which Usage embeds. The snippets below use them.
import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

case class UserProfile(@Modifier.id userId: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

val projection: Projection[UserProfile] = Projection[UserProfile]("user_profiles")
  .from("users")
  .routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

The snippet below runs the `projection` from [Usage](#usage) and a second projection of the same row type, each reading its own event store. Both primary stores land in `users/`, the folder derived from `UserProfile`, so the program creates `events/` and `users/` first:

```scala mdoc:compile-only
import java.nio.file.{Files, Paths}

val admins: Projection[UserProfile] = Projection[UserProfile]("admin_profiles")
  .from("admins")
  .routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val twoSources: ZIO[Scope, Throwable, ProjectionEngine] = for {
  _      <- ZIO.attemptBlocking(List("events", "users").foreach(d => Files.createDirectories(Paths.get(d))))
  cache  <- TransactorCache.make()
  users  <- cache.get("events/users.db").flatMap(tx => SQLiteEventStore.make[UserCreated](tx))
  admin  <- cache.get("events/admins.db").flatMap(tx => SQLiteEventStore.make[UserCreated](tx))
  engine <- ProjectionEngine.makeWithStoresVarargs(
              cache,
              Map[String, EventStore[?]]("users" -> users, "admins" -> admin)
            )(projection, admins)
  _      <- engine.start
} yield engine
```

The engine reads `users/user_profiles.db` and `users/admin_profiles.db`. Like `makeWithStores`, the factory registers no finalizer, so the cache here is closed by the `Scope` that `TransactorCache.make` runs in.

### Without Event Stores — `make` and `makeWithConfig`

`ProjectionEngine.make` and `ProjectionEngine.makeWithConfig` take only the projections. `make` uses `ProjectionEngineConfig.default`, and `makeWithConfig` takes the settings described under [Configuration](#configuration):

```scala
object ProjectionEngine {
  def make(specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine]
  def makeWithConfig(config: ProjectionEngineConfig, specs: Projection[_]*): ZIO[Scope, Throwable, ProjectionEngine]
}
```

They check the names and folders, create a `TransactorCache` with `TransactorCache.make()`, which is closed when the enclosing `Scope` closes, and create the primary stores at the same paths as `makeWithStoresVarargs`:

```scala mdoc:compile-only
val withoutEvents: ZIO[Scope, Throwable, Set[String]] =
  ProjectionEngine
    .makeWithConfig(ProjectionEngineConfig(batchSize = 500), projection)
    .map(_.storesMap.keySet)
```

:::caution[No events to read]
An engine created with `make` or `makeWithConfig` has no event stores, and there is no method to add them later. Its `start` logs `no EventStore for source '...' - skipping` for every source, so the engine neither catches up nor follows new events. It cannot rebuild a store after a schema change either: it keeps the old fingerprint. See [Limitations](./migration.md#limitations). To run projections on SQLite files, use `makeWithStores` or `makeWithStoresVarargs`.
:::

### Companion Helpers — `validateName`, `validateBasePath` and `boundedHub`

The companion object also has the checks the factories run, and a helper for building a hub:

```scala
object ProjectionEngine {
  def validateName(name: String): Unit
  def validateBasePath(path: String): Unit
  def boundedHub[E](capacity: Int = 4096): UIO[Hub[EventEnvelope[E]]]
}
```

- `validateName` throws an `IllegalArgumentException` unless the name is non-empty and made only of letters, digits, `_` and `-`. A projection name is also a file name, which is why it is checked.
- `validateBasePath` throws an `IllegalArgumentException` for a folder path that could leave the working directory: an empty path, a backslash, `..`, `//`, or a leading `/`.
- The factories run both on each projection: `validateName` on its name, and `validateBasePath` on the folder of its `EntityPath` and on the folder of a `@path` annotation on the row type.
- `boundedHub` creates a bounded `Hub` for live events. A ZIO `Hub` hands each message published to it to every subscriber, and an event store publishes each new event to its hub. When a bounded hub is full, publishing waits, so a slow consumer slows the producer down instead of losing events. The engine does not call it.

A hub from `boundedHub` can be given to an event store, as described under [Choosing the Hub](./event-store.md#choosing-the-hub--makewithhub):

```scala mdoc:compile-only
val smallHub: ZIO[Scope, Throwable, SQLiteEventStore[UserCreated]] = for {
  cache <- TransactorCache.make()
  tx    <- cache.get("events/users.db")
  hub   <- ProjectionEngine.boundedHub[UserCreated](capacity = 256)
  users <- SQLiteEventStore.makeWithHub[UserCreated](tx, hub)
} yield users
```

## Core Operations

An engine is started once, then queried; the operations below cover starting it, reading rows back, registering a migration, and reaching its stores.

### Starting the Engine — `start`

`ProjectionEngine#start` runs the projections and returns once they have caught up:

```scala
final class ProjectionEngine {
  def start: ZIO[Scope, Nothing, Unit]
}
```

It runs these steps in order:

1. **Schema check.** For each projection, it compares the fingerprint of the row type stored in the primary store with the current one, and brings a stale store up to date, as described under [Schema Changes](#schema-changes).
2. **Event store lookup.** For each source of each projection, it looks for the event store under the source's name, then under `"_default"`, and then takes the first store in the map. So a misspelt source name does not fail: it reads another store. With no event stores at all, it logs a warning and skips the source.
3. **Catch-up.** It reads the watermark from the primary store and applies the stored events after it, read with `EventStore#readFrom` in batches of up to `batchSize`.
4. **Live fiber.** It subscribes to the event store's `Hub`, which hands every new event to each subscriber, and forks the live fiber, which applies those events as they arrive.
5. **Pre-warm.** With `lazyRebuild` on, it forks one more fiber that rebuilds the pending projections in the background.

Steps 2 to 4 run for one source after another, not in parallel. `start` returns when every source has caught up and its live fiber is running. A source whose catch-up fails is logged with `bind failed` and skipped, and it gets no live fiber. With `lazyRebuild`, `start` does not wait for the pre-warm.

`start` has no error type: a failing source or rebuild is logged, not returned. Its fibers are forked in the `Scope` it runs in, so closing that `Scope` stops the engine. Wrap the program in `ZIO.scoped`, as [Usage](#usage) does, or run it in a `ZIOAppDefault`, which provides a `Scope` for the whole application.

### Reading Rows — `query`, `queryByName` and `getLastProcessedSeq`

Three methods read from the stores of a projection:

```scala
final class ProjectionEngine {
  def query[A](spec: Projection[A], entityId: String): Task[Option[A]]
  def queryByName[A](specName: String, entityId: String): Task[Option[A]]
  def getLastProcessedSeq[A](spec: Projection[A]): Task[Long]
}
```

- `query` returns the row with the ID `entityId`, or `None`. If the projection is waiting for a [lazy rebuild](./migration.md#rebuilding-later--lazyrebuild), the query first runs the rebuild and waits for it, and a failed rebuild fails the query. Without an event store, the projection is just unmarked, with no rebuild. The query then reads the primary store.
- For a cross-entity projection only, when the primary store has no such row, `query` searches the shards this engine has opened, in no particular order, and returns the first match. The engine opens a shard when it routes an event to it, and a read error in a shard counts as no match.
- `queryByName` reads only the primary store of the projection named `specName`, and returns `None` for an unknown name. It neither runs a pending rebuild nor searches shards, and the type `A` is not checked: the caller chooses it.
- `getLastProcessedSeq` returns the watermark. It is kept in the primary store, also for a cross-entity projection, and `0` means that no event was applied yet.

Events appended after `start` are applied by the live fibers, so a query right after an append can still return `None`. Poll until the row appears. This function appends an event and asks again every 20 milliseconds until the new row is there:

```scala mdoc:compile-only
def appendAndRead(engine: ProjectionEngine, users: EventStore[UserCreated]): Task[Option[UserProfile]] =
  users.append("user-3", UserCreated("Carol", "carol@example.com")) *>
    engine.query(projection, "user-3").delay(20.millis).repeatUntil(_.isDefined)
```

### Registering a Migration — `registerMigration`

`ProjectionEngine#registerMigration` tells the engine how the row type of a projection changed, so that it can add columns instead of rebuilding:

```scala
final class ProjectionEngine {
  def registerMigration[Old, New](spec: Projection[New], migration: Migration[Old, New]): Task[Unit]
}
```

Register it before `start`, which is where the schema check runs. The migration is kept under the projection's name, so registering another one replaces it. When it is used, and what the old rows get, is described under [Adding Fields Without a Rebuild](./migration.md#adding-fields-without-a-rebuild--registermigration).

## Configuration

`ProjectionEngineConfig` holds the settings of an engine, and `ProjectionEngineConfig.default` is the value with every default:

```scala
final case class ProjectionEngineConfig(
  batchSize: Int = 100,
  batchTimeout: zio.Duration = 50.millis,
  ringCapacity: Int = 4096,
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  evolution: SchemaEvolutionConfig = SchemaEvolutionConfig.default
)

object ProjectionEngineConfig {
  val default: ProjectionEngineConfig = ProjectionEngineConfig()
}
```

- `batchSize` is the most events that catch-up reads together. Each event of a batch is still applied, and its watermark saved, on its own, so the size does not change how much is applied again after a crash. Live events are processed one at a time.
- `batchTimeout` is how long catch-up waits for a batch to fill before it applies what it has.
- `ringCapacity` is not read by the engine. Only `TestEngine.makeWithConfig` reads it, as the capacity of the hub of each in-memory source. A `SQLiteEventStore` created with `make` has its own hub of 4,096 events; to choose another, see [Choosing the Hub](./event-store.md#choosing-the-hub--makewithhub).
- `rebuildParallelism` is the size of the groups in which the pre-warm fiber rebuilds pending projections. The groups run one after another, and so do the projections in a group, so it does not make rebuilds run in parallel. It is read only when `lazyRebuild` is on.
- `lazyRebuild` makes `start` mark a projection whose schema changed instead of rebuilding it; see [Rebuilding Later](./migration.md#rebuilding-later--lazyrebuild).
- `evolution` is not read by the engine. In particular, its `enableMigrationShortcut` is ignored: the engine always tries the shortcut when a migration is registered.

A configuration is an ordinary case class value, built with named arguments or copied from the default:

```scala mdoc:compile-only
val catchUpInLargeBatches = ProjectionEngineConfig(batchSize = 1000, batchTimeout = 200.millis)
val rebuildLater          = ProjectionEngineConfig.default.copy(lazyRebuild = true)
```

## Storage Layout

The engine keeps each projection in its own SQLite files. `ProjectionEngine.make`, `ProjectionEngine.makeWithConfig` and `ProjectionEngine.makeWithStoresVarargs` choose the paths, relative to the working directory of the process; with `ProjectionEngine.makeWithStores`, the primary store is wherever you created it:

- **Primary store:** `<basePath>/<name>.db`, where `basePath` comes from the projection's `EntityPath`, or `global/<name>.db` for a global projection. The `global/` folder is fixed by the engine.
- **Folders:** SQLite does not create them, so `<basePath>/` and `global/` must exist before a store is used.
- **Shards:** `<basePath>/<name>_shard_<key>.db`, one per routing key, opened when the first event with that key arrives. The key is changed into a safe file name as described under [CrossEntity](./projection.md#crossentity-one-drawer-per-group), so two keys that give the same file name share one file. Shards are SQLite files only when the primary store is a `SQLiteProjectionStore`; otherwise they are `InMemoryProjectionStore`s, which are lost when the program ends.
- **ID column of a global projection:** a global projection has no `EntityPath`, so the store the engine creates for it uses the column `id`, whatever field `@Modifier.id` marks; see [Global](./projection.md#global-one-scoreboard-on-the-wall).

Inside each store file, the store creates its tables the first time it is used:

- The table of rows, named after the row type in snake_case, as described under [Changing the Read Model](./migration.md#changing-the-read-model), with a unique index on the ID column.
- The table `_projection_meta`, with the columns `name`, `last_seq`, `schema_hash` and `updated_at`. Its row is keyed by the name of the rows' table, not by the projection name. `last_seq` is the watermark, `schema_hash` the fingerprint of the row type, and `updated_at` the time of the last change, in milliseconds since the epoch.

Each file also gets `-wal` and `-shm` files next to it, because the `TransactorCache` turns on SQLite's write-ahead log; see [Connection Management](#connection-management) and the WAL entry of the [Glossary](./index.md#stores-and-the-engine).

## Schema Changes

On every `start`, the engine compares the fingerprint stored in each primary store with the fingerprint of the current row type, as described under [How a Change Is Detected](./migration.md#how-a-change-is-detected--schemahash). A new store without a fingerprint just gets the current one. When they differ, the engine takes the [add-column shortcut](./migration.md#adding-fields-without-a-rebuild--registermigration) if a registered migration fits, and otherwise runs a [full rebuild](./migration.md#full-rebuild) from the projection's event store, before catch-up begins. With [`lazyRebuild`](./migration.md#rebuilding-later--lazyrebuild) on, it only marks the projection, and the first `query` or the pre-warm fiber rebuilds it. A rebuild replays only one event store, found the way `start` finds the store of the projection's first source, and an engine without event stores does not rebuild at all; see [Limitations](./migration.md#limitations).

## Connection Management

Every `SQLiteProjectionStore` reaches its file through a `TransactorCache`, and a `SQLiteEventStore` usually gets its `Transactor` from one. The cache keeps one `Transactor`, from the [SQL module](../sql/index.md), for each file path:

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

- `TransactorCache.make` creates a cache that is closed when the enclosing `Scope` closes. `TransactorCache.makeUnscoped` creates one that you close yourself with `TransactorCache#close`. `TransactorCache.live` and `TransactorCache.liveConfig` provide a cache from `make` as a `ZLayer`.
- `TransactorCache#get` returns the `Transactor` of a path, and creates it on the first request. Creating it loads the `org.sqlite.JDBC` driver, opens one connection, runs `PRAGMA journal_mode=WAL`, `PRAGMA synchronous=NORMAL` and `PRAGMA busy_timeout=5000` on it, and closes it again.
- The cache keeps `Transactor` values, not open connections. Each operation through a `Transactor` opens its own JDBC connection and closes it when done, and `SQLiteEventStore#readFrom`, which catch-up uses, opens its own connection for the stream. So `maxSize` limits how many paths are cached, not how many connections are open.
- When the cache holds `maxSize` paths and a new one is requested, the least recently used path is evicted. A `maxSize` of 0 or less means no limit. `evict` removes one path, and `size` returns the number of cached paths.
- After `close`, the cache is empty, and `get` fails with `IllegalStateException("TransactorCache is closed")`.

The engine and the event stores can share one cache. The snippet below opens the event store and the engine with the cache from the environment, starts the engine, and then provides that cache with `TransactorCache.live`:

```scala mdoc:compile-only
val cacheLayer: ZLayer[Scope, Nothing, TransactorCache] = TransactorCache.live(maxSize = 64)

val startOnSharedCache: ZIO[TransactorCache & Scope, Throwable, ProjectionEngine] = for {
  cache  <- ZIO.service[TransactorCache]
  users  <- cache.get("events/users.db").flatMap(tx => SQLiteEventStore.make[UserCreated](tx))
  engine <- ProjectionEngine.makeWithStoresVarargs(cache, Map[String, EventStore[?]]("users" -> users))(projection)
  _      <- engine.start
} yield engine

val program: ZIO[Scope, Throwable, ProjectionEngine] = startOnSharedCache.provideSomeLayer[Scope](cacheLayer)
```

## Testing

[`TestEngine`](./testing.md#testengine) creates an `InMemoryProjectionStore` for each projection and an in-memory event store for each source name, then calls `ProjectionEngine.makeWithStores` and `start` for you. The engine it wraps is available as its `engine` field, so a test can call the operations on this page with no files and no `sqlite-jdbc`. Because `start` runs inside `TestEngine.make`, a test cannot register a migration first; see [Limitations](./migration.md#limitations).

## Running the Examples

The projection examples that use the engine are embedded on the pages that explain them. Each file is under `projection-examples/src/main/scala/projection/examples/`, and each command runs from the repository root. `sbt "projection-examples/run"` runs all the examples in turn.

- `Example1QuickStart.scala`: the quick start, an engine from `ProjectionEngine.makeWithStores` on SQLite files, polled with `repeatUntil`. Run `sbt "projection-examples/runMain projection.examples.Example1QuickStart"`; see [Quick Start](./index.md#quick-start).
- `Example3CrossEntity.scala`: a cross-entity projection and a global one, from `ProjectionEngine.makeWithStoresVarargs` with two sources, and the shard files it creates. Run `sbt "projection-examples/runMain projection.examples.Example3CrossEntity"`; see [`routedByField`](./projection.md#routedbyfield).
- `Example6GlobalAggregate.scala`: one global row of counters fed by two sources. Run `sbt "projection-examples/runMain projection.examples.Example6GlobalAggregate"`; see [Aggregating Fields](./projection.md#aggregating-fields).
- `Example8SchemaEvolution.scala`: two engines in a row, the second rebuilding a changed read model in `start`. Run `sbt "projection-examples/runMain projection.examples.Example8SchemaEvolution"`; see [Full Rebuild](./migration.md#full-rebuild).
- `Example9MigrationShortcut.scala`: the same two engines, with `ProjectionEngine#registerMigration` before `ProjectionEngine#start`, so the column is added without a rebuild. Run `sbt "projection-examples/runMain projection.examples.Example9MigrationShortcut"`; see [Adding Fields Without a Rebuild](./migration.md#adding-fields-without-a-rebuild--registermigration).
- `Example10RebuildOnDemand.scala`: a changed handler, and a rebuild with `SchemaEvolution.rebuildWithHash` before the engine starts. Run `sbt "projection-examples/runMain projection.examples.Example10RebuildOnDemand"`; see [Rebuilding on Demand](./migration.md#rebuilding-on-demand).
- `Example13EngineCatchUp.scala`: events appended before `ProjectionEngine#start`, applied by catch-up and read right after it, and the watermark. Run `sbt "projection-examples/runMain projection.examples.Example13EngineCatchUp"`; see [Usage](#usage).
