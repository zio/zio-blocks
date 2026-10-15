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

`EventStore#append` returns the allocated sequence number, which the store records in an `events` table as the row's primary key. `EventStore#readFrom` streams all events after a given `seq`, optionally filtered to specific tags — the engine uses this for catch-up replay. `EventStore#subscribe` returns the live `Hub`; the engine subscribes when it transitions from catch-up to streaming mode.

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

By default, `EventStore[E]` uses the case class simple name as the tag. Renaming a case class therefore changes the tag that new events are stored under, leaving old events unreadable under the new name.

There are two ways to keep old events readable: give each case a stable numeric tag with `@eventTag(n)`, or describe the rename with a `Migration` so that the store knows the old name. Both are explained in [Event Tagging and Migration](./projection.md#event-tagging-and-migration).

## See Also

- [Projection module overview](./index.md) — how the event store, the projections, the engine and the projection stores fit together.
- [Projection[A]](./projection.md) — the projections that read the event store, and [Event Tagging and Migration](./projection.md#event-tagging-and-migration) for renamed events.
- [ProjectionEngine](./engine.md) — the runtime that replays and follows an event store.
