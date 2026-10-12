---
id: projection
title: "Projection"
---

`zio-blocks-projection` provides event-sourced projections backed by per-entity SQLite files. Each projection gets its own isolated `.db` file, plus one more for each shard of a cross-entity projection, giving you WAL-mode writes (SQLite's write-ahead log, so queries do not wait for writes), offline queryability, and zero contention between unrelated entities.

## Event Sourcing Basics

Most applications store the **current state** of things: a row in a table that is updated in place. When an order is shipped, the `status` column changes from `placed` to `shipped`, and the earlier value is gone.

**Event sourcing** stores something else: the **sequence of facts that happened**. Nothing is updated in place. Each fact is an **event**, and it is appended to a log:

```text
1. OrderPlaced   order-1, alice, keyboard, 80
2. OrderPlaced   order-2, alice, monitor, 250
3. OrderShipped  order-1
```

The current state is not stored. It is worked out by replaying the events in order: after event 3, `order-1` is shipped and `order-2` is still placed.

A few properties follow from this:

- **Events are facts about the past.** They are only ever appended, never edited or deleted, and their order is kept. The log is the source of truth.
- **State can be recomputed.** Replaying the same events always gives the same state, so a view of the data can be thrown away and rebuilt, or built in a different shape later.
- **History comes for free.** You can answer "how did we get here?", not only "where are we now?".
- **Events are permanent.** Once written they stay as they are, so renaming an event or changing its shape needs care. The [Event Tagging and Migration](#event-tagging-and-migration) section covers this.

### Reading the current state

Replaying the whole log to answer every question would be slow. Event sourcing therefore usually separates the two sides of an application, often called **CQRS** (command query responsibility segregation):

- the **write side** appends events to the log;
- the **read side** answers queries from **read models**, which are tables or documents shaped for the questions you ask, such as "all orders of a customer" or "how many orders are pending".

A **projection** is what keeps a read model up to date. It listens to the events as they are appended and applies each one to the read model:

```text
append events  -->  event log  -->  projection  -->  read model  -->  queries
  (write side)                                                         (read side)
```

Two consequences are worth knowing up front:

- **Reads can lag a little.** A read model is updated after an event is appended, not at the same instant, so a query right after a write may not see it yet. This is called eventual consistency.
- **A read model is disposable.** If its shape changes, or it is lost, it is rebuilt by replaying the log through the projection.

This library is the projection part: you declare how events turn into read models, and an engine runs the declaration and keeps SQLite files up to date.

## Motivation

A projection keeps a read model up to date as events arrive, so queries such as "what is Alice's current email?" or "how many orders were placed today?" never replay history. Written by hand, that code tends to go wrong in the same few ways:

- **Routing is scattered.** The knowledge that an `OrderPlaced` event must update the order, the customer's totals, and a daily counter lives in several handlers, so it is easy to miss one.
- **Restarts are fragile.** The code has to catch up on events it missed while it was down, then switch to live events, without skipping or double-applying any.
- **Event shapes change.** Once an event or a read model gains a field, old data no longer matches the code, and the read model has to be rebuilt by hand.
- **Tests need infrastructure.** Verifying a handler usually means standing up a database and a stream.

ZIO Blocks Projection addresses these by making a projection a declaration rather than a hand-rolled consumer. You state which event types a projection listens to, which entity each event belongs to, and how an event changes that entity. The engine then does the rest:

- it reads historical events to catch up, then follows live events;
- it routes each event to the entity (or entities) that should see it;
- it detects a changed schema and rebuilds the read model lazily, with migrations available to map old event tags to new ones;
- it stores each projection in its own SQLite file, so read models are isolated, individually backed up, and queryable offline.

Because the same declaration runs against an in-memory store, you can test a projection by appending events and querying the result, with no database. The [Quick Start](#quick-start) shows the whole loop in about twenty lines.

Projection is a good fit when you already treat events as the source of truth and want fast, local, query-friendly views of them. It is not a general query engine over arbitrary data, and the read models it maintains are derived state: they can always be discarded and rebuilt from the events.

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
- `eventTag`: The annotation `@eventTag(n)`, which gives an event case a stable numeric tag. See [Event Tagging and Migration](#event-tagging-and-migration).

#### Projections

- `Projection[A]`: The declaration of how events become the read model `A`: which sources to listen to, how to route their events, and what each event does. It does nothing until an engine runs it.
- `Handler[E, A]`: The function that turns an event `E` into a `ProjectionAction[A]`. It only describes the change.
- `ProjectionContext`: The metadata a handler receives together with an event, such as the entity ID.
- `ProjectionAction`: What a handler returns: an insert, an upsert, an update, a delete, a truncate or nothing.
- `FieldUpdate`: A change to one field of a row, used by an update action, such as setting a value or incrementing a counter. See [Aggregate Projections](#aggregate-projections).
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

- `EntityPath`: Says which folder the files of a projection go in and which field of the row is its ID. See [Entity Path Conventions](#entity-path-conventions).
- `SchemaHash`: A fingerprint of a read model's schema. When it changes, the engine rebuilds the `ProjectionStore`.
- `SchemaEvolution`: Rebuilds a `ProjectionStore` by replaying the events. See [Schema Evolution](#schema-evolution).
- `TagResolver` and `TagInfo`: Work out the current tags of an event type, and the aliases from old tags to current ones after a case was renamed.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "@VERSION@"

// JVM only — SQLite-backed stores
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "@VERSION@"

// ZIO integration (transactor cache, engine runtime)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"

// for SQLite persistence add: libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0" // or InMemory fallback if not present
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The library does not bring `sqlite-jdbc` with it, so add it to your own build if you want SQLite persistence. If it is missing, `ProjectionEngine.make` falls back to `InMemoryProjectionStore`.

A [`TransactorCache`](#transactorcache) loads the SQLite driver by name when it first opens a file, so `sqlite-jdbc` must be on the runtime classpath whenever you use one directly, for example to build a `SQLiteEventStore`. Without it, opening the first file fails with `ClassNotFoundException: org.sqlite.JDBC`.

## Overview

Projections turn an event stream into queryable read models. Instead of querying the event store directly, you define how events map to entities, and the engine materializes those entities into SQLite files you can read anytime.

**Why per-entity SQLite?**

A single shared database means every projection contends on the same locks, the same WAL, and the same file. With per-entity files, each projection type gets isolated storage:

| Approach | Contention | WAL contention | Offline access | Backup granularity |
| --- | --- | --- | --- | --- |
| Single DB | High | High | Yes | Full DB |
| Per-entity SQLite | Zero | Zero | Yes | Per projection |

Each projection has a name, the string you give to `Projection[A]("userProfiles")`, and a folder, called its `basePath`, which comes from its [`EntityPath`](#entity-path-conventions), for example `users`. The SQLite files of a projection are named from these two, and which files there are depends on its scope, see [Projection Scopes](#projection-scopes). The [`TransactorCache`](#transactorcache) reuses connections via an LRU cache so you don't open hundreds of file handles.

## Quick Start

A projection is first defined once, then run by an engine. The two steps below use the same events and the same projection. The first runs them in memory with `TestEngine`, which is the quickest way to try one. The second keeps the events and the read model in SQLite files, the way an application does.

### Step 1: Try it in memory with TestEngine

Define your events, your projection entity, declare the projection, and start the engine:

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

// 1. Define events
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

// 2. Define the projection entity
case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

// 3. Define the projection
val projection = Projection[UserProfile]("userProfiles")
  .from("users")
  .routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

// 4. Create a test engine (auto-creates stores and hubs)
val program: ZIO[Scope, Throwable, Unit] = for {
  engine <- TestEngine.make(projection)
  // Append events
  _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
  _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))
  // Query
  u1 <- engine.query(projection, "user-1")
  u2 <- engine.query(projection, "user-2")
} yield ()
```

The engine starts a catch-up fiber that reads all historical events, then switches to live mode via the Hub subscription. Query results appear once the engine processes the relevant events.

### Step 2: Keep it in SQLite files

`TestEngine` keeps everything in memory, so nothing survives the program. To keep the events and the read model on disk, create the stores yourself and give them to `ProjectionEngine.makeWithStores`. The events and the projection are the same as in step 1, and only the wiring changes. It needs `sqlite-jdbc` on the runtime classpath, see [Installation](#installation):

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

// The same events and projection as in step 1
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

// Wire the projection to SQLite files
val program: ZIO[Scope, Throwable, Option[UserProfile]] = for {
  cache    <- TransactorCache.make()
  tx       <- cache.get("events/users.db")
  users    <- SQLiteEventStore.make[UserCreated](tx)
  profiles <- SQLiteProjectionStore.make[UserProfile]("users/userProfiles.db", cache)
  engine   <- ProjectionEngine.makeWithStores(
                List(projection),
                Map("userProfiles" -> profiles), // projection name -> its store
                Map("users" -> users),           // source name given to `from` -> its event store
                cache
              )
  _        <- engine.start
  _        <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
  // The engine applies the event in the background, so ask until the row is there
  alice    <- engine.query(projection, "user-1").repeatUntil(_.isDefined)
} yield alice
```

The files stay on disk after the program ends. When a program starts again, the events are still in `events/users.db`, and the engine continues after the last event it processed, so no event is applied twice. The files are plain SQLite databases, so `sqlite3 users/userProfiles.db` shows the rows while the program is stopped.

## Entity Path Conventions

`EntityPath[A]` tells the engine two things: which folder stores the SQLite file, and which field holds the entity ID. The ID field is identified via `@Modifier.id` (from `zio.blocks.schema.Modifier`) or, for ergonomics, a field named `id`. Which projections use the folder is explained in [Projection Scopes](#projection-scopes).

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id userId: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "users", entityIdField = "userId"
```

**Derivation rules** (`EntityPath.derived[A]`):

1. Look for a field annotated with `@Modifier.id`. If found, use it as the entity ID field.
2. Otherwise, find the field named `id`.
3. If neither is found, fail with `Entity must have @Modifier.id field`.
4. Derive the folder name from the ID field name:
   - Strip trailing `Id` suffix: `userId` → `user`
   - Convert to snake_case: `userId` → `user_id`
   - Pluralize: `user` → `users`
   - Result: `basePath = "users"`, `entityIdField = "userId"`

If the field is exactly `id`, `basePath` is `ids` — prefer `userId` or use `@path` to override.

**Override with `@path`:**

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

@path("custom_users")
case class UserProfile(@Modifier.id id: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "custom_users", entityIdField = "id"
```

The same override can be given without an annotation: `EntityPath.derived[UserProfile]("custom_users")` also gives `basePath = "custom_users"`. The path can have several folders, such as `"example2/orders"`.

**Manual construction:**

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class MyEntity(@Modifier.id id: String, value: Int)
object MyEntity {
  implicit val schema: Schema[MyEntity] = Schema.derived[MyEntity]
  implicit val entityPath: EntityPath[MyEntity] = EntityPath[MyEntity]("my_entities", "id")
}
```

## Projection Scopes

A scope is the way the rows of a projection are organized. To see what that means, picture a shop that writes a note every time something happens: "Alice placed order 7", "order 7 shipped". These notes are the events. To answer questions quickly ("is order 7 shipped?", "how many orders so far?"), someone files each note into a cabinet as it arrives. The filed result is the read model, and the scope is how that cabinet is arranged.

A `ProjectionScope` is the type that describes it. It answers two questions: which `ProjectionStore` an event is applied to, and which file that store lives in. There are three scopes: `ProjectionScope.PerEntity`, `ProjectionScope.CrossEntity` and `ProjectionScope.Global`.

You never set a scope directly. It is worked out from how the projection is built, and `Projection.scope` returns it:

1. A projection made with `Projection.global` has the scope `Global`.
2. Otherwise, if any source uses `routedBy`, it has the scope `CrossEntity`.
3. Otherwise it has the scope `PerEntity`, which is the default.

Note that `routeToAll` alone does not make a projection global. Only `Projection.global` does.

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserRegistered(name: String)
object UserRegistered {
  implicit val schema: Schema[UserRegistered] = Schema.derived[UserRegistered]
}

case class OrderPlaced(customerId: String, total: Long)
object OrderPlaced {
  implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced]
}

case class UserProfile(@Modifier.id id: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

case class CustomerOrder(@Modifier.id id: String, customerId: String, total: Long)
object CustomerOrder {
  implicit val schema: Schema[CustomerOrder]         = Schema.derived[CustomerOrder]
  implicit val entityPath: EntityPath[CustomerOrder] = EntityPath.derived[CustomerOrder]
}

case class OrderStats(@Modifier.id id: String, orders: Long)
object OrderStats {
  implicit val schema: Schema[OrderStats] = Schema.derived[OrderStats]
}

// PerEntity: one row for each entity ID.
val profiles = Projection[UserProfile]("userProfiles")
  .from("users")
  .routeToSelf
  .on[UserRegistered]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name))

// CrossEntity: events are grouped by a key read from the event.
val ordersByCustomer = Projection[CustomerOrder]("ordersByCustomer")
  .from("orders")
  .routedBy[OrderPlaced](_.customerId)
  .on[OrderPlaced]
  .insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.total))

// Global: one shared store, here with a counter.
val stats = Projection
  .global[OrderStats]("orderStats")
  .from("orders")
  .routeToAll
  .on[OrderPlaced]
  .aggregate(FieldUpdate.Increment("orders", 1L))

// profiles.scope         == ProjectionScope.PerEntity
// ordersByCustomer.scope == ProjectionScope.CrossEntity(...)
// stats.scope            == ProjectionScope.Global
```

### PerEntity: one card per thing

Each order, user or product gets its own card, and a new note about order 7 updates the card of order 7 only. This scope answers the question "what is the state of this one thing?", for example "alice, keyboard, shipped".

Every entity has its own row, and the events recorded under an entity ID update that entity's row. The store is the file `<basePath>/<name>.db`, for example `users/userProfiles.db`. It is the right scope for state that belongs to one thing, such as a user's profile or the status of an order.

### CrossEntity: one drawer per group

Here the cabinet has a drawer for each customer. The note says "order 7 placed, customer Alice", so it is filed in Alice's drawer and not in a drawer for order 7. Inside the drawer each order still has its own card. This scope answers the question "what belongs together under this customer?".

Events are grouped by a key that is read from the event, not by the ID they were recorded under. You say which part of the event picks the drawer with `routedBy(_.customerId)`. Each routing key gets its own store, called a shard, next to the main one: `<basePath>/<name>_shard_<key>.db`. With SQLite each shard is its own file, such as `customer_orders_shard_alice.db`. In the file name the key is cut to 64 characters, and any character other than letters, digits, `_` and `-` is replaced by `_`. If the key cannot be read from an event, the engine uses the entity ID.

The routing key only chooses the shard. A row keeps its own ID, so two rows in the same shard still need different IDs. To read a row you give its ID: the engine looks in the main store first, and then in the shards.

It is the right scope for views that group things by a related entity, such as the orders of a customer or the repositories of an owner. See [Multi-Source Projections](#multi-source-projections).

### Global: one scoreboard on the wall

There is no card for each thing and no drawer for each group. Every note, whoever it is about, changes the numbers on one board: orders so far, shipped, pending. This scope answers the question "how many in total?".

All events go to one shared store, `global/<name>.db`, whichever entity or key they belong to. A counter update changes the number inside the store with a single SQL statement, and creates the row first if it is missing. It is the right scope for totals and counters, such as the number of orders or the revenue of a shop. See [Aggregate Projections](#aggregate-projections).

Two details matter in practice:

- A counter update is applied to the row whose ID the event was recorded under. To keep one row of totals, record all the events under one fixed ID, for example a date or `"shop"`. Otherwise each event updates a different row.
- A global projection has no `EntityPath`, so its store looks for an ID column called `id`. Name the ID field of the row `id`.

### Choosing a scope

- Use `PerEntity` when each thing has its own state and you look it up by its own ID.
- Use `CrossEntity` when you want things grouped by something inside the event, and each group kept in its own file.
- Use `Global` when you only want totals and counters over all events.

## Multi-Source Projections

Cross-entity projections receive events from multiple sources and route them by a key extracted from the event:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class RepoListEntry(@Modifier.id id: String, ownerId: String, repoName: String)
object RepoListEntry {
  implicit val schema: Schema[RepoListEntry]         = Schema.derived[RepoListEntry]
  implicit val entityPath: EntityPath[RepoListEntry] = EntityPath.derived[RepoListEntry]
}

val spec = Projection[RepoListEntry]("repoListEntries")
  .from("repos")
  .routedBy[RepoCreated](_.ownerId)
  .on[RepoCreated]
  .custom((e, _) => ProjectionAction.Upsert(RepoListEntry(e.ownerId, e.ownerId, e.repoName)))

// spec.scope == ProjectionScope.CrossEntity(extractor)
```

The `routedBy` call tells the engine to extract a routing key from each event. Events with the same key go to the same shard store, so querying `engine.query(spec, "alice")` searches Alice's shard first.

**Routing modes:**

| Mode | Behavior | Scope derived |
| --- | --- | --- |
| `.routeToSelf` | Uses `ProjectionContext#entityId` as key | `PerEntity` |
| `.routedBy[E](_.field)` | Extracts key from event | `CrossEntity` |
| `.routeToAll` | All events go to same store | `Global` (if `isGlobal`) |

## Aggregate Projections

Global projections aggregate events from multiple sources into a single row (or a few rows keyed by a grouping string). The engine applies atomic counter updates:

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

val spec = Projection
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

The `COALESCE` handles missing rows. The engine runs `INSERT OR IGNORE` before any `UPDATE`, so incrementing a counter that doesn't exist yet creates the row with value `1` (not `0 + 1`).

**Concurrent safety:** Counter operations are atomic at the SQL level. Ten fibers each incrementing the same counter produce the correct total without lost updates.

## Schema Evolution

When you change a projection entity's schema (add a field, rename a column), the engine detects the mismatch and rebuilds the projection from events.

### How it works

1. On startup, `ProjectionEngine` computes `SchemaHash.compute[A]` for each spec's entity type. This is a SHA-256 hash of the schema structure (field names, types, order).
2. The hash is stored in `_projection_meta.schema_hash`.
3. If the stored hash doesn't match the current hash, the engine rebuilds:
   - Truncate the projection store
   - Replay all events from the EventStore through the spec's handlers
   - Store the new hash

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfileV1(@Modifier.id id: String, name: String)
object UserProfileV1 {
  implicit val schema: Schema[UserProfileV1] = Schema.derived[UserProfileV1]
}

case class UserProfileV2(@Modifier.id id: String, name: String, email: String)
object UserProfileV2 {
  implicit val schema: Schema[UserProfileV2] = Schema.derived[UserProfileV2]
}

val hashV1 = SchemaHash.compute[UserProfileV1]
val hashV2 = SchemaHash.compute[UserProfileV2]
// hashV1 != hashV2 because V2 has an extra "email" field
```

### Lazy rebuild

Set `lazyRebuild = true` in `ProjectionEngineConfig` to defer rebuilds. The engine marks specs needing rebuild but doesn't block startup. The first query to a spec triggers its rebuild:

```scala mdoc:compile-only
import zio.blocks.projection.ProjectionEngineConfig

val config = ProjectionEngineConfig(
  batchSize = 100,
  batchTimeout = zio.Duration.fromMillis(50),
  ringCapacity = 4096,
  rebuildParallelism = 4,
  lazyRebuild = true
)
```

### Migration shortcut

For simple `AddField` migrations (adding columns with defaults), the engine uses `ALTER TABLE ADD COLUMN` instead of a full rebuild. This is much faster for large projections:

```scala
import zio.blocks.schema.migration.Migration

// Assumes UserProfileV1 and UserProfileV2 are defined as above
val migration = Migration
  .newBuilder[UserProfileV1, UserProfileV2]
  .addField(_.email, "")
  .build

// Engine detects this is a simple AddField migration
// and uses ALTER TABLE instead of full rebuild
```

## Event Tagging and Migration

Event tags identify variant cases for serialization. By default, the tag is the case class name (e.g., `"UserCreated"`). You can override with numeric tags or rename cases across versions.

### String tags (default)

For a sealed trait `UserEvent` with cases `UserCreated` and `UserDeleted`, the tags are `"UserCreated"` and `"UserDeleted"`.

### Numeric tags

Use the `@eventTag` annotation for stable numeric identifiers:

```scala mdoc:compile-only
import zio.blocks.projection.eventTag
import zio.blocks.schema.{Modifier, Schema}

sealed trait UserEvent
object UserEvent {
  @eventTag(1)
  case class Created(name: String) extends UserEvent
  object Created {
    implicit val schema: Schema[Created] = Schema.derived[Created]
  }

  @eventTag(2)
  case class Deleted(userId: String) extends UserEvent
  object Deleted {
    implicit val schema: Schema[Deleted] = Schema.derived[Deleted]
  }

  implicit val schema: Schema[UserEvent] = Schema.derived[UserEvent]
}
```

### Tag aliases via Migration

When you rename a case, old events in the database still have the old tag. Use `Migration.renameCase` to create an alias:

```scala
import zio.blocks.schema.migration.Migration
import zio.blocks.projection.TagResolver
import zio.blocks.schema.{Modifier, Schema}

sealed trait LoginEvent
object LoginEvent {
  case class UserLoggedIn(userId: String) extends LoginEvent
  object UserLoggedIn {
    implicit val loggedInSchema: Schema[UserLoggedIn] = Schema.derived
  }
  case class UserAuthenticated(userId: String) extends LoginEvent
  object UserAuthenticated {
    implicit val authSchema: Schema[UserAuthenticated] = Schema.derived
  }
  implicit val loginEventSchema: Schema[LoginEvent] = Schema.derived
}

val migration = Migration
  .newBuilder[LoginEvent, LoginEvent]
  .renameCase("UserLoggedIn", "UserAuthenticated")
  .build

val tagInfo = TagResolver.resolve[LoginEvent](migration)
// tagInfo.aliases == Map("UserLoggedIn" -> "UserAuthenticated")
// tagInfo.allTags == Set("UserLoggedIn", "UserAuthenticated")
```

The `TagInfo` object provides:

- `expandRequested(tags)` — returns the union of requested tags plus all their old aliases, so selective reads fetch both old and new events
- `isOldTag(tag)` — true if the tag was renamed
- `currentTagFor(oldTag)` — returns the current tag for an old tag
- `normalize(tag)` — maps old tags to current tags

### Transitive chains

If you rename `A → B` then `B → C`, the resolver collapses this to `A → C`. All three tags are queryable.

### Startup warning

On startup, the `SQLiteEventStore` checks all distinct tags in the database against the known tag set. Unknown tags produce a warning log, not an error. This catches typos or events from a different schema version without crashing the application.

## Testing

The projection module ships with in-memory implementations for unit tests. No SQLite required.

### InMemoryProjectionStore

A `ProjectionStore[A]` backed by `Ref[Map[String, A]]`. Supports all operations: insert, upsert, updateFields, delete, truncate, schema hash tracking.

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

val test: ZIO[Any, Throwable, Unit] = for {
  store <- InMemoryProjectionStore.make[UserProfile]
  _     <- store.insert(UserProfile("u1", "Alice", "alice@example.com"))
  alice <- store.findById("u1")
  // alice == Some(UserProfile("u1", "Alice", "alice@example.com"))
} yield ()
```

### TestEngine

A simple test helper that auto-creates `InMemoryProjectionStore` per projection and `Hub` per source. No manual `makeWithStores` wiring:

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

val projection = Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val test: ZIO[Scope, Throwable, Unit] = for {
  engine <- TestEngine.make(projection)
  _      <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
  result <- engine.query(projection, "user-1")
  // result == Some(UserProfile("user-1", "Alice", "alice@example.com"))
} yield ()
```

### TestContext

Factory for `ProjectionContext` values in tests:

```scala mdoc:compile-only
import zio.blocks.projection.testing.TestContext
import java.time.Instant

val ctx1 = TestContext.make(entityId = "user-1")
// ProjectionContext("user-1", now, 0, None)

val ctx2 = TestContext.makeWithSource(entityId = "user-1", sourceEntityId = "src-1")
// ProjectionContext("user-1", now, 0, Some("src-1"))

val ctx3 = TestContext.withSeq(entityId = "user-1", seq = 42L)
// ProjectionContext("user-1", now, 42, None)
```

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

## API Reference

| Type | Package | Description |
| --- | --- | --- |
| `@Modifier.id` | `zio.blocks.schema.Modifier` | Annotation marking the entity ID field |
| `EntityPath[A]` | `zio.blocks.projection` | Folder name + ID field for a projection entity |
| `@path(name)` | `zio.blocks.projection` | Annotation to override the derived folder name |
| `@eventTag(n)` | `zio.blocks.projection` | Annotation for numeric event tags |
| `Projection[A]` | `zio.blocks.projection` | Defines a projection: name, schema, handlers, routing |
| `ProjectionAction[+A]` | `zio.blocks.projection` | Enum: Insert, Upsert, Update, Delete, Truncate, Noop |
| `FieldUpdate` | `zio.blocks.projection` | Enum: Set, Increment, Decrement, Max, Min |
| `ProjectionContext` | `zio.blocks.projection` | Event metadata: entityId, timestamp, seq, sourceEntityId |
| `EventEnvelope[+E]` | `zio.blocks.projection` | Event wrapper: seq, tag, event, timestamp, entityId |
| `EventStore[E]` | `zio.blocks.projection` | Trait: append, readFrom, readAll, subscribe |
| `SQLiteEventStore[E]` | `zio.blocks.projection` | SQLite-backed EventStore with tag migration |
| `ProjectionStore[A]` | `zio.blocks.projection` | Trait: insert, upsert, updateFields, delete, truncate, findById |
| `InMemoryProjectionStore[A]` | `zio.blocks.projection.testing` | In-memory ProjectionStore for tests |
| `TransactorCache` | `zio.blocks.projection` | LRU cache of SQLite Transactors |
| `TransactorCacheConfig` | `zio.blocks.projection` | Configuration for TransactorCache |
| `ProjectionEngine` | `zio.blocks.projection` | Orchestrates catch-up + live processing |
| `ProjectionEngineConfig` | `zio.blocks.projection` | Engine configuration (batching, rebuild, etc.) |
| `SchemaHash` | `zio.blocks.projection` | SHA-256 hash of Schema structure for evolution detection |
| `SchemaEvolution` | `zio.blocks.projection` | Hash check, rebuild, migration shortcut |
| `SchemaEvolutionConfig` | `zio.blocks.projection` | Evolution config (parallelism, lazy, migration shortcut) |
| `TagResolver` | `zio.blocks.projection` | Builds TagInfo from Schema + optional Migration |
| `TagInfo` | `zio.blocks.projection` | Alias map, all tags, old tag detection, value migration |
| `AggregateProjection` | `zio.blocks.projection` | Helpers for global aggregate specs with counters |
| `TestEngine` | `zio.blocks.projection.testing` | Simple test engine with auto-created stores and hubs |
| `TestProjectionEngine` | `zio.blocks.projection.testing` | Synchronous test engine (deprecated, use TestEngine) |
| `TestContext` | `zio.blocks.projection.testing` | Factory for ProjectionContext in tests |

### Projection methods

| Method | Description |
| --- | --- |
| `Projection[A](name)` | Create a per-entity spec (requires `Schema[A]` + `EntityPath[A]`) |
| `Projection.global[A](name)` | Create a global spec (requires `Schema[A]`) |
| `.from(sourceName)` | Bind to a named event source |
| `.on[E]` | Register a handler for event type `E` |
| `.insert((E, ProjectionContext) => A)` | Handler: insert a new entity |
| `.update((E, ProjectionContext) => A)` | Handler: replace the entity |
| `.updateWithField(fieldName, (E, ProjectionContext) => Any)` | Handler: update a single field |
| `.delete` | Handler: delete the entity |
| `.custom((E, ProjectionContext) => ProjectionAction[A])` | Handler: arbitrary action |
| `.aggregate(FieldUpdate)` | Handler: atomic counter update |
| `.routedBy[E](E => String)` | Route events by extracted key |
| `.routeToSelf` | Route events by `ProjectionContext#entityId` |
| `.routeToAll` | Send all events to same store |
| `spec.scope` | Derived scope: `PerEntity`, `CrossEntity`, or `Global` |

### ProjectionEngine methods

| Method | Description |
| --- | --- |
| `ProjectionEngine.make(specs*)` | Create engine with default config (requires `Scope`) |
| `ProjectionEngine.makeWithConfig(config, specs*)` | Create engine with custom config |
| `engine.start` | Start catch-up + live processing (requires `Scope`) |
| `engine.query(spec, entityId)` | Query an entity by ID |
| `engine.queryByName(specName, entityId)` | Query by spec name string |
| `engine.registerMigration(spec, migration)` | Register a migration for schema evolution |
| `ProjectionEngine#transactorCache` | Access the underlying TransactorCache |
