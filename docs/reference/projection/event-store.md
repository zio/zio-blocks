---
id: event-store
title: "EventStore[E]"
sidebar_label: "EventStore[E]"
description: "The write side of the projection module: EventStore[E], the append-only log of events, with EventEnvelope and event tags."
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

The examples on this page run against a real SQLite file and show what each call returns. They share the setup below: an event type with two cases, a helper that runs an effect, and one store created with [`SQLiteEventStore.make`](#creating-a-store--make), which is described further down:

```scala mdoc:silent
import zio.*
import zio.stream.ZStream
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

// Runs an effect and returns its result
def run[A](effect: ZIO[Any, Throwable, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }

// A fresh folder for the database files of this page
val folder = java.nio.file.Files.createTempDirectory("event-store-docs").toString
val cache  = run(TransactorCache.makeUnscoped())
val store  = run(cache.get(s"$folder/users.db").flatMap(tx => SQLiteEventStore.make[UserEvent](tx)))
```

## Appending Events — `append`

```scala
def append(entityId: String, event: E): Task[Long]
```

`append` records `event` under the entity `entityId` and returns the sequence number the store gave it. Sequence numbers start at 1 and only grow, so they also give the order of the events. The event is saved with its tag, the case name or its `@eventTag` number, and the current time. Once it is stored, its envelope is published to the store's hub, so live subscribers see it too:

```scala mdoc
run(store.append("user-1", UserCreated("Alice", "alice@example.com")))
run(store.append("user-2", UserCreated("Bob", "bob@example.com")))
run(store.append("user-1", EmailChanged("alice@zio.dev")))
```

## Reading Events — `readFrom` and `readAll`

```scala
def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
```

`readFrom` streams the stored events whose sequence number is strictly greater than `afterSeq`, in order, each in its [`EventEnvelope`](#eventenvelope). `readAll` is `readFrom(0L, tags)`, so it streams every event. Both read from the database as the stream is pulled, rather than loading the whole log first. The engine uses `readFrom` to catch up from its saved position, and a schema rebuild uses `readAll` to replay everything.

`tags` keeps only the events with one of the given tags, and an empty set, the default, keeps all of them. A tag that was renamed is matched under its old name too, and the envelope always carries the current name, see [Event Tags](#event-tags).

The examples print each envelope without its timestamp, which changes on every run:

```scala mdoc:silent
def show(events: ZStream[Any, Throwable, EventEnvelope[UserEvent]]) =
  run(events.map(e => (e.seq, e.tag, e.entityId, e.event)).runCollect)
```

```scala mdoc
show(store.readAll())
show(store.readFrom(1L))
show(store.readAll(Set("EmailChanged")))
```

## Following New Events — `subscribe`

```scala
def subscribe: Hub[EventEnvelope[E]]
```

`subscribe` returns the store's hub, the ZIO `Hub` that every appended envelope is published to. Despite its name it does not subscribe by itself: call `subscribe` on the hub, inside a `Scope`, to get a queue of the envelopes appended from then on. Events stored earlier are not in that queue; read them with `readFrom`. The engine does both: it catches up with `readFrom`, then follows the hub:

```scala mdoc
run(ZIO.scoped {
  for {
    queue <- store.subscribe.subscribe
    _     <- store.append("user-3", UserCreated("Charlie", "charlie@example.com"))
    next  <- queue.take
  } yield (next.seq, next.tag, next.entityId, next.event)
})
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

The `seq` field is the resumption position: the engine saves it after each successful event and passes it to `readFrom` on restart so no event is re-processed. The `tag` field identifies the event kind for filtering and for `TagResolver`'s rename detection.

## Event Tags

By default, `EventStore[E]` uses the case class simple name as the tag. Renaming a case class therefore changes the tag that new events are stored under, and the events stored under the old name are no longer read.

A read keeps only the events whose tag the store knows: the current tags, and the old names of renamed cases declared with a `Migration`. Events under any other tag are skipped without an error. To keep a renamed case readable, see [Renaming an Event Case](./migration.md#renaming-an-event-case); for numeric tags, see [Numeric Tags](./migration.md#numeric-tags--eventtag).

## SQLiteEventStore

`SQLiteEventStore[E]` is the implementation of `EventStore[E]` that keeps the events in a SQLite file. Each event is a row of the `events` table, whose columns are `seq`, `tag`, `payload`, `ts` and `entityId`: `seq` is the row's primary key and gives the sequence number, and `payload` is the event encoded as JSON. An index on `tag` and `seq` serves filtered reads.

Its constructor is private. Every factory below takes a `Transactor` for the database file, usually from a [`TransactorCache`](./engine.md#connection-management), and creates the table and its index if they are missing.

### Creating a Store — `make`

```scala
object SQLiteEventStore {
  def make[E: Schema](transactor: Transactor): Task[SQLiteEventStore[E]]
  def make[E: Schema](transactor: Transactor, migration: Migration[E, E]): Task[SQLiteEventStore[E]]
}
```

`make` is the usual way to create a store. It creates a bounded hub of 4,096 envelopes for the store to publish to, and works out the store's tags from the event type with `TagResolver`. Pass a `migration` when a case of the event type was renamed: its `renameCase` steps tell the store the old names, so events saved under them are still found, see [Renaming an Event Case](./migration.md#renaming-an-event-case). When the store starts, it compares the tags in the database with the ones it knows, and logs a warning for any it cannot explain.

```scala mdoc:silent
// UserCreated used to be called UserRegistered
val migration = Migration
  .newBuilder[UserEvent, UserEvent]
  .renameCase("UserRegistered", "UserCreated")
  .build

val renamed = run(cache.get(s"$folder/renamed.db").flatMap(tx => SQLiteEventStore.make[UserEvent](tx, migration)))
```

```scala mdoc
renamed.tagInfo.aliases
```

### The Store's Tags — `tagInfo`

```scala
val tagInfo: TagInfo
```

`tagInfo` is the [`TagInfo`](./migration.md#taginfo-and-tagresolver) the store was created with: the tags the event type has now, the old tags that were renamed to them, and the migration for old events. The store uses it to widen filtered reads, to decode old events, and for the check at startup:

```scala mdoc
store.tagInfo.currentTags
store.tagInfo.aliases
renamed.tagInfo.allTags
```

### Bringing Your Own TagInfo — `makeWithResolver`

```scala
object SQLiteEventStore {
  def makeWithResolver[E: Schema](transactor: Transactor, tagInfo: TagInfo): Task[SQLiteEventStore[E]]
}
```

`makeWithResolver` takes a `TagInfo` you built yourself, for example with `TagResolver.resolve`, instead of working it out. `make` is a shortcut for it:

```scala mdoc:silent
val info     = TagResolver.resolve[UserEvent](migration)
val resolved = run(cache.get(s"$folder/resolved.db").flatMap(tx => SQLiteEventStore.makeWithResolver[UserEvent](tx, info)))
```

```scala mdoc
resolved.tagInfo == info
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

```scala mdoc:silent
val sliding = run(Hub.sliding[EventEnvelope[UserEvent]](16))
val withHub = run(cache.get(s"$folder/hub.db").flatMap(tx => SQLiteEventStore.makeWithHub[UserEvent](tx, sliding)))

val withHubAndMigration = run(
  Hub.bounded[EventEnvelope[UserEvent]](16).zip(cache.get(s"$folder/hub-migration.db")).flatMap { case (hub, tx) =>
    SQLiteEventStore.makeWithHub[UserEvent](tx, hub, migration)
  }
)
val withHubAndInfo = run(
  Hub.bounded[EventEnvelope[UserEvent]](16).zip(cache.get(s"$folder/hub-info.db")).flatMap { case (hub, tx) =>
    SQLiteEventStore.makeWithHub[UserEvent](tx, hub, info)
  }
)
```

```scala mdoc
withHub.subscribe eq sliding
withHubAndMigration.tagInfo.aliases
withHubAndInfo.tagInfo == info
```

### Blocking Construction — `makeBlocking` and `makeBlockingWithResolver`

```scala
object SQLiteEventStore {
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): SQLiteEventStore[E]
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], migration: Migration[E, E]): SQLiteEventStore[E]
  def makeBlockingWithResolver[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]], tagInfo: TagInfo): SQLiteEventStore[E]
}
```

These return the store directly instead of a `Task`, for tests that need to build one outside an effect. They create the table and its index on the calling thread, and unlike the factories above they do not check the tags in the database. As with `makeWithHub`, you pass the hub:

```scala mdoc:silent
val blockingHub = run(Hub.bounded[EventEnvelope[UserEvent]](16))
val blocking    = SQLiteEventStore.makeBlocking[UserEvent](run(cache.get(s"$folder/blocking.db")), blockingHub)
val blockingMigration =
  SQLiteEventStore.makeBlocking[UserEvent](run(cache.get(s"$folder/blocking-migration.db")), blockingHub, migration)
val blockingInfo =
  SQLiteEventStore.makeBlockingWithResolver[UserEvent](run(cache.get(s"$folder/blocking-info.db")), blockingHub, info)
```

```scala mdoc
run(blocking.append("user-1", UserCreated("Alice", "alice@example.com")))
blockingMigration.tagInfo.aliases
blockingInfo.tagInfo == info
```

```scala mdoc:invisible
run(cache.close)
```

## See Also

- [Projection module overview](./index.md) — how the event store, the projections, the engine and the projection stores fit together.
- [Projection[A]](./projection.md) — the projections that read the event store.
- [Migration](./migration.md) — renamed event cases, `@eventTag`, and changed event fields.
- [ProjectionEngine](./engine.md) — the runtime that replays and follows an event store.
