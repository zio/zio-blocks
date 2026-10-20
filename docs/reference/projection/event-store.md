---
id: event-store
title: "EventStore[E]"
sidebar_label: "EventStore[E]"
description: "The write side of the projection module: EventStore[E], the append-only log of events, with EventEnvelope and the SQLite implementation."
keywords:
  - "Event Sourcing"
  - "Event Store"
  - "EventEnvelope"
  - "Event Tags"
  - "Append-Only Log"
---

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

The examples on this page share the setup below: an event type with two cases, a helper that gets the `Transactor` of a database file, and an effect that opens a store with [`SQLiteEventStore.make`](#creating-a-store--make):

```scala mdoc:silent
import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration

sealed trait UserEvent
object UserEvent {
  case class UserCreated(name: String, email: String) extends UserEvent
  case class EmailChanged(newEmail: String)            extends UserEvent
  implicit val schema: Schema[UserEvent] = Schema.derived[UserEvent]
}
import UserEvent.*

// The Transactor of a database file, from a cache that is closed with the Scope
def transactor(file: String) = TransactorCache.make().flatMap(_.get(file))

// The store kept in events/users.db
val openStore = transactor("events/users.db").flatMap(tx => SQLiteEventStore.make[UserEvent](tx))
```

## Appending Events — `append`

```scala
def append(entityId: String, event: E): Task[Long]
```

`append` records `event` under the entity `entityId` and returns the sequence number the store gave it. Sequence numbers start at 1 and only grow, so they also give the order of the events. The event is saved with its tag, the case name or its `@eventTag` number, and the current time. Once it is stored, its envelope is published to the store's [hub](#following-new-events--subscribe):

```scala mdoc:compile-only
val appended = openStore.flatMap { store =>
  for {
    a <- store.append("user-1", UserCreated("Alice", "alice@example.com"))
    b <- store.append("user-2", UserCreated("Bob", "bob@example.com"))
    c <- store.append("user-1", EmailChanged("alice@zio.dev"))
  } yield List(a, b, c) // List(1, 2, 3) on a new store
}
```

## Reading Events — `readFrom` and `readAll`

```scala
def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
```

`readFrom` streams the stored events whose sequence number is strictly greater than `afterSeq`, in order, each in its [`EventEnvelope`](#eventenvelope). `readAll` is `readFrom(0L, tags)`, so it streams every event. Both read from the database as the stream is pulled, rather than loading the whole log first. The engine uses `readFrom` to catch up from its saved position, and a schema rebuild uses `readAll` to replay everything.

`tags` keeps only the events with one of the given tags, and an empty set, the default, keeps all of them. Which stored tags a read can return is described under [Which Stored Events Are Read](./migration.md#which-stored-events-are-read).

After the three appends above, these reads return:

```scala mdoc:compile-only
val reads = openStore.flatMap { store =>
  for {
    all     <- store.readAll().runCollect                    // events 1, 2 and 3
    after1  <- store.readFrom(1L).runCollect                 // events 2 and 3
    changed <- store.readAll(Set("EmailChanged")).runCollect // event 3 only
  } yield (all, after1, changed)
}
```

## Following New Events — `subscribe`

```scala
def subscribe: Hub[EventEnvelope[E]]
```

`subscribe` returns the store's hub, the ZIO `Hub` that every appended envelope is published to. Despite its name it does not subscribe by itself: call `subscribe` on the hub, inside a `Scope`, to get a queue of the envelopes appended from then on. Events stored earlier are not in that queue; read them with `readFrom`. The engine does both: it catches up first, then follows the hub:

```scala mdoc:compile-only
val next = openStore.flatMap { store =>
  for {
    queue <- store.subscribe.subscribe
    _     <- store.append("user-3", UserCreated("Charlie", "charlie@example.com"))
    env   <- queue.take // event 4, the UserCreated of user-3
  } yield env
}
```


## EventEnvelope

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

The engine saves `seq` as its watermark after each event it applies, and resumes after it on restart.

## SQLiteEventStore

`SQLiteEventStore[E]` is the implementation of `EventStore[E]` that keeps the events in a SQLite file. Each event is a row of the `events` table, whose columns are `seq`, `tag`, `payload`, `ts` and `entityId`: `seq` is the row's primary key and gives the sequence number, and `payload` is the event encoded as JSON. An index on `tag` and `seq` serves filtered reads.

Its constructor is private. Every factory below takes a `Transactor` for the database file, usually from a [`TransactorCache`](./projection-engine.md#connection-management), and creates the table and its index if they are missing.

### Creating a Store — `make`

```scala
object SQLiteEventStore {
  def make[E: Schema](transactor: Transactor): Task[SQLiteEventStore[E]]
  def make[E: Schema](transactor: Transactor, migration: Migration[E, E]): Task[SQLiteEventStore[E]]
}
```

`make` is the usual way to create a store. It creates a bounded hub of 4,096 envelopes for the store to publish to, and works out the store's tags from the event type with `TagResolver`. Pass a `migration` when a case of the event type was renamed; see [Renaming an Event Case](./migration.md#renaming-an-event-case). When the store starts, it logs a warning for the tags in the database that it cannot explain.

```scala mdoc:silent
// UserCreated used to be called UserRegistered
val migration = Migration
  .newBuilder[UserEvent, UserEvent]
  .renameCase("UserRegistered", "UserCreated")
  .build
```

The store is then created with the migration:

```scala mdoc:compile-only
val renamed = transactor("events/renamed.db").flatMap(tx => SQLiteEventStore.make[UserEvent](tx, migration))
```

### The Store's Tags — `tagInfo`

```scala
val tagInfo: TagInfo
```

`tagInfo` is the [`TagInfo`](./migration.md#taginfo-and-tagresolver) the store was created with: the tags the event type has now, the old tags that were renamed to them, and the migration for old events. `make` works it out with `TagResolver.resolve`, so the `tagInfo` of the two stores above can be computed without a store:

```scala mdoc
TagResolver.resolve[UserEvent].currentTags
TagResolver.resolve[UserEvent].aliases
TagResolver.resolve[UserEvent](migration).aliases
TagResolver.resolve[UserEvent](migration).allTags
```

### Bringing Your Own TagInfo — `makeWithResolver`

```scala
object SQLiteEventStore {
  def makeWithResolver[E: Schema](transactor: Transactor, tagInfo: TagInfo): Task[SQLiteEventStore[E]]
}
```

`makeWithResolver` takes a `TagInfo` you built yourself, for example with `TagResolver.resolve`, instead of working it out. `make` is a shortcut for it:

```scala mdoc:silent
val info = TagResolver.resolve[UserEvent](migration)
```

The store then has `info` as its `tagInfo`:

```scala mdoc:compile-only
val resolved = transactor("events/resolved.db").flatMap(tx => SQLiteEventStore.makeWithResolver[UserEvent](tx, info))
```

### Choosing the Hub — `makeWithHub`

```scala
object SQLiteEventStore {
  def makeWithHub[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): Task[SQLiteEventStore[E]]
  def makeWithHub[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], migration: Migration[E, E]): Task[SQLiteEventStore[E]]
  def makeWithHub[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], tagInfo: TagInfo): Task[SQLiteEventStore[E]]
}
```

`makeWithHub` uses the hub you give it instead of creating one, for example to choose its capacity or its strategy when it is full. The three overloads differ in how the tags are worked out, the same way as `make` with and without a migration, and `makeWithResolver`:

```scala mdoc:compile-only
// The Transactor of a file, and a new sliding hub of 16 envelopes
def txAndHub(file: String) = transactor(file).zip(Hub.sliding[EventEnvelope[UserEvent]](16))

val withHub = txAndHub("events/hub.db").flatMap { case (tx, hub) =>
  SQLiteEventStore.makeWithHub[UserEvent](tx, hub)
}
val withHubAndMigration = txAndHub("events/hub-migration.db").flatMap { case (tx, hub) =>
  SQLiteEventStore.makeWithHub[UserEvent](tx, hub, migration)
}
val withHubAndInfo = txAndHub("events/hub-info.db").flatMap { case (tx, hub) =>
  SQLiteEventStore.makeWithHub[UserEvent](tx, hub, info)
}
```

Each store publishes to the hub it was given: its `subscribe` returns that hub.

### Blocking Construction — `makeBlocking` and `makeBlockingWithResolver`

```scala
object SQLiteEventStore {
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): SQLiteEventStore[E]
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], migration: Migration[E, E]): SQLiteEventStore[E]
  def makeBlockingWithResolver[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], tagInfo: TagInfo): SQLiteEventStore[E]
}
```

These return the store directly instead of a `Task`, for tests that need to build one outside an effect. They work on the calling thread, and unlike the factories above they do not check the tags in the database. As with `makeWithHub`, you pass the hub:

```scala mdoc:compile-only
val blocking = for {
  hub <- Hub.bounded[EventEnvelope[UserEvent]](16)
  tx1 <- transactor("events/blocking.db")
  tx2 <- transactor("events/blocking-migration.db")
  tx3 <- transactor("events/blocking-info.db")
} yield (
  SQLiteEventStore.makeBlocking[UserEvent](tx1, hub),
  SQLiteEventStore.makeBlocking[UserEvent](tx2, hub, migration),
  SQLiteEventStore.makeBlockingWithResolver[UserEvent](tx3, hub, info)
)
```

## See Also

- [Projection module overview](./index.md) — how the event store, the projections, the engine and the projection stores fit together.
- [Projection[A]](./projection.md) — the projections that read the event store.
- [Migration](./migration.md) — renamed event cases, `@eventTag`, and changed event fields.
- [ProjectionEngine](./projection-engine.md) — the runtime that replays and follows an event store.
