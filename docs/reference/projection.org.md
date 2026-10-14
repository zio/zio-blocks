---
id: projection
title: "Projection"
---

`zio-blocks-projection` provides event-sourced projections backed by per-entity SQLite files. Each projection gets its own isolated `.db` file, plus one more for each shard of a cross-entity projection, giving you WAL-mode writes (SQLite's write-ahead log, so queries do not wait for writes), offline queryability, and zero contention between unrelated entities.

## Overview

Projections turn an event stream into queryable read models. Instead of querying the event store directly, you define how events map to entities, and the engine materializes those entities into SQLite files you can read anytime.

**Why per-entity SQLite?**

A single shared database means every projection contends on the same locks, the same WAL, and the same file. With per-entity files, each projection type gets isolated storage:

| Approach | Contention | WAL contention | Offline access | Backup granularity |
| --- | --- | --- | --- | --- |
| Single DB | High | High | Yes | Full DB |
| Per-entity SQLite | Zero | Zero | Yes | Per projection |

Each projection has a name, the string you give to `Projection[A]("userProfiles")`, and a folder, called its `basePath`, which comes from its [`EntityPath`](./projection/projection.md#entity-paths), for example `users`. The SQLite files of a projection are named from these two, and which files there are depends on its scope, see [Projection Scopes](./projection/projection.md#projection-scope). The [`TransactorCache`](./projection/engine.md#connection-management) reuses connections via an LRU cache so you don't open hundreds of file handles.

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

