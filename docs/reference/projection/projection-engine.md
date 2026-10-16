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

## Schema Changes

On every `start`, before catch-up, the engine checks whether the stored rows still match the row type. It compares two **fingerprints**, hashes of the shape of the row type: the one saved in the primary store, and the one of the current row type. See [How a Change Is Detected](./migration.md#how-a-change-is-detected--schemahash). What it does next depends on the result:

- **No saved fingerprint**, because the store is new: it saves the current one.
- **Same fingerprint:** nothing.
- **Different fingerprint:** it brings the rows up to date in one of two ways:
  - with the [add-column shortcut](./migration.md#adding-fields-without-a-rebuild--registermigration), if a migration registered for the projection only adds fields;
  - otherwise with a [full rebuild](./migration.md#full-rebuild) from the projection's event store.
- **Different fingerprint, with [`lazyRebuild`](./migration.md#rebuilding-later--lazyrebuild) on:** it only marks the projection. The first `query`, or the pre-warm fiber, rebuilds it later.

Two limits apply; see [Limitations](./migration.md#limitations):

- A rebuild replays only one event store: the one `start` uses for the projection's first source.
- An engine without event stores, made with `make` or `makeWithConfig`, changes nothing and keeps the old fingerprint.

## Testing

[`TestEngine`](./testing.md#testengine) creates an `InMemoryProjectionStore` for each projection and an in-memory event store for each source name, then calls `ProjectionEngine.makeWithStores` and `start` for you. The engine it wraps is available as its `engine` field, so a test can call the operations on this page with no files and no `sqlite-jdbc`. Because `start` runs inside `TestEngine.make`, a test cannot register a migration first; see [Limitations](./migration.md#limitations).

