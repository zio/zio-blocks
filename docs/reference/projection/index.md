---
id: index
title: Projection
sidebar_label: Overview
description: "Build event-sourced read models declaratively: transform an append-only event log into queryable per-entity stores."
keywords:
  - "Event Sourcing"
  - "Read Models"
  - "Projection Engine"
  - "Schema Evolution"
  - "Declarative Handlers"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

The projection module turns an append-only event log into queryable read models with a purely declarative API. You describe — once, in a builder chain — which events a model listens to, how they route to entity stores, and what each kind of event does to the model. An engine then reads those descriptions and handles replay, watermark tracking, schema-change detection, and live streaming automatically. Core types: [`Projection[A]`](./projection.md), [`ProjectionEngine`](./engine.md), `EventStore[E]`, `EventEnvelope[E]`, `EventTag`, `ProjectionStore[A]`, `ProjectionAction[A]`, and `FieldUpdate`.

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
- **Events are permanent.** Once written they stay as they are, so renaming an event or changing its shape needs care. The [Event Tagging and Migration](./projection.md#event-tagging-and-migration) section covers this.

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

- it reads historical events to catch up, then follows live events. It saves its position after every event it applies, so a restart resumes exactly where it left off;
- it routes each event to the entity (or entities) that should see it, following the routing declared once on the projection (`routeToSelf`, `routedBy`, `routeToAll`);
- it detects a changed schema and rebuilds the read model, with a shortcut for added fields and migrations to map old event tags to new ones;
- it stores each projection in its own SQLite file, so read models are isolated, individually backed up, and queryable offline.

Because the same declaration runs against an in-memory store, you can test a projection by appending events and querying the result, with no database. The [Quick Start](#quick-start) shows the whole loop in about twenty lines.

Projection is a good fit when you already treat events as the source of truth and want fast, local, query-friendly views of them. It is not a general query engine over arbitrary data, and the read models it maintains are derived state: they can always be discarded and rebuilt from the events.

## Installation

The core module is sufficient to compile projections and run tests:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "@VERSION@"
```

For durable SQLite persistence on the JVM, add the JDBC driver:

```scala
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The library does not bring `sqlite-jdbc` with it, so add it to your own build if you want SQLite persistence. Without it, `ProjectionEngine.make` silently falls back to `InMemoryProjectionStore`, so data is lost on restart. The same fallback happens on any other error while opening a SQLite store, not only a missing driver.

A [`TransactorCache`](./engine.md#connection-management) loads the SQLite driver by name when it first opens a file, so `sqlite-jdbc` must be on the runtime classpath whenever you use one directly, for example to build a `SQLiteEventStore`. Without it, opening the first file fails with `ClassNotFoundException: org.sqlite.JDBC`.

## Overview

Two types carry the whole lifecycle. `Projection[A]` is the descriptor — a pure, immutable value that names the read model, lists its sources, and records a handler per event type. `ProjectionEngine` is the runtime — it reads `Projection[A]` values, connects each one to an `EventStore[E]`, and drives events through the right handler into a `ProjectionStore[A]`. Neither type knows about the other's internals; `Projection[A]` holds no fibers and `ProjectionEngine` does not inspect the handler functions.

Several supporting types fill the gaps. `EventStore[E]` is the write-side contract: append an event and receive a monotonic sequence number; read history with `readFrom`; subscribe to a live `Hub` for new events. `EventEnvelope[E]` wraps each stored event with that sequence number, a string tag (the event class name or a numeric `@eventTag` value), a timestamp, and the entity ID. `ProjectionStore[A]` is the read-side contract: rows keyed by entity ID, a watermark, and a schema hash. `ProjectionAction[A]` is what a handler returns — `Insert`, `Upsert`, `Update` (field-level deltas via `FieldUpdate`), `Delete`, `Truncate`, or `Noop`. `TestEngine` wraps all of this for testing: one `make` call replaces the full JVM wiring.

For the complete API reference, see the [`Projection[A]`](./projection.md) subpage (the builder and descriptor) and the [`ProjectionEngine`](./engine.md) subpage (the runtime and its configuration).

## How They Work Together

A projection goes through seven steps from declaration to query:

1. **Declare** — call `Projection[A](name)` (per-entity) or `Projection.global[A](name)` (shared aggregate). This produces an empty, immutable descriptor holding the schema and entity path.
2. **Bind a source** — call `Projection#from(sourceName)` then one of `FromBuilder#routeToSelf`, `FromBuilder#routedBy[E](_.field)`, or `FromBuilder#routeToAll`. This adds a `SourceBinding` that names the event store to read from and records the routing decision.
3. **Add handlers** — call `Projection#on[E]` then a `HandlerBuilder` verb such as `insert(...)`, `aggregate(FieldUpdate.Increment(...))`, or `custom(...)`. Each handler is a pure function `(E, ProjectionContext) => ProjectionAction[A]` that describes what to do but performs no effects.
4. **Create the engine** — call `TestEngine.make(projection)` in tests or `ProjectionEngine.makeWithStores(...)` on the JVM with real SQLite stores.
5. **Append events** — call `eventStore.append(entityId, event)`. The store assigns a monotonic `seq`, wraps the event in an `EventEnvelope`, and publishes it to its `Hub`.
6. **Automatic processing** — the engine's catch-up and live fibers dequeue each `EventEnvelope`, build a `ProjectionContext` from the envelope metadata, and call `projection.handle(event, ctx, source)`. The returned `Option[ProjectionAction[A]]` (if non-empty) is applied to the right `ProjectionStore[A]` (chosen by the routing key), and the watermark is advanced to the envelope's `seq`.
7. **Query** — call `engine.query(projection, entityId)`, which delegates to `ProjectionStore[A].findById(entityId)`.

The data flow among the types is:

```
┌─────────────────────┐              ┌──────────────────────┐
│ Application         │─ append() ──►│ EventStore[E]        │
└─────────────────────┘              └──────────┬───────────┘
                                                │ readFrom / subscribe
                                                │ EventEnvelope[E]
                                                ▼
┌─────────────────────┐        ┌────────────────────────────┐
│ Projection[A]       │        │    ProjectionEngine        │
│ (descriptor)        │◄───────│                            │
│                     │handle()│                            │
│                     │───────►│  ProjectionAction[A]       │
└─────────────────────┘        └─────────────┬──────────────┘
                                             │ insert / upsert /
                                             │ updateFields / delete
                                             ▼
                               ┌─────────────────────────────┐
                               │    ProjectionStore[A]        │
                               │    (SQLite or InMemory)      │
                               └─────────────────────────────┘
                                             ▲
                               engine.query(projection, id)
```

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

## Common Patterns

Three routing modes drive three distinct projection shapes. Choose by how the data is partitioned: one row per originating entity, one shard per a key extracted from the event, or one shared row for the whole projection.

### Per-Entity Projection

Each entity ID gets its own row. Use `Projection[A](name)` — which requires [`Schema[A]`](../schema/schema.md) and [`EntityPath[A]`](./projection.md#entity-paths) — with `routeToSelf`. The store file on disk is `<basePath>/<name>.db`, and each row is addressed by `ctx.entityId`:

```scala
import zio.blocks.projection._

// One row per user — UserProfile("user-1", ...) stored at entity ID "user-1"
val userProfiles: Projection[UserProfile] =
  Projection[UserProfile]("userProfiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
    .on[UserEmailChanged]
    .updateWithField("email", (e, _) => e.newEmail)
```

This is the default shape. `EntityPath.derived[A]` finds the field annotated `@Modifier.id` (or named `id`), uses it as the entity-ID column, and derives the folder name by stripping any trailing `Id` suffix, snake-casing the result, and pluralising it (e.g., `userId` → strip → `user` → snake-case → `user` → pluralise → `users`).

### Cross-Entity (Sharded) Projection

Events recorded under one entity ID are grouped into shards keyed by a field inside the event. Use `FromBuilder#routedBy[E](_.field)` to declare the sharding key. The engine creates a separate shard file for each distinct key value, for example `customer_orders_shard_alice.db` alongside `customer_orders_shard_bob.db`:

```scala
import zio.blocks.projection._

// Orders grouped by customer — each customer's orders live in their own shard
val customerOrders: Projection[CustomerOrder] =
  Projection[CustomerOrder]("customer_orders")
    .from("orders")
    .routedBy[OrderEvent](_.customerId)   // shard key read from the event
    .on[OrderPlaced]
    .insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product, e.total, "placed"))
    .on[OrderShipped]
    .updateWithField("status", (_, _) => "shipped")
```

Both `OrderPlaced` and `OrderShipped` carry `customerId`, so they always land in the same customer's shard. Within a shard, rows are keyed by `ctx.entityId` (the order ID), so `OrderShipped` finds and updates the row that `OrderPlaced` created.

### Global Aggregate Projection

One shared row accumulates totals from any number of sources. Use `Projection.global[A](name)` with `routeToAll` on every source, and handle events with `aggregate(FieldUpdate.Increment(...))` for atomic counter updates. The store lives in `global/<name>.db`:

```scala
import zio.blocks.projection._

// One row, updated atomically by events from two separate sources
val dailyStats: Projection[DailyStats] =
  Projection.global[DailyStats]("dailyStats")
    .from("users")
    .routeToAll
    .on[UserCreated]
    .aggregate(FieldUpdate.Increment("user_count", 1L))
    .from("repos")
    .routeToAll
    .on[RepoCreated]
    .aggregate(FieldUpdate.Increment("repo_count", 1L))
```

`FieldUpdate.Increment` translates to `COALESCE(col, 0) + ?` in SQLite, so the counter is safe even if the row does not exist yet. The row's ID is `ctx.entityId`, which the application sets to a stable key such as a date string (`"2025-08-26"`) when appending events.

## Integration Points

The projection module depends on three sibling modules:

- **`zio-blocks-schema`** — `Schema[A]` drives DDL generation, JSON encoding/decoding of event payloads, `DynamicValue` manipulation for in-memory field updates, `Migration` for schema-evolution shortcuts, and `Modifier.id` for `EntityPath` derivation.
- **`zio-blocks-sql`** — [`Transactor`](../sql/transactor.md) and [`DbCon`](../sql/db-con.md) back the SQLite implementations of `EventStore[E]` and `ProjectionStore[A]`. [`SqlNameMapper`](../sql/sql-name-mapper.md) maps Scala field names to snake_case column names for both DDL and `FieldUpdate`.
- **ZIO core and ZIO Streams** — `Task`, `ZIO`, and `Scope` are used throughout the engine lifecycle; `ZStream` and `Hub` carry the event streams.

## Event Store

`EventStore[E]` is the write side of the module: an append-only log for one event type `E`. It assigns a monotonic sequence number to each event, streams history on demand, and broadcasts live events to a `Hub`:

```scala
import zio._
import zio.stream.ZStream
import zio.blocks.projection._

trait EventStore[E] {
  def append(entityId: String, event: E): Task[Long]
  def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
  def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
  def subscribe: Hub[EventEnvelope[E]]
}
```

`EventStore#append` returns the allocated sequence number, which the store records in an `events` table as the row's primary key. `EventStore#readFrom` streams all events after a given `seq`, optionally filtered to specific tags — the engine uses this for catch-up replay. `EventStore#subscribe` returns the live `Hub`; the engine subscribes when it transitions from catch-up to streaming mode.

### EventEnvelope

Every event in the store is wrapped in an `EventEnvelope`:

```scala
import zio.blocks.projection._

final case class EventEnvelope[+E](
  seq: Long,       // monotonic sequence number; used as the watermark
  tag: String,     // event type identifier (class name or @eventTag number)
  event: E,        // the original event value
  timestamp: java.time.Instant,
  entityId: String // the entity the event was recorded under
)
```

The `seq` field is the resumption position: the engine saves it after each successful event and passes it to `readFrom` on restart so no event is re-processed. The `tag` field identifies the event kind for filtering and for `TagResolver`'s rename detection.

### Event Tags

By default, `EventStore[E]` uses the case class simple name as the tag. Renaming a case class therefore changes the tag that new events are stored under, leaving old events unreadable under the new name.

There are two ways to keep old events readable: give each case a stable numeric tag with `@eventTag(n)`, or describe the rename with a `Migration` so that the store knows the old name. Both are explained in [Event Tagging and Migration](./projection.md#event-tagging-and-migration).

## Projection Persistence

`ProjectionStore[A]` is the read side of the module: a persistence contract for projection rows. Each `ProjectionStore[A]` also holds the engine's watermark and the schema hash alongside the entity rows:

```scala
import zio._
import zio.blocks.projection._

trait ProjectionStore[A] {
  // Row mutations
  def insert(a: A): Task[Unit]
  def upsert(a: A): Task[Unit]
  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]
  def delete(entityId: String): Task[Unit]
  def truncate: Task[Unit]

  // Row reads
  def findById(entityId: String): Task[Option[A]]

  // Watermark — last successfully processed event seq
  def getLastProcessedSeq: Task[Long]
  def updateLastProcessedSeq(seq: Long): Task[Unit]

  // Schema fingerprint — triggers rebuild when changed
  def getSchemaHash: Task[Option[String]]
  def updateSchemaHash(hash: String): Task[Unit]
}
```

`ProjectionStore#upsert` uses `INSERT ... ON CONFLICT DO UPDATE` so no read-before-write is needed. `ProjectionStore#updateFields` runs field-level mutations inside a single transaction — it inserts a default row first with `INSERT OR IGNORE`, then applies each `FieldUpdate` in one round trip.

### FieldUpdate

`FieldUpdate` describes a single field-level mutation and is used inside a `ProjectionAction.Update`:

<Tabs groupId="scala-version" defaultValue="scala2">
<TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.projection._

sealed abstract class FieldUpdate
object FieldUpdate {
  final case class Set(field: String, value: Any)     extends FieldUpdate
  final case class Increment(field: String, by: Long) extends FieldUpdate
  final case class Decrement(field: String, by: Long) extends FieldUpdate
  final case class Max(field: String, value: Long)    extends FieldUpdate
  final case class Min(field: String, value: Long)    extends FieldUpdate
}
```

</TabItem>
<TabItem value="scala3" label="Scala 3">

```scala
import zio.blocks.projection._

enum FieldUpdate {
  case Set(field: String, value: Any)     extends FieldUpdate
  case Increment(field: String, by: Long) extends FieldUpdate
  case Decrement(field: String, by: Long) extends FieldUpdate
  case Max(field: String, value: Long)    extends FieldUpdate
  case Min(field: String, value: Long)    extends FieldUpdate
}
```

</TabItem>
</Tabs>

`Increment`, `Decrement`, `Max`, and `Min` are atomic in SQLite (`COALESCE(col, 0) + ?`, `MAX(col, ?)`, etc.), so no separate read is required. Field names can be given as raw snake_case strings (`FieldUpdate.Increment("user_count", 1L)`) or via a compile-time selector that maps Scala field names automatically (`FieldUpdate.increment[Stats](_.userCount)`).

### Implementations

`SQLiteProjectionStore[A]` is the production implementation on the JVM. The engine creates it automatically when `sqlite-jdbc` is on the classpath, storing each projection in a separate SQLite file under `<entityPath.basePath>/<name>.db`. Each file runs in WAL mode so readers never block writers.

`InMemoryProjectionStore[A]` is a `Map`-backed implementation intended for tests. It simulates `FieldUpdate` operations by decoding the stored entity into a [`DynamicValue`](../schema/dynamic-value.md), applying the mutation, and re-encoding. `TestEngine.make` creates one automatically per projection.

## Testing

`TestEngine` replaces the full JVM wiring for unit and integration tests. It creates an `InMemoryProjectionStore[A]` per projection, an in-memory `EventStore` per source name, starts the underlying `ProjectionEngine`, and manages the whole lifecycle in a single `Scope`. The [Testing](./testing.md) page documents it, together with `InMemoryProjectionStore`, `TestContext` and `TestProjectionEngine`.

## Type Pages

The two core types each have a dedicated reference page with complete API coverage. [`Projection[A]`](./projection.md) documents the descriptor, its builder chain (`from`, `on`, routing methods, handler methods), `ProjectionAction`, `FieldUpdate` factories, `ProjectionScope`, and `EntityPath`. [`ProjectionEngine`](./engine.md) documents the runtime, `ProjectionEngineConfig`, watermark and schema-hash mechanics, schema evolution, `TransactorCache`, and the production store implementations. [Testing](./testing.md) documents the in-memory helpers: `TestEngine`, `InMemoryProjectionStore`, `TestContext`, and `TestProjectionEngine`.

## See Also

The [SQL Module](../sql/index.md) supplies the `Transactor`, `DbCon`, and `SqlNameMapper` types that back the SQLite implementations of `EventStore[E]` and `ProjectionStore[A]`. The [Data Migration](../data-migration.md) reference covers typed online schema migrations that pair naturally with projection-backed read models during schema evolution.
