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

The projection module turns an append-only event log into queryable read models with a purely declarative API. You describe — once, in a builder chain — which events a model listens to, how they route to entity stores, and what each kind of event does to the model. An engine then reads those descriptions and handles replay, watermark tracking, schema-change detection, and live streaming automatically. 

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
- **Events are permanent.** Once written they stay as they are, so renaming an event or changing its shape needs care. The [Migration](./migration.md#changing-events) page covers this.

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
- it stores each projection in its own SQLite file, so read models are isolated, individually backed up, and queryable offline. Unrelated projections never wait for each other's locks or write-ahead log, as they would with one shared database.

Because the same declaration runs against an in-memory store, you can test a projection by appending events and querying the result, with no database. The [Quick Start](#quick-start) shows the whole loop in one short program.

Projection is a good fit when you already treat events as the source of truth and want fast, local, query-friendly views of them. It is not a general query engine over arbitrary data, and the read models it maintains are derived state: they can always be discarded and rebuilt from the events.

## Glossary

The terms below are used throughout the pages of this module. The first list gives the general event sourcing terms, which apply to any event-sourced system. The second list names the types of this library. Most of them are explained in more detail in the sections that follow and on the type pages.

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
- **Event versioning (schema evolution)**: Changing the name or shape of an event or a read model after data exists. Old events stay as they are and are read by the new code, and a read model is rebuilt from them. See [Migration](./migration.md).

```text
event store  --(engine reads events and runs handlers)-->  projection store
(the log)                                                  (the finished rows)
```

### Types in This Library

The types below are the ones you meet when you use the library, grouped by the part of the picture above they belong to. Events are written to an event store. A projection says how those events become rows, and its handlers do the work for each event. Sources and routing decide which events reach which rows. The engine runs everything and keeps the rows in stores. Schemas keep the old events and rows working when their shape changes.

#### Events

- `EventStore`: The event log, as a trait. It appends events, reads the history, and lets a consumer follow new events.
- `SQLiteEventStore`: An `EventStore` kept in a SQLite file.
- `EventEnvelope`: An event together with the metadata the store records about it: its sequence number, its tag, its timestamp and the entity ID it was recorded under.
- `eventTag`: The annotation `@eventTag(n)`, which gives an event case a stable numeric tag. See [Numeric Tags](./migration.md#numeric-tags--eventtag).

#### Projections

- `Projection[A]`: The declaration of how events become the read model `A`: which sources to listen to, how to route their events, and what each event does. It does nothing until an engine runs it.
- `Handler[E, A]`: The function that turns an event `E` into a `ProjectionAction[A]`. It only describes the change.
- `ProjectionContext`: The metadata a handler receives together with an event, such as the entity ID.
- `ProjectionAction`: What a handler returns: an insert, an upsert, an update, a delete, a truncate or nothing.
- `FieldUpdate`: A change to one field of a row, used by an update action, such as setting a value or incrementing a counter. See [Aggregating Fields](./projection.md#aggregating-fields).
- `ProjectionScope`: How the data of a `Projection` is split across `ProjectionStore`s. It is worked out from how the projection is built and is not set directly. It is one of three: one row for each entity ID (the default), one store for each routing key, called a shard, or one shared store for the whole projection. See [Projection Scope](./projection.md#projection-scope).

#### Sources and routing

- `SourceBinding`: A named source of events, given to `Projection.from`, together with its routing and its handlers. The name must match the key of an `EventStore` given to the engine.
- `RoutingMode`: How the events of a source are directed: to the entity they were recorded under, to one shared store, or to a store chosen by a key that is read from the event.

#### Stores and the engine

- `ProjectionStore[A]`: Where the rows of a read model are kept.
- `SQLiteProjectionStore`: A `ProjectionStore` kept in a SQLite file.
- `InMemoryProjectionStore`: A `ProjectionStore` kept in an in-memory map, used for tests.
- `ProjectionEngine`: The runtime that reads events from `EventStore`s, runs the handlers and writes the results to `ProjectionStore`s.
- `TestEngine`: A `ProjectionEngine` with in-memory stores, for tests and examples.
- `TransactorCache`: A cache of open SQLite connections, one for each database file, shared by the stores and the engine. See [TransactorCache](./engine.md#connection-management).
- **WAL (write-ahead log)**: A way for SQLite to make writes safe. A write is first appended to a separate `.db-wal` file, and it is copied into the main `.db` file later. Readers can keep reading while a write happens, and a crash does not lose the writes in the log. There is still only one writer at a time for each file. The `TransactorCache` turns it on for every file it opens, so you will see `-wal` and `-shm` files next to the `.db` files.

#### Schemas

- `EntityPath`: Says which folder the files of a projection go in and which field of the row is its ID. See [Entity Paths](./projection.md#entity-paths).
- `SchemaHash`: A fingerprint of a read model's schema. When it changes, the engine rebuilds the `ProjectionStore`.
- `SchemaEvolution`: Rebuilds a `ProjectionStore` by replaying the events. See [Full Rebuild](./migration.md#full-rebuild).
- `TagInfo`: A cheat sheet of the tags of an event type: which tags are current, and which old tags were renamed to them. It lets an `EventStore` find the events that were saved under an old name. See [Renaming an Event Case](./migration.md#renaming-an-event-case).
- `TagResolver`: Builds a `TagInfo` from an event type and, if a case was renamed, a `Migration`.

## Installation

The core module is sufficient to compile projections and run tests:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-projection" % "@VERSION@"
```

For durable SQLite persistence on the JVM, add the JDBC driver:

```scala
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The library does not bring `sqlite-jdbc` with it, so add it to your own build to keep events and read models in SQLite files. The SQLite stores open their files through a [`TransactorCache`](./engine.md#connection-management), which loads the driver by name when it first opens a file. Without the driver, that first file fails to open with `ClassNotFoundException: org.sqlite.JDBC`. For a `SQLiteEventStore` that happens when you create it. For the projection stores that the engine creates, it happens when a store is first used, because the engine does not open their files when it is created.

## Overview

Two types carry the whole lifecycle:

- [`Projection[A]`](./projection.md) is the descriptor: a pure, immutable value that names the read model, lists its sources, and records a handler per event type. It holds no fibers.
- [`ProjectionEngine`](./engine.md) is the runtime: it reads `Projection[A]` values, connects each one to an `EventStore[E]`, and drives events through the right handler into a `ProjectionStore[A]`. It does not inspect the handler functions. `EventStore[E]` is the write-side contract, the log that events are appended to, and [`ProjectionStore[A]`](./projection-store.md) is the read-side contract, the rows of a read model, as described under [Event Sourcing Basics](#event-sourcing-basics).

## How They Work Together

A projection goes through nine steps, from the declaration in your code to a row you can query:

1. **Declare the projection.** Call `Projection[A](name)` for a read model with one row per entity, or `Projection.global[A](name)` for one shared row of totals. The result is an empty, immutable description: it knows the shape of a row and where its files go, and nothing else yet.
2. **Say where the events come from.** Call `from(sourceName)` to name the event store the projection reads. A projection can read several, one `from` call each: everything that follows a `from` call, the routing and the handlers up to the next `from`, belongs to that source, which is how one read model can combine facts from more than one feed, such as a daily stats row that counts both new users and new repositories (see [`from`](./projection.md#defining-sources)).
3. **Say how the events are routed.** Right after `from`, choose one of three:
   - **Per entity:** `routeToSelf` sends each event to the row of the entity it was recorded under.
   - **By a key from the event:** `routedBy[E](_.field)` groups events by a value read from the event.
   - **One shared store:** `routeToAll` sends them all to one store.

   Which one fits, and where each keeps its files, is described under [Projection Scope](./projection.md#projection-scope).
4. **Add a handler for each event type.** Call `on[E]` for an event type, then one of the handler verbs, which says what that event does to the row:
   - **Insert and delete:** the event adds a new row or removes one. `insert(...)` builds the row from the event; `delete` removes the row of the entity the event belongs to.
   - **Field updates:** the event changes one column of an existing row, for example `status` to `"shipped"`. `update(_.field)(...)` picks the column by field, checked by the compiler; `updateWithField("column", ...)` and `updateField("column")(...)` pick it by name.
   - **Aggregation:** the event makes the same change every time, such as adding one to a counter: `aggregate(FieldUpdate.Increment("order_count", 1L))` on `OrderPlaced` adds one to `order_count` for every order. `aggregate(Chunk(...))` makes several changes at once, and `aggregateField(...)` is another name for the single form.
   - **Custom actions:** the handler looks at the event and decides what to do: insert, update, or nothing. `custom(...)` returns that decision as a `ProjectionAction`.
5. **Open the event stores.** Each source name given to `from` needs an `EventStore`, which holds the events of one event type. On the JVM, open a SQLite file through a `TransactorCache` and wrap it: `cache.get("events/users.db")` gives a connection to the file, and `SQLiteEventStore.make[E](transactor)` the store for the event type `E`.
6. **Create an engine.** Give the projections to a `ProjectionEngine`, together with the event stores they read. 
7. **Append events.** Call `eventStore.append(entityId, event)`. The store gives the event the next sequence number, wraps it in an `EventEnvelope` with that number, its tag, the time and the entity ID, and publishes it to the engine.
8. **The engine does the rest.** It first catches up on the events it has not seen yet, then follows new ones as they arrive. For each event it builds a `ProjectionContext` from the envelope, asks the projection for the handler's action with `handle`, applies that action to the store chosen by the routing, and saves the sequence number as its watermark, so that a restart continues from there.
9. **Query.** Call `engine.query(projection, entityId)` to read the row back from the store.

The diagram shows how the types hand the data along:

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
                               │    ProjectionStore[A]       │
                               │    (SQLite or InMemory)     │
                               └─────────────────────────────┘
                                             ▲
                               engine.query(projection, id)
```

## Quick Start

A projection is defined once, then run by an engine. The example below defines the events and the read model, declares the projection, and runs it on SQLite files, the way an application does.

Open the event store and the projection store yourself, through a `TransactorCache`, and give them to `ProjectionEngine.makeWithStores`. The stores do not create folders, so the program creates `events/` and `users/` first. It needs `sqlite-jdbc` on the runtime classpath, see [Installation](#installation). The program is `Example1QuickStart` in the projection examples:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example1QuickStart.scala

```

The files stay on disk after the program ends. When a program starts again, the events are still in `events/users.db`, and the engine continues after the last event it processed, so no event is applied twice. That is why the program appends its events only when the event store is empty: run it a second time, and it reads the same rows back from the files. The files are plain SQLite databases, so `sqlite3 users/user_profiles.db` shows the rows while the program is stopped.

## See Also

The [SQL Module](../sql/index.md) supplies the `Transactor`, `DbCon`, and `SqlNameMapper` types that back the SQLite implementations of `EventStore[E]` and `ProjectionStore[A]`. The [Data Migration](../data-migration.md) reference covers typed online schema migrations that pair naturally with projection-backed read models during schema evolution.
