---
id: projection
title: "Projection"
---

`zio-blocks-projection` provides event-sourced projections backed by per-entity SQLite files. Each projection gets its own isolated `.db` file, plus one more for each shard of a cross-entity projection, giving you WAL-mode writes (SQLite's write-ahead log, so queries do not wait for writes), offline queryability, and zero contention between unrelated entities.

## Glossary

The terms below are used throughout this page. The first list gives the general event sourcing terms, which apply to any event-sourced system. The second list names the types of this library. Most of them are explained in more detail in the sections that follow.

### Event-sourcing Terms

- **Event**: A fact that happened, such as `OrderPlaced`. Events are only appended to the log and are never changed.
- **Event log (event store)**: The append-only record of all events, in order. It is the source of truth.
- **Entity (aggregate)**: The thing events are about, such as a user, an order or a repository. Events are recorded under the ID of an entity.
- **Sequence number**: The position of an event in the log. A consumer saves the last one it processed, so it can resume after it.
- **Replay**: Applying the events of the log in order, to work out a state or to rebuild a view.
- **Read model**: A table or document shaped for queries, such as "all orders of a customer". It is derived from the events and can be rebuilt.
- **Projection**: The rules, and the process running them, that keep a read model up to date by applying each event as it arrives.
- **CQRS**: Command query responsibility segregation: the write side appends events, and the read side answers queries from read models.
- **Eventual consistency**: A read model is updated shortly after an event is appended, so a query made right after a write may not see it yet.
- **Event versioning (schema evolution)**: Changing the name or shape of an event or a read model after data exists. Old events stay as they are, so a migration maps them to the new shape, and a read model is rebuilt.

```text
event store  --(engine reads events and runs handlers)-->  projection store
(the log)                                                  (the finished rows)
```

### Types in this library

The types below are the ones you meet when you use the library, grouped by the part of the picture above they belong to. Events are written to an event store. A projection says how those events become rows, and its handlers do the work for each event. Sources and routing decide which events reach which rows. The engine runs everything and keeps the rows in stores. Schemas keep the old events and rows working when their shape changes.

#### Events

- `EventStore`: The event log, as a trait. It appends events, reads the history, and lets a consumer follow new events.
- `SQLiteEventStore`: An `EventStore` kept in a SQLite file.
- `EventEnvelope`: An event together with the metadata the store records about it: its sequence number, its tag, its timestamp and the entity ID it was recorded under.
- `eventTag`: The annotation `@eventTag(n)`, which gives an event case a stable numeric tag. See [Event Tagging and Migration](./projection/projection.md#event-tagging-and-migration).

#### Projections

- `Projection[A]`: The declaration of how events become the read model `A`: which sources to listen to, how to route their events, and what each event does. It does nothing until an engine runs it.
- `Handler[E, A]`: The function that turns an event `E` into a `ProjectionAction[A]`. It only describes the change.
- `ProjectionContext`: The metadata a handler receives together with an event, such as the entity ID.
- `ProjectionAction`: What a handler returns: an insert, an upsert, an update, a delete, a truncate or nothing.
- `FieldUpdate`: A change to one field of a row, used by an update action, such as setting a value or incrementing a counter. See [Field Updates](#field-updates).
- `ProjectionScope`: How the data of a `Projection` is split across `ProjectionStore`s. It is worked out from how the projection is built and is not set directly. It is one of three: one row for each entity ID (the default), one store for each routing key, called a shard, or one shared store for the whole projection. See [Multi-Source Projections](#multi-source-projections).

#### Sources and routing

- `SourceBinding`: A named source of events, given to `Projection.from`, together with its routing and its handlers. The name must match the key of an `EventStore` given to the engine.
- `RoutingMode`: How the events of a source are directed: to the entity they were recorded under, to one shared store, or to a store chosen by a key that is read from the event.

#### Stores and the engine

- `ProjectionStore[A]`: Where the rows of a read model are kept.
- `SQLiteProjectionStore`: A `ProjectionStore` kept in a SQLite file.
- `InMemoryProjectionStore`: A `ProjectionStore` kept in an in-memory map, used for tests.
- `ProjectionEngine`: The runtime that reads events from `EventStore`s, runs the handlers and writes the results to `ProjectionStore`s.
- `TestEngine`: A `ProjectionEngine` with in-memory stores, for tests and examples.
- `TransactorCache`: A cache of open SQLite connections, one for each database file, shared by the stores and the engine. See [TransactorCache](#transactorcache).
- **WAL (write-ahead log)**: A way for SQLite to make writes safe. A write is first appended to a separate `.db-wal` file, and it is copied into the main `.db` file later. Readers can keep reading while a write happens, and a crash does not lose the writes in the log. There is still only one writer at a time for each file. The `TransactorCache` turns it on for every file it opens, so you will see `-wal` and `-shm` files next to the `.db` files.

#### Schemas

- `EntityPath`: Says which folder the files of a projection go in and which field of the row is its ID. See [Entity Path Conventions](./projection/projection.md#entity-paths).
- `SchemaHash`: A fingerprint of a read model's schema. When it changes, the engine rebuilds the `ProjectionStore`.
- `SchemaEvolution`: Rebuilds a `ProjectionStore` by replaying the events. See [Schema Evolution](./projection/engine.md#schema-evolution).
- `TagInfo`: A cheat sheet of the tags of an event type: which tags are current, and which old tags were renamed to them. It lets an `EventStore` find the events that were saved under an old name. See [Tag aliases via Migration](./projection/projection.md#tag-aliases-via-migration).
- `TagResolver`: Builds a `TagInfo` from an event type and, if a case was renamed, a `Migration`.

## Overview

Projections turn an event stream into queryable read models. Instead of querying the event store directly, you define how events map to entities, and the engine materializes those entities into SQLite files you can read anytime.

**Why per-entity SQLite?**

A single shared database means every projection contends on the same locks, the same WAL, and the same file. With per-entity files, each projection type gets isolated storage:

| Approach | Contention | WAL contention | Offline access | Backup granularity |
| --- | --- | --- | --- | --- |
| Single DB | High | High | Yes | Full DB |
| Per-entity SQLite | Zero | Zero | Yes | Per projection |

Each projection has a name, the string you give to `Projection[A]("userProfiles")`, and a folder, called its `basePath`, which comes from its [`EntityPath`](./projection/projection.md#entity-paths), for example `users`. The SQLite files of a projection are named from these two, and which files there are depends on its scope, see [Projection Scopes](./projection/projection.md#projection-scope). The [`TransactorCache`](#transactorcache) reuses connections via an LRU cache so you don't open hundreds of file handles.

## Multi-Source Projections

A projection can read events from more than one source. Each call to `from` starts a new source binding, with its own routing and its own handlers, and the engine reads every source by the name you pass to `from`. The next projection keeps one activity row for each repository created and each issue opened, and puts both kinds under their owner:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class IssueOpened(ownerId: String, title: String)
object IssueOpened {
  implicit val schema: Schema[IssueOpened] = Schema.derived[IssueOpened]
}

case class OwnerActivity(@Modifier.id id: String, ownerId: String, kind: String, text: String)
object OwnerActivity {
  implicit val schema: Schema[OwnerActivity]         = Schema.derived[OwnerActivity]
  implicit val entityPath: EntityPath[OwnerActivity] = EntityPath.derived[OwnerActivity]
}

val activity = Projection[OwnerActivity]("ownerActivity")
  .from("repos")                       // first source
  .routedBy[RepoCreated](_.ownerId)
  .on[RepoCreated]
  .insert((e, ctx) => OwnerActivity(ctx.entityId, e.ownerId, "repo", e.repoName))
  .from("issues")                      // second source
  .routedBy[IssueOpened](_.ownerId)
  .on[IssueOpened]
  .insert((e, ctx) => OwnerActivity(ctx.entityId, e.ownerId, "issue", e.title))

// activity.sourceNames == List("repos", "issues")
// activity.scope       == ProjectionScope.CrossEntity(...)
```

The engine needs an `EventStore` for each name, `"repos"` and `"issues"`, in the `eventStores` map you give it. Here both sources use `routedBy` with the owner, so a repository and an issue of the same owner go to the same shard store. The routing mode, such as `routedBy` here, is chosen for each source right after its `from`, while the scope belongs to the whole projection.

## Field Updates

A handler does not have to insert a whole row. It can apply a `FieldUpdate`, which changes one field of an existing row, for example by incrementing a counter. This is how projections keep counters and running totals, such as the number of users created. It is most common in a `Global` projection, but any projection can do it. The next projection counts the events of two sources in one row for each day:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}


case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}
case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class DailyStats(@Modifier.id date: String, userCount: Int, repoCount: Int)
object DailyStats {
  implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
  implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
}

val stats = Projection
  .global[DailyStats]("dailyStats")
  .from("users")
  .routeToAll
  .on[UserCreated]
  .aggregate(FieldUpdate.Increment("user_count", 1L))
  .from("repos")
  .routeToAll
  .on[RepoCreated]
  .aggregate(FieldUpdate.Increment("repo_count", 1L))
```

**FieldUpdate operations:**

| Operation | SQL translation | Description |
| --- | --- | --- |
| `Set(field, value)` | `SET col = ?` | Replace field value |
| `Increment(field, by)` | `SET col = COALESCE(col,0) + ?` | Atomic increment |
| `Decrement(field, by)` | `SET col = COALESCE(col,0) - ?` | Atomic decrement |
| `Max(field, value)` | `SET col = MAX(COALESCE(col, ?), ?)` | Keep highest value |
| `Min(field, value)` | `SET col = MIN(COALESCE(col, ?), ?)` | Keep lowest value |

Before it applies the updates, the engine runs `INSERT OR IGNORE`, which creates the row with the default value of each column if it is missing. Incrementing a counter of a row that does not exist yet therefore gives `1`, and `COALESCE` treats a missing value as `0`.

**Concurrent safety:** Counter operations are atomic at the SQL level. Ten fibers each incrementing the same counter produce the correct total without lost updates.

## TransactorCache

A `TransactorCache` keeps one open SQLite connection (a `Transactor`) for each database file and reuses it, so the engine does not reopen files for every event. It also limits how many files are open at once, which matters for a `CrossEntity` projection with one shard file for each routing key.

You usually do not create one. `ProjectionEngine.make` creates its own and `ProjectionEngine#transactorCache` returns it. Create one yourself when you build the engine with event stores, so the engine and the stores share it. A cache is created and used like this:

```scala
object TransactorCache {
  def make(maxSize: Int = 256): ZIO[Scope, Nothing, TransactorCache]
  def makeUnscoped(maxSize: Int = 256): UIO[TransactorCache]
}

trait TransactorCache {
  def get(path: String): Task[Transactor]
  def evict(path: String): Task[Unit]
  def close: Task[Unit]
  def size: Task[Int]
}
```

`maxSize` is the most database files the cache keeps open at the same time, 256 by default. Every open file holds a connection, and a process can only have so many files open, so a `CrossEntity` projection with a shard file for each routing key needs a limit. When the cache is full and a new path is asked for, it closes the least recently used file to make room. That file is not lost: its data stays on disk, and the next `get` for it opens it again, which is slower. If your projections have more active shards than `maxSize`, the cache keeps closing and reopening files, so raise it. A `maxSize` of 0 or less means no limit.

The engine takes the cache as an argument:

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

`makeWithStores` is how you give an engine event stores to read from, and the cache to use. `eventStores` maps each source name, the one you pass to `from`, to its `EventStore`. `stores` maps each projection name to its primary store, which you create yourself. If an event store is a `SQLiteEventStore`, get its `Transactor` from the same `cache`, so the engine and the event stores share one limit on open files.

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

## Configuration

### ProjectionEngineConfig

Controls batching, ring buffer size, rebuild behavior, and schema evolution:

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.ProjectionEngineConfig

val config = ProjectionEngineConfig(
  batchSize         = 100,       // events per batch in catch-up
  batchTimeout      = 50.millis, // max wait before flushing partial batch
  ringCapacity      = 4096,      // Hub capacity for live events
  rebuildParallelism = 4,        // concurrent rebuild fibers
  lazyRebuild       = false      // true = defer rebuilds to first query
)
```

| Parameter | Default | Description |
| --- | --- | --- |
| `batchSize` | 100 | Number of events processed per batch during catch-up |
| `batchTimeout` | 50ms | Maximum wait before flushing a partial batch |
| `ringCapacity` | 4096 | Capacity of the Hub ring buffer for live events |
| `rebuildParallelism` | 4 | Number of concurrent rebuild fibers |
| `lazyRebuild` | false | Defer schema rebuilds to first query |
| `evolution` | default | `SchemaEvolutionConfig` sub-config |

### TransactorCacheConfig

Controls the SQLite transactor cache:

```scala mdoc:compile-only
import zio.blocks.projection.TransactorCacheConfig

val cacheConfig = TransactorCacheConfig(maxSize = 256)
```

| Parameter | Default | Description                                 |
| --------- | ------- | ------------------------------------------- |
| `maxSize` | 256     | Maximum number of cached SQLite connections |

The cache uses LRU eviction. Each cached transactor sets `PRAGMA journal_mode=WAL` and `PRAGMA synchronous=NORMAL` on first open. The cache is `Scope`-managed: all transactors close when the scope exits.

### Scope lifecycle

The `ProjectionEngine.make` and `TransactorCache.make` methods require `ZIO[Scope, ...]`. This ensures resources are cleaned up when your application shuts down:

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*

val app: ZIO[Scope, Throwable, Unit] = for {
  cache  <- TransactorCache.make()
  engine <- ProjectionEngine.makeWithConfig(
              ProjectionEngineConfig.default,
              /* specs... */
            )
  _ <- engine.start
} yield ()
// cache and engine cleaned up when scope closes
```

